package com.makentmgreatagain.items;

import java.util.List;
import java.util.Locale;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.EnumRarity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;

/**
 * 机器覆层：HBM 那四个 {@code fusion_shield_*} 遗留物的新用途。
 *
 * <p>原版的 {@code ItemFusionShield} 是「耐久 + 熔点」，插进聚变反应堆里挨烧掉耐久用的，
 * 但那个机制在 5852 里早就拆干净了（全库只有一个没人用的 import）。这里改成完全不同的东西：
 * <b>带内部流体容量的机器部件</b>，容量从 512,000 mB 逐级翻倍到 8,192,000 mB。</p>
 *
 * <p>目前只到「物品 + 配方」这一步，还没有任何机器去读它，所以除了容量之外不给任何属性。
 * 已经把 NBT 存取写好了（{@link #getStored}/{@link #setStored}，键 {@value #NBT_STORED}），
 * 以后接机器时直接拿来当储罐用，不用再动物品类。</p>
 */
public class ItemCoating extends ItemWithDesc {

	/** 内部流体量的 NBT 键。 */
	public static final String NBT_STORED = "mnga_coating_fluid";

	/** 容量（mB）。 */
	private final long capacity;
	/** 档位，1 最低。 */
	private final int tier;

	public ItemCoating(long capacity, int tier, EnumRarity rarity) {
		this.capacity = capacity;
		this.tier = tier;
		this.setRarity(rarity);
		this.setMaxStackSize(1);
	}

	public long getCapacity() {
		return this.capacity;
	}

	public int getTier() {
		return this.tier;
	}

	/* ------------------------------------------------------------------ 内部流体 */

	/** 已装量（mB）。没有 NBT 时返回 0。 */
	public static long getStored(ItemStack stack) {
		if(stack == null || !stack.hasTagCompound()) return 0L;
		NBTTagCompound nbt = stack.stackTagCompound;
		return nbt.hasKey(NBT_STORED) ? nbt.getLong(NBT_STORED) : 0L;
	}

	/** 写入已装量，自动夹在 0 ~ 容量之间。 */
	public static void setStored(ItemStack stack, long amount) {
		if(stack == null) return;

		long max = stack.getItem() instanceof ItemCoating
			? ((ItemCoating) stack.getItem()).getCapacity()
			: Long.MAX_VALUE;

		if(amount < 0L) amount = 0L;
		if(amount > max) amount = max;

		if(!stack.hasTagCompound()) stack.stackTagCompound = new NBTTagCompound();
		stack.stackTagCompound.setLong(NBT_STORED, amount);
	}

	/* ------------------------------------------------------------------ tooltip */

	@Override
	@SideOnly(Side.CLIENT)
	@SuppressWarnings("unchecked")
	public void addInformation(ItemStack stack, EntityPlayer player, List list, boolean advanced) {
		list.add(EnumChatFormatting.GRAY + StatCollector.translateToLocalFormatted(
			"desc.coating.capacity", format(this.capacity)));

		long stored = getStored(stack);
		if(stored > 0L) {
			list.add(EnumChatFormatting.GRAY + StatCollector.translateToLocalFormatted(
				"desc.coating.stored", format(stored)));
		}

		// 说明文字（item.<name>.desc），交给基类处理
		super.addInformation(stack, player, list, advanced);
	}

	/** 512000 → 512,000：容量都是六位数往上，不加分隔符根本读不出来。 */
	public static String format(long amount) {
		return String.format(Locale.US, "%,d", amount);
	}
}
