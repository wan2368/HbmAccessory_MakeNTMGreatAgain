package com.makentmgreatagain.recipes;

import java.util.ArrayList;
import java.util.List;

import com.hbm.inventory.fluid.FluidType;
import com.hbm.inventory.fluid.Fluids;
import com.hbm.inventory.recipes.FusionRecipe;
import com.hbm.util.BobMathUtil;
import com.hbm.util.i18n.I18nUtil;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.util.EnumChatFormatting;

/**
 * 本模组聚变配方的简介：去掉宿主的「速调管能量输入」这一项。
 *
 * <p>速调管是 HBM 聚变堆的点火装置，等离子加热器 / 仿星器不用它；简介里只保留
 * <b>能量消耗、加热效率、启动耗时、约束力量、等离子体能量输出、中子通量、输入、输出</b>。
 * 能量消耗取配方自身的 {@code power}；加热效率按 {@link HydrogenFusion#heaterFeed(String)}
 * 的逻辑输入拼出来；启动耗时是每条配方自己的 {@code startupTicks}。</p>
 */
public class HydrogenFusionRecipe extends FusionRecipe {

	/** 约束力量（HE/t）：仿星器的内部储电上限 = 它的 100 倍 */
	private long confinementPower;

	/** 启动读条时长（tick），每条配方可以不同 */
	private int startupTicks = HydrogenFusion.STARTUP_TICKS;

	public HydrogenFusionRecipe(String name) {
		super(name);
	}

	public HydrogenFusionRecipe setConfinementPower(long power) {
		this.confinementPower = power;
		return this;
	}

	public long getConfinementPower() {
		return this.confinementPower;
	}

	public HydrogenFusionRecipe setStartupTicks(int ticks) {
		this.startupTicks = ticks;
		return this;
	}

	public int getStartupTicks() {
		return this.startupTicks;
	}

	@Override
	public List<String> print() {

		List<String> list = new ArrayList<String>();

		list.add(EnumChatFormatting.YELLOW + this.getLocalizedName());
		// 第一行：能量消耗（加热器造这份等离子体的电）
		power(list);
		// 第二行：加热效率（红色，只显示输入原料配方，不显示输出）
		String efficiency = this.heaterEfficiency();
		if(efficiency != null) list.add(EnumChatFormatting.RED + "加热效率: " + efficiency);
		list.add(EnumChatFormatting.AQUA + "启动耗时: " + this.startupTicks + "tick ("
				+ (this.startupTicks / 20) + "s)");
		list.add(EnumChatFormatting.AQUA + "约束力量: " + BobMathUtil.getShortNumber(this.confinementPower) + "HE/t");
		list.add(EnumChatFormatting.LIGHT_PURPLE + I18nUtil.resolveKey("gui.recipe.fusionOut") + ": "
				+ BobMathUtil.getShortNumber(this.outputTemp) + "TU/t");
		list.add(EnumChatFormatting.LIGHT_PURPLE + I18nUtil.resolveKey("gui.recipe.fusionFlux") + ": "
				+ ((int) (this.neutronFlux * 10)) / 10D + " flux/t");
		input(list);
		output(list);

		return list;
	}

	/**
	 * 加热效率那一行：按配方在加热器侧的进料拼，只显示原料输入，例如
	 * 「100mB 氢 / tick」、「100mB 氢 + 100mB 液态硼 / tick」。
	 */
	private String heaterEfficiency() {

		HydrogenFusion.HeaterFeed feed = HydrogenFusion.heaterFeed(this.getInternalName());
		if(feed == null) return null;

		StringBuilder sb = new StringBuilder();

		for(int i = 0; i < feed.inFluid.length; i++) {
			if(i > 0) sb.append(" + ");
			sb.append(feed.inAmount[i]).append("mB ").append(shortName(feed.inFluid[i]));
		}

		sb.append(" / tick");
		return sb.toString();
	}

	/** 氢在简介里简写成「氢」，其余用流体自己的本地化名 */
	private static String shortName(FluidType type) {
		if(type == Fluids.HYDROGEN) return "氢";
		return type.getLocalizedName();
	}

	/** NEI 右侧的小字：只显示启动耗时与能量消耗，不再轮播点火能量 / 循环耗时 */
	@Override
	public void printNEIExtras() {

		int side = 164;
		FontRenderer fontRenderer = Minecraft.getMinecraft().fontRenderer;

		String startup = (this.startupTicks / 20) + "s";
		fontRenderer.drawString(startup, side - fontRenderer.getStringWidth(startup), 45, 0x404040);

		String consumption = BobMathUtil.getShortNumber(this.power) + "HE/t";
		fontRenderer.drawString(consumption, side - fontRenderer.getStringWidth(consumption), 57, 0x404040);
	}
}
