package com.makentmgreatagain.gui;

import org.lwjgl.opengl.GL11;

import com.hbm.inventory.gui.GuiInfoContainer;
import com.hbm.packet.PacketDispatcher;
import com.hbm.packet.toserver.NBTControlPacket;
import com.hbm.util.i18n.I18nUtil;
import com.makentmgreatagain.MakeNTMGreatAgain;
import com.makentmgreatagain.container.ContainerLargeCentrifuge;
import com.makentmgreatagain.tileentity.TileEntityLargeCentrifuge;

import net.minecraft.client.renderer.Tessellator;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.MathHelper;
import net.minecraft.util.ResourceLocation;

/**
 * 大型离心机界面。
 */
public class GUILargeCentrifuge extends GuiInfoContainer {

	private static final ResourceLocation TEXTURE =
			new ResourceLocation(MakeNTMGreatAgain.MODID, "textures/gui/gui_large_centrifuge.png");
	private static final int TEX_W = 320;
	private static final int TEX_H = 256;

	/* 电力条 */
	private static final int POWER_X = 229;
	private static final int POWER_Y = 18;
	private static final int POWER_W = 16;
	private static final int POWER_H = 66;

	/* 输入罐 / 输出罐 / 冷却剂罐 */
	private static final int IN_X = 7, IN_Y = 13, IN_W = 16, IN_H = 53;
	private static final int OUT_X = 137, OUT_Y = 30, OUT_W = 16, OUT_H = 36, OUT_STEP = 18;
	private static final int COOL_Y = 118, COOL_W = 7, COOL_H = 34;
	private static final int COOL_X_COLD = 231, COOL_X_HOT = 240;

	/** 面板底边：贴图 y=212 以下是那排箭头装饰，不画 */
	private static final int PANEL_H = 212;
	/** (25,144) 往左下是贴图里多余的控件素材（滑块 I~IV 数字和手柄），不采样 */
	private static final int CUT_X = 25, CUT_Y = 144;

	/* 流体产物动画：素材 UV (27,212)，179×26，从左至右填充 */
	private static final int FLUID_U = 27, FLUID_V = 212, FLUID_W = 179, FLUID_H = 26;
	private static final int FLUID_X = 24, FLUID_Y = 17;

	/* 并行倍率滑块：4 档，档位 y 自下而上 133 / 128 / 123 / 118 */
	private static final int SLIDER_X = 12;
	private static final int SLIDER_BOTTOM = 133;
	private static final int SLIDER_STEP = 5;
	private static final int SLIDER_W = 8, SLIDER_H = 4;
	/** 拖动块在贴图里的位置（8×4） */
	private static final int HANDLE_U = 12, HANDLE_V = 144;
	/** 左侧罗马数字：贴图 (0,144)~(0,162) 共 4 帧，每帧 9×6，自下而上是 I~IV */
	private static final int NUMERAL_X = 4;
	private static final int NUMERAL_U = 0, NUMERAL_V_TOP = 144, NUMERAL_STEP = 6, NUMERAL_W = 9, NUMERAL_H = 6;
	/** 拖动块的点击判定向外各放 4 像素，否则 8×4 太难点中 */
	private static final int HANDLE_PAD = 4;

	private final TileEntityLargeCentrifuge tile;

	/** 鼠标是否正按在滑块上拖动 */
	private boolean draggingSlider;

	public GUILargeCentrifuge(InventoryPlayer inventory, TileEntityLargeCentrifuge tile) {
		super(new ContainerLargeCentrifuge(inventory, tile));
		this.tile = tile;
		this.xSize = 253;
		this.ySize = 226;
	}

