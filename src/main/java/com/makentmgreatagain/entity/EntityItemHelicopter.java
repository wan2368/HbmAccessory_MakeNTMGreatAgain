package com.makentmgreatagain.entity;

import java.util.List;

import com.makentmgreatagain.damage.DamageSourceHelicopter;
import com.makentmgreatagain.hook.HelicopterGuardRules;

import cpw.mods.fml.common.registry.IEntityAdditionalSpawnData;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.World;

/**
 * 耐摔合金（helicopter）系列物品的掉落物。
 *
 * <h2>四个阶段（数值以常量字段为准）</h2>
 * <ol>
 * <li><b>蓄势 55 tick</b>：原地、原版重力；出生即自旋，10 → 120 rpm（2 rpm/tick）。</li>
 * <li><b>上升 89 tick</b>：转速满 120（{@link #LIFT_START}）离地，匀加速走完 24 格，
 * 第一 tick 0.006 格、顶点 0.54 格/tick。</li>
 * <li><b>悬停 10 tick</b>：自旋平滑减速停在整圈上，正面朝镜头。</li>
 * <li><b>下坠 ~3 tick</b>：0.5 格/tick²，吃满 1.5 格/tick；落地炸碎屑 + 扬尘 +
 * {@code makentmgreatagain:man}，之后不再运动、不再渲染（{@link #isLanded()}）。</li>
 * </ol>
 *
 * <h2>为什么上升是「速度驱动 + 剩余距离钳制」</h2>
 * <p>如果直接给速度曲线让位置自由累积，会超调（浮点误差 + 碰撞修正），
 * 「必须停在 24 格」就保不住；如果只用「目标高度 − 当前位置」做闭环，
 * 闭环输出的位移会一路缩小、到顶才归零，等于「一上来最快、越后越慢」，
 * 和「缓慢加速」正好相反。所以两件事分开做：
 * <ul>
 * <li><b>速度</b>负责手感：{@code RISE_ACCEL·age}，从 0 开始一点点加起来；</li>
 * <li><b>钳制</b>负责精度：{@code offset = min(速度, 到 24 格还剩多少)}，
 * 最后一步正好踩在 24 格上，下一 tick 剩余为 0 就定住，不超调、不抖动。</li>
 * </ul>
 *
 * <h2>速度怎么写才不被原版吃掉</h2>
 * <p>原版 {@code EntityItem.onUpdate} 的顺序是「先 {@code motionY -= 0.04}，
 * 再 {@code moveEntity}」，所以本 tick 的净位移正好是 {@code 写入值 − 0.04}，
 * 反解就是 {@code motionY = d + 0.04}（见 {@link #GRAVITY}）。
 * 注意这个 {@code -= 0.04} <b>不在 onGround 判断里</b>，贴地时也照减，
 * 所以补偿不能按「只有空中才减」来写。</p>
 *
 * <p>至于 {@code moveEntity} 末尾那个 ×0.98，作用在<b>下一 tick</b> 的 motionY 上，
 * 而这里每 tick 都把 motionY 整值重写，所以完全不需要补偿
 * （早期写成 {@code d / 0.98 + 0.04}，每一步都会多走 2%，24 格会冲出半格）。</p>
 *
 * <h2>粒子为什么在客户端发</h2>
 * <p>{@code World.spawnParticle} 的实现是遍历 {@code worldAccesses} 逐个转发，而服务端
 * 注册的 {@code IWorldAccess} 是 {@code ServerWorldEventHandler}，它的
 * {@code spawnParticle} 是<b>空方法</b> —— 在服务端调用等于什么都没发生（这就是最初
 * 「没有粒子」的原因）。客户端侧的 {@code EffectRenderer} 才会真的生成粒子，
 * 而且它顺手解析 {@code iconcrack_<物品id>_<meta>} 这种字符串。
 * 所以特效走客户端，服务端只负责 {@link #setDead()}。</p>
 *
 * <p>为了不让服务端先把实体删掉、客户端来不及跑自己的落地判定，服务端会多活
 * {@link #SPLASH_DELAY} tick（从 {@link #landed} 开始计时）。这几 tick 里实体还在但
 * <b>不渲染</b>（{@link #isLanded()} + 渲染器早退），否则就会看到
 * 「已经砸碎的东西还贴在地上」那 0.25~0.5 秒的残留。</p>
 *
 * <h2>落地瞬杀（「牢大肘飞」）</h2>
 * <p>砸到地面那一 tick，服务端会把落点水平半径 {@link #SHOCK_RADIUS} 格内的玩家
 * <b>直接处决</b>：血量置 0 → {@code onDeath}，完全不走 {@code attackEntityFrom}，
 * 所以护甲、抗性提升、无敌帧都不参与，创造模式也照杀。死亡播报由
 * {@code damage/DamageSourceHelicopter} 提供（{@code death.attack.mnga.helicopter}）。
 * 特效仍然只在客户端 —— 两边各做各的那一半，见 {@link #crushNearbyPlayers()}。</p>
 *
 * <h2>运动学仍然是「无状态的」</h2>
 * <p>速度、悬停、下坠全部由 {@code ticksExisted} 推导，客户端能独立算出和服务端
 * 一样的轨迹，不需要同步包。存档里只存起点高度、「被顶住」的计时和总开关。</p>
 *
 * <h2>不会无限递归（重要）</h2>
 * <p>Forge 1.7.10 的替换入口判断的是 {@code entity.getClass().equals(EntityItem.class)}
 * —— <b>精确类匹配</b>，子类直接跳过。所以本类被 spawn 时不会再去问
 * {@code Item.hasCustomEntity}，不会出现「替换 → 再替换 → 栈溢出」。</p>
 *
 * <h2>怎么挂到物品上</h2>
 * <p>物品侧覆盖 {@code Item.hasCustomEntity} 返回 true、{@code Item.createEntity}
 * 调用 {@link #fromDropped(World, Entity, ItemStack)} 即可，见
 * {@code items/ItemHelicopterDrop}、{@code items/ItemToolAbilityHelicopter}。
 * 方块（{@code block_helicopter} 等金属储存块）用的是原版 {@code ItemBlock}，不在此列。</p>
 */
