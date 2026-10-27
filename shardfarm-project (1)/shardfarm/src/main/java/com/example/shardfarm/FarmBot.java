package com.example.shardfarm;

import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.Locale;
import java.util.function.Predicate;

/**
 * Loop: plant sapling -> bone meal until it grows -> chop logs in reach -> repeat.
 * Everything runs on the client tick, one action per configured delay.
 */
public final class FarmBot {
    private FarmBot() {}

    private enum State { PLANT, BONEMEAL, CHOP }

    private static boolean running;
    private static State state = State.PLANT;
    private static BlockPos groundPos;
    private static BlockPos plantPos;
    private static int cooldown;
    private static int idleTicks;
    private static int boneMealUses;

    public static boolean isRunning() {
        return running;
    }

    public static void toggle(MinecraftClient c) {
        if (running) {
            stop(c, "Farming stopped");
        } else {
            start(c);
        }
    }

    private static void start(MinecraftClient c) {
        if (c.player == null || c.world == null || c.interactionManager == null) return;

        HitResult target = c.crosshairTarget;
        if (target == null || target.getType() != HitResult.Type.BLOCK) {
            msg(c, "Aim at the block you want to plant on, then start");
            return;
        }
        BlockHitResult hit = (BlockHitResult) target;
        BlockPos looked = hit.getBlockPos();

        if (c.world.getBlockState(looked).isIn(BlockTags.SAPLINGS)) {
            plantPos = looked;
            groundPos = looked.down();
        } else {
            groundPos = looked;
            plantPos = looked.up();
        }

        state = State.PLANT;
        cooldown = 0;
        idleTicks = 0;
        boneMealUses = 0;
        running = true;
        msg(c, "Farming started");
    }

    public static void stop(MinecraftClient c, String reason) {
        running = false;
        msg(c, reason);
    }

    public static void tick(MinecraftClient c) {
        if (!running) return;
        if (c.player == null || c.world == null || c.interactionManager == null) {
            running = false;
            return;
        }
        if (c.currentScreen != null) return; // pause while a menu is open
        if (cooldown > 0) {
            cooldown--;
            return;
        }
        switch (state) {
            case PLANT -> plant(c);
            case BONEMEAL -> boneMeal(c);
            case CHOP -> chop(c);
        }
    }

    // ---- steps -----------------------------------------------------------

    private static void plant(MinecraftClient c) {
        ClientPlayerEntity p = c.player;
        BlockState s = c.world.getBlockState(plantPos);

        if (s.isIn(BlockTags.SAPLINGS)) {
            state = State.BONEMEAL;
            boneMealUses = 0;
            return;
        }
        if (s.isIn(BlockTags.LOGS)) {
            state = State.CHOP;
            idleTicks = 0;
            return;
        }
        if (!s.isAir()) {
            stop(c, "Plant spot is blocked");
            return;
        }

        int slot = findHotbar(p, st -> st.isIn(ItemTags.SAPLINGS));
        if (slot < 0) {
            stop(c, "No saplings in your hotbar");
            return;
        }
        p.getInventory().setSelectedSlot(slot);

        Vec3d hitPos = new Vec3d(plantPos.getX() + 0.5, plantPos.getY(), plantPos.getZ() + 0.5);
        BlockHitResult hit = new BlockHitResult(hitPos, Direction.UP, groundPos, false);
        c.interactionManager.interactBlock(p, Hand.MAIN_HAND, hit);
        p.swingHand(Hand.MAIN_HAND);
        cooldown = FarmConfig.actionDelayTicks;
    }

    private static void boneMeal(MinecraftClient c) {
        ClientPlayerEntity p = c.player;
        BlockState s = c.world.getBlockState(plantPos);

        if (s.isIn(BlockTags.LOGS)) {
            state = State.CHOP;
            idleTicks = 0;
            return;
        }
        if (!s.isIn(BlockTags.SAPLINGS)) {
            state = State.PLANT;
            return;
        }
        if (boneMealUses >= FarmConfig.maxBoneMealPerSapling) {
            stop(c, "Sapling didn't grow (blocked by something above it?)");
            return;
        }

        int slot = findHotbar(p, st -> st.isOf(Items.BONE_MEAL));
        if (slot < 0) {
            stop(c, "No bone meal in your hotbar");
            return;
        }
        p.getInventory().setSelectedSlot(slot);

        BlockHitResult hit = new BlockHitResult(Vec3d.ofCenter(plantPos), Direction.UP, plantPos, false);
        c.interactionManager.interactBlock(p, Hand.MAIN_HAND, hit);
        p.swingHand(Hand.MAIN_HAND);
        boneMealUses++;
        cooldown = FarmConfig.actionDelayTicks;
    }

    private static void chop(MinecraftClient c) {
        ClientPlayerEntity p = c.player;

        int axe = findHotbar(p, FarmBot::isShardAxe);
        if (axe < 0) {
            stop(c, "No matching axe in your hotbar");
            return;
        }

        BlockPos log = nearestLog(c);
        if (log == null) {
            // No logs left in reach; after a short pause start the next sapling.
            if (++idleTicks > 10) {
                idleTicks = 0;
                state = State.PLANT;
            }
            return;
        }
        idleTicks = 0;

        p.getInventory().setSelectedSlot(axe);
        Vec3d eye = p.getEyePos();
        Vec3d center = Vec3d.ofCenter(log);
        Direction face = Direction.getFacing(eye.x - center.x, eye.y - center.y, eye.z - center.z);

        // Keeps calling this each tick until the block breaks.
        c.interactionManager.updateBlockBreakingProgress(log, face);
        p.swingHand(Hand.MAIN_HAND);
    }

    // ---- helpers ---------------------------------------------------------

    private static BlockPos nearestLog(MinecraftClient c) {
        ClientPlayerEntity p = c.player;
        Vec3d eye = p.getEyePos();
        double reach = p.getBlockInteractionRange() - 0.5;
        double reachSq = reach * reach;

        BlockPos best = null;
        double bestScore = Double.MAX_VALUE;

        for (int dx = -4; dx <= 4; dx++) {
            for (int dy = 0; dy <= 10; dy++) {
                for (int dz = -4; dz <= 4; dz++) {
                    BlockPos pos = plantPos.add(dx, dy, dz);
                    if (!c.world.getBlockState(pos).isIn(BlockTags.LOGS)) continue;
                    double distSq = eye.squaredDistanceTo(Vec3d.ofCenter(pos));
                    if (distSq > reachSq) continue;
                    // Lowest logs first, then closest.
                    double score = pos.getY() * 1000.0 + distSq;
                    if (score < bestScore) {
                        bestScore = score;
                        best = pos;
                    }
                }
            }
        }
        return best;
    }

    private static boolean isShardAxe(ItemStack st) {
        if (!st.isIn(ItemTags.AXES)) return false;
        if (!FarmConfig.requireNamedAxe) return true;
        String name = st.getName().getString().toLowerCase(Locale.ROOT);
        return name.contains(FarmConfig.axeNameContains.toLowerCase(Locale.ROOT));
    }

    private static int findHotbar(ClientPlayerEntity p, Predicate<ItemStack> test) {
        for (int i = 0; i < 9; i++) {
            ItemStack st = p.getInventory().getStack(i);
            if (!st.isEmpty() && test.test(st)) return i;
        }
        return -1;
    }

    private static void msg(MinecraftClient c, String text) {
        if (c.player != null) {
            c.player.sendMessage(Text.literal("[Shard Farm] " + text), true);
        }
    }
}
