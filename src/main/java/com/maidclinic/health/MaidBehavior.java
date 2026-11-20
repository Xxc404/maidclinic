package com.maidclinic.health;

import com.maidclinic.ClinicSettings;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * 女仆的“行为暴露”记录：把游戏内行为换算成一组会缓慢衰减的风险值。
 * <p>
 * 每 20 tick 采样一次。淋雨、受热、战斗、受伤、劳作、熬夜、挨饿、压力、接触过敏原
 * 都会让对应数值上升；离开对应环境/休息/吃饱后又按指数衰减。
 * 数值越高，发病概率越高，也越可能决定“得什么病”。
 * </p>
 * <p>
 * 这些数值会写进健康档案，因此换世界/重进游戏后仍然保留；但它们只是<b>最近一段时间</b>
 * 的行为，离线太久会按时间自动衰减，避免“上次淋雨导致三天后感冒”。
 * </p>
 */
public final class MaidBehavior {
    /** 健康档案里展示的一条行为读数。 */
    public record Reading(Trigger trigger, int value) {
    }

    /**
     * 一次采样需要的游戏状态。
     * <p>由 {@code ClinicService} 从女仆实体上读取后填入，本类不直接依赖 TLM，
     * 这样即使没有安装车万女仆，健康档案相关代码也能正常加载。</p>
     */
    public static final class Signals {
        public boolean raining;
        public boolean wet;
        public boolean inWater;
        public boolean cold;
        public boolean hot;
        public boolean sleeping;
        public boolean sitting;
        public boolean passenger;
        public boolean night;
        public boolean workTime;
        public boolean laborTask;
        public boolean combatTask;
        public boolean allergenTask;
        public boolean fishingTask;
        public boolean working;
        public boolean moving;
        public boolean fighting;
        public boolean onFire;
        public int hunger;
        public int favorability;
        public float healthRatio;
        public float biomeTemperature;
    }

    // 任务分类由 ClinicService 读取 TLM 任务 id 后填入 Signals，本类不直接依赖 TLM。

    /** 会造成烫伤/灼伤的伤害类型。 */
    private static final Set<String> FIRE_DAMAGE = Set.of(
            "in_fire", "on_fire", "lava", "hot_floor", "campfire", "unattended_campfire",
            "fireball", "fireworks", "lightning_bolt");
    /** 战斗类伤害：更容易造成伤口感染。 */
    private static final Set<String> COMBAT_DAMAGE = Set.of(
            "mob", "mob_attack", "player", "arrow", "trident", "explosion", "fireball", "sting", "thorns");
    private static final Set<String> COLD_DAMAGE = Set.of("freeze");

    // 衰减系数（每秒）：越接近 1 忘得越慢。
    private static final float DECAY_CHILL = 0.9980F;
    private static final float DECAY_HEAT = 0.9970F;
    private static final float DECAY_EXERTION = 0.9985F;
    private static final float DECAY_SLEEP = 0.9990F;
    private static final float DECAY_INJURY = 0.9950F;
    private static final float DECAY_HUNGER = 0.9970F;
    private static final float DECAY_STRESS = 0.9995F;
    private static final float DECAY_ALLERGEN = 0.9970F;

    private float chill;
    private float heat;
    private float exertion;
    private float sleepDebt;
    private float injury;
    private float hunger;
    private float stress;
    private float allergen;

    private long lastSampleTick;
    private long lastWarnTick;

    /** 环境扫描结果（每 5 秒刷新一次，不写入存档）。 */
    private boolean nearHeatSource;
    private boolean nearAllergen;
    /** 女仆正上方是否有完整方块遮挡（屋顶/岩层），用于判断是否被暴晒。 */
    private boolean covered;

    // ------------------------------------------------------------------ 采样