public class EntityItemHelicopter extends EntityItem implements IEntityAdditionalSpawnData {

    /* ---------------------------------------------- 自旋 / 起飞时刻 */

    /** 起始转速（rpm）。物品一出生就在转，不是从静止开始。 */
    public static final double SPIN_RPM_START = 10.0D;

    /** 目标转速（rpm）。<b>转速打到这里的那一 tick 就是起飞时刻。</b> */
    public static final double SPIN_RPM_MAX = 120.0D;

    /** 转速加速度（rpm/tick）。10 → 120 需要 55 tick（2.75 秒）。 */
    public static final double SPIN_RPM_ACCEL = 2.0D;

    /**
     * rpm 换算成「度/tick」的系数。
     *
     * <p>1 rpm = 每分钟 1 圈 = 每秒 1/60 圈 = 每 tick 1/1200 圈 = 0.3 度/tick。
     * 所以 {@link #SPIN_RPM_MAX}（120 rpm）等于 36 度/tick = 2 转/秒。</p>
     */
    public static final double DEG_PER_TICK_PER_RPM = 0.3D;

    /**
     * 起飞那一 tick：转速从 {@link #SPIN_RPM_START} 按 {@link #SPIN_RPM_ACCEL}
     * 涨到 {@link #SPIN_RPM_MAX} 的时刻，即 (120 − 10) / 2 = 55。
     *
     * <p>起飞时刻不是拍脑袋写的秒数，而是<b>由转速推出来的</b>：调转速参数时，
     * 起飞时刻会自动跟着走，不会出现「转还没起来就飞了」或者
     * 「转速满了还在地上干等」。想让它早点飞就加大 {@link #SPIN_RPM_ACCEL}。</p>
     */
    public static final double LIFT_START = (SPIN_RPM_MAX - SPIN_RPM_START) / SPIN_RPM_ACCEL;

    /** 升到相对落点多少格就钉住。 */
    public static final double RISE_HEIGHT = 24.0D;

    /**
     * 上升加速度（格/tick²）。
     *
     * <p><b>很小，是刻意的</b>。起飞不是「弹射」，是「缓缓被拽上去」：从 0 开始
     * 匀加速走完 {@link #RISE_HEIGHT}，第一 tick 只走 0.006 格（0.12 像素），
     * 第 20 tick 才 0.12 格，到顶点 0.54 格/tick。全程 89 tick（4.5 秒）。</p>
     *
     * <p>这里<b>不设速度上限</b>：只有 24 格，设了上限就会出现「加速 → 匀速」，
     * 匀速段看起来像电梯。想让上升更快/更慢就改这一个数：
     * 时间 = √(2H/a)，末速 = √(2Ha) —— 加速度翻倍，时间变为 1/√2。</p>
     */
    public static final double RISE_ACCEL = 0.006D;

    /** 在顶点悬停多久（tick）。只做「顿一下」的节奏，不给人反应时间。 */
    public static final int HOLD_TICKS = 10;

    /**
     * 下落加速度（格/tick²）。
     *
     * <p>0.5 配上 {@link #MAX_DROP} = 1.5，3 tick 就吃满限速：24 格约 17.5 tick 到底，
     * 也就是不到 1 秒。之前是 0.15 / 0.25（4.8 秒落地），慢得像羽毛，说不通
     * —— 一个从 24 格高处被放开的铁疙瘩不该有终端速度。</p>
     */
    public static final double DROP_ACCEL = 0.5D;

