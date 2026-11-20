package com.maidclinic.network;

import com.maidclinic.terminal.TerminalHub;
import com.maidclinic.terminal.TerminalState;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** 终端界面的网络包注册。客户端处理类只按类名加载，服务端不会触碰 Screen。 */
public final class TerminalNetwork {
    private static final String CLIENT_BRIDGE = "com.maidclinic.client.TerminalClientBridge";

    private TerminalNetwork() {
    }

    public static void register(IEventBus modBus) {
        modBus.addListener(TerminalNetwork::onRegisterPayloads);
    }

    private static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");

        registrar.playToClient(TerminalStatePayload.TYPE, TerminalStatePayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> handleClient(payload.state())));

        registrar.playToServer(SelectBedPayload.TYPE, SelectBedPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player) {
                        TerminalHub.select(player, payload.terminal(), payload.bed());
                    }
                }));

        registrar.playToServer(UnsubscribeTerminalPayload.TYPE, UnsubscribeTerminalPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player) {
                        TerminalHub.close(player, payload.terminal());
                    }
                }));
    }

    private static void handleClient(TerminalState state) {
        try {
            Class.forName(CLIENT_BRIDGE)
                    .getMethod("handle", TerminalState.class)
                    .invoke(null, state);
        } catch (ReflectiveOperationException | LinkageError ignored) {
            // 专用服务器不会加载客户端界面
        }
    }
}
