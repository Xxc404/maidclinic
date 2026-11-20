package com.maidclinic.compat;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.maidclinic.registry.ModEffects;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.effect.MobEffectInstance;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;

/**
 * 生病女仆的“表情”软兼容层。
 * <p>
 * 睡眠表情由服务端决定：服务端把选中的表情序号写进 {@link ModEffects#SICK_FACE}
 * 的 amplifier 并同步给客户端，客户端只负责播放；因此联机/专用服务器上所有玩家
 * 看到的是同一张脸，而不是只有本地玩家能看到。
 * </p>
 * <p>《更多动画》《女仆可爱动作》仍是软依赖：缺任意一个都会降级，不会报缺失。</p>
 */
public final class SickExpressions {
    private static final String CUTE_HANDLER = "cn.autoforged.maid_cute_activity.MoreExpressionHandler";
    private static final String ANIMATION_DATA =
            "com.github.JumDa5he.moreanimation.compat.animation.MaidAnimationData";

    /** 服务端持久表情（会同步给附近玩家）。 */
    private static final String SERVER_FACE_KEY = "moreanimation_expression";
    /** 客户端渲染表情（绕过睡觉时的 one-shot 拦截）。 */
    private static final String CLIENT_FACE_KEY = "moreanimation_expression_render";
    /** 《更多动画》的随机表情键：睡眠期间必须清掉，否则会点播到 chijing。 */
    private static final String RANDOM_FACE_KEY = "moreanimation_random_expression";
    private static final String RANDOM_FACE_UNTIL_KEY = "moreanimation_random_expression_until";

    /**
     * 睡觉时播放的表情：只用难过。
     * <p>实测 nanshou（难受）在睡眠动画上会整张脸变成"吃惊相"，是问题来源，已移除；
     * chijing 同样会瞪眼，也已移除；hurt/afraid 不在《更多动画》的 EXPRESSIONS 名单里，
     * 调了会被它静默丢弃，睡眠时无法显示。</p>
     */
    private static final List<String> SICK_FACES = List.of("sosad");
    /** 《更多动画》客户端接口能直接播放的名字。 */
    private static final Set<String> DIRECT_FACES = Set.of("sosad");
    /** 历史版本可能写进存档/客户端渲染键的名字，清理时必须一并视为“本模组的残留”。 */
    private static final Set<String> LEGACY_FACES = Set.of("nanshou", "sosad", "chijing", "hurt", "afraid");
    /** 已经不再使用的旧表情名：周期性清理只针对这几个，避免误清玩家自己选的表情。 */
    private static final Set<String> REMOVED_FACES = Set.of("chijing", "hurt", "afraid");
    private static final long FACE_SWITCH_TICKS = 100L;
    private static final int FACE_DURATION_TICKS = 20 * 8;
    /** 客户端帧的持续 tick：渲染前每帧重设，只求"当前有效"，不需要长。 */
    private static final int FACE_REFRESH_TICKS = 40;

    private static boolean resolved;
    private static boolean moreAnimation;
    private static Method cuteTrigger;
    private static Method cuteCancel;
    private static Method syncExpression;
    private static Method setExpressionLocal;
    /** 清掉客户端本地的表情帧/动作状态，防止第三方动画压在我们的表情上。 */
    private static Method clearLocal;
    /** NeoForge 给 Entity 打的补丁方法，离线编译用的原版 jar 里没有，只能反射。 */
    private static Method persistentData;

    private SickExpressions() {
    }

    /** 醒着时：只调用《女仆可爱动作》的难过/无语表情。 */
    public static void show(EntityMaid maid) {
        resolve();
        invoke(cuteTrigger, maid);
    }