    /**
     * 下落速度上限（格/tick）。1.5 格/tick = 30 格/秒，约玩家自由落体的 40%。
     *
     * <p>不设更高是因为原版 {@code moveEntity} 的碰撞是「整段路径 AABB 扫描」，
     * 1.5 格/tick 仍能正确撞到方块；再往上在区块边界处有穿透风险，而且
     * 客户端插值渲染会明显撕裂成「一格一格跳」。24 格用这个速度已经够重了。</p>
     */
    public static final double MAX_DROP = 1.5D;

    /** 上升段总耗时（tick），由速度曲线解出：先匀加速到限速，再匀速。 */
    public static final double RISE_TICKS = solveRiseTicks();

    /** 进入下落阶段的 tick 数（相对出生）。 */
    public static final double DROP_START = LIFT_START + RISE_TICKS + HOLD_TICKS;

    /** 被顶住多久后自毁：5 分钟 × 20 tick/秒。 */
    public static final int BLOCKED_LIFETIME = 5 * 60 * 20;

    /** 落地后服务端再活多久才删（给客户端留出跑落地判定的时间）。 */
    public static final int SPLASH_DELAY = 8;

    /** 落地时播放的音效（文件在 {@code assets/makentmgreatagain/sounds/man.ogg}）。 */
    public static final String SHATTER_SOUND = "makentmgreatagain:man";

    /**
     * 落地瞬间的杀伤半径（格，<b>水平真实距离</b>）。
     *
     * <p>以落点为圆心、半径 1 格的圆柱。水平方向用两点距离判定，而不是
     * {@code boundingBox.expand(1, …)} —— 后者得到的是方盒，四个角上会多出
     * 0.41 格的「擦边也死」区域。竖直给 ±1.5 格容差，站在落点旁边半格台阶上的人也扫得到。</p>
     */
    public static final double SHOCK_RADIUS = 1.0D;

    /** 落地时溅出的物品碎屑个数。 */
    private static final int SHATTER_PARTICLES = 48;

    /** 落地时扬起的烟尘个数（少量，让「重」有体积感）。 */
    private static final int SHATTER_DUST = 10;

    /** 起飞前多久开始收窄「呼吸」浮动（tick）。 */
    private static final double BOB_FADE_TICKS = 20.0D;

    /** 原版 {@code EntityItem.onUpdate} 里每 tick 的重力，用来做速度补偿。 */
    private static final double GRAVITY = 0.03999999910593033D;

    /** 这一 tick 期望的<b>相对位移</b>（格）：正数上升、负数下坠、0 表示不动。 */
    private double moveOffset;
    /** 出生高度，所有相对高度都以它为基准。 */
    private double spawnY;
    /** 连续被顶住的 tick 数。 */
    private int blockedTicks;

    /** 已经落地（只用于让特效与销毁各执行一次）。 */
    private boolean landed;
    /** 落地时的 tick，服务端据此延迟删除。 */
    private int landedTick;

    /**
     * 总开关。false 时完全退回原版掉落物（不飞、不砸、不碎），可以正常捡起。
     *
     * <p>掉出来的是材料就无所谓；掉出来的是工具或方块，砸碎就是永久损失，
     * 那种情况该关。见 {@code items/ItemToolAbilityHelicopter}。</p>
     */
    private boolean liftEnabled = true;

    /** 关掉整套演出。返回 this，便于在 {@code fromDropped(...)} 后面链式调用。 */
    public EntityItemHelicopter setLiftEnabled(boolean liftEnabled) {
        this.liftEnabled = liftEnabled;
        return this;
    }

    public boolean isLiftEnabled() {
        return this.liftEnabled;
    }

    /**
     * 是否已经砸到地上。渲染器据此<b>立刻停止绘制</b>。
     *
     * <p>为什么需要这个：服务端故意多活 {@link #SPLASH_DELAY} tick 才 {@code setDead}
     * （给客户端留出跑落地判定、放粒子的时间），这段时间里实体本身还在，如果照常渲染，
     * 玩家就会看到「已经砸碎的东西还贴在地上」—— 也就是 0.25~0.5 秒的贴图残留。
     * 粒子和音效是落地那一帧发的，所以只要不画，观感就是「碎掉消失」。</p>
     */
    public boolean isLanded() {
        return this.landed;
    }

