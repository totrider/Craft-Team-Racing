package com.craftteamracing.client;

import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.craftteamracing.CraftTeamRacingMod;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.RandomSource;

/**
 * The look of the weapons' and crates' effects (the flames of a bomb's fuse, a missile's exhaust, the poof of a weapon that goes away, a flask bursting, the rain cloud...) as data:
 * {@code assets/craftteamracing/fx/<id>.json}. The jar has defaults made of vanilla particles; a resource pack replaces a file to change what is shown (the mod's own particles, other
 * vanilla ones) or empties its {@code emitters} to show nothing, so a pack's look is all there is. The code only says which effect, where, and which way it faces:
 * {@link #play}. Effects the server causes arrive as a {@code FxPayload} and are played here too.
 * <pre>
 * { "emitters": [ {
 *     "model": "burst", "frames": 30, "fps": 30, "billboard": true, "roll": 0, "scale": 1   instead of "particle": a baked model played frame by frame (see ModelFx)
 *     "sparks": { "count": 10, "speed": 0.35, "colors": ["ff8c1a"], "fade": ["404040"], "trail": true, "twinkle": false }    instead of "particle": a ball of vanilla firework sparks, a
 *                                             few of them, thrown out in all directions (no firework sound: that is the explosion's own); colours as RRGGBB hex
 *     "sound": "minecraft:entity.generic.explode", "volume": 4.0, "pitch": 1.0    instead of "particle": a sound played at the place (client side)
 *     "ring": 24, "ring_speed": 0.4                      with "particle": that many of them on a flat ring round the place, flying outwards
 *     "particle": "minecraft:small_flame",    the particle type (a simple one: vanilla's or craftteamracing:burst, dust_puff, exhaust_glow...)
 *     "count": 1, "every": 1, "chance": 1.0,  how many each time, only on the ticks a multiple of "every", and with this probability
 *     "pos": [0, 0, 0], "pos_spread": [0, 0, 0],            where, from the effect's place, plus or minus the spread
 *     "velocity": [0, 0.03, 0], "velocity_spread": [0.01, 0.01, 0.01],
 *     "local": false                          true: the vectors are (right, up, forward) turned to the way the effect faces; false: world axes
 * } ] }
 * </pre>
 * Any number may be a string {@code "$name"}: a number the code passes ({@code radius} of a blast, {@code arg}, {@code tick}).
 */
public final class Fx {
    private Fx() {}

    /** A model effect: the baked frames {@code item/pickup_<model>_<n>} played at {@code fps} (30 is the game's frame rate), facing the camera (rolled by {@code roll} degrees) or lying flat. */
    public record ModelSpec(String model, int frames, double fps, boolean billboard, double roll, double scale) {}

    private record Emitter(ParticleOptions particle, ModelSpec model, Sparks sparks, SoundSpec sound, int ring, double ringSpeed, Value count, int every, double chance, Value[] pos, Value[] posSpread, Value[] vel, Value[] velSpread, boolean local) {}

    /** A number or the name of one the caller passes. */
    private record Value(double number, String name) {
        double get(Map<String, Double> params) {
            if (name == null) return number;
            Double v = params.get(name);
            return v == null ? 0.0 : v;
        }
    }

    private static volatile Map<String, List<Emitter>> profiles = Map.of();
    private static final RandomSource RANDOM = RandomSource.create();

    /** A reload listener's body: reads every {@code fx/*.json}. */
    public static void load(ResourceManager manager) {
        Map<String, List<Emitter>> out = new HashMap<>();
        for (Map.Entry<ResourceLocation, Resource> e : manager.listResources("fx", id -> id.getPath().endsWith(".json")).entrySet()) {
            ResourceLocation id = e.getKey();
            if (!id.getNamespace().equals(CraftTeamRacingMod.MODID)) continue;
            String name = id.getPath().substring("fx/".length(), id.getPath().length() - ".json".length());
            try (Reader in = e.getValue().openAsReader()) {
                JsonObject root = JsonParser.parseReader(in).getAsJsonObject();
                List<Emitter> list = new ArrayList<>();
                for (JsonElement em : root.getAsJsonArray("emitters")) {
                    Emitter parsed = parse(em.getAsJsonObject(), name);
                    if (parsed != null) list.add(parsed);
                }
                out.put(name, List.copyOf(list));
            } catch (Exception ex) {
                CraftTeamRacingMod.LOGGER.warn("Could not read the effect {}: {}", id, ex.toString());
            }
        }
        profiles = Map.copyOf(out);
    }