	@Override
	public void drawScreen(int mouseX, int mouseY, float partial) {
		super.drawScreen(mouseX, mouseY, partial);

		// 电力条悬浮提示
		this.drawElectricityInfo(this, mouseX, mouseY, guiLeft + POWER_X, guiTop + POWER_Y, POWER_W, POWER_H, this.tile.getPower(), this.tile.getMaxPower());

		// 流体罐悬浮提示：没有流体时类型就是 NONE，显示“无”
		this.tile.tankIn.renderTankInfo(this, mouseX, mouseY, guiLeft + IN_X, guiTop + IN_Y, IN_W, IN_H);
		for(int i = 0; i < 4; i++) {
			this.tile.tanksOut[i].renderTankInfo(this, mouseX, mouseY, guiLeft + OUT_X + i * OUT_STEP, guiTop + OUT_Y, OUT_W, OUT_H);
		}
		this.tile.coolantCold.renderTankInfo(this, mouseX, mouseY, guiLeft + COOL_X_COLD, guiTop + COOL_Y, COOL_W, COOL_H);
		this.tile.coolantHot.renderTankInfo(this, mouseX, mouseY, guiLeft + COOL_X_HOT, guiTop + COOL_Y, COOL_W, COOL_H);

		// 并行倍率滑块悬浮说明
		if(isOverSlider(mouseX, mouseY)) {
			int mode = getSliderMode();
			this.drawCustomInfoStat(mouseX, mouseY, guiLeft + SLIDER_X - 1, guiTop + sliderY(mode) - HANDLE_PAD,
					SLIDER_W + 2, SLIDER_H + HANDLE_PAD * 2, mouseX, mouseY, new String[] {
							I18nUtil.resolveKey("container.large_centrifuge.parallel"),
							I18nUtil.resolveKey("container.large_centrifuge.parallel." + (mode + 1))
					});
		}
	}

