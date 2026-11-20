package com.maidclinic.client;

import com.maidclinic.network.SelectBedPayload;
import com.maidclinic.network.UnsubscribeTerminalPayload;
import com.maidclinic.terminal.TerminalState;
import com.maidclinic.util.Msg;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;
import java.util.Objects;

/** 智能终端界面：左侧病床列表，点击后右侧显示该床女仆的完整档案。 */
public final class SmartTerminalScreen extends Screen {
    private static final int ROW_HEIGHT = 14;
    private static final int HEADER_HEIGHT = 28;
    private static final int FOOTER_HEIGHT = 16;

    private TerminalState state;
    private BlockPos selected;
    private boolean detailPending;
    private boolean unsubscribeSent;

    private int listScroll;
    private int detailScroll;

    private int panelX;
    private int panelY;
    private int panelW;
    private int panelH;
    private int leftW;
    private int contentY;
    private int contentH;

    public SmartTerminalScreen(TerminalState state) {
        super(Component.translatable("block.maidclinic.smart_terminal"));
        this.state = state;
        this.selected = state.selected();
    }

    public BlockPos terminalPos() {
        return state.terminal();
    }

    public void update(TerminalState next) {
        if (!next.terminal().equals(state.terminal())) {
            return;
        }
        if (!Objects.equals(selected, next.selected())) {
            detailScroll = 0;
        }
        this.state = next;
        this.selected = next.selected();
        this.detailPending = false;
    }

    @Override
    protected void init() {
        panelW = Math.min(360, width - 16);
        panelH = Math.min(240, height - 16);
        panelX = (width - panelW) / 2;
        panelY = (height - panelH) / 2;
        leftW = Math.min(132, panelW / 2);
        contentY = panelY + HEADER_HEIGHT;
        contentH = panelH - HEADER_HEIGHT - FOOTER_HEIGHT;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        graphics.fill(panelX, panelY, panelX + panelW, panelY + panelH, 0xF0101820);
        graphics.fill(panelX, panelY, panelX + panelW, panelY + 1, 0xFF2AA0A8);
        graphics.fill(panelX + leftW, panelY + HEADER_HEIGHT, panelX + leftW + 1,
                panelY + panelH - FOOTER_HEIGHT, 0xFF2AA0A8);

        graphics.drawCenteredString(font, title, panelX + panelW / 2, panelY + 7, 0xFF9FE8F0);
        Component count = Component.translatable("message.maidclinic.terminal.beds_short",
                state.beds().size());
        graphics.drawString(font, count, panelX + 8, panelY + HEADER_HEIGHT - 10, 0xFFA8B8C0);

        renderBedList(graphics, mouseX, mouseY);
        renderDetail(graphics);

        Component hint = Component.translatable("message.maidclinic.terminal.hint");
        graphics.drawCenteredString(font, hint, panelX + panelW / 2,
                panelY + panelH - 12, 0xFF708890);
    }

    private void renderBedList(GuiGraphics graphics, int mouseX, int mouseY) {
        int top = contentY + 4;
        int bottom = panelY + panelH - FOOTER_HEIGHT - 2;
        int visible = Math.max(1, (bottom - top) / ROW_HEIGHT);
        listScroll = Math.max(0, Math.min(listScroll, Math.max(0, state.beds().size() - visible)));

        graphics.enableScissor(panelX + 2, top, panelX + leftW - 2, bottom);
        String hoveredCoords = null;
        for (int i = 0; i < visible && listScroll + i < state.beds().size(); i++) {
            TerminalState.BedSummary bed = state.beds().get(listScroll + i);
            int rowY = top + i * ROW_HEIGHT;
            boolean hover = mouseX >= panelX + 2 && mouseX < panelX + leftW - 2
                    && mouseY >= rowY && mouseY < rowY + ROW_HEIGHT;
            if (bed.bed().equals(selected)) {
                graphics.fill(panelX + 2, rowY, panelX + leftW - 2, rowY + ROW_HEIGHT, 0x8030A0B0);
            } else if (hover) {
                graphics.fill(panelX + 2, rowY, panelX + leftW - 2, rowY + ROW_HEIGHT, 0x402AA0A8);
            }
            graphics.fill(panelX + 6, rowY + 5, panelX + 10, rowY + 9, bed.statusColor());
            String label = bed.valid() ? (bed.occupied() ? bed.name()
                    : Msg.tr("message.maidclinic.terminal.bed_empty").getString())
                    : Msg.tr("message.maidclinic.terminal.bed_broken").getString();
            graphics.drawString(font, label, panelX + 14, rowY + 3, 0xFFE8F4F6, false);
            if (hover) {
                hoveredCoords = bed.bed().getX() + ", " + bed.bed().getY() + ", " + bed.bed().getZ();
            }
        }
        graphics.disableScissor();
        if (hoveredCoords != null) {
            graphics.renderTooltip(font, Component.literal(hoveredCoords), mouseX, mouseY);
        }

        if (state.beds().size() > visible) {
            int barX = panelX + leftW - 4;
            int barTop = top;
            int barH = bottom - top;
            int thumbH = Math.max(10, barH * visible / state.beds().size());
            int thumbY = barTop + (barH - thumbH) * listScroll
                    / Math.max(1, state.beds().size() - visible);
            graphics.fill(barX, barTop, barX + 2, barTop + barH, 0x30305060);
            graphics.fill(barX, thumbY, barX + 2, thumbY + thumbH, 0xFF2AA0A8);
        }
    }

