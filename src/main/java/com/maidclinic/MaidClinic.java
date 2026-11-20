package com.maidclinic;

import com.maidclinic.compat.TlmSupport;
import com.maidclinic.command.ClinicCommand;
import com.maidclinic.registry.ModBlocks;
import com.maidclinic.registry.ModCreativeTabs;
import com.maidclinic.registry.ModEffects;
import com.maidclinic.registry.ModItems;
import com.maidclinic.network.TerminalNetwork;
import com.maidclinic.terminal.TerminalHub;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;

/**
 * 《车万女仆：女仆诊所》入口。
 * <p>
 * 设计原则：
 * <ul>
 *     <li>不写 mixin、不注入其它模组的类，只通过注册表与 NeoForge 事件工作；</li>
 *     <li>对车万女仆（Touhou Little Maid）是<b>软依赖</b>：未安装时本模组仍可正常加载，
 *         物品只是暂时没有作用对象；</li>
 *     <li>所有涉及 TLM 的代码集中在 {@code com.maidclinic.compat} 包，只有检测到 TLM 时才会被加载。</li>
 * </ul>
 */
@Mod(MaidClinic.MOD_ID)
public final class MaidClinic {
    public static final String MOD_ID = "maidclinic";

    public MaidClinic(IEventBus modEventBus, ModContainer modContainer) {
        ModBlocks.BLOCKS.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        ModEffects.EFFECTS.register(modEventBus);
        ModCreativeTabs.TABS.register(modEventBus);
        modContainer.registerConfig(ModConfig.Type.COMMON, ClinicConfig.SPEC);
        TerminalNetwork.register(modEventBus);
        NeoForge.EVENT_BUS.addListener(TerminalHub::onServerTick);
        NeoForge.EVENT_BUS.addListener(ClinicCommand::register);
        TlmSupport.bootstrap();
        if (TlmSupport.isMaidModPresent() && isClient()) {
            registerClientEvents();
        }
    }

    private static boolean isClient() {
        try {
            Class.forName("net.minecraft.client.Minecraft", false, MaidClinic.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException | LinkageError ignored) {
            return false;
        }
    }

    private static void registerClientEvents() {
        try {
            Class.forName("com.maidclinic.compat.ClientEvents")
                    .getMethod("register")
                    .invoke(null);
        } catch (ReflectiveOperationException | LinkageError ignored) {
            // 客户端类不可用时静默跳过，不影响服务端/主体功能
        }
    }
}
