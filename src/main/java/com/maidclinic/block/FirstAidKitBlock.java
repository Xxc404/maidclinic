package com.maidclinic.block;

import com.maidclinic.health.Treatment;
import com.maidclinic.registry.ModItems;
import com.maidclinic.util.Msg;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * 急救箱方块：右键领取一份基础物资，有冷却。
 * <p>
 * 放在女仆常待的地方，作为“常备药”的补充来源，避免玩家在突发疾病时无药可用。
 */
public class FirstAidKitBlock extends Block {
    private static final int COOLDOWN_TICKS = 20 * 90;

    public FirstAidKitBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hitResult) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        Item kit = ModItems.FIRST_AID_KIT.get();
        if (player.getCooldowns().isOnCooldown(kit)) {
            Msg.bar(player, Msg.tr("message.maidclinic.first_aid_kit.cooldown"));
            return InteractionResult.CONSUME;
        }
        player.getCooldowns().addCooldown(kit, COOLDOWN_TICKS);
        give(player, ModItems.MEDICINES.get(Treatment.BANDAGE).get(), 2);
        give(player, ModItems.MEDICINES.get(Treatment.ORS).get(), 2);
        give(player, ModItems.MEDICINES.get(Treatment.COOLING).get(), 1);
        level.playSound(null, pos, SoundEvents.CHEST_OPEN, SoundSource.BLOCKS, 0.8F, 1.2F);
        Msg.chat(player, Msg.green("message.maidclinic.first_aid_kit.used"));
        return InteractionResult.CONSUME;
    }

    private static void give(Player player, Item item, int count) {
        ItemStack stack = new ItemStack(item, count);
        if (!player.addItem(stack)) {
            player.drop(stack, false);
        }
    }
}
