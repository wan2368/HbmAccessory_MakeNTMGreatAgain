package com.makentmgreatagain.hook;

import com.makentmgreatagain.config.GlobalConfig;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

/**
 * 「长流程模式」的判定入口，给 coremod 补丁（BedrockOreGuardTransformer）调用。
 *
 * <p>参数一律用 Object：coremod 由系统类加载器加载，补丁里不能出现
 * net.minecraft / com.hbm 的混淆类型名（dev 与生产环境名字不同），
 * 用 Object 传引用可以绕过描述符差异，字节码里也不需要 CHECKCAST。</p>
 *
 * <p>开关关闭时永远返回 true（不干预原版行为）。开启后屏蔽基岩矿石的生成，
 * 唯独放行浮冰基岩矿 —— 航天版各星球的浮冰分支传进来的 ItemStack 就是 packed_ice，
 * 按物品判据豁免，不依赖维度 ID，所以地球、下界、所有星球一次全覆盖。</p>
 */
public final class LongProcessHook {

    /** 诊断：每种物品最多打 3 条，避免世界生成刷屏。排查完可整段删掉。 */
    private static final java.util.Map<String, Integer> LOGGED = new java.util.HashMap<String, Integer>();

    private LongProcessHook() {
    }

    /**
     * 基岩矿石这一次能否生成。
     *
     * @param worldObj 实际是 World，这里不参与判定，仅保留以便日后按维度细化
     * @param stackObj 实际是 ItemStack，即这处基岩矿将要产出的东西
     */
    public static boolean allowBedrockOre(Object worldObj, Object stackObj) {
        if (!GlobalConfig.longProcessMode && !com.hbm.config.GeneralConfig.enable528) return true;
        boolean ice = isPackedIce(stackObj);
        diag(stackObj, worldObj, ice);
        return ice;
    }

    /** 排查用日志：打印闸门收到的资源物品、所在维度和放行结果。 */
    private static void diag(Object stackObj, Object worldObj, boolean allowed) {
        try {
            String name = "null";
            if (stackObj instanceof ItemStack) {
                Item item = ((ItemStack) stackObj).getItem();
                if (item != null) {
                    Object reg = Item.itemRegistry.getNameForObject(item);
                    name = String.valueOf(reg) + " meta=" + ((ItemStack) stackObj).getItemDamage();
                }
            }
            String dim = " ?";
            if (worldObj instanceof net.minecraft.world.World) {
                dim = " dim=" + ((net.minecraft.world.World) worldObj).provider.dimensionId;
            }
            String key = (allowed ? "ALLOW " : "DENY  ") + name;
            int n;
            Integer seen = LOGGED.get(key);
            if (seen == null) { n = 0; } else { n = seen; }
            if (n < 3) {
                LOGGED.put(key, n + 1);
                // 走模组 logger 而不是 System.out：诊断日志不该混进发布版控制台
                com.makentmgreatagain.MakeNTMGreatAgain.LOG.debug("[longprocess] gate" + dim + " -> " + key);
            }
        } catch (Throwable ignored) {
            // 诊断失败绝不影响判定本身
        }
    }

    /** 产物是不是浮冰：浮冰基岩矿豁免屏蔽。 */
    private static boolean isPackedIce(Object stackObj) {
        if (!(stackObj instanceof ItemStack)) return false;
        Item item = ((ItemStack) stackObj).getItem();
        if (item == null) return false;
        Block ice = Blocks.packed_ice;
        return ice != null && item == Item.getItemFromBlock(ice);
    }
}
