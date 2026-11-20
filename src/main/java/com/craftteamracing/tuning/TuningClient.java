package com.craftteamracing.tuning;

import java.util.List;

/** The profiles the server last sent (empty until it has: then everything is Balanced, the fallback). Plain data, usable from common and client code. */
public final class TuningClient {
    private TuningClient() {}

    private static volatile List<TuningProfile> profiles = List.of();

    public static void set(List<TuningProfile> list) {
        profiles = List.copyOf(list);
    }

    public static List<TuningProfile> profiles() {
        return profiles;
    }

    public static TuningProfile resolve(String id) {
        return TuningManager.resolve(profiles, id);
    }
}
