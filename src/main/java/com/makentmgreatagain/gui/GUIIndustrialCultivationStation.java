package com.makentmgreatagain.gui;

import java.util.ArrayList;
import java.util.List;

import org.lwjgl.opengl.GL11;

import com.hbm.inventory.gui.GuiInfoContainer;
import com.makentmgreatagain.MakeNTMGreatAgain;
import com.makentmgreatagain.container.ContainerIndustrialCultivationStation;
import com.makentmgreatagain.tileentity.TileEntityIndustrialCultivationStation;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.ResourceLocation;

/**
 * 工业种植站 GUI（1:1 像素对齐用户重绘的 gui_industrial_cultivation_station.png 320×256，实画 210×187）：
 * - 左侧电力条 (8, 18, 16×42，取自 x=210, y=0) + 电池槽 (8, 62) + 升级槽 (8, 95)
 * - 流体槽 (44, 18, 16×42，左/右键点击切换营养液) + 流体识别码槽 (44, 63)
 * - 3×2 作物槽 + 每格下方同宽 16×2 灰->亮绿生长进度条（y=36 / y=58）
 * - 肥料槽 (65, 63) + 右侧横向肥料储备条 (87, 75, 30×4)
 * - 最右侧 3×3 物品输出口 (149, 18)
 */
public class GUIIndustrialCultivationStation extends GuiInfoContainer {

	private static final ResourceLocation TEXTURE = new ResourceLocation(
			MakeNTMGreatAgain.MODID, "textures/gui/gui_industrial_cultivation_station.png");
	private static final double TEX_W = 320.0D;

	private static final int W = 210, H = 187;

	private static final int POWER_X = 8, POWER_Y = 18, POWER_W = 16, POWER_H = 42;
	private static final int POWER_U = 210, POWER_V = 0;

	private static final int TANK_X = 44, TANK_Y = 18, TANK_W = 16, TANK_H = 42;

	private static final int CROP_X0 = 65, CROP_STEP = 18;
	private static final int[] BAR_YS = { 36, 58 };
	private static final int BAR_W = 16, BAR_H = 2;

	private static final int FERT_SLOT_X = 65, FERT_SLOT_Y = 63;
	private static final int FERT_BAR_X = 87, FERT_BAR_Y = 75, FERT_BAR_W = 31, FERT_BAR_H = 4;

	private final TileEntityIndustrialCultivationStation te;

	public GUIIndustrialCultivationStation(InventoryPlayer invPlayer, TileEntityIndustrialCultivationStation te) {
		super(new ContainerIndustrialCultivationStation(invPlayer, te));
		this.te = te;
		this.xSize = W;
		this.ySize = H;
	}

	@Override
	public void initGui() {
		super.initGui();
		// 左侧独立电力/升级模块宽 34px，整体左移 17px 让下方 176px 玩家物品栏严格居中于屏幕正中
		this.guiLeft -= 17;
	}

	@Override
	public void drawScreen(int mouseX, int mouseY, float interp) {
		super.drawScreen(mouseX, mouseY, interp);

		// 电力条悬停提示
		this.drawElectricityInfo(this, mouseX, mouseY,
				guiLeft + POWER_X, guiTop + POWER_Y, POWER_W, POWER_H,
				te.getPower(), te.getMaxPower());

		// 流体罐悬停提示（完全沿用 HBM 原版 renderTankInfo，流体切换走下方流体识别码槽）
		te.tank.renderTankInfo(this, mouseX, mouseY, guiLeft + TANK_X, guiTop + TANK_Y, TANK_W, TANK_H);

		// 3×2 作物槽底部小进度条悬停提示
		for(int i = 0; i < TileEntityIndustrialCultivationStation.CROP_COUNT; i++) {
			int r = i / 3;
			int c = i % 3;
			int bx = CROP_X0 + c * CROP_STEP;
			int by = BAR_YS[r];
			if(isInRect(mouseX, mouseY, bx, by - 1, BAR_W, BAR_H + 2)) {
				int pct = Math.round(te.getSlotGrowthRatio(i) * 100.0F);
				List<String> tip = new ArrayList<String>();
				tip.add("种植位 #" + (i + 1));
				tip.add("生长进度: " + EnumChatFormatting.GREEN + pct + "%");
				this.func_146283_a(tip, mouseX, mouseY);
				break;
			}
		}

		// 肥料储备条悬停提示：仅显示储备与当前生效增速
		if(isInRect(mouseX, mouseY, FERT_BAR_X, FERT_BAR_Y - 1, FERT_BAR_W, FERT_BAR_H + 2)) {
			List<String> tip = new ArrayList<String>();
			tip.add(EnumChatFormatting.GOLD + "肥料缓冲");
			String effect = te.fertilizerBuffer >= TileEntityIndustrialCultivationStation.FERTILIZER_PER_HARVEST
					? String.format("x%.1f / +%d", te.fertilizerSpeed, te.fertilizerBonus) : "待机";
			tip.add(te.fertilizerBuffer + " / " + TileEntityIndustrialCultivationStation.MAX_FERTILIZER + "  " + EnumChatFormatting.GREEN + effect);
			this.func_146283_a(tip, mouseX, mouseY);
		}

		// 肥料输入槽悬停提示：仅在槽位为空且未手持物品时显示支持列表，避免与物品名称提示重叠
		if(te.getStackInSlot(TileEntityIndustrialCultivationStation.SLOT_FERTILIZER) == null
				&& mc.thePlayer.inventory.getItemStack() == null
				&& isInRect(mouseX, mouseY, FERT_SLOT_X, FERT_SLOT_Y, 16, 16)) {
			List<String> tip = new ArrayList<String>();
			tip.add(EnumChatFormatting.YELLOW + "肥料槽（支持分级加速与增产）");
			tip.add(EnumChatFormatting.GRAY + "灰烬 (x1.3) / 骨粉 & 生物质 (x1.5)");
			tip.add(EnumChatFormatting.GRAY + "压缩生物质 (x2.0) / 白磷 & 磷晶体 (x2.2)");
			tip.add(EnumChatFormatting.GRAY + "HBM 化肥 (x3.0, 额外 +2 产物)");
			this.func_146283_a(tip, mouseX, mouseY);
		}
	}

