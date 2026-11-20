package com.maidclinic.client;

import com.maidclinic.terminal.TerminalState;
import net.minecraft.client.Minecraft;

/** 客户端收到服务端快照后，打开或更新终端界面。 */
public final class TerminalClientBridge {
    private TerminalClientBridge() {
    }

    public static void handle(TerminalState state) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof SmartTerminalScreen screen
                && screen.terminalPos().equals(state.terminal())) {
            screen.update(state);
            return;
        }
        minecraft.setScreen(new SmartTerminalScreen(state));
    }
}