    private void renderDetail(GuiGraphics graphics) {
        int x = panelX + leftW + 8;
        int y = contentY + 4;
        int width = panelW - leftW - 16;
        int bottom = panelY + panelH - FOOTER_HEIGHT - 2;

        if (selected == null) {
            graphics.drawString(font, Msg.tr("message.maidclinic.terminal.select_bed"),
                    x, y, 0xFFA8B8C0);
            return;
        }
        TerminalState.BedSummary summary = null;
        for (TerminalState.BedSummary bed : state.beds()) {
            if (bed.bed().equals(selected)) {
                summary = bed;
                break;
            }
        }
        if (summary == null || !summary.valid()) {
            graphics.drawString(font, Msg.tr("message.maidclinic.terminal.bed_missing_short"),
                    x, y, 0xFFFF7070);
            return;
        }
        if (!state.detailReady() || detailPending) {
            graphics.drawString(font, Msg.tr("message.maidclinic.terminal.loading"), x, y, 0xFFA8B8C0);
            return;
        }
        if (state.detail().isEmpty()) {
            graphics.drawString(font, Msg.tr("message.maidclinic.terminal.empty_short"),
                    x, y, 0xFFA8B8C0);
            return;
        }

        int innerBottom = bottom;
        int maxScroll = Math.max(0, detailHeight(width) - (innerBottom - y));
        detailScroll = Math.max(0, Math.min(detailScroll, maxScroll));

        graphics.enableScissor(x, y, x + width, innerBottom);
        int drawY = y - detailScroll;
        for (Component line : state.detail()) {
            List<FormattedCharSequence> wrapped = font.split(line, width);
            for (FormattedCharSequence sequence : wrapped) {
                if (drawY + 10 >= y && drawY <= innerBottom) {
                    graphics.drawString(font, sequence, x, drawY, 0xFFE8F4F6);
                }
                drawY += 10;
            }
            drawY += 2;
        }
        graphics.disableScissor();

        if (maxScroll > 0) {
            int barX = panelX + panelW - 4;
            int barH = innerBottom - y;
            int thumbH = Math.max(10, barH * barH / Math.max(barH, detailHeight(width)));
            int thumbY = y + (barH - thumbH) * detailScroll / maxScroll;
            graphics.fill(barX, y, barX + 2, y + barH, 0x30305060);
            graphics.fill(barX, thumbY, barX + 2, thumbY + thumbH, 0xFF2AA0A8);
        }
    }

    private int detailHeight(int width) {
        int height = 0;
        for (Component line : state.detail()) {
            height += font.split(line, width).size() * 10 + 2;
        }
        return height;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && mouseX >= panelX + 2 && mouseX < panelX + leftW - 2
                && mouseY >= contentY + 4 && mouseY < panelY + panelH - FOOTER_HEIGHT - 2) {
            int visible = Math.max(1, (panelY + panelH - FOOTER_HEIGHT - 2 - (contentY + 4)) / ROW_HEIGHT);
            int index = listScroll + (int) ((mouseY - (contentY + 4)) / ROW_HEIGHT);
            if (index >= 0 && index < state.beds().size() && index < listScroll + visible) {
                BlockPos bed = state.beds().get(index).bed();
                selected = bed;
                detailPending = true;
                detailScroll = 0;
                PacketDistributor.sendToServer(new SelectBedPayload(state.terminal(), bed));
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (mouseX >= panelX && mouseX < panelX + panelW
                && mouseY >= contentY && mouseY < panelY + panelH - FOOTER_HEIGHT) {
            if (mouseX < panelX + leftW) {
                listScroll -= (int) Math.signum(scrollY);
                return true;
            }
            detailScroll -= (int) (scrollY * 12);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            onClose();
            return;
        }
        if (!(minecraft.level.getBlockState(state.terminal()).getBlock() instanceof com.maidclinic.block.SmartTerminalBlock)
                || minecraft.player.distanceToSqr(Vec3.atCenterOf(state.terminal())) > 64.0D) {
            onClose();
        }
    }

    @Override
    public void onClose() {
        if (!unsubscribeSent) {
            unsubscribeSent = true;
            PacketDistributor.sendToServer(new UnsubscribeTerminalPayload(state.terminal()));
        }
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
