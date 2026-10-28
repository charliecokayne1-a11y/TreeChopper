
package com.example.shardfarm;

import net.minecraft.util.math.Direction;

public final class FarmConfig {
    private FarmConfig() {}

    // Direction you're facing when looking at the farm.
    public static Direction farmFacing = Direction.NORTH;

    // Delay between farming actions, in ticks.
    public static int actionDelayTicks = 4;

    // Maximum bone meal uses before stopping.
    public static int maxBoneMealPerSapling = 64;

    // Whether the axe must have a specific name.
    public static boolean requireNamedAxe = false;

    // Required axe name, when enabled.
    public static String axeNameContains = "shard";
}