    /**
     * 已经砸碎的就不该还能捡。
     *
     * <p>服务端要多活 {@link #SPLASH_DELAY} tick 才删，这几 tick 里玩家如果正好站在落点，
     * 原版逻辑会把「已经碎掉」的物品塞进背包 —— 演出就白做了。</p>
     */
    @Override
    public void onCollideWithPlayer(EntityPlayer player) {
        if (this.landed) {
            return;
        }
        super.onCollideWithPlayer(player);
    }

    public EntityItemHelicopter(World world) {
        super(world);
        this.spawnY = this.posY;
    }

    public EntityItemHelicopter(World world, double x, double y, double z) {
        super(world, x, y, z);
        this.spawnY = y;
    }

    public EntityItemHelicopter(World world, double x, double y, double z, ItemStack stack) {
        super(world, x, y, z, stack);
        this.spawnY = y;
    }

    /**
     * 由原版 {@code EntityItem} 转成本实体时用。位置、速度、拾取延迟、年龄、悬浮相位
     * 全部照抄，避免「丢出来那一下跳一下」或者捡不起来。
     *
     * @param source 即将被 {@code setDead()} 掉的原版掉落物
     * @param stack  物品堆（会复制一份，原实体的那份随它一起消失）
     */
    public static EntityItemHelicopter fromDropped(World world, Entity source, ItemStack stack) {
        EntityItemHelicopter item = new EntityItemHelicopter(world, source.posX, source.posY, source.posZ,
                stack == null ? null : stack.copy());
        item.motionX = source.motionX;
        item.motionY = source.motionY;
        item.motionZ = source.motionZ;
        item.spawnY = source.posY;
        if (source instanceof EntityItem) {
            EntityItem other = (EntityItem) source;
            item.delayBeforeCanPickup = other.delayBeforeCanPickup;
            item.age = other.age;
            item.hoverStart = other.hoverStart;
        }
        return item;
    }

    /* ------------------------------------------------------------------ 轨迹 */

    /**
     * 解出走完 {@link #RISE_HEIGHT} 需要的 tick 数。
     *
     * <p>全程匀加速、无速度上限，所以就是最朴素的 {@code H = ½·a·t²} 反解：
     * {@code t = √(2H/a)}。24 格 / 0.006 得到 89 tick（4.5 秒）。</p>
     */
    private static double solveRiseTicks() {
        return Math.sqrt(2.0D * RISE_HEIGHT / RISE_ACCEL);
    }

    /**
     * 第 age 个 tick 时（相对 {@link #LIFT_START}）的上升速度（格/tick）。
     *
     * <p>就是 {@code a·age} —— <b>没有上限，会一直加速到顶点</b>。
     * 因为 {@link #RISE_ACCEL} 只有 0.006，第一 tick 的速度是 0.006 格/tick
     * （0.12 像素），到顶点才 0.54 格/tick，所以速度是「一点点往上爬」而不是弹射。
     * 唯一的收敛来自 {@link #applyMotion()} 里的「剩余距离钳制」：
     * 最后一步只走剩下的那点距离，所以不会冲过 24 格。</p>
     */
    public static double riseSpeedAt(double age) {
        if (age <= 0.0D) {
            return 0.0D;
        }
        return RISE_ACCEL * age;
    }

    /**
     * 第 t 个 tick 的下落速度（格/tick，正数表示「往下多少」）。
     * 从 0 按 {@link #DROP_ACCEL} 线性加速，{@link #MAX_DROP} 封顶。
     */
    public static double dropSpeedAt(double t) {
        double age = t - DROP_START;
        if (age <= 0.0D) {
            return 0.0D;
        }
        return Math.min(MAX_DROP, DROP_ACCEL * age);
    }

    /* ------------------------------------------------------------------ 自旋 */

    /**
     * 第 {@code ticks} tick 时物品绕竖直轴的旋转角度（度，正数）。
     *
     * <p>三段，全部是 {@code ticks} 的纯函数，客户端自己算得出来，不需要同步：</p>
     * <ol>
     * <li><b>出生 → 上升结束</b>：转速从 {@link #SPIN_RPM_START}（10 rpm）按
     * {@link #SPIN_RPM_ACCEL}（2 rpm/tick）匀加速到 {@link #SPIN_RPM_MAX}（120 rpm），
     * 然后恒定。<b>蓄势那 2.75 秒就在转</b> —— 躺在地上先把转速拉满，
     * 转速到 120 rpm 的那一 tick 正好起飞（见 {@link #LIFT_START}）。</li>
     * <li><b>悬停</b>：从上升末的角度平滑收到<b>最近的整圈</b>。
     * 收整圈是因为薄片转到侧面时宽为零，几乎看不见 —— 必须让它正面朝镜头落地。
     * 收尾在悬停的 {@link #HOLD_TICKS} tick 里做完，所以<b>下坠段角度恒为某个整圈倍数</b>
     * （视觉上就是正面 0°，但数值上不归零，避免 1.5 圈这种位置被强行拉回造成回弹），
     * 砸下来时不再旋转。</li>
     * <li><b>下坠</b>：定格。</li>
     * </ol>
     */
    public static double spinAngleAt(double ticks) {
        if (ticks <= 0.0D) {
            return 0.0D;
        }
        // 上升段结束 = 悬停开始。DROP_START 就是这个值再加 HOLD_TICKS
        double riseEnd = LIFT_START + RISE_TICKS;
        double atRiseEnd = spinAngleSpinUp(riseEnd);
        if (ticks >= riseEnd + HOLD_TICKS || HOLD_TICKS <= 0) {
            return settledAngle(atRiseEnd);
        }
        if (ticks <= riseEnd) {
            return spinAngleSpinUp(ticks);
        }
        // 悬停段：从 atRiseEnd 平滑收到最近整圈，两端斜率为 0
        double u = (ticks - riseEnd) / HOLD_TICKS;
        double s = u * u * (3.0D - 2.0D * u);
        return atRiseEnd + (settledAngle(atRiseEnd) - atRiseEnd) * s;
    }

