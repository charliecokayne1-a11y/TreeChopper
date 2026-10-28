
package com.example.shardfarm;

import net.minecraft.util.math.Direction;

public final class FarmConfig {
    private FarmConfig() {}

    // Direction the player is facing while looking at the farm.
    public static Direction farmFacing = Direction.NORTH;

    // Existing farming settings.
    public static int actionDelayTicks = 4;
    public static int maxBoneMealPerSapling = 64;
    public static boolean requireNamedAxe = false;
    public static String axeNameContains = "shard";
}
