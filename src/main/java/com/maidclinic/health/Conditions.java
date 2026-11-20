package com.maidclinic.health;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 疾病定义表。
 * <p>
 * 这里的每一项都对应一类现实中的常见问题，数值只做“游戏化压缩”：
 * 现实中的 5～7 天病程被压缩到 6～8 分钟，但<b>判断逻辑</b>（该不该用抗生素、
 * 要不要补液、烫伤先冷水冲洗）保持与医学常识一致。
 * 参考来源见 {@code docs/MEDICAL_REFERENCES.md}。
 */
public final class Conditions {
    private Conditions() {
    }

    /** 病毒性上呼吸道感染（普通感冒）：自限性，抗生素无效。 */
    public static final Condition VIRAL_URI = Condition.builder("viral_uri")
            .category(Condition.Category.INFECTION)
            .virulence(0.8F)
            .selfLimiting(true)
            .infectious(true, 0.35F)
            .duration(360, 480)
            .fever(0.9F)
            .heartRate(8)
            .respiratoryRate(3)
            .maxSeverity(2)
            .symptoms("symptom.maidclinic.sore_throat", "symptom.maidclinic.runny_nose",
                    "symptom.maidclinic.cough", "symptom.maidclinic.fatigue")
            .redFlags("redflag.maidclinic.dyspnea", "redflag.maidclinic.high_fever_3d")
            .primary()
            .supportive(Treatment.ANTIPYRETIC, Treatment.ORS, Treatment.COOLING)
            .harmful(Treatment.ANTIBIOTIC, Treatment.AZITHROMYCIN)
            .build();

    /** 细菌性咽炎（A 组链球菌）：需要抗生素并完成疗程。 */
    public static final Condition BACTERIAL_PHARYNGITIS = Condition.builder("bacterial_pharyngitis")
            .category(Condition.Category.INFECTION)
            .virulence(1.6F)
            .infectious(true, 0.25F)
            .duration(600, 720)
            .fever(1.1F)
            .heartRate(12)
            .respiratoryRate(2)
            .maxSeverity(3)
            .requiredDoses(3)
            .symptoms("symptom.maidclinic.severe_sore_throat", "symptom.maidclinic.fever",
                    "symptom.maidclinic.swallow_pain", "symptom.maidclinic.tonsil_exudate")
            .redFlags("redflag.maidclinic.drooling", "redflag.maidclinic.dyspnea")
            .primary(Treatment.ANTIBIOTIC, Treatment.AZITHROMYCIN)
            .supportive(Treatment.ANTIPYRETIC)
            .build();

    /** 急性胃肠炎：多数为自限性，但补液是降低脱水风险的关键。 */
    public static final Condition GASTROENTERITIS = Condition.builder("gastroenteritis")
            .category(Condition.Category.INFECTION)
            .virulence(2.4F)
            .selfLimiting(0.85F)
            .recoveryNeedsSupport(true)
            .infectious(true, 0.30F)
            .duration(300, 420)
            .mortality(0.7F)
            .fever(0.4F)
            .heartRate(10)
            .bloodPressure(-8, -4)
            .respiratoryRate(1)
            .maxSeverity(3)
            .symptoms("symptom.maidclinic.diarrhea", "symptom.maidclinic.nausea",
                    "symptom.maidclinic.thirst", "symptom.maidclinic.fatigue")
            .redFlags("redflag.maidclinic.no_urine", "redflag.maidclinic.blood_stool")
            .primary(Treatment.ORS)
            .harmful(Treatment.ANTIBIOTIC, Treatment.AZITHROMYCIN)
            .complication("dehydration", 0.8F)
            .build();

    /**
     * 食源性胃肠炎：由不洁饮食直接诱发，临床表现与 {@link #GASTROENTERITIS} 完全一致，
     * 但不具传染性，显示名沿用“急性胃肠炎”。
     */
    public static final Condition DIETARY_GASTROENTERITIS = Condition.builder("dietary_gastroenteritis")
            .category(Condition.Category.INFECTION)
            .virulence(2.4F)
            .selfLimiting(0.85F)
            .recoveryNeedsSupport(true)
            .infectious(false, 0.0F)
            .duration(300, 420)
            .mortality(0.7F)
            .fever(0.4F)
            .heartRate(10)
            .bloodPressure(-8, -4)
            .respiratoryRate(1)
            .maxSeverity(3)
            .symptoms("symptom.maidclinic.diarrhea", "symptom.maidclinic.nausea",
                    "symptom.maidclinic.thirst", "symptom.maidclinic.fatigue")
            .redFlags("redflag.maidclinic.no_urine", "redflag.maidclinic.blood_stool")
            .primary(Treatment.ORS)
            .harmful(Treatment.ANTIBIOTIC, Treatment.AZITHROMYCIN)
            .complication("dehydration", 0.8F)
            .nameKey("condition.maidclinic.gastroenteritis")
            .build();

