package com.makentmgreatagain.recipes;

import java.util.ArrayList;
import java.util.List;

import com.hbm.inventory.FluidStack;
import com.hbm.inventory.RecipesCommon.AStack;
import com.hbm.inventory.recipes.loader.GenericRecipe;
import com.hbm.inventory.recipes.loader.GenericRecipes.IOutput;
import com.hbm.util.i18n.I18nUtil;

import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;

/**
 * MF 合并出来的虚拟配方。
 *
 * <p>继承 {@link HeatChemicalRecipe} 而不是普通 {@code GenericRecipe}：机器是靠
 * {@code instanceof HeatChemicalRecipe} 读热量与催化剂等级的，用普通类型会<b>丢掉热量需求</b>，
 * 表现为「选了带热量的配方却不显示热量、也永远处理不了」。</p>
 *
 * <h2>提示排版</h2>
 * <p>输入/输出各自<b>均分成两列</b>并排显示，避免竖着拉太长。
 * 标题固定为「混合配方」，图标按已选配方<b>每 0.75 秒轮换</b>一个。</p>
 */
public class MergedHeatRecipe extends HeatChemicalRecipe {

	/** 图标轮换间隔（毫秒） */
	private static final long ICON_CYCLE_MS = 750L;

	/** 参与合并的原配方，用来轮播图标 */
	private final List<GenericRecipe> parts;

	public MergedHeatRecipe(String name, int heat, int catalyst, List<GenericRecipe> parts) {
		super(name, heat, catalyst);
		this.parts = parts;
	}

	/** 标题固定「混合配方」，不随轮播图标变化 */
	@Override
	public String getLocalizedName() {
		return "混合配方";
	}

	/** 按时间轮播已选配方的图标，每个 0.75 秒 */
	@Override
	public ItemStack getIcon() {
		if(parts == null || parts.isEmpty()) return super.getIcon();
		int index = (int) ((System.currentTimeMillis() / ICON_CYCLE_MS) % parts.size());
		ItemStack icon = parts.get(index).getIcon();
		return icon != null ? icon : super.getIcon();
	}

	@Override
	protected void header(List<String> list) {
		list.add(EnumChatFormatting.YELLOW + getLocalizedName());

		// 热量/催化剂不在这里画：父类 HeatChemicalRecipe.print() 已按合并后的
		// 最高热量/最高催化剂等级插入一组，这里再画就重复成两组。
		// 多个子配方需要热量/催化剂时，只显示等级最高的那一组。

		// 列出参与合并的配方名，两列排布避免框太长挡视野
		if(parts != null && !parts.isEmpty()) {
			List<String> names = new ArrayList<String>();
			for(GenericRecipe part : parts) {
				names.add(EnumChatFormatting.DARK_GRAY + part.getLocalizedName());
			}
			twoColumns(list, names);
		}
	}

	/** 输入：均分两列并排 */
	@Override
	protected void input(List<String> list) {
		list.add(EnumChatFormatting.BOLD + I18nUtil.resolveKey("gui.recipe.input") + ":");
		twoColumns(list, inputLines());
	}

	/** 输出：均分两列并排 */
	@Override
	protected void output(List<String> list) {
		list.add(EnumChatFormatting.BOLD + I18nUtil.resolveKey("gui.recipe.output") + ":");
		twoColumns(list, outputLines());
	}

	private List<String> inputLines() {
		List<String> lines = new ArrayList<String>();
		if(inputItem != null) {
			for(AStack stack : inputItem) {
				if(stack == null) continue;
				ItemStack display = stack.extractForCyclingDisplay(20);
				lines.add(EnumChatFormatting.GRAY + "" + display.stackSize + "x " + display.getDisplayName());
			}
		}
		if(inputFluid != null) {
			for(FluidStack fluid : inputFluid) {
				if(fluid == null || fluid.type == null) continue;
				lines.add(EnumChatFormatting.BLUE + "" + fluid.fill + "mB " + fluid.type.getLocalizedName()
						+ pressurePart(fluid));
			}
		}
		return lines;
	}

	private List<String> outputLines() {
		List<String> lines = new ArrayList<String>();
		if(outputItem != null) {
			for(IOutput out : outputItem) {
				if(out == null) continue;
				for(String line : out.getLabel()) lines.add(line);
			}
		}
		if(outputFluid != null) {
			for(FluidStack fluid : outputFluid) {
				if(fluid == null || fluid.type == null) continue;
				lines.add(EnumChatFormatting.BLUE + "" + fluid.fill + "mB " + fluid.type.getLocalizedName()
						+ pressurePart(fluid));
			}
		}
		return lines;
	}

	private String pressurePart(FluidStack fluid) {
		return fluid.pressure == 0 ? ""
				: " " + I18nUtil.resolveKey("gui.recipe.atPressure") + " "
						+ EnumChatFormatting.RED + fluid.pressure + " PU";
	}

	/**
	 * 把条目均分成左右两列并排：前半在上半行、后半在下半行，逐行拼接。
	 *
	 * <p>用空格补齐左列宽度对齐。MC 字体非等宽，这里按字符数近似补，
	 * 视觉上够整齐；条目数为奇数时左列多一个。</p>
	 */
	private void twoColumns(List<String> target, List<String> lines) {
		if(lines.isEmpty()) return;

		if(lines.size() == 1) {
			target.add("  " + lines.get(0));
			return;
		}

		int half = (lines.size() + 1) / 2;

		// 左列最长显示宽度（去掉颜色码后按字符数算）
		int width = 0;
		for(int i = 0; i < half; i++) {
			width = Math.max(width, plainLength(lines.get(i)));
		}

		for(int i = 0; i < half; i++) {
			String left = lines.get(i);
			int rightIndex = i + half;

			if(rightIndex >= lines.size()) {
				target.add("  " + left);
				continue;
			}

			int pad = width - plainLength(left) + 2;
			StringBuilder sb = new StringBuilder("  ").append(left);
			for(int p = 0; p < pad; p++) sb.append(' ');
			sb.append(EnumChatFormatting.RESET).append(lines.get(rightIndex));
			target.add(sb.toString());
		}
	}

	/** 去掉 §颜色码后的字符数，用来近似对齐 */
	private int plainLength(String s) {
		int n = 0;
		for(int i = 0; i < s.length(); i++) {
			if(s.charAt(i) == '\u00a7') { i++; continue; }
			n++;
		}
		return n;
	}
}