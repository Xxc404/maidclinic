package com.maidclinic.health;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.maidclinic.ClinicConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * 灵体归因：把“这次死亡该不该留下灵体”从“伤害来源是不是玩家”升级为
 * “归因链 + 证据 + 策略”。
 * <p>
 * 归因链（置信度从高到低）：
 * <ol>
 *     <li>直接玩家（近战、弹射物、玩家宠物/可驯服生物）；</li>
 *     <li>爆炸：{@link ExplosionEvent.Detonate} 缓存 + 被点燃 TNT 的方块记录；</li>
 *     <li>危险方块：玩家用桶倒的岩浆、用打火石/火焰弹点的火、玩家放置的营火/仙人掌等；</li>
 *     <li>推挤证据：玩家（或其船/矿车等载具）用碰撞箱把后代推下去——要求速度突变、
 *         玩家朝向女仆、推挤方向前方就是落差/岩浆/水/虚空；同向推靠“移动意图未变”
 *         识别，并额外要求陆地、未受伤、玩家与女仆之间没有其他实体遮挡；</li>
 *     <li>接触证据：死亡前 {@code contactWindowTicks} 内被该玩家或其宠物打过的环境死亡
 *         （爆炸、火焰、摔落、窒息、溺水等）；</li>
 *     <li>在场证据：{@link Mode#STRICT} 下，纯环境死亡且现场只有一名玩家；</li>
 *     <li>主人过失：{@link ClinicConfig#SPIRIT_BLOCK_ON_OWNER_NEGLIGENCE} 开启时，
 *         主人就在 16 格内的环境死亡。</li>
 * </ol>
 * <p>所有记录都是事件驱动 + 短时间窗缓存，不做每 tick 扫描；只在服务器主线程读写。</p>
 */
public final class SpiritAttribution {
    /** 归因档位：越高越容易把“玩家造成的环境死亡”算成恶意。 */
    public enum Mode {
        /** 只认直接玩家伤害（旧行为）。 */
        OFF,
        /** 直接玩家 + 爆炸 + 玩家放置/引燃的危险方块。 */
        LENIENT,
        /** 在 LENIENT 之上，加上“死前 5 秒内被该玩家打过”的接触证据（默认）。 */
        BALANCED,
        /** 在 BALANCED 之上，加上“现场只有一名玩家”的在场证据。 */
        STRICT
    }

    /** 归因层级，也用于调试输出。 */
    public enum Tier {
        NONE,
        DIRECT,
        OWNED_ENTITY,
        EXPLOSION,
        HAZARD_BLOCK,
        PUSH,
        CONTACT,
        NEARBY,
        OWNER_NEGLIGENCE
    }

    /** 推挤方向前方的危险类型；死亡时必须与伤害类型匹配。 */
    private enum HazardKind {
        NONE,
        LAVA,
        WATER,
        FALL,
        VOID
    }

    /** 归因结果：{@code blocked()} 为真表示这次死亡不应生成灵体。 */
    public record Result(Player player, Tier tier, String reason) {
        public static final Result NONE = new Result(null, Tier.NONE, "natural");

        public boolean blocked() {
            return tier != Tier.NONE;
        }
    }

    private record Contact(UUID playerId, long gameTime, ResourceKey<Level> dimension) {
    }

    /**
     * 一次“被玩家推下去”的证据：冲量造成的轨迹偏转 + 推挤方向前方的危险。
     */
    private record PushEvidence(UUID playerId, long gameTime, ResourceKey<Level> dimension,
                                Vec3 direction, Vec3 origin, double deviationDegrees,
                                HazardKind hazard) {
    }

    /**
     * 女仆上一 tick 的移动状态快照。
     * <p>TLM 的 {@code MaidMoveControl} 只写 {@code speed/zza/xxa/yRot} 这些“意图”，
     * 水平速度完全由 {@code moveRelative(getSpeed(), input)} 产生；所以意图不变而速度突变，
     * 只可能是外部推挤（同向推也能靠这条识别）。</p>
     */
    private record MaidMotion(Vec3 delta, float speed, float zza, float xxa, float yRot) {
    }

    private record Placement(UUID playerId, long gameTime) {
    }

    private record ExplosionSite(ResourceKey<Level> dimension, Vec3 center, double radius,
                                 UUID playerId, long gameTime) {
    }

    private static final double NEARBY_RADIUS = 12.0D;
    private static final double OWNER_RADIUS = 16.0D;
    private static final double EXPLOSION_REACH = 8.0D;
    private static final int MAX_PLACEMENTS = 8192;
    private static final int MAX_EXPLOSIONS = 64;

    /** 推挤检测：玩家/载具与女仆的水平距离上限。 */
    private static final double PUSH_REACH = 2.5D;
    /** 推挤检测：水平速度突变阈值（原版 push 会给出约 0.05 格/tick 的瞬时增量）。 */
    private static final double PUSH_IMPULSE_MIN = 0.02D;
    /** 推挤检测：玩家/载具朝向与“指向女仆”方向的点积下限（±60°）。 */
    private static final double PUSH_FACING_DOT = 0.5D;
    /** 推挤检测：推挤后速度必须背离玩家。 */
    private static final double PUSH_AWAY_DOT = 0.5D;
    /** 推挤检测：玩家/载具水平位移下限，站桩不算。 */
    private static final double PUSH_MIN_MOTION = 1.0E-4D;
    /** 推挤方向前方要扫描的危险格数。 */
    private static final int PUSH_HAZARD_STEPS = 3;
    /** 落差达到多少格算“可摔死”。 */
    private static final int PUSH_FALL_DROP = 3;

    /** 受害者 UUID → 最近一次玩家来源伤害，用于“接触证据”。 */
    private static final Map<UUID, Contact> CONTACTS = new HashMap<>();
    /** 受害者 UUID → 最近一次被玩家（含载具）推下去的现场证据。 */
    private static final Map<UUID, PushEvidence> PUSH_EVIDENCE = new HashMap<>();
    /** 女仆 UUID → 上一 tick 的水平速度与移动意图，用于区分“外部冲量”和“AI 自己起步/转向”。 */
    private static final Map<UUID, MaidMotion> LAST_MOTION = new WeakHashMap<>();
    /** 近期爆炸点，用于把爆炸伤害归因到引爆者。 */
    private static final List<ExplosionSite> EXPLOSIONS = new ArrayList<>();
    /** 玩家放置/引燃的危险方块（GlobalPos 含维度）。 */
    private static final Map<GlobalPos, Placement> PLACEMENTS = new HashMap<>();

    private SpiritAttribution() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, SpiritAttribution::onIncomingDamage);
        NeoForge.EVENT_BUS.addListener(SpiritAttribution::onExplosion);
        NeoForge.EVENT_BUS.addListener(SpiritAttribution::onBlockPlaced);
        NeoForge.EVENT_BUS.addListener(SpiritAttribution::onUseOnBlock);
        NeoForge.EVENT_BUS.addListener(SpiritAttribution::onUseItem);
        NeoForge.EVENT_BUS.addListener(SpiritAttribution::onMaidTick);
        NeoForge.EVENT_BUS.addListener(SpiritAttribution::onLevelTick);
    }

    /** 当前归因档位；配置尚未加载时回落到默认值，供命令/调试显示使用。 */
    public static Mode currentMode() {
        return mode();
    }

    /**
     * 归因入口：给出这次死亡最可能的责任玩家与层级。
     * <p>返回 {@link Result#NONE} 表示按当前档位没有玩家归因，应保留灵体。</p>
     */
    public static Result attribute(LivingEntity victim, DamageSource source) {
        if (victim == null || source == null || victim.level().isClientSide()) {
            return Result.NONE;
        }
        if (!(victim.level() instanceof ServerLevel level)) {
            return Result.NONE;
        }

        Player direct = attackerOf(source);
        if (direct != null) {
            return new Result(direct, Tier.DIRECT, "direct-player");
        }

        if (source.is(DamageTypeTags.IS_EXPLOSION)) {
            Result explosion = explosion(level, victim);
            if (explosion.blocked()) {
                return explosion;
            }
        }

        if (isHazardDamage(source)) {
            Result hazard = placedHazard(level, victim);
            if (hazard.blocked()) {
                return hazard;
            }
        }

        if (mode().compareTo(Mode.BALANCED) >= 0
                && (source.getEntity() == null
                || source.is(DamageTypeTags.IS_FALL)
                || isHazardDamage(source))) {
            Result pushed = pushed(level, victim, source);
            if (pushed.blocked()) {
                return pushed;
            }
            Result contact = recentContact(level, victim);
            if (contact.blocked()) {
                return contact;
            }
        }

        if (mode() == Mode.STRICT && source.getEntity() == null) {
            Player nearby = solePlayerNear(level, victim.position(), NEARBY_RADIUS);
            if (nearby != null) {
                return new Result(nearby, Tier.NEARBY, "nearby-only");
            }
        }

        Result negligence = ownerNegligence(level, victim, source);
        if (negligence.blocked()) {
            return negligence;
        }
        return Result.NONE;
    }

    /** 周期清理过期缓存；由服务端 tick 调用，避免长期存档内存增长。 */
    public static void prune(long gameTime) {
        long contactWindow = contactWindowTicks();
        CONTACTS.values().removeIf(contact -> gameTime - contact.gameTime() > contactWindow);
        long pushWindow = pushWindowTicks();
        PUSH_EVIDENCE.values().removeIf(evidence -> gameTime - evidence.gameTime() > pushWindow);
        long explosionWindow = explosionWindowTicks();
        EXPLOSIONS.removeIf(site -> gameTime - site.gameTime() > explosionWindow);
        long placementTtl = placementTtlTicks();
        PLACEMENTS.values().removeIf(placement -> gameTime - placement.gameTime() > placementTtl);
    }

    // ------------------------------------------------------------------
    // 归因层级实现
    // ------------------------------------------------------------------

    private static Result explosion(ServerLevel level, LivingEntity victim) {
        Vec3 at = victim.position();
        long now = level.getGameTime();
        long window = explosionWindowTicks();
        ExplosionSite best = null;
        for (ExplosionSite site : EXPLOSIONS) {
            if (!site.dimension().equals(level.dimension())) {
                continue;
            }
            if (now - site.gameTime() > window) {
                continue;
            }
            double reach = Math.max(EXPLOSION_REACH, site.radius() + 4.0D);
            if (site.center().distanceToSqr(at) > reach * reach) {
                continue;
            }
            if (best == null || site.gameTime() >= best.gameTime()) {
                best = site;
            }
        }
        if (best != null && best.playerId() != null) {
            return new Result(player(level, best.playerId()), Tier.EXPLOSION, "explosion@cached");
        }

        // 兜底：爆炸伤害可能在同一 tick 早于 Detonate 事件，直接查附近已点燃的 TNT。
        UUID tntOwner = tntOwnerNear(level, at);
        if (tntOwner != null) {
            return new Result(player(level, tntOwner), Tier.EXPLOSION, "explosion@primed-tnt");
        }

        if (mode() == Mode.STRICT) {
            Player nearby = solePlayerNear(level, at, NEARBY_RADIUS);
            if (nearby != null) {
                return new Result(nearby, Tier.NEARBY, "explosion-nearby");
            }
        }
        return Result.NONE;
    }

    private static UUID tntOwnerNear(ServerLevel level, Vec3 at) {
        AABB area = new AABB(at, at).inflate(EXPLOSION_REACH);
        UUID owner = null;
        for (PrimedTnt tnt : level.getEntitiesOfClass(PrimedTnt.class, area)) {
            Placement placement = PLACEMENTS.get(GlobalPos.of(level.dimension(), tnt.blockPosition()));
            if (placement == null) {
                continue;
            }
            if (level.getGameTime() - placement.gameTime() > placementTtlTicks()) {
                continue;
            }
            owner = placement.playerId();
        }
        return owner;
    }

    private static Result placedHazard(ServerLevel level, LivingEntity victim) {
        BlockPos origin = victim.blockPosition();
        long now = level.getGameTime();
        long ttl = placementTtlTicks();
        Placement best = null;
        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-1, -1, -1), origin.offset(1, 1, 1))) {
            if (!isHazardBlock(level.getBlockState(pos))) {
                continue;
            }
            Placement placement = PLACEMENTS.get(GlobalPos.of(level.dimension(), pos.immutable()));
            if (placement == null || now - placement.gameTime() > ttl) {
                continue;
            }
            if (best == null || placement.gameTime() >= best.gameTime()) {
                best = placement;
            }
        }
        if (best == null) {
            return Result.NONE;
        }
        return new Result(player(level, best.playerId()), Tier.HAZARD_BLOCK, "placed-hazard");
    }

    private static Result recentContact(ServerLevel level, LivingEntity victim) {
        Contact contact = CONTACTS.get(victim.getUUID());
        if (contact == null || !contact.dimension().equals(level.dimension())) {
            return Result.NONE;
        }
        if (level.getGameTime() - contact.gameTime() > contactWindowTicks()) {
            return Result.NONE;
        }
        Player player = player(level, contact.playerId());
        if (player == null) {
            return Result.NONE;
        }
        return new Result(player, Tier.CONTACT, "recent-contact");
    }

    /**
     * 推挤归因：必须同时满足“这次死亡发生在推挤方向上、落点匹配、危险类型匹配”。
     * <p>只看“最近被推过”不够：玩家穿过人群后女仆在别处失足，不应算玩家的责任。</p>
     */
    private static Result pushed(ServerLevel level, LivingEntity victim, DamageSource source) {
        PushEvidence evidence = PUSH_EVIDENCE.get(victim.getUUID());
        if (evidence == null || !evidence.dimension().equals(level.dimension())) {
            return Result.NONE;
        }
        if (level.getGameTime() - evidence.gameTime() > pushWindowTicks()) {
            return Result.NONE;
        }
        HazardKind expected = hazardKindOf(source);
        if (expected == HazardKind.NONE) {
            return Result.NONE;
        }
        boolean matches = expected == evidence.hazard()
                // 被推下悬崖后落水/坠入虚空，也属于同一次“摔下去”。
                || (evidence.hazard() == HazardKind.FALL
                && (expected == HazardKind.WATER || expected == HazardKind.VOID));
        if (!matches) {
            return Result.NONE;
        }
        Vec3 move = horizontal(victim.position().subtract(evidence.origin()));
        double maxDistance = evidence.hazard() == HazardKind.FALL ? 4.0D : 3.0D;
        if (move.lengthSqr() > maxDistance * maxDistance) {
            return Result.NONE;
        }
        if (evidence.hazard() == HazardKind.FALL && victim.getY() > evidence.origin().y - 2.0D) {
            return Result.NONE;
        }
        if (move.length() > 0.5D && move.normalize().dot(evidence.direction()) < -0.1D) {
            return Result.NONE;
        }
        return new Result(player(level, evidence.playerId()), Tier.PUSH, "pushed-" + evidence.hazard());
    }

    /** 死亡伤害对应的危险类型；只有这些类型才允许用推挤证据归因。 */
    private static HazardKind hazardKindOf(DamageSource source) {
        if (source.is(DamageTypeTags.IS_FALL)) {
            return HazardKind.FALL;
        }
        if (source.is(DamageTypeTags.IS_FIRE)) {
            return HazardKind.LAVA;
        }
        if (source.is(DamageTypeTags.IS_DROWNING)) {
            return HazardKind.WATER;
        }
        if ("outOfWorld".equals(source.getMsgId())) {
            return HazardKind.VOID;
        }
        return HazardKind.NONE;
    }

    private static Result ownerNegligence(ServerLevel level, LivingEntity victim, DamageSource source) {
        if (mode() == Mode.OFF || !ownerNegligenceEnabled()) {
            return Result.NONE;
        }
        // 只算“环境死亡”：怪物主动击杀（getEntity 非空）不算主人过失，避免把战斗死亡算成玩家责任。
        if (source.getEntity() != null) {
            return Result.NONE;
        }
        if (!(victim instanceof EntityMaid maid)) {
            return Result.NONE;
        }
        if (!(maid.getOwner() instanceof Player owner)) {
            return Result.NONE;
        }
        if (owner.level() != level || owner.distanceToSqr(victim) > OWNER_RADIUS * OWNER_RADIUS) {
            return Result.NONE;
        }
        return new Result(owner, Tier.OWNER_NEGLIGENCE, "owner-nearby");
    }

    // ------------------------------------------------------------------
    // 事件采集
    // ------------------------------------------------------------------

    /** 记录“这个后代最近被玩家来源打过”，供摔落/岩浆/窒息等追问归因。 */
    private static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (event.isCanceled() || mode() == Mode.OFF) {
            return;
        }
        if (!(event.getEntity() instanceof EntityMaid maid)) {
            return;
        }
        if (!(maid.level() instanceof ServerLevel level)) {
            return;
        }
        Player player = attackerOf(event.getSource());
        if (player == null) {
            return;
        }
        CONTACTS.put(maid.getUUID(), new Contact(player.getUUID(), level.getGameTime(), level.dimension()));
    }

    /**
     * 推挤检测：碰撞箱推挤不产生伤害事件，只能靠“水平速度突变”识别。
     * <p>两条并列判据：</p>
     * <ul>
     *     <li>推挤改变了行进方向（偏转 ≥ 阈值）；</li>
     *     <li>同向推：偏转不够，但移动意图（speed/zza/xxa/yRot）完全没变，
     *         说明速度突变来自外部——要求陆地、未受伤、玩家与女仆间无遮挡。</li>
     * </ul>
     * <p>两条都还要满足：速度背离玩家、玩家/载具朝向女仆且在移动、
     * 推挤方向前方就是落差/岩浆/水/虚空。</p>
     */
    private static void onMaidTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof EntityMaid maid)) {
            return;
        }
        if (maid.level().isClientSide() || mode() == Mode.OFF) {
            return;
        }
        if (!(maid.level() instanceof ServerLevel level) || maid.isPassenger()) {
            return;
        }
        Vec3 delta = horizontal(maid.getDeltaMovement());
        MaidMotion last = LAST_MOTION.put(maid.getUUID(), new MaidMotion(delta,
                maid.getSpeed(), maid.zza, maid.xxa, maid.getYRot()));
        if (last == null) {
            return;
        }
        if (delta.distanceTo(last.delta()) < PUSH_IMPULSE_MIN) {
            return;
        }
        if (delta.lengthSqr() < 1.0E-6D) {
            return;
        }
        Player pusher = findPusher(level, maid);
        if (pusher == null) {
            return;
        }
        Vec3 toMaid = horizontal(maid.position().subtract(pusher.position()));
        if (toMaid.lengthSqr() < 1.0E-6D) {
            return;
        }
        toMaid = toMaid.normalize();
        if (delta.normalize().dot(toMaid) < PUSH_AWAY_DOT) {
            return;
        }
        double deviation = deviationDegrees(last.delta(), delta);
        if (deviation < pushDeviationDegrees()
                && !externalImpulse(level, pusher, maid, last)) {
            // 偏转不够，而且这次速度变化可以由女仆自己的移动意图解释：不是玩家推的。
            return;
        }
        HazardKind hazard = hazardAhead(level, maid.position(), toMaid);
        if (hazard == HazardKind.NONE) {
            return;
        }
        PUSH_EVIDENCE.put(maid.getUUID(), new PushEvidence(pusher.getUUID(), level.getGameTime(),
                level.dimension(), toMaid, maid.position(), deviation, hazard));
    }

    /**
     * 同向推的判据：女仆的移动意图完全没变，但速度突变了。
     * <p>TLM 的移动意图只由 {@code MaidMoveControl} 写入；意图不变时
     * 水平速度只可能来自外部推挤。另外要求陆地、未受伤（排除击退）、
     * 玩家与女仆之间没有其他实体遮挡（排除“人群里被别人挤的”）。</p>
     */
    private static boolean externalImpulse(ServerLevel level, Player pusher, EntityMaid maid,
                                           MaidMotion last) {
        if (maid.isInWater() || maid.hurtTime > 0) {
            return false;
        }
        if (!nearlyEqual(maid.getSpeed(), last.speed())
                || !nearlyEqual(maid.zza, last.zza())
                || !nearlyEqual(maid.xxa, last.xxa())
                || !nearlyEqual(maid.getYRot(), last.yRot())) {
            return false;
        }
        return !blockedBetween(level, pusher, maid);
    }

    /** 玩家（或载具）与女仆之间是否有其他实体挡住。 */
    private static boolean blockedBetween(ServerLevel level, Player pusher, EntityMaid maid) {
        Entity between = pusher.getRootVehicle();
        Vec3 from = pusher.getEyePosition();
        Vec3 to = maid.getBoundingBox().getCenter();
        AABB area = new AABB(from, to).inflate(0.5D);
        for (Entity entity : level.getEntities(pusher, area, candidate -> candidate != pusher
                && candidate != between
                && candidate != maid
                && !candidate.isPassengerOfSameVehicle(maid)
                && candidate.isAlive()
                && !candidate.isSpectator()
                && !(candidate instanceof Projectile))) {
            if (entity.getBoundingBox().inflate(0.05D).clip(from, to).isPresent()) {
                return true;
            }
        }
        return false;
    }

    private static boolean nearlyEqual(float left, float right) {
        return Math.abs(left - right) < 1.0E-4F;
    }

    /**
     * 找“贴着并朝向女仆”的玩家：优先用载具的碰撞箱与朝向（船/矿车/马推挤），
     * 否则用玩家本人的。
     */
    private static Player findPusher(ServerLevel level, EntityMaid maid) {
        AABB maidBox = maid.getBoundingBox().inflate(0.5D);
        for (Player player : level.players()) {
            if (player.isSpectator() || !player.isAlive()) {
                continue;
            }
            Entity pusher = player.getRootVehicle();
            if (horizontal(pusher.position().subtract(maid.position())).lengthSqr()
                    > PUSH_REACH * PUSH_REACH) {
                continue;
            }
            if (!pusher.getBoundingBox().inflate(0.5D).intersects(maidBox)) {
                continue;
            }
            if (horizontal(pusher.getDeltaMovement()).lengthSqr() < PUSH_MIN_MOTION) {
                continue;
            }
            Vec3 toMaid = horizontal(maid.position().subtract(pusher.position()));
            if (toMaid.lengthSqr() < 1.0E-6D) {
                continue;
            }
            if (facingOf(pusher).dot(toMaid.normalize()) < PUSH_FACING_DOT) {
                continue;
            }
            return player;
        }
        return null;
    }

    private static Vec3 facingOf(Entity entity) {
        Vec3 look = entity instanceof Player player
                ? player.getLookAngle()
                : Vec3.directionFromRotation(0.0F, entity.getYRot());
        return horizontal(look).normalize();
    }

    private static double deviationDegrees(Vec3 from, Vec3 to) {
        double fromLength = from.length();
        double toLength = to.length();
        if (fromLength < 0.01D || toLength < 1.0E-4D) {
            return toLength < 1.0E-4D ? 0.0D : 90.0D;
        }
        double dot = from.normalize().dot(to.normalize());
        return Math.toDegrees(Math.acos(Math.max(-1.0D, Math.min(1.0D, dot))));
    }

    private static Vec3 horizontal(Vec3 vector) {
        return new Vec3(vector.x, 0.0D, vector.z);
    }

    /** 沿推挤方向看 1–3 格：先看岩浆/火，再看水，最后看落差。 */
    private static HazardKind hazardAhead(ServerLevel level, Vec3 origin, Vec3 direction) {
        BlockPos base = BlockPos.containing(origin);
        for (int step = 1; step <= PUSH_HAZARD_STEPS; step++) {
            int dx = (int) Math.round(direction.x * step);
            int dz = (int) Math.round(direction.z * step);
            BlockPos pos = base.offset(dx, 0, dz);
            for (int dy = -1; dy <= 1; dy++) {
                BlockState state = level.getBlockState(pos.offset(0, dy, 0));
                if (isPushHazardBlock(state)) {
                    return HazardKind.LAVA;
                }
                if (state.getBlock() == Blocks.WATER) {
                    return HazardKind.WATER;
                }
            }
            if (dropBelow(level, pos) >= PUSH_FALL_DROP) {
                return HazardKind.FALL;
            }
        }
        if (base.getY() <= level.getMinBuildHeight() + 2) {
            return HazardKind.VOID;
        }
        return HazardKind.NONE;
    }

    private static boolean isPushHazardBlock(BlockState state) {
        Block block = state.getBlock();
        return block == Blocks.LAVA
                || block == Blocks.FIRE
                || block == Blocks.SOUL_FIRE
                || block == Blocks.MAGMA_BLOCK
                || block == Blocks.CAMPFIRE
                || block == Blocks.SOUL_CAMPFIRE;
    }

    /** 该位置往下几格落到实心方块；顺带处理“下面就是虚空”。 */
    private static int dropBelow(ServerLevel level, BlockPos pos) {
        for (int drop = 1; drop <= PUSH_FALL_DROP + 2; drop++) {
            BlockPos below = pos.below(drop);
            if (below.getY() < level.getMinBuildHeight()) {
                return 99;
            }
            if (!level.getBlockState(below).getCollisionShape(level, below).isEmpty()) {
                return drop;
            }
        }
        return 0;
    }

    /** 缓存爆炸点与引爆者：爆炸产生的伤害来源通常指不到玩家。 */
    private static void onExplosion(ExplosionEvent.Detonate event) {
        if (event.getLevel().isClientSide() || mode() == Mode.OFF) {
            return;
        }
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        var explosion = event.getExplosion();
        Player player = attackerOfEntity(explosion.getIndirectSourceEntity());
        if (player == null) {
            player = attackerOfEntity(explosion.getDirectSourceEntity());
        }
        UUID owner = player == null ? null : player.getUUID();
        if (owner == null) {
            owner = placementNear(level, BlockPos.containing(explosion.center()), 2);
        }
        addExplosion(new ExplosionSite(level.dimension(), explosion.center(), explosion.radius(),
                owner, level.getGameTime()));
    }

    private static void addExplosion(ExplosionSite site) {
        EXPLOSIONS.add(site);
        if (EXPLOSIONS.size() > MAX_EXPLOSIONS) {
            EXPLOSIONS.remove(0);
        }
    }

    /** 记录玩家放置的危险方块（TNT、岩浆桶不算方块放置，见 {@link #onUseItem}）。 */
    private static void onBlockPlaced(BlockEvent.EntityPlaceEvent event) {
        if (mode() == Mode.OFF || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (!(event.getBlockSnapshot().getLevel() instanceof ServerLevel level)) {
            return;
        }
        if (!isHazardBlock(event.getPlacedBlock())) {
            return;
        }
        rememberPlacement(level, event.getBlockSnapshot().getPos(), player);
    }

    /** 打火石/火焰弹点火、TNT 点燃、岩浆桶倒岩浆：这些都不经过方块放置事件。 */
    private static void onUseOnBlock(PlayerInteractEvent.RightClickBlock event) {
        if (mode() == Mode.OFF || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        ItemStack stack = event.getItemStack();
        boolean igniter = stack.is(Items.FLINT_AND_STEEL) || stack.is(Items.FIRE_CHARGE);
        boolean bucket = stack.is(Items.LAVA_BUCKET) || stack.is(Items.POWDER_SNOW_BUCKET);
        if (!igniter && !bucket) {
            return;
        }
        BlockPos clicked = event.getPos();
        rememberPlacement(level, clicked, player);
        if (event.getFace() != null) {
            rememberPlacement(level, clicked.relative(event.getFace()), player);
        }
    }

    /** 对着空气倒岩浆：右键方块事件不触发，用视线射线兜底。 */
    private static void onUseItem(PlayerInteractEvent.RightClickItem event) {
        if (mode() == Mode.OFF || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        ItemStack stack = event.getItemStack();
        if (!(stack.is(Items.LAVA_BUCKET) || stack.is(Items.POWDER_SNOW_BUCKET))) {
            return;
        }
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        HitResult hit = player.pick(6.0D, 0.0F, true);
        if (!(hit instanceof BlockHitResult blockHit)) {
            return;
        }
        rememberPlacement(level, blockHit.getBlockPos(), player);
        rememberPlacement(level, blockHit.getBlockPos().relative(blockHit.getDirection()), player);
    }

    private static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        if (level.getGameTime() % 1200L != 0L) {
            return;
        }
        prune(level.getGameTime());
    }

    // ------------------------------------------------------------------
    // 工具方法
    // ------------------------------------------------------------------

    private static void rememberPlacement(ServerLevel level, BlockPos pos, ServerPlayer player) {
        if (pos == null || PLACEMENTS.size() >= MAX_PLACEMENTS) {
            // 记录已满：放弃新记录，等周期 prune 回收；避免每次放置都做全表扫描。
            return;
        }
        PLACEMENTS.put(GlobalPos.of(level.dimension(), pos.immutable()),
                new Placement(player.getUUID(), level.getGameTime()));
    }

    private static UUID placementNear(ServerLevel level, BlockPos origin, int radius) {
        long now = level.getGameTime();
        long ttl = placementTtlTicks();
        UUID owner = null;
        for (BlockPos pos : BlockPos.betweenClosed(
                origin.offset(-radius, -radius, -radius), origin.offset(radius, radius, radius))) {
            Placement placement = PLACEMENTS.get(GlobalPos.of(level.dimension(), pos.immutable()));
            if (placement == null || now - placement.gameTime() > ttl) {
                continue;
            }
            owner = placement.playerId();
        }
        return owner;
    }

    /** 玩家本人、玩家射出的弹射物、玩家驯服的生物都算玩家来源。 */
    private static Player attackerOf(DamageSource source) {
        if (source.getEntity() instanceof Player player) {
            return player;
        }
        if (source.getDirectEntity() instanceof Projectile projectile
                && projectile.getOwner() instanceof Player player) {
            return player;
        }
        Player owner = attackerOfEntity(source.getDirectEntity());
        return owner != null ? owner : attackerOfEntity(source.getEntity());
    }

    private static Player attackerOfEntity(Entity entity) {
        if (entity instanceof Player player) {
            return player;
        }
        if (entity instanceof OwnableEntity ownable && ownable.getOwner() instanceof Player player) {
            return player;
        }
        if (entity == null) {
            return null;
        }
        try {
            Method method = entity.getClass().getMethod("getOwner");
            return method.invoke(entity) instanceof Player player ? player : null;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            return null;
        }
    }

    /**
     * 按 UUID 找在线玩家。
     * <p>只用当前维度在线列表：能造成伤害的玩家必定和受害者同维度；
     * 归因只用 {@link Result#blocked()} 做判定，玩家已离线时只是拿不到提示对象。</p>
     */
    private static Player player(ServerLevel level, UUID id) {
        if (id == null) {
            return null;
        }
        for (Player player : level.players()) {
            if (player.getUUID().equals(id)) {
                return player;
            }
        }
        return null;
    }

    private static Player solePlayerNear(ServerLevel level, Vec3 at, double radius) {
        List<Player> players = level.getEntitiesOfClass(Player.class,
                new AABB(at, at).inflate(radius), player -> !player.isSpectator() && player.isAlive());
        return players.size() == 1 ? players.get(0) : null;
    }

    private static boolean isHazardDamage(DamageSource source) {
        if (source.is(DamageTypeTags.IS_FIRE)) {
            return true;
        }
        String id = source.getMsgId();
        return "cactus".equals(id) || "sweetBerryBush".equals(id) || "freeze".equals(id);
    }

    private static boolean isHazardBlock(BlockState state) {
        Block block = state.getBlock();
        return block == Blocks.LAVA
                || block == Blocks.FIRE
                || block == Blocks.SOUL_FIRE
                || block == Blocks.MAGMA_BLOCK
                || block == Blocks.CAMPFIRE
                || block == Blocks.SOUL_CAMPFIRE
                || block == Blocks.POWDER_SNOW
                || block == Blocks.CACTUS
                || block == Blocks.SWEET_BERRY_BUSH
                || block == Blocks.TNT
                || block == Blocks.RESPAWN_ANCHOR
                || block instanceof BedBlock;
    }

    private static Mode mode() {
        try {
            Mode mode = ClinicConfig.SPIRIT_ATTRIBUTION.get();
            return mode == null ? Mode.BALANCED : mode;
        } catch (RuntimeException ignored) {
            return Mode.BALANCED;
        }
    }

    private static boolean ownerNegligenceEnabled() {
        try {
            return ClinicConfig.SPIRIT_BLOCK_ON_OWNER_NEGLIGENCE.get();
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static int contactWindowTicks() {
        try {
            return ClinicConfig.SPIRIT_CONTACT_WINDOW_TICKS.get();
        } catch (RuntimeException ignored) {
            return 100;
        }
    }

    private static int explosionWindowTicks() {
        try {
            return ClinicConfig.SPIRIT_EXPLOSION_CACHE_TICKS.get();
        } catch (RuntimeException ignored) {
            return 5;
        }
    }

    private static int placementTtlTicks() {
        try {
            return ClinicConfig.SPIRIT_PLACED_BLOCK_TTL_TICKS.get();
        } catch (RuntimeException ignored) {
            return 6000;
        }
    }

    private static int pushWindowTicks() {
        try {
            return ClinicConfig.SPIRIT_PUSH_WINDOW_TICKS.get();
        } catch (RuntimeException ignored) {
            return 100;
        }
    }

    private static double pushDeviationDegrees() {
        try {
            return ClinicConfig.SPIRIT_PUSH_DEVIATION_DEGREES.get();
        } catch (RuntimeException ignored) {
            return 20.0D;
        }
    }

    /** 调试用：当前“伤害接触”证据的剩余时间。 */
    public static String describeContact(LivingEntity maid) {
        if (maid == null || !(maid.level() instanceof ServerLevel level)) {
            return "无";
        }
        Contact contact = CONTACTS.get(maid.getUUID());
        if (contact == null || !contact.dimension().equals(level.dimension())) {
            return "无";
        }
        long remaining = contactWindowTicks() - (level.getGameTime() - contact.gameTime());
        return remaining <= 0L ? "无" : "剩" + remaining + "t";
    }

    /** 调试用：当前“被推下去”的现场证据（危险类型、轨迹偏转、剩余时间）。 */
    public static String describePushEvidence(LivingEntity maid) {
        if (maid == null || !(maid.level() instanceof ServerLevel level)) {
            return "无";
        }
        PushEvidence evidence = PUSH_EVIDENCE.get(maid.getUUID());
        if (evidence == null || !evidence.dimension().equals(level.dimension())) {
            return "无";
        }
        long remaining = pushWindowTicks() - (level.getGameTime() - evidence.gameTime());
        if (remaining <= 0L) {
            return "无";
        }
        return evidence.hazard() + " 偏转" + Math.round(evidence.deviationDegrees())
                + "° 剩" + remaining + "t";
    }
}
