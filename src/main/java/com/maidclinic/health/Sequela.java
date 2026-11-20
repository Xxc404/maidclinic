package com.maidclinic.health;

import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;

import java.util.Locale;
import java.util.function.Supplier;

/**
 * 重症未治疗留下的“后遗症”。
 * <p>
 * 每一项都对应现实中被指南/共识认可的长程风险，而不是随意的数值惩罚：
 * <ul>
 *     <li>链球菌性咽炎 → 风湿热/风湿性心脏病；</li>
 *     <li>高血压 → 靶器官损害（心、脑、肾）；</li>
 *     <li>2 型糖尿病 → 周围神经病变；</li>
 *     <li>重度脱水 → 急性肾损伤后遗；</li>
 *     <li>热衰竭/热射病 → 神经系统后遗；</li>
 *     <li>伤口感染 → 慢性伤口/瘢痕；</li>
 *     <li>重度低血糖 → 认知功能影响；</li>
 *     <li>退热药过量 → 药物性肝损伤；</li>
 *     <li>慢性胃炎 → 反复发作；</li>
 *     <li>骨质疏松 → 骨折风险。</li>
 * </ul>
 * 游戏内统一用“工作效率”惩罚来表示体力/耐力/注意力下降，单项 2%–4%，合计封顶 15%。
 */
public enum Sequela {
    RHEUMATIC_HEART("rheumatic_heart", 0.04F, "bacterial_pharyngitis", () -> MobEffects.WEAKNESS, 0),
    HYPERTENSIVE_DAMAGE("hypertensive_damage", 0.04F, "hypertension", () -> MobEffects.MOVEMENT_SLOWDOWN, 0),
    DIABETIC_NEUROPATHY("diabetic_neuropathy", 0.04F, "type_2_diabetes", () -> MobEffects.MOVEMENT_SLOWDOWN, 0),
    KIDNEY_INJURY("kidney_injury", 0.03F, "dehydration", () -> MobEffects.WEAKNESS, 0),
    HEAT_INJURY("heat_injury", 0.04F, "heat_exhaustion", () -> MobEffects.CONFUSION, 0),
    CHRONIC_WOUND("chronic_wound", 0.02F, "wound_infection", () -> MobEffects.MOVEMENT_SLOWDOWN, 0),
    COGNITIVE_IMPAIRMENT("cognitive_impairment", 0.03F, "hypoglycemia", () -> MobEffects.CONFUSION, 0),
    LIVER_INJURY("liver_injury", 0.03F, "medication_overuse", () -> MobEffects.WEAKNESS, 0),
    GASTRITIS_FLARE("gastritis_flare", 0.02F, "chronic_gastritis", () -> MobEffects.CONFUSION, 0),
    FRACTURE_RISK("fracture_risk", 0.03F, "osteoporosis", () -> MobEffects.MOVEMENT_SLOWDOWN, 1);

    private final String id;
    private final float penalty;
    private final String conditionId;
    private final Supplier<Holder<MobEffect>> effect;
    private final int amplifier;

    Sequela(String id, float penalty, String conditionId, Supplier<Holder<MobEffect>> effect, int amplifier) {
        this.id = id;
        this.penalty = penalty;
        this.conditionId = conditionId;
        this.effect = effect;
        this.amplifier = amplifier;
    }

    public String id() {
        return id;
    }

    public float penalty() {
        return penalty;
    }

    public String conditionId() {
        return conditionId;
    }

    /** 后遗症周期性造成的功能影响（虚弱/迟缓/意识模糊）。 */
    public Holder<MobEffect> effect() {
        return effect.get();
    }

    public int amplifier() {
        return amplifier;
    }

    public String translationKey() {
        return "sequela.maidclinic." + id.toLowerCase(Locale.ROOT);
    }

    public static Sequela forCondition(String conditionId) {
        for (Sequela sequela : values()) {
            if (sequela.conditionId.equals(conditionId)) {
                return sequela;
            }
        }
        return null;
    }

    public static Sequela byId(String id) {
        for (Sequela sequela : values()) {
            if (sequela.id.equals(id)) {
                return sequela;
            }
        }
        return null;
    }
}
