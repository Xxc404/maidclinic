package com.maidclinic.item;

import com.maidclinic.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.Item.TooltipContext;

import java.util.List;

/** 支援兵战备：装备后为附近攻击模式的女仆提供支援 buff。 */
public class SupportItem extends Item {
    public SupportItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Msg.tr("tooltip.maidclinic.support_kit").withStyle(ChatFormatting.GRAY));
    }
}
