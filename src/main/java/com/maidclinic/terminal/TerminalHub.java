package com.maidclinic.terminal;

import com.maidclinic.block.HospitalBedBlock;
import com.maidclinic.block.SmartTerminalBlock;
import com.maidclinic.block.TerminalData;
import com.maidclinic.network.TerminalStatePayload;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 服务端终端订阅中心：只在打开/选择时请求，之后由服务端比较快照 hash，有变化才推送。
 */
public final class TerminalHub {
    private static final int PUSH_INTERVAL = 10;

    private static final Map<UUID, View> VIEWS = new HashMap<>();
    private static Provider provider;

    private TerminalHub() {
    }

    /** 由 TLM 兼容层注册；TLM 缺失时使用无女仆信息的降级快照。 */
    @FunctionalInterface
    public interface Provider {
        TerminalState build(ServerPlayer player, BlockPos terminal, BlockPos selected);
    }

    public static void setProvider(Provider value) {
        provider = value;
    }

    public static void open(ServerPlayer player, BlockPos terminal) {
        View view = new View(player, terminal);
        VIEWS.put(player.getUUID(), view);
        sendState(player, view, false);
    }

    public static void select(ServerPlayer player, BlockPos terminal, BlockPos bed) {
        View view = VIEWS.get(player.getUUID());
        if (view == null || !view.terminal.equals(terminal)) {
            return;
        }
        if (!(player.level() instanceof ServerLevel level) || !isViewValid(player, level, terminal)) {
            VIEWS.remove(player.getUUID());
            return;
        }
        if (!TerminalData.get(level).bedsFor(terminal).contains(bed)) {
            return;
        }
        view.selected = bed;
        sendState(player, view, true);
    }

    public static void close(ServerPlayer player, BlockPos terminal) {
        View view = VIEWS.get(player.getUUID());
        if (view != null && view.terminal.equals(terminal)) {
            VIEWS.remove(player.getUUID());
        }
    }

    public static void onServerTick(ServerTickEvent.Post event) {
        if (VIEWS.isEmpty()) {
            return;
        }
        Iterator<Map.Entry<UUID, View>> iterator = VIEWS.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, View> entry = iterator.next();
            View view = entry.getValue();
            ServerPlayer player = view.player;
            if (player == null || player.isRemoved() || player.hasDisconnected()
                    || !(player.level() instanceof ServerLevel level)) {
                iterator.remove();
                continue;
            }
            if (!isViewValid(player, level, view.terminal)) {
                iterator.remove();
                continue;
            }
            if (level.getGameTime() % PUSH_INTERVAL != 0L) {
                continue;
            }
            sendState(player, view, false);
        }
    }

    private static boolean isViewValid(ServerPlayer player, ServerLevel level, BlockPos terminal) {
        return level.getBlockState(terminal).getBlock() instanceof SmartTerminalBlock
                && player.distanceToSqr(Vec3.atCenterOf(terminal)) <= 64.0D;
    }

    private static void sendState(ServerPlayer player, View view, boolean force) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        TerminalState state;
        try {
            state = provider == null ? fallback(level, view.terminal, view.selected)
                    : provider.build(player, view.terminal, view.selected);
        } catch (RuntimeException | LinkageError ignored) {
            state = fallback(level, view.terminal, view.selected);
        }
        int hash = state.hashCode();
        if (!force && view.sent && view.lastHash == hash) {
            return;
        }
        view.lastHash = hash;
        view.sent = true;
        PacketDistributor.sendToPlayer(player, new TerminalStatePayload(state));
    }

    private static TerminalState fallback(ServerLevel level, BlockPos terminal, BlockPos selected) {
        List<TerminalState.BedSummary> beds = new ArrayList<>();
        for (BlockPos bed : TerminalData.get(level).bedsFor(terminal)) {
            boolean valid = level.getBlockState(bed).getBlock() instanceof HospitalBedBlock;
            beds.add(new TerminalState.BedSummary(bed, "", valid ? 0xFFAAAAAA : 0xFF555555, false, valid));
        }
        boolean ready = selected != null;
        return new TerminalState(terminal, beds, selected, ready, List.of());
    }

    private static final class View {
        private final ServerPlayer player;
        private final BlockPos terminal;
        private BlockPos selected;
        private int lastHash;
        private boolean sent;

        private View(ServerPlayer player, BlockPos terminal) {
            this.player = player;
            this.terminal = terminal;
        }
    }
}
