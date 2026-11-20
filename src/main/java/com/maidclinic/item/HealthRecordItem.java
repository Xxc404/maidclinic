package com.maidclinic.item;

import com.maidclinic.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/** 健康档案：右键女仆查看完整的生命体征、诊断与信任度。 */
public class HealthRecordItem extends AbstractCareItem {
    public HealthRecordItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Msg.tr("tooltip.maidclinic.health_record").withStyle(ChatFormatting.GRAY));
        addUsageLine(tooltip);
    }
}
