package com.makentmgreatagain.client;

import org.lwjgl.opengl.GL11;

import com.hbm.blocks.BlockDummyable;
import com.hbm.blocks.ModBlocks;
import com.hbm.blocks.generic.BlockNTMFlower.EnumFlowerType;
import com.hbm.blocks.generic.BlockTallPlant.EnumTallFlower;
import com.hbm.render.item.ItemRenderBase;
import com.hbm.render.tileentity.IItemRendererProvider;
import com.makentmgreatagain.tileentity.TileEntityIndustrialCultivationStation;

import net.minecraft.block.Block;
import net.minecraft.block.BlockCactus;
import net.minecraft.block.BlockDoublePlant;
import net.minecraft.block.BlockNetherWart;
import net.minecraft.block.BlockStem;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.IIcon;
import net.minecraft.world.ColorizerGrass;
import net.minecraftforge.client.IItemRenderer;
import net.minecraftforge.client.model.IModelCustom;

/**
 * 工业种植站渲染器（7×7×5，纯正 HBM 工业机械质感）：
 * - 静态主体 + HBM 肋纹灰管道/双缓冲罐 + 双种植槽；
 * - 顶部双轴流旋转风扇与补光灯（仅通电时点亮/旋转）；
 * - 6 个种植位 1:1 对应 GUI 中的 6 个作物槽，动态展示原版作物、南瓜/西瓜（染色瓜藤 + 3D 果实生长）、
 *   HBM 大麻（1格->2格高染色生长）与原版向日葵（两格高，顶端带侧转花盘）。
 */
public class RenderIndustrialCultivationStation extends TileEntitySpecialRenderer implements IItemRendererProvider {

	private static final double[][] FAN_CENTERS = {
		{ -0.85D, 4.92D, -1.45D },
		{ -0.85D, 4.92D,  1.45D }
	};
	private static final String[] FAN_PARTS = { "Fan1", "Fan2" };

	/** 6 个 3D 种植位中心坐标（左槽 3 位 + 右槽 3 位），与 GUI 中 0..5 号作物槽一一对应 */
	private static final double[][] PLOT_POS = {
		{ -1.90D, -1.45D },
		{ -1.90D,  0.00D },
		{ -1.90D,  1.45D },
		{  0.20D, -1.45D },
		{  0.20D,  0.00D },
		{  0.20D,  1.45D }
	};

	@Override
	public void renderTileEntityAt(TileEntity tile, double x, double y, double z, float interp) {
		GL11.glPushMatrix();
		GL11.glTranslated(x + 0.5D, y, z + 0.5D);
		GL11.glEnable(GL11.GL_LIGHTING);
		GL11.glEnable(GL11.GL_CULL_FACE);

		switch(tile.getBlockMetadata() - BlockDummyable.offset) {
		case 3: GL11.glRotatef(0F, 0F, 1F, 0F); break;		// 南
		case 5: GL11.glRotatef(90F, 0F, 1F, 0F); break;		// 东
		case 2: GL11.glRotatef(180F, 0F, 1F, 0F); break;	// 北
		case 4: GL11.glRotatef(270F, 0F, 1F, 0F); break;	// 西
		}

		TileEntityIndustrialCultivationStation station =
				(tile instanceof TileEntityIndustrialCultivationStation)
						? (TileEntityIndustrialCultivationStation) tile : null;

		renderModel(station, interp);

		GL11.glPopMatrix();
	}

