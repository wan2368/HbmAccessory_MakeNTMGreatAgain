package com.makentmgreatagain.hazard;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import com.hbm.handler.ArmorModHandler;
import com.hbm.hazard.HazardData;
import com.hbm.hazard.HazardEntry;
import com.hbm.hazard.HazardSystem;
import com.hbm.hazard.type.HazardTypeBase;
import com.hbm.hazard.type.HazardTypeBlinding;
import com.hbm.hazard.type.HazardTypeExplosive;
import com.hbm.hazard.type.HazardTypeHot;
import com.hbm.hazard.type.HazardTypeHydroactive;
import com.makentmgreatagain.MakeNTMGreatAgain;
import com.makentmgreatagain.items.ItemCompositeTank;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

/**
 * 复合材料储罐：拦截背包物品的「易燃易爆 / 遇水反应 / 自燃 / 致盲」词条。
 *
 * 为什么不能靠护甲判断：HBM 的 hazard 是「每件带词条的物品每 tick 对持有者触发一次」，
 * 原版的易燃易爆、遇水反应和自燃不检查护甲；致盲只检查头盔的防光能力。
 * 这些词条没有检查护腿改装件的接口。
 *
 * 做法：在 postInit 阶段遍历 HBM 的三张 hazard 注册表，把这四个类型实例换成子类。
 * 子类只改 onUpdate 的第一行——穿了这个配件就直接 return，否则原样交给父类。
 * 这样：
 *   - 词条等级、modifier、tooltip 全部原封不动（HazardEntry 只换了 type，baseLevel/mods 没动）；
 *   - 没装这个配件的玩家行为与本体完全一致；
 *   - 掉在地上的危险品走的是 updateEntity，不经过这里，仍然会炸 —— 这也符合"物品栏内"的定位。
 *
 * 依赖两个包私有字段（HazardData.entries、HazardEntry.type），HBM 一改名就会失效。
 * 所以整段都包在 try/catch 里：失败只打日志，配件退化成一件没有效果的护甲改装件，不会崩。
 */
public final class CompositeTankHazardGuard {

    private CompositeTankHazardGuard() { }

    /**
     * 玩家（或任意生物）的护腿槽里是否装着复合材料储罐。
     * 直接读 NBT，不走 ArmorModHandler.pryMod —— 那个方法在槽位为空时会顺手 removeMod，
     * 而这里是每 tick 每件危险品都要调一次的高频路径，不能让它反复改 NBT。
     */
    public static boolean isProtected(EntityLivingBase entity) {

        if(entity == null) return false;

        // 1.7.10 的装备槽编号：0 手持、1 靴、2 腿、3 胸、4 头
        ItemStack legs = entity.getEquipmentInSlot(2);
        if(legs == null || !legs.hasTagCompound()) return false;

        NBTTagCompound nbt = legs.getTagCompound();
        if(!nbt.hasKey(ArmorModHandler.MOD_COMPOUND_KEY)) return false;

        NBTTagCompound mods = nbt.getCompoundTag(ArmorModHandler.MOD_COMPOUND_KEY);
        String key = ArmorModHandler.MOD_SLOT_KEY + ArmorModHandler.legs_only;
        if(!mods.hasKey(key)) return false;

        NBTTagCompound modTag = mods.getCompoundTag(key);

        // 快路径：先比注册名字符串，命中才判类型。loadItemStackFromNBT 每次都要
        // 反序列化整个 NBT，而这里是每 tick 每件危险品都要调的高频路径
        if(modTag.hasKey("id", 8)) {
            Item byId = (Item) Item.itemRegistry.getObject(modTag.getString("id"));
            if(byId != null) return byId == com.makentmgreatagain.init.ModItems.composite_tank;
        }

        // id 缺失或查不到（理论不该发生）才走完整反序列化兜底
        ItemStack mod = ItemStack.loadItemStackFromNBT(modTag);
        return mod != null && mod.getItem() instanceof ItemCompositeTank;
    }

    /** 在 postInit 调用一次：把 HBM 已注册的 hazard 类型替换成带拦截的版本。 */
    @SuppressWarnings("unchecked")
    public static void install() {

        Field entriesField;
        Field typeField;

        try {
            entriesField = HazardData.class.getDeclaredField("entries");
            entriesField.setAccessible(true);
            typeField = HazardEntry.class.getDeclaredField("type");
            typeField.setAccessible(true);
        } catch(Throwable t) {
            MakeNTMGreatAgain.LOG.warn("[CompositeTank] HazardEntry.type 不可访问，复合材料储罐将没有拦截效果", t);
            return;
        }

        List<HazardData> all = new ArrayList<HazardData>();
        all.addAll(HazardSystem.oreMap.values());
        all.addAll(HazardSystem.itemMap.values());
        all.addAll(HazardSystem.stackMap.values());

        // 同一类型共用一个包装实例：hazard 类型本身无状态，没必要每条词条都 new 一个
        HazardTypeBase explosive = new GuardedExplosive();
        HazardTypeBase hydroactive = new GuardedHydroactive();
        HazardTypeBase hot = new GuardedHot();
        HazardTypeBase blinding = new GuardedBlinding();

        int scanned = 0;
        int replaced = 0;

        for(HazardData data : all) {

            List<HazardEntry> entries;
            try {
                entries = (List<HazardEntry>) entriesField.get(data);
            } catch(Throwable t) {
                continue;
            }
            if(entries == null) continue;

            for(HazardEntry entry : entries) {

                scanned++;

                // 用精确类比较而不是 instanceof：别的模组如果继承了这些类型并改过行为，
                // 我们不能把它一起包掉，那等于抹掉了对方的实现。
                Class<?> c = entry.getType().getClass();
                HazardTypeBase replacement = null;

                if(c == HazardTypeExplosive.class) replacement = explosive;
                else if(c == HazardTypeHydroactive.class) replacement = hydroactive;
                else if(c == HazardTypeHot.class) replacement = hot;
                else if(c == HazardTypeBlinding.class) replacement = blinding;

                if(replacement == null) continue;

                try {
                    typeField.set(entry, replacement);
                    replaced++;
                } catch(Throwable t) {
                    // 单条失败无所谓，继续处理下一条
                }
            }
        }

        MakeNTMGreatAgain.LOG.info("[CompositeTank] hazard 拦截已挂载：扫描 " + scanned + " 条词条，替换 " + replaced + " 条");
    }

    /* 只拦截装备者身上的背包物品触发，其余行为交给原版。 */

    static class GuardedExplosive extends HazardTypeExplosive {
        @Override
        public void onUpdate(EntityLivingBase target, float level, ItemStack stack) {
            if(isProtected(target)) return;
            super.onUpdate(target, level, stack);
        }
    }

    static class GuardedHydroactive extends HazardTypeHydroactive {
        @Override
        public void onUpdate(EntityLivingBase target, float level, ItemStack stack) {
            if(isProtected(target)) return;
            super.onUpdate(target, level, stack);
        }
    }

    static class GuardedHot extends HazardTypeHot {
        @Override
        public void onUpdate(EntityLivingBase target, float level, ItemStack stack) {
            if(isProtected(target)) return;
            super.onUpdate(target, level, stack);
        }
    }

    static class GuardedBlinding extends HazardTypeBlinding {
        @Override
        public void onUpdate(EntityLivingBase target, float level, ItemStack stack) {
            if(isProtected(target)) return;
            super.onUpdate(target, level, stack);
        }
    }
}
