package com.makentmgreatagain.blocks;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.hbm.blocks.BlockDummyable;
import com.hbm.blocks.ILookOverlay;
import com.hbm.blocks.ITooltipProvider;
import com.hbm.inventory.fluid.Fluids;
import com.hbm.tileentity.TileEntityProxyCombo;
import com.hbm.util.BobMathUtil;
import com.hbm.util.i18n.I18nUtil;
import com.makentmgreatagain.tileentity.TileEntityEnvironmentPump;
import com.makentmgreatagain.util.EnvironmentPumpMedia;
import com.makentmgreatagain.util.EnvironmentPumpMedia.Medium;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.block.material.Material;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraftforge.client.event.RenderGameOverlayEvent.Pre;
import net.minecraftforge.common.util.ForgeDirection;

import org.lwjgl.opengl.GL11;

/**
 * 环境地下泵。
 *
 * 结构与 HBM 的 MachinePump 一致（3×1×1 多方块、四面端口），但方块实体换成按群系换介质的实现。
 * 不能继承 MachinePump：它的 createNewTileEntity 用 `this == ModBlocks.pump_electric` 判断，
 * 子类的 this 永远不等于那两个静态字段，会返回 null 导致方块摆下去是空壳。
 */
public class BlockEnvironmentPump extends BlockDummyable implements ITooltipProvider, ILookOverlay {

	public BlockEnvironmentPump() {
		super(Material.iron);
	}

	@Override
	public TileEntity createNewTileEntity(World world, int meta) {
		if(meta >= 12) return new TileEntityEnvironmentPump();
		if(meta >= 6) return new TileEntityProxyCombo().fluid().power();
		return null;
	}

	@Override
	public int[] getDimensions() {
		return new int[] {3, 0, 1, 1, 1, 1};
	}

	@Override
	public int getOffset() {
		return 1;
	}

	@Override
	public int[][] getAllPorts(ForgeDirection dir) {
		return rotatePorts(new int[][] {
			{1, 0, 0, ForgeDirection.EAST.ordinal()},
			{-1, 0, 0, ForgeDirection.WEST.ordinal()},
			{0, 0, 1, ForgeDirection.SOUTH.ordinal()},
			{0, 0, -1, ForgeDirection.NORTH.ordinal()}
		}, dir);
	}

	@Override
	public void fillSpace(World world, int x, int y, int z, ForgeDirection dir, int o) {
		super.fillSpace(world, x, y, z, dir, o);
		for(int[] offset : getAllPorts(dir)) {
			this.makeExtra(world, x + offset[0], y + offset[1], z + offset[2]);
		}
	}

	@Override
	public void addInformation(ItemStack stack, EntityPlayer player, List list, boolean ext) {
		this.addStandardInfo(stack, player, list, ext);
	}

	@Override
	public void printHook(Pre event, World world, int x, int y, int z) {

		int[] pos = this.findCore(world, x, y, z);
		if(pos == null) return;

		TileEntity te = world.getTileEntity(pos[0], pos[1], pos[2]);
		if(!(te instanceof TileEntityEnvironmentPump)) return;

		TileEntityEnvironmentPump pump = (TileEntityEnvironmentPump) te;
		Medium m = pump.getMedium();
		List<String> text = new ArrayList<String>();

		int[] band = EnvironmentPumpMedia.heightBand(world);
		int steps = EnvironmentPumpMedia.penaltySteps(world, pos[1]);
		long cost = pump.getEffectivePower();
		BiomeGenBase biome = world.getBiomeGenForCoords(pos[0], pos[2]);
		String biomeName = biome == null ? "-" : biome.biomeName;

		// 群系与开采等级
		text.add(color(0x55ffff) + I18nUtil.resolveKey("info.environment_pump.biome") + ": " + biomeName
				+ "    " + I18nUtil.resolveKey("info.environment_pump.tier") + ": " + roman(m.tier));

		// 输入介质（数值已含高度惩罚加成）
		if(pump.inputTanks[0].getTankType() != Fluids.NONE && m.inputAmount > 0) {
			text.add(color(0x55ff55) + "-> " + pump.inputTanks[0].getTankType().getLocalizedName() + ": "
					+ fmt(pump.inputTanks[0].getFill()) + " / " + fmt(pump.inputTanks[0].getMaxFill()) + " mB"
					+ "   -" + fmt(pump.getEffectiveInputAmount()) + " mB/5s");
		} else {
			text.add(color(0x555555) + I18nUtil.resolveKey("info.environment_pump.noinput"));
		}

		// 主产物（数值已含高度惩罚折减）
		text.add(color(0xff5555) + "<- " + pump.outputTanks[0].getTankType().getLocalizedName() + ": "
				+ fmt(pump.outputTanks[0].getFill()) + " / " + fmt(pump.outputTanks[0].getMaxFill()) + " mB"
				+ "   +" + fmt(pump.getEffectiveOutputAmount()) + " mB/5s");

		// 副产物
		if(pump.outputTanks[1].getTankType() != Fluids.NONE) {
			text.add(color(0xff9955) + "<- " + pump.outputTanks[1].getTankType().getLocalizedName() + ": "
					+ fmt(pump.outputTanks[1].getFill()) + " / " + fmt(pump.outputTanks[1].getMaxFill()) + " mB"
					+ "   +" + fmt(pump.getEffectiveOutput2Amount()) + " mB/5s");
		}

		// 电力：缩写为 k / M，否则整行会顶出屏幕。括号里是未折算等级系数的原始耗电
		text.add(color(0xffff55) + I18nUtil.resolveKey("info.environment_pump.power") + ": "
				+ fmtPower(pump.getPower()) + " / " + fmtPower(TileEntityEnvironmentPump.MAX_POWER) + " HE   "
				+ fmtPower(cost) + " HE/t (" + I18nUtil.resolveKey("info.environment_pump.base_power") + " "
				+ fmtPower(m.basePower) + ")");

		// 高度惩罚：电力、输入、产出三条一起压，每档各 5%
		if(steps > 0) {
			int powerPen = (int) Math.round((EnvironmentPumpMedia.penaltyMultiplier(world, pos[1]) - 1D) * 100);
			int flowPen = steps * 5;
			int cutPen = Math.min(100, steps * 5);
			text.add("&[" + (BobMathUtil.getBlink() ? 0xff0000 : 0xffff00) + "&]"
					+ I18nUtil.resolveKey("info.environment_pump.height") + ": Y=" + pos[1]
					+ "   " + I18nUtil.resolveKey("info.environment_pump.pen_power") + " +" + powerPen + "%"
					+ "   " + I18nUtil.resolveKey("info.environment_pump.pen_input") + " +" + flowPen + "%"
					+ "   " + I18nUtil.resolveKey("info.environment_pump.pen_output") + " -" + cutPen + "%"
					+ "   (" + band[0] + "~" + band[1] + ", " + I18nUtil.resolveKey("info.environment_pump.offby") + " " + steps + ")");
		} else {
			text.add(color(0x55ff55) + I18nUtil.resolveKey("info.environment_pump.height") + ": Y=" + pos[1]
					+ "   " + I18nUtil.resolveKey("info.environment_pump.nopenalty"));
		}

		// 状态
		text.add(color(0xaaaaaa) + I18nUtil.resolveKey("info.environment_pump.state") + ": " + status(pump));

		drawInfo(event, I18nUtil.resolveKey(getUnlocalizedName() + ".name"), 0xffff00, 0x404000, text);
	}

