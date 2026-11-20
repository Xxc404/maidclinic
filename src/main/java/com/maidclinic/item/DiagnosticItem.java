package com.maidclinic.item;

import com.maidclinic.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;
import java.util.Locale;

/**
 * 检查工具：听诊器、体温计、血压计、血氧仪。
 * <p>
 * 不同的工具只能测到不同信息，对应真实的分级查体：
 * 听诊器给出最完整的问诊结果，其余工具用于快速复查单项指标。
 */
public class DiagnosticItem extends AbstractCareItem {

    public enum Mode {
        STETHOSCOPE,
        THERMOMETER,
        SPHYGMOMANOMETER,
        OXIMETER,
        GLUCOMETER
    }

    private final Mode mode;

    public DiagnosticItem(Properties properties, Mode mode) {
        super(properties);
        this.mode = mode;
    }

    public Mode mode() {
        return mode;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Msg.tr("tooltip.maidclinic.mode." + mode.name().toLowerCase(Locale.ROOT))
                .withStyle(ChatFormatting.GRAY));
        addUsageLine(tooltip);
    }
}
