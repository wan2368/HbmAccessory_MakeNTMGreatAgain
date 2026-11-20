package com.makentmgreatagain.items;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.hbm.handler.ArmorModHandler;
import com.hbm.hazard.HazardEntry;
import com.hbm.hazard.HazardSystem;
import com.hbm.hazard.type.HazardTypeBase;
import com.hbm.hazard.type.HazardTypeExplosive;
import com.hbm.hazard.type.HazardTypeHot;
import com.hbm.hazard.type.HazardTypeHydroactive;
import com.hbm.inventory.fluid.FluidType;
import com.hbm.inventory.fluid.Fluids;
import com.hbm.items.armor.ItemArmorMod;
import com.hbm.util.i18n.I18nUtil;

import api.hbm.fluidmk2.IFillableItem;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;

/**
 * 自动灭火器：装胸甲、占护腿槽的护甲改装件，内置 16,000 mB 二氧化碳罐。
 * 充装和矢量喷气背包同一条路：储罐右侧槽 4 进、槽 5 出，由 FluidLoaderFillableItem 逐个改装件填充。
 * 灭火把燃烧时间直接压到 0.5 秒余火；setFire 只延长不缩短，所以写 fire 字段。
 */
public class ItemAutoExtinguisher extends ItemArmorMod implements IFillableItem {

    private static final Logger LOG = LogManager.getLogger("MakeNTMGreatAgain");

    public static final int CAPACITY = 16_000;
    /** 每次喷射消耗的二氧化碳 */
    private static final int DOSE = 25;
    /** 检查周期，tick */
    private static final int INTERVAL = 4;
    /** 压制后保留的燃烧时间，tick。既是目标值也是停止补喷的门槛：10 tick 不会触发 %20 的灼烧伤害 */
    private static final int EMBER_TICKS = 10;
    /** 单次最多扑灭的火焰方块数，防止一次刷掉大片火场 */
    private static final int DOUSE_BUDGET = 4;
    /** 背包危险词条的缓存时长，tick */
    private static final int SCAN_INTERVAL = 20;

    private static final String KEY_FILL = "co2";
    private static final String KEY_TAG = "tag";

    private static final Field FIELD_FIRE = findFireField();

    private static final Map<EntityPlayer, ReactiveCache> CACHE = new WeakHashMap<EntityPlayer, ReactiveCache>();

    public ItemAutoExtinguisher() {
        super(ArmorModHandler.legs_only, false, true, false, false);
    }

    @Override
    public void modUpdate(EntityLivingBase entity, ItemStack armor) {

        if(entity.worldObj.isRemote) return;
        if(!(entity instanceof EntityPlayer)) return;

        EntityPlayer player = (EntityPlayer) entity;

        if(inLava(player)) return;
        if(player.ticksExisted % INTERVAL != 0) return;

        int fill = getArmorFill(armor);
        if(fill < DOSE) return;

        // 已经压到余火值就不再补喷：否则每 4 tick 把 fire 重置回 EMBER_TICKS，
        // 计数永远降不到 0，服务端 setFlag(0) 一直为真，客户端火焰特效不灭、气也不停。
        boolean burning = getFire(player) > EMBER_TICKS;
        int doused = carriesReactive(player) ? 0 : douseNearbyFire(player);
        if(!burning && doused <= 0) return;

        setArmorFill(armor, fill - DOSE);

        if(burning) dampFire(player);

        if(player.ticksExisted % 20 == 0) {
            player.worldObj.playSoundAtEntity(player, "random.fizz", 0.3F, 1.6F);
        }
    }

    private static boolean inLava(EntityPlayer player) {

        int x = MathHelper.floor_double(player.posX);
        int z = MathHelper.floor_double(player.posZ);
        int y = MathHelper.floor_double(player.posY + 0.1D);

        for(int dy = 0; dy <= 1; dy++) {
            Block block = player.worldObj.getBlock(x, y + dy, z);
            if(block == Blocks.lava || block == Blocks.flowing_lava) return true;
        }

        return false;
    }