    private static Emitter parse(JsonObject o, String effect) {
        ParticleOptions simple = null;
        ModelSpec model = null;
        Sparks sparks = null;
        SoundSpec sound = null;
        if (o.has("sound")) {
            var ev = BuiltInRegistries.SOUND_EVENT.get(ResourceLocation.parse(o.get("sound").getAsString()));
            if (ev == null) return null;
            sound = new SoundSpec(ev, o.has("volume") ? o.get("volume").getAsFloat() : 1.0F, o.has("pitch") ? o.get("pitch").getAsFloat() : 1.0F);
        } else if (o.has("sparks")) {
            sparks = sparks(o.getAsJsonObject("sparks"));
        } else if (o.has("model")) {
            model = new ModelSpec(o.get("model").getAsString(), o.has("frames") ? o.get("frames").getAsInt() : 1, o.has("fps") ? o.get("fps").getAsDouble() : 30.0,
                    !o.has("billboard") || o.get("billboard").getAsBoolean(), o.has("roll") ? o.get("roll").getAsDouble() : 0.0, o.has("scale") ? o.get("scale").getAsDouble() : 1.0);
        } else if (o.has("particle")) {
            ResourceLocation pid = ResourceLocation.parse(o.get("particle").getAsString());
            ParticleType<?> type = BuiltInRegistries.PARTICLE_TYPE.get(pid);
            if (!(type instanceof SimpleParticleType st)) {
                CraftTeamRacingMod.LOGGER.warn("Effect {}: {} is not a simple particle type, skipped", effect, pid);
                return null;
            }
            simple = st;
        }
        return new Emitter(simple, model, sparks, sound, o.has("ring") ? o.get("ring").getAsInt() : 0, o.has("ring_speed") ? o.get("ring_speed").getAsDouble() : 0.4, value(o, "count", 1.0), o.has("every") ? Math.max(1, o.get("every").getAsInt()) : 1, o.has("chance") ? o.get("chance").getAsDouble() : 1.0,
                vec(o, "pos"), vec(o, "pos_spread"), vec(o, "velocity"), vec(o, "velocity_spread"), o.has("local") && o.get("local").getAsBoolean());
    }

    /** A ball of firework sparks: how many, how fast, the colours they start in and fade to, and whether they trail or twinkle. */
    private record Sparks(int count, double speed, double size, int life, int[] colours, int[] fade, boolean trail, boolean twinkle) {}

    private record SoundSpec(net.minecraft.sounds.SoundEvent event, float volume, float pitch) {}

    private static Sparks sparks(JsonObject o) {
        return new Sparks(o.has("count") ? o.get("count").getAsInt() : 10, o.has("speed") ? o.get("speed").getAsDouble() : 0.35, o.has("size") ? o.get("size").getAsDouble() : 1.0, o.has("life") ? o.get("life").getAsInt() : 0,
                colours(o, "colors"), colours(o, "fade"),
                o.has("trail") && o.get("trail").getAsBoolean(), o.has("twinkle") && o.get("twinkle").getAsBoolean());
    }

    private static int[] colours(JsonObject o, String key) {
        if (!o.has(key)) return new int[0];
        JsonArray a = o.getAsJsonArray(key);
        int[] out = new int[a.size()];
        for (int i = 0; i < out.length; i++) out[i] = Integer.parseInt(a.get(i).getAsString(), 16);
        return out;
    }

    /** Throws the sparks of one burst: the vanilla firework spark particle, tinted, on random directions of a sphere (a little flattened: a ground burst is not a ball). */
    private static void burst(ClientLevel level, Sparks s, double x, double y, double z) {
        for (int i = 0; i < s.count(); i++) {
            double ux, uy, uz, l;
            do {
                ux = RANDOM.nextDouble() * 2.0 - 1.0;
                uy = RANDOM.nextDouble() * 2.0 - 1.0;
                uz = RANDOM.nextDouble() * 2.0 - 1.0;
                l = ux * ux + uy * uy + uz * uz;
            } while (l > 1.0 || l < 0.01);
            l = Math.sqrt(l);
            double v = s.speed() * (0.6 + 0.4 * RANDOM.nextDouble());
            var made = Minecraft.getInstance().particleEngine.createParticle(net.minecraft.core.particles.ParticleTypes.FIREWORK, x, y, z, ux / l * v, (uy / l * 0.6 + 0.45) * v, uz / l * v);
            if (made instanceof net.minecraft.client.particle.SimpleAnimatedParticle spark) {
                if (s.colours().length > 0) spark.setColor(s.colours()[RANDOM.nextInt(s.colours().length)]);
                if (s.fade().length > 0) spark.setFadeColor(s.fade()[RANDOM.nextInt(s.fade().length)]);
                if (s.size() != 1.0) spark.scale((float) s.size());
                if (s.life() > 0) spark.setLifetime(s.life() + RANDOM.nextInt(8));
                flag(made, "setTrail", s.trail());                    // the spark class is vanilla's and not public: these two are set through reflection
                flag(made, "setFlicker", s.twinkle());
            }
        }
    }

    private static void flag(Object particle, String method, boolean on) {
        try {
            java.lang.reflect.Method m = particle.getClass().getDeclaredMethod(method, boolean.class);
            m.setAccessible(true);
            m.invoke(particle, on);
        } catch (ReflectiveOperationException | RuntimeException e) {
            // no trail or twinkle then
        }
    }

    private static Value value(JsonObject o, String key, double fallback) {
        return o.has(key) ? value(o.get(key)) : new Value(fallback, null);
    }

