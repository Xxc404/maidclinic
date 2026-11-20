package com.maidclinic.compat;

import com.github.tartaricacid.touhoulittlemaid.client.animation.inner.IAnimation;
import com.github.tartaricacid.touhoulittlemaid.client.animation.script.ModelRendererWrapper;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.maidclinic.registry.ModEffects;
import net.minecraft.world.entity.Mob;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 生病女仆的睡眠姿态：在 TLM 默认睡眠动画基础上改成蜷缩/抱臂的病容，而不是悠闲平躺。
 * 只影响有“身体不适”效果的女仆，普通女仆仍使用原版睡姿。
 */
final class SickSleepAnimation implements IAnimation<Mob> {
    private final IAnimation<?> original;

    SickSleepAnimation(IAnimation<?> original) {
        this.original = original;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void setRotationAngles(float limbSwing, float limbSwingAmount, float ageInTicks,
                                  float netHeadYaw, float headPitch, float scale,
                                  Mob entity, HashMap<String, ModelRendererWrapper> parts) {
        if (original != null) {
            ((IAnimation<Mob>) original).setRotationAngles(limbSwing, limbSwingAmount, ageInTicks,
                    netHeadYaw, headPitch, scale, entity, parts);
        }
        if (!(entity instanceof EntityMaid maid) || maid.getEffect(ModEffects.UNWELL) == null) {
            return;
        }
        // 蜷缩：低头、抱臂、屈腿，和默认放松睡姿区分开。
        curl(parts, "head", 0.55F, 0.25F, 0.15F);
        curl(parts, "body", 0.18F, 0.0F, 0.08F);
        curl(parts, "leftArm", -1.10F, 0.0F, 0.55F);
        curl(parts, "rightArm", -1.10F, 0.0F, -0.55F);
        curl(parts, "leftLeg", -0.65F, 0.0F, 0.15F);
        curl(parts, "rightLeg", -0.65F, 0.0F, -0.15F);
        // 只有抽中“强行睁眼”方案的失眠女仆才掀眼皮，普通生病女仆保持模型/睡眠动画的原样。
        if (maid.getEffect(ModEffects.OPEN_EYES) != null) {
            openEyes(parts);
        }
    }

    /** 方案一「强行睁眼」：睡眠动画会把 Right/LeftEyelid（部分模型叫 blink）压成 0，这里还原成 1。 */
    private static void openEyes(Map<String, ModelRendererWrapper> parts) {
        setEyelids(parts, 1.0F);
    }

    /** 设置眼皮缩放：睡眠动画原生是 0（闭眼），{@code 1.0} 为睁眼。 */
    private static void setEyelids(Map<String, ModelRendererWrapper> parts, float scale) {
        for (Map.Entry<String, ModelRendererWrapper> entry : parts.entrySet()) {
            String name = entry.getKey().toLowerCase(Locale.ROOT);
            if (!name.contains("eyelid") && !name.contains("blink")) {
                continue;
            }
            var part = entry.getValue().getModelRenderer();
            if (part == null) {
                continue;
            }
            part.xScale = scale;
            part.yScale = scale;
            part.zScale = scale;
        }
    }

    private static void curl(Map<String, ModelRendererWrapper> parts, String name,
                             float x, float y, float z) {
        ModelRendererWrapper part = findPart(parts, name);
        if (part == null) {
            return;
        }
        part.setRotateAngleX(part.getInitRotateAngleX() + x);
        part.setRotateAngleY(part.getInitRotateAngleY() + y);
        part.setRotateAngleZ(part.getInitRotateAngleZ() + z);
    }

    /** 模型骨骼大小写不统一（Head/head），这里做一次忽略大小写的查找。 */
    private static ModelRendererWrapper findPart(Map<String, ModelRendererWrapper> parts, String name) {
        ModelRendererWrapper exact = parts.get(name);
        if (exact != null) {
            return exact;
        }
        for (Map.Entry<String, ModelRendererWrapper> entry : parts.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(name)) {
                return entry.getValue();
            }
        }
        return null;
    }
}
