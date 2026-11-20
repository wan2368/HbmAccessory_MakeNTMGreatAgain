package com.makentmgreatagain.achievement;

import com.makentmgreatagain.init.ModAchievements;
import com.makentmgreatagain.init.ModBlocks;
import com.makentmgreatagain.init.ModFluids;
import com.makentmgreatagain.init.ModItems;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.world.BlockEvent;

/**
 * 成就触发。
 *
 * <p>成就本身定义在 {@link ModAchievements}（走 Minecraft 原生成就系统），
 * 这里只负责「什么时候调 {@code ModAchievements.unlock}」。</p>
 *
 * <p>触发方式分两类：</p>
 * <ol>
 *   <li>能精确挂钩子的（挖矿、右键、悬浮）直接在事件里触发；</li>
 *   <li>机器产出的物品没法挂钩子，统一用「每 20 tick 扫一次背包」兜底 ——
 *       合成、离心、化工厂、热解炉、高炉全都覆盖，代价是每人每半秒一次 36 格遍历，可以忽略。</li>
 * </ol>
 *
 * <p>★ 只注册一个实例、但要挂两条总线：{@code PlayerInteractEvent} / {@code BlockEvent}
 * 属于 Forge 总线，{@code TickEvent.PlayerTickEvent} 属于 FML 总线。
 * 只注册其一的话，另一半触发点永远收不到（不报错，就是没反应）。</p>
 */
public class AchHandler {

    /** 扫背包的间隔（tick） */
    private static final int SCAN_INTERVAL = 20;

    /** 「我应该把世界泡在酸里。」的门槛：16 个钨酸 ≈ 10,000 mB 废酸 */
    public static final int WASTE_ACID_THRESHOLD = 16;

    /* ================= 挖矿 ================= */

    @SubscribeEvent
    public void onBlockBreak(BlockEvent.BreakEvent event) {

        if (event.world == null || event.world.isRemote) return;
        if (event.getPlayer() == null) return;

        // Duna 变体同样计入：湿法炼钨的进度不该因为矿长在火星上就不算
        if (event.block != ModBlocks.ore_scheelite && event.block != ModBlocks.ore_scheelite_duna) return;

        EntityPlayer player = event.getPlayer();

        // 假玩家（建筑机器人、区块加载器之类）没有真正的统计文件，也没有存档，
        // 直接跳过，免得把累计数写到临时实体上。
        if (!(player instanceof net.minecraft.entity.player.EntityPlayerMP)) return;

        // 第一次挖到 → 「芜！」
        ModAchievements.unlock(player, ModAchievements.scheelite_mined);

        // 累计 300 块 → 「这就是全部了」。
        // 计数存在玩家持久 NBT 里（见 ModAchievements.addScheeliteMined），死亡换维度都不丢。
        if (ModAchievements.addScheeliteMined(player) >= ModAchievements.SCHEELITE_TARGET) {
            ModAchievements.unlock(player, ModAchievements.scheelite_300);
        }
    }

    /* ================= 右键环境泵 ================= */

    @SubscribeEvent
    public void onInteract(PlayerInteractEvent event) {

        // 只认右键：1.7.10 的 action 里 LEFT_CLICK_BLOCK 左键挖掘也会走到这里，
        // 不过滤的话拿镐子敲环境泵也能解锁成就
        if (event.action != PlayerInteractEvent.Action.RIGHT_CLICK_AIR
                && event.action != PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK) return;
        if (event.world == null || event.world.isRemote) return;
        if (event.entityPlayer == null) return;

        // 1.7.10 的 PlayerInteractEvent 没有 block 字段，只有 x/y/z 坐标
        if (event.world.getBlock(event.x, event.y, event.z) != ModBlocks.environment_pump) return;

        ModAchievements.unlock(event.entityPlayer, ModAchievements.seawater);
    }

    /* ================= 放下大型机器 ================= */

