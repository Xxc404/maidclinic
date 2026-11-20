package com.maidclinic.compat;

import com.maidclinic.util.Msg;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * 车万女仆软依赖入口。
 * <p>
 * 之所以用“类名字符串 + Class.forName”而不是 {@code ModList.isLoaded}：
 * 后者依赖 FML 的加载器 API，而这里希望整个模组在 TLM 缺失时连它的类都不触碰。
 */
public final class TlmSupport {
    /** TLM 女仆实体类名，仅作为字符串出现，不会在缺失时触发类加载。 */
    private static final String MAID_CLASS =
            "com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid";

    private TlmSupport() {
    }

    public static void bootstrap() {
        if (!isMaidModPresent()) {
            return;
        }
        // 只有确认 TLM 存在后，才会加载 TlmEvents（其中引用了 EntityMaid）
        TlmEvents.register();
    }

    /**
     * 智能终端读取病床上的女仆。真正涉及 EntityMaid 的代码放在 {@link TlmEvents}，
     * 这里用反射调用，保证 TLM 缺失时本模组仍能加载。
     */
    public static void reportBed(ServerPlayer player, ServerLevel level, BlockPos bedPos) {
        if (!isMaidModPresent()) {
            Msg.chat(player, Msg.red("message.maidclinic.terminal.no_maid_mod"));
            return;
        }
        try {
            Class.forName("com.maidclinic.compat.TlmEvents", true, TlmSupport.class.getClassLoader())
                    .getMethod("reportBed", ServerPlayer.class, ServerLevel.class, BlockPos.class)
                    .invoke(null, player, level, bedPos);
        } catch (ReflectiveOperationException | LinkageError ignored) {
            Msg.chat(player, Msg.red("message.maidclinic.terminal.no_maid_mod"));
        }
    }

    public static boolean isMaidModPresent() {
        try {
            Class.forName(MAID_CLASS, false, TlmSupport.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException | LinkageError ignored) {
            return false;
        }
    }
}
