package com.craftteamracing.tuning;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.craftteamracing.CtrConfig;
import com.craftteamracing.CraftTeamRacingMod;
import com.craftteamracing.physics.KartStats;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The server's kart tuning profiles, read from data packs: {@code data/<namespace>/craftteamracing/kart_tuning/<name>.json}.
 * <pre>
 * { "name": "Heavy", "description": "...", "official": false, "enabled": true,
 *   "stats": { "accel": 480, "speed": 13140, "turn_rate": 28, "turn_decrease": 4080, "turn_input_delay": 5000, "drift_turn_base": 14, "weight": 256 } }
 * </pre>
 * Missing numbers are Balanced's. The four original classes ship in the mod (craftteamracing:balanced, acceleration, speed, turning, "official"). A data pack turns one off by replacing its file
 * with {@code { "enabled": false }}; the server config's {@code disabledTuningProfiles} does the same by id. A kart whose profile is unknown or off is tuned as Balanced (the first
 * profile left, if Balanced itself is off, and the original Balanced numbers when none is left). The enabled list goes to every player on joining and on a data pack reload.
 */
public final class TuningManager extends SimpleJsonResourceReloadListener {
    private static final Gson GSON = new Gson();
    public static final TuningManager INSTANCE = new TuningManager();
    /** Every profile the data packs define, disabled ones left out (sorted: the official classes first). */
    private static volatile List<TuningProfile> all = List.of();
    private static volatile List<String> disabledInPacks = List.of();

    private TuningManager() {
        super(GSON, "craftteamracing/kart_tuning");
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener((AddReloadListenerEvent e) -> e.addListener(INSTANCE));
        NeoForge.EVENT_BUS.addListener(TuningManager::sync);
    }

    private static final List<String> OFFICIAL_ORDER = List.of("craftteamracing:balanced", "craftteamracing:acceleration", "craftteamracing:speed", "craftteamracing:turning");

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager, ProfilerFiller profiler) {
        Map<String, TuningProfile> found = new TreeMap<>();
        List<String> off = new ArrayList<>();
        for (Map.Entry<ResourceLocation, JsonElement> e : files.entrySet()) {
            String id = e.getKey().toString();
            try {
                JsonObject o = GsonHelper.convertToJsonObject(e.getValue(), "kart tuning");
                if (!GsonHelper.getAsBoolean(o, "enabled", true)) {
                    off.add(id);
                    continue;
                }
                JsonObject s = GsonHelper.getAsJsonObject(o, "stats", new JsonObject());
                KartStats b = KartStats.BALANCED;
                KartStats stats = new KartStats(GsonHelper.getAsInt(s, "accel", b.accel()), GsonHelper.getAsInt(s, "speed", b.speed()), GsonHelper.getAsInt(s, "turn_rate", b.turnRate()),
                        GsonHelper.getAsInt(s, "turn_decrease", b.turnDecrease()), GsonHelper.getAsInt(s, "turn_input_delay", b.turnInputDelay()),
                        GsonHelper.getAsInt(s, "drift_turn_base", b.driftTurnBase()), GsonHelper.getAsInt(s, "weight", b.weight()));
                if (!stats.valid()) {
                    LogUtils.getLogger().warn("Kart tuning {} has numbers out of range, left out: {}", id, stats);
                    continue;
                }
                found.put(id, new TuningProfile(id, GsonHelper.getAsString(o, "name", e.getKey().getPath()), GsonHelper.getAsString(o, "description", ""),
                        GsonHelper.getAsBoolean(o, "official", false), stats));
            } catch (RuntimeException ex) {
                LogUtils.getLogger().warn("Kart tuning {} could not be read: {}", id, ex.getMessage());
            }
        }
        List<TuningProfile> sorted = new ArrayList<>(found.values());
        sorted.sort(Comparator.comparingInt((TuningProfile p) -> OFFICIAL_ORDER.contains(p.id()) ? OFFICIAL_ORDER.indexOf(p.id()) : OFFICIAL_ORDER.size()).thenComparing(TuningProfile::id));
        all = List.copyOf(sorted);
        disabledInPacks = List.copyOf(off);
    }

    private static boolean disabledByConfig(String id) {
        try {
            for (Object o : CtrConfig.DISABLED_TUNING.get()) {
                String d = String.valueOf(o);
                if (d.equals(id) || (!d.contains(":") && (CraftTeamRacingMod.MODID + ":" + d).equals(id))) return true;
            }
        } catch (IllegalStateException notLoaded) {
            // the server config is not loaded yet: nothing is disabled by it
        }
        return false;
    }

    /** The profiles a kart can be given now. */
    public static List<TuningProfile> enabled() {
        List<TuningProfile> out = new ArrayList<>();
        for (TuningProfile p : all) {
            if (!disabledByConfig(p.id())) out.add(p);
        }
        return out;
    }

    /** The profile of the id, or the fallback (see the class doc). */
    public static TuningProfile resolve(String id) {
        return resolve(enabled(), id);
    }

    /** Resolution in a list (the server's, or the one a client was sent). */
    public static TuningProfile resolve(List<TuningProfile> list, String id) {
        TuningProfile debug = TuningProfile.parseDebug(id);
        if (debug != null) return debug;
        if (id != null && !id.isEmpty()) {
            for (TuningProfile p : list) {
                if (p.id().equals(id)) return p;
            }
        }
        for (TuningProfile p : list) {
            if (p.id().equals(TuningProfile.FALLBACK.id())) return p;
        }
        return list.isEmpty() ? TuningProfile.FALLBACK : list.get(0);
    }

    private static void sync(OnDatapackSyncEvent event) {
        TuningListPayload payload = new TuningListPayload(enabled());
        for (ServerPlayer p : event.getRelevantPlayers().toList()) {
            PacketDistributor.sendToPlayer(p, payload);
        }
    }

}
