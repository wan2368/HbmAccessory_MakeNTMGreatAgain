package com.makentmgreatagain.achievement;

import com.makentmgreatagain.init.ModBlocks;
import com.makentmgreatagain.init.ModFluids;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent.PlayerLoggedInEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraftforge.event.entity.EntityEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.world.BlockEvent;

/**
 * 成就触发。
 *
 * 触发方式分两类：
 *  1. 能精确挂钩子的（挖矿、右键、悬浮背包）直接调 AchManager.unlock；
 *  2. 机器产出的物品没法挂钩子，统一用「每 20 tick 扫一次背包」兜底。
 *     扫背包能覆盖合成、离心、化工厂、热解炉等所有来源，代价是每条玩家每半秒一次
 *     36 格遍历——开销可以忽略。
 *
 * 同一个实例同时注册到 Forge 总线和 FML 总线：
 * PlayerTickEvent / PlayerLoggedInEvent 属于 FML，注册到 Forge 总线收不到。
 */
public class AchHandler {

    /** 扫背包的间隔（tick） */
    private static final int SCAN_INTERVAL = 20;

    /** 「这就是全部了」的门槛 */
    public static final int SCHEELITE_TARGET = 300;

    /** 「我应该把世界泡在酸里。」的门槛：16 个钨酸 ≈ 10,000 mB 废酸 */
    public static final int WASTE_ACID_THRESHOLD = 16;

    /* ================= 存档注册 ================= */

    @SubscribeEvent
    public void onEntityConstructing(EntityEvent.EntityConstructing event) {

        if (event.entity instanceof EntityPlayer) {
            EntityPlayer player = (EntityPlayer) event.entity;
            if (player.getExtendedProperties(PlayerAchievements.KEY) == null) {
                player.registerExtendedProperties(PlayerAchievements.KEY, new PlayerAchievements(player));
            }
        }
    }

    /* ================= 登录全量同步 ================= */

    @SubscribeEvent
    public void onLoggedIn(PlayerLoggedInEvent event) {

        if (event.player instanceof EntityPlayerMP) {
            AchManager.syncFull((EntityPlayerMP) event.player);
        }
    }

    /* ================= 挖矿 ================= */

    @SubscribeEvent
    public void onBlockBreak(BlockEvent.BreakEvent event) {

        if (event.world == null || event.world.isRemote) return;
        if (event.getPlayer() == null) return;

        if (event.block != ModBlocks.ore_scheelite) return;

        EntityPlayer player = event.getPlayer();

        AchManager.unlock(player, AchRegistry.SCHEELITE_MINED);

        PlayerAchievements props = PlayerAchievements.get(player);
        if (props == null) return;

        props.scheeliteMined++;

        if (props.scheeliteMined >= SCHEELITE_TARGET) {
            AchManager.unlock(player, AchRegistry.SCHEELITE_300);
        }
    }

    /* ================= 右键环境泵 ================= */

    @SubscribeEvent
    public void onInteract(PlayerInteractEvent event) {

        if (event.world == null || event.world.isRemote) return;
        if (event.entityPlayer == null) return;

        // 1.7.10 的 PlayerInteractEvent 没有 block 字段，只有 x/y/z 坐标
        if (event.world.getBlock(event.x, event.y, event.z) != ModBlocks.environment_pump) return;

        AchManager.unlock(event.entityPlayer, AchRegistry.SEAWATER);
    }

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
            AchManager.unlock(player, AchRegistry.WASTE_ACID);
        }
    }

    /** @return 本组物品里钨酸的个数，供废酸门槛累计 */
    private int scan(EntityPlayer player, ItemStack[] inv) {

        if (inv == null) return 0;

        int acid = 0;

        for (ItemStack stack : inv) {

            if (stack == null || stack.getItem() == null) continue;

            if (stack.getItem() == com.makentmgreatagain.init.ModItems.tungstic_acid) {
                acid += stack.stackSize;
                AchManager.unlock(player, AchRegistry.SCHEELITE_ACID);
            }

            if (stack.getItem() == com.makentmgreatagain.init.ModItems.beryllium_concentrate) {
                AchManager.unlock(player, AchRegistry.BERYLLIUM_CONC);
            }

            if (stack.getItem() == com.makentmgreatagain.init.ModItems.arsenic_trioxide) {
                AchManager.unlock(player, AchRegistry.ARSENIC_CAKE);
            }

            if (stack.getItem() == com.makentmgreatagain.init.ModItems.powder_zinc_isp) {
                AchManager.unlock(player, AchRegistry.ZINC);
            }

            if (stack.getItem() == com.hbm.items.ModItems.nugget_arsenic) {
                AchManager.unlock(player, AchRegistry.ARSENIC_NUGGET);
            }

            if (stack.getItem() == com.hbm.items.ModItems.nothing) {
                AchManager.unlock(player, AchRegistry.NOTHING_SMELT);
            }

            if (stack.getItem() == com.hbm.items.ModItems.ingot_tungsten_carbide) {
                AchManager.unlock(player, AchRegistry.TUNGSTEN_CARBIDE);
            }

            if (stack.getItem() == com.hbm.items.ModItems.powder_lithium) {
                AchManager.unlock(player, AchRegistry.LITHIUM);
            }

            if (stack.getItem() == com.hbm.items.ModItems.fluid_tank_full) {
                checkTank(player, stack);
            }
        }

        return acid;
    }

    /** 罐装流体：meta 就是 FluidType 的 id */
    private void checkTank(EntityPlayer player, ItemStack stack) {

        int id = stack.getItemDamage();

        if (id == ModFluids.SEA_WATER.id) {
            AchManager.unlock(player, AchRegistry.SEAWATER);
            return;
        }

        if (com.hbm.inventory.fluid.Fluids.CHLORINE != null
                && id == com.hbm.inventory.fluid.Fluids.CHLORINE.getID()) {
            AchManager.unlock(player, AchRegistry.CHLORINE);
        }
    }
}
