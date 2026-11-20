package com.makentmgreatagain.items;

import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;

/**
 * Threx 块的物品形态。放在背包任意一格时持续获得<b>缓慢 III</b>（比锭、粉重一级）。
 *
 * <p>必须单独建类：{@code ItemBlock} 已继承 {@code Item}，接不上
 * {@link ItemThrexSlowdown} 的基类，只能覆写 {@code onUpdate} 去调它的静态方法。</p>
 *
 * <p>Forge 用反射构造，所以必须保留单参 {@code Block} 构造。</p>
 */
public class ItemBlockThrex extends ItemBlock {

	/** amp 2 → 界面「缓慢 III」。 */
	private static final int AMPLIFIER = 2;

	public ItemBlockThrex(Block block) {
		super(block);
	}

	@Override
	public void onUpdate(ItemStack stack, World world, Entity entity, int slot, boolean isHeld) {
		ItemThrexSlowdown.tickSlowdown(world, entity, AMPLIFIER);
	}

	/**
	 * 抗爆数值，格式与 HBM 的 {@code com.hbm.items.block.ItemBlockBlastInfo} 逐字一致：
	 * 金色 + {@code Blast Resistance: } + 方块抗爆值。
	 *
	 * <p>HBM 的加固建材（CMB 钢瓦块、加固混凝土等）都有这一行，Threx 块抗爆 450000、
	 * 比加固 CMB 钢砖块高一个数量级，不写出来玩家看不出它凭什么值这么多锭。
	 * 取的是 {@code Block#getExplosionResistance(null)}，也就是 {@code setResistance(450000F)}
	 * 存进去的那个数。</p>
	 */
	@Override
	@SuppressWarnings("unchecked")
	public void addInformation(ItemStack stack, EntityPlayer player, List list, boolean advanced) {
		super.addInformation(stack, player, list, advanced);

		// 本类不能继承 ItemWithDesc（ItemBlock 已占用父类），
		// 所以这里自己按 $ 拆行，行为与 ItemWithDesc.addInformation 一致：
		// 键名 tile.<方块名>.desc，缺键时什么都不加。
		String key = this.field_150939_a.getUnlocalizedName() + ItemWithDesc.DESC_SUFFIX;
		String raw = StatCollector.translateToLocal(key);
		if(raw != null && !raw.isEmpty() && !raw.equals(key)) {
			for(String line : raw.split("\\$")) {
				if(!line.isEmpty()) list.add(line);
			}
		}

		list.add(EnumChatFormatting.GOLD + "Blast Resistance: "
			+ this.field_150939_a.getExplosionResistance(null));
	}
}
