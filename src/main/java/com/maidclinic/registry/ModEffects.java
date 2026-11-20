package com.maidclinic.registry;

import com.maidclinic.MaidClinic;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 状态效果。
 * <p>
 * 只有一个“身体不适”标记效果：它不产生任何数值影响，仅用于在女仆身上显示图标，
 * 让玩家远远看到就知道她需要照顾——真正的数值由 {@link com.maidclinic.health.Condition} 负责。
 */
public final class ModEffects {
    public static final DeferredRegister<MobEffect> EFFECTS =
            DeferredRegister.create(Registries.MOB_EFFECT, MaidClinic.MOD_ID);

    public static final DeferredHolder<MobEffect, MobEffect> UNWELL = EFFECTS.register("unwell",
            () -> new MobEffect(MobEffectCategory.HARMFUL, 0x8A6D3B) {
            });

    /**
     * 给客户端用的“睁眼”标记：失眠女仆被抽中“强行睁眼”方案时挂上，
     * 无任何数值效果、不显示图标，仅用于让睡眠动画强制掀开眼皮。
     */
    public static final DeferredHolder<MobEffect, MobEffect> OPEN_EYES = EFFECTS.register("open_eyes",
            () -> new MobEffect(MobEffectCategory.NEUTRAL, 0xFFFFFF) {
            });

    /**
     * 睡觉表情的服务端标记：amplifier 就是表情序号（难受/难过/吃惊/受伤/害怕）。
     * 服务端选好表情后挂上，客户端据此播放，保证联机时所有玩家看到同一张脸。
     */
    public static final DeferredHolder<MobEffect, MobEffect> SICK_FACE = EFFECTS.register("sick_face",
            () -> new MobEffect(MobEffectCategory.NEUTRAL, 0xFFFFFF) {
            });

    private ModEffects() {
    }
}
