package com.makentmgreatagain.client;

import java.util.Random;

import org.lwjgl.opengl.GL11;

import com.hbm.blocks.BlockDummyable;
import com.hbm.render.item.ItemRenderBase;
import com.hbm.render.tileentity.IItemRendererProvider;
import com.hbm.render.util.BeamPronter;
import com.hbm.render.util.BeamPronter.EnumBeamType;
import com.hbm.render.util.BeamPronter.EnumWaveType;
import com.makentmgreatagain.init.ModBlocks;
import com.makentmgreatagain.tileentity.TileEntitySupercriticalMHDGenerator;

import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.item.Item;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.Vec3;
import net.minecraftforge.client.IItemRenderer;
import net.minecraftforge.client.model.IModelCustom;

/**
 * 超临界磁流体膨胀发电机渲染器（17×7×7）：
 * 静态工业机身 + 前置内嵌同轴金属叶轮转子 + 复用 HBM BeamPronter 的多点高压磁流体电弧。
 */
public class RenderSupercriticalMHDGenerator extends TileEntitySpecialRenderer implements IItemRendererProvider {

	private static final int COLOR_OUTER_MHD = 0x1A75FF;
	private static final int COLOR_INNER_MHD = 0xE6FFFF;
	private static final int COLOR_OUTER_SURGE = 0x4CC8FF;
	private static final int COLOR_INNER_SURGE = 0xFFFFFF;

	@Override
	public void renderTileEntityAt(TileEntity tile, double x, double y, double z, float interp) {
		GL11.glPushMatrix();
		GL11.glTranslated(x + 0.5D, y, z + 0.5D);
		GL11.glEnable(GL11.GL_LIGHTING);
		GL11.glEnable(GL11.GL_CULL_FACE);

		switch(tile.getBlockMetadata() - BlockDummyable.offset) {
		case 3: GL11.glRotatef(90F, 0F, 1F, 0F); break;		// 南
		case 5: GL11.glRotatef(180F, 0F, 1F, 0F); break;	// 东
		case 2: GL11.glRotatef(270F, 0F, 1F, 0F); break;	// 北
		case 4: GL11.glRotatef(0F, 0F, 1F, 0F); break;		// 西
		}

		TileEntitySupercriticalMHDGenerator mhd =
				(tile instanceof TileEntitySupercriticalMHDGenerator) ? (TileEntitySupercriticalMHDGenerator) tile : null;

		renderModel(mhd, interp);

		if(mhd != null && mhd.operational) {
			renderMhdElectricArcs(mhd);
		}

		GL11.glPopMatrix();
	}

	private void renderModel(TileEntitySupercriticalMHDGenerator te, float interp) {
		bindTexture(AddonModels.supercritical_mhd_tex);
		IModelCustom model = AddonModels.supercriticalMhdModel();

		// 静态主体部件
		model.renderPart("Chassis");
		model.renderPart("Coils");
		model.renderPart("Fins");
		model.renderPart("Ports");

		// 动画 1：前置内嵌同轴金属叶轮转子（绕 X 轴在 y=3.50, z=0 旋转）
		float angle = 0.0F;
		if(te != null) {
			angle = te.lastRotorAngle + (te.rotorAngle - te.lastRotorAngle) * interp;
		}
		GL11.glPushMatrix();
		GL11.glTranslated(0.0D, 3.5D, 0.0D);
		GL11.glRotatef(angle, 1.0F, 0.0F, 0.0F);
		GL11.glTranslated(0.0D, -3.5D, 0.0D);
		model.renderPart("NozzleRotor");
		GL11.glPopMatrix();
	}

