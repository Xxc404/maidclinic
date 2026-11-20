package com.maidclinic.block;

import com.maidclinic.terminal.TerminalHub;
import com.maidclinic.util.Msg;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;

import java.util.List;

/**
 * 智能终端：右键查看所有绑定病床上女仆的完整健康档案；潜行右键绑定最近的未绑定病床。
 * <p>每个终端最多绑定 {@link TerminalData#MAX_BEDS} 张病床，绑定关系存进存档级 {@link TerminalData}。</p>
 * <p>带水平朝向，正面（北面）是屏幕，其余面是白色机壳。</p>
 */
public class SmartTerminalBlock extends Block {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    private static final int SEARCH_RADIUS = 8;

    public SmartTerminalBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hitResult) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (!(level instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.CONSUME;
        }
        TerminalData data = TerminalData.get(serverLevel);
        List<BlockPos> beds = data.bedsFor(pos);
        if (player.isShiftKeyDown()) {
            if (beds.size() >= TerminalData.MAX_BEDS) {
                Msg.bar(player, Msg.red("message.maidclinic.terminal.full", TerminalData.MAX_BEDS));
                return InteractionResult.CONSUME;
            }
            BlockPos bed = findNearestUnboundBed(level, pos, beds);
            if (bed == null) {
                Msg.bar(player, Msg.red(beds.isEmpty()
                        ? "message.maidclinic.terminal.no_bed"
                        : "message.maidclinic.terminal.already_bound"));
                return InteractionResult.CONSUME;
            }
            switch (data.bind(pos, bed)) {
                case ADDED -> Msg.chat(player, Msg.green("message.maidclinic.terminal.bound",
                        bed.getX(), bed.getY(), bed.getZ()));
                case ALREADY_BOUND -> Msg.bar(player,
                        Msg.yellow("message.maidclinic.terminal.already_bound"));
                case FULL -> Msg.bar(player,
                        Msg.red("message.maidclinic.terminal.full", TerminalData.MAX_BEDS));
            }
            return InteractionResult.CONSUME;
        }
        if (beds.isEmpty()) {
            Msg.chat(player, Msg.yellow("message.maidclinic.terminal.unbound"));
            return InteractionResult.CONSUME;
        }
        TerminalHub.open(serverPlayer, pos);
        return InteractionResult.CONSUME;
    }

    private static BlockPos findNearestUnboundBed(Level level, BlockPos origin, List<BlockPos> bound) {
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (BlockPos pos : BlockPos.betweenClosed(
                origin.offset(-SEARCH_RADIUS, -3, -SEARCH_RADIUS),
                origin.offset(SEARCH_RADIUS, 3, SEARCH_RADIUS))) {
            BlockState state = level.getBlockState(pos);
            if (!(state.getBlock() instanceof HospitalBedBlock)) {
                continue;
            }
            BlockPos bed = normalizeBedPos(state, pos);
            if (isBound(bound, bed, state)) {
                continue;
            }
            double distance = bed.distToCenterSqr(origin.getX() + 0.5D, origin.getY() + 0.5D, origin.getZ() + 0.5D);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = bed;
            }
        }
        return best;
    }

    /** 同一张床的头/脚只算一张：统一存床头坐标。 */
    private static BlockPos normalizeBedPos(BlockState state, BlockPos pos) {
        if (state.getValue(BedBlock.PART) == BedPart.FOOT) {
            return pos.relative(state.getValue(BedBlock.FACING)).immutable();
        }
        return pos.immutable();
    }

    /** 兼容旧存档里按脚格保存的绑定：只比较同一张床的另一半，不会误伤相邻的床。 */
    private static boolean isBound(List<BlockPos> bound, BlockPos bedHead, BlockState state) {
        BlockPos foot = bedHead.relative(state.getValue(BedBlock.FACING).getOpposite());
        return bound.contains(bedHead) || bound.contains(foot);
    }
}
