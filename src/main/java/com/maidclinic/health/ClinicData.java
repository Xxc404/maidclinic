package com.maidclinic.health;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 存档级的健康档案库。
 * <p>
 * 用 {@link SavedData} 而不是写在女仆实体 NBT 上，原因有二：
 * <ol>
 *     <li>不需要依赖任何实体数据注入接口，兼容性最好；</li>
 *     <li>即使女仆被收起/转移，档案仍按 UUID 保留。</li>
 * </ol>
 */
public final class ClinicData extends SavedData {
    private static final String FILE_ID = "maidclinic";
    private static final String TAG_RECORDS = "records";

    private static final SavedData.Factory<ClinicData> FACTORY =
            new SavedData.Factory<>(ClinicData::new, ClinicData::load, DataFixTypes.LEVEL);

    private final Map<UUID, MaidRecord> records = new HashMap<>();
    /** 玩家个人的玩法开关（致死、慢性病）；没有条目表示跟随配置。 */
    private final Map<UUID, PlayerSettings> playerSettings = new HashMap<>();
    /** 死亡女仆等待“复活继承”的健康档案，按主人+名字+模型匹配。 */
    private final Map<String, RecordInheritance> recordInheritances = new HashMap<>();

    /** 跨死亡保留的健康档案内容（不含当前疾病、临时状态与行为暴露）。 */
    public record Inheritance(int trust, int cured, int exams, int wrongDoses, boolean contracted,
                              int doctorXp, int healthyDays, int carePoints, int careTier,
                              Set<String> sequelae, CompoundTag profile) {
        public Inheritance {
            sequelae = Set.copyOf(sequelae == null ? Set.of() : sequelae);
            profile = profile == null ? null : profile.copy();
        }
    }

    private static final class RecordInheritance {
        private final String name;
        private final String model;
        private final Inheritance data;
        private final long tick;

        private RecordInheritance(String name, String model, Inheritance data, long tick) {
            this.name = name;
            this.model = model;
            this.data = data;
            this.tick = tick;
        }
    }