    /** 缺铁性贫血：铁剂 + 维生素 C，疗程以月计。 */
    public static final Condition IRON_DEFICIENCY_ANEMIA = Condition.builder("iron_deficiency_anemia")
            .category(Condition.Category.METABOLIC)
            .virulence(1.2F)
            .duration(900, 1200)
            .heartRate(16)
            .oxygen(-2)
            .respiratoryRate(2)
            .maxSeverity(2)
            .requiredDoses(2)
            .symptoms("symptom.maidclinic.fatigue", "symptom.maidclinic.dizziness",
                    "symptom.maidclinic.pallor", "symptom.maidclinic.palpitations")
            .redFlags("redflag.maidclinic.chest_pain", "redflag.maidclinic.syncope")
            .primary(Treatment.IRON)
            .build();

    /** 过敏性鼻炎：抗组胺药 + 避免过敏原。 */
    public static final Condition ALLERGIC_RHINITIS = Condition.builder("allergic_rhinitis")
            .category(Condition.Category.INFLAMMATION)
            .virulence(0.6F)
            .selfLimiting(0.0F)
            .duration(420, 600)
            .fever(0.1F)
            .heartRate(4)
            .respiratoryRate(3)
            .maxSeverity(2)
            .symptoms("symptom.maidclinic.sneeze", "symptom.maidclinic.itchy_nose",
                    "symptom.maidclinic.itchy_eyes", "symptom.maidclinic.runny_nose")
            .primary(Treatment.ANTIHISTAMINE)
            .harmful(Treatment.ANTIBIOTIC, Treatment.AZITHROMYCIN)
            .build();

    /** 失眠：先做睡眠卫生，褪黑素只短期使用。 */
    public static final Condition INSOMNIA = Condition.builder("insomnia")
            .category(Condition.Category.MENTAL)
            .virulence(0.6F)
            .selfLimiting(0.5F)
            .duration(480, 720)
            .fever(0.1F)
            .heartRate(6)
            .maxSeverity(2)
            .symptoms("symptom.maidclinic.insomnia", "symptom.maidclinic.daytime_sleepiness",
                    "symptom.maidclinic.poor_concentration")
            .primary(Treatment.MELATONIN)
            .build();

    /** 热衰竭（中暑的较轻阶段）：转移阴凉、补液、物理降温。 */
    public static final Condition HEAT_EXHAUSTION = Condition.builder("heat_exhaustion")
            .category(Condition.Category.TRAUMA)
            .virulence(3.0F)
            .selfLimiting(0.5F)
            .recoveryNeedsSupport(true)
            .duration(240, 360)
            .mortality(1.3F)
            .fever(0.9F)
            .heartRate(18)
            .bloodPressure(-10, -4)
            .respiratoryRate(4)
            .maxSeverity(3)
            .symptoms("symptom.maidclinic.dizziness", "symptom.maidclinic.sweating",
                    "symptom.maidclinic.nausea", "symptom.maidclinic.fatigue")
            .redFlags("redflag.maidclinic.confusion", "redflag.maidclinic.hot_dry_skin")
            .primary(Treatment.COOLING)
            .supportive(Treatment.ORS)
            .complication("dehydration", 0.45F)
            .build();

    /** 伤口感染：清创包扎，化脓时需要抗生素。 */
    public static final Condition WOUND_INFECTION = Condition.builder("wound_infection")
            .category(Condition.Category.INFECTION)
            .virulence(2.6F)
            .infectious(true, 0.10F)
            .duration(360, 480)
            .mortality(1.0F)
            .fever(0.7F)
            .heartRate(8)
            .maxSeverity(3)
            .symptoms("symptom.maidclinic.wound_pain", "symptom.maidclinic.wound_redness",
                    "symptom.maidclinic.wound_discharge", "symptom.maidclinic.fever")
            .redFlags("redflag.maidclinic.spreading_redness", "redflag.maidclinic.high_fever")
            .primary(Treatment.BANDAGE)
            .supportive(Treatment.ANTIBIOTIC, Treatment.AZITHROMYCIN, Treatment.DRESSING)
            .build();

