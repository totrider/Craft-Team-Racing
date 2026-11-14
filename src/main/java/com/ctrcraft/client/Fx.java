package com.ctrcraft.client;

import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.ctrcraft.CtrCraftMod;
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
 * {@code assets/ctrcraft/fx/<id>.json}. The jar has defaults made of vanilla particles; a resource pack replaces a file to change what is shown (the mod's own particles, other
 * vanilla ones) or empties its {@code emitters} to show nothing, so a pack's look is all there is. The code only says which effect, where, and which way it faces:
 * {@link #play}. Effects the server causes arrive as a {@code FxPayload} and are played here too.
 * <pre>
 * { "emitters": [ {
 *     "model": "burst", "frames": 30, "fps": 30, "billboard": true, "roll": 0, "scale": 1   instead of "particle": a baked model played frame by frame (see ModelFx)
 *     "particle": "minecraft:small_flame",    the particle type (a simple one: vanilla's or ctrcraft:burst, dust_puff, exhaust_glow...)
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

    private record Emitter(ParticleOptions particle, ModelSpec model, Value count, int every, double chance, Value[] pos, Value[] posSpread, Value[] vel, Value[] velSpread, boolean local) {}

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
            if (!id.getNamespace().equals(CtrCraftMod.MODID)) continue;
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
                CtrCraftMod.LOGGER.warn("Could not read the effect {}: {}", id, ex.toString());
            }
        }
        profiles = Map.copyOf(out);
    }

    private static Emitter parse(JsonObject o, String effect) {
        ParticleOptions simple = null;
        ModelSpec model = null;
        if (o.has("model")) {
            model = new ModelSpec(o.get("model").getAsString(), o.has("frames") ? o.get("frames").getAsInt() : 1, o.has("fps") ? o.get("fps").getAsDouble() : 30.0,
                    !o.has("billboard") || o.get("billboard").getAsBoolean(), o.has("roll") ? o.get("roll").getAsDouble() : 0.0, o.has("scale") ? o.get("scale").getAsDouble() : 1.0);
        } else {
            ResourceLocation pid = ResourceLocation.parse(o.get("particle").getAsString());
            ParticleType<?> type = BuiltInRegistries.PARTICLE_TYPE.get(pid);
            if (!(type instanceof SimpleParticleType st)) {
                CtrCraftMod.LOGGER.warn("Effect {}: {} is not a simple particle type, skipped", effect, pid);
                return null;
            }
            simple = st;
        }
        return new Emitter(simple, model, value(o, "count", 1.0), o.has("every") ? Math.max(1, o.get("every").getAsInt()) : 1, o.has("chance") ? o.get("chance").getAsDouble() : 1.0,
                vec(o, "pos"), vec(o, "pos_spread"), vec(o, "velocity"), vec(o, "velocity_spread"), o.has("local") && o.get("local").getAsBoolean());
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
                if (e.model() != null) {
                    ModelFx.spawn(e.model(), px, py, pz, level.getGameTime());
                } else {
                    level.addParticle(e.particle(), px, py, pz, vx, vy, vz);
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
    public static void play(String id, double x, double y, double z, float yawDegrees, float arg, int tick) {
        play(id, x, y, z, yawDegrees, Map.of("arg", (double) arg, "radius", (double) arg, "tick", (double) tick));
    }
}
