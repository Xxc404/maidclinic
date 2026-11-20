package com.maidclinic.compat;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.client.animation.inner.IAnimation;
import com.maidclinic.registry.ModEffects;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectInstance;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLivingEvent;
import net.neoforged.neoforge.common.NeoForge;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

/**
 * 生病女仆的“外表”表现：渲染时给模型加一层病态色偏（发热泛红/贫血苍白），
 * 不依赖第三方动画模组，也不改动作。
 */
public final class ClientEvents {
    private static boolean sleepAnimationInstalled;
    private static IAnimation<?> sleepOriginal;
    private static IAnimation<?> sleepWrapper;

    private ClientEvents() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(ClientEvents::onRenderPre);
        NeoForge.EVENT_BUS.addListener(ClientEvents::onRenderPost);
        NeoForge.EVENT_BUS.addListener(ClientEvents::onClientTick);
    }

    /** 睡着的生病女仆每 tick 强制维持难受/受伤/害怕表情，避免睡脸过平静。 */
    private static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        long now = minecraft.level.getGameTime();
        boolean sweep = now % 20L == 0L;
        for (var entity : minecraft.level.entitiesForRendering()) {
            if (!(entity instanceof EntityMaid maid)) {
                continue;
            }
            if (maid.isSleeping()) {
                SickExpressions.clientSleepFace(maid, now);
            } else if (sweep) {
                // 每 20 tick 清一次旧版本残留的睡眠表情键
                SickExpressions.clearStale(maid);
            }
        }
    }

    private static void onRenderPre(RenderLivingEvent.Pre<?, ?> event) {
        installSleepAnimation();
        patchSleepAnimation(event.getRenderer());
        if (!(event.getEntity() instanceof EntityMaid maid)) {
            return;
        }
        MobEffectInstance unwell = maid.getEffect(ModEffects.UNWELL);
        if (unwell == null) {
            return;
        }
        // 渲染前最后一刻抢表情帧：客户端渲染只认活动 ExpressionFrame，
        // 每 tick 写的 NBT 键会被事件帧（例如 chijing）压掉。
        if (maid.isSleeping()) {
            SickExpressions.clientSleepFace(maid, maid.level().getGameTime());
        }
        float severity = Math.min(1.0F, unwell.getAmplifier() / 2.0F);
        // 越严重越偏红/苍白，形成“病容”而不是动作。
        RenderSystem.setShaderColor(1.0F, 0.86F - severity * 0.14F, 0.86F - severity * 0.14F, 1.0F);
    }

    private static void onRenderPost(RenderLivingEvent.Post event) {
        if (event.getEntity() instanceof EntityMaid maid && maid.getEffect(ModEffects.UNWELL) != null) {
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        }
    }

    /**
     * 找到 TLM 默认睡眠动画的原始实例。
     * <p>不直接替换 {@code INNER_ANIMATION} 里的那一份：模型在资源重载时就已把
     * 动画实例拷进自己的渲染列表，替换 map 往往来不及；这里保留原实例，
     * 再在渲染前把渲染器列表里的同一实例换成包装版。</p>
     */
    @SuppressWarnings("unchecked")
    private static void installSleepAnimation() {
        if (sleepAnimationInstalled) {
            return;
        }
        try {
            Class<?> inner = Class.forName(
                    "com.github.tartaricacid.touhoulittlemaid.client.animation.inner.InnerAnimation");
            Field field = inner.getDeclaredField("INNER_ANIMATION");
            field.setAccessible(true);
            Object value = field.get(null);
            if (!(value instanceof Map<?, ?> rawMap)) {
                return;
            }
            ResourceLocation id = ResourceLocation.parse(
                    "touhou_little_maid:animation/maid/default/sleep/default.js");
            Map<ResourceLocation, IAnimation<?>> animations = (Map<ResourceLocation, IAnimation<?>>) rawMap;
            IAnimation<?> original = animations.get(id);
            if (original == null) {
                return;
            }
            sleepOriginal = original;
            sleepWrapper = new SickSleepAnimation(original);
            animations.put(id, sleepWrapper);
            sleepAnimationInstalled = true;
        } catch (ReflectiveOperationException | LinkageError ignored) {
            // TLM 版本变化时静默跳过
        }
    }

    /** 把渲染器动画列表里的睡眠动画换成包装版（每次渲染前做，重复替换无副作用）。 */
    @SuppressWarnings("unchecked")
    private static void patchSleepAnimation(Object renderer) {
        if (sleepOriginal == null || sleepWrapper == null || renderer == null) {
            return;
        }
        try {
            Field field = findField(renderer.getClass(), "mainAnimations");
            if (field == null) {
                return;
            }
            field.setAccessible(true);
            Object value = field.get(renderer);
            if (!(value instanceof List<?> raw)) {
                return;
            }
            List<Object> animations = (List<Object>) raw;
            for (int i = 0; i < animations.size(); i++) {
                if (animations.get(i) == sleepOriginal) {
                    animations.set(i, sleepWrapper);
                }
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // 渲染器结构变化时静默跳过
        }
    }

    private static Field findField(Class<?> type, String name) {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            try {
                return current.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
                // 继续向父类找
            }
        }
        return null;
    }
}