    public static ClinicData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FACTORY, FILE_ID);
    }

    /** 读取（或创建）某只女仆的档案。 */
    public MaidRecord recordOf(UUID maidId, String maidName) {
        MaidRecord record = records.computeIfAbsent(maidId, MaidRecord::new);
        if (maidName != null && !maidName.isEmpty()) {
            record.setMaidName(maidName);
        }
        return record;
    }

    public MaidRecord find(UUID maidId) {
        return records.get(maidId);
    }

    public Map<UUID, MaidRecord> records() {
        return records;
    }

    /** 玩家个人的“疾病致死”设置；返回 null 表示跟随配置文件。 */
    public Boolean hardcoreOverride(UUID playerId) {
        PlayerSettings settings = playerSettings.get(playerId);
        return settings == null ? null : settings.hardcore();
    }

    public void setHardcoreOverride(UUID playerId, Boolean value) {
        PlayerSettings settings = settingsFor(playerId);
        settings.setHardcore(value);
        dropIfEmpty(playerId, settings);
        setDirty();
    }

    /** 玩家个人的“慢性病模式”设置；返回 null 表示跟随配置文件。 */
    public Boolean chronicOverride(UUID playerId) {
        PlayerSettings settings = playerSettings.get(playerId);
        return settings == null ? null : settings.chronic();
    }

    public void setChronicOverride(UUID playerId, Boolean value) {
        PlayerSettings settings = settingsFor(playerId);
        settings.setChronic(value);
        dropIfEmpty(playerId, settings);
        setDirty();
    }

    /** 玩家个人的“疾病影响工作效率”设置；返回 null 表示跟随配置文件。 */
    public Boolean efficiencyOverride(UUID playerId) {
        PlayerSettings settings = playerSettings.get(playerId);
        return settings == null ? null : settings.efficiency();
    }

    public void setEfficiencyOverride(UUID playerId, Boolean value) {
        PlayerSettings settings = settingsFor(playerId);
        settings.setEfficiency(value);
        dropIfEmpty(playerId, settings);
        setDirty();
    }

    /** 玩家个人的“死亡后保留后遗症”设置；返回 null 表示跟随配置文件。 */
    public Boolean sequelaOverride(UUID playerId) {
        PlayerSettings settings = playerSettings.get(playerId);
        return settings == null ? null : settings.sequela();
    }

    public void setSequelaOverride(UUID playerId, Boolean value) {
        PlayerSettings settings = settingsFor(playerId);
        settings.setSequela(value);
        dropIfEmpty(playerId, settings);
        setDirty();
    }

    /** 玩家个人的“危重掉血方式”；返回 null 表示跟随配置文件。 */
    public DeathStyle deathStyleOverride(UUID playerId) {
        PlayerSettings settings = playerSettings.get(playerId);
        return settings == null ? null : settings.deathStyle();
    }

    public void setDeathStyleOverride(UUID playerId, DeathStyle value) {
        PlayerSettings settings = settingsFor(playerId);
        settings.setDeathStyle(value);
        dropIfEmpty(playerId, settings);
        setDirty();
    }

    /** 记录一份等待复活继承的完整健康档案。 */
    public void addRecordInheritance(UUID owner, String name, String model, MaidRecord record, long now) {
        if (owner == null || record == null) {
            return;
        }
        CompoundTag profile = record.profileOrNull() == null ? null : record.profileOrNull().save();
        Inheritance data = new Inheritance(record.trust(), record.cured(), record.exams(),
                record.wrongDoses(), record.contracted(), record.doctorXp(),
                record.healthyDays(), record.carePoints(), record.careTier(),
                record.sequelae(), profile);
        addRecordInheritance(owner, name, model, data, now);
    }

    public void addRecordInheritance(UUID owner, String name, String model, Inheritance data, long now) {
        if (owner == null || data == null) {
            return;
        }
        recordInheritances.put(inheritanceKey(owner, name, model),
                new RecordInheritance(name, model, data, now));
        setDirty();
    }

    /** 取走等待继承的健康档案；没有或已过期时返回 null。 */
    public Inheritance takeRecordInheritance(UUID owner, String name, String model,
                                             long now, long maxAgeTicks) {
        if (owner == null) {
            return null;
        }
        RecordInheritance inheritance = recordInheritances.remove(inheritanceKey(owner, name, model));
        setDirty();
        if (inheritance == null || now - inheritance.tick > maxAgeTicks) {
            return null;
        }
        return inheritance.data;
    }

    private static String inheritanceKey(UUID owner, String name, String model) {
        return owner + "|" + (name == null ? "" : name.toLowerCase(Locale.ROOT))
                + "|" + (model == null ? "" : model);
    }

    private PlayerSettings settingsFor(UUID playerId) {
        return playerSettings.computeIfAbsent(playerId, id -> new PlayerSettings());
    }

    private void dropIfEmpty(UUID playerId, PlayerSettings settings) {
        if (settings.isEmpty()) {
            playerSettings.remove(playerId);
        }
    }

    /**
     * 清理长期没有出现的女仆档案，避免存档无限膨胀。
     * 只清理“曾经在线过、且超过 maxAgeTicks 未出现”的记录。
     */
    public void prune(long now, long maxAgeTicks) {
        boolean removed = records.values().removeIf(record ->
                record.lastSeenTick() > 0L && now - record.lastSeenTick() > maxAgeTicks);
        if (removed) {
            setDirty();
        }
    }

    // ------------------------------------------------------------------ 序列化

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (MaidRecord record : records.values()) {
            list.add(writeRecord(record));
        }
        tag.put(TAG_RECORDS, list);

        ListTag settingsList = new ListTag();
        for (Map.Entry<UUID, PlayerSettings> entry : playerSettings.entrySet()) {
            PlayerSettings settings = entry.getValue();
            if (settings.isEmpty()) {
                continue;
            }
            CompoundTag entryTag = new CompoundTag();
            entryTag.putUUID("player", entry.getKey());
            if (settings.hardcore() != null) {
                entryTag.putBoolean("hardcore", settings.hardcore());
            }
            if (settings.chronic() != null) {
                entryTag.putBoolean("chronic", settings.chronic());
            }
            if (settings.efficiency() != null) {
                entryTag.putBoolean("efficiency", settings.efficiency());
            }
            if (settings.sequela() != null) {
                entryTag.putBoolean("sequela", settings.sequela());
            }
            if (settings.deathStyle() != null) {
                entryTag.putString("death_style", settings.deathStyle().id());
            }
            settingsList.add(entryTag);
        }
        tag.put("player_settings", settingsList);

        ListTag inheritanceList = new ListTag();
        for (Map.Entry<String, RecordInheritance> entry : recordInheritances.entrySet()) {
            RecordInheritance inheritance = entry.getValue();
            Inheritance data = inheritance.data;
            CompoundTag inheritanceTag = new CompoundTag();
            // key 的第一段是主人 UUID
            String key = entry.getKey();
            int split = key.indexOf('|');
            if (split <= 0) {
                continue;
            }
            try {
                inheritanceTag.putUUID("owner", UUID.fromString(key.substring(0, split)));
            } catch (IllegalArgumentException exception) {
                continue;
            }
            inheritanceTag.putString("name", inheritance.name);
            inheritanceTag.putString("model", inheritance.model);
            inheritanceTag.putLong("tick", inheritance.tick);
            inheritanceTag.putInt("trust", data.trust());
            inheritanceTag.putInt("cured", data.cured());
            inheritanceTag.putInt("exams", data.exams());
            inheritanceTag.putInt("wrong_doses", data.wrongDoses());
            inheritanceTag.putBoolean("contracted", data.contracted());
            inheritanceTag.putInt("doctor_xp", data.doctorXp());
            inheritanceTag.putInt("healthy_days", data.healthyDays());
            inheritanceTag.putInt("care_points", data.carePoints());
            inheritanceTag.putInt("care_tier", data.careTier());
            if (data.profile() != null) {
                inheritanceTag.put("profile", data.profile());
            }
            ListTag ids = new ListTag();
            for (String id : data.sequelae()) {
                CompoundTag idTag = new CompoundTag();
                idTag.putString("id", id);
                ids.add(idTag);
            }
            inheritanceTag.put("sequelae", ids);
            inheritanceList.add(inheritanceTag);
        }
        tag.put("record_inheritance", inheritanceList);
        return tag;
    }

    public static ClinicData load(CompoundTag tag, HolderLookup.Provider registries) {
        ClinicData data = new ClinicData();
        ListTag list = tag.getList(TAG_RECORDS, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            MaidRecord record = readRecord(entry);
            if (record != null) {
                data.records.put(record.maidId(), record);
            }
        }

        ListTag settingsList = tag.getList("player_settings", Tag.TAG_COMPOUND);
        for (int i = 0; i < settingsList.size(); i++) {
            CompoundTag entry = settingsList.getCompound(i);
            if (!entry.contains("player")) {
                continue;
            }
            UUID playerId = entry.getUUID("player");
            PlayerSettings settings = data.settingsFor(playerId);
            if (entry.contains("hardcore")) {
                settings.setHardcore(entry.getBoolean("hardcore"));
            }
            if (entry.contains("chronic")) {
                settings.setChronic(entry.getBoolean("chronic"));
            }
            if (entry.contains("efficiency")) {
                settings.setEfficiency(entry.getBoolean("efficiency"));
            }
            if (entry.contains("sequela")) {
                settings.setSequela(entry.getBoolean("sequela"));
            }
            if (entry.contains("death_style")) {
                settings.setDeathStyle(DeathStyle.byId(entry.getString("death_style")));
            }
            data.dropIfEmpty(playerId, settings);
        }

        ListTag inheritanceList = tag.getList("record_inheritance", Tag.TAG_COMPOUND);
        if (inheritanceList.isEmpty()) {
            // 兼容上一版只保存后遗症的格式
            inheritanceList = tag.getList("sequela_inheritance", Tag.TAG_COMPOUND);
        }
        for (int i = 0; i < inheritanceList.size(); i++) {
            CompoundTag entry = inheritanceList.getCompound(i);
            if (!entry.contains("owner")) {
                continue;
            }
            Set<String> ids = new LinkedHashSet<>();
            ListTag idList = entry.getList("sequelae", Tag.TAG_COMPOUND);
            for (int j = 0; j < idList.size(); j++) {
                String id = idList.getCompound(j).getString("id");
                if (Sequela.byId(id) != null) {
                    ids.add(id);
                }
            }
            CompoundTag profile = entry.contains("profile", Tag.TAG_COMPOUND)
                    ? entry.getCompound("profile") : null;
            Inheritance inheritance = new Inheritance(entry.getInt("trust"), entry.getInt("cured"),
                    entry.getInt("exams"), entry.getInt("wrong_doses"), entry.getBoolean("contracted"),
                    entry.getInt("doctor_xp"), entry.getInt("healthy_days"),
                    entry.getInt("care_points"), entry.getInt("care_tier"), ids, profile);
            data.addRecordInheritance(entry.getUUID("owner"), entry.getString("name"),
                    entry.getString("model"), inheritance, entry.getLong("tick"));
        }

        // 兼容早期版本保存的 "hardcore" 列表
        ListTag legacy = tag.getList("hardcore", Tag.TAG_COMPOUND);
        for (int i = 0; i < legacy.size(); i++) {
            CompoundTag entry = legacy.getCompound(i);
            if (entry.contains("player")) {
                data.setHardcoreOverride(entry.getUUID("player"), entry.getBoolean("value"));
            }
        }
        return data;
    }

    private static CompoundTag writeRecord(MaidRecord record) {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("id", record.maidId());
        tag.putString("name", record.maidName());
        tag.putInt("trust", record.trust());
        tag.putInt("cured", record.cured());
        tag.putInt("exams", record.exams());
        tag.putInt("wrong_doses", record.wrongDoses());
        tag.putBoolean("contracted", record.contracted());
        tag.putLong("last_exam", record.lastExamTick());
        tag.putLong("last_seen", record.lastSeenTick());
        tag.putFloat("last_health", record.lastHealth());
        tag.putLong("dose_day", record.doseDay());
        tag.putInt("antipyretic_doses", record.antipyreticDoses());
        tag.putLong("masked_until", record.maskedUntil());
        tag.putLong("sleep_death_at", record.sleepDeathAt());
        tag.putBoolean("sleep_death_attempted", record.sleepDeathAttempted());
        tag.putLong("sleep_wake_at", record.sleepWakeAt());
        tag.putLong("last_symptom", record.lastSymptomTick());
        tag.putBoolean("resting", record.resting());
        tag.putBoolean("forced_work", record.forcedWork());
        tag.putInt("doctor_xp", record.doctorXp());
        tag.putInt("healthy_days", record.healthyDays());
        tag.putInt("care_points", record.carePoints());
        tag.putInt("care_tier", record.careTier());
        tag.putInt("healthy_days", record.healthyDays());

        ListTag sequelae = new ListTag();
        for (String id : record.sequelae()) {
            CompoundTag sequelaTag = new CompoundTag();
            sequelaTag.putString("id", id);
            sequelae.add(sequelaTag);
        }
        tag.put("sequelae", sequelae);

        ListTag conditions = new ListTag();
        for (ActiveCondition active : record.conditions()) {
            CompoundTag conditionTag = new CompoundTag();
            conditionTag.putString("id", active.conditionId());
            conditionTag.putString("cause", active.cause().id());
            conditionTag.putInt("severity", active.severity());
            conditionTag.putInt("elapsed", active.elapsedTicks());
            conditionTag.putInt("duration", active.durationTicks());
            conditionTag.putBoolean("treated", active.treated());
            conditionTag.putBoolean("supported", active.supported());
            conditionTag.putInt("doses", active.doses());
            conditionTag.putLong("last_dose", active.lastDoseTick());
            conditions.add(conditionTag);
        }
        tag.put("conditions", conditions);
        tag.put("behavior", record.behavior().save());

        if (record.profileOrNull() != null) {
            tag.put("profile", record.profileOrNull().save());
        }
        return tag;
    }

    private static MaidRecord readRecord(CompoundTag tag) {
        if (!tag.contains("id")) {
            return null;
        }
        MaidRecord record = new MaidRecord(tag.getUUID("id"));
        record.setMaidName(tag.getString("name"));
        record.setTrust(tag.getInt("trust"));
        record.setCured(tag.getInt("cured"));
        record.setExams(tag.getInt("exams"));
        record.setWrongDoses(tag.getInt("wrong_doses"));
        record.setContracted(tag.getBoolean("contracted"));
        record.setLastExamTick(tag.getLong("last_exam"));
        record.setLastSeenTick(tag.getLong("last_seen"));
        record.setLastHealth(tag.getFloat("last_health"));
        record.setDoseDay(tag.getLong("dose_day"));
        record.setAntipyreticDoses(tag.getInt("antipyretic_doses"));
        record.setMaskedUntil(tag.getLong("masked_until"));
        record.setSleepDeathAt(tag.getLong("sleep_death_at"));
        record.setSleepDeathAttempted(tag.getBoolean("sleep_death_attempted"));
        record.setSleepWakeAt(tag.getLong("sleep_wake_at"));
        record.setLastSymptomTick(tag.getLong("last_symptom"));
        record.setResting(tag.getBoolean("resting"));
        record.setForcedWork(tag.getBoolean("forced_work"));
        record.setDoctorXp(tag.getInt("doctor_xp"));
        record.setCarePoints(tag.getInt("care_points"));
        record.setCareTier(tag.getInt("care_tier"));
        record.setHealthyDays(tag.getInt("healthy_days"));

        ListTag sequelae = tag.getList("sequelae", Tag.TAG_COMPOUND);
        for (int i = 0; i < sequelae.size(); i++) {
            Sequela sequela = Sequela.byId(sequelae.getCompound(i).getString("id"));
            if (sequela != null) {
                record.addSequela(sequela);
            }
        }

        ListTag conditions = tag.getList("conditions", Tag.TAG_COMPOUND);
        for (int i = 0; i < conditions.size(); i++) {
            CompoundTag conditionTag = conditions.getCompound(i);
            String id = conditionTag.getString("id");
            if (Conditions.byId(id) == null) {
                continue;
            }
            ActiveCondition active = new ActiveCondition(id,
                    Math.max(1, conditionTag.getInt("severity")),
                    Math.max(20, conditionTag.getInt("duration")));
            active.setCause(Trigger.byId(conditionTag.getString("cause")));
            active.progress(conditionTag.getInt("elapsed"));
            active.setTreated(conditionTag.getBoolean("treated"));
            active.setSupported(conditionTag.getBoolean("supported"));
            active.setDoses(conditionTag.getInt("doses"));
            active.setLastDoseTick(conditionTag.getLong("last_dose"));
            record.conditions().add(active);
        }

        if (tag.contains("behavior", Tag.TAG_COMPOUND)) {
            record.setBehavior(MaidBehavior.load(tag.getCompound("behavior")));
        }

        if (tag.contains("profile", Tag.TAG_COMPOUND)) {
            record.setProfile(MaidProfile.load(tag.getCompound("profile")));
        }
        return record;
    }
}
