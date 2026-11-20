package com.makentmgreatagain.hook;

/**
 * 耐摔合金守卫的「纯判定规则」——不依赖 Minecraft，可独立单元测试。
 *
 * <p>把四条容易写错、又必须跨环境一致的判据从字节码闸门里剥出来：</p>
 * <ol>
 *   <li><b>摔落放行窗口</b> {@link #isFallPassFresh}：摔落必须绕过全部守卫，
 *       而摔死是一条跨 tick 的流程（{@code setHealth → onDeath → setDead}），
 *       窗口只活 1 tick 会让后半段被拦下、摔不死；</li>
 *   <li><b>setDead 判定</b> {@link #allowSetDead}：只拦「血量见底 + 非摔落」的强制移除，
 *       登出/切维度（血量正常）一律放行；</li>
 *   <li><b>isDead 撤销判定</b> {@link #shouldRevokeDeadFlag}：别的模组直接写
 *       {@code isDead = true} 不经过任何方法，只能每 tick 发现后撤销，但摔死不能撤；</li>
 *   <li><b>扒甲来源判定</b> {@link #allowArmorSlotWrite}：护甲槽只允许玩家自己改，
 *       其他生物/模组的移除一律拒绝。</li>
 * </ol>
 *
 * <p>本类刻意不 import 任何 {@code net.minecraft} / {@code com.hbm}，
 * 因此可以用 {@code javac + java} 独立编译运行（见 {@code HelicopterGuardRulesTest}）。</p>
 */
public final class HelicopterGuardRules {

    /**
     * 摔落放行窗口（tick）。
     *
     * <p>取 5 而不是 1：摔死要依次走 {@code setHealth(0)} → {@code onDeath} →
     * {@code setDead}，中间还可能隔着死亡动画与事件派发，1 tick 的窗口会在
     * 后半段过期，导致摔死被守卫拦成「0 血活死人」。</p>
     *
     * <p><b>代价</b>：摔落后的这几 tick 内守卫整体放行，所以紧接着摔落发生的
     * 其他伤害也会掉血。这是「摔落必须完全绕过检测器」的必然取舍。</p>
     */
    public static final int FALL_PASS_TICKS = 5;

    /** 护甲槽在 {@code setCurrentItemOrArmor} 里的编号范围（0 是手持栏）。 */
    public static final int ARMOR_SLOT_MIN = 1;
    public static final int ARMOR_SLOT_MAX = 4;

    private HelicopterGuardRules() {
    }

    /**
     * 摔落放行标记是否仍有效。
     *
     * @param markedTime 打标记时的世界时间；{@code < 0} 表示没打过标记
     * @param now        当前世界时间
     */
    public static boolean isFallPassFresh(long markedTime, long now) {
        if (markedTime < 0L) return false;
        long diff = now - markedTime;
        return diff >= 0L && diff <= FALL_PASS_TICKS;
    }

    /**
     * 这次 {@code setDead} 能不能执行。
     *
     * @param health   当前血量
     * @param fallPass 是否处在摔落放行窗口内
     * @param wearsSet 是否穿着耐摔合金全套
     * @return true 放行，false 拦下（这次强制移除不发生）
     */
    public static boolean allowSetDead(float health, boolean fallPass, boolean wearsSet) {
        if (health > 0.0F) return true;   // 登出 / 切维度：血量正常，必须放行
        if (!wearsSet) return true;       // 没穿这套不干预
        if (fallPass) return true;        // 摔死照常
        return false;
    }

    /**
     * 每 tick 发现 {@code isDead == true} 时，要不要撤销它。
     *
     * <p>只撤销「血量正常却被标死」这一种——那是别的模组直接写字段强杀。
     * 摔死不撤（套装设定就是只摔得死）；血量为 0 且非摔落属于濒死，
     * 交给血量兜底处理，不在这里动死亡标记。</p>
     */
    public static boolean shouldRevokeDeadFlag(float health, boolean fallPass, boolean wearsSet, int deathTime) {
        // 死亡动画正在跑就绝不插手。原版 deathTime 每 tick 自增、到 20 才真正移除实体
        // 并弹重生界面；在这里把它清零会让死亡流程永远走不到第 20 tick，
        // 玩家卡成倾斜的红色尸体（开启死亡不掉落时尤其明显：重生重建玩家后血量恢复成满，
        // 「血量正常」判据成立，正好踩中这个坑）。
        if (deathTime > 0) return false;
        return wearsSet && !fallPass && health > 0.0F;
    }

    /**
     * 每 tick 要不要把血量拉回上限。
     *
     * <p>与 {@link #shouldRevokeDeadFlag} 同理：死亡动画期间不能拉血，
     * 否则死亡判定被反复打断，同样会卡住死亡流程。摔落窗口内也不拉，
     * 让摔伤正常结算 —— 套装设定是「只摔得死」。</p>
     *
     * @param health    当前血量
     * @param maxHealth 血量上限
     * @param fallPass  是否处在摔落放行窗口内
     * @param wearsSet  是否穿着耐摔合金全套
     * @param deathTime 原版死亡动画计时（&gt; 0 表示死亡流程正在跑）
     */
    public static boolean shouldTopUpHealth(float health, float maxHealth,
                                           boolean fallPass, boolean wearsSet, int deathTime) {
        if (deathTime > 0) return false;
        if (!wearsSet || fallPass) return false;
        return health < maxHealth;
    }

    /**
     * 这次护甲槽写入能不能执行。
     *
     * @param slot          {@code setCurrentItemOrArmor} 的槽号（0 手持，1~4 护甲）
     * @param selfInitiated 是否由玩家自己的操作发起（GUI 点击 / 服务端交互包）
     * @param wearsSet      是否穿着耐摔合金（任意一件即算，被扒的正是这一件）
     * @return true 放行，false 拒绝（这次扒甲不发生）
     */
    public static boolean allowArmorSlotWrite(int slot, boolean selfInitiated, boolean wearsSet) {
        if (slot < ARMOR_SLOT_MIN || slot > ARMOR_SLOT_MAX) return true;  // 手持栏 / 越界不管
        if (!wearsSet) return true;
        if (selfInitiated) return true;   // 玩家自己脱甲永远允许
        return false;
    }

    /**
     * 玩家是否处在死亡流程里（含「刚死那一 tick」）。
     *
     * <p>四条判据缺一不可，尤其是前两条 —— 原版 {@code EntityLivingBase.onDeath}
     * 只置 {@code dead = true}，{@code deathTime} 下一 tick 才自增、{@code isDead}
     * 要等 20 tick 后 {@code setDead()} 才置位。只看后两条会漏掉死亡那一 tick，
     * 护甲兜底把刚被 {@code dropAllItems} 清空的槽补回去 → 地上掉一份、槽里一份，
     * 摔一次复制一套甲。</p>
     *
     * @param health    当前血量（&le; 0 视为已死）
     * @param dead      Forge 的 {@code EntityLivingBase.dead} 标记
     * @param isDead    原版 {@code Entity.isDead} 标记
     * @param deathTime 原版死亡动画计时
     */
    public static boolean isDeathInProgress(float health, boolean dead, boolean isDead, int deathTime) {
        return health <= 0.0F || dead || isDead || deathTime > 0;
    }

    /**
     * 落地冲击要不要处决这个实体。
     *
     * <p>玩家一律放过（套装主人不该被自己的材料砸死，别的玩家也不该被误伤）；
     * 非玩家生物一律处决。</p>
     *
     * @param isPlayer 目标是否玩家
     */
    public static boolean shouldCrush(boolean isPlayer) {
        return !isPlayer;
    }
}