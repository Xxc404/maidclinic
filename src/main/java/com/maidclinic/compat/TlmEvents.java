package com.maidclinic.compat;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.api.event.MaidDeathEvent;
import com.maidclinic.ClinicSettings;
import com.maidclinic.block.HospitalBedBlock;
import com.maidclinic.block.TerminalData;
import com.maidclinic.health.ClinicData;
import com.maidclinic.health.ClinicService;
import com.maidclinic.health.SpiritAttribution;
import com.maidclinic.registry.ModEffects;
import com.maidclinic.terminal.TerminalHub;
import com.maidclinic.terminal.TerminalState;
import com.maidclinic.util.Msg;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.ICancellableEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityMountEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.util.List;

/**
 * 所有与 TLM 有关的事件监听。
 * <p>
 * 注意：本类只能由 {@link TlmSupport#bootstrap()} 在确认 TLM 存在后加载，
 * 否则会在 TLM 缺失时触发 {@link NoClassDefFoundError}。
 */
public final class TlmEvents {
    private TlmEvents() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(TlmEvents::onEntityInteract);
        NeoForge.EVENT_BUS.addListener(TlmEvents::onItemFinished);
        NeoForge.EVENT_BUS.addListener(TlmEvents::onMaidDeath);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, ClinicService::onLivingDeath);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, ClinicService::onLivingDeathMessage);
        // 灵体归因：爆炸/岩浆/摔落等“结构性”责任收集，供 ClinicService 判定是否拦截灵体。
        SpiritAttribution.register();
        NeoForge.EVENT_BUS.addListener(TlmEvents::onEntityMount);
        NeoForge.EVENT_BUS.addListener(TlmEvents::onLevelTick);
        NeoForge.EVENT_BUS.addListener(TlmEvents::onEntityTickPre);
        NeoForge.EVENT_BUS.addListener(TlmEvents::onEntityTick);
        NeoForge.EVENT_BUS.addListener(ClinicService::onEffectApplicable);
        TerminalHub.setProvider(TlmEvents::buildTerminalState);
    }

    /** 智能终端界面快照：只放入不随时间线性变化的字段，避免每 tick 推送。 */
    private static TerminalState buildTerminalState(ServerPlayer player, BlockPos terminal, BlockPos selected) {
        ServerLevel level = (ServerLevel) player.level();
        List<TerminalState.BedSummary> beds = new java.util.ArrayList<>();
        for (BlockPos bed : TerminalData.get(level).bedsFor(terminal)) {
            boolean valid = level.getBlockState(bed).getBlock() instanceof HospitalBedBlock;
            EntityMaid maid = valid ? findSleeper(level, bed, null) : null;
            int color = statusColor(level, maid, valid);
            beds.add(new TerminalState.BedSummary(bed,
                    maid == null ? "" : maid.getName().getString(),
                    color, maid != null, valid));
        }
        List<Component> detail = List.of();
        boolean ready = selected != null;
        if (ready) {
            EntityMaid occupant = findSleeper(level, selected, null);
            if (occupant != null) {
                detail = ClinicService.buildReportLines(occupant, false);
            }
        }
        return new TerminalState(terminal, beds, selected, ready, detail);
    }

    private static int statusColor(ServerLevel level, EntityMaid maid, boolean valid) {
        if (!valid) {
            return 0xFF555555;
        }
        if (maid == null) {
            return 0xFFAAAAAA;
        }
        var record = ClinicData.get(level).find(maid.getUUID());
        if (record == null || record.conditions().isEmpty()) {
            return 0xFF55FF55;
        }
        for (var active : record.conditions()) {
            if (active.severity() >= 3) {
                return 0xFFFF5555;
            }
        }
        return 0xFFFFFF55;
    }

    /** 智能终端：找到绑定病床上躺着的女仆，输出完整档案。 */
    public static void reportBed(ServerPlayer player, ServerLevel level, BlockPos bedPos) {
        EntityMaid occupant = findSleeper(level, bedPos, null);
        if (occupant == null) {
            Msg.chat(player, Msg.gray("message.maidclinic.terminal.empty",
                    bedPos.getX(), bedPos.getY(), bedPos.getZ()));
            return;
        }
        Msg.chat(player, Msg.aqua("message.maidclinic.terminal.header",
                occupant.getName().getString(), bedPos.getX(), bedPos.getY(), bedPos.getZ()));
        ClinicService.reportHealth(player, occupant);
    }

    private static EntityMaid findSleeper(ServerLevel level, BlockPos bedPos, EntityMaid exclude) {
        AABB area = new AABB(bedPos.getX(), bedPos.getY(), bedPos.getZ(),
                bedPos.getX() + 1.0D, bedPos.getY() + 1.0D, bedPos.getZ() + 1.0D).inflate(8.0D);
        for (EntityMaid maid : level.getEntitiesOfClass(EntityMaid.class, area)) {
            if (maid == exclude || !maid.isAlive() || maid.isRemoved()) {
                continue;
            }
            var sleepingPos = maid.getSleepingPos();
            if (sleepingPos.isPresent() && isSameBed(level, bedPos, sleepingPos.get())) {
                return maid;
            }
        }
        return null;
    }

    /** 头/脚两格算同一张床；相邻的另一张床不算。 */
    private static boolean isSameBed(ServerLevel level, BlockPos bedPos, BlockPos sleepingPos) {
        if (bedPos.distManhattan(sleepingPos) > 1) {
            return false;
        }
        BlockState state = level.getBlockState(bedPos);
        if (!(state.getBlock() instanceof HospitalBedBlock)) {
            return false;
        }
        BlockPos otherPart = state.getValue(BedBlock.PART) == BedPart.HEAD
                ? bedPos.relative(state.getValue(BedBlock.FACING).getOpposite())
                : bedPos.relative(state.getValue(BedBlock.FACING));
        return sleepingPos.equals(bedPos) || sleepingPos.equals(otherPart);
    }

    /** 病床休息必须在 TLM 的 brain tick 之前决定，mixin 才能在 MaidClearSleepTask 里看到状态。 */
    private static void onEntityTickPre(EntityTickEvent.Pre event) {
        if (!(event.getEntity() instanceof EntityMaid maid)) {
            return;
        }
        if (maid.level() instanceof ServerLevel level) {
            HospitalBedRest.tick(level, maid);
        }
    }

    /**
     * 失眠女仆的入睡处理：每 tick 在实体 tick（含 TLM 的入睡 AI）之后交给诊所，
     * 由它按 50/50 抽取“强行睁眼”或“睡不着”两种互斥方案。
     */
    private static void onEntityTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof EntityMaid maid)) {
            return;
        }
        if (maid.level() instanceof ServerLevel level) {
            ClinicService.updateSleepSpot(maid);
            ClinicService.maintainSickSleep(maid);
            ClinicService.handleInsomniaSleep(level, maid);
            // 每 tick 锁一次睡眠表情：第三方动画写入的 chijing 最多显示 1 tick
            SickExpressions.lockSleeping(maid);
        }
    }

    /**
     * 住院后代不能被母亲/看护女仆抱走。
     * <p>《心婚誓约》的婴儿每 tick 都会尝试骑到母亲身上；病床强制休息期间如果
     * 不拦截，就会出现“病床 ↔ 母亲怀里”每 tick 来回跳。玩家主动抱走视为出院，
     * 这里会先解除卧床锁，允许玩家接管。</p>
     */
    private static void onEntityMount(EntityMountEvent event) {
        if (event.isCanceled() || !event.isMounting() || event.getLevel().isClientSide()) {
            return;
        }
        if (!(event.getEntityMounting() instanceof EntityMaid infant)) {
            return;
        }
        if (!ClinicService.isMaidChild(infant) || !ClinicService.isHeldInHospitalBed(infant)) {
            return;
        }
        if (event.getEntityBeingMounted() instanceof EntityMaid) {
            ((ICancellableEvent) event).setCanceled(true);
            return;
        }
        // 玩家（或其它非女仆载具）主动抱走：出院，避免婴儿带着 sleepingPos 被拖走。
        HospitalBedRest.suppress(infant, (ServerLevel) event.getLevel(), 20 * 30);
        HospitalBedRest.forget(infant);
        ClinicService.releaseHospitalBed(infant);
        if (infant.isSleeping()) {
            infant.stopSleeping();
        }
    }

    /** 女仆死亡：交给诊所暂存永久后遗症，复活后由新女仆继承。 */
    private static void onMaidDeath(MaidDeathEvent event) {
        HospitalBedRest.forget(event.getMaid());
        if (event.getMaid().level() instanceof ServerLevel level) {
            ClinicService.onMaidDeath(level, event.getMaid());
        }
    }

    /**
     * 女仆吃完东西：把“吃了什么”交给诊所判断。
     * <p>TLM 的进食走原版的使用物品流程，因此这里能拿到进食前的原始物品；
     * 客户端也会触发该事件，真正的逻辑只在服务端执行。</p>
     */
    private static void onItemFinished(LivingEntityUseItemEvent.Finish event) {
        if (event.getEntity() instanceof EntityMaid maid) {
            ClinicService.onMaidAte(maid, event.getItem());
        }
    }

    /**
     * 玩家右键实体：只处理“手持本模组物品 + 目标是自己女仆”的情况。
     * <p>
     * 客户端也要取消，否则客户端会先跑一遍 TLM 的交互预测（出现界面闪烁）。
     * 真正的逻辑只在服务端执行。
     */
    private static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.isCanceled()) {
            return;
        }
        if (!(event.getTarget() instanceof EntityMaid maid)) {
            return;
        }
        if (!maid.isOwnedBy(event.getEntity())) {
            return;
        }
        if (!ClinicService.isCareItem(event.getItemStack())) {
            return;
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        if (event.getEntity() instanceof ServerPlayer player) {
            ClinicService.handleInteraction(player, maid, event.getItemStack(), event.getHand());
        }
    }

    /** 每 20 tick 遍历一次该维度的实体，推进所有女仆的健康状态。 */
    private static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        if (level.getGameTime() % ClinicSettings.TICK_INTERVAL != 0L) {
            return;
        }
        if (level.getGameTime() % 1200L == 0L) {
            ClinicService.prune(level);
        }
        for (Entity entity : level.getAllEntities()) {
            if (entity instanceof EntityMaid maid) {
                ClinicService.tickMaid(level, maid);
            }
        }
    }
}