    /**
     * 采样一次行为状态。
     *
     * @return 是否还有非零暴露值（调用方据此决定是否标记存档脏数据）
     */
    public boolean sample(ServerLevel level, BlockPos pos, Signals signals, long now, boolean forceScan) {
        boolean firstSample = lastSampleTick == 0L;
        long delta = firstSample ? 20L : Math.max(0L, now - lastSampleTick);
        lastSampleTick = now;
        if (delta > 20L) {
            // 女仆可能被收起/区块卸载了一段时间，先按秒补上衰减
            decay(Math.min(3600.0D, delta / 20.0D));
        }

        if (firstSample || forceScan) {
            scanEnvironment(level, pos);
        }

        boolean sleeping = signals.sleeping;
        boolean resting = sleeping || signals.sitting || signals.passenger;
        boolean active = !resting;
        boolean working = signals.working;
        boolean moving = signals.moving;
        boolean fighting = signals.fighting;
        boolean onFire = signals.onFire;
        boolean wet = signals.wet;
        boolean hot = signals.hot;
        boolean cold = signals.cold;
        boolean laborTask = signals.laborTask;
        boolean allergenTask = signals.allergenTask;
        float biomeTemperature = signals.biomeTemperature;
        int hungerLevel = signals.hunger;
        boolean lowFavor = signals.favorability < 64;

        // 受寒：淋雨/泡水最明显，寒冷群系与钓鱼任务会加速；靠近火源或高温会缓解
        float chillGain = 0.0F;
        if (wet) {
            chillGain += 1.0F;
        }
        if (cold) {
            chillGain += 0.2F;
        }
        if (signals.inWater && !cold) {
            chillGain += 0.2F;
        }
        if (signals.fishingTask && active) {
            chillGain += 0.6F;
        }
        if (nearHeatSource) {
            chillGain -= 1.2F;
        }
        if (hot) {
            // 有完整方块遮挡时只是闷热，不算暴晒
            chillGain -= covered ? 0.1F : 0.4F;
        }
        if (sleeping) {
            chillGain *= 0.5F;
        }
        chill = update(chill, chillGain, DECAY_CHILL);

        // 受热：综合群系温度、上方遮挡与附近热源；火把/灵魂火把不算热源
        float heatGain = 0.0F;
        if (onFire) {
            heatGain += 6.0F;
        }
        if (nearHeatSource) {
            heatGain += 0.4F;
        }
        if (hot && !wet) {
            if (covered) {
                heatGain += 0.1F;
            } else {
                heatGain += working && moving ? 1.0F : 0.35F;
            }
        }
        if (biomeTemperature > 1.5F) {
            heatGain += covered ? 0.15F : 0.4F;
        }
        if (wet) {
            heatGain -= 1.5F;
        }
        if (resting) {
            heatGain -= 0.4F;
        }
        heat = update(heat, heatGain, DECAY_HEAT);

        // 劳累：工作、战斗、饥饿都会累积；休息时快速恢复
        float exertionGain = 0.0F;
        if (working) {
            if (moving) {
                exertionGain += 0.45F;
            } else if (laborTask) {
                // 站着干活（钓鱼、挤奶、剪羊毛等）也算轻度劳累
                exertionGain += 0.15F;
            }
        }
        if (fighting) {
            exertionGain += 1.0F;
        }
        if (hungerLevel <= 6) {
            exertionGain += 0.5F;
        }
        if (sleepDebt > 60.0F) {
            exertionGain += 0.3F;
        }
        if (resting) {
            exertionGain -= 2.0F;
        }
        exertion = update(exertion, exertionGain, DECAY_EXERTION);

        // 熬夜：夜间继续工作/战斗才会累积，单纯在夜里发呆只算很少；睡觉会快速清偿
        float sleepGain = 0.0F;
        if (sleeping) {
            sleepGain -= 3.0F;
        } else if (signals.night) {
            if (working) {
                sleepGain += 1.0F;
            } else if (fighting) {
                sleepGain += 0.6F;
            } else if (active) {
                sleepGain += 0.05F;
            }
        }
        sleepDebt = update(sleepDebt, sleepGain, DECAY_SLEEP);

        // 外伤：由 onDamaged 直接加，平时缓慢恢复
        injury = update(injury, 0.0F, DECAY_INJURY);
        if (signals.healthRatio < 0.4F) {
            injury = clamp(injury + 0.1F);
        }

        // 饥饿：TLM 饥饿值 0–20，越低越危险；吃饱后会下降
        float hungerGain;
        if (hungerLevel <= 6) {
            hungerGain = 1.5F;
        } else if (hungerLevel <= 12) {
            hungerGain = 0.6F;
        } else if (hungerLevel >= 18) {
            hungerGain = -1.5F;
        } else {
            hungerGain = -0.2F;
        }
        hunger = update(hunger, hungerGain, DECAY_HUNGER);

        // 压力：低好感度、战斗、熬夜、劳累、饥饿都会累积
        float stressGain = 0.0F;
        if (lowFavor) {
            stressGain += 0.25F;
        }
        if (fighting) {
            stressGain += 0.5F;
        }
        if (sleepDebt > 60.0F) {
            stressGain += 0.3F;
        }
        if (exertion > 70.0F) {
            stressGain += 0.2F;
        }
        if (hunger > 60.0F) {
            stressGain += 0.2F;
        }
        if (sleeping) {
            stressGain -= 0.8F;
        } else if (signals.sitting) {
            stressGain -= 0.4F;
        }
        stress = update(stress, stressGain, DECAY_STRESS);

        // 过敏原：花草/蜂箱附近与相关任务会累积，下雨会冲刷掉
        float allergenGain = 0.0F;
        if (nearAllergen || (allergenTask && working)) {
            allergenGain += 0.4F;
        }
        if (signals.raining) {
            allergenGain -= 1.0F;
        }
        if (resting && !nearAllergen) {
            allergenGain -= 0.3F;
        }
        allergen = update(allergen, allergenGain, DECAY_ALLERGEN);

        return total() > 0.01F;
    }