    /**
     * 「大就是好」。多方块的核心块是普通 ItemBlock 放下的，PlaceEvent 会正常触发；
     * 机器自己 setBlock 出来的结构件不会再走这个事件，所以只认核心块本身。
     */
    @SubscribeEvent
    public void onBlockPlace(BlockEvent.PlaceEvent event) {

        if (event.world == null || event.world.isRemote) return;
        if (event.player == null) return;

        if (event.placedBlock == ModBlocks.machine_large_catalytic_chemical_factory
                || event.placedBlock == ModBlocks.machine_hybrid_catalytic_fraction_tower) {
            ModAchievements.unlock(event.player, ModAchievements.big_is_good);
        }

        // 「遗迹房地产」：在下界的日落六号内部放一张床
        if (event.placedBlock == net.minecraft.init.Blocks.bed) {
            checkRuinEstate(event.player, event.world, event.x, event.y, event.z);
        }
    }

    /**
     * 「遗迹房地产」：床放在下界日落六号的包围盒里。
     *
     * <p>范围直接用存档标记算，不扫方块：{@link com.makentmgreatagain.world.SunsetSixSavedData}
     * 存了结构中心 X/Z 与底座 Y，配上结构尺寸（9×30×10）就能框出整栋楼。
     * 比逐格比对结构方块可靠得多，也不受玩家拆掉几块墙的影响。</p>
     *
     * <p>没生成过（{@code generated == false}）直接返回——没有楼，就没有房地产。</p>
     */
    private void checkRuinEstate(EntityPlayer player, net.minecraft.world.World world, int x, int y, int z) {

        if (world.provider.dimensionId != com.makentmgreatagain.config.ModConfig.structureSunsetSixDimensionId) return;

        com.makentmgreatagain.world.SunsetSixSavedData data =
                com.makentmgreatagain.world.SunsetSixSavedData.get(world);
        if (!data.generated) return;

        // 存档记的是中心坐标，换算回包围盒（尺寸取选址核心里的常量，与实际结构一致）
        int halfX = SUNSET_SIZE_X / 2;
        int halfZ = SUNSET_SIZE_Z / 2;
        int minX = data.centerX - halfX;
        int maxX = minX + SUNSET_SIZE_X - 1;
        int minZ = data.centerZ - halfZ;
        int maxZ = minZ + SUNSET_SIZE_Z - 1;
        int maxY = data.baseY + com.makentmgreatagain.world.SunsetSixSite.STRUCT_HEIGHT - 1;

        if (x < minX || x > maxX || z < minZ || z > maxZ) return;
        if (y < data.baseY || y > maxY) return;

        ModAchievements.unlock(player, ModAchievements.ruin_estate);
    }

    /** 日落六号的平面尺寸（与结构 NBT 的 size 一致：9×30×10）。 */
    private static final int SUNSET_SIZE_X = 9;
    private static final int SUNSET_SIZE_Z = 10;

    /* ================= 背包扫描 ================= */

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {

        if (event.phase != TickEvent.Phase.END) return;

        EntityPlayer player = event.player;

        if (player == null || player.worldObj == null || player.worldObj.isRemote) return;
        if (player.ticksExisted % SCAN_INTERVAL != 0) return;

        scanInventory(player);
    }

    private void scanInventory(EntityPlayer player) {

        int acidCount = 0;

        acidCount += scan(player, player.inventory.mainInventory);
        acidCount += scan(player, player.inventory.armorInventory);

        if (acidCount >= WASTE_ACID_THRESHOLD) {
            ModAchievements.unlock(player, ModAchievements.waste_acid);
        }

        checkHelicopterElements(player);
    }

