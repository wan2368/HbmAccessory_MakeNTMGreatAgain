package com.makentmgreatagain.init;

import com.makentmgreatagain.MakeNTMGreatAgain;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.stats.Achievement;
import net.minecraftforge.common.AchievementPage;

/**
 * 成就表，走 Minecraft 原生成就系统，在成就界面里单独占一页 {@link #PAGE_NAME}（{@code MNGA}）。
 *
 * <p>加一条成就：字段区加 {@code public static Achievement mnga_xxx;} →
 * {@link #init()} 里 {@code mnga_xxx = ach("mnga_xxx", 列, 行, itemStack(...), 前置);} →
 * 加进 {@link AchievementPage} 构造参数 → 中英 lang 各补两行 →
 * 触发点调 {@link #unlock(EntityPlayer, Achievement)}。</p>
 *
 * <p>四个静默失败的坑：忘了 {@code registerStat()}（永远触发不了，无报错）、
 * 没被页面收录（掉进原版 Minecraft 页）、在客户端触发（无效）、
 * 给多来源产物挂硬前置（1.7.10 前置是硬门槛，走另一条线的玩家永远点不亮）。</p>
 *
 * <p>坐标一格 24px，列右行下，可写负数。连线是「先横后竖」的折线，别让别的图标挡在路径上。
 * 本表布局：{@code (0,0) 芜！→ (1,0) 这是什么能喝吗？}，支线挂 (1,-1)、(1,2)、(3,1)，
 * 其余一律独立树根。</p>
 */
public final class ModAchievements {

    /* ---------------------------- 成就字段（触发点引用字段，打错字编译期就报错） ---------------------------- */

    // ---- 白钨矿湿法主线 ----
    public static Achievement scheelite_mined;    // 芜！
    public static Achievement scheelite_acid;     // 这是什么能喝吗？
    public static Achievement tungsten_carbide;   // 高强度碳化钨

    // ---- 白钨矿支线 ----
    public static Achievement nothing_smelt;      // 滚木？
    public static Achievement scheelite_300;      // 这就是全部了
    public static Achievement waste_acid;         // 我应该把世界泡在酸里。

    // ---- 浸出渣处理链 ----
    public static Achievement beryllium_conc;     // 尘封的故事
    public static Achievement arsenic_cake;       // 别舔
    public static Achievement arsenic_nugget;     // 拿命换的

    // ---- 海水与冶金 ----
    public static Achievement seawater;           // 抽上来的不是水
    public static Achievement chlorine;           // 两种都要命的
    public static Achievement lithium;            // 从这锂开始
    public static Achievement zinc;               // 我的“心”都碎了！

    // ---- 彩蛋 ----
    public static Achievement hover;              // 我飞了
    public static Achievement escape_abyss;       // 逃离深渊
    public static Achievement fork_shock;         // 酥酥麻麻（电叉电到自己）

    // ---- 机制彩蛋 ----
    public static Achievement pump_overworked;    // 吃力不讨好
    public static Achievement sea_dredge;         // 海底捞
    public static Achievement big_is_good;        // 大就是好
    public static Achievement absolute_zero;      // 绝对零度
    public static Achievement helicopter;         // MAN！
    public static Achievement pump_strike;        // 她罢工了（隐藏：未解锁时标题与描述都是 ???）
    public static Achievement helicopter_elements; // HeLiCoPtEr
    public static Achievement ruin_estate;        // 遗迹房地产

    /** 页面名：成就界面顶上那个可切换的标签就显示这几个字。 */
    public static final String PAGE_NAME = "MNGA";

    /** 「这就是全部了」的累计挖矿门槛 */
    public static final int SCHEELITE_TARGET = 300;

    /**
     * 「吃力不讨好」的高度门槛：环境泵偏离工作高度带多少档。
     *
     * <p>每 5 格一档，10 档 = 离开区间 50 格，此时产出 -50%、输入 +50%、耗电 ×(1+2.5)×(1+0.5)=5.25。
     * 主世界的高度带是 45~75，所以要把泵架到 y≥125 才够（高空没有介质，落在兜底的抽水档）。</p>
     */
    public static final int PUMP_PENALTY_STEPS = 10;

    /**
     * 「她罢工了」的高度门槛：产出归零的那一档。
     *
     * <p>每档产出 −5%，20 档正好归零（见 {@code EnvironmentPumpMedia.penaltyOutputFactor}），
     * 此时机器直接停机。20 档 = 偏离高度带 100 格：主世界要架到 y≥175，
     * 下界 y≥160，末地 y≥180。</p>
     */
    public static final int PUMP_STRIKE_STEPS = 20;

    /** 玩家持久 NBT 里记录白钨矿累计挖掘数的键（见 {@link #addScheeliteMined}） */
    private static final String NBT_SCHEELITE_MINED = "mngaScheeliteMined";

    private static boolean initialized = false;