    private static float update(float value, float gain, float decay) {
        return clamp(value * decay + gain);
    }

    private static float clamp(float value) {
        return Math.max(0.0F, Math.min(100.0F, value));
    }

    private float total() {
        return chill + heat + exertion + sleepDebt + injury + hunger + stress + allergen;
    }

    private void decay(double seconds) {
        chill = (float) (chill * Math.pow(DECAY_CHILL, seconds));
        heat = (float) (heat * Math.pow(DECAY_HEAT, seconds));
        exertion = (float) (exertion * Math.pow(DECAY_EXERTION, seconds));
        sleepDebt = (float) (sleepDebt * Math.pow(DECAY_SLEEP, seconds));
        injury = (float) (injury * Math.pow(DECAY_INJURY, seconds));
        hunger = (float) (hunger * Math.pow(DECAY_HUNGER, seconds));
        stress = (float) (stress * Math.pow(DECAY_STRESS, seconds));
        allergen = (float) (allergen * Math.pow(DECAY_ALLERGEN, seconds));
    }

    // ------------------------------------------------------------------ 环境

    private void scanEnvironment(ServerLevel level, BlockPos origin) {
        covered = hasOverheadCover(level, origin);
        boolean heat = false;
        boolean flowers = false;
        for (int dx = -3; dx <= 3 && !(heat && flowers); dx++) {
            for (int dz = -3; dz <= 3 && !(heat && flowers); dz++) {
                for (int dy = -1; dy <= 2; dy++) {
                    BlockPos pos = origin.offset(dx, dy, dz);
                    if (!level.isLoaded(pos)) {
                        continue;
                    }
                    BlockState state = level.getBlockState(pos);
                    if (!heat && isHeatSource(state)) {
                        heat = true;
                    }
                    if (!flowers && isAllergen(state)) {
                        flowers = true;
                    }
                    if (heat && flowers) {
                        break;
                    }
                }
            }
        }
        nearHeatSource = heat;
        nearAllergen = flowers;
    }

