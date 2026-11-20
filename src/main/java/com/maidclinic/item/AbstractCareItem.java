package com.maidclinic.item;

import com.maidclinic.util.Msg;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * 所有“对女仆使用”的物品的基类，统一处理空挥提示。
 */
public abstract class AbstractCareItem extends Item {
    protected AbstractCareItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (!level.isClientSide) {
            Msg.bar(player, Msg.tr("message.maidclinic.need_maid"));
        }
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
    }

    protected static void addUsageLine(List<Component> tooltip) {
        tooltip.add(Msg.tr("tooltip.maidclinic.usage").withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
    }

    @Override
    public abstract void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag);
}