	/**
	 * 仿 ILookOverlay.printGeneric，改为整块垂直居中，并在最长行会超出右边界时整体左移，
	 * 避免长行（尤其是电力）被屏幕裁掉。
	 */
	@SideOnly(Side.CLIENT)
	private void drawInfo(Pre event, String title, int titleCol, int bgCol, List<String> text) {

		Minecraft mc = Minecraft.getMinecraft();
		ScaledResolution res = event.resolution;
		int screenW = res.getScaledWidth();

		int widest = mc.fontRenderer.getStringWidth(title);
		for(String line : text) widest = Math.max(widest, mc.fontRenderer.getStringWidth(stripColor(line)));

		int pX = screenW / 2 + 8;
		if(pX + widest > screenW - 6) pX = Math.max(6, screenW - 6 - widest);
		int pY = res.getScaledHeight() / 2 - ((text.size() + 1) * 10) / 2;

		GL11.glPushMatrix();

		mc.fontRenderer.drawString(title, pX + 1, pY + 1, bgCol);
		mc.fontRenderer.drawString(title, pX, pY, titleCol);
		pY += 10;

		for(String line : text) {
			int color = 0xFFFFFF;
			if(line.startsWith("&[")) {
				int end = line.lastIndexOf("&]");
				color = Integer.parseInt(line.substring(2, end));
				line = line.substring(end + 2);
			}
			mc.fontRenderer.drawStringWithShadow(line, pX, pY, color);
			pY += 10;
		}

		GL11.glDisable(GL11.GL_BLEND);
		GL11.glColor3f(1F, 1F, 1F);
		GL11.glPopMatrix();
		mc.renderEngine.bindTexture(Gui.icons);
	}

	private static String stripColor(String line) {
		if(line.startsWith("&[")) {
			int end = line.lastIndexOf("&]");
			if(end > 0) return line.substring(end + 2);
		}
		return line;
	}

	private String status(TileEntityEnvironmentPump pump) {
		if(pump.draining) return I18nUtil.resolveKey("info.environment_pump.draining");
		if(!pump.onGround) return I18nUtil.resolveKey("info.environment_pump.noground");
		if(pump.getEffectiveOutputAmount() <= 0) return I18nUtil.resolveKey("info.environment_pump.toofar");
		if(pump.getPower() < pump.getEffectivePower()) return I18nUtil.resolveKey("info.environment_pump.nopower");
		if(pump.inputTanks[0].getTankType() != Fluids.NONE && pump.inputTanks[0].getFill() < pump.getEffectiveInputAmount())
			return I18nUtil.resolveKey("info.environment_pump.noinput_short");
		if(pump.outputTanks[0].getFill() >= pump.outputTanks[0].getMaxFill()) return I18nUtil.resolveKey("info.environment_pump.full");
		return I18nUtil.resolveKey("info.environment_pump.running");
	}

	private static String color(int rgb) {
		return "&[" + rgb + "&]";
	}

	private static String fmt(long v) {
		return String.format(Locale.US, "%,d", v);
	}

	/** 电力数值缩写：640 → 640，2,000 → 2k，40,000,000 → 40M */
	private static String fmtPower(long v) {
		if(v >= 1_000_000L) return trim(v / 1_000_000D) + "M";
		if(v >= 1_000L) return trim(v / 1_000D) + "k";
		return Long.toString(v);
	}

	private static String trim(double d) {
		String s = String.format(Locale.US, "%.1f", d);
		return s.endsWith(".0") ? s.substring(0, s.length() - 2) : s;
	}

	private static String roman(int tier) {
		switch(tier) {
		case 1: return "I";
		case 2: return "II";
		case 3: return "III";
		case 4: return "IV";
		case 5: return "V";
		default: return String.valueOf(tier);
		}
	}
}
