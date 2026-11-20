package com.maidclinic.registry;

import com.maidclinic.MaidClinic;
import com.maidclinic.health.Treatment;
import com.maidclinic.item.CareManualItem;
import com.maidclinic.item.ContractItem;
import com.maidclinic.item.DiagnosticItem;
import com.maidclinic.item.DoctorBadgeItem;
import com.maidclinic.item.HealthRecordItem;
import com.maidclinic.item.ManualContent;
import com.maidclinic.item.MaskItem;
import com.maidclinic.item.MedicineItem;
import com.maidclinic.item.SupportItem;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.BedItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.EnumMap;
import java.util.Map;

/**
 * 物品注册。
 * <p>
 * 命名约定：物品注册名与 {@link Treatment#itemName()} 一致，
 * 这样“治疗手段 → 物品”的映射不需要再维护第二张表。
 */
public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MaidClinic.MOD_ID);

    // ------------------------------------------------------------------ 检查工具

    public static final DeferredItem<DiagnosticItem> STETHOSCOPE = ITEMS.register("stethoscope",
            () -> new DiagnosticItem(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON),
                    DiagnosticItem.Mode.STETHOSCOPE));

    public static final DeferredItem<DiagnosticItem> THERMOMETER = ITEMS.register("thermometer",
            () -> new DiagnosticItem(new Item.Properties().stacksTo(1),
                    DiagnosticItem.Mode.THERMOMETER));

    public static final DeferredItem<DiagnosticItem> SPHYGMOMANOMETER = ITEMS.register("sphygmomanometer",
            () -> new DiagnosticItem(new Item.Properties().stacksTo(1),
                    DiagnosticItem.Mode.SPHYGMOMANOMETER));

    public static final DeferredItem<DiagnosticItem> OXIMETER = ITEMS.register("oximeter",
            () -> new DiagnosticItem(new Item.Properties().stacksTo(1),
                    DiagnosticItem.Mode.OXIMETER));

    public static final DeferredItem<DiagnosticItem> GLUCOMETER = ITEMS.register("glucometer",
            () -> new DiagnosticItem(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON),
                    DiagnosticItem.Mode.GLUCOMETER));

    // ------------------------------------------------------------------ 档案与手册

    public static final DeferredItem<HealthRecordItem> HEALTH_RECORD = ITEMS.register("health_record",
            () -> new HealthRecordItem(new Item.Properties().stacksTo(1)));

    /** 问诊后生成/更新的诊断单（可翻阅的书）。 */
    public static final DeferredItem<CareManualItem> DIAGNOSIS_NOTE = ITEMS.register("diagnosis_note",
            () -> new CareManualItem(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON)));

    public static final DeferredItem<CareManualItem> CARE_MANUAL = ITEMS.register("care_manual",
            () -> new CareManualItem(new Item.Properties()
                    .stacksTo(1)
                    .rarity(Rarity.UNCOMMON)
                    .component(DataComponents.WRITTEN_BOOK_CONTENT, ManualContent.create())));

    public static final DeferredItem<ContractItem> DOCTOR_CONTRACT = ITEMS.register("doctor_contract",
            () -> new ContractItem(new Item.Properties().stacksTo(1).rarity(Rarity.RARE)));

    public static final DeferredItem<MaskItem> MEDICAL_MASK = ITEMS.register("medical_mask",
            () -> new MaskItem(new Item.Properties().stacksTo(16)));

    // ------------------------------------------------------------------ 医师徽章（女仆饰品）

    /** 仅保留最高级徽章；等级仍会随医疗经验自动提升。 */
    public static final DeferredItem<DoctorBadgeItem> DOCTOR_BADGE_4 = ITEMS.register("doctor_badge_4",
            () -> new DoctorBadgeItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC), 4));

    /** 支援兵战备：给附近攻击模式女仆提供支援 buff。 */
    public static final DeferredItem<SupportItem> SUPPORT_KIT = ITEMS.register("support_kit",
            () -> new SupportItem(new Item.Properties().stacksTo(1).rarity(Rarity.RARE)));

    // ------------------------------------------------------------------ 药品与耗材

    public static final Map<Treatment, DeferredItem<MedicineItem>> MEDICINES =
            new EnumMap<>(Treatment.class);

    static {
        for (Treatment treatment : Treatment.values()) {
            MEDICINES.put(treatment, ITEMS.register(treatment.itemName(),
                    () -> new MedicineItem(new Item.Properties().stacksTo(16), treatment)));
        }
    }

    // ------------------------------------------------------------------ 方块

    public static final DeferredItem<BlockItem> FIRST_AID_KIT = ITEMS.registerSimpleBlockItem(ModBlocks.FIRST_AID_KIT);

    public static final DeferredItem<BedItem> HOSPITAL_BED = ITEMS.register("hospital_bed",
            () -> new BedItem(ModBlocks.HOSPITAL_BED.get(), new Item.Properties().stacksTo(1)));

    public static final DeferredItem<BlockItem> SMART_TERMINAL =
            ITEMS.registerSimpleBlockItem(ModBlocks.SMART_TERMINAL);

    private ModItems() {
    }
}
