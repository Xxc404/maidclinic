package com.maidclinic.health;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.entity.chatbubble.implement.TextChatBubbleData;
import com.maidclinic.ClinicConfig;
import com.maidclinic.ClinicSettings;
import com.maidclinic.compat.SickExpressions;
import com.maidclinic.item.ContractItem;
import com.maidclinic.item.DiagnosticItem;
import com.maidclinic.item.HealthRecordItem;
import com.maidclinic.item.MaskItem;
import com.maidclinic.item.MedicineItem;
import com.maidclinic.registry.ModEffects;
import com.maidclinic.registry.ModItems;
import com.maidclinic.util.Msg;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.BlockPos;
import org.joml.Vector3f;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundOpenBookPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.Filterable;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.WrittenBookItem;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.WeakHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 女仆诊所的核心逻辑：问诊、用药、病程推进、信任与签约。
 * <p>
 * 该类只依赖 TLM 的 {@link EntityMaid} 类型本身，不依赖其内部数据接口，
 * 因此与其它女仆附属模组冲突的概率很低。
 */
public final class ClinicService {
    /** 工作效率 modifier 的固定 id，便于随时更新/移除。 */
    private static final ResourceLocation WORK_EFFICIENCY_ID =
            ResourceLocation.fromNamespaceAndPath("maidclinic", "work_efficiency");
    private static final ResourceLocation CONSTITUTION_HEALTH_ID =
            ResourceLocation.fromNamespaceAndPath("maidclinic", "constitution_health");
    private static final ResourceLocation CONSTITUTION_WORK_ID =
            ResourceLocation.fromNamespaceAndPath("maidclinic", "constitution_work");
    private static final ResourceLocation CARE_HEALTH_ID =
            ResourceLocation.fromNamespaceAndPath("maidclinic", "care_health");
    private static final ResourceLocation CARE_ATTACK_ID =
            ResourceLocation.fromNamespaceAndPath("maidclinic", "care_attack");
    private static final ResourceKey<DamageType> ILLNESS_DAMAGE =
            ResourceKey.create(Registries.DAMAGE_TYPE,
                    ResourceLocation.fromNamespaceAndPath("maidclinic", "illness"));

    /**
     * 《心婚誓约》的灵体类名。
     * <p>灵体只是 {@link EntityMaid} 的子类，并不是活着的女仆：不发病、不被传染，
     * 也不作为传染源。用类名软兼容，避免诊所对《心婚誓约》产生编译期依赖；
     * 未安装该模组时字符串永远不匹配。</p>
     */
    private static final String SPIRIT_ENTITY_CLASS = "com.example.maidmarriage.entity.MaidSpiritEntity";

    /**
     * 《心婚誓约》的后代类名。
     * <p>只用于软判断“这是不是一个需要阻止被母亲抱走的后代”，不做编译期依赖。
     * 未安装该模组时字符串永远不匹配。</p>
     */
    private static final String CHILD_ENTITY_CLASS = "com.example.maidmarriage.entity.MaidChildEntity";

    /** 痊愈后恢复期：虚弱Ⅱ + 缓慢Ⅰ，持续 2 分钟。 */
    private static final int RECOVERY_TICKS = 20 * 120;

    /** 完整睡完一觉的劳累减免（最低扣到 0）。 */
    private static final float SLEEP_EXERTION_RELIEF = 80.0F;
    /** 白天入睡时，至少要睡这么久才算“完整一觉”。 */
    private static final int MIN_FULL_SLEEP_TICKS = 20 * 120;

    /** 最近一次睡觉的床，用于疾病伤害把女仆打醒后恢复真实睡眠状态。 */
    private static final Map<UUID, BlockPos> SLEEP_SPOTS = new WeakHashMap<>();
    /**
     * 正在诊所病床上强制卧床的后代 → 床位。
     * <p>用于在《心婚誓约》每 tick 把婴儿抱回母亲身上时拒绝骑乘，
     * 避免婴儿在病床和母亲怀里之间来回变动。</p>
     */
    private static final Map<UUID, BlockPos> HOSPITAL_BED_REST = new WeakHashMap<>();
    /** 记录女仆本次入睡的时刻与是否在夜里入睡，用于判断“是否完整睡了一觉”。 */
    private static final Map<UUID, SleepStart> SLEEP_START = new WeakHashMap<>();

    private record SleepStart(long tick, boolean night) {
    }

    /** 失眠女仆本次“入睡对策”的有效期；到期后重新 50/50 抽取。 */
    private static final int INSOMNIA_PLAN_TICKS = 20 * 60;
    /** 失眠女仆入睡对策：强行睁眼还是根本睡不着。 */
    private static final Map<UUID, InsomniaPlan> INSOMNIA_PLANS = new WeakHashMap<>();

    private record InsomniaPlan(boolean eyesOpen, long until) {
    }

    /** 恢复期（虚弱Ⅱ/缓慢Ⅰ）的结束时刻，用于防止其它模组的无限虚弱覆盖。 */
    private static final Map<UUID, Long> RECOVERY_UNTIL = new WeakHashMap<>();

    /** 生病女仆睡觉时被强制维持的目标血量（TLM 睡眠回血会被压回这个值）。 */
    private static final Map<UUID, Float> SLEEP_HEALTH_HOLD = new WeakHashMap<>();

    /** 最近一次已发送死亡提示的游戏刻，用于同一次死亡被重复派发时去重。 */
    private static final Map<UUID, Long> DEATH_MESSAGE_TICKS = new HashMap<>();

    /** 这些伤害来自疾病本身或环境机制，不应再叠加“伤口感染”。 */
    private static final Set<String> IGNORED_DAMAGE = Set.of(
            "maidclinic.illness",
            "magic", "wither", "dragon_breath", "sonic_boom", "starve", "freeze", "drown",
            "outside_border", "generic", "generic_kill", "in_wall", "cramming", "fly_into_wall");
    private static final Set<String> FIRE_DAMAGE = Set.of(
            "in_fire", "on_fire", "lava", "hot_floor", "campfire", "unattended_campfire",
            "fireball", "fireworks", "lightning_bolt");
    private static final Set<String> COMBAT_DAMAGE = Set.of(
            "mob", "mob_attack", "player", "arrow", "trident", "explosion", "fireball", "sting", "thorns");

    /** TLM 任务 id 的 path：只读字符串，不依赖具体任务类，兼容其它附属新增任务。 */
    private static final Set<String> COMBAT_TASKS = Set.of(
            "attack", "ranged_attack", "crossbow_attack", "danmaku_attack",
            "trident_attack", "gun_attack");
    private static final Set<String> LABOR_TASKS = Set.of(
            "farm", "sugar_cane", "melon", "cocoa", "honey", "grass", "snow", "feed",
            "shears", "milk", "torch", "feed_animal", "extinguishing", "fishing");
    private static final Set<String> ALLERGEN_TASKS = Set.of(
            "farm", "sugar_cane", "melon", "cocoa", "honey", "grass", "shears", "feed_animal");

    private ClinicService() {
    }

    // ------------------------------------------------------------------ 交互入口

    /**
     * 判断某个物品是否由本模组处理。
     * <p>客户端也用它来做交互预测：只有本模组的物品才会被拦截，
     * 避免误伤 TLM 自己的交互（背包、任务、驯服等）。</p>
     */
    public static boolean isCareItem(ItemStack stack) {
        Item item = stack.getItem();
        return item instanceof DiagnosticItem
                || item instanceof MedicineItem
                || item instanceof HealthRecordItem
                || item instanceof ContractItem
                || item instanceof MaskItem
                || stack.is(ModItems.CARE_MANUAL.get());
    }

    /**
     * 处理玩家手持物品右键女仆。
     *
     * @return true 表示“本模组已处理”，调用方应取消原版/TLM 的交互
     */
    public static boolean handleInteraction(ServerPlayer player, EntityMaid maid, ItemStack stack, InteractionHand hand) {
        if (!maid.isOwnedBy(player)) {
            return false;
        }
        Item item = stack.getItem();
        if (item instanceof DiagnosticItem diagnostic) {
            examine(player, maid, diagnostic.mode());
            return true;
        }
        if (item instanceof MedicineItem medicine) {
            treat(player, maid, medicine.treatment(), stack);
            return true;
        }
        if (item instanceof HealthRecordItem) {
            report(player, maid);
            return true;
        }
        if (item instanceof ContractItem) {
            signContract(player, maid, stack);
            return true;
        }
        if (item instanceof MaskItem) {
            wearMask(player, maid, stack);
            return true;
        }
        if (item instanceof WrittenBookItem) {
            // 自定义 WrittenBookItem 不会被 ServerPlayer.openItemGui 识别（它只认原版书），
            // 所以直接发打开书本的数据包。
            player.connection.send(new ClientboundOpenBookPacket(hand));
            return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ 问诊

    private static void examine(ServerPlayer player, EntityMaid maid, DiagnosticItem.Mode mode) {
        ServerLevel level = (ServerLevel) maid.level();
        ClinicData data = ClinicData.get(level);
        MaidRecord record = data.recordOf(maid.getUUID(), maid.getName().getString());
        record.profile(level.random);
        Vitals vitals = Vitals.of(record);
        String name = maid.getName().getString();

        List<Component> pages = new ArrayList<>();
        pages.add(Msg.aqua("message.maidclinic.exam.header", name));

        if (mode == DiagnosticItem.Mode.STETHOSCOPE && record.exams() == 0) {
            pages.addAll(profilePages(maid, record.profile(level.random)));
        }

        switch (mode) {
            case THERMOMETER -> pages.add(Msg.tr("message.maidclinic.temperature",
                    vitals.temperatureText(),
                    Msg.tr(vitals.hasFever() ? "vitals.maidclinic.fever" : "vitals.maidclinic.afebrile")));
            case SPHYGMOMANOMETER -> pages.add(Msg.tr("message.maidclinic.blood_pressure",
                    vitals.pressureText(), vitals.heartRate()));
            case OXIMETER -> pages.add(Msg.tr("message.maidclinic.oximetry",
                    vitals.oxygen() + "%", vitals.heartRate(), vitals.respiratoryRate()));
            case GLUCOMETER -> pages.add(Msg.tr("message.maidclinic.glucose",
                    vitals.glucoseText(), Msg.tr(vitals.glucoseLabelKey())));
            case STETHOSCOPE -> {
                pages.add(Msg.tr("message.maidclinic.vitals",
                        vitals.temperatureText(), vitals.heartRate(), vitals.pressureText(),
                        vitals.respiratoryRate(), vitals.oxygen() + "%"));
                pages.add(Msg.tr("message.maidclinic.exam.diagnosis", diagnosisText(record)));
                if (record.conditions().isEmpty()) {
                    pages.add(Msg.gray("advice.maidclinic.healthy"));
                } else {
                    Condition first = Conditions.byId(record.conditions().get(0).conditionId());
                    if (first != null) {
                        pages.add(Msg.gray(first.adviceKey()));
                        pages.add(Msg.gray("message.maidclinic.exam.cause",
                                Msg.tr(record.conditions().get(0).cause().nameKey())));
                    }
                }
                List<MaidBehavior.Reading> readings = record.behavior().readings(3);
                if (!readings.isEmpty()) {
                    pages.add(Msg.tr("message.maidclinic.record.behavior", behaviorText(readings)));
                }
            }
        }

        updateDiagnosisNote(player, maid, pages);
        Msg.bar(player, Msg.gray("message.maidclinic.exam.note_updated", name));

        long now = level.getGameTime();
        boolean awardTrust = record.exams() == 0
                || now - record.lastExamTick() >= ClinicSettings.EXAM_VALID_TICKS;
        record.setLastExamTick(now);
        if (awardTrust) {
            record.addTrust(ClinicSettings.TRUST_EXAM);
        }
        record.setExams(record.exams() + 1);
        data.setDirty();
        level.playSound(null, maid.blockPosition(), SoundEvents.VILLAGER_YES, SoundSource.NEUTRAL, 0.6F, 1.3F);
    }

    /** 问诊结果写进玩家背包里的“问诊记录”，同一只女仆会更新同一本。 */
    private static void updateDiagnosisNote(ServerPlayer player, EntityMaid maid, List<Component> pages) {
        UUID maidId = maid.getUUID();
        ItemStack note = ItemStack.EMPTY;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!stack.is(ModItems.DIAGNOSIS_NOTE.get())) {
                continue;
            }
            CustomData data = stack.get(DataComponents.CUSTOM_DATA);
            if (data != null && data.copyTag().contains("maid")
                    && maidId.equals(data.copyTag().getUUID("maid"))) {
                note = stack;
                break;
            }
        }
        boolean newNote = note.isEmpty();
        if (newNote) {
            note = new ItemStack(ModItems.DIAGNOSIS_NOTE.get());
        }

        CompoundTag noteTag = new CompoundTag();
        noteTag.putUUID("maid", maidId);
        note.set(DataComponents.CUSTOM_DATA, CustomData.of(noteTag));

        String title = "问诊记录 · " + maid.getName().getString();
        if (title.length() > WrittenBookContent.TITLE_MAX_LENGTH) {
            title = title.substring(0, WrittenBookContent.TITLE_MAX_LENGTH);
        }
        List<Filterable<Component>> bookPages = new ArrayList<>(pages.size());
        for (Component page : pages) {
            bookPages.add(Filterable.passThrough(page));
        }
        note.set(DataComponents.WRITTEN_BOOK_CONTENT, new WrittenBookContent(
                Filterable.passThrough(title), "女仆诊所", 0, bookPages, true));

        // 必须先把书内容写好再放进背包，否则客户端可能拿到一个没有内容的空书。
        if (newNote && !player.getInventory().add(note)) {
            player.drop(note, false);
        }
        player.getInventory().setChanged();
        player.containerMenu.broadcastChanges();
    }

    // ------------------------------------------------------------------ 用药