    /**
     * 服务端每 40 tick 调用一次：决定当前睡觉表情并同步给客户端。
     * <p>这里<b>不能</b>再调《女仆可爱动作》的 {@code forceTrigger}，并且要顺手清掉
     * 《更多动画》的随机表情键：随机池里有 chijing（吃惊），睡眠中点播到就是整脸瞪眼，
     * 表现为 nanshou / sosad / chijing 三张脸轮播。睡眠期间表情只由本类接管。</p>
     */
    public static void showSleeping(EntityMaid maid) {
        resolve();
        long gameTime = maid.level().getGameTime();
        String face = faceFor(gameTime);
        if (moreAnimation) {
            CompoundTag data = dataOf(maid);
            boolean changed = !face.equals(data.getString(SERVER_FACE_KEY));
            changed |= data.contains(RANDOM_FACE_KEY) || data.contains(RANDOM_FACE_UNTIL_KEY);
            data.putString(SERVER_FACE_KEY, face);
            data.remove(RANDOM_FACE_KEY);
            data.remove(RANDOM_FACE_UNTIL_KEY);
            if (changed) {
                invoke(syncExpression, maid);
            }
            syncFaceEffect(maid, face);
        }
    }

    /**
     * 服务端每 tick 调用：睡觉生病期间独占表情。
     * <p>《更多动画》的客户端的表情动画是随同步包重建的，光在客户端改帧/改 NBT
     * 可能改不动已经播起来的动画；这里每 tick 把服务端表情改回本模组的值，
     * 任何第三方写进来的 chijing 最多显示 1 tick 就会被同步覆盖。</p>
     * <p>{@code syncExpression} 内部按（表情, 起始时间）去重，值没变时不会重复发包。</p>
     */
    public static void lockSleeping(EntityMaid maid) {
        resolve();
        if (!moreAnimation || !maid.isSleeping() || maid.getEffect(ModEffects.UNWELL) == null) {
            return;
        }
        String face = faceFor(maid.level().getGameTime());
        CompoundTag data = dataOf(maid);
        data.putString(SERVER_FACE_KEY, face);
        data.remove(RANDOM_FACE_KEY);
        data.remove(RANDOM_FACE_UNTIL_KEY);
        invoke(syncExpression, maid);
        syncFaceEffect(maid, face);
    }

    /**
     * 客户端每 tick（以及渲染前）调用：把睡眠表情锁成 {@link #SICK_FACES} 之一。
     * <p>除了写 NBT 键和表情帧，还会主动清掉《更多动画》的随机表情键、本地动画状态，
     * 避免它的随机池点播到 chijing 后整脸盖上来。</p>
     */
    public static void clientSleepFace(EntityMaid maid, long gameTime) {
        resolve();
        CompoundTag data = dataOf(maid);
        MobEffectInstance marker = maid.getEffect(ModEffects.SICK_FACE);
        // 只要在睡觉且身上还有“身体不适”，就绝不把渲染键留空：
        // 之前这里会清掉自己的键，结果把《更多动画》旧存档里的 chijing 露了出来。
        if (!moreAnimation || !maid.isSleeping() || maid.getEffect(ModEffects.UNWELL) == null) {
            if (LEGACY_FACES.contains(data.getString(CLIENT_FACE_KEY))) {
                data.remove(CLIENT_FACE_KEY);
            }
            return;
        }
        int index = marker == null ? 0
                : Math.min(Math.max(marker.getAmplifier(), 0), SICK_FACES.size() - 1);
        String face = SICK_FACES.get(index);
        // 先清掉《更多动画》自己的本地状态（表情帧、动作、随机表情），再写我们的，
        // 保证渲染时最后生效的是本模组选的脸。
        invoke(clearLocal, maid);
        data.remove(RANDOM_FACE_KEY);
        data.remove(RANDOM_FACE_UNTIL_KEY);
        data.putString(SERVER_FACE_KEY, face);
        data.putString(CLIENT_FACE_KEY, face);
        if (DIRECT_FACES.contains(face)) {
            playLocal(maid, face, gameTime);
        }
    }

    /** 痊愈后清掉本模组设置的表情，避免病好了还一直哭丧着脸。 */
    public static void clear(EntityMaid maid) {
        resolve();
        CompoundTag data = dataOf(maid);
        boolean changed = false;
        if (LEGACY_FACES.contains(data.getString(SERVER_FACE_KEY))) {
            data.remove(SERVER_FACE_KEY);
            changed = true;
        }
        if (LEGACY_FACES.contains(data.getString(CLIENT_FACE_KEY))) {
            data.remove(CLIENT_FACE_KEY);
        }
        if (changed) {
            invoke(syncExpression, maid);
        }
        maid.removeEffect(ModEffects.SICK_FACE);
        invoke(cuteCancel, maid);
    }