    /**
     * 从出生起算的累计角度：转速按 {@link #SPIN_RPM_ACCEL} 从 {@link #SPIN_RPM_START}
     * 线性涨到 {@link #SPIN_RPM_MAX}，到顶后恒定。
     *
     * <p>角速度函数是 {@code ω(t) = (R₀ + a·t)·k}（k = {@link #DEG_PER_TICK_PER_RPM}），
     * 直接积分就是 {@code k·(R₀·t + a·t²/2)} —— 没有 smoothstep，因为是<b>匀加速</b>：
     * 用户要的是「每 tick 稳定加 2 转」，不是「慢慢起步」。</p>
     *
     * @param t 出生后的 tick 数（蓄势段就要算，所以基准是 0）
     */
    private static double spinAngleSpinUp(double t) {
        if (t <= 0.0D) {
            return 0.0D;
        }
        if (t <= LIFT_START) {
            return DEG_PER_TICK_PER_RPM
                    * (SPIN_RPM_START * t + 0.5D * SPIN_RPM_ACCEL * t * t);
        }
        // 过了起飞点：转速已到 SPIN_RPM_MAX，之后匀速
        double atLift = DEG_PER_TICK_PER_RPM
                * (SPIN_RPM_START * LIFT_START + 0.5D * SPIN_RPM_ACCEL * LIFT_START * LIFT_START);
        double rpmAtLift = SPIN_RPM_START + SPIN_RPM_ACCEL * LIFT_START;
        return atLift + DEG_PER_TICK_PER_RPM * rpmAtLift * (t - LIFT_START);
    }

    /** 距离 {@code angle} 最近的整圈角度。 */
    private static double settledAngle(double angle) {
        return 360.0D * Math.round(angle / 360.0D);
    }

    /** 是否已经过了顶点悬停、进入自由落体。 */
    public boolean isDropping() {
        return this.ticksExisted >= DROP_START;
    }

    /* ------------------------------------------------------------------ 物理 */

    @Override
    public void onUpdate() {
        double yBefore = this.posY;

        // 两侧都跑：客户端不跑的话会先按原版重力往下掉，再被服务端的位置包拽回来，看着一抖一抖。
        // 位移只由 ticksExisted 决定，两边算出来完全一致，不需要同步。
        this.applyMotion();

        super.onUpdate();

        if (this.isDead || !this.liftEnabled) {
            return;
        }

        double moved = this.posY - yBefore;   // 实际位移，负数是往下

        if (this.isDropping()) {
            if (!this.landed) {
                // 落地判据：onGround 最直接；位移判据兜住「擦着方块边缘滑下去」。
                // 位移判据额外要求位置已经回到出生高度附近 —— 否则客户端在追赶服务端
                // 位置包、某几 tick 位移被吃掉时，会在半空中就误判成落地。
                boolean backHome = this.posY <= this.spawnY + 0.5D;
                if (this.onGround || (backHome && moved > this.moveOffset * 0.5D)) {
                    this.landed = true;
                    this.landedTick = this.ticksExisted;
                    // 粒子/音效只有客户端能生成，见类注释
                    if (this.worldObj.isRemote) {
                        this.spawnShatterFx();
                    } else {
                        // 击杀只在服务端做。两边都做没有意义：血量是服务端权威，
                        // 客户端改了下一 tick 就被 S06 包刷回去。
                        this.crushNearbyPlayers();
                    }
                }
            } else if (!this.worldObj.isRemote
                    && this.ticksExisted - this.landedTick >= SPLASH_DELAY) {
                // 服务端故意晚几 tick 再删，好让客户端的落地判定跑得到。
                // 这几 tick 实体还在，但渲染器已经按 isLanded() 停止绘制，不会穿帮。
                this.setDead();
            }
            return;
        }

        if (this.ticksExisted >= LIFT_START && this.moveOffset > 0.0D) {
            // 上升段被天花板顶住：位移提不上去就计数，超过上限自毁，免得永远卡在下面
            if (moved < this.moveOffset * 0.5D) {
                if (++this.blockedTicks > BLOCKED_LIFETIME) {
                    this.setDead();
                }
            } else {
                this.blockedTicks = 0;
            }
        }
    }