    /** 轻度烫伤：冷水冲洗足够时间，不要冰敷、不要涂牙膏/酱油。 */
    public static final Condition MINOR_BURN = Condition.builder("minor_burn")
            .category(Condition.Category.TRAUMA)
            .virulence(1.2F)
            .selfLimiting(true)
            .duration(240, 360)
            .fever(0.3F)
            .heartRate(6)
            .maxSeverity(2)
            .symptoms("symptom.maidclinic.burn_pain", "symptom.maidclinic.burn_redness",
                    "symptom.maidclinic.blister")
            .redFlags("redflag.maidclinic.large_burn", "redflag.maidclinic.face_burn")
            .primary(Treatment.COOLING)
            .supportive(Treatment.DRESSING)
            .build();

    /** 低血糖：15 g 快速碳水，清醒后补充复合碳水。 */
    public static final Condition HYPOGLYCEMIA = Condition.builder("hypoglycemia")
            .category(Condition.Category.METABOLIC)
            .virulence(3.2F)
            .duration(90, 150)
            .mortality(1.1F)
            .heartRate(20)
            .bloodPressure(-4, -2)
            .respiratoryRate(3)
            .glucose(-2.0F)
            .maxSeverity(3)
            .symptoms("symptom.maidclinic.trembling", "symptom.maidclinic.sweating",
                    "symptom.maidclinic.palpitations", "symptom.maidclinic.confusion")
            .redFlags("redflag.maidclinic.unconscious", "redflag.maidclinic.seizure")
            .primary(Treatment.GLUCOSE)
            .build();

    /** 脱水：胃肠炎/热衰竭的并发症。 */
    public static final Condition DEHYDRATION = Condition.builder("dehydration")
            .category(Condition.Category.METABOLIC)
            .virulence(3.5F)
            .natural(false)
            .duration(300, 420)
            .mortality(1.4F)
            .fever(0.4F)
            .heartRate(14)
            .bloodPressure(-12, -6)
            .respiratoryRate(2)
            .maxSeverity(3)
            .symptoms("symptom.maidclinic.thirst", "symptom.maidclinic.dizziness",
                    "symptom.maidclinic.dark_urine", "symptom.maidclinic.fatigue")
            .redFlags("redflag.maidclinic.no_urine", "redflag.maidclinic.lethargy")
            .primary(Treatment.ORS)
            .build();

    /** 退热药过量：医源性，处理方式是停药并观察。 */
    public static final Condition MEDICATION_OVERUSE = Condition.builder("medication_overuse")
            .category(Condition.Category.IATROGENIC)
            .virulence(1.4F)
            .natural(false)
            .selfLimiting(true)
            .duration(420, 600)
            .fever(0.2F)
            .heartRate(5)
            .maxSeverity(2)
            .symptoms("symptom.maidclinic.nausea", "symptom.maidclinic.fatigue",
                    "symptom.maidclinic.abdominal_pain")
            .redFlags("redflag.maidclinic.jaundice", "redflag.maidclinic.vomiting")
            .harmful(Treatment.ANTIPYRETIC)
            .build();

    // ------------------------------------------------------------------ 慢性病
    // 慢性病默认出现在“慢性病模式”开启后；病程以 25–50 分钟计，
    // 需要多次、按间隔给药，属于“长期管理”而不是一次急救。

    /** 高血压：限盐、规律作息、长期服药；不处理会持续损伤。 */
    public static final Condition HYPERTENSION = Condition.builder("hypertension")
            .category(Condition.Category.CHRONIC)
            .virulence(2.0F)
            .duration(1800, 2400)
            .heartRate(4)
            .bloodPressure(16, 10)
            .respiratoryRate(1)
            .maxSeverity(3)
            .requiredDoses(4)
            .doseInterval(90)
            .symptoms("symptom.maidclinic.headache", "symptom.maidclinic.dizziness",
                    "symptom.maidclinic.neck_stiffness", "symptom.maidclinic.tinnitus",
                    "symptom.maidclinic.fatigue")
            .redFlags("redflag.maidclinic.severe_headache", "redflag.maidclinic.chest_pain",
                    "redflag.maidclinic.vision_loss", "redflag.maidclinic.confusion")
            .primary(Treatment.ANTIHYPERTENSIVE)
            .build();

