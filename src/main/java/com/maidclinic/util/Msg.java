package com.maidclinic.util;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.player.Player;

/**
 * 文本工具：统一走翻译键，方便维护多语言。
 */
public final class Msg {
    private Msg() {
    }

    public static MutableComponent tr(String key, Object... args) {
        return Component.translatable(key, args);
    }

    public static Component gray(String key, Object... args) {
        return tr(key, args).withStyle(ChatFormatting.GRAY);
    }

    public static Component green(String key, Object... args) {
        return tr(key, args).withStyle(ChatFormatting.GREEN);
    }

    public static Component red(String key, Object... args) {
        return tr(key, args).withStyle(ChatFormatting.RED);
    }

    public static Component yellow(String key, Object... args) {
        return tr(key, args).withStyle(ChatFormatting.YELLOW);
    }

    public static Component gold(String key, Object... args) {
        return tr(key, args).withStyle(ChatFormatting.GOLD);
    }

    public static Component aqua(String key, Object... args) {
        return tr(key, args).withStyle(ChatFormatting.AQUA);
    }

    public static void chat(Player player, Component message) {
        player.displayClientMessage(message, false);
    }

    public static void bar(Player player, Component message) {
        player.displayClientMessage(message, true);
    }
}