    /**
     * 把这一 tick 的目标位移翻译成 {@code motionY}。
     *
     * <p>上升段是「速度曲线 + 剩余距离钳制」：速度负责手感，钳制负责「必须停在 24 格」。
     * 到顶后剩余为 0，位移归零，物品定住 —— 不是靠减速刹车，所以不会冲过头。
     * 下坠段直接取 {@link #dropSpeedAt(double)}。</p>
     *
     * <p>已经落地（{@code landed}）就直接退出并把 motionY 归零 —— 见方法里的说明，
     * 否则会一直往方块里顶。</p>
     */
    private void applyMotion() {
        if (!this.liftEnabled) {
            return;
        }

        // 落地之后一 tick 都不能再推。dropSpeedAt 是随 ticks 单调增的，落地后
        // 若继续写 motionY，每一 tick 都会比上一 tick 更大，被地面挡住就变成
        // 持续往方块里顶 —— 客户端看着是陷进地里抖。
        if (this.landed) {
            this.moveOffset = 0.0D;
            this.motionY = 0.0D;
            return;
        }

        double age = this.ticksExisted - LIFT_START;
        if (age < 0.0D) {
            return;   // 蓄势阶段，交给原版重力
        }

        double offset;
        if (this.isDropping()) {
            offset = -dropSpeedAt(this.ticksExisted);
        } else {
            double remaining = (this.spawnY + RISE_HEIGHT) - this.posY;
            if (remaining <= 0.0D) {
                offset = 0.0D;          // 已经到 24 格，钉住
            } else {
                offset = Math.min(riseSpeedAt(age), remaining);
            }
        }

        this.moveOffset = offset;
        // 原版随后会先 -0.04 再 moveEntity，所以这里补回 0.04，净位移就正好是 offset。
        this.motionY = offset + GRAVITY;
    }

    /**
     * 落地瞬间把半径 {@link #SHOCK_RADIUS} 格内的<b>非玩家生物</b>全部处决。
     * <b>只能在服务端调用。</b>
     *
     * <p><b>玩家完全放过</b>：不造成伤害、不处决。套装主人不该被自己的材料砸死
     * （尤其是摔死后重生回原地，正好站在落点上，会被无限循环砸死），
     * 别的玩家也不该被误伤。</p>
     *
     * <p>候选集先用 AABB 粗筛（省得自己遍历所有实体），再用水平真实距离精筛 ——
     * AABB 的四个角比圆多出 0.41 格，不精筛会出现「站在斜角两格外也被砸死」。</p>
     */
    private void crushNearbyPlayers() {
        AxisAlignedBB box = this.boundingBox.expand(SHOCK_RADIUS, 1.5D, SHOCK_RADIUS);
        List<EntityLivingBase> nearby = this.worldObj.getEntitiesWithinAABB(EntityLivingBase.class, box);

        for (EntityLivingBase target : nearby) {
            // 玩家一律跳过（判据见 HelicopterGuardRules.shouldCrush）
            if (!HelicopterGuardRules.shouldCrush(target instanceof EntityPlayer)) {
                continue;
            }

            double dx = target.posX - this.posX;
            double dz = target.posZ - this.posZ;
            if (dx * dx + dz * dz > SHOCK_RADIUS * SHOCK_RADIUS) {
                continue;
            }
            smite(target);
        }
    }