    /**
     * 建表 + 注册页面。
     *
     * <p>必须在物品和方块注册<b>之后</b>调用（图标要 new ItemStack），
     * 也就是本模组 preInit 的末尾；同时必须早于任何玩家进入世界。</p>
     */
    public static void init() {

        if (initialized) return;
        initialized = true;

        // ── 白钨矿湿法主线：矿 → 钨酸 ──
        scheelite_mined = ach("mnga_scheelite_mined", 0, 0, blockStack(ModBlocks.ore_scheelite), null);
        scheelite_acid = ach("mnga_scheelite_acid", 1, 0, itemStack(ModItems.tungstic_acid), scheelite_mined);

        // 独立树根：钨粉还有原版钨矿、钨晶碎裂等来源，挂硬前置会锁死别的路线。
        tungsten_carbide = ach("mnga_tungsten_carbide", 2, 0,
                itemStack(com.hbm.items.ModItems.ingot_tungsten_carbide), null).setSpecial();

        // ── 支线一：把矿石丢进熔炉，烧完什么也没有 ──
        nothing_smelt = ach("mnga_nothing_smelt", 1, -1,
                itemStack(com.hbm.items.ModItems.nothing), scheelite_mined);

        // ── 支线二：累计挖 300 块 ──
        scheelite_300 = ach("mnga_scheelite_300", 0, 1, blockStack(ModBlocks.ore_scheelite), scheelite_mined);

        // ── 支线三：废酸闭环（16 份钨酸 ≈ 10,000 mB 废酸），放在钨酸正下方两格 ──
        waste_acid = ach("mnga_waste_acid", 1, 2,
                fluidStack(ModFluids.WASTE_ACID.id), scheelite_acid);

        // ── 浸出渣处理链：铍精矿只有 R1/R2 一个来源，前置成立 ──
        beryllium_conc = ach("mnga_beryllium_conc", 3, 1,
                itemStack(ModItems.beryllium_concentrate), scheelite_acid);

        // 独立树根：白砷另有 ISP 脱砷浸出、含砷金矿渣焙烧两个来源。
        arsenic_cake = ach("mnga_arsenic_cake", 4, 1,
                itemStack(ModItems.arsenic_trioxide), null);

        // 砷粒唯一的来源是 ⑪ 砷化氢热分解，输入就有白砷，这条硬前置是真的必然。
        arsenic_nugget = ach("mnga_arsenic_nugget", 5, 1,
                itemStack(com.hbm.items.ModItems.nugget_arsenic), arsenic_cake);

        // ── 海水线：独立树，海水不是白钨的产物 ──
        seawater = ach("mnga_seawater", 7, 0, blockStack(ModBlocks.environment_pump), null);

        // 氯、锂各自成根：氯能由「水 + 盐粉」电解得到，锂走吸附线，两条都不碰对方。
        chlorine = ach("mnga_chlorine", 8, 0, fluidStack(com.hbm.inventory.fluid.Fluids.CHLORINE.getID()), null);
        lithium = ach("mnga_lithium", 9, 0, itemStack(com.hbm.items.ModItems.powder_lithium), null);

        // ── 铅锌线：独立树 ──
        zinc = ach("mnga_zinc", 4, 4, itemStack(ModItems.powder_zinc_isp), null);

        // ── 彩蛋：各成一根，不串父子（否则会互相要求前置） ──
        hover = ach("mnga_hover", 0, 4, itemStack(ModItems.hover_jetpack), null);
        escape_abyss = ach("mnga_escape_abyss", 1, 4, itemStack(ModItems.cigarette_drx), null).setSpecial();

        // 泡在自己电的那摊水里会被回灌电一下：纯彩蛋，独立一棵树
        fork_shock = ach("mnga_fork_shock", 2, 4, itemStack(ModItems.electric_fork), null);

        // ── 机制彩蛋：环境泵被架到超出高度带 10 档还在转（产出减半、耗电 5.25 倍）──
        // 高空没有识别到介质，会落到兜底的抽水档，所以这台泵一定转得起来。
        pump_overworked = ach("mnga_pump_overworked", 7, 6, blockStack(ModBlocks.environment_pump), null);

        // ── 机制彩蛋：深海结核、大型机器、氦锭、耐摔合金（各自成根，互不前置）──
        sea_dredge = ach("mnga_sea_dredge", 0, 5, blockStack(ModBlocks.deep_sea_nodule), null);
        big_is_good = ach("mnga_big_is_good", 2, 5,
                blockStack(ModBlocks.machine_large_catalytic_chemical_factory), null);
        absolute_zero = ach("mnga_absolute_zero", 4, 5, itemStack(ModItems.ingot_helium), null);
        helicopter = ach("mnga_helicopter", 6, 5, itemStack(ModItems.ingot_helicopter), null);

        // ── 「她罢工了」：环境泵被架到 20 档以上，产出归零、机器直接停机 ──
        // 隐藏成就：没解锁时界面里标题和描述都是 ???（见 HiddenAchievement）。
        // 独立树根 —— 不挂 pump_overworked 前置，否则没解锁「吃力不讨好」的玩家永远点不亮它。
        pump_strike = new HiddenAchievement("mnga_pump_strike", 7, 7,
                blockStack(ModBlocks.environment_pump), null)
                .initIndependentStat().setSpecial().registerStat();

        // ── 「HeLiCoPtEr」：穿全套耐摔合金，且背包里同时有锭/板/线/螺栓/管/壳六种形态 ──
        helicopter_elements = ach("mnga_helicopter_elements", 6, 7,
                itemStack(ModItems.ingot_helicopter), null).setSpecial();

        // ── 「遗迹房地产」：在下界的日落六号内部放一张床 ──
        ruin_estate = ach("mnga_ruin_estate", 0, 7,
                itemStack(net.minecraft.init.Items.bed), null).setSpecial();

        // ── 页面：漏掉的成就会掉进默认的 "Minecraft" 页，新增时务必加到这里 ──
        AchievementPage.registerAchievementPage(new AchievementPage(PAGE_NAME,
                scheelite_mined, scheelite_acid, tungsten_carbide, nothing_smelt, scheelite_300,
                waste_acid, beryllium_conc, arsenic_cake, arsenic_nugget,
                seawater, chlorine, lithium, zinc, hover, escape_abyss, fork_shock,
                pump_overworked, sea_dredge, big_is_good, absolute_zero, helicopter,
                pump_strike, helicopter_elements, ruin_estate));

        MakeNTMGreatAgain.LOG.info("[MNGA] 已注册成就页 " + PAGE_NAME + "，共 24 条");
    }