    private static void treat(ServerPlayer player, EntityMaid maid, Treatment treatment, ItemStack stack) {
        ServerLevel level = (ServerLevel) maid.level();
        ClinicData data = ClinicData.get(level);
        MaidRecord record = data.recordOf(maid.getUUID(), maid.getName().getString());
        long now = level.getGameTime();
        String maidName = maid.getName().getString();
        MaidProfile profile = record.profile(level.random);

        if (treatment == Treatment.ANTIBIOTIC && profile.penicillinAllergy()) {
            Msg.chat(player, Msg.red("message.maidclinic.penicillin_allergy", maidName));
            return;
        }

        if (record.conditions().isEmpty()) {
            if (treatment == Treatment.ANTIBIOTIC || treatment == Treatment.AZITHROMYCIN) {
                Msg.chat(player, Msg.yellow("message.maidclinic.antibiotic_without_infection", maidName));
                record.addTrust(-2);
                data.setDirty();
            } else {
                Msg.chat(player, Msg.gray("message.maidclinic.no_condition", maidName));
            }
            return;
        }

        if (!treatment.firstAid() && ClinicConfig.REQUIRE_EXAM.get()) {
            boolean neverExamined = record.lastExamTick() == Long.MIN_VALUE;
            if (neverExamined || now - record.lastExamTick() > ClinicSettings.EXAM_VALID_TICKS) {
                Msg.chat(player, Msg.yellow("message.maidclinic.need_exam"));
                return;
            }
        }

        ActiveCondition target = findTarget(record, treatment);
        if (target == null) {
            Msg.chat(player, Msg.yellow("message.maidclinic.no_indication",
                    Msg.tr(treatment.translationKey()), diagnosisText(record)));
            if (treatment == Treatment.ANTIBIOTIC || treatment == Treatment.AZITHROMYCIN) {
                Msg.chat(player, Msg.gray("message.maidclinic.antibiotic_only_bacterial"));
            }
            return;
        }

        Condition definition = Conditions.byId(target.conditionId());
        if (definition == null) {
            return;
        }

        boolean applied;
        if (definition.primary().contains(treatment)) {
            applied = applyPrimary(player, level, maid, record, target, definition, treatment, now);
        } else if (definition.supportive().contains(treatment)) {
            applied = applySupportive(player, level, maid, record, target, definition, treatment);
        } else {
            applied = applyHarmful(player, level, maid, record, target, definition, treatment);
        }

        if (applied) {
            stack.shrink(1);
            if (treatment == Treatment.ANTIPYRETIC) {
                trackAntipyretic(player, level, maid, record, now);
            }
            data.setDirty();
        }
    }

    private static ActiveCondition findTarget(MaidRecord record, Treatment treatment) {
        ActiveCondition best = null;
        int bestScore = -1;
        for (ActiveCondition active : record.conditions()) {
            Condition definition = Conditions.byId(active.conditionId());
            if (definition == null) {
                continue;
            }
            int score;
            if (definition.primary().contains(treatment)) {
                score = 3;
            } else if (definition.supportive().contains(treatment)) {
                score = 2;
            } else if (definition.harmful().contains(treatment)) {
                score = 1;
            } else {
                continue;
            }
            // 同优先级下优先处理更严重的那一条
            int finalScore = score * 10 + active.severity();
            if (finalScore > bestScore) {
                bestScore = finalScore;
                best = active;
            }
        }
        return best;
    }