    /**
     * 处决一个非玩家生物：<b>血量置 0 + setDead() + isDead = true</b>，三步都做。
     *
     * <h2>为什么不调 {@code attackEntityFrom}</h2>
     * <p>那是一条「正常伤害」链路：护甲吸收、抗性提升减免、无敌帧去重、击退……
     * 抗性提升 V 能把任何伤害吃到 0，HBM 的高档护甲也能挡下绝大部分。
     * 这里要的是「砸中即死」，所以直接写血量，一件装备都不参与。</p>
     *
     * <h2>为什么三步都要写</h2>
     * <ul>
     *   <li>{@code setHealth(0)}：血量归零，客户端下一 tick 收到同步包才会显示死亡；</li>
     *   <li>{@code onDeath}：走标准死亡流程 —— 掉落物、经验、统计都在里面，
     *       不调的话生物「死了但什么都不掉」；</li>
     *   <li>{@code setDead()} + {@code isDead = true}：立刻标记移除。
     *       只写血量会让尸体在原地躺满 20 tick 死亡动画，看着像「砸不死」；
     *       {@code setDead()} 走的是原版流程（会发移除包），
     *       直接置 {@code isDead} 是兜底 —— 别的模组可能把 setDead 覆写成空方法。</li>
     * </ul>
     *
     * <p>仍然喂一次 CombatTracker：死亡播报与掉落判定取的是最后一条伤害记录，
     * 不喂的话生物会显示成「被上一个打它的东西杀死」。</p>
     */
    private static void smite(EntityLivingBase target) {
        if (target == null || target.isDead || target.getHealth() <= 0.0F) {
            return;   // 已经是尸体了，别重复处理
        }

        DamageSourceHelicopter source = DamageSourceHelicopter.INSTANCE;

        // ① 让「最后一击」是这个来源，死亡播报与掉落判定才会用它
        target.func_110142_aN().func_94547_a(source, target.getHealth(), target.getMaxHealth());

        // ② 血量归零
        target.setHealth(0.0F);

        // ③ 标准死亡流程：掉落物、经验、统计
        target.onDeath(source);

        // ④ 立刻移除，不等 20 tick 死亡动画
        target.setDead();
        target.isDead = true;
    }

    /**
     * 落地特效：物品碎屑 + 扬尘 + 一声闷响。<b>只能在客户端调用。</b>
     *
     * <p>碎屑用 {@code iconcrack_<物品 id>_<meta>} —— 客户端会拿物品图标切成小片飞出去，
     * 所以「和这个锭同色」是白给的，不用按材质维护色表。扬尘单独用 {@code smoke}，
     * 数量少，只作为冲击的「体积感」，太多会盖住碎屑。</p>
     *
     * <p>音效走 {@code World.playSound}：它在服务端是空方法，在客户端才会真的出声，
     * 所以和粒子一样放这边。{@code distanceDelay=false} 是因为玩家就在几格内，
     * 加延迟反而拖沓。</p>
     */
    private void spawnShatterFx() {
        ItemStack stack = this.getEntityItem();
        if (stack == null || stack.getItem() == null) {
            return;
        }

        String crack = "iconcrack_" + Item.getIdFromItem(stack.getItem()) + "_" + stack.getItemDamage();

        for (int i = 0; i < SHATTER_PARTICLES; i++) {
            double vx = (this.rand.nextDouble() - 0.5D) * 0.8D;
            double vy = this.rand.nextDouble() * 0.5D + 0.1D;
            double vz = (this.rand.nextDouble() - 0.5D) * 0.8D;
            this.worldObj.spawnParticle(crack, this.posX, this.posY + 0.1D, this.posZ, vx, vy, vz);
        }

        for (int i = 0; i < SHATTER_DUST; i++) {
            double vx = (this.rand.nextDouble() - 0.5D) * 0.35D;
            double vy = this.rand.nextDouble() * 0.15D;
            double vz = (this.rand.nextDouble() - 0.5D) * 0.35D;
            this.worldObj.spawnParticle("smoke", this.posX, this.posY + 0.1D, this.posZ, vx, vy, vz);
        }

        // 原调、原音量。之前写成 1.5F / 0.7F 想「更响更沉」，实际效果是变调
        // —— Minecraft 的第二个参数是 pitch，0.7 就是降了 5 个半音，听着像磁带拖慢。
        this.worldObj.playSound(this.posX, this.posY, this.posZ, SHATTER_SOUND, 1.0F, 1.0F, false);
    }

    /* ------------------------------------------------------------------ 浮沉 */

    /**
     * 渲染时「上下呼吸」幅度的系数，1 = 原版幅度，0 = 完全稳定。
     *
     * <p>原版掉落物会以 ±0.1 格的幅度上下浮动，静止时看着有生气；但一旦开始上升，
     * 这个浮动就变成叠加在飞行轨迹上的抖动。所以让它在起飞前
     * {@link #BOB_FADE_TICKS} tick 内平滑收干净 —— 起飞瞬间是平稳的，
     * 而上冲和下坠本身的速度变化才是视觉主体。</p>
     */
    public static double bobScaleAt(double ticks) {
        double start = LIFT_START - BOB_FADE_TICKS;
        double u = (ticks - start) / BOB_FADE_TICKS;
        if (u <= 0.0D) {
            return 1.0D;
        }
        if (u >= 1.0D) {
            return 0.0D;
        }
        // smoothstep：两端斜率为 0，起止都不留折角
        return 1.0D - u * u * (3.0D - 2.0D * u);
    }

