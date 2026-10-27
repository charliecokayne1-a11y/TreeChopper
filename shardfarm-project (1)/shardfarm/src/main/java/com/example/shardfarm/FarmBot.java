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
 * Loop: plant a 2x2 of saplings -> bone meal one until it grows -> break the bottom log -> repeat.
 */
public final class FarmBot {
    private FarmBot() {}

    private enum State { PLANT, BONEMEAL, CHOP }

    /** Ticks to wait after breaking the bottom log (20 ticks = 1 second). */
    private static final int AFTER_CHOP_WAIT_TICKS = 40;

    private static boolean running;
    private static State state = State.PLANT;
    private static final BlockPos[] groundPos = new BlockPos[4];
    private static final BlockPos[] plantPos = new BlockPos[4];
    private static final int[] plantAttempts = new int[4];
    private static BlockPos chopTarget;
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
            msg(c, "Aim at the near-left ground block of the 2x2, then start");
            return;
        }
        BlockPos looked = ((BlockHitResult) target).getBlockPos();
        BlockPos ground = c.world.getBlockState(looked).isIn(BlockTags.SAPLINGS) ? looked.down() : looked;

        // The 2x2 extends forward and to the right of the aimed block.
        Direction forward = c.player.getHorizontalFacing();
        Direction right = forward.rotateYClockwise();
        groundPos[0] = ground;
        groundPos[1] = ground.offset(forward);
        groundPos[2] = ground.offset(right);
        groundPos[3] = ground.offset(forward).offset(right);
        for (int i = 0; i < 4; i++) {
            plantPos[i] = groundPos[i].up();
            plantAttempts[i] = 0;
        }

        state = State.PLANT;
        chopTarget = null;
        cooldown = 0;
        idleTicks = 0;
        boneMealUses = 0;
        running = true;
        msg(c, "Farming started");
    }

    public static void stop(MinecraftClient c, String reason) {
        running = false;
        chopTarget = null;
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

        // If a tree is still standing here, wait for it to fall.
        for (int i = 0; i < 4; i++) {
            if (isLog(c, plantPos[i])) {
                if (++idleTicks > 100) {
                    stop(c, "Logs are still standing - is your axe a tree feller?");
                }
                return;
            }
        }
        idleTicks = 0;

        for (int i = 0; i < 4; i++) {
            BlockState s = c.world.getBlockState(plantPos[i]);
            if (s.isIn(BlockTags.SAPLINGS)) continue;
            if (!s.isAir()) {
                stop(c, "Plant spot " + (i + 1) + " is blocked");
                return;
            }
            if (++plantAttempts[i] > 6) {
                stop(c, "Can't place a sapling at spot " + (i + 1) + " (bad ground?)");
                return;
            }

            int slot = findHotbar(p, st -> st.isIn(ItemTags.SAPLINGS));
            if (slot < 0) {
                stop(c, "No saplings in your hotbar");
                return;
            }
            p.getInventory().setSelectedSlot(slot);

            Vec3d hitPos = new Vec3d(plantPos[i].getX() + 0.5, plantPos[i].getY(), plantPos[i].getZ() + 0.5);
            BlockHitResult hit = new BlockHitResult(hitPos, Direction.UP, groundPos[i], false);
            c.interactionManager.interactBlock(p, Hand.MAIN_HAND, hit);
            p.swingHand(Hand.MAIN_HAND);
            cooldown = FarmConfig.actionDelayTicks;
            return;
        }

        // All four saplings are in place.
        for (int i = 0; i < 4; i++) plantAttempts[i] = 0;
        boneMealUses = 0;
        state = State.BONEMEAL;
    }

    private static void boneMeal(MinecraftClient c) {
        ClientPlayerEntity p = c.player;

        for (int i = 0; i < 4; i++) {
            if (isLog(c, plantPos[i])) {
                chopTarget = null;
                idleTicks = 0;
                state = State.CHOP;
                return;
            }
        }

        BlockPos sapling = null;
        for (int i = 0; i < 4; i++) {
            if (c.world.getBlockState(plantPos[i]).isIn(BlockTags.SAPLINGS)) {
                sapling = plantPos[i];
                break;
            }
        }
        if (sapling == null) {
            state = State.PLANT;
            return;
        }
        if (boneMealUses >= FarmConfig.maxBoneMealPerSapling) {
            stop(c, "Tree didn't grow (not enough space above?)");
            return;
        }

        int slot = findHotbar(p, st -> st.isOf(Items.BONE_MEAL));
        if (slot < 0) {
            stop(c, "No bone meal in your hotbar");
            return;
        }
        p.getInventory().setSelectedSlot(slot);

        BlockHitResult hit = new BlockHitResult(Vec3d.ofCenter(sapling), Direction.UP, sapling, false);
        c.interactionManager.interactBlock(p, Hand.MAIN_HAND, hit);
        p.swingHand(Hand.MAIN_HAND);
        boneMealUses++;
        cooldown = FarmConfig.actionDelayTicks;
    }

    private static void chop(MinecraftClient c) {
        ClientPlayerEntity p = c.player;

        if (chopTarget == null) {
            chopTarget = findBottomLogInReach(c);
            if (chopTarget == null) {
                boolean anyLog = false;
                for (int i = 0; i < 4; i++) {
                    if (isLog(c, plantPos[i])) anyLog = true;
                }
                if (anyLog) {
                    if (++idleTicks > 60) stop(c, "Bottom log is out of reach - stand closer");
                    return;
                }
                idleTicks = 0;
                cooldown = AFTER_CHOP_WAIT_TICKS;
                state = State.PLANT;
                return;
            }
        }

        // Bottom log is gone (broken or felled): wait a moment, then replant.
        if (!isLog(c, chopTarget)) {
            chopTarget = null;
            idleTicks = 0;
            cooldown = AFTER_CHOP_WAIT_TICKS;
            state = State.PLANT;
            return;
        }

        int axe = findHotbar(p, FarmBot::isShardAxe);
        if (axe < 0) {
            stop(c, "No matching axe in your hotbar");
            return;
        }
        p.getInventory().setSelectedSlot(axe);

        Vec3d eye = p.getEyePos();
        Vec3d center = Vec3d.ofCenter(chopTarget);
        Direction face = Direction.getFacing(eye.x - center.x, eye.y - center.y, eye.z - center.z);

        // Keeps calling this each tick until the block breaks.
        c.interactionManager.updateBlockBreakingProgress(chopTarget, face);
        p.swingHand(Hand.MAIN_HAND);
    }

    // ---- helpers ---------------------------------------------------------

    private static boolean isLog(MinecraftClient c, BlockPos pos) {
        return c.world.getBlockState(pos).isIn(BlockTags.LOGS);
    }

    /** One of the four trunk-base positions that holds a log and is within reach. */
    private static BlockPos findBottomLogInReach(MinecraftClient c) {
        ClientPlayerEntity p = c.player;
        Vec3d eye = p.getEyePos();
        double reach = p.getBlockInteractionRange() - 0.5;
        double reachSq = reach * reach;

        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        for (int i = 0; i < 4; i++) {
            if (!isLog(c, plantPos[i])) continue;
            double d = eye.squaredDistanceTo(Vec3d.ofCenter(plantPos[i]));
            if (d <= reachSq && d < bestDist) {
                bestDist = d;
                best = plantPos[i];
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
