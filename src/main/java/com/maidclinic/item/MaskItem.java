package com.maidclinic.item;

import com.maidclinic.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/** 医用口罩：给女仆戴上后，一段时间内降低交叉感染与发病概率。 */
public class MaskItem extends AbstractCareItem {
    public MaskItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Msg.tr("tooltip.maidclinic.mask").withStyle(ChatFormatting.GRAY));
        addUsageLine(tooltip);
    }
}
