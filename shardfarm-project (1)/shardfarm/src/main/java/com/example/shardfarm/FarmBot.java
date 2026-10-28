
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

public final class FarmBot {
    private FarmBot() {}

    private enum State {
        PLANT, BONEMEAL, CHOP
    }

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
        if (c.player == null || c.world == null
                || c.interactionManager == null) {
            return;
        }

        HitResult target = c.crosshairTarget;

        if (target == null
                || target.getType() != HitResult.Type.BLOCK) {
            msg(c, "Aim at the bottom-left ground block of the 2x2, then start");
            return;
        }

        BlockPos looked = ((BlockHitResult) target).getBlockPos();

        BlockPos ground = c.world.getBlockState(looked)
                .isIn(BlockTags.SAPLINGS) ? looked.down() : looked;

        // Read the direction selected in the FarmConfig setting.
        Direction forward = FarmConfig.farmFacing;
        Direction right = forward.rotateYClockwise();
        Direction back = forward.getOpposite();

        // Start at the bottom-left block from the selected viewing direction.
        // Order: top-left, top-right, bottom-right, bottom-left.
        groundPos[0] = ground.offset(back);
        groundPos[1] = ground.offset(back).offset(right);
        groundPos[2] = ground.offset(right);
        groundPos[3] = ground;

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

        msg(c, "Farming started. Direction: " + forward);
    }

    public static void stop(MinecraftClient c, String reason) {
        running = false;
        chopTarget = null;
        msg(c, reason);
    }

    public static void tick(MinecraftClient c) {
        if (!running) return;

        if (c.player == null || c.world == null
                || c.interactionManager == null) {
            running = false;
            return;
        }

        if (c.currentScreen != null) return;

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

    private static void plant(MinecraftClient c) {
        ClientPlayerEntity p = c.player;

        for (int i = 0; i < 4; i++) {
            if (isLog(c, plantPos[i])) {
                if (++idleTicks > 100) {
                    stop(c, "Clear the tree base first");
                }
                return;
            }
        }

        idleTicks = 0;

        for (int i = 0; i < 4; i++) {
            BlockState block = c.world.getBlockState(plantPos[i]);

            if (block.isIn(BlockTags.SAPLINGS)) continue;

            if (!block.isAir()) {
                stop(c, "Plant spot " + (i + 1) + " is blocked");
                return;
            }

            if (++plantAttempts[i] > 6) {
                stop(c, "Cannot place sapling at spot " + (i + 1));
                return;
            }

            int slot = findHotbar(p,
                    stack -> stack.isIn(ItemTags.SAPLINGS));

            if (slot < 0) {
                stop(c, "No saplings in your hotbar");
                return;
            }

            p.getInventory().setSelectedSlot(slot);

            Vec3d hitPos = new Vec3d(
                    groundPos[i].getX() + 0.5,
                    groundPos[i].getY() + 1.0,
                    groundPos[i].getZ() + 0.5
            );

            aimAt(p, hitPos);

            BlockHitResult hit = new BlockHitResult(
                    hitPos, Direction.UP, groundPos[i], false);

            c.interactionManager.interactBlock(
                    p, Hand.MAIN_HAND, hit);
            p.swingHand(Hand.MAIN_HAND);

            cooldown = FarmConfig.actionDelayTicks;
            return;
        }

        for (int i = 0; i < 4; i++) {
            plantAttempts[i] = 0;
        }

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
            if (c.world.getBlockState(plantPos[i])
                    .isIn(BlockTags.SAPLINGS)) {
                sapling = plantPos[i];
                break;
            }
        }

        if (sapling == null) {
            state = State.PLANT;
            return;
        }

        if (boneMealUses >= FarmConfig.maxBoneMealPerSapling) {
            stop(c, "Tree did not grow; check the space above it");
            return;
        }

        int slot = findHotbar(p,
                stack -> stack.isOf(Items.BONE_MEAL));

        if (slot < 0) {
            stop(c, "No bone meal in your hotbar");
            return;
        }

        p.getInventory().setSelectedSlot(slot);

        Vec3d hitPos = Vec3d.ofCenter(sapling);
        aimAt(p, hitPos);

        BlockHitResult hit = new BlockHitResult(
                hitPos, Direction.UP, sapling, false);

        c.interactionManager.interactBlock(
                p, Hand.MAIN_HAND, hit);
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
                    if (++idleTicks > 60) {
                        stop(c, "Bottom log out of reach; stand closer");
                    }
                    return;
                }

                idleTicks = 0;
                cooldown = AFTER_CHOP_WAIT_TICKS;
                state = State.PLANT;
                return;
            }
        }

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

        Vec3d center = Vec3d.ofCenter(chopTarget);
        aimAt(p, center);

        Vec3d eye = p.getEyePos();

        Direction face = Direction.getFacing(
                eye.x - center.x,
                eye.y - center.y,
                eye.z - center.z
        );

        c.interactionManager.updateBlockBreakingProgress(
                chopTarget, face);
        p.swingHand(Hand.MAIN_HAND);
    }

    private static void aimAt(
            ClientPlayerEntity player, Vec3d target) {
        Vec3d eye = player.getEyePos();

        double dx = target.x - eye.x;
        double dy = target.y - eye.y;
        double dz = target.z - eye.z;

        double horizontal = Math.sqrt(dx * dx + dz * dz);

        float yaw = (float) Math.toDegrees(
                Math.atan2(dz, dx)) - 90.0F;

        float pitch = (float) -Math.toDegrees(
                Math.atan2(dy, horizontal));

        player.setYaw(yaw);
        player.setPitch(pitch);
    }

    private static boolean isLog(MinecraftClient c, BlockPos pos) {
        return c.world.getBlockState(pos).isIn(BlockTags.LOGS);
    }

    private static BlockPos findBottomLogInReach(MinecraftClient c) {
        ClientPlayerEntity p = c.player;

        Vec3d eye = p.getEyePos();
        double reach = p.getBlockInteractionRange() - 0.5;
        double reachSq = reach * reach;

        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;

        for (int i = 0; i < 4; i++) {
            if (!isLog(c, plantPos[i])) continue;

            double distance = eye.squaredDistanceTo(
                    Vec3d.ofCenter(plantPos[i]));

            if (distance <= reachSq && distance < bestDist) {
                bestDist = distance;
                best = plantPos[i];
            }
        }

        return best;
    }

    private static boolean isShardAxe(ItemStack stack) {
        if (!stack.isIn(ItemTags.AXES)) return false;

        if (!FarmConfig.requireNamedAxe) return true;

        String name = stack.getName().getString()
                .toLowerCase(Locale.ROOT);

        return name.contains(
                FarmConfig.axeNameContains.toLowerCase(Locale.ROOT));
    }

    private static int findHotbar(
            ClientPlayerEntity player,
            Predicate<ItemStack> test) {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = player.getInventory().getStack(i);

            if (!stack.isEmpty() && test.test(stack)) {
                return i;
            }
        }

        return -1;
    }

    private static void msg(MinecraftClient c, String text) {
        if (c.player != null) {
            c.player.sendMessage(
                    Text.literal("[Shard Farm] " + text), true);
        }
    }
}