	private void renderModel(TileEntityIndustrialCultivationStation te, float interp) {
		bindTexture(AddonModels.industrial_cultivation_tex);
		IModelCustom model = AddonModels.industrialCultivationModel();

		// 1. 静态机身、HBM 肋纹管道/缓冲罐、双种植槽与土壤
		model.renderPart("Chassis");
		model.renderPart("Plumbing");
		model.renderPart("Interior");

		// 2. 顶部双轴流旋转风扇（仅通电旋转）
		float fanAngle = 0.0F;
		if(te != null) {
			fanAngle = te.lastFanAngle + (te.fanAngle - te.lastFanAngle) * interp;
		}
		for(int i = 0; i < 2; i++) {
			double cx = FAN_CENTERS[i][0];
			double cy = FAN_CENTERS[i][1];
			double cz = FAN_CENTERS[i][2];
			GL11.glPushMatrix();
			GL11.glTranslated(cx, cy, cz);
			GL11.glRotatef((i == 0) ? fanAngle : -fanAngle, 0.0F, 1.0F, 0.0F);
			GL11.glTranslated(-cx, -cy, -cz);
			model.renderPart(FAN_PARTS[i]);
			GL11.glPopMatrix();
		}

		// 3. 顶罩内侧双光谱补光灯条（仅机器通电后点亮）
		boolean powered = (te != null && te.isPowered());
		if(powered) {
			float prevLightX = OpenGlHelper.lastBrightnessX;
			float prevLightY = OpenGlHelper.lastBrightnessY;
			OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240F, 240F);
			GL11.glDisable(GL11.GL_LIGHTING);
			model.renderPart("GrowLights");
			GL11.glEnable(GL11.GL_LIGHTING);
			OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, prevLightX, prevLightY);
		} else {
			GL11.glColor4f(0.32F, 0.32F, 0.36F, 1.0F);
			model.renderPart("GrowLights");
			GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
		}

		// 4. 动态按 6 个槽位逐格渲染对应作物的真实模型与生长阶段
		if(te != null) {
			renderPlantedCrops(te);
			bindTexture(AddonModels.industrial_cultivation_tex);
		}

		// 5. 三面强化观察玻璃与顶部中央天窗（最后混合渲染保证通透）
		GL11.glEnable(GL11.GL_BLEND);
		GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
		GL11.glDepthMask(false);
		model.renderPart("Glass");
		GL11.glDepthMask(true);
		GL11.glDisable(GL11.GL_BLEND);
	}

	private void renderPlantedCrops(TileEntityIndustrialCultivationStation te) {
		boolean any = false;
		for(int i = 0; i < TileEntityIndustrialCultivationStation.CROP_COUNT; i++) {
			if(te.getDisplayCropStack(i) != null) {
				any = true;
				break;
			}
		}
		if(!any) return;

		bindTexture(TextureMap.locationBlocksTexture);
		GL11.glDisable(GL11.GL_CULL_FACE);
		GL11.glEnable(GL11.GL_ALPHA_TEST);
		GL11.glAlphaFunc(GL11.GL_GREATER, 0.1F);

		Tessellator tess = Tessellator.instance;
		double y0 = 1.88D;

		for(int i = 0; i < TileEntityIndustrialCultivationStation.CROP_COUNT; i++) {
			ItemStack stack = te.getDisplayCropStack(i);
			if(stack == null) continue;

			double px = PLOT_POS[i][0];
			double pz = PLOT_POS[i][1];
			float ratio = te.getSlotGrowthRatio(i);

			// A. 原版向日葵：与 RenderBlocks#renderBlockDoublePlant 同款画法，下半格茎叶 + 上半格顶叶 + 顶端花盘
			if(TileEntityIndustrialCultivationStation.isVanillaSunflower(stack)) {
				renderVanillaSunflower(tess, px, y0, pz, ratio);
				continue;
			}

			// B. HBM 大麻：1 格幼苗 -> 2 格高大麻（草色染色）
			if(TileEntityIndustrialCultivationStation.isHbmHemp(stack)) {
				renderHbmHemp(tess, px, y0, pz, ratio);
				continue;
			}

			Block plantBlock = TileEntityIndustrialCultivationStation.resolvePlantBlock(stack);
			if(plantBlock == null) plantBlock = Blocks.wheat;

			// C. 南瓜 / 西瓜：绿->黄染色瓜藤 + 3D 实体南瓜/西瓜随进度长出
			if(plantBlock == Blocks.pumpkin_stem || plantBlock == Blocks.melon_stem || plantBlock instanceof BlockStem) {
				Block fruit = (plantBlock == Blocks.melon_stem) ? Blocks.melon_block : Blocks.pumpkin;
				renderStemAndFruit(tess, plantBlock, fruit, px, y0, pz, ratio);
				continue;
			}

			// D. 仙人掌：原版画法的迷你柱体（分节棱 + 四侧与顶面独立贴图）
			if(plantBlock instanceof BlockCactus) {
				renderVanillaCactus(tess, plantBlock, px, y0, pz, ratio);
				continue;
			}

			// E. 通用原版 & 模组交叉面片作物（小麦、胡萝卜、马铃薯、甘蔗、地狱疣、可可、蘑菇等）
			int stageMeta = resolveStageMeta(plantBlock, ratio);
			IIcon icon = safeGetIcon(plantBlock, 2, stageMeta);
			if(icon == null) continue;

			double h = 0.22D + 0.85D * ratio;
			double halfW = 0.34D;
			GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
			tess.startDrawingQuads();
			tess.setNormal(0.0F, 1.0F, 0.0F);
			addCrossCrop(tess, px - 0.18D, y0, pz - 0.18D, halfW, h, icon);
			addCrossCrop(tess, px + 0.18D, y0, pz + 0.18D, halfW, h, icon);
			tess.draw();
		}

		GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
		GL11.glEnable(GL11.GL_CULL_FACE);
	}

	/**
	 * 渲染原版向日葵。
	 *
	 * <p>画法照抄 {@code RenderBlocks#renderBlockDoublePlant}：下半格是茎叶交叉面片，
	 * 上半格是顶叶交叉面片，顶端再竖一张 0.6 格宽、绕竖轴略侧转的花盘（正面 + 背面两张）。</p>
	 *
	 * <p>幼株只长下半格，生长过半才抽出上半格与花盘，成熟时正好两格高。</p>
	 */
	private static void renderVanillaSunflower(Tessellator tess, double px, double y0, double pz, float ratio) {
		// 极少数整合包会把 double_plant 换成别的实现，换掉了就老老实实不渲染
		if(!(Blocks.double_plant instanceof BlockDoublePlant)) return;

		BlockDoublePlant plant = (BlockDoublePlant) Blocks.double_plant;
		IIcon stemIcon = plant.func_149888_a(false, 0);	// double_plant_sunflower_bottom
		IIcon leafIcon = plant.func_149888_a(true, 0);	// double_plant_sunflower_top

		GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
		double halfW = 0.42D;
		double segH = 0.90D;

		// 1. 下半格：茎与下部叶随进度长满一格
		if(stemIcon != null) {
			double h = (ratio < 0.55F) ? segH * (0.44D + 0.56D * (ratio / 0.55F)) : segH;
			tess.startDrawingQuads();
			tess.setNormal(0.0F, 1.0F, 0.0F);
			addCrossCrop(tess, px, y0, pz, halfW, h, stemIcon);
			tess.draw();
		}

		// 2. 上半格：生长过半才出现，避免幼苗阶段凭空冒出一株两格高的花
		if(ratio >= 0.55F) {
			float upper = (ratio - 0.55F) / 0.45F;
			double topH = segH * Math.min(1.0D, 0.35D + 0.65D * upper);

			if(leafIcon != null) {
				tess.startDrawingQuads();
				tess.setNormal(0.0F, 1.0F, 0.0F);
				addCrossCrop(tess, px, y0 + segH, pz, halfW, topH, leafIcon);
				tess.draw();
			}

			renderSunflowerHead(tess, plant, px, y0 + segH, pz, topH);
		}
	}

	/** 向日葵花盘：正面 / 背面各一张竖直面片，绕竖轴侧转 24°，背面沿法线挪一点避免与正面共面闪烁 */
	private static void renderSunflowerHead(Tessellator tess, BlockDoublePlant plant, double cx, double y0, double cz, double h) {
		IIcon front = plant.sunflowerIcons[0];
		IIcon back = (plant.sunflowerIcons.length > 1) ? plant.sunflowerIcons[1] : null;
		if(front == null) return;

		double ang = Math.toRadians(24.0D);
		double dx = Math.cos(ang) * 0.30D;
		double dz = Math.sin(ang) * 0.30D;
		double nx = dz * 0.05D;
		double nz = -dx * 0.05D;

		tess.startDrawingQuads();
		tess.setNormal(0.0F, 0.0F, 1.0F);
		addBillboard(tess, front, cx, y0, cz, dx, dz, h);
		if(back != null) {
			tess.setNormal(0.0F, 0.0F, -1.0F);
			addBillboard(tess, back, cx + nx, y0, cz + nz, -dx, -dz, h);
		}
		tess.draw();
	}

	private static void addBillboard(Tessellator tess, IIcon icon, double cx, double y0, double cz, double dx, double dz, double h) {
		double u0 = icon.getMinU();
		double u1 = icon.getMaxU();
		double v0 = icon.getMinV();
		double v1 = icon.getMaxV();

		tess.addVertexWithUV(cx - dx, y0,     cz - dz, u0, v1);
		tess.addVertexWithUV(cx + dx, y0,     cz + dz, u1, v1);
		tess.addVertexWithUV(cx + dx, y0 + h, cz + dz, u1, v0);
		tess.addVertexWithUV(cx - dx, y0 + h, cz - dz, u0, v0);
	}

	/** 渲染 HBM 大麻生长动画：草色染色，前期 1 格（plant_flower WEED），中后期拔高为 2 格（plant_tall WEED） */
	private static void renderHbmHemp(Tessellator tess, double px, double y0, double pz, float ratio) {
		int col = ColorizerGrass.getGrassColor(0.5D, 1.0D);
		float r = ((col >> 16) & 255) / 255.0F;
		float g = ((col >> 8) & 255) / 255.0F;
		float b = (col & 255) / 255.0F;
		GL11.glColor4f(r, g, b, 1.0F);

		double halfW = 0.40D;
		if(ratio < 0.45F) {
			IIcon icon = safeGetIcon(ModBlocks.plant_flower, 0, EnumFlowerType.WEED.ordinal());
			if(icon != null) {
				double h = 0.30D + 0.55D * (ratio / 0.45F);
				tess.startDrawingQuads();
				tess.setNormal(0.0F, 1.0F, 0.0F);
				addCrossCrop(tess, px, y0, pz, halfW, h, icon);
				tess.draw();
			}
		} else {
			int meta = EnumTallFlower.WEED.ordinal();
			IIcon lower = safeGetIcon(ModBlocks.plant_tall, 0, meta);
			IIcon upper = safeGetIcon(ModBlocks.plant_tall, 0, meta + 8);
			double segH = 0.80D;
			double topH = segH * Math.min(1.0D, 0.35D + 0.65D * ((ratio - 0.45F) / 0.55F));
			tess.startDrawingQuads();
			tess.setNormal(0.0F, 1.0F, 0.0F);
			if(lower != null) addCrossCrop(tess, px, y0, pz, halfW, segH, lower);
			if(upper != null) addCrossCrop(tess, px, y0 + segH, pz, halfW, topH, upper);
			tess.draw();
		}
		GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
	}

	/** 渲染南瓜/西瓜：按生长阶段绿->黄染色瓜藤 + 中后期在旁长出 3D 实体南瓜/西瓜方块 */
	private static void renderStemAndFruit(Tessellator tess, Block stem, Block fruit, double px, double y0, double pz, float ratio) {
		IIcon stemIcon = safeGetIcon(stem, 0, Math.min(7, Math.round(ratio * 7.0F)));
		if(stemIcon != null) {
			// 原版瓜藤贴图是灰度图，按原版逻辑由鲜绿渐变至熟黄绿
			float r = 0.22F + 0.65F * ratio;
			float g = 0.82F - 0.06F * ratio;
			float b = 0.18F - 0.04F * ratio;
			GL11.glColor4f(r, g, b, 1.0F);
			double stemH = 0.22D + 0.58D * ratio;
			tess.startDrawingQuads();
			tess.setNormal(0.0F, 1.0F, 0.0F);
			addCrossCrop(tess, px - 0.18D, y0, pz, 0.28D, stemH, stemIcon);
			tess.draw();
			GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
		}

		// 生长进度 >= 30% 后在藤旁长出 3D 实体南瓜/西瓜并随进度膨大
		if(ratio >= 0.30F) {
			float fRatio = (ratio - 0.30F) / 0.70F;
			double halfSize = 0.08D + 0.19D * fRatio;
			double fh = halfSize * 2.0D;
			IIcon fSide = safeGetIcon(fruit, 2, 0);
			IIcon fTop = safeGetIcon(fruit, 1, 0);
			if(fSide != null) {
				renderMiniBox(tess, px + 0.18D, y0, pz, halfSize, fh, fSide, fTop != null ? fTop : fSide);
			}
		}
	}

	private static int resolveStageMeta(Block b, float ratio) {
		if(b instanceof BlockNetherWart) {
			return Math.min(3, Math.round(ratio * 3.0F));
		}
		if(b == Blocks.cocoa) {
			return Math.min(2, Math.round(ratio * 2.0F));
		}
		return Math.min(7, Math.round(ratio * 7.0F));
	}

	private static IIcon safeGetIcon(Block b, int side, int meta) {
		try {
			IIcon icon = b.getIcon(side, meta);
			if(icon != null) return icon;
			return b.getIcon(0, 0);
		} catch(Throwable t) {
			return Blocks.wheat.getIcon(0, Math.min(7, meta));
		}
	}

	private static void addCrossCrop(Tessellator tess, double cx, double y0, double cz, double halfW, double h, IIcon icon) {
		double u0 = icon.getMinU();
		double u1 = icon.getMaxU();
		double v0 = icon.getMinV();
		double v1 = icon.getMaxV();

		tess.addVertexWithUV(cx - halfW, y0,     cz - halfW, u0, v1);
		tess.addVertexWithUV(cx + halfW, y0,     cz + halfW, u1, v1);
		tess.addVertexWithUV(cx + halfW, y0 + h, cz + halfW, u1, v0);
		tess.addVertexWithUV(cx - halfW, y0 + h, cz - halfW, u0, v0);

		tess.addVertexWithUV(cx + halfW, y0,     cz - halfW, u0, v1);
		tess.addVertexWithUV(cx - halfW, y0,     cz + halfW, u1, v1);
		tess.addVertexWithUV(cx - halfW, y0 + h, cz + halfW, u1, v0);
		tess.addVertexWithUV(cx + halfW, y0 + h, cz - halfW, u0, v0);
	}

	/**
	 * 渲染原版仙人掌式迷你柱体。
	 *
	 * <p>比例参照 {@code RenderBlocks#renderBlockCactus}：柱身四侧贴图比整格内缩，
	 * 顶面单独使用 cactus_top。原版仙人掌按格分节，这里同样按 0.42 格一节向上叠，
	 * 每节比下面一节再收 0.005 格，节与节之间自然露出一道棱，
	 * 任何角度都能看出是立体的柱状仙人掌，而不是一张竖直的面片。</p>
	 *
	 * <p>幼株只有一节（0.32 格高），成熟时三节共 1.16 格，正好长到观察玻璃下沿。</p>
	 */
	private static void renderVanillaCactus(Tessellator tess, Block block, double px, double y0, double pz, float ratio) {
		IIcon side = safeGetIcon(block, 2, 0);
		IIcon top = safeGetIcon(block, 1, 0);
		if(side == null) return;
		if(top == null) top = side;

		final double segH = 0.42D;			// 单节高度，与原版一格仙人掌等高
		double total = 0.32D + 0.84D * ratio;	// 幼苗 0.32 格 -> 成熟 1.16 格
		double baseHalf = 0.26D + 0.10D * ratio;	// 幼苗细（0.52 格宽），成熟后 0.72 格宽，长成一根实心柱子
		int segs = Math.max(1, (int) Math.ceil(total / segH - 0.001D));

		double y = y0;
		for(int s = 0; s < segs; s++) {
			double h = Math.min(segH, (y0 + total) - y);
			if(h <= 1.0E-4D) break;
			double r = baseHalf - 0.005D * s;
			boolean last = (s == segs - 1);
			// 非顶节的顶面用柱身贴图，露出的那一圈就是节与节之间的棱
			// 第 9 个参数 1.0：把贴图四周 1 像素的透明边内缩掉，四棱处才不会露出透光的缝
			renderMiniBox(tess, px, y, pz, r, h, side, last ? top : side, 1.0D);
			y += h;
		}
	}

	private static void renderMiniBox(Tessellator tess, double cx, double y0, double cz, double r, double h, IIcon side, IIcon top) {
		renderMiniBox(tess, cx, y0, cz, r, h, side, top, 0.0D);
	}

	/**
	 * 画一个实心小方柱。
	 *
	 * @param uvInset 贴图四周内缩的像素数：原版 cactus_side / cactus_top 的边缘留着 1 像素全透明，
	 *                直接整张贴上去，柱子的四条棱上就会各露出一条透光的缝，看着像模型缺了一块；
	 *                内缩 1 像素后四面与顶面都是实的。
	 */
	private static void renderMiniBox(Tessellator tess, double cx, double y0, double cz, double r, double h, IIcon side, IIcon top, double uvInset) {
		double u0 = side.getInterpolatedU(uvInset), u1 = side.getInterpolatedU(16.0D - uvInset);
		double v0 = side.getInterpolatedV(uvInset), v1 = side.getInterpolatedV(16.0D - uvInset);
		double tu0 = top.getInterpolatedU(uvInset), tu1 = top.getInterpolatedU(16.0D - uvInset);
		double tv0 = top.getInterpolatedV(uvInset), tv1 = top.getInterpolatedV(16.0D - uvInset);
		tess.startDrawingQuads();
		// +Z
		tess.setNormal(0F, 0F, 1F);
		tess.addVertexWithUV(cx - r, y0,     cz + r, u0, v1);
		tess.addVertexWithUV(cx + r, y0,     cz + r, u1, v1);
		tess.addVertexWithUV(cx + r, y0 + h, cz + r, u1, v0);
		tess.addVertexWithUV(cx - r, y0 + h, cz + r, u0, v0);
		// -Z
		tess.setNormal(0F, 0F, -1F);
		tess.addVertexWithUV(cx + r, y0,     cz - r, u0, v1);
		tess.addVertexWithUV(cx - r, y0,     cz - r, u1, v1);
		tess.addVertexWithUV(cx - r, y0 + h, cz - r, u1, v0);
		tess.addVertexWithUV(cx + r, y0 + h, cz - r, u0, v0);
		// +X
		tess.setNormal(1F, 0F, 0F);
		tess.addVertexWithUV(cx + r, y0,     cz + r, u0, v1);
		tess.addVertexWithUV(cx + r, y0,     cz - r, u1, v1);
		tess.addVertexWithUV(cx + r, y0 + h, cz - r, u1, v0);
		tess.addVertexWithUV(cx + r, y0 + h, cz + r, u0, v0);
		// -X
		tess.setNormal(-1F, 0F, 0F);
		tess.addVertexWithUV(cx - r, y0,     cz - r, u0, v1);
		tess.addVertexWithUV(cx - r, y0,     cz + r, u1, v1);
		tess.addVertexWithUV(cx - r, y0 + h, cz + r, u1, v0);
		tess.addVertexWithUV(cx - r, y0 + h, cz - r, u0, v0);
		// +Y
		tess.setNormal(0F, 1F, 0F);
		tess.addVertexWithUV(cx - r, y0 + h, cz + r, tu0, tv1);
		tess.addVertexWithUV(cx + r, y0 + h, cz + r, tu1, tv1);
		tess.addVertexWithUV(cx + r, y0 + h, cz - r, tu1, tv0);
		tess.addVertexWithUV(cx - r, y0 + h, cz - r, tu0, tv0);
		tess.draw();
	}

	@Override
	public Item getItemForRenderer() {
		return Item.getItemFromBlock(com.makentmgreatagain.init.ModBlocks.industrial_cultivation_station);
	}

	@Override
	public IItemRenderer getRenderer() {
		return new ItemRenderBase() {
			@Override
			public void renderInventory() {
				GL11.glScaled(1.5525, 1.5525, 1.5525);
				GL11.glTranslated(0, -1.8, 0);
			}
			@Override
			public void renderNonInv() {
				GL11.glScaled(0.28, 0.28, 0.28);
				GL11.glTranslated(0, -2.5, 0);
			}
			@Override
			public void renderCommon() {
				GL11.glEnable(GL11.GL_CULL_FACE);
				renderModel(null, 0.0F);
			}
		};
	}
}
