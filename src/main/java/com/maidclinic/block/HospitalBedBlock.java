package com.maidclinic.block;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionResult;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.phys.BlockHitResult;

/**
 * 病床：代码结构直接沿用原版 {@link BedBlock}（两格、朝向、OCCUPIED、重生点）。
 * <p>
 * 与原版床的区别：这里直接调用 {@link Player#startSleeping(BlockPos)}，
 * 不再检查“只能在夜晚/雷暴时睡觉”，所以生病女仆（以及玩家）白天也能躺一整天。
 */
public class HospitalBedBlock extends BedBlock {
    public HospitalBedBlock(DyeColor color, Properties properties) {
        super(color, properties);
    }

    /**
     * 不使用 {@code Minecraft:bed} 的方块实体（床单颜色/渲染由原版 BedRenderer 负责），
     * 病床的外观直接由 block model 提供。这样也能避免原版 BED 类型的合法方块校验。
     */
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return null;
    }

    /** 原版床靠 BedRenderer 渲染；病床不用方块实体，必须显式改用普通模型渲染。 */
    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hitResult) {
        if (level.isClientSide) {
            return InteractionResult.CONSUME;
        }
        // 与原版一致：从脚那格点击时，睡姿和占用判定都对齐到床头。
        BlockPos sleepPos = state.getValue(PART) == BedPart.FOOT
                ? pos.relative(state.getValue(FACING))
                : pos;
        BlockState headState = level.getBlockState(sleepPos);
        if (headState.getBlock() instanceof BedBlock && headState.getValue(OCCUPIED)) {
            player.displayClientMessage(Component.translatable("block.minecraft.bed.occupied"), true);
            return InteractionResult.CONSUME;
        }
        player.startSleeping(sleepPos);
        if (player instanceof ServerPlayer serverPlayer) {
            // 保留原版床的重生点功能。
            serverPlayer.setRespawnPosition(level.dimension(), sleepPos, 0.0F, false, true);
        }
        player.awardStat(Stats.SLEEP_IN_BED);
        return InteractionResult.CONSUME;
    }
}
