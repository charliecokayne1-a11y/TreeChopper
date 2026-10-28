
package com.example.shardfarm;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

public final class ReplantManager {
    private ReplantManager() {}

    private static BlockPos[] originalPositions;

    /**
     * Save the original 2x2 planting layout.
     * Call this when the farm first starts.
     */
    public static void rememberOriginalLayout(BlockPos ground, Direction facing) {
        Direction right = facing.rotateYClockwise();
        Direction back = facing.getOpposite();

        originalPositions = new BlockPos[] {
            ground.offset(back),
            ground.offset(back).offset(right),
            ground.offset(right),
            ground
        };
    }

    /**
     * Get the saved planting position for one of the four saplings.
     */
    public static BlockPos getPlantingPosition(int index) {
        if (originalPositions == null || index < 0 || index >= 4) {
            return null;
        }

        return originalPositions[index];
    }

    public static boolean hasOriginalLayout() {
        return originalPositions != null;
    }

    public static void clear() {
        originalPositions = null;
    }
}