    private static Value value(JsonElement e) {
        if (e.getAsJsonPrimitive().isString()) {
            String s = e.getAsString();
            return new Value(0.0, s.startsWith("$") ? s.substring(1) : s);
        }
        return new Value(e.getAsDouble(), null);
    }

    private static Value[] vec(JsonObject o, String key) {
        Value[] v = { new Value(0, null), new Value(0, null), new Value(0, null) };
        if (o.has(key)) {
            JsonArray a = o.getAsJsonArray(key);
            for (int i = 0; i < 3 && i < a.size(); i++) v[i] = value(a.get(i));
        }
        return v;
    }

    /** Plays an effect on the client at a place, facing {@code yawDegrees} (Minecraft's yaw: forward is (-sin, cos)); {@code params} are the numbers its profile may ask for. */
    public static void play(String id, double x, double y, double z, float yawDegrees, Map<String, Double> params) {
        play(id, x, y, z, yawDegrees, params, null);
    }

    /** The same, with the puffs tied to a kart: they travel with it (the rain cloud over a kart). */
    public static void play(String id, double x, double y, double z, float yawDegrees, Map<String, Double> params, com.craftteamracing.kart.KartEntity anchor) {
        ClientLevel level = Minecraft.getInstance().level;
        List<Emitter> list = profiles.get(id);
        if (level == null || list == null) return;
        double yaw = Math.toRadians(yawDegrees);
        double fx = -Math.sin(yaw);
        double fz = Math.cos(yaw);
        double rx = -Math.cos(yaw);
        double rz = -Math.sin(yaw);
        int tick = (int) Math.round(params.getOrDefault("tick", 0.0));
        for (Emitter e : list) {
            if (tick % e.every() != 0 || (e.chance() < 1.0 && RANDOM.nextDouble() >= e.chance())) continue;
            if (e.sound() != null) {
                level.playLocalSound(x, y, z, e.sound().event(), net.minecraft.sounds.SoundSource.BLOCKS, e.sound().volume(), e.sound().pitch(), false);
                continue;
            }
            if (e.ring() > 0 && e.particle() != null) {
                // a flat ring round the place, thrown outwards: the halo of a blast
                double[] p0 = vector(e.pos(), e.posSpread(), params);
                for (int k = 0; k < e.ring(); k++) {
                    double a = k * Math.PI * 2.0 / e.ring() + RANDOM.nextDouble() * 0.2;
                    double sp = e.ringSpeed() * (0.8 + 0.4 * RANDOM.nextDouble());
                    level.addParticle(e.particle(), x + p0[0] + Math.cos(a) * 0.3, y + p0[1], z + p0[2] + Math.sin(a) * 0.3, Math.cos(a) * sp, 0.02 + RANDOM.nextDouble() * 0.03, Math.sin(a) * sp);
                }
                continue;
            }
            int n = (int) Math.round(e.count().get(params));
            for (int k = 0; k < n; k++) {
                double[] p = vector(e.pos(), e.posSpread(), params);
                double[] v = vector(e.vel(), e.velSpread(), params);
                double px, py, pz, vx, vy, vz;
                if (e.local()) {
                    px = x + rx * p[0] + fx * p[2];
                    pz = z + rz * p[0] + fz * p[2];
                    vx = rx * v[0] + fx * v[2];
                    vz = rz * v[0] + fz * v[2];
                } else {
                    px = x + p[0];
                    pz = z + p[2];
                    vx = v[0];
                    vz = v[2];
                }
                py = y + p[1];
                vy = v[1];
                if (e.sparks() != null) {
                    burst(level, e.sparks(), px, py, pz);
                } else if (e.model() != null) {
                    ModelFx.spawn(e.model(), px, py, pz, level.getGameTime());
                } else {
                    if (anchor != null) {
                        net.minecraft.client.particle.Particle made = Minecraft.getInstance().particleEngine.createParticle(e.particle(), px, py, pz, vx, vy, vz);
                        if (made instanceof DustPuffParticle puff) puff.anchorTo(anchor);
                    } else {
                        level.addParticle(e.particle(), px, py, pz, vx, vy, vz);
                    }
                }
            }
        }
    }

    private static double[] vector(Value[] base, Value[] spread, Map<String, Double> params) {
        double[] out = new double[3];
        for (int i = 0; i < 3; i++) {
            double s = spread[i].get(params);
            out[i] = base[i].get(params) + (s == 0.0 ? 0.0 : (RANDOM.nextDouble() * 2.0 - 1.0) * s);
        }
        return out;
    }

    /** Plays an effect with the one number the server sends. */
    /** An effect that travels with a kart (a rain cloud's puffs). */
    public static void playAnchored(String id, com.craftteamracing.kart.KartEntity kart, int tick) {
        play(id, kart.getX(), kart.getY(), kart.getZ(), 0.0F, Map.of("arg", 0.0, "radius", 0.0, "tick", (double) tick), kart);
    }

    public static void play(String id, double x, double y, double z, float yawDegrees, float arg, int tick) {
        play(id, x, y, z, yawDegrees, Map.of("arg", (double) arg, "radius", (double) arg, "tick", (double) tick));
    }
}