    /**
     * 「HeLiCoPtEr」：穿着耐摔合金全套，<b>且</b>背包里同时有锭 / 板 / 线 / 螺栓 / 管 / 壳六种形态。
     *
     * <p>六形态取的是加工链上的六个不同环节（锭→板→线→螺栓／管／壳），
     * 铸造板、焊接板、致密线这些同类变体不单独计数——否则光囤板就能凑齐，失去收集意义。</p>
     *
     * <p>护甲槽里的四件由「全套」判定负责，所以六形态只在主背包里找。</p>
     */
    private void checkHelicopterElements(EntityPlayer player) {

        if (!com.makentmgreatagain.hook.HelicopterArmorHandler.wearsFullSetForAchievement(player)) return;

        ItemStack[] inv = player.inventory.mainInventory;
        if (inv == null) return;

        boolean ingot = false, plate = false, wire = false, bolt = false, pipe = false, shell = false;

        for (ItemStack stack : inv) {
            if (stack == null || stack.getItem() == null) continue;
            net.minecraft.item.Item item = stack.getItem();

            if (item == ModItems.ingot_helicopter) ingot = true;
            else if (item == ModItems.plate_helicopter) plate = true;
            else if (item == ModItems.wire_helicopter) wire = true;
            else if (item == ModItems.bolt_helicopter) bolt = true;
            else if (item == ModItems.pipe_helicopter) pipe = true;
            else if (item == ModItems.shell_helicopter) shell = true;
        }

        if (ingot && plate && wire && bolt && pipe && shell) {
            ModAchievements.unlock(player, ModAchievements.helicopter_elements);
        }
    }

    /** @return 本组物品里钨酸的个数，供废酸门槛累计 */
    private int scan(EntityPlayer player, ItemStack[] inv) {

        if (inv == null) return 0;

        int acid = 0;

        for (ItemStack stack : inv) {

            if (stack == null || stack.getItem() == null) continue;

            if (stack.getItem() == ModItems.tungstic_acid) {
                acid += stack.stackSize;
                ModAchievements.unlock(player, ModAchievements.scheelite_acid);
            }

            if (stack.getItem() == ModItems.beryllium_concentrate) {
                ModAchievements.unlock(player, ModAchievements.beryllium_conc);
            }

            if (stack.getItem() == ModItems.arsenic_trioxide) {
                ModAchievements.unlock(player, ModAchievements.arsenic_cake);
            }

            if (stack.getItem() == ModItems.powder_zinc_isp) {
                ModAchievements.unlock(player, ModAchievements.zinc);
            }

            if (stack.getItem() == com.hbm.items.ModItems.nugget_arsenic) {
                ModAchievements.unlock(player, ModAchievements.arsenic_nugget);
            }

            if (stack.getItem() == com.hbm.items.ModItems.nothing) {
                ModAchievements.unlock(player, ModAchievements.nothing_smelt);
            }

            if (stack.getItem() == com.hbm.items.ModItems.ingot_tungsten_carbide) {
                ModAchievements.unlock(player, ModAchievements.tungsten_carbide);
            }

            if (stack.getItem() == com.hbm.items.ModItems.powder_lithium) {
                ModAchievements.unlock(player, ModAchievements.lithium);
            }

            if (stack.getItem() == com.hbm.items.ModItems.fluid_tank_full) {
                checkTank(player, stack);
            }

            if (stack.getItem() == ModItems.ingot_helium) {
                ModAchievements.unlock(player, ModAchievements.absolute_zero);
            }

            if (stack.getItem() == ModItems.ingot_helicopter) {
                ModAchievements.unlock(player, ModAchievements.helicopter);
            }

            if (stack.getItem() == net.minecraft.item.Item.getItemFromBlock(ModBlocks.deep_sea_nodule)) {
                ModAchievements.unlock(player, ModAchievements.sea_dredge);
            }
        }

        return acid;
    }

    /** 罐装流体：meta 就是 FluidType 的 id */
    private void checkTank(EntityPlayer player, ItemStack stack) {

        int id = stack.getItemDamage();

        if (id == ModFluids.SEA_WATER.id) {
            ModAchievements.unlock(player, ModAchievements.seawater);
            return;
        }

        if (com.hbm.inventory.fluid.Fluids.CHLORINE != null
                && id == com.hbm.inventory.fluid.Fluids.CHLORINE.getID()) {
            ModAchievements.unlock(player, ModAchievements.chlorine);
        }
    }
}