	@Override
	protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
		String title = I18n.format(te.getName());
		this.fontRendererObj.drawString(title, 44, 6, 4210752);
		this.fontRendererObj.drawString(I18n.format("container.inventory"), 42, 95, 4210752);
	}

	@Override
	protected void drawGuiContainerBackgroundLayer(float interp, int mouseX, int mouseY) {
		GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
		Minecraft.getMinecraft().getTextureManager().bindTexture(TEXTURE);

		// 1. 面板底图（仅采样 0..210 列，1:1 像素不拉伸）
		drawTex(guiLeft, guiTop, 0, 0, W, H);

		// 2. 左侧电力条（从底图 x=210, y=0 截取 16×42）
		int p = te.getPowerScaled(POWER_H);
		if(p > 0) {
			drawTex(guiLeft + POWER_X, guiTop + POWER_Y + POWER_H - p,
					POWER_U, POWER_V + (POWER_H - p), POWER_W, p);
		}

		// 3. 流体槽液柱
		te.tank.renderTank(guiLeft + TANK_X, guiTop + TANK_Y + TANK_H, this.zLevel, TANK_W, TANK_H);

		// 4. 3×2 作物槽底部同宽（16×2）生长进度条：灰色 -> 亮绿色平滑过渡
		for(int i = 0; i < TileEntityIndustrialCultivationStation.CROP_COUNT; i++) {
			float ratio = te.getSlotGrowthRatio(i);
			int fillW = Math.round(ratio * BAR_W);
			if(fillW > 0) {
				int r = i / 3;
				int c = i % 3;
				int bx = guiLeft + CROP_X0 + c * CROP_STEP;
				int by = guiTop + BAR_YS[r];
				int barColor = lerpGrayToBrightGreen(ratio);
				drawRect(bx, by, bx + fillW, by + BAR_H, barColor);
				drawRect(bx, by, bx + fillW, by + 1, lighten(barColor));
			}
		}

		// 5. 肥料槽右侧横向肥料储备条（87, 75, 30×4）
		if(te.fertilizerBuffer > 0) {
			float fRatio = Math.min(1.0F, (float) te.fertilizerBuffer / (float) TileEntityIndustrialCultivationStation.MAX_FERTILIZER);
			int fw = Math.max(1, Math.round(fRatio * FERT_BAR_W));
			int fx = guiLeft + FERT_BAR_X;
			int fy = guiTop + FERT_BAR_Y;
			drawRect(fx, fy, fx + fw, fy + FERT_BAR_H, 0xFFE6B830);
			drawRect(fx, fy, fx + fw, fy + 1, 0xFFFFDF70);
		}
	}

	private static int lerpGrayToBrightGreen(float t) {
		t = Math.max(0.0F, Math.min(1.0F, t));
		int r = Math.round(0x78 + (0x34 - 0x78) * t);
		int g = Math.round(0x78 + (0xFF - 0x78) * t);
		int b = Math.round(0x78 + (0x34 - 0x78) * t);
		return 0xFF000000 | (r << 16) | (g << 8) | b;
	}

	private static int lighten(int argb) {
		int r = Math.min(255, ((argb >> 16) & 0xFF) + 32);
		int g = Math.min(255, ((argb >> 8) & 0xFF) + 32);
		int b = Math.min(255, (argb & 0xFF) + 32);
		return 0xFF000000 | (r << 16) | (g << 8) | b;
	}

	private boolean isInRect(int mouseX, int mouseY, int rx, int ry, int rw, int rh) {
		int lx = mouseX - guiLeft;
		int ly = mouseY - guiTop;
		return lx >= rx && lx < rx + rw && ly >= ry && ly < ry + rh;
	}

	private void drawTex(double x, double y, double u, double v, double w, double h) {
		Tessellator t = Tessellator.instance;
		t.startDrawingQuads();
		t.addVertexWithUV(x,     y + h, this.zLevel, u / TEX_W,       (v + h) / 256.0D);
		t.addVertexWithUV(x + w, y + h, this.zLevel, (u + w) / TEX_W, (v + h) / 256.0D);
		t.addVertexWithUV(x + w, y,     this.zLevel, (u + w) / TEX_W, v / 256.0D);
		t.addVertexWithUV(x,     y,     this.zLevel, u / TEX_W,       v / 256.0D);
		t.draw();
	}
}
