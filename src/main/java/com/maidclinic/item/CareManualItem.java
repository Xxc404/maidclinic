package com.maidclinic.item;

import net.minecraft.network.protocol.game.ClientboundOpenBookPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.WrittenBookItem;
import net.minecraft.world.level.Level;

/**
 * 《家庭护理手册》。
 * <p>
 * 原版 {@code ServerPlayer.openItemGui} 只对 {@code Items.WRITTEN_BOOK} 发打开书本的数据包，
 * 自定义的 {@link WrittenBookItem} 子类会被直接忽略。所以这里自己发
 * {@link ClientboundOpenBookPacket}，让手册无论拿在手里右键还是对女仆右键都能打开。
 * </p>
 */
public class CareManualItem extends WrittenBookItem {
    public CareManualItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            serverPlayer.connection.send(new ClientboundOpenBookPacket(hand));
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }
}