	/**
	 * 动画 2：复用 HBM 原生 BeamPronter 绘制立体高压磁流体电弧：
	 * 1) 机身左右两侧铜磁轭之间（视线高度）持续跃动的双股磁流体电弧（高负载追加第 3 股过载电弧）；
	 * 2) 顶部铜母线桥上方的高压放电电弧；
	 * 3) 前置进气罩内壁与旋转叶轮间的脉冲电晕放电。
	 */
	private void renderMhdElectricArcs(TileEntitySupercriticalMHDGenerator te) {
		int frame = te.clientTicks / 2; // 每 2 tick 刷新一次电弧形态，灵动不刺眼
		int baseSeed = (te.xCoord * 73428767) ^ (te.yCoord * 912931) ^ (te.zCoord * 42317861) ^ (frame * 19349663);
		Random rand = new Random(baseSeed);

		float load = Math.max(0.15F, te.loadFactor);

		// 1. 两侧铜磁轭之间（x = -1.50 -> +2.10, z = ±3.18）的眼高立体电弧
		for(int side = -1; side <= 1; side += 2) {
			double zWall = side * 3.16D;

			// 主磁流体电弧（上下缓慢游走）
			double y1Start = 2.4D + Math.sin(te.clientTicks * 0.13D + side) * 1.1D;
			double y1End   = 2.6D + Math.cos(te.clientTicks * 0.11D - side) * 1.1D;
			drawArc(-1.48D, y1Start, zWall, 2.08D, y1End, zWall,
					COLOR_OUTER_MHD, COLOR_INNER_MHD, rand.nextInt(), 10, 0.22F, 3, 0.055F);

			// 次级高频细电弧
			double y2Start = 4.2D + Math.cos(te.clientTicks * 0.17D + side * 2) * 0.95D;
			double y2End   = 4.0D + Math.sin(te.clientTicks * 0.15D + side) * 0.95D;
			drawArc(-1.48D, y2Start, zWall + side * 0.06D, 2.08D, y2End, zWall + side * 0.06D,
					COLOR_OUTER_SURGE, COLOR_INNER_SURGE, rand.nextInt(), 9, 0.18F, 2, 0.036F);

			// 高负载脉冲第 3 股过载电弧（在脉冲周期内跃现）
			if(te.arcActiveTicks > 0 && load > 0.30F) {
				double y3A = 1.6D + rand.nextDouble() * 3.8D;
				double y3B = 1.6D + rand.nextDouble() * 3.8D;
				drawArc(-1.48D, y3A, zWall + side * 0.10D, 2.08D, y3B, zWall + side * 0.10D,
						COLOR_OUTER_MHD, COLOR_INNER_SURGE, rand.nextInt(), 12, 0.28F, 3, 0.068F);
			}
		}

		// 2. 顶部铜母线桥（x = -1.48 -> +2.08, y = 6.92）高压电弧
		double zTopA = (rand.nextDouble() - 0.5D) * 1.4D;
		double zTopB = (rand.nextDouble() - 0.5D) * 1.4D;
		drawArc(-1.48D, 6.90D, zTopA, 2.08D, 6.90D, zTopB,
				COLOR_OUTER_MHD, COLOR_INNER_MHD, rand.nextInt(), 10, 0.24F, 3, 0.058F);

		if(te.arcActiveTicks > 0) {
			double zTopC = (rand.nextDouble() - 0.5D) * 1.6D;
			double zTopD = (rand.nextDouble() - 0.5D) * 1.6D;
			drawArc(-1.48D, 6.96D, zTopC, 2.08D, 6.96D, zTopD,
					COLOR_OUTER_SURGE, COLOR_INNER_SURGE, rand.nextInt(), 12, 0.30F, 3, 0.072F);
		}

		// 3. 前置进气罩内壁与旋转叶轮间的脉冲电晕放电
		if(te.arcActiveTicks > 0) {
			int sparkCount = (load > 0.5F) ? 2 : 1;
			for(int s = 0; s < sparkCount; s++) {
				double ang = rand.nextDouble() * Math.PI * 2.0D;
				double yOut = 3.50D + Math.cos(ang) * 0.92D;
				double zOut = Math.sin(ang) * 0.92D;
				double yIn  = 3.50D + Math.cos(ang + 0.35D) * 0.28D;
				double zIn  = Math.sin(ang + 0.35D) * 0.28D;
				drawArc(8.26D, yOut, zOut, 8.28D, yIn, zIn,
						COLOR_OUTER_SURGE, COLOR_INNER_SURGE, rand.nextInt(), 6, 0.10F, 2, 0.032F);
			}
		}

		// BeamPronter 会关闭背面剔除，这里显式恢复渲染状态
		GL11.glEnable(GL11.GL_CULL_FACE);
		GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
	}

	private void drawArc(double x1, double y1, double z1, double x2, double y2, double z2,
			int outerColor, int innerColor, int seed, int segments, float jitter, int layers, float thickness) {
		GL11.glPushMatrix();
		GL11.glTranslated(x1, y1, z1);
		Vec3 vec = Vec3.createVectorHelper(x2 - x1, y2 - y1, z2 - z1);
		BeamPronter.prontBeam(vec, EnumWaveType.RANDOM, EnumBeamType.SOLID,
				outerColor, innerColor, seed, segments, jitter, layers, thickness);
		GL11.glPopMatrix();
	}

	@Override
	public Item getItemForRenderer() {
		return Item.getItemFromBlock(ModBlocks.supercritical_mhd_generator);
	}

	@Override
	public IItemRenderer getRenderer() {
		return new ItemRenderBase() {
			@Override
			public void renderInventory() {
				GL11.glScaled(0.78, 0.78, 0.78);
			}
			@Override
			public void renderNonInv() {
				GL11.glScaled(0.20, 0.20, 0.20);
				GL11.glTranslated(0, -3.5, 0);
			}
			@Override
			public void renderCommon() {
				GL11.glEnable(GL11.GL_CULL_FACE);
				renderModel(null, 0.0F);
			}
		};
	}
}