    private static boolean applyPrimary(ServerPlayer player, ServerLevel level, EntityMaid maid, MaidRecord record,
                                        ActiveCondition active, Condition definition, Treatment treatment, long now) {
        String maidName = maid.getName().getString();
        int required = definition.requiredDoses();
        if (required > 1) {
            int intervalTicks = Math.max(20, definition.doseIntervalSeconds() * 20);
            boolean firstDose = active.lastDoseTick() == Long.MIN_VALUE;
            if (!firstDose && now - active.lastDoseTick() < intervalTicks) {
                long wait = (intervalTicks - (now - active.lastDoseTick())) / 20L;
                Msg.chat(player, Msg.yellow("message.maidclinic.dose_too_soon", wait));
                return false;
            }
            active.setDoses(active.doses() + 1);
            active.setLastDoseTick(now);
            if (active.doses() < required) {
                Msg.chat(player, Msg.green("message.maidclinic.dose_progress",
                        maidName, active.doses(), required, Msg.tr(definition.nameKey())));
                return true;
            }
        }
        active.setTreated(true);
        active.setDurationTicks(active.elapsedTicks() + 20 * 20);
        record.addTrust(ClinicSettings.TRUST_TREAT);
        Msg.chat(player, Msg.green("message.maidclinic.treated",
                maidName, Msg.tr(treatment.translationKey()), Msg.tr(definition.nameKey())));
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                maid.getX(), maid.getY() + 1.2D, maid.getZ(), 8, 0.3D, 0.3D, 0.3D, 0.0D);
        level.playSound(null, maid.blockPosition(), SoundEvents.BOTTLE_FILL, SoundSource.NEUTRAL, 0.7F, 1.4F);
        return true;
    }

    private static boolean applySupportive(ServerPlayer player, ServerLevel level, EntityMaid maid, MaidRecord record,
                                           ActiveCondition active, Condition definition, Treatment treatment) {
        active.setSeverity(Math.max(1, active.severity() - 1));
        active.setSupported(true);
        int remaining = active.remainingTicks();
        active.setDurationTicks(active.elapsedTicks() + Math.max(200, remaining * 3 / 4));
        record.addTrust(ClinicSettings.TRUST_SUPPORT);
        Msg.chat(player, Msg.green("message.maidclinic.supportive",
                maid.getName().getString(), Msg.tr(treatment.translationKey()), Msg.tr(definition.nameKey())));
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                maid.getX(), maid.getY() + 1.2D, maid.getZ(), 4, 0.25D, 0.25D, 0.25D, 0.0D);
        return true;
    }

    private static boolean applyHarmful(ServerPlayer player, ServerLevel level, EntityMaid maid, MaidRecord record,
                                        ActiveCondition active, Condition definition, Treatment treatment) {
        active.setSeverity(Math.min(definition.maxSeverity(), active.severity() + 1));
        record.addTrust(-ClinicSettings.TRUST_WRONG);
        record.addWrongDose();
        record.addCarePoints(-3);
        Msg.chat(player, Msg.red("message.maidclinic.wrong_medication",
                maid.getName().getString(), Msg.tr(treatment.translationKey()), Msg.tr(definition.nameKey())));
        if (treatment == Treatment.ANTIBIOTIC || treatment == Treatment.AZITHROMYCIN) {
            Msg.chat(player, Msg.gray("message.maidclinic.antibiotic_explain"));
        }
        maid.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 20 * 8, 0, true, false, false));
        level.playSound(null, maid.blockPosition(), SoundEvents.VILLAGER_NO, SoundSource.NEUTRAL, 0.8F, 0.9F);
        return true;
    }

    private static void trackAntipyretic(ServerPlayer player, ServerLevel level, EntityMaid maid,
                                         MaidRecord record, long now) {
        long day = level.getDayTime() / 24000L;
        if (record.doseDay() != day) {
            record.setDoseDay(day);
            record.setAntipyreticDoses(0);
        }
        record.setAntipyreticDoses(record.antipyreticDoses() + 1);
        if (record.antipyreticDoses() > ClinicSettings.MAX_ANTIPYRETIC_DOSES_PER_DAY
                && !record.hasCondition("medication_overuse")) {
            addCondition(record, Conditions.MEDICATION_OVERUSE, 2, Trigger.IATROGENIC, level.random);
            Msg.chat(player, Msg.red("message.maidclinic.overdose", maid.getName().getString()));
        }
    }

    // ------------------------------------------------------------------ 健康档案

    private static void report(ServerPlayer player, EntityMaid maid) {
        for (Component line : buildReportLines(maid, true)) {
            Msg.chat(player, line);
        }
    }

    /** 智能终端/聊天共用的完整档案文本；timed=false 时省略会持续变化的时间字段。 */
    public static List<Component> buildReportLines(EntityMaid maid, boolean timed) {
        ServerLevel level = (ServerLevel) maid.level();
        MaidRecord record = ClinicData.get(level).recordOf(maid.getUUID(), maid.getName().getString());
        record.profile(level.random);
        Vitals vitals = Vitals.of(record);
        String name = maid.getName().getString();
        List<Component> lines = new ArrayList<>();

        lines.add(Msg.gold("message.maidclinic.record.header", name));
        MaidProfile profile = record.profile(level.random);
        lines.add(Msg.tr("message.maidclinic.record.profile",
                Msg.tr(profile.bloodTypeKey()), profile.bmiText(), Msg.tr(profile.bmiCategoryKey()),
                profile.penicillinAllergy()
                        ? Msg.tr("profile.maidclinic.allergy.penicillin")
                        : Msg.tr("profile.maidclinic.allergy.none")));
        lines.add(Msg.tr("message.maidclinic.vitals",
                vitals.temperatureText(), vitals.heartRate(), vitals.pressureText(),
                vitals.respiratoryRate(), vitals.oxygen() + "%"));

        List<MaidBehavior.Reading> readings = record.behavior().readings(3);
        if (!readings.isEmpty()) {
            lines.add(Msg.tr("message.maidclinic.record.behavior", behaviorText(readings)));
        }

        if (record.conditions().isEmpty()) {
            lines.add(Msg.green("message.maidclinic.record.healthy"));
        } else {
            for (ActiveCondition active : record.conditions()) {
                Condition definition = Conditions.byId(active.conditionId());
                if (definition == null) {
                    continue;
                }
                if (timed) {
                    int percent = Math.round(active.progressRatio() * 100.0F);
                    lines.add(Msg.tr("message.maidclinic.record.condition",
                            Msg.tr(definition.nameKey()), severityText(active.severity()),
                            percent + "%", formatTicks(active.remainingTicks())));
                } else {
                    lines.add(Msg.tr("message.maidclinic.record.condition_gui",
                            Msg.tr(definition.nameKey()), severityText(active.severity())));
                }
                lines.add(Msg.gray("message.maidclinic.record.symptoms", symptomText(definition)));
                lines.add(Msg.gray("message.maidclinic.record.cause", Msg.tr(active.cause().nameKey())));
            }
            Condition first = Conditions.byId(record.conditions().get(0).conditionId());
            if (first != null) {
                lines.add(Msg.gray(first.adviceKey()));
            }
        }

        lines.add(Msg.tr("message.maidclinic.record.trust",
                record.trust(), Msg.tr(record.trustLevel().translationKey()),
                record.cured(), record.contracted()
                        ? Msg.tr("message.maidclinic.record.contracted_yes")
                        : Msg.tr("message.maidclinic.record.contracted_no")));

        lines.add(Msg.tr("message.maidclinic.record.care",
                record.healthyDays(), record.carePoints(), record.careTier()));

        if (!workEfficiencyEnabled(level, maid)) {
            lines.add(Msg.gray("message.maidclinic.record.efficiency_disabled"));
        } else {
            int temporary = Math.round(temporaryEfficiencyPenalty(record) * 100.0F);
            int permanent = Math.round(record.efficiencyPenalty() * 100.0F);
            int bonus = Math.round(healthyBonus(level, maid, record) * 100.0F);
            if (temporary + permanent <= 0) {
                if (bonus > 0) {
                    lines.add(Msg.green("message.maidclinic.record.healthy_bonus", "+" + bonus + "%"));
                } else {
                    lines.add(Msg.green("message.maidclinic.record.efficiency_normal"));
                }
            } else {
                lines.add(Msg.tr("message.maidclinic.record.efficiency",
                        "-" + Math.min(50, temporary + permanent) + "%",
                        "-" + temporary + "%",
                        "-" + permanent + "%"));
                if (permanent > 0 && !record.sequelae().isEmpty()) {
                    lines.add(Msg.gray("message.maidclinic.record.sequelae", sequelaText(record)));
                }
            }
        }
        lines.add(Msg.gray("message.maidclinic.disclaimer"));
        return lines;
    }

    /** 智能终端：把同一份完整档案输出给玩家。 */
    public static void reportHealth(ServerPlayer player, EntityMaid maid) {
        report(player, maid);
    }

    // ------------------------------------------------------------------ 家庭医生

    private static void signContract(ServerPlayer player, EntityMaid maid, ItemStack stack) {
        ServerLevel level = (ServerLevel) maid.level();
        ClinicData data = ClinicData.get(level);
        MaidRecord record = data.recordOf(maid.getUUID(), maid.getName().getString());

        if (record.contracted()) {
            Msg.chat(player, Msg.gray("message.maidclinic.contract.already", maid.getName().getString()));
            return;
        }
        if (record.trust() < ClinicSettings.CONTRACT_TRUST || record.cured() < ClinicSettings.CONTRACT_CURES) {
            Msg.chat(player, Msg.yellow("message.maidclinic.contract.requirements",
                    record.trust(), ClinicSettings.CONTRACT_TRUST,
                    record.cured(), ClinicSettings.CONTRACT_CURES));
            return;
        }
        record.setContracted(true);
        record.addTrust(30);
        stack.shrink(1);
        data.setDirty();
        Msg.chat(player, Msg.gold("message.maidclinic.contract.signed", maid.getName().getString()));
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                maid.getX(), maid.getY() + 1.4D, maid.getZ(), 20, 0.4D, 0.5D, 0.4D, 0.0D);
        level.playSound(null, maid.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.NEUTRAL, 0.7F, 1.6F);
        maid.setFavorability(maid.getFavorability() + 5);
    }

    private static void wearMask(ServerPlayer player, EntityMaid maid, ItemStack stack) {
        ServerLevel level = (ServerLevel) maid.level();
        ClinicData data = ClinicData.get(level);
        MaidRecord record = data.recordOf(maid.getUUID(), maid.getName().getString());
        long now = level.getGameTime();
        if (record.masked(now)) {
            Msg.chat(player, Msg.gray("message.maidclinic.mask.already", maid.getName().getString()));
            return;
        }
        record.setMaskedUntil(now + ClinicSettings.MASK_DURATION_TICKS);
        stack.shrink(1);
        data.setDirty();
        Msg.chat(player, Msg.green("message.maidclinic.mask.worn", maid.getName().getString(), 3));
    }

    // ------------------------------------------------------------------ 每刻推进

    public static void tickMaid(ServerLevel level, EntityMaid maid) {
        if (isSpirit(maid)) {
            // 灵体没有生理病程：清掉旧存档残留的疾病/后遗症后，完全不参与发病与传染。
            clearSpiritIllness(level, maid);
            return;
        }
        ClinicData data = ClinicData.get(level);
        MaidRecord record = data.recordOf(maid.getUUID(), maid.getName().getString());
        long now = level.getGameTime();
        boolean firstSeen = record.lastSeenTick() == 0L;
        if (firstSeen) {
            maybeInheritRecord(level, maid, record);
        }
        MaidProfile profile = record.profile(level.random);
        if (profile.tickDaily(level.getDayTime() / 24000L, record.conditions().isEmpty(), record.trust())) {
            boolean healthy = record.conditions().isEmpty() && record.sequelae().isEmpty();
            record.advanceHealthyDay(healthy);
            data.setDirty();
        }
        record.setLastSeenTick(now);

        // 先采样行为：淋雨、受热、战斗、劳累、熬夜、饥饿、压力、过敏原都会变成暴露值
        boolean changed = record.behavior().sample(level, maid.blockPosition(), behaviorSignals(level, maid),
                now, level.getGameTime() % 100 == 0);

        float health = maid.getHealth();
        if (!firstSeen && health < record.lastHealth() - 1.0F) {
            changed |= onHealthDrop(level, maid, record, health);
        }
        record.setLastHealth(health);

        changed |= progressConditions(level, maid, record, now);
        suppressCriticalBuffs(maid, record);
        applyCriticalDamage(level, maid, record);
        maybeSleepDeath(level, maid, record, now);
        applyWorkEfficiency(level, maid, record);
        applyConstitution(level, maid, record);
        applyCareAttributes(maid, record);
        claimCareRewards(level, maid, record);
        applySequelaEffects(level, maid, record);
        enforceRecovery(level, maid);
        changed |= updateRest(level, maid, record);
        changed |= updateSleepRecovery(level, maid, record, now);
        sickAura(level, maid, record);
        changed |= forcedWorkFeedback(level, maid, record);
        spawnSickParticles(level, maid, record);

        if (record.conditions().size() < ClinicSettings.MAX_ACTIVE_CONDITIONS) {
            changed |= maybeContagion(level, maid, record);
            changed |= maybeOnset(level, maid, record, chronicEnabled(level, maid));
        }

        if (changed) {
            data.setDirty();
            syncMarker(maid, record);
        } else if (!record.conditions().isEmpty() && maid.getEffect(ModEffects.UNWELL) == null) {
            // 状态图标到期但病还没好时补一次，避免“看不见症状”
            syncMarker(maid, record);
        }
        maybeWarnBehavior(level, maid, record, now);
        maybeSpeak(level, maid, record, now);
        sickSleepSound(level, maid, record);
        rememberSleepSpot(maid);
        if (!record.conditions().isEmpty()) {
            if (maid.isSleeping()) {
                // 睡觉时表情会被《更多动画》的 one-shot 拦截，这里更勤地刷新持久表情
                if (level.getGameTime() % 40 == 0) {
                    SickExpressions.showSleeping(maid);
                }
            } else if (level.getGameTime() % 160 == 0) {
                SickExpressions.show(maid);
                // 顺手清掉旧版本/旧存档残留的睡眠表情键，避免醒着还挂着 chijing 等表情
                SickExpressions.clearStale(maid);
            }
        } else if (!maid.isSleeping()) {
            // 病已好但身上可能还留着旧表情键
            SickExpressions.clearStale(maid);
        }
    }

    /** 是否为《心婚誓约》的灵体（含其子类）：向上遍历类名，不引入编译期依赖。 */
    private static boolean isSpirit(EntityMaid maid) {
        for (Class<?> type = maid.getClass(); type != null && type != EntityMaid.class;
             type = type.getSuperclass()) {
            if (SPIRIT_ENTITY_CLASS.equals(type.getName())) {
                return true;
            }
        }
        return false;
    }

    /** 是否为《心婚誓约》的后代（含其子类）：只做类名软判断，不引入编译期依赖。 */
    public static boolean isMaidChild(EntityMaid maid) {
        for (Class<?> type = maid.getClass(); type != null && type != EntityMaid.class;
             type = type.getSuperclass()) {
            if (CHILD_ENTITY_CLASS.equals(type.getName())) {
                return true;
            }
        }
        return false;
    }

    /** 灵体不该带病：旧存档里已经生病的灵体清掉疾病/后遗症与病容标记。 */
    private static void clearSpiritIllness(ServerLevel level, EntityMaid maid) {
        ClinicData data = ClinicData.get(level);
        MaidRecord record = data.find(maid.getUUID());
        boolean diseased = record != null
                && (!record.conditions().isEmpty() || !record.sequelae().isEmpty());
        boolean marked = maid.getEffect(ModEffects.UNWELL) != null
                || maid.getEffect(ModEffects.SICK_FACE) != null;
        if (!diseased && !marked) {
            return;
        }
        if (record != null) {
            record.conditions().clear();
            record.sequelae().clear();
            record.setResting(false);
            record.setForcedWork(false);
            data.setDirty();
        }
        maid.removeEffect(ModEffects.UNWELL);
        releaseHospitalBed(maid);
        SickExpressions.clear(maid);
    }

    /** 把女仆实体的状态整理成不依赖 TLM 的采样信号。 */
    private static MaidBehavior.Signals behaviorSignals(ServerLevel level, EntityMaid maid) {
        MaidBehavior.Signals signals = new MaidBehavior.Signals();
        BlockPos pos = maid.blockPosition();
        signals.raining = level.isRainingAt(pos);
        signals.wet = signals.raining || maid.isInWaterRainOrBubble();
        signals.inWater = maid.isInWater();
        signals.biomeTemperature = level.getBiome(pos).value().getBaseTemperature();
        signals.cold = signals.biomeTemperature < 0.3F;
        signals.hot = signals.biomeTemperature > 0.95F;
        signals.sleeping = maid.isSleeping();
        signals.sitting = maid.isMaidInSittingPose();
        signals.passenger = maid.isPassenger();
        signals.night = level.isNight();
        signals.workTime = Activity.WORK.equals(maid.getScheduleDetail());

        String taskPath = taskPath(maid);
        signals.laborTask = LABOR_TASKS.contains(taskPath);
        signals.combatTask = COMBAT_TASKS.contains(taskPath);
        signals.allergenTask = ALLERGEN_TASKS.contains(taskPath);
        signals.fishingTask = "fishing".equals(taskPath);

        boolean resting = signals.sleeping || signals.sitting || signals.passenger;
        signals.working = !resting && signals.workTime
                && !"idle".equals(taskPath) && !"board_games".equals(taskPath);
        signals.moving = maid.getDeltaMovement().horizontalDistanceSqr() > 0.003D;
        signals.fighting = !resting && (maid.getTarget() != null || signals.combatTask);
        signals.onFire = maid.getRemainingFireTicks() > 0 || maid.isInLava();
        signals.hunger = maid.getHunger();
        signals.favorability = maid.getFavorability();
        signals.healthRatio = maid.getMaxHealth() <= 0.0F ? 1.0F : maid.getHealth() / maid.getMaxHealth();
        return signals;
    }

    private static String taskPath(EntityMaid maid) {
        var task = maid.getTask();
        if (task == null || task.getUid() == null) {
            return "";
        }
        return task.getUid().getPath();
    }

    /**
     * 生病休息：只记录状态并停止闲逛，不再强制睡床/改睡姿，避免与 TLM 原生床与 AI 冲突。
     * 分配工作或空手右键即可叫醒她。
     */
    private static boolean updateRest(ServerLevel level, EntityMaid maid, MaidRecord record) {
        boolean sick = !record.conditions().isEmpty();
        String task = taskPath(maid);
        boolean wantsWork = !"idle".equals(task);
        boolean clearedForcedWork = false;
        if (!sick && record.forcedWork()) {
            record.setForcedWork(false);
            clearedForcedWork = true;
        }

        if (!sick || wantsWork || record.forcedWork()) {
            releaseHospitalBed(maid);
            if (!record.resting()) {
                return clearedForcedWork;
            }
            if (maid.isSleeping()) {
                maid.stopSleeping();
            }
            maid.setPose(Pose.STANDING);
            maid.setInSittingPose(false);
            record.setResting(false);
            return true;
        }

        if (record.resting()) {
            return false;
        }
        record.setResting(true);
        maid.getNavigation().stop();
        return true;
    }

    /**
     * 失眠女仆：返回她的健康档案，否则返回 null。
     */
    public static MaidRecord insomniaRecord(ServerLevel level, EntityMaid maid) {
        MaidRecord record = ClinicData.get(level).find(maid.getUUID());
        return record != null && record.hasCondition(Conditions.INSOMNIA.id()) ? record : null;
    }

    /**
     * 失眠女仆的入睡处理，由 {@code TlmEvents} 每 tick 在实体 tick 结束后调用。
     * <p>睡下时 50/50 抽一个互斥方案，并在 {@link #INSOMNIA_PLAN_TICKS} 内保持：</p>
     * <ul>
     *     <li>方案一「强行睁眼」：正常睡，挂上 {@link ModEffects#OPEN_EYES} 让客户端睡眠动画掀开眼皮，
     *     同时照常走生病表情；</li>
     *     <li>方案二「睡不着」：立刻叫醒，并抵消 TLM {@code startSleeping} 的“回满血”，
     *     避免反复入睡无限回血。</li>
     * </ul>
     */
    public static void handleInsomniaSleep(ServerLevel level, EntityMaid maid) {
        UUID id = maid.getUUID();
        if (!maid.isSleeping()) {
            // 醒来后“睁眼”方案作废，下一次入睡重新抽签
            InsomniaPlan plan = INSOMNIA_PLANS.get(id);
            if (plan != null && plan.eyesOpen()) {
                INSOMNIA_PLANS.remove(id);
                maid.removeEffect(ModEffects.OPEN_EYES);
            }
            return;
        }
        MaidRecord record = insomniaRecord(level, maid);
        if (record == null) {
            clearInsomniaPlan(maid);
            return;
        }
        long now = level.getGameTime();
        InsomniaPlan plan = INSOMNIA_PLANS.get(id);
        if (plan == null) {
            plan = new InsomniaPlan(level.random.nextBoolean(), now + INSOMNIA_PLAN_TICKS);
            INSOMNIA_PLANS.put(id, plan);
        } else if (now >= plan.until()) {
            // 睡着中的“睁眼”方案续期，避免半夜突然被叫醒；拒睡方案到期后重新抽签
            plan = plan.eyesOpen()
                    ? new InsomniaPlan(true, now + INSOMNIA_PLAN_TICKS)
                    : new InsomniaPlan(level.random.nextBoolean(), now + INSOMNIA_PLAN_TICKS);
            INSOMNIA_PLANS.put(id, plan);
        }
        if (plan.eyesOpen()) {
            refreshEyesOpen(maid);
            return;
        }
        // 危重（重度且未对因治疗）的失眠女仆不允许被“睡不着”方案反复叫醒：
        // 疾病伤害本来就会打断睡眠，再叠加叫醒会形成“醒→睡→再醒”的死循环。
        if (isCritical(record)) {
            INSOMNIA_PLANS.put(id, new InsomniaPlan(true, now + INSOMNIA_PLAN_TICKS));
            refreshEyesOpen(maid);
            return;
        }
        maid.removeEffect(ModEffects.OPEN_EYES);
        maid.stopSleeping();
        float before = record.lastHealth();
        if (before > 0.0F && maid.getHealth() > before) {
            maid.setHealth(before);
        }
    }

    /** 失眠方案一变回清醒/痊愈时收尾，避免“睁眼”标记残留。 */
    public static void clearInsomniaPlan(EntityMaid maid) {
        INSOMNIA_PLANS.remove(maid.getUUID());
        maid.removeEffect(ModEffects.OPEN_EYES);
    }

    /** 是否有重度且未对因治疗的疾病（与危重掉血判定一致）。 */
    private static boolean isCritical(MaidRecord record) {
        for (ActiveCondition active : record.conditions()) {
            if (active.severity() >= 3 && !active.treated()) {
                return true;
            }
        }
        return false;
    }

    private static void refreshEyesOpen(EntityMaid maid) {
        MobEffectInstance effect = maid.getEffect(ModEffects.OPEN_EYES);
        if (effect == null || effect.getDuration() < 60) {
            maid.addEffect(new MobEffectInstance(ModEffects.OPEN_EYES, 20 * 8, 0, false, false, false));
        }
    }

    /**
     * 睡眠恢复：完整睡完一觉后劳累 -80（最低到 0）。
     * <p>“完整”指夜里入睡、天亮后才醒（含玩家睡觉跳过夜晚时女仆仍躺在床上），
     * 或白天连续睡了至少 {@link #MIN_FULL_SLEEP_TICKS}；夜里被叫醒不算。</p>
     */
    private static boolean updateSleepRecovery(ServerLevel level, EntityMaid maid, MaidRecord record, long now) {
        UUID id = maid.getUUID();
        if (maid.isSleeping()) {
            SLEEP_START.putIfAbsent(id, new SleepStart(now, !isDaytime(level)));
            return false;
        }
        SleepStart start = SLEEP_START.remove(id);
        if (start == null || !isDaytime(level)) {
            return false;
        }
        boolean sleptThroughNight = start.night();
        boolean longEnough = now - start.tick() >= MIN_FULL_SLEEP_TICKS;
        if (!sleptThroughNight && !longEnough) {
            return false;
        }
        record.behavior().relieveExertion(SLEEP_EXERTION_RELIEF);
        return true;
    }

    /** 按世界时间判断白天（0~13000 tick 为白天）：服务器端 skyDarken 不一定及时刷新，直接算更稳。 */
    private static boolean isDaytime(ServerLevel level) {
        return Math.floorMod(level.getDayTime(), 24000L) < 13000L;
    }

    private static BlockPos findBed(ServerLevel level, EntityMaid maid) {
        BlockPos origin = maid.blockPosition();
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-6, -2, -6), origin.offset(6, 2, 6))) {
            if (!level.isLoaded(pos)) {
                continue;
            }
            BlockState state = level.getBlockState(pos);
            if (!(state.getBlock() instanceof BedBlock)) {
                continue;
            }
            double distance = pos.distToCenterSqr(maid.position());
            if (distance < bestDistance) {
                bestDistance = distance;
                best = pos.immutable();
            }
        }
        return best;
    }

    /** 生病被强迫工作：病情加重/病程延长、劳累上升，并冒泡抱怨。 */
    private static boolean forcedWorkFeedback(ServerLevel level, EntityMaid maid, MaidRecord record) {
        if (!isForcedWork(maid, record)
                || level.getGameTime() % ClinicSettings.FORCED_WORK_FEEDBACK_INTERVAL != 0) {
            return false;
        }
        ActiveCondition target = null;
        Condition definition = null;
        for (ActiveCondition active : record.conditions()) {
            Condition candidate = Conditions.byId(active.conditionId());
            if (candidate == null) {
                continue;
            }
            if (target == null || active.severity() > target.severity()) {
                target = active;
                definition = candidate;
            }
        }
        if (target == null || definition == null) {
            return false;
        }
        // 每 20 秒只有一半概率加重；其余时候只是累加疲劳并抱怨，避免惩罚过重。
        if (level.random.nextFloat() < 0.5F) {
            if (target.severity() < definition.maxSeverity()) {
                target.setSeverity(target.severity() + 1);
            } else {
                target.setDurationTicks(target.elapsedTicks() + 20 * 15);
            }
        }
        record.behavior().onForcedWork();
        ClinicData.get(level).setDirty();
        String[] forcedLines = {
                "message.maidclinic.sick.line.forced1",
                "message.maidclinic.sick.line.forced2",
                "message.maidclinic.sick.line.forced3"
        };
        showBubble(maid, forcedLines[level.random.nextInt(forcedLines.length)]);
        return true;
    }

    private static boolean isForcedWork(EntityMaid maid, MaidRecord record) {
        return record.forcedWork()
                || (!record.conditions().isEmpty() && !"idle".equals(taskPath(maid)));
    }

    private static void showBubble(EntityMaid maid, String key) {
        maid.getChatBubbleManager().addChatBubble(TextChatBubbleData.type1(Msg.tr(key)));
    }

    /** 生病睡着时偶尔发出难受/受伤声音。 */
    private static void sickSleepSound(ServerLevel level, EntityMaid maid, MaidRecord record) {
        if (!maid.isSleeping() || record.conditions().isEmpty()
                || level.getGameTime() % ClinicSettings.SICK_SLEEP_SOUND_INTERVAL != 0) {
            return;
        }
        if (level.random.nextFloat() < 0.6F) {
            // EntityMaid.playSound 会发 PlayMaidSoundPackage，客户端按语音包播放；
            // 不走 level.playSound，否则只会播放原版静态音效。
            SoundEvent voice = maidHurtVoice(level, maid);
            if (voice != null) {
                maid.playSound(voice, 0.45F, 0.95F);
            }
        }
    }

    /** 取 TLM 语音包里的受伤/环境音，而不是 MC 原版受伤音。 */
    private static SoundEvent maidHurtVoice(ServerLevel level, EntityMaid maid) {
        try {
            java.lang.reflect.Method method = EntityMaid.class.getDeclaredMethod(
                    "getHurtSound", DamageSource.class);
            method.setAccessible(true);
            SoundEvent sound = (SoundEvent) method.invoke(maid, level.damageSources().generic());
            if (sound != null) {
                return sound;
            }
        } catch (ReflectiveOperationException | LinkageError ignored) {
            // 反射失败时尝试环境音
        }
        try {
            java.lang.reflect.Method method = EntityMaid.class.getDeclaredMethod("getAmbientSound");
            method.setAccessible(true);
            return (SoundEvent) method.invoke(maid);
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return null;
        }
    }

    /** 生病的“外表”：持续冒病气粒子，并周期性冒病中气泡（不改动作）。 */
    private static void sickAura(ServerLevel level, EntityMaid maid, MaidRecord record) {
        if (record.conditions().isEmpty() || level.getGameTime() % 400 != 0) {
            return;
        }
        ActiveCondition active = record.conditions().get(level.random.nextInt(record.conditions().size()));
        Condition definition = Conditions.byId(active.conditionId());
        if (definition != null) {
            showBubble(maid, sickLineKey(definition, level.random));
        }
    }

    /** 生病的可见表现：喷嚏/发热烟雾粒子（不依赖其它模组）。 */
    private static void spawnSickParticles(ServerLevel level, EntityMaid maid, MaidRecord record) {
        if (record.conditions().isEmpty()) {
            return;
        }
        Condition condition = Conditions.byId(record.conditions().get(0).conditionId());
        if (condition == null) {
            return;
        }
        float x = (float) maid.getX();
        float y = (float) maid.getY();
        float z = (float) maid.getZ();
        if (condition == Conditions.VIRAL_URI || condition == Conditions.BACTERIAL_PHARYNGITIS
                || condition == Conditions.ALLERGIC_RHINITIS) {
            level.sendParticles(ParticleTypes.SNEEZE, x, y + 1.45D, z, 8, 0.35D, 0.25D, 0.35D, 0.03D);
            level.sendParticles(new DustParticleOptions(new Vector3f(0.55F, 0.75F, 1.0F), 1.2F),
                    x, y + 1.3D, z, 6, 0.3D, 0.3D, 0.3D, 0.0D);
        } else if (condition.feverPerSeverity() > 0.4F) {
            level.sendParticles(ParticleTypes.LARGE_SMOKE, x, y + 1.5D, z, 7, 0.3D, 0.25D, 0.3D, 0.02D);
            level.sendParticles(new DustParticleOptions(new Vector3f(1.0F, 0.35F, 0.25F), 1.2F),
                    x, y + 1.3D, z, 5, 0.3D, 0.3D, 0.3D, 0.0D);
        } else {
            level.sendParticles(ParticleTypes.DAMAGE_INDICATOR, x, y + 1.3D, z, 4, 0.3D, 0.3D, 0.3D, 0.02D);
            level.sendParticles(new DustParticleOptions(new Vector3f(0.6F, 0.85F, 0.5F), 1.0F),
                    x, y + 1.2D, z, 4, 0.25D, 0.25D, 0.25D, 0.0D);
        }
    }

    /** 定期清理长期未出现的女仆档案。 */
    public static void prune(ServerLevel level) {
        ClinicData.get(level).prune(level.getGameTime(), 24000L * 7L);
    }

    /**
     * 女仆死亡：把整份健康档案暂存到当前维度存档，等待复活出的新女仆继承。
     * <p>是否保留由配置 {@code keepSequelaOnDeath} 或玩家个人
     * {@code /maidclinic sequela on|off} 决定（该开关现在控制整份档案）。</p>
     */
    public static void onMaidDeath(ServerLevel level, EntityMaid maid) {
        if (isSpirit(maid)) {
            return;
        }
        SLEEP_START.remove(maid.getUUID());
        INSOMNIA_PLANS.remove(maid.getUUID());
        RECOVERY_UNTIL.remove(maid.getUUID());
        SLEEP_HEALTH_HOLD.remove(maid.getUUID());
        releaseHospitalBed(maid);
        UUID ownerId = maid.getOwnerUUID();
        if (ownerId == null) {
            return;
        }
        ClinicData data = ClinicData.get(level);
        MaidRecord record = data.find(maid.getUUID());
        if (record == null || !keepRecord(level, ownerId)) {
            return;
        }
        data.addRecordInheritance(ownerId, maid.getName().getString(), maid.getModelId(),
                record, level.getGameTime());
    }

    private static boolean keepRecord(ServerLevel level, UUID ownerId) {
        Boolean personal = ClinicData.get(level).sequelaOverride(ownerId);
        return personal != null ? personal : ClinicConfig.KEEP_SEQUELA_ON_DEATH.get();
    }

    /** 新女仆第一次出现时，继承死亡女仆的健康档案（不含当前疾病与临时状态）。 */
    private static boolean maybeInheritRecord(ServerLevel level, EntityMaid maid, MaidRecord record) {
        UUID ownerId = maid.getOwnerUUID();
        if (ownerId == null || !keepRecord(level, ownerId)) {
            return false;
        }
        ClinicData.Inheritance inherited = ClinicData.get(level)
                .takeRecordInheritance(ownerId, maid.getName().getString(), maid.getModelId(),
                        level.getGameTime(), 24000L * 30L);
        if (inherited == null) {
            return false;
        }
        record.setTrust(inherited.trust());
        record.setCured(inherited.cured());
        record.setExams(inherited.exams());
        record.setWrongDoses(inherited.wrongDoses());
        record.setContracted(inherited.contracted());
        record.setDoctorXp(inherited.doctorXp());
        record.setHealthyDays(inherited.healthyDays());
        record.setCarePoints(inherited.carePoints());
        record.setCareTier(inherited.careTier());
        for (String id : inherited.sequelae()) {
            Sequela sequela = Sequela.byId(id);
            if (sequela != null) {
                record.addSequela(sequela);
            }
        }
        if (inherited.profile() != null) {
            record.setProfile(MaidProfile.load(inherited.profile()));
        }
        ClinicData.get(level).setDirty();
        notifyOwner(maid, Msg.yellow("message.maidclinic.record.inherited", maid.getName().getString()));
        return true;
    }

    /**
     * 危重状态：只有“重度且未对因治疗”的疾病才会持续消耗生命。
     * <p>
     * 伤害按最严重疾病的毒力计算；血量归零时调用 {@code die()}，
     * 由 TLM 正常生成墓碑。默认<b>非致死</b>：最多掉到 {@code criticalMinHealth}。
     * </p>
     */
    private static void applyCriticalDamage(ServerLevel level, EntityMaid maid, MaidRecord record) {
        if (!ClinicConfig.CRITICAL_DAMAGE.get()) {
            return;
        }
        Condition deadlyCondition = deadliestUntreated(record);
        if (deadlyCondition == null) {
            return;
        }
        boolean lethal = isLethal(level, maid);
        int interval = Math.max(20, ClinicConfig.CRITICAL_INTERVAL_SECONDS.get() * 20);
        if (lethal) {
            // 致死模式缩短到 5 秒一次，避免扣血间隔过长导致看起来“死不了”。
            interval = Math.min(interval, 20 * 5);
        }
        if (level.getGameTime() % interval != 0) {
            return;
        }

        float health = maid.getHealth();
        float minHealth = ClinicConfig.CRITICAL_MIN_HEALTH.get().floatValue();
        float virulence = deadlyCondition.virulence();
        float baseDamage = Math.max(2.0F, (maid.getMaxHealth() * 0.05F) * virulence);
        float damage = lethal
                ? baseDamage
                : Math.min(baseDamage, health - minHealth);
        if (damage <= 0.0F) {
            return;
        }

        float newHealth = Math.max(0.0F, health - damage);
        float decrease = health - newHealth;
        if (decrease <= 0.0F) {
            return;
        }
        DamageSource source = illnessSource(level, deadlyCondition);
        // 先写战斗记录：《万法皆通》只在“当前血量 - 新血量”与最后一条战斗记录匹配时
        // 才允许女仆的 setHealth 下降，否则会直接 cancel。
        maid.getCombatTracker().recordDamage(source, decrease);
        maid.setHealth(newHealth);
        float after = maid.getHealth();
        // 同步血量记录，避免下一 tick 把这次疾病扣血当成外伤去判定伤口感染。
        record.setLastHealth(after);
        ClinicData.get(level).setDirty();

        if (after <= 0.0F) {
            dieOfIllness(level, maid, deadlyCondition);
            return;
        }
        if (maid.isSleeping()) {
            // 睡眠中手动压住血量，覆盖 TLM 的睡眠回血，并避免被推挤弄醒。
            SLEEP_HEALTH_HOLD.put(maid.getUUID(), after);
            maid.setDeltaMovement(Vec3.ZERO);
            maid.getNavigation().stop();
        }
        notifyOwner(maid, Msg.red("message.maidclinic.critical", maid.getName().getString()));
    }

    /**
     * 危重期间压制生命恢复与抗火（女仆可爱动作的无限 buff、信标恢复等）。
     * <p>医生光环只对没有“身体不适”的女仆生效，因此不会被这里影响。</p>
     */
    private static void suppressCriticalBuffs(EntityMaid maid, MaidRecord record) {
        if (!isCritical(record)) {
            return;
        }
        maid.removeEffect(MobEffects.REGENERATION);
        maid.removeEffect(MobEffects.FIRE_RESISTANCE);
    }

    /** 危重期间拒绝重新挂上生命恢复/抗火；医生光环不会作用在 UNWELL 女仆身上，因此保留。 */
    public static void onEffectApplicable(MobEffectEvent.Applicable event) {
        if (!(event.getEntity() instanceof EntityMaid maid) || maid.level().isClientSide()) {
            return;
        }
        var effect = event.getEffectInstance().getEffect();
        if (effect != MobEffects.REGENERATION && effect != MobEffects.FIRE_RESISTANCE) {
            return;
        }
        MaidRecord record = ClinicData.get((ServerLevel) maid.level()).find(maid.getUUID());
        if (record != null && isCritical(record)) {
            event.setResult(MobEffectEvent.Applicable.Result.DO_NOT_APPLY);
        }
    }

    /** 取“重度且未治疗”中最严重（mortality 最高）的疾病；没有则返回 null。 */
    private static Condition deadliestUntreated(MaidRecord record) {
        Condition deadliest = null;
        float mortality = 0.0F;
        for (ActiveCondition active : record.conditions()) {
            if (active.severity() < 3 || active.treated()) {
                continue;
            }
            Condition definition = Conditions.byId(active.conditionId());
            if (definition == null) {
                continue;
            }
            if (deadliest == null || definition.mortality() > mortality) {
                deadliest = definition;
                mortality = definition.mortality();
            }
        }
        return deadliest;
    }

    /**
     * 走原版 die() 死亡，由 TLM 正常生成墓碑。
     * <p>死亡前必须清掉睡眠血量保持，否则 die() 被其它模组拦截/复活后，
     * maintainSickSleep 会把旧血量写回去，让女仆“原地复活”。</p>
     * <p>原版只为玩家广播 {@link net.minecraft.world.entity.player.Player#die(DamageSource)}
     * 里的死亡提示，女仆是普通生物，{@code die()} 不会把
     * {@link IllnessDamageSource#getLocalizedDeathMessage} 发给任何人，
     * 所以由 {@link #onLivingDeath(LivingDeathEvent)} 在死亡事件里补发一次。</p>
     * <p>这里不能用 {@code isAlive()} 判重：按毒力扣血的路径会先把血量扣到 0，
     * 再调用本方法；而 {@code isAlive()} 以血量大于 0 为准，会把真正的死亡拦掉，
     * 导致女仆停在 0 点血却永远不触发 {@code die()}（也就没有死亡提示/墓碑）。</p>
     */
    private static void dieOfIllness(ServerLevel level, EntityMaid maid, Condition condition) {
        if (maid.isRemoved()) {
            return;
        }
        SLEEP_HEALTH_HOLD.remove(maid.getUUID());
        releaseHospitalBed(maid);
        maid.die(illnessSource(level, condition));
        ClinicData.get(level).setDirty();
    }

    /**
     * 女仆死亡前置处理：在《心婚誓约》生成灵体前拦下“不应生成灵体”的后代死亡。
     * <p>玩家击杀、诊所疾病致死，以及玩家造成的爆炸/岩浆/摔落等（由
     * {@link SpiritAttribution} 按配置档位归因）都会拦下；真正无法归因的自然死亡
     * 仍然保留灵体。死亡提示不在这里发，见 {@link #onLivingDeathMessage(LivingDeathEvent)}。</p>
     */
    public static void onLivingDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof EntityMaid maid)) {
            return;
        }
        if (maid.level().isClientSide() || event.isCanceled() || isSpirit(maid)) {
            return;
        }
        if (!(maid.level() instanceof ServerLevel)) {
            return;
        }
        DamageSource source = event.getSource();
        if (source != null && shouldPreventSpirit(maid, source)) {
            preventChildSpirit(maid);
        }
    }

    /**
     * 女仆死亡提示：以 LOWEST 优先级在所有可能取消死亡的处理之后发一次。
     * <p>同一死亡事件可能被重复派发，{@link #announceDeath} 里再做一次 UUID 去重，
     * 保证同一次死亡只显示一条。灵体不是活着的女仆，不显示死亡提示。</p>
     */
    public static void onLivingDeathMessage(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof EntityMaid maid)) {
            return;
        }
        if (maid.level().isClientSide() || event.isCanceled() || isSpirit(maid)) {
            return;
        }
        if (!(maid.level() instanceof ServerLevel level)) {
            return;
        }
        DamageSource source = event.getSource();
        if (source == null) {
            return;
        }
        announceDeath(level, maid, source);
    }

    /**
     * 是否需要阻止这次后代死亡生成灵体。
     * <p>疾病致死一律拦；其余交给 {@link SpiritAttribution}：按
     * {@code spiritAttribution} 档位把玩家直接击杀、爆炸、玩家放置的岩浆/火、
     * 推落与主人过失等逐层归因。归因不到的自然死亡保留灵体。</p>
     */
    private static boolean shouldPreventSpirit(EntityMaid maid, DamageSource source) {
        if ("maidclinic.illness".equals(source.getMsgId())) {
            return true;
        }
        return SpiritAttribution.attribute(maid, source).blocked();
    }

    /**
     * 玩家击杀/疾病致死等不应让后代留下灵体。
     * <p>只对《心婚誓约》判定为“仍是孩子”的女仆生效；写入其死亡桥接
     * 读取的 {@code maidmarriage_spirit_spawned} 标记。未安装该模组时静默跳过。</p>
     */
    private static void preventChildSpirit(EntityMaid maid) {
        if (!isMarriageChild(maid)) {
            return;
        }
        try {
            Method method = maid.getClass().getMethod("getPersistentData");
            if (method.invoke(maid) instanceof CompoundTag tag) {
                tag.putBoolean("maidmarriage_spirit_spawned", true);
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            // 《心婚誓约》未安装或映射不可用时无需处理。
        }
    }

    /** 与《心婚誓约》的灵体生成条件保持一致：shouldStayChild 的孩子才需要拦截。 */
    private static boolean isMarriageChild(EntityMaid maid) {
        try {
            Class<?> type = Class.forName("com.example.maidmarriage.entity.MaidChildEntity");
            Method method = type.getMethod("shouldStayChild", EntityMaid.class);
            return Boolean.TRUE.equals(method.invoke(null, maid));
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            return false;
        }
    }

    /**
     * 补发女仆死亡提示，只发给该女仆的主人。
     * <p>不跟随原版把玩家死亡消息广播给所有人：女仆死亡属于主人的私人事件。
     * 主人的在线状态由 TLM 的 {@link EntityMaid#getOwner()} 解析（跨维度也能找到），
     * 主人不在线时自然没有接收者。仍遵循 {@code showDeathMessages} 游戏规则。</p>
     */
    private static void announceDeath(ServerLevel level, EntityMaid maid, DamageSource source) {
        if (!level.getGameRules().getBoolean(GameRules.RULE_SHOWDEATHMESSAGES)) {
            return;
        }
        long now = level.getGameTime();
        DEATH_MESSAGE_TICKS.entrySet().removeIf(entry -> now - entry.getValue() > 200L);
        Long last = DEATH_MESSAGE_TICKS.get(maid.getUUID());
        if (last != null && now - last <= 200L) {
            return;
        }
        if (maid.getOwner() instanceof ServerPlayer owner) {
            DEATH_MESSAGE_TICKS.put(maid.getUUID(), now);
            owner.sendSystemMessage(source.getLocalizedDeathMessage(maid));
        }
    }

    /** 疾病伤害来源：死亡提示会带上病名。 */
    private static DamageSource illnessSource(ServerLevel level, Condition condition) {
        return new IllnessDamageSource(level.registryAccess()
                .registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(ILLNESS_DAMAGE), condition);
    }

    /**
     * 睡梦猝死：开启疾病致死且选择 sleep 方式后，危重女仆睡着时随机 1–4.5 分钟内死亡。
     * <p>死亡时刻用绝对游戏刻记在健康档案里，只在安排/取消时写档；
     * 醒来不超过 20 秒会续上剩余计时，超过宽限或不再危重则作废。</p>
     */
    private static void maybeSleepDeath(ServerLevel level, EntityMaid maid, MaidRecord record, long now) {
        boolean armed = deathStyle(level, maid) == DeathStyle.SLEEP
                && isLethal(level, maid)
                && maid.isAlive()
                && isCritical(record);
        if (!armed) {
            resetSleepDeath(level, record);
            return;
        }
        if (!maid.isSleeping()) {
            // 短暂醒来（10–20 秒内）不重置计时；醒来超过宽限才作废。
            if (record.sleepDeathAt() != 0L || record.sleepDeathAttempted()) {
                if (record.sleepWakeAt() == 0L) {
                    record.setSleepWakeAt(now);
                    ClinicData.get(level).setDirty();
                } else if (now - record.sleepWakeAt() > ClinicSettings.SLEEP_DEATH_WAKE_GRACE_TICKS) {
                    resetSleepDeath(level, record);
                }
            }
            return;
        }
        if (record.sleepWakeAt() != 0L) {
            long awakeTicks = now - record.sleepWakeAt();
            record.setSleepWakeAt(0L);
            if (awakeTicks <= ClinicSettings.SLEEP_DEATH_WAKE_GRACE_TICKS
                    && record.sleepDeathAt() != 0L) {
                // 续上：把醒来占用的时间补回死亡时刻
                record.setSleepDeathAt(record.sleepDeathAt() + awakeTicks);
            } else {
                record.setSleepDeathAt(0L);
                record.setSleepDeathAttempted(false);
            }
            ClinicData.get(level).setDirty();
        }
        // 本段睡眠已经调用过 die()：即使死亡被其它模组拦截/复活，也不再重复，
        // 否则会每秒重新发一次死亡提示、反复生成墓碑。
        if (record.sleepDeathAttempted()) {
            return;
        }
        if (record.sleepDeathAt() == 0L) {
            int delay = ClinicSettings.SLEEP_DEATH_MIN_TICKS + level.random.nextInt(
                    ClinicSettings.SLEEP_DEATH_MAX_TICKS - ClinicSettings.SLEEP_DEATH_MIN_TICKS + 1);
            record.setSleepDeathAt(now + delay);
            ClinicData.get(level).setDirty();
            return;
        }
        if (now < record.sleepDeathAt()) {
            return;
        }
        Condition deadlyCondition = deadliestUntreated(record);
        record.setSleepDeathAt(0L);
        if (deadlyCondition == null) {
            record.setSleepDeathAttempted(false);
            ClinicData.get(level).setDirty();
            return;
        }
        record.setSleepDeathAttempted(true);
        ClinicData.get(level).setDirty();
        dieOfIllness(level, maid, deadlyCondition);
    }

    /** 醒来/不再危重/切换模式：清空本段睡眠的计时、短暂醒来标记与已尝试标记。 */
    private static void resetSleepDeath(ServerLevel level, MaidRecord record) {
        if (record.sleepDeathAt() != 0L || record.sleepDeathAttempted() || record.sleepWakeAt() != 0L) {
            record.setSleepDeathAt(0L);
            record.setSleepDeathAttempted(false);
            record.setSleepWakeAt(0L);
            ClinicData.get(level).setDirty();
        }
    }

    /** 玩家个人选择优先于配置文件。 */
    private static DeathStyle deathStyle(ServerLevel level, EntityMaid maid) {
        UUID ownerId = maid.getOwnerUUID();
        DeathStyle personal = ownerId == null ? null : ClinicData.get(level).deathStyleOverride(ownerId);
        return personal != null ? personal : DeathStyle.byId(ClinicConfig.DEATH_STYLE.get());
    }

    /** 病床休息开始时锁定入睡前血量，抵消 TLM {@code startSleeping} 的回满血。 */
    public static void holdSleepHealth(EntityMaid maid, float health) {
        float value = Math.max(0.0F, Math.min(maid.getMaxHealth(), health));
        SLEEP_HEALTH_HOLD.put(maid.getUUID(), value);
        if (Math.abs(maid.getHealth() - value) > 0.01F) {
            maid.setHealth(value);
        }
    }

    public static void releaseSleepHealth(EntityMaid maid) {
        SLEEP_HEALTH_HOLD.remove(maid.getUUID());
    }

    /**
     * 每 tick 维持生病女仆的睡眠与血量：服务端执行。
     * <p>不碰睡姿/床坐标，只做三件事：把被 TLM 睡眠回血抬高的血量按记录值压回去、
     * 清零速度、停止寻路，从而抵消回血和推挤，不会触发原版起床。</p>
     */
    public static void maintainSickSleep(EntityMaid maid) {
        if (maid.level().isClientSide()) {
            return;
        }
        Float hold = SLEEP_HEALTH_HOLD.get(maid.getUUID());
        if (hold == null) {
            return;
        }
        if (!maid.isAlive() || maid.getEffect(ModEffects.UNWELL) == null
                || !maid.getSleepingPos().isPresent()) {
            SLEEP_HEALTH_HOLD.remove(maid.getUUID());
            return;
        }
        if (Math.abs(maid.getHealth() - hold) > 0.01F) {
            maid.setHealth(hold);
        }
        maid.setDeltaMovement(Vec3.ZERO);
        maid.getNavigation().stop();
    }

    /** 记住/清理女仆的床。 */
    private static void rememberSleepSpot(EntityMaid maid) {
        if (maid.isSleeping()) {
            maid.getSleepingPos().ifPresent(pos ->
                    SLEEP_SPOTS.put(maid.getUUID(), pos.immutable()));
        } else {
            SLEEP_SPOTS.remove(maid.getUUID());
        }
    }

    /** 记录某只后代正在诊所病床上强制卧床。 */
    public static void holdInHospitalBed(EntityMaid maid, BlockPos bed) {
        HOSPITAL_BED_REST.put(maid.getUUID(), bed.immutable());
    }

    /** 解除卧床锁定：痊愈、被安排工作、死亡或玩家主动抱走时调用。 */
    public static void releaseHospitalBed(EntityMaid maid) {
        HOSPITAL_BED_REST.remove(maid.getUUID());
    }

    /** 这只后代是否正在病床上强制卧床；是的话拒绝被母亲/看护女仆骑乘带走。 */
    public static boolean isHeldInHospitalBed(EntityMaid maid) {
        return HOSPITAL_BED_REST.containsKey(maid.getUUID());
    }

    /** 当前卧床锁定的床位；没有锁定时返回 null。 */
    public static BlockPos heldHospitalBed(EntityMaid maid) {
        return HOSPITAL_BED_REST.get(maid.getUUID());
    }

    /** 每 tick 刷新睡觉位置，保证受伤起床后马上能找回床（不用等诊所的 20 tick 轮询）。 */
    public static void updateSleepSpot(EntityMaid maid) {
        rememberSleepSpot(maid);
    }

    public static boolean isIllnessDamage(DamageSource source) {
        // 同时按来源类型和伤害类型判断：旧存档/异常路径可能构造出普通 DamageSource
        return source instanceof IllnessDamageSource || source.is(ILLNESS_DAMAGE);
    }

    /**
     * 致死模式下，重度未治疗疾病不会自然结束；否则它可能在扣光生命前先自愈。
     */
    private static boolean isLethal(ServerLevel level, EntityMaid maid) {
        UUID ownerId = maid.getOwnerUUID();
        Boolean personal = ownerId == null ? null : ClinicData.get(level).hardcoreOverride(ownerId);
        return personal != null ? personal : ClinicConfig.ILLNESS_CAN_KILL.get();
    }

    private static boolean progressConditions(ServerLevel level, EntityMaid maid, MaidRecord record, long now) {
        boolean changed = false;
        boolean lethal = isLethal(level, maid);
        List<ActiveCondition> finished = new ArrayList<>();

        // 用快照遍历：finishCondition 可能会追加“并发症”，直接改原列表会触发并发修改异常
        for (ActiveCondition active : new ArrayList<>(record.conditions())) {
            Condition definition = Conditions.byId(active.conditionId());
            if (definition == null) {
                record.conditions().remove(active);
                changed = true;
                continue;
            }
            active.progress(ClinicSettings.TICK_INTERVAL);

            if (!active.treated() && active.severity() < definition.maxSeverity()) {
                float ratio = active.progressRatio();
                if (ratio >= 0.35F && active.severity() < 2) {
                    active.setSeverity(2);
                    changed = true;
                } else if (ratio >= 0.70F && active.severity() < 3) {
                    active.setSeverity(3);
                    changed = true;
                }
            }

            if (active.finished()) {
                if (lethal && ClinicConfig.CRITICAL_DAMAGE.get()
                        && active.severity() >= 3 && !active.treated()) {
                    // 致死模式下不让重度未治疗疾病先自然结束，否则会停在致死线。
                    active.setDurationTicks(active.elapsedTicks() + 20 * 30);
                    changed = true;
                } else if (!active.treated() && !selfLimitsNow(definition, level.random)) {
                    // 非自限或本次概率未通过：延长病程，继续危重掉血/等待治疗。
                    active.setDurationTicks(active.elapsedTicks()
                            + ClinicSettings.NON_SELF_LIMITING_EXTENSION_TICKS);
                    changed = true;
                } else {
                    finished.add(active);
                }
            }
        }

        for (ActiveCondition active : finished) {
            Condition definition = Conditions.byId(active.conditionId());
            record.conditions().remove(active);
            if (definition != null) {
                finishCondition(level, maid, record, active, definition);
            }
            changed = true;
        }
        return changed;
    }

    /** 本次病程结束时是否自行缓解：0 永不自限，1 必定自限，中间按概率掷骰。 */
    private static boolean selfLimitsNow(Condition definition, RandomSource random) {
        float chance = definition.selfLimitingChance();
        return chance >= 1.0F || (chance > 0.0F && random.nextFloat() < chance);
    }

    /** 一次病程结束时的“护理等级”，决定信任与治愈计数。 */
    private enum CareLevel {
        /** 对因治疗（如抗生素、补液盐、铁剂）。 */
        TREATED,
        /** 只做了对症护理（如退热、冷敷），病程自行结束。 */
        ASSISTED,
        /** 完全没有干预，靠自限性恢复。 */
        NATURAL
    }

    private static void finishCondition(ServerLevel level, EntityMaid maid, MaidRecord record,
                                        ActiveCondition active, Condition definition) {
        String maidName = maid.getName().getString();
        Component conditionName = Msg.tr(definition.nameKey());

        if (active.treated()) {
            record.addCarePoints(2 + active.severity() * 2);
            cure(level, maid, record, definition, CareLevel.TREATED);
            return;
        }

        // 重度且没有对因治疗：留下与疾病对应的后遗症
        if (active.severity() >= 3 && workEfficiencyEnabled(level, maid)) {
            Sequela sequela = Sequela.forCondition(definition.id());
            if (sequela != null && record.addSequela(sequela)) {
                notifyOwner(maid, Msg.red("message.maidclinic.sequela",
                        maid.getName().getString(), Msg.tr(sequela.translationKey()),
                        "-" + Math.round(record.efficiencyPenalty() * 100.0F) + "%"));
            }
        }

        if (definition.selfLimiting()) {
            if (active.supported()) {
                record.addCarePoints(1 + active.severity());
                cure(level, maid, record, definition, CareLevel.ASSISTED);
                return;
            }
            if (definition.recoveryNeedsSupport()) {
                Condition extra = rollComplication(level, record, definition);
                if (extra != null) {
                    notifyOwner(maid, Msg.red("message.maidclinic.complication_no_care",
                            maidName, conditionName, Msg.tr(extra.nameKey())));
                    return;
                }
                cure(level, maid, record, definition, CareLevel.NATURAL, true);
                return;
            }
            cure(level, maid, record, definition, CareLevel.NATURAL);
            return;
        }

        Condition extra = rollComplication(level, record, definition);
        if (extra != null) {
            notifyOwner(maid, Msg.red("message.maidclinic.complication",
                    maidName, Msg.tr(extra.nameKey())));
            return;
        }
        notifyOwner(maid, Msg.yellow("message.maidclinic.untreated_recovery",
                maidName, Msg.tr(definition.nameKey())));
    }

    /** 按概率触发并发症；返回新加的病症，没有触发则返回 null。 */
    private static Condition rollComplication(ServerLevel level, MaidRecord record, Condition definition) {
        if (definition.complicationId() == null
                || level.random.nextFloat() >= definition.complicationChance()) {
            return null;
        }
        Condition extra = Conditions.byId(definition.complicationId());
        if (extra == null || record.hasCondition(extra.id())) {
            return null;
        }
        addCondition(record, extra, 2, Trigger.COMPLICATION, level.random);
        return extra;
    }

    private static void cure(ServerLevel level, EntityMaid maid, MaidRecord record,
                             Condition definition, CareLevel care) {
        cure(level, maid, record, definition, care, false);
    }

    private static void cure(ServerLevel level, EntityMaid maid, MaidRecord record,
                             Condition definition, CareLevel care, boolean withoutCare) {
        switch (care) {
            case TREATED -> {
                record.addCured();
                record.addTrust(ClinicSettings.TRUST_CURE);
            }
            case ASSISTED -> {
                record.addCured();
                record.addTrust(ClinicSettings.TRUST_CURE * 2 / 3);
            }
            case NATURAL -> record.addTrust(4);
        }
        maid.setFavorability(maid.getFavorability() + (care == CareLevel.NATURAL ? 1 : 2));
        // 病完全好了才进入恢复期：去掉“身体不适”标记，改为虚弱Ⅱ + 缓慢Ⅰ 两分钟
        if (record.conditions().isEmpty()) {
            SickExpressions.clear(maid);
            SLEEP_HEALTH_HOLD.remove(maid.getUUID());
            releaseHospitalBed(maid);
            applyRecovery(maid);
        }
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                maid.getX(), maid.getY() + 1.3D, maid.getZ(), 12, 0.35D, 0.4D, 0.35D, 0.0D);

        String maidName = maid.getName().getString();
        Component conditionName = Msg.tr(definition.nameKey());
        Component message = switch (care) {
            case TREATED -> Msg.green("message.maidclinic.cured", maidName, conditionName);
            case ASSISTED -> Msg.green("message.maidclinic.cured_assisted", maidName, conditionName);
            case NATURAL -> Msg.gray(withoutCare
                    ? "message.maidclinic.recovered_without_care"
                    : "message.maidclinic.recovered", maidName, conditionName);
        };
        notifyOwner(maid, message);
    }

    /**
     * 痊愈后的恢复期表现：只清掉本模组的“身体不适”标记，
     * 后遗症带来的负面效果保留；随后叠加虚弱Ⅱ与缓慢Ⅰ，持续 2 分钟。
     */
    private static void applyRecovery(EntityMaid maid) {
        maid.removeEffect(ModEffects.UNWELL);
        // 兼容《女仆可爱动作》：它的孤独/铃铛状态会给自己挂“无限时长”的虚弱/缓慢
        // （duration = -1）。原版合并规则会保留更强的无限效果，导致我们 2 分钟的
        // 恢复期效果看起来永远不结束；只清无限时长的那一个，正常的后遗症 buff 保留。
        clearInfinite(maid, MobEffects.WEAKNESS);
        clearInfinite(maid, MobEffects.MOVEMENT_SLOWDOWN);
        maid.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, RECOVERY_TICKS, 1, true, true, true));
        maid.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, RECOVERY_TICKS, 0, true, true, true));
        RECOVERY_UNTIL.put(maid.getUUID(),
                maid.level().getGameTime() + RECOVERY_TICKS);
    }

    /**
     * 恢复期内如果检测到无限时长的虚弱/缓慢（典型来源：《女仆可爱动作》的孤独/铃铛状态，
     * duration = -1），用剩余恢复时间重新挂一次，保证玩家看到的仍然是有限时长。
     */
    private static void enforceRecovery(ServerLevel level, EntityMaid maid) {
        Long until = RECOVERY_UNTIL.get(maid.getUUID());
        if (until == null) {
            return;
        }
        long now = level.getGameTime();
        if (now >= until) {
            RECOVERY_UNTIL.remove(maid.getUUID());
            return;
        }
        int remaining = (int) Math.min(Integer.MAX_VALUE, until - now);
        overrideInfinite(maid, MobEffects.WEAKNESS, 1, remaining);
        overrideInfinite(maid, MobEffects.MOVEMENT_SLOWDOWN, 0, remaining);
    }

    private static void overrideInfinite(EntityMaid maid,
                                         net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> effect,
                                         int amplifier, int duration) {
        MobEffectInstance current = maid.getEffect(effect);
        if (current != null && current.getDuration() == -1) {
            maid.removeEffect(effect);
            maid.addEffect(new MobEffectInstance(effect, duration, amplifier, true, true, true));
        }
    }

    /** 只移除无限时长的该效果，保留正常有限时长的（例如后遗症）。 */
    private static void clearInfinite(EntityMaid maid,
                                      net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> effect) {
        MobEffectInstance current = maid.getEffect(effect);
        if (current != null && current.getDuration() == -1) {
            maid.removeEffect(effect);
        }
    }

    // ------------------------------------------------------------------ 发病

    private static boolean onHealthDrop(ServerLevel level, EntityMaid maid, MaidRecord record, float health) {
        DamageSource source = maid.getLastDamageSource();
        if (source == null) {
            // 读档、魂符收回/放出时实体重新生成，最大生命的属性修饰符晚一 tick 才恢复，
            // 生命值会先被钳制回基准上限；这不是受伤，不能当成外伤。
            return false;
        }
        float drop = record.lastHealth() - health;
        if (drop <= 0.0F) {
            return false;
        }
        String damageType = source.getMsgId();
        // 外伤暴露先记账：即使这次没有感染，也会提高之后的发病风险
        record.behavior().onDamaged(drop, damageType);

        if (IGNORED_DAMAGE.contains(damageType)) {
            // 疾病危重掉血、饥饿等自身机制不当作外伤
            return false;
        }
        if (FIRE_DAMAGE.contains(damageType)) {
            return maybeBurn(level, maid, record, drop);
        }
        return maybeWound(level, maid, record, drop, damageType);
    }

    /** 火焰/岩浆造成烫伤：这是行为直接导致的，不做随机判定。 */
    private static boolean maybeBurn(ServerLevel level, EntityMaid maid, MaidRecord record, float drop) {
        if (record.conditions().size() >= ClinicSettings.MAX_ACTIVE_CONDITIONS) {
            return false;
        }
        if (record.hasCondition("minor_burn")) {
            return false;
        }
        addCondition(record, Conditions.MINOR_BURN, drop >= 4.0F ? 2 : 1, Trigger.FIRE, level.random);
        notifyOwner(maid, Msg.yellow("message.maidclinic.onset.burn", maid.getName().getString()));
        return true;
    }

    /** 战斗/摔伤/仙人掌等外伤：按受伤程度决定伤口感染概率。 */
    private static boolean maybeWound(ServerLevel level, EntityMaid maid, MaidRecord record,
                                      float drop, String damageType) {
        if (record.conditions().size() >= ClinicSettings.MAX_ACTIVE_CONDITIONS) {
            return false;
        }
        if (record.hasCondition("wound_infection") || record.hasCondition("minor_burn")) {
            return false;
        }
        float chance = 0.30F + Math.min(0.45F, drop * 0.035F);
        if (COMBAT_DAMAGE.contains(damageType)) {
            chance += 0.20F;
        }
        if (level.random.nextFloat() > chance) {
            return false;
        }
        Trigger cause = COMBAT_DAMAGE.contains(damageType) ? Trigger.COMBAT : Trigger.INJURY;
        addCondition(record, Conditions.WOUND_INFECTION, 1, cause, level.random);
        notifyOwner(maid, Msg.yellow("message.maidclinic.onset.wound", maid.getName().getString()));
        return true;
    }

    /**
     * 女仆吃完东西后的判定：腐肉、生肉、河豚等不洁食物会直接诱发急性胃肠炎。
     * <p>由 {@code TlmEvents} 监听原版“使用物品完成”事件后调用。</p>
     */
    public static void onMaidAte(EntityMaid maid, ItemStack stack) {
        if (isSpirit(maid)) {
            return;
        }
        if (!(maid.level() instanceof ServerLevel level)) {
            return;
        }
        if (!stack.has(DataComponents.FOOD)) {
            // 只处理“吃下去”的物品，拉弓、举盾等使用物品不会走到这里
            return;
        }
        ClinicData data = ClinicData.get(level);
        MaidRecord record = data.recordOf(maid.getUUID(), maid.getName().getString());
        record.behavior().onAte();
        data.setDirty();

        float risk = foodRisk(stack);
        if (risk <= 0.0F) {
            return;
        }
        if (record.conditions().size() >= ClinicSettings.MAX_ACTIVE_CONDITIONS
                || record.hasCondition("gastroenteritis")
                || record.hasCondition("dietary_gastroenteritis")) {
            return;
        }
        if (level.random.nextFloat() > risk) {
            return;
        }
        addCondition(record, Conditions.DIETARY_GASTROENTERITIS, 1, Trigger.FOOD, level.random);
        data.setDirty();
        notifyOwner(maid, Msg.yellow("message.maidclinic.onset.food",
                maid.getName().getString(), Msg.tr(Conditions.DIETARY_GASTROENTERITIS.nameKey())));
    }

    /** 不洁饮食的风险：生肉较低，腐肉/毒马铃薯/河豚很高。 */
    private static float foodRisk(ItemStack stack) {
        if (stack.isEmpty()) {
            return 0.0F;
        }
        Item item = stack.getItem();
        if (item == Items.ROTTEN_FLESH || item == Items.SPIDER_EYE
                || item == Items.POISONOUS_POTATO || item == Items.PUFFERFISH) {
            return 0.85F;
        }
        if (item == Items.CHICKEN || item == Items.BEEF || item == Items.PORKCHOP
                || item == Items.MUTTON || item == Items.RABBIT || item == Items.COD
                || item == Items.SALMON || item == Items.TROPICAL_FISH) {
            return 0.35F;
        }
        if (item == Items.SUSPICIOUS_STEW) {
            return 0.30F;
        }
        return 0.0F;
    }

    /** 行为暴露过高时给主人一个预警，让玩家有机会在发病前介入。 */
    private static void maybeWarnBehavior(ServerLevel level, EntityMaid maid, MaidRecord record, long now) {
        if (!behaviorTriggersEnabled() || !record.conditions().isEmpty()) {
            return;
        }
        MaidBehavior behavior = record.behavior();
        if (now - behavior.lastWarnTick() < ClinicSettings.BEHAVIOR_WARNING_COOLDOWN_TICKS) {
            return;
        }
        Trigger trigger = behavior.warningTrigger();
        if (trigger == null) {
            return;
        }
        ServerPlayer owner = nearbyOwner(maid, 32.0D);
        if (owner == null) {
            return;
        }
        behavior.markWarned(now);
        Msg.chat(owner, Msg.yellow(trigger.warningKey(), maid.getName().getString()));
    }

    private static boolean maybeContagion(ServerLevel level, EntityMaid maid, MaidRecord record) {
        long now = level.getGameTime();
        List<EntityMaid> neighbours = level.getEntities(EntityTypeTest.forClass(EntityMaid.class),
                maid.getBoundingBox().inflate(8.0D), other -> other != maid && !isSpirit(other));
        if (neighbours.isEmpty()) {
            return false;
        }
        ClinicData data = ClinicData.get(level);
        for (EntityMaid other : neighbours) {
            MaidRecord otherRecord = data.find(other.getUUID());
            if (otherRecord == null) {
                continue;
            }
            for (ActiveCondition active : otherRecord.conditions()) {
                Condition definition = Conditions.byId(active.conditionId());
                if (definition == null || !definition.infectious() || record.hasCondition(definition.id())) {
                    continue;
                }
                float chance = definition.contagion() * 0.004F;
                if (record.masked(now)) {
                    chance *= ClinicSettings.MASK_ONSET_MULTIPLIER;
                }
                if (record.contracted()) {
                    chance *= ClinicSettings.CONTRACT_ONSET_MULTIPLIER;
                }
                if (level.random.nextFloat() < chance) {
                    addCondition(record, definition, 1, Trigger.CONTAGION, level.random);
                    notifyOwner(maid, Msg.yellow("message.maidclinic.onset.contagion",
                            maid.getName().getString(), Msg.tr(definition.nameKey())));
                    return true;
                }
            }
        }
        return false;
    }

    /** 疾病是否主要由行为触发。 */
    private static boolean behaviorTriggersEnabled() {
        return ClinicConfig.ENABLE_BEHAVIOR_TRIGGERS.get();
    }

    private static boolean maybeOnset(ServerLevel level, EntityMaid maid, MaidRecord record, boolean chronicEnabled) {
        RandomSource random = level.random;
        long now = level.getGameTime();
        float base = ClinicConfig.BASE_ONSET_CHANCE.get().floatValue();

        if (!behaviorTriggersEnabled()) {
            // 旧版模型：纯随机 + 环境系数，保留给不想要行为诱因的整合包
            float chance = base;
            if (level.isRaining()) {
                chance *= 1.6F;
            }
            float biomeTemperature = level.getBiome(maid.blockPosition()).value().getBaseTemperature();
            if (biomeTemperature < 0.3F) {
                chance *= 1.4F;
            }
            if (maid.getHealth() < maid.getMaxHealth() * 0.5F) {
                chance *= 1.4F;
            }
            if (record.contracted()) {
                chance *= ClinicSettings.CONTRACT_ONSET_MULTIPLIER;
            }
            if (record.masked(now)) {
                chance *= ClinicSettings.MASK_ONSET_MULTIPLIER;
            }
            if (random.nextFloat() > chance) {
                return false;
            }
            Condition picked = pickNaturalCondition(level, maid, record, biomeTemperature, chronicEnabled);
            if (picked == null) {
                return false;
            }
            addCondition(record, picked, 1, Trigger.NATURAL, random);
            notifyOwner(maid, Msg.yellow("message.maidclinic.onset.generic",
                    maid.getName().getString(), Msg.tr(picked.nameKey())));
            return true;
        }

        // 行为模型：自然发病只保留很低的基础值，其余都由最近的行为暴露决定
        float hazard = base * ClinicSettings.NATURAL_ONSET_SHARE + record.behavior().hazard();
        hazard = Math.min(hazard, ClinicSettings.MAX_ONSET_CHANCE);
        if (record.contracted()) {
            hazard *= ClinicSettings.CONTRACT_ONSET_MULTIPLIER;
        }
        if (record.masked(now)) {
            hazard *= ClinicSettings.MASK_ONSET_MULTIPLIER;
        }
        if (random.nextFloat() > hazard) {
            return false;
        }

        Trigger trigger = record.behavior().pickTrigger(random,
                maid.getRemainingFireTicks() > 0 || maid.isInLava(), maid.getTarget() != null);
        Condition picked = pickConditionForTrigger(trigger, level, maid, record, chronicEnabled);
        if (picked == null) {
            return false;
        }
        addCondition(record, picked, 1, trigger, random);
        if (trigger == Trigger.NATURAL) {
            notifyOwner(maid, Msg.yellow("message.maidclinic.onset.generic",
                    maid.getName().getString(), Msg.tr(picked.nameKey())));
        } else {
            notifyOwner(maid, Msg.yellow("message.maidclinic.onset.behavior",
                    maid.getName().getString(), Msg.tr(picked.nameKey()), Msg.tr(trigger.nameKey())));
        }
        return true;
    }

    /** 由行为诱因决定“得什么病”：每种行为都有对应的合理候选疾病。 */
    private static Condition pickConditionForTrigger(Trigger trigger, ServerLevel level, EntityMaid maid,
                                                     MaidRecord record, boolean chronicEnabled) {
        if (trigger == Trigger.NATURAL) {
            float biomeTemperature = level.getBiome(maid.blockPosition()).value().getBaseTemperature();
            return pickNaturalCondition(level, maid, record, biomeTemperature, chronicEnabled);
        }
        MaidProfile profile = record.profile(level.random);
        List<Condition> pool = new ArrayList<>();
        List<Float> weights = new ArrayList<>();
        switch (trigger) {
            case CHILL -> {
                addBehaviorCandidate(pool, weights, record, Conditions.VIRAL_URI,
                        65.0F * profile.infectionRisk(), chronicEnabled);
                addBehaviorCandidate(pool, weights, record, Conditions.BACTERIAL_PHARYNGITIS,
                        8.0F * profile.infectionRisk(), chronicEnabled);
                addBehaviorCandidate(pool, weights, record, Conditions.WOUND_INFECTION, 10.0F, chronicEnabled);
            }
            case HEAT -> {
                addBehaviorCandidate(pool, weights, record, Conditions.HEAT_EXHAUSTION, 75.0F, chronicEnabled);
                addBehaviorCandidate(pool, weights, record, Conditions.MINOR_BURN, 15.0F, chronicEnabled);
                addBehaviorCandidate(pool, weights, record, Conditions.VIRAL_URI,
                        8.0F * profile.infectionRisk(), chronicEnabled);
            }
            case FIRE -> {
                addBehaviorCandidate(pool, weights, record, Conditions.MINOR_BURN, 90.0F, chronicEnabled);
                addBehaviorCandidate(pool, weights, record, Conditions.HEAT_EXHAUSTION, 10.0F, chronicEnabled);
            }
            case COMBAT -> {
                addBehaviorCandidate(pool, weights, record, Conditions.WOUND_INFECTION, 65.0F, chronicEnabled);
                addBehaviorCandidate(pool, weights, record, Conditions.IRON_DEFICIENCY_ANEMIA,
                        12.0F * profile.anemiaRisk(), chronicEnabled);
                addBehaviorCandidate(pool, weights, record, Conditions.INSOMNIA,
                        10.0F * profile.insomniaRisk(), chronicEnabled);
            }
            case INJURY -> {
                addBehaviorCandidate(pool, weights, record, Conditions.WOUND_INFECTION, 80.0F, chronicEnabled);
                addBehaviorCandidate(pool, weights, record, Conditions.IRON_DEFICIENCY_ANEMIA,
                        15.0F * profile.anemiaRisk(), chronicEnabled);
            }
            case OVERWORK -> {
                addBehaviorCandidate(pool, weights, record, Conditions.IRON_DEFICIENCY_ANEMIA,
                        30.0F * profile.anemiaRisk(), chronicEnabled);
                addBehaviorCandidate(pool, weights, record, Conditions.HYPOGLYCEMIA, 22.0F, chronicEnabled);
                addBehaviorCandidate(pool, weights, record, Conditions.INSOMNIA,
                        18.0F * profile.insomniaRisk(), chronicEnabled);
                addBehaviorCandidate(pool, weights, record, Conditions.VIRAL_URI,
                        10.0F * profile.infectionRisk(), chronicEnabled);
                addBehaviorCandidate(pool, weights, record, Conditions.HYPERTENSION,
                        8.0F * profile.hypertensionRisk(), chronicEnabled);
                addBehaviorCandidate(pool, weights, record, Conditions.CHRONIC_GASTRITIS, 8.0F, chronicEnabled);
            }
            case SLEEP -> {
                addBehaviorCandidate(pool, weights, record, Conditions.INSOMNIA,
                        48.0F * profile.insomniaRisk(), chronicEnabled);
                addBehaviorCandidate(pool, weights, record, Conditions.VIRAL_URI,
                        26.0F * profile.infectionRisk(), chronicEnabled);
                addBehaviorCandidate(pool, weights, record, Conditions.HYPERTENSION,
                        10.0F * profile.hypertensionRisk(), chronicEnabled);
                addBehaviorCandidate(pool, weights, record, Conditions.IRON_DEFICIENCY_ANEMIA,
                        8.0F * profile.anemiaRisk(), chronicEnabled);
            }
            case HUNGER -> {
                addBehaviorCandidate(pool, weights, record, Conditions.HYPOGLYCEMIA, 52.0F, chronicEnabled);
                addBehaviorCandidate(pool, weights, record, Conditions.GASTROENTERITIS,
                        14.0F * profile.infectionRisk(), chronicEnabled);
                addBehaviorCandidate(pool, weights, record, Conditions.IRON_DEFICIENCY_ANEMIA,
                        14.0F * profile.anemiaRisk(), chronicEnabled);
                addBehaviorCandidate(pool, weights, record, Conditions.CHRONIC_GASTRITIS, 8.0F, chronicEnabled);
            }
            case STRESS -> {
                addBehaviorCandidate(pool, weights, record, Conditions.INSOMNIA,
                        36.0F * profile.insomniaRisk(), chronicEnabled);
                addBehaviorCandidate(pool, weights, record, Conditions.HYPERTENSION,
                        22.0F * profile.hypertensionRisk(), chronicEnabled);
                addBehaviorCandidate(pool, weights, record, Conditions.CHRONIC_GASTRITIS, 16.0F, chronicEnabled);
                addBehaviorCandidate(pool, weights, record, Conditions.VIRAL_URI,
                        12.0F * profile.infectionRisk(), chronicEnabled);
            }
            case ALLERGEN -> {
                addBehaviorCandidate(pool, weights, record, Conditions.ALLERGIC_RHINITIS, 85.0F, chronicEnabled);
                addBehaviorCandidate(pool, weights, record, Conditions.VIRAL_URI,
                        10.0F * profile.infectionRisk(), chronicEnabled);
            }
            default -> {
                float biomeTemperature = level.getBiome(maid.blockPosition()).value().getBaseTemperature();
                return pickNaturalCondition(level, maid, record, biomeTemperature, chronicEnabled);
            }
        }
        Condition picked = pickWeighted(pool, weights, level.random);
        if (picked == null) {
            // 候选都被已有疾病/未开启的慢性病过滤掉了，退回自然发病池
            float biomeTemperature = level.getBiome(maid.blockPosition()).value().getBaseTemperature();
            return pickNaturalCondition(level, maid, record, biomeTemperature, chronicEnabled);
        }
        return picked;
    }

    private static void addBehaviorCandidate(List<Condition> pool, List<Float> weights, MaidRecord record,
                                             Condition condition, float weight, boolean chronicEnabled) {
        if (condition.category() == Condition.Category.CHRONIC && !chronicEnabled) {
            return;
        }
        addCandidate(pool, weights, record, condition, weight);
    }

    /** 自然发病的疾病池：权重受体质、环境与饥饿影响（关闭行为诱因时使用）。 */
    private static Condition pickNaturalCondition(ServerLevel level, EntityMaid maid, MaidRecord record,
                                                  float biomeTemperature, boolean chronicEnabled) {
        RandomSource random = level.random;
        MaidProfile profile = record.profile(random);
        boolean raining = level.isRaining();
        boolean hot = biomeTemperature > 0.95F;
        boolean hungry = maid.getHunger() <= 6;

        float viral = (26.0F + (raining ? 24.0F : 0.0F) + (biomeTemperature < 0.3F ? 16.0F : 0.0F))
                * profile.infectionRisk();
        float gastro = (14.0F + (maid.getHunger() < 12 ? 8.0F : 0.0F)) * profile.infectionRisk();
        float allergy = 12.0F;
        float insomniaRisk = 9.0F * profile.insomniaRisk();
        float anemia = 7.0F * profile.anemiaRisk();
        float bacterial = 3.0F * profile.infectionRisk();
        float heat = hot ? 14.0F : 4.0F;
        float burn = maid.getRemainingFireTicks() > 0 ? 40.0F : 0.0F;
        float hypoglycemia = hungry ? 30.0F : 2.0F;

        List<Condition> pool = new ArrayList<>();
        List<Float> weights = new ArrayList<>();
        addCandidate(pool, weights, record, Conditions.VIRAL_URI, viral);
        addCandidate(pool, weights, record, Conditions.GASTROENTERITIS, gastro);
        addCandidate(pool, weights, record, Conditions.ALLERGIC_RHINITIS, allergy);
        addCandidate(pool, weights, record, Conditions.INSOMNIA, insomniaRisk);
        addCandidate(pool, weights, record, Conditions.IRON_DEFICIENCY_ANEMIA, anemia);
        addCandidate(pool, weights, record, Conditions.BACTERIAL_PHARYNGITIS, bacterial);
        addCandidate(pool, weights, record, Conditions.HEAT_EXHAUSTION, heat);
        addCandidate(pool, weights, record, Conditions.MINOR_BURN, burn);
        addCandidate(pool, weights, record, Conditions.HYPOGLYCEMIA, hypoglycemia);

        if (chronicEnabled) {
            // 慢性病权重较低，出现后需要较长时间、多次给药管理
            addCandidate(pool, weights, record, Conditions.HYPERTENSION, 7.0F * profile.hypertensionRisk());
            addCandidate(pool, weights, record, Conditions.TYPE_2_DIABETES, 5.0F * profile.diabetesRisk());
            addCandidate(pool, weights, record, Conditions.CHRONIC_GASTRITIS, 6.0F);
            addCandidate(pool, weights, record, Conditions.OSTEOPOROSIS, 4.0F * profile.osteoporosisRisk());
        }
        return pickWeighted(pool, weights, random);
    }

    private static Condition pickWeighted(List<Condition> pool, List<Float> weights, RandomSource random) {
        if (pool.isEmpty()) {
            return null;
        }
        float total = 0.0F;
        for (float weight : weights) {
            total += weight;
        }
        if (total <= 0.0F) {
            return null;
        }
        float roll = random.nextFloat() * total;
        for (int i = 0; i < pool.size(); i++) {
            roll -= weights.get(i);
            if (roll <= 0.0F) {
                return pool.get(i);
            }
        }
        return pool.get(pool.size() - 1);
    }

    /** 慢性病是否对这只女仆启用（个人设置优先于配置文件）。 */
    private static boolean chronicEnabled(ServerLevel level, EntityMaid maid) {
        UUID ownerId = maid.getOwnerUUID();
        Boolean personal = ownerId == null ? null : ClinicData.get(level).chronicOverride(ownerId);
        return personal != null ? personal : ClinicConfig.ENABLE_CHRONIC.get();
    }

    /** 疾病影响工作效率是否对这只女仆启用。 */
    private static boolean workEfficiencyEnabled(ServerLevel level, EntityMaid maid) {
        UUID ownerId = maid.getOwnerUUID();
        Boolean personal = ownerId == null ? null : ClinicData.get(level).efficiencyOverride(ownerId);
        return personal != null ? personal : ClinicConfig.ENABLE_WORK_EFFICIENCY.get();
    }

    /** 当前疾病造成的临时工作效率惩罚（取最严重的一条，不叠加）。 */
    private static float temporaryEfficiencyPenalty(MaidRecord record) {
        float penalty = 0.0F;
        for (ActiveCondition active : record.conditions()) {
            float value = switch (active.severity()) {
                case 3 -> 0.35F;
                case 2 -> 0.20F;
                default -> 0.10F;
            };
            penalty = Math.max(penalty, value);
        }
        return penalty;
    }

    /**
     * 把“疾病 + 后遗症”转换成移动速度/攻击伤害的乘算修正。
     * <p>默认开启，可用 {@code /maidclinic efficiency off} 或配置关闭。</p>
     */
    private static void applyWorkEfficiency(ServerLevel level, EntityMaid maid, MaidRecord record) {
        boolean enabled = workEfficiencyEnabled(level, maid);
        float penalty = enabled
                ? Math.min(0.50F, temporaryEfficiencyPenalty(record) + record.efficiencyPenalty())
                : 0.0F;
        float bonus = healthyBonus(level, maid, record);
        float modifier = bonus - penalty;
        applyEfficiencyModifier(maid, Attributes.MOVEMENT_SPEED, modifier);
        applyEfficiencyModifier(maid, Attributes.ATTACK_DAMAGE, modifier);
    }

    /** 正反馈：无疾病、无后遗症时获得少量工作效率加成。 */
    private static float healthyBonus(ServerLevel level, EntityMaid maid, MaidRecord record) {
        if (!workEfficiencyEnabled(level, maid)
                || !ClinicConfig.ENABLE_HEALTHY_BONUS.get()
                || !record.conditions().isEmpty()
                || !record.sequelae().isEmpty()) {
            return 0.0F;
        }
        float streak = record.healthyDays() >= 14
                ? ClinicSettings.HEALTHY_STREAK_14
                : record.healthyDays() >= 7
                ? ClinicSettings.HEALTHY_STREAK_7
                : record.healthyDays() >= 3 ? ClinicSettings.HEALTHY_STREAK_3 : 0.0F;
        return Math.min(ClinicSettings.HEALTHY_BONUS_MAX,
                ClinicSettings.HEALTHY_BONUS_BASE
                        + ClinicSettings.HEALTHY_BONUS_PER_XP * record.doctorXp()
                        + streak);
    }

    private static void applyEfficiencyModifier(EntityMaid maid,
                                                net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute,
                                                float modifier) {
        applyModifier(maid, attribute, WORK_EFFICIENCY_ID, modifier,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
    }

    /** 体质：改变最大生命值，并让偏弱体质在做重体力/战斗工作时效率下降。 */
    private static void applyConstitution(ServerLevel level, EntityMaid maid, MaidRecord record) {
        MaidProfile profile = record.profile(level.random);
        applyModifier(maid, Attributes.MAX_HEALTH, CONSTITUTION_HEALTH_ID,
                profile.maxHealthBonus(), AttributeModifier.Operation.ADD_VALUE);
        if (maid.getHealth() > maid.getMaxHealth()) {
            maid.setHealth(maid.getMaxHealth());
        }
        float penalty = profile.isSuitableFor(taskPath(maid)) ? 0.0F : ClinicSettings.CONSTITUTION_PENALTY;
        applyModifier(maid, Attributes.MOVEMENT_SPEED, CONSTITUTION_WORK_ID, -penalty,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        applyModifier(maid, Attributes.ATTACK_DAMAGE, CONSTITUTION_WORK_ID, -penalty,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
    }

    /** 护理积分的一次性永久奖励：25 分 +5 最大生命/+1 攻击；50 分累计 +10/+3。 */
    private static void applyCareAttributes(EntityMaid maid, MaidRecord record) {
        float health = record.careTier() >= 3 ? 10.0F : record.careTier() >= 2 ? 5.0F : 0.0F;
        float attack = record.careTier() >= 3 ? 3.0F : record.careTier() >= 2 ? 1.0F : 0.0F;
        applyModifier(maid, Attributes.MAX_HEALTH, CARE_HEALTH_ID,
                health, AttributeModifier.Operation.ADD_VALUE);
        applyModifier(maid, Attributes.ATTACK_DAMAGE, CARE_ATTACK_ID,
                attack, AttributeModifier.Operation.ADD_VALUE);
        if (maid.getHealth() > maid.getMaxHealth()) {
            maid.setHealth(maid.getMaxHealth());
        }
    }

    /** 领取护理积分奖励；只有主人在线时发放，避免奖励丢失。 */
    private static void claimCareRewards(ServerLevel level, EntityMaid maid, MaidRecord record) {
        if (!(maid.getOwner() instanceof ServerPlayer owner) || record.careTier() >= 3) {
            return;
        }
        boolean changed = false;
        if (record.careTier() < 1 && record.carePoints() >= 10) {
            giveToOwner(owner, new ItemStack(ModItems.MEDICINES.get(Treatment.BANDAGE).get(), 2));
            giveToOwner(owner, new ItemStack(ModItems.MEDICINES.get(Treatment.ORS).get(), 1));
            giveToOwner(owner, new ItemStack(ModItems.MEDICINES.get(Treatment.GLUCOSE).get(), 1));
            record.setCareTier(1);
            Msg.chat(owner, Msg.green("message.maidclinic.care.supplies"));
            changed = true;
        }
        if (record.careTier() < 2 && record.carePoints() >= 25) {
            record.setCareTier(2);
            Msg.chat(owner, Msg.green("message.maidclinic.care.health", maid.getName().getString()));
            changed = true;
        }
        if (record.careTier() < 3 && record.carePoints() >= 50) {
            record.setCareTier(3);
            Msg.chat(owner, Msg.green("message.maidclinic.care.attack", maid.getName().getString()));
            changed = true;
        }
        if (changed) {
            ClinicData.get(level).setDirty();
        }
    }

    private static void giveToOwner(ServerPlayer owner, ItemStack stack) {
        if (!owner.getInventory().add(stack)) {
            owner.drop(stack, false);
        }
    }

    private static void applyModifier(EntityMaid maid,
                                      net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute,
                                      ResourceLocation id, float amount,
                                      AttributeModifier.Operation operation) {
        AttributeInstance instance = maid.getAttribute(attribute);
        if (instance == null) {
            return;
        }
        if (Math.abs(amount) <= 0.0001F) {
            instance.removeModifier(id);
            return;
        }
        instance.addOrReplacePermanentModifier(new AttributeModifier(id, amount, operation));
    }

    /** 后遗症不只是数值惩罚：定期造成虚弱/迟缓/意识模糊，影响实际动作。 */
    private static void applySequelaEffects(ServerLevel level, EntityMaid maid, MaidRecord record) {
        if (!workEfficiencyEnabled(level, maid) || record.sequelae().isEmpty()
                || level.getGameTime() % (20 * 30) != 0) {
            return;
        }
        for (String id : record.sequelae()) {
            Sequela sequela = Sequela.byId(id);
            if (sequela == null || sequela.effect() == null) {
                continue;
            }
            maid.addEffect(new MobEffectInstance(sequela.effect(), 20 * 8,
                    sequela.amplifier(), true, true, true));
        }
    }

    private static void addCandidate(List<Condition> pool, List<Float> weights, MaidRecord record,
                                     Condition condition, float weight) {
        if (weight <= 0.0F || record.hasCondition(condition.id())) {
            return;
        }
        pool.add(condition);
        weights.add(weight);
    }

    private static void addCondition(MaidRecord record, Condition condition, int severity,
                                     Trigger cause, RandomSource random) {
        int min = condition.minSeconds() * 20;
        int max = condition.maxSeconds() * 20;
        int duration = min + random.nextInt(Math.max(1, max - min));
        ActiveCondition active = new ActiveCondition(condition.id(), severity, duration);
        active.setCause(cause);
        record.conditions().add(active);
    }

    // ------------------------------------------------------------------ 表现与提示

    private static void syncMarker(EntityMaid maid, MaidRecord record) {
        if (record.conditions().isEmpty()) {
            return;
        }
        int severity = 1;
        int remaining = 0;
        for (ActiveCondition active : record.conditions()) {
            severity = Math.max(severity, active.severity());
            remaining = Math.max(remaining, active.remainingTicks());
        }
        maid.addEffect(new MobEffectInstance(ModEffects.UNWELL, Math.max(40, remaining),
                severity - 1, true, true, true));
    }

    private static void maybeSpeak(ServerLevel level, EntityMaid maid, MaidRecord record, long now) {
        if (record.conditions().isEmpty() || now - record.lastSymptomTick() < ClinicSettings.SYMPTOM_SPEAK_INTERVAL_TICKS) {
            return;
        }
        record.setLastSymptomTick(now);
        ClinicData.get(level).setDirty();
        ActiveCondition active = record.conditions().get(level.random.nextInt(record.conditions().size()));
        Condition definition = Conditions.byId(active.conditionId());
        if (definition == null) {
            return;
        }
        String key = sickLineKey(definition, level.random);
        showBubble(maid, key);
    }

    private static String sickLineKey(Condition definition, net.minecraft.util.RandomSource random) {
        String[] lines;
        if (definition == Conditions.GASTROENTERITIS || definition == Conditions.DIETARY_GASTROENTERITIS
                || definition == Conditions.DEHYDRATION) {
            lines = new String[]{"message.maidclinic.sick.line.stomach",
                    "message.maidclinic.sick.line.stomach2"};
        } else if (definition == Conditions.INSOMNIA) {
            lines = new String[]{"message.maidclinic.sick.line.tired",
                    "message.maidclinic.sick.line.tired2"};
        } else if (definition == Conditions.WOUND_INFECTION || definition == Conditions.MINOR_BURN) {
            lines = new String[]{"message.maidclinic.sick.line.pain",
                    "message.maidclinic.sick.line.pain2"};
        } else if (definition == Conditions.VIRAL_URI || definition == Conditions.BACTERIAL_PHARYNGITIS
                || definition == Conditions.ALLERGIC_RHINITIS) {
            lines = new String[]{"message.maidclinic.sick.line.cough",
                    "message.maidclinic.sick.line.cough2"};
        } else if (definition.feverPerSeverity() > 0.4F) {
            lines = new String[]{"message.maidclinic.sick.line.fever",
                    "message.maidclinic.sick.line.fever2"};
        } else {
            lines = new String[]{"message.maidclinic.sick.line.weak",
                    "message.maidclinic.sick.line.weak2",
                    "message.maidclinic.sick.line.weak3"};
        }
        return lines[random.nextInt(lines.length)];
    }

    private static void notifyOwner(EntityMaid maid, Component message) {
        ServerPlayer owner = nearbyOwner(maid, 32.0D);
        if (owner != null) {
            Msg.chat(owner, message);
        }
    }

    private static ServerPlayer nearbyOwner(EntityMaid maid, double range) {
        if (maid.getOwner() instanceof ServerPlayer owner
                && owner.level() == maid.level()
                && owner.distanceToSqr(maid) <= range * range) {
            return owner;
        }
        return null;
    }

    // ------------------------------------------------------------------ 文本

    /** 首次问诊时的“建档”信息：固定属性 + 当前可变属性，写入问诊记录。 */
    private static List<Component> profilePages(EntityMaid maid, MaidProfile profile) {
        List<Component> pages = new ArrayList<>();
        pages.add(Msg.aqua("message.maidclinic.profile.header", maid.getName().getString()));
        pages.add(Msg.tr("message.maidclinic.profile.body1",
                Msg.tr(profile.bloodTypeKey()), Msg.tr(profile.rhKey()),
                profile.heightCm(), profile.weightText(), profile.bmiText(),
                Msg.tr(profile.bmiCategoryKey())));
        pages.add(Msg.tr("message.maidclinic.profile.body2",
                profile.penicillinAllergy()
                        ? Msg.tr("profile.maidclinic.allergy.penicillin")
                        : Msg.tr("profile.maidclinic.allergy.none"),
                familyText(profile), Msg.tr(profile.constitutionKey())));
        pages.add(Msg.gray("message.maidclinic.profile.blood_note"));
        pages.add(Msg.tr("message.maidclinic.profile.lifestyle",
                profile.exercise(), profile.saltIntake(), profile.sugarIntake(),
                profile.sleepQuality(), profile.stress()));
        pages.add(Msg.gray("message.maidclinic.profile.note"));
        return pages;
    }

    private static Component familyText(MaidProfile profile) {
        MutableComponent family = Component.empty();
        if (profile.familyHypertension()) {
            family.append(Msg.tr("profile.maidclinic.family.hypertension"));
        }
        if (profile.familyDiabetes()) {
            if (!family.getString().isEmpty()) {
                family.append(Component.literal("、"));
            }
            family.append(Msg.tr("profile.maidclinic.family.diabetes"));
        }
        return family.getString().isEmpty() ? Msg.tr("profile.maidclinic.family.none") : family;
    }

    private static Component sequelaText(MaidRecord record) {
        MutableComponent text = Component.empty();
        for (String id : record.sequelae()) {
            Sequela sequela = Sequela.byId(id);
            if (sequela == null) {
                continue;
            }
            if (!text.getString().isEmpty()) {
                text.append(Component.literal("、"));
            }
            text.append(Msg.tr(sequela.translationKey()));
        }
        return text;
    }

    private static Component diagnosisText(MaidRecord record) {
        if (record.conditions().isEmpty()) {
            return Msg.tr("message.maidclinic.exam.no_findings");
        }
        MutableComponent text = Component.empty();
        for (ActiveCondition active : record.conditions()) {
            Condition definition = Conditions.byId(active.conditionId());
            if (definition == null) {
                continue;
            }
            if (!text.getString().isEmpty()) {
                text.append(Component.literal("、"));
            }
            text.append(Msg.tr(definition.nameKey()))
                    .append(Component.literal("（"))
                    .append(severityText(active.severity()))
                    .append(Component.literal("）"));
        }
        return text;
    }

    private static Component severityText(int severity) {
        return Msg.tr("severity.maidclinic." + Math.min(3, Math.max(1, severity)));
    }

    private static Component symptomText(Condition definition) {
        MutableComponent text = Component.empty();
        for (String key : definition.symptoms()) {
            if (!text.getString().isEmpty()) {
                text.append(Component.literal("、"));
            }
            text.append(Msg.tr(key));
        }
        return text;
    }

    private static Component behaviorText(List<MaidBehavior.Reading> readings) {
        MutableComponent text = Component.empty();
        for (MaidBehavior.Reading reading : readings) {
            if (!text.getString().isEmpty()) {
                text.append(Component.literal("｜"));
            }
            text.append(Msg.tr(reading.trigger().exposureKey()))
                    .append(Component.literal(" " + reading.value()));
        }
        return text;
    }

    private static Component formatTicks(int ticks) {
        int seconds = Math.max(0, ticks / 20);
        int minutes = seconds / 60;
        int rest = seconds % 60;
        return Msg.tr("time.maidclinic.remaining", minutes, rest);
    }

    /** 供外部（如方块/指令）调用的诊断入口，保持行为一致。 */
    public static void diagnose(ServerPlayer player, EntityMaid maid) {
        examine(player, maid, DiagnosticItem.Mode.STETHOSCOPE);
    }

    /** 兼容入口：仅用于调试或其它模组集成时读取档案。 */
    public static MaidRecord recordOf(ServerLevel level, Mob maid) {
        return ClinicData.get(level).recordOf(maid.getUUID(), maid.getName().getString());
    }
}