    /**
     * 解锁一条成就（服务端调用）。
     *
     * <p>只触发目标这一条，不补祖先链。曾经会沿 {@code parentAchievement} 倒序补全整条链，
     * 结果是跳步拿到砷粒就把前面五条一起点亮，等于白送整条科技线。</p>
     */
    public static void unlock(EntityPlayer player, Achievement achievement) {

        if(player == null || achievement == null) return;
        if(player.worldObj == null || player.worldObj.isRemote) return;
        if(!(player instanceof EntityPlayerMP)) return;

        // 只触发这一条。已解锁的重复触发无副作用：存档里的值一直是 1，不会重复弹提示。
        player.triggerAchievement(achievement);
    }

    /**
     * 给机器附近的玩家解锁。
     *
     * <p>机器产出没有玩家上下文（方块实体不知道自己是谁的），只能按「谁站在旁边」算。
     * 已解锁的重复触发由原生成就系统吃掉，不需要额外判重。</p>
     */
    public static void unlockNearby(net.minecraft.world.World world, double x, double y, double z,
                                    double range, Achievement achievement) {

        if(world == null || world.isRemote || achievement == null) return;

        double rangeSq = range * range;

        for(Object entry : world.playerEntities) {

            EntityPlayer player = (EntityPlayer) entry;

            if(player != null && player.getDistanceSq(x, y, z) <= rangeSq) {
                unlock(player, achievement);
            }
        }
    }

    /**
     * 白钨矿累计挖掘数 +1，返回累加后的值。
     * 存在 {@code PlayerPersisted} 子标签里，死亡、换维度都不会丢。
     */
    public static int addScheeliteMined(EntityPlayer player) {

        NBTTagCompound persisted = player.getEntityData().getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG);
        int count = persisted.getInteger(NBT_SCHEELITE_MINED) + 1;
        persisted.setInteger(NBT_SCHEELITE_MINED, count);
        player.getEntityData().setTag(EntityPlayer.PERSISTED_NBT_TAG, persisted);
        return count;
    }

    /** 只读查询：当前玩家挖了多少块白钨矿 */
    public static int getScheeliteMined(EntityPlayer player) {
        return player.getEntityData().getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG)
                .getInteger(NBT_SCHEELITE_MINED);
    }

    /* ---------------------------- 构造小工具 ---------------------------- */

    /** 建一条成就并注册；parent 为 null 时按根成就处理。 */
    private static Achievement ach(String name, int col, int row, ItemStack icon, Achievement parent) {

        Achievement achievement = new Achievement("achievement." + name, name, col, row, icon, parent);

        if(parent == null) achievement.initIndependentStat();

        return achievement.registerStat();
    }

    private static ItemStack itemStack(Item item) {
        if(item == null) {
            MakeNTMGreatAgain.LOG.warn("[MNGA] 成就图标引用的物品是 null，先用纸代替");
            return new ItemStack(Items.paper);
        }
        return new ItemStack(item);
    }

    private static ItemStack blockStack(net.minecraft.block.Block block) {
        if(block == null) {
            MakeNTMGreatAgain.LOG.warn("[MNGA] 成就图标引用的方块是 null，先用纸代替");
            return new ItemStack(Items.paper);
        }
        return new ItemStack(block);
    }

    /** 流体罐图标：HBM 的 fluid_tank_full 用 meta 存流体 id */
    private static ItemStack fluidStack(int fluidId) {
        if(com.hbm.items.ModItems.fluid_tank_full == null) {
            MakeNTMGreatAgain.LOG.warn("[MNGA] fluid_tank_full 不存在，流体成就先用纸代替");
            return new ItemStack(Items.paper);
        }
        return new ItemStack(com.hbm.items.ModItems.fluid_tank_full, 1, fluidId);
    }

    private ModAchievements() { }
}