    /**
     * 周期性清理历史版本/旧存档留下的表情键（包括已从列表移除的 chijing/hurt/afraid），
     * 否则旧值会一直被《更多动画》当作当前表情播放。
     */
    public static void clearStale(EntityMaid maid) {
        resolve();
        CompoundTag data = dataOf(maid);
        boolean changed = false;
        if (REMOVED_FACES.contains(data.getString(SERVER_FACE_KEY))) {
            data.remove(SERVER_FACE_KEY);
            changed = true;
        }
        if (REMOVED_FACES.contains(data.getString(CLIENT_FACE_KEY))) {
            data.remove(CLIENT_FACE_KEY);
        }
        if (changed) {
            invoke(syncExpression, maid);
        }
    }

    private static String faceFor(long gameTime) {
        int index = (int) Math.floorMod(gameTime / FACE_SWITCH_TICKS, SICK_FACES.size());
        return SICK_FACES.get(index);
    }

    /** 服务端把表情序号挂成标记效果；序号没变且时间还够就不重复发包。 */
    private static void syncFaceEffect(EntityMaid maid, String face) {
        int index = SICK_FACES.indexOf(face);
        if (index < 0) {
            return;
        }
        MobEffectInstance current = maid.getEffect(ModEffects.SICK_FACE);
        if (current != null && current.getAmplifier() == index && current.getDuration() > 100) {
            return;
        }
        maid.addEffect(new MobEffectInstance(ModEffects.SICK_FACE, FACE_DURATION_TICKS, index,
                false, false, false));
    }

    /**
     * 调用《更多动画》的客户端表情接口。
     * <p>第 4 参是帧的<b>绝对起始</b> gameTime，必须传当前时间；传未来时间会让帧一直
     * “尚未开始”而不生效。第 5 参是持续 tick，渲染前每帧都会重设，所以给短一点即可。
     * 客户端渲染只认活动帧、其次才是 NBT 键，这是唯一能压过事件帧的通道。</p>
     */
    private static void playLocal(EntityMaid maid, String face, long gameTime) {
        if (setExpressionLocal == null) {
            return;
        }
        try {
            setExpressionLocal.invoke(null, maid, face, "maidclinic", gameTime, FACE_REFRESH_TICKS);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
            // 版本不匹配时静默降级
        }
    }

    private static void resolve() {
        if (resolved) {
            return;
        }
        resolved = true;
        try {
            Class<?> handler = Class.forName(CUTE_HANDLER);
            cuteTrigger = handler.getMethod("forceTrigger", EntityMaid.class);
            cuteCancel = handler.getMethod("cancelMoreExpression", EntityMaid.class);
        } catch (Throwable ignored) {
            cuteTrigger = null;
            cuteCancel = null;
        }
        try {
            persistentData = Class.forName("net.minecraft.world.entity.Entity")
                    .getMethod("getPersistentData");
        } catch (Throwable ignored) {
            persistentData = null;
        }
        try {
            Class<?> data = Class.forName(ANIMATION_DATA);
            syncExpression = data.getMethod("syncExpression", EntityMaid.class);
            setExpressionLocal = data.getMethod("setExpressionLocal", EntityMaid.class,
                    String.class, String.class, long.class, int.class);
            try {
                clearLocal = data.getMethod("clearLocal", EntityMaid.class);
            } catch (ReflectiveOperationException | LinkageError ignored) {
                // 旧版本可能没有这个入口，缺失时只靠 NBT 键兜底
                clearLocal = null;
            }
            moreAnimation = true;
        } catch (Throwable ignored) {
            syncExpression = null;
            setExpressionLocal = null;
            clearLocal = null;
            moreAnimation = false;
        }
    }

    private static CompoundTag dataOf(EntityMaid maid) {
        if (persistentData == null) {
            return new CompoundTag();
        }
        try {
            return (CompoundTag) persistentData.invoke(maid);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
            return new CompoundTag();
        }
    }

    private static void invoke(Method method, EntityMaid maid) {
        if (method == null) {
            return;
        }
        try {
            method.invoke(null, maid);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
            // 前置模组版本不匹配时静默降级
        }
    }
}
