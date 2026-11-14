package com.ctrcraft.pickup;

/** The client's copy of what the driver carries (see {@link ItemStatePayload}). */
public final class ItemClient {
    private ItemClient() {}

    private static ItemStatePayload state = ItemStatePayload.EMPTY;
    private static long rollStartedMs;
    private static int lastSeq;

    public static void update(ItemStatePayload p) {
        if (p.roll() > 0 && state.roll() == 0) rollStartedMs = System.currentTimeMillis();
        if (p.effectSeq() == 0) lastSeq = 0;
        state = p;
    }

    public static void clear() {
        state = ItemStatePayload.EMPTY;
        lastSeq = 0;
    }

    public static int powerFruit() {
        return state.powerFruit();
    }

    /** The shots a stack still has (the digit the HUD shows beside the icon), 0 when not a stack. */
    public static int count() {
        return state.count();
    }

    public static String held() {
        return state.held();
    }

    /** True while the roulette runs (it ends when the server sends the item; the client does not count it down itself). */
    public static boolean rolling() {
        return state.roll() > 0;
    }

    public static long rollingForMs() {
        return System.currentTimeMillis() - rollStartedMs;
    }

    /** A power-up effect the driver's kart has to apply, once; {@link ItemManager#EFFECT_NONE} when there is none. */
    public static int takeEffect() {
        if (state.effectSeq() == lastSeq) return ItemManager.EFFECT_NONE;
        lastSeq = state.effectSeq();
        return state.effect();
    }
}