    /* ------------------------------------------------------------------ 生成包同步 */

    /**
     * 服务端：把轨迹基准随生成包发给客户端。
     *
     * <p>三个量都必须传，原因见 {@link HelicopterDropSync} 的类注释 ——
     * 尤其是 {@code spawnY}：FML 客户端先 {@code new EntityItemHelicopter(world)}
     * （posY=0 → spawnY=0）再设真实位置，不传的话客户端会朝错误目标高度
     * 先走一遍，再被位置包拽回来，看着就是「卡顿着从下往上重走一遍」。</p>
     */
    @Override
    public void writeSpawnData(ByteBuf buffer) {
        buffer.writeBytes(HelicopterDropSync.write(this.spawnY, this.liftEnabled, this.ticksExisted));
    }

    /** 客户端：读回轨迹基准，让两端从同一个起点、同一个 tick 开始算。 */
    @Override
    public void readSpawnData(ByteBuf additionalData) {
        byte[] data = new byte[HelicopterDropSync.SIZE];
        if (additionalData.readableBytes() < HelicopterDropSync.SIZE) {
            return;   // 包不完整：保持构造值，最坏只是这一个掉落物轨迹不对，不让客户端崩
        }
        additionalData.readBytes(data);
        HelicopterDropSync.State state = HelicopterDropSync.read(data);

        // 一律用同步值覆盖构造器里的 0（见 HelicopterDropSync.resolveSpawnY）
        this.spawnY = HelicopterDropSync.resolveSpawnY(this.spawnY, state);
        this.liftEnabled = state.liftEnabled;
        this.ticksExisted = state.ticksExisted;
    }

    /**
     * 客户端收到服务端位置包时的处理：<b>演出期间忽略远程硬设，继续本地推算</b>。
     *
     * <p>原版 {@code Entity.setPositionAndRotation2} 是「直接 setPosition + 碰撞修正」，
     * 没有平滑插值。掉落物每 {@code updateFrequency} tick 收一次位置包，
     * 每次都会把客户端硬拽到服务端位置 —— 上升段每 tick 只走 0.006~0.54 格，
     * 硬拽的落差就表现为一次次「顿一下」。</p>
     *
     * <p>轨迹已经两端一致（同一个 spawnY、同一个 ticksExisted、纯函数），
     * 本地推算的结果和服务端本来就该相同，所以演出期间直接忽略远程位置最平滑。
     * 只在演出关掉（护甲）或已落地时才接受服务端位置，保证最终落点权威一致。</p>
     */
    @Override
    public void setPositionAndRotation2(double x, double y, double z, float yaw, float pitch, int increment) {
        if (this.worldObj != null && this.worldObj.isRemote && this.liftEnabled && !this.landed) {
            // 演出中：忽略远程硬设，本地轨迹自己算（两端一致，不会漂）
            return;
        }
        super.setPositionAndRotation2(x, y, z, yaw, pitch, increment);
    }

    /* ------------------------------------------------------------------ 存档 */

    /**
     * 只存「不能重算」的量。
     *
     * <p>高度、速度全部由 {@code ticksExisted} 推出，落盘反而会和 tick 计数打架
     * （读档瞬间 ticksExisted 从 0 重新算，存下来的高度就对不上了）。</p>
     */
    @Override
    public void writeEntityToNBT(NBTTagCompound nbt) {
        super.writeEntityToNBT(nbt);
        nbt.setDouble("MNGA_SpawnY", this.spawnY);
        nbt.setInteger("MNGA_Blocked", this.blockedTicks);
        nbt.setBoolean("MNGA_Lift", this.liftEnabled);
        // 落地状态也要存：落地延迟窗口内重载世界，不存会重新起飞
        nbt.setBoolean("MNGA_Landed", this.landed);
        nbt.setInteger("MNGA_LandedTick", this.landedTick);
    }

    @Override
    public void readEntityFromNBT(NBTTagCompound nbt) {
        super.readEntityFromNBT(nbt);
        this.blockedTicks = nbt.getInteger("MNGA_Blocked");
        // 缺这个 tag（老存档、或不是从本模组放出来的）就保持默认 true
        if (nbt.hasKey("MNGA_Lift")) {
            this.liftEnabled = nbt.getBoolean("MNGA_Lift");
        }
        // 老存档 / 首次读档没有这个 tag，就以读档位置当作起点
        this.spawnY = nbt.hasKey("MNGA_SpawnY") ? nbt.getDouble("MNGA_SpawnY") : this.posY;
        if (nbt.hasKey("MNGA_Landed")) {
            this.landed = nbt.getBoolean("MNGA_Landed");
            this.landedTick = nbt.getInteger("MNGA_LandedTick");
        }
    }
}
