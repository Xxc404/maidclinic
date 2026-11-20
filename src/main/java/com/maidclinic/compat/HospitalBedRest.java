package com.maidclinic.compat;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.maidclinic.block.HospitalBedBlock;
import com.maidclinic.health.ClinicData;
import com.maidclinic.health.ClinicService;
import com.maidclinic.registry.ModEffects;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 病床住院状态：只在这里调用一次 startSleeping，避免和 TLM 的 MaidClearSleepTask
 * 每 tick 互抢睡姿；TLM 的清理任务由 mixin 在病床休息期间跳过。
 */
public final class HospitalBedRest {
    private static final int RETRY_TICKS = 20;
    private static final int MAX_RETRIES = 3;

    private static final Map<UUID, State> RESTING = new HashMap<>();
    private static final Map<UUID, Long> SUPPRESSED_UNTIL = new HashMap<>();

    private HospitalBedRest() {
    }

    public static boolean isResting(EntityMaid maid) {
        return RESTING.containsKey(maid.getUUID());
    }

    public static void tick(ServerLevel level, EntityMaid maid) {
        if (!maid.isAlive() || maid.isRemoved()) {
            forget(maid);
            return;
        }
        State state = RESTING.get(maid.getUUID());
        if (state == null) {
            if (maid.isSleeping()) {
                BlockPos bed = hospitalBedHeadAt(level, maid.getSleepingPos().orElse(null));
                if (bed != null && maid.getEffect(ModEffects.UNWELL) != null
                        && isIdle(maid) && !isForcedWork(level, maid)) {
                    RESTING.put(maid.getUUID(), new State(bed));
                    if (ClinicService.isMaidChild(maid)) {
                        ClinicService.holdInHospitalBed(maid, bed);
                    }
                    ClinicService.holdSleepHealth(maid, maid.getHealth());
                }
                return;
            }
            if (!canStart(level, maid)) {
                return;
            }
            start(level, maid);
            return;
        }

        if (!canContinue(level, maid, state)) {
            stop(level, maid);
            return;
        }
        if (ClinicService.isMaidChild(maid)) {
            ClinicService.holdInHospitalBed(maid, state.bed);
        }
        if (!maid.isSleeping()) {
            long now = level.getGameTime();
            if (state.retries >= MAX_RETRIES) {
                stop(level, maid);
                return;
            }
            if (now >= state.retryAt) {
                state.retries++;
                state.retryAt = now + RETRY_TICKS;
                float before = maid.getHealth();
                maid.getNavigation().stop();
                maid.startSleeping(state.bed);
                if (maid.isSleeping()) {
                    ClinicService.holdSleepHealth(maid, before);
                }
            }
        }
    }

    public static void forget(EntityMaid maid) {
        if (RESTING.remove(maid.getUUID()) != null) {
            ClinicService.releaseHospitalBed(maid);
            ClinicService.releaseSleepHealth(maid);
        }
    }

    /** 玩家主动把后代从病床抱走后，短时间内不要再自动躺回去。 */
    public static void suppress(EntityMaid maid, ServerLevel level, int ticks) {
        SUPPRESSED_UNTIL.put(maid.getUUID(), level.getGameTime() + ticks);
    }

    private static void start(ServerLevel level, EntityMaid maid) {
        BlockPos bed = findHospitalBedHead(level, maid.blockPosition());
        if (bed == null || isBedOccupied(level, bed)) {
            return;
        }
        State state = new State(bed);
        RESTING.put(maid.getUUID(), state);
        float before = maid.getHealth();
        maid.getNavigation().stop();
        maid.startSleeping(bed);
        if (maid.isSleeping()) {
            if (ClinicService.isMaidChild(maid)) {
                ClinicService.holdInHospitalBed(maid, bed);
            }
            ClinicService.holdSleepHealth(maid, before);
        } else {
            RESTING.remove(maid.getUUID());
            ClinicService.releaseHospitalBed(maid);
            ClinicService.releaseSleepHealth(maid);
        }
    }

    private static void stop(ServerLevel level, EntityMaid maid) {
        RESTING.remove(maid.getUUID());
        ClinicService.releaseHospitalBed(maid);
        ClinicService.releaseSleepHealth(maid);
        if (maid.isAlive() && maid.isSleeping()) {
            maid.stopSleeping();
        }
    }

    private static boolean canStart(ServerLevel level, EntityMaid maid) {
        if (maid.isSleeping() || maid.getEffect(ModEffects.UNWELL) == null || !isIdle(maid)
                || ClinicService.insomniaRecord(level, maid) != null) {
            return false;
        }
        Long until = SUPPRESSED_UNTIL.get(maid.getUUID());
        if (until != null) {
            if (level.getGameTime() < until) {
                return false;
            }
            SUPPRESSED_UNTIL.remove(maid.getUUID());
        }
        if (isForcedWork(level, maid)) {
            return false;
        }
        return findHospitalBedHead(level, maid.blockPosition()) != null;
    }

    private static boolean canContinue(ServerLevel level, EntityMaid maid, State state) {
        if (maid.getEffect(ModEffects.UNWELL) == null || !isIdle(maid)
                || isForcedWork(level, maid) || maid.isPassenger()
                || ClinicService.insomniaRecord(level, maid) != null) {
            return false;
        }
        return level.getBlockState(state.bed).getBlock() instanceof HospitalBedBlock;
    }

    private static boolean isForcedWork(ServerLevel level, EntityMaid maid) {
        var record = ClinicData.get(level).find(maid.getUUID());
        return record != null && record.forcedWork();
    }

    private static boolean isIdle(EntityMaid maid) {
        var task = maid.getTask();
        return task == null || task.getUid() == null || "idle".equals(task.getUid().getPath());
    }

    private static BlockPos findHospitalBedHead(ServerLevel level, BlockPos origin) {
        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-2, -1, -2), origin.offset(2, 1, 2))) {
            BlockState state = level.getBlockState(pos);
            if (!(state.getBlock() instanceof HospitalBedBlock)) {
                continue;
            }
            return state.getValue(BedBlock.PART) == BedPart.FOOT
                    ? pos.relative(state.getValue(BedBlock.FACING)).immutable()
                    : pos.immutable();
        }
        return null;
    }

    private static BlockPos hospitalBedHeadAt(ServerLevel level, BlockPos pos) {
        if (pos == null) {
            return null;
        }
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof HospitalBedBlock)) {
            return null;
        }
        return state.getValue(BedBlock.PART) == BedPart.FOOT
                ? pos.relative(state.getValue(BedBlock.FACING)).immutable()
                : pos.immutable();
    }

    private static boolean isBedOccupied(ServerLevel level, BlockPos bedHead) {
        BlockState headState = level.getBlockState(bedHead);
        if (!(headState.getBlock() instanceof HospitalBedBlock)) {
            return true;
        }
        if (headState.getValue(BedBlock.OCCUPIED)) {
            return true;
        }
        BlockPos foot = bedHead.relative(headState.getValue(BedBlock.FACING).getOpposite());
        BlockState footState = level.getBlockState(foot);
        return footState.getBlock() instanceof HospitalBedBlock
                && footState.getValue(BedBlock.OCCUPIED);
    }

    private static final class State {
        private final BlockPos bed;
        private int retries;
        private long retryAt;

        private State(BlockPos bed) {
            this.bed = bed;
        }
    }
}