	@Override
	protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
		this.fontRendererObj.drawString(I18nUtil.resolveKey("container.large_centrifuge"), 91, 6, 0x404040);
		this.fontRendererObj.drawString(I18nUtil.resolveKey("container.inventory"), 35, 119, 0x404040);
	}

	@Override
	protected void drawGuiContainerBackgroundLayer(float partial, int mouseX, int mouseY) {

		GL11.glColor4f(1F, 1F, 1F, 1F);
		this.mc.getTextureManager().bindTexture(TEXTURE);

		int x = (this.width - this.xSize) / 2;
		int y = (this.height - this.ySize) / 2;

		// 上方照常整块画；从 (25,144) 往左下是贴图里多余的控件素材，直接不参与采样
		drawTexturedModalRect(x, y, 0, 0, this.xSize, CUT_Y);
		drawTexturedModalRect(x + CUT_X, y + CUT_Y, CUT_X, CUT_Y, this.xSize - CUT_X, PANEL_H - CUT_Y);

		// 并行倍率滑块（拖动块 + 左侧当前档位的罗马数字）
		drawParallelSlider(x, y);

		// 电力条
		int powerH = (int) (POWER_H * this.tile.getPower() / (double) this.tile.getMaxPower());
		if(powerH > 0) drawTexturedModalRect(x + POWER_X, y + POWER_Y + POWER_H - powerH, 253, POWER_H - powerH, POWER_W, powerH);

		// 物品产物的动画：四条依次自下向上填充
		float progress = this.tile.animItems;
		float stage = progress * 4F;

		for(int i = 0; i < 4; i++) {
			float fill = Math.min(1F, Math.max(0F, stage - i));
			if(fill <= 0F) break;
			int h = (int) (34F * fill);
			drawTexturedModalRect(x + 58 + i * 20, y + 34 + 34 - h, 253, 66 + 34 - h, 12, h);
		}

		// 流体产物的动画：从左至右填充，只有产物含流体时才会亮
		if(this.tile.animFluids > 0F) {
			int w = (int) Math.ceil(FLUID_W * this.tile.animFluids);
			if(w > 0) drawTexturedModalRect(x + FLUID_X, y + FLUID_Y, FLUID_U, FLUID_V, w, FLUID_H);
		}

		// 流体罐放在最后画：renderTank 会切换成流体贴图
		this.tile.tankIn.renderTank(x + IN_X, y + IN_Y + IN_H, this.zLevel, IN_W, IN_H);
		for(int i = 0; i < 4; i++) {
			this.tile.tanksOut[i].renderTank(x + OUT_X + i * OUT_STEP, y + OUT_Y + OUT_H, this.zLevel, OUT_W, OUT_H);
		}
		this.tile.coolantCold.renderTank(x + COOL_X_COLD, y + COOL_Y + COOL_H, this.zLevel, COOL_W, COOL_H);
		this.tile.coolantHot.renderTank(x + COOL_X_HOT, y + COOL_Y + COOL_H, this.zLevel, COOL_W, COOL_H);
	}

	/** 贴图宽度 320，按真实宽度换算 UV */
	@Override
	public void drawTexturedModalRect(int x, int y, int u, int v, int w, int h) {
		drawTexturedRect(x, y, u, v, w, h, TEX_W, TEX_H);
	}

	/* ---------------- 并行倍率滑块 ---------------- */

	/** 当前档位（0 = I 档 / 1 份，3 = IV 档 / 8 份） */
	private int getSliderMode() {
		return MathHelper.clamp_int(this.tile.parallelMode, 0, 3);
	}

	/** 档位对应的拖动块 y（相对界面左上角）：I 在最下，IV 在最上 */
	private int sliderY(int mode) {
		return SLIDER_BOTTOM - mode * SLIDER_STEP;
	}

	/** 画拖动块和左侧当前档位的罗马数字（只画一帧，不是四个都画） */
	private void drawParallelSlider(int guiX, int guiY) {

		int mode = getSliderMode();
		int y = sliderY(mode);

		// 罗马数字：贴图自下而上 I~IV，正好对应 mode 0~3
		drawTexturedModalRect(guiX + NUMERAL_X, guiY + y - 1,
				NUMERAL_U, NUMERAL_V_TOP + (3 - mode) * NUMERAL_STEP, NUMERAL_W, NUMERAL_H);

		// 拖动块
		drawTexturedModalRect(guiX + SLIDER_X, guiY + y, HANDLE_U, HANDLE_V, SLIDER_W, SLIDER_H);
	}

	/** 鼠标是否落在拖动块上（判定区比贴图略大，方便抓取） */
	private boolean isOverSlider(int mouseX, int mouseY) {

		int y = guiTop + sliderY(getSliderMode());

		return mouseX >= guiLeft + SLIDER_X - 1 && mouseX < guiLeft + SLIDER_X + SLIDER_W + 1
				&& mouseY >= y - HANDLE_PAD && mouseY < y + SLIDER_H + HANDLE_PAD;
	}

	/** 拖动时把鼠标位置吸附到最近的档位 */
	private void dragSlider(int mouseY) {

		int rel = mouseY - this.guiTop;
		int mode = Math.round((SLIDER_BOTTOM - rel) / (float) SLIDER_STEP);
		setSliderMode(MathHelper.clamp_int(mode, 0, 3));
	}

	/** 客户端先改本地值让拖动跟手，再发 NBT 控制包让服务端写入权威值 */
	private void setSliderMode(int mode) {

		if(this.tile.parallelMode == mode) return;

		this.tile.parallelMode = mode;

		NBTTagCompound data = new NBTTagCompound();
		data.setByte("parallelMode", (byte) mode);
		PacketDispatcher.wrapper.sendToServer(
				new NBTControlPacket(data, this.tile.xCoord, this.tile.yCoord, this.tile.zCoord));
	}

	/** 只有按在拖动块上才开始拖，按下不交给 GuiContainer，避免顺手拿起槽里的物品 */
	@Override
	protected void mouseClicked(int mouseX, int mouseY, int button) {

		if(button == 0 && isOverSlider(mouseX, mouseY)) {
			this.draggingSlider = true;
			dragSlider(mouseY);
			return;
		}

		super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	protected void mouseClickMove(int mouseX, int mouseY, int button, long timeSinceLastClick) {

		if(this.draggingSlider && button == 0) {
			dragSlider(mouseY);
			return;
		}

		super.mouseClickMove(mouseX, mouseY, button, timeSinceLastClick);
	}

	@Override
	protected void mouseMovedOrUp(int mouseX, int mouseY, int which) {

		// which = 0 表示左键松开，结束拖动
		if(this.draggingSlider && which == 0) {
			this.draggingSlider = false;
			return;
		}

		super.mouseMovedOrUp(mouseX, mouseY, which);
	}

	/** 按指定贴图尺寸换算 UV 画矩形（本体 gui_utility.png 是 256×256） */
	private void drawTexturedRect(int x, int y, int u, int v, int w, int h, int texW, int texH) {

		float u0 = u / (float) texW;
		float v0 = v / (float) texH;
		float u1 = (u + w) / (float) texW;
		float v1 = (v + h) / (float) texH;

		Tessellator tessellator = Tessellator.instance;
		tessellator.startDrawingQuads();
		tessellator.addVertexWithUV(x, y + h, this.zLevel, u0, v1);
		tessellator.addVertexWithUV(x + w, y + h, this.zLevel, u1, v1);
		tessellator.addVertexWithUV(x + w, y, this.zLevel, u1, v0);
		tessellator.addVertexWithUV(x, y, this.zLevel, u0, v0);
		tessellator.draw();
	}
}