    /** 1.7.10 的 Entity.fire 是 private，直接写才能缩短燃烧时间 */
    private static Field findFireField() {

        for(String name : new String[] {"fire", "field_70151_ae"}) {
            try {
                Field field = Entity.class.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch(Throwable ignored) {}
        }

        LOG.warn("[AutoExtinguisher] Entity.fire not found, fallback to extinguish()");
        return null;
    }

    private static void dampFire(EntityPlayer player) {

        if(FIELD_FIRE != null) {
            try {
                FIELD_FIRE.setInt(player, EMBER_TICKS);
                return;
            } catch(Throwable ignored) {}
        }

        player.extinguish();
    }

    /** 读 Entity.fire；反射不可用时退化成 isBurning，走 extinguish() 那条完整熄灭的路 */
    private static int getFire(EntityPlayer player) {

        if(FIELD_FIRE == null) return player.isBurning() ? Integer.MAX_VALUE : 0;

        try {
            return FIELD_FIRE.getInt(player);
        } catch(Throwable ignored) {
            return 0;
        }
    }

    /**
     * 扑灭碰撞箱外扩 1 格的火焰方块，最多 DOUSE_BUDGET 个。
     * 扫描量固定在 3×3×4 左右，单次不到 40 次 getBlock，可以忽略。
     */
    private static int douseNearbyFire(EntityPlayer player) {

        World world = player.worldObj;
        AxisAlignedBB bb = player.boundingBox;

        int minX = MathHelper.floor_double(bb.minX) - 1;
        int maxX = MathHelper.floor_double(bb.maxX) + 1;
        int minZ = MathHelper.floor_double(bb.minZ) - 1;
        int maxZ = MathHelper.floor_double(bb.maxZ) + 1;
        int minY = MathHelper.floor_double(bb.minY) - 1;
        int maxY = MathHelper.floor_double(bb.maxY) + 1;

        int doused = 0;

        for(int x = minX; x <= maxX; x++) {
            for(int z = minZ; z <= maxZ; z++) {
                for(int y = minY; y <= maxY; y++) {

                    if(world.getBlock(x, y, z) != Blocks.fire) continue;

                    world.setBlockToAir(x, y, z);
                    if(++doused >= DOUSE_BUDGET) return doused;
                }
            }
        }

        return doused;
    }

    /** 背包里带着易燃易爆 / 遇水反应 / 自燃时不做方块灭火，否则会被反复点燃变成空转 */
    private static boolean carriesReactive(EntityPlayer player) {

        ReactiveCache cache = CACHE.get(player);

        if(cache != null && player.ticksExisted >= cache.tick && player.ticksExisted - cache.tick < SCAN_INTERVAL) {
            return cache.reactive;
        }

        boolean reactive = scanReactive(player);
        CACHE.put(player, new ReactiveCache(player.ticksExisted, reactive));
        return reactive;
    }

    private static boolean scanReactive(EntityPlayer player) {

        for(ItemStack stack : player.inventory.mainInventory) {
            if(hasReactiveHazard(stack)) return true;
        }

        for(ItemStack stack : player.inventory.armorInventory) {
            if(hasReactiveHazard(stack)) return true;
        }

        return false;
    }

    private static boolean hasReactiveHazard(ItemStack stack) {

        if(stack == null) return false;

        List<HazardEntry> entries = HazardSystem.getHazardsFromStack(stack);

        for(HazardEntry entry : entries) {
            HazardTypeBase type = entry.getType();
            if(type instanceof HazardTypeExplosive) return true;
            if(type instanceof HazardTypeHydroactive) return true;
            if(type instanceof HazardTypeHot) return true;
        }

        return false;
    }

    private static final class ReactiveCache {

        final int tick;
        final boolean reactive;

        ReactiveCache(int tick, boolean reactive) {
            this.tick = tick;
            this.reactive = reactive;
        }
    }

    /** 直接读写护甲 NBT 里本插件的数据，槽位跟着 this.type 走 */
    private NBTTagCompound getArmorTag(ItemStack armor, boolean create) {

        if(armor == null || !ArmorModHandler.hasMods(armor)) return null;

        NBTTagCompound mods = armor.getTagCompound().getCompoundTag(ArmorModHandler.MOD_COMPOUND_KEY);
        NBTTagCompound cmp = mods.getCompoundTag(ArmorModHandler.MOD_SLOT_KEY + this.type);

        if(cmp.hasKey(KEY_TAG, 10)) return cmp.getCompoundTag(KEY_TAG);
        if(!create) return null;

        NBTTagCompound tag = new NBTTagCompound();
        cmp.setTag(KEY_TAG, tag);
        return tag;
    }

    private int getArmorFill(ItemStack armor) {
        NBTTagCompound tag = getArmorTag(armor, false);
        return tag == null ? 0 : tag.getInteger(KEY_FILL);
    }

    private void setArmorFill(ItemStack armor, int fill) {
        NBTTagCompound tag = getArmorTag(armor, true);
        if(tag == null) return;
        tag.setInteger(KEY_FILL, Math.max(0, Math.min(CAPACITY, fill)));
    }

    @Override
    public boolean acceptsFluid(FluidType type, ItemStack stack) {
        return type == Fluids.CARBONDIOXIDE && getFill(stack) < CAPACITY;
    }

    @Override
    public int tryFill(FluidType type, int amount, ItemStack stack) {

        if(!acceptsFluid(type, stack)) return amount;

        int add = Math.min(amount, CAPACITY - getFill(stack));
        if(add <= 0) return amount;

        setFill(stack, getFill(stack) + add);
        return amount - add;
    }

    @Override
    public boolean providesFluid(FluidType type, ItemStack stack) {
        return false;
    }

    @Override
    public int tryEmpty(FluidType type, int amount, ItemStack stack) {
        return 0;
    }

    @Override
    public FluidType getFirstFluidType(ItemStack stack) {
        return getFill(stack) > 0 ? Fluids.CARBONDIOXIDE : null;
    }

    @Override
    public int getFill(ItemStack stack) {

        if(stack == null || stack.getTagCompound() == null) return 0;
        return stack.getTagCompound().getInteger(KEY_FILL);
    }

    private static void setFill(ItemStack stack, int fill) {

        if(stack.getTagCompound() == null) stack.setTagCompound(new NBTTagCompound());
        stack.getTagCompound().setInteger(KEY_FILL, Math.max(0, Math.min(CAPACITY, fill)));
    }

    @Override
    public void addInformation(ItemStack stack, EntityPlayer player, List list, boolean bool) {

        super.addInformation(stack, player, list, bool);

        list.add(EnumChatFormatting.GRAY + I18nUtil.resolveKey("desc.auto_extinguisher.1"));
        // .2 一直写在语言文件里却没被调用，玩家找不到怎么补气
        list.add(EnumChatFormatting.GRAY + I18nUtil.resolveKey("desc.auto_extinguisher.2"));
        list.add(EnumChatFormatting.GRAY + I18nUtil.resolveKey("desc.auto_extinguisher.4"));
        list.add(EnumChatFormatting.GRAY + I18nUtil.resolveKey("desc.auto_extinguisher.3")
                + " " + getFill(stack) + " / " + CAPACITY + " mB");
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addDesc(List list, ItemStack stack, ItemStack armor) {
        list.add(EnumChatFormatting.GRAY + "  " + I18nUtil.resolveKey("desc.auto_extinguisher.3")
                + " " + getFill(stack) + " / " + CAPACITY + " mB");
    }
}