    /** 检查头顶是否有完整方块；用于区分“太阳直晒”和“有屋顶/岩层遮挡”。 */
    private static boolean hasOverheadCover(ServerLevel level, BlockPos origin) {
        for (int dy = 2; dy <= 8; dy++) {
            BlockPos pos = origin.above(dy);
            if (!level.isLoaded(pos)) {
                return false;
            }
            if (level.getBlockState(pos).isCollisionShapeFullBlock(level, pos)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 附近的热源方块。
     * <p>按需求只统计真正的热源：火焰、点燃的营火、岩浆、岩浆块、点燃的熔炉类；
     * <b>火把与灵魂火把不算热源</b>，也不会被计入。</p>
     */
    private static boolean isHeatSource(BlockState state) {
        if (state.is(Blocks.FIRE) || state.is(Blocks.SOUL_FIRE) || state.is(Blocks.MAGMA_BLOCK)) {
            return true;
        }
        if (state.is(Blocks.CAMPFIRE) || state.is(Blocks.SOUL_CAMPFIRE)) {
            return state.getValue(CampfireBlock.LIT);
        }
        if (state.getBlock() instanceof AbstractFurnaceBlock) {
            return state.getValue(AbstractFurnaceBlock.LIT);
        }
        return state.getFluidState().is(FluidTags.LAVA);
    }

    private static boolean isAllergen(BlockState state) {
        return state.is(BlockTags.FLOWERS)
                || state.is(Blocks.BEEHIVE)
                || state.is(Blocks.BEE_NEST);
    }

    // ------------------------------------------------------------------ 外部事件

    /** 女仆受到伤害：累积外伤、受热与压力。 */
    public void onDamaged(float amount, String damageType) {
        if (amount <= 0.0F) {
            return;
        }
        injury = clamp(injury + Math.min(25.0F, amount * 4.0F));
        if (FIRE_DAMAGE.contains(damageType)) {
            heat = clamp(heat + Math.min(50.0F, amount * 7.0F));
        }
        if (COMBAT_DAMAGE.contains(damageType)) {
            stress = clamp(stress + Math.min(10.0F, amount * 2.0F));
        }
        if (COLD_DAMAGE.contains(damageType)) {
            chill = clamp(chill + 20.0F);
        }
        if ("starve".equals(damageType)) {
            hunger = clamp(hunger + 15.0F);
        }
    }

    /** 女仆吃了东西：饥饿暴露下降。 */
    public void onAte() {
        hunger = Math.max(0.0F, hunger - 25.0F);
    }

    /** 完整睡完一觉：劳累直接扣减，最低扣到 0。 */
    public void relieveExertion(float amount) {
        exertion = clamp(exertion - amount);
    }

    /** 生病被强迫工作：额外累积劳累与压力。 */
    public void onForcedWork() {
        exertion = clamp(exertion + 4.0F);
        stress = clamp(stress + 2.0F);
    }

    // ------------------------------------------------------------------ 风险

    /** 行为带来的每秒发病概率（不含配置里的自然发病基数）。 */
    public float hazard() {
        float total = hazardOf(chill)
                + hazardOf(heat)
                + hazardOf(exertion)
                + hazardOf(sleepDebt)
                + hazardOf(injury)
                + hazardOf(hunger)
                + hazardOf(stress)
                + hazardOf(allergen);
        return Math.min(ClinicSettings.MAX_BEHAVIOR_HAZARD, total);
    }

    private static float hazardOf(float value) {
        return Math.max(0.0F, value - ClinicSettings.BEHAVIOR_THRESHOLD)
                * ClinicSettings.BEHAVIOR_HAZARD_SCALE;
    }

    /** 按当前暴露值加权抽取诱因；着火/战斗会额外提高对应权重。 */
    public Trigger pickTrigger(RandomSource random, boolean onFire, boolean inCombat) {
        List<Trigger> pool = new ArrayList<>();
        List<Float> weights = new ArrayList<>();
        addCandidate(pool, weights, Trigger.NATURAL, 10.0F);
        addCandidate(pool, weights, Trigger.CHILL, chill);
        addCandidate(pool, weights, Trigger.HEAT, heat);
        addCandidate(pool, weights, Trigger.OVERWORK, exertion);
        addCandidate(pool, weights, Trigger.SLEEP, sleepDebt);
        addCandidate(pool, weights, Trigger.INJURY, injury);
        addCandidate(pool, weights, Trigger.HUNGER, hunger);
        addCandidate(pool, weights, Trigger.STRESS, stress);
        addCandidate(pool, weights, Trigger.ALLERGEN, allergen);
        if (onFire) {
            addCandidate(pool, weights, Trigger.FIRE, 90.0F);
        }
        if (inCombat) {
            addCandidate(pool, weights, Trigger.COMBAT, 35.0F);
        }

        float total = 0.0F;
        for (float weight : weights) {
            total += weight;
        }
        if (total <= 0.0F) {
            return Trigger.NATURAL;
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

    private static void addCandidate(List<Trigger> pool, List<Float> weights, Trigger trigger, float weight) {
        if (weight <= 0.01F) {
            return;
        }
        pool.add(trigger);
        weights.add(weight);
    }

    /** 最高且超过预警阈值的暴露，用于“她快生病了”的提醒。 */
    public Trigger warningTrigger() {
        Trigger best = null;
        float bestValue = ClinicSettings.BEHAVIOR_WARNING_THRESHOLD;
        float[] values = {chill, heat, exertion, sleepDebt, injury, hunger, stress, allergen};
        Trigger[] triggers = {Trigger.CHILL, Trigger.HEAT, Trigger.OVERWORK, Trigger.SLEEP,
                Trigger.INJURY, Trigger.HUNGER, Trigger.STRESS, Trigger.ALLERGEN};
        for (int i = 0; i < values.length; i++) {
            if (values[i] > bestValue) {
                bestValue = values[i];
                best = triggers[i];
            }
        }
        return best;
    }

    /** 健康档案里展示的“近期状态”，只返回最高、且 >=10 的几项。 */
    public List<Reading> readings(int limit) {
        List<Reading> list = new ArrayList<>();
        collect(list, Trigger.CHILL, chill);
        collect(list, Trigger.HEAT, heat);
        collect(list, Trigger.OVERWORK, exertion);
        collect(list, Trigger.SLEEP, sleepDebt);
        collect(list, Trigger.INJURY, injury);
        collect(list, Trigger.HUNGER, hunger);
        collect(list, Trigger.STRESS, stress);
        collect(list, Trigger.ALLERGEN, allergen);
        list.sort(Comparator.comparingInt(Reading::value).reversed());
        return list.size() <= limit ? list : list.subList(0, limit);
    }

    private static void collect(List<Reading> list, Trigger trigger, float value) {
        int rounded = Math.round(value);
        if (rounded >= 10) {
            list.add(new Reading(trigger, rounded));
        }
    }

    // ------------------------------------------------------------------ 测试/调试

    public float exposure(Trigger trigger) {
        return switch (trigger) {
            case CHILL -> chill;
            case HEAT -> heat;
            case OVERWORK -> exertion;
            case SLEEP -> sleepDebt;
            case INJURY -> injury;
            case HUNGER -> hunger;
            case STRESS -> stress;
            case ALLERGEN -> allergen;
            default -> 0.0F;
        };
    }

    public void setExposure(Trigger trigger, float value) {
        float clamped = clamp(value);
        switch (trigger) {
            case CHILL -> chill = clamped;
            case HEAT -> heat = clamped;
            case OVERWORK -> exertion = clamped;
            case SLEEP -> sleepDebt = clamped;
            case INJURY -> injury = clamped;
            case HUNGER -> hunger = clamped;
            case STRESS -> stress = clamped;
            case ALLERGEN -> allergen = clamped;
            default -> {
            }
        }
    }

    public long lastWarnTick() {
        return lastWarnTick;
    }

    public void markWarned(long now) {
        lastWarnTick = now;
    }

    // ------------------------------------------------------------------ 存档

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putFloat("chill", chill);
        tag.putFloat("heat", heat);
        tag.putFloat("exertion", exertion);
        tag.putFloat("sleep", sleepDebt);
        tag.putFloat("injury", injury);
        tag.putFloat("hunger", hunger);
        tag.putFloat("stress", stress);
        tag.putFloat("allergen", allergen);
        tag.putLong("last_sample", lastSampleTick);
        tag.putLong("last_warn", lastWarnTick);
        return tag;
    }

    public static MaidBehavior load(CompoundTag tag) {
        MaidBehavior behavior = new MaidBehavior();
        behavior.chill = clamp(tag.getFloat("chill"));
        behavior.heat = clamp(tag.getFloat("heat"));
        behavior.exertion = clamp(tag.getFloat("exertion"));
        behavior.sleepDebt = clamp(tag.getFloat("sleep"));
        behavior.injury = clamp(tag.getFloat("injury"));
        behavior.hunger = clamp(tag.getFloat("hunger"));
        behavior.stress = clamp(tag.getFloat("stress"));
        behavior.allergen = clamp(tag.getFloat("allergen"));
        behavior.lastSampleTick = tag.getLong("last_sample");
        behavior.lastWarnTick = tag.getLong("last_warn");
        return behavior;
    }
}
