package com.example.shardfarm;

/** Simple in-memory settings, editable from the menu. */
public final class FarmConfig {
    private FarmConfig() {}

    /** Ticks to wait between actions (20 ticks = 1 second). */
    public static int actionDelayTicks = 4;

    /** Only use an axe whose name contains axeNameContains. */
    public static boolean requireNamedAxe = true;

    /** Text that must appear in the axe's name (case-insensitive). */
    public static String axeNameContains = "shard";

    /** Give up on a sapling after this many bone meals. */
    public static int maxBoneMealPerSapling = 24;
}
