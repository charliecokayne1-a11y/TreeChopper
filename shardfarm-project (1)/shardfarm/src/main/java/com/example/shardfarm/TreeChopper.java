
package com.example.shardfarm;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.world.World;

public class TreeChopper implements ModInitializer {

    @Override
    public void onInitialize() {
        PlayerBlockBreakEvents.AFTER.register(
            (world, player, pos, state, blockEntity) -> {
                if (!(world instanceof ServerWorld serverWorld)) {
                    return;
                }

                // Only react to spruce logs.
                if (!state.isOf(Blocks.SPRUCE_LOG)) {
                    return;
                }

                // Only replant when four saplings are available.
                if (countSpruceSaplings(player) < 4) {
                    return;
                }

                // The broken log might be any corner of the 2x2 base.
                for (int dx = -1; dx <= 0; dx++) {
                    for (int dz = -1; dz <= 0; dz++) {
                        BlockPos base = pos.add(dx, 0, dz);

                        if (plantFourSaplings(serverWorld, player, base)) {
                            player.sendMessage(
                                net.minecraft.text.Text.literal(
                                    "TreeChopper: Replanted a 2x2 spruce farm."
                                ),
                                true
                            );
                            return;
                        }
                    }
                }
            }
        );
    }

    private static boolean plantFourSaplings(
        ServerWorld world,
        PlayerEntity player,
        BlockPos base
    ) {
        BlockState sapling = Blocks.SPRUCE_SAPLING.getDefaultState();

        // Validate every planting position and its supporting ground
        // before changing any blocks.
        for (int dx = 0; dx < 2; dx++) {
            for (int dz = 0; dz < 2; dz++) {
                BlockPos plantPos = base.add(dx, 0, dz);

                if (!world.getBlockState(plantPos).isAir()) {
                    return false;
                }

                if (!sapling.canPlaceAt(world, plantPos)) {
                    return false;
                }
            }
        }

        // Plant all four saplings.
        for (int dx = 0; dx < 2; dx++) {
            for (int dz = 0; dz < 2; dz++) {
                BlockPos plantPos = base.add(dx, 0, dz);

                if (!world.setBlockState(
                    plantPos, sapling, Block.NOTIFY_ALL
                )) {
                    return false;
                }
            }
        }

        // Consume four saplings from the player's inventory.
        consumeSpruceSaplings(player, 4);
        return true;
    }

    private static int countSpruceSaplings(PlayerEntity player) {
        int count = 0;

        for (int i = 0; i < player.getInventory().size(); i++) {
            ItemStack stack = player.getInventory().getStack(i);

            if (stack.isOf(Items.SPRUCE_SAPLING)) {
                count += stack.getCount();
            }
        }

        return count;
    }

    private static void consumeSpruceSaplings(
        PlayerEntity player, int amount
    ) {
        for (int i = 0; i < player.getInventory().size(); i++) {
            ItemStack stack = player.getInventory().getStack(i);

            if (!stack.isOf(Items.SPRUCE_SAPLING)) {
                continue;
            }

            int taken = Math.min(amount, stack.getCount());
            stack.decrement(taken);
            amount -= taken;

            if (amount == 0) {
                return;
            }
        }
    }
}