    /** 2 型糖尿病：控制精制糖、规律进餐、长期用药；脱水是常见并发症。 */
    public static final Condition TYPE_2_DIABETES = Condition.builder("type_2_diabetes")
            .category(Condition.Category.CHRONIC)
            .virulence(2.2F)
            .duration(2400, 3000)
            .heartRate(6)
            .respiratoryRate(2)
            .glucose(3.0F)
            .maxSeverity(3)
            .requiredDoses(5)
            .doseInterval(100)
            .symptoms("symptom.maidclinic.polydipsia", "symptom.maidclinic.polyuria",
                    "symptom.maidclinic.polyphagia", "symptom.maidclinic.weight_loss",
                    "symptom.maidclinic.fatigue", "symptom.maidclinic.blurred_vision")
            .redFlags("redflag.maidclinic.acetone_breath", "redflag.maidclinic.confusion",
                    "redflag.maidclinic.vomiting", "redflag.maidclinic.no_urine")
            .primary(Treatment.ANTIDIABETIC)
            .harmful(Treatment.GLUCOSE)
            .complication("dehydration", 0.5F)
            .build();

    /** 慢性胃炎：规律饮食、抑酸；黑便/呕血提示出血，需就医。 */
    public static final Condition CHRONIC_GASTRITIS = Condition.builder("chronic_gastritis")
            .category(Condition.Category.CHRONIC)
            .virulence(1.0F)
            .duration(1500, 2100)
            .fever(0.2F)
            .heartRate(4)
            .maxSeverity(2)
            .requiredDoses(3)
            .doseInterval(90)
            .symptoms("symptom.maidclinic.epigastric_pain", "symptom.maidclinic.bloating",
                    "symptom.maidclinic.acid_reflux", "symptom.maidclinic.belching",
                    "symptom.maidclinic.nausea")
            .redFlags("redflag.maidclinic.black_stool", "redflag.maidclinic.hematemesis",
                    "redflag.maidclinic.weight_loss", "redflag.maidclinic.dysphagia")
            .primary(Treatment.ANTACID)
            .harmful(Treatment.ANTIBIOTIC, Treatment.AZITHROMYCIN)
            .build();

    /** 骨质疏松：钙+维生素D、负重运动、日照；突发剧痛可能提示骨折。 */
    public static final Condition OSTEOPOROSIS = Condition.builder("osteoporosis")
            .category(Condition.Category.CHRONIC)
            .virulence(1.0F)
            .duration(2400, 3000)
            .heartRate(2)
            .maxSeverity(2)
            .requiredDoses(4)
            .doseInterval(120)
            .symptoms("symptom.maidclinic.back_pain", "symptom.maidclinic.height_loss",
                    "symptom.maidclinic.easy_fracture", "symptom.maidclinic.fatigue")
            .redFlags("redflag.maidclinic.sudden_back_pain", "redflag.maidclinic.unable_to_stand")
            .primary(Treatment.CALCIUM)
            .build();

    private static final Map<String, Condition> BY_ID = new LinkedHashMap<>();

    static {
        for (Condition condition : List.of(
                VIRAL_URI,
                BACTERIAL_PHARYNGITIS,
                GASTROENTERITIS,
                DIETARY_GASTROENTERITIS,
                IRON_DEFICIENCY_ANEMIA,
                ALLERGIC_RHINITIS,
                INSOMNIA,
                HEAT_EXHAUSTION,
                WOUND_INFECTION,
                MINOR_BURN,
                HYPOGLYCEMIA,
                DEHYDRATION,
                MEDICATION_OVERUSE,
                HYPERTENSION,
                TYPE_2_DIABETES,
                CHRONIC_GASTRITIS,
                OSTEOPOROSIS)) {
            BY_ID.put(condition.id(), condition);
        }
    }

    public static Condition byId(String id) {
        return BY_ID.get(id);
    }

    public static Collection<Condition> all() {
        return BY_ID.values();
    }
}
