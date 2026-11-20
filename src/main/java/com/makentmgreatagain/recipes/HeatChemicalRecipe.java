package com.makentmgreatagain.recipes;

import java.util.List;

import com.hbm.inventory.recipes.loader.GenericRecipe;
import com.hbm.util.BobMathUtil;
import com.hbm.util.i18n.I18nUtil;

import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;

/**
 * 带热量需求的化工厂配方。
 *
 * <p>只有大型催化化工厂会检查 {@link #requiredHeat}，本体化工厂仍然只按普通配方处理。
 * 机器需要先用电力把内部热量加热到这个值，之后才会开始加工；加工过程中还要额外耗电保温。</p>
 */
public class HeatChemicalRecipe extends GenericRecipe {

	/** 配方需要的内部热量（Tu），0 表示不需要加热 */
	public final int requiredHeat;
	/** 需要的催化剂等级：0 不需要 / 1 原版 / 2 铂 / 3 CMB */
	public final int requiredCatalyst;

	public HeatChemicalRecipe(String name, int requiredHeat) {
		this(name, requiredHeat, 0);
	}

	public HeatChemicalRecipe(String name, int requiredHeat, int requiredCatalyst) {
		super(name);
		this.requiredHeat = requiredHeat;
		this.requiredCatalyst = requiredCatalyst;
	}

	/** 配方提示：热量和催化剂紧跟在名字下面，排在时长、功耗前面。
	 *  覆写 print 而不是 header：旧版 HBM 的 GenericRecipe 没有 header 方法，
	 *  print 两版都有（新版 print 内部会调 header，这里在名字行后插入，顺序一致）。 */
	@Override
	public List<String> print() {
		List<String> list = super.print();

		int insertAt = list.isEmpty() ? 0 : 1;
		if(this.requiredCatalyst > 0) {
			list.add(insertAt, EnumChatFormatting.RED + I18nUtil.resolveKey("gui.recipe.catalyst") + ": " + catalystName());
		}
		if(this.requiredHeat > 0) {
			list.add(insertAt, EnumChatFormatting.RED + I18nUtil.resolveKey("gui.recipe.heat") + ": "
					+ BobMathUtil.getShortNumber(this.requiredHeat) + "TU");
		}

		return list;
	}

	private String catalystName() {

		ItemStack stack = this.requiredCatalyst >= 3
				? new ItemStack(com.makentmgreatagain.init.ModItems.cmb_converter)
				: this.requiredCatalyst == 2
						? new ItemStack(com.makentmgreatagain.init.ModItems.platinum_converter)
						: new ItemStack(com.hbm.items.ModItems.catalytic_converter);

		return stack.getDisplayName();
	}
}
