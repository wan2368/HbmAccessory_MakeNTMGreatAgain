package com.makentmgreatagain.gui;
import java.util.ArrayList;
import java.util.List;
import org.lwjgl.opengl.GL11;

import com.hbm.packet.PacketDispatcher;
import com.hbm.packet.toserver.NBTControlPacket;
import com.hbm.util.BobMathUtil;
import com.makentmgreatagain.container.ContainerAlloyShredder;
import com.makentmgreatagain.tileentity.TileEntityAlloyShredder;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;

/**
 * 高级粉碎机的 GUI。贴图 gui_alloy_shredder.png（320×256）。
 */
public class GUIAlloyShredder extends GuiContainer {

	private static final ResourceLocation TEXTURE =
			new ResourceLocation("makentmgreatagain:textures/gui/gui_alloy_shredder.png");

	/** 贴图真实宽度（UV 换算用；高度仍是 256 不用改） */
	private static final double TEX_W = 320.0;

	/* ---------- 可调坐标（全部=贴图真实像素，1:1） ---------- */
	/** 能量条：位置/大小 + 素材区取帧坐标 */
	private static final int ENERGY_X = 10, ENERGY_Y = 39, ENERGY_W = 16, ENERGY_H = 60;
	private static final int ENERGY_U = 230, ENERGY_V = 87;          // 素材区电量帧左上角

	/** 进度箭头：位置 + 素材区 */
	private static final int PROGRESS_X = 135, PROGRESS_Y = 96, PROGRESS_W = 16, PROGRESS_H = 8;
	private static final int PROGRESS_U = 230, PROGRESS_V = 59;      // 素材区箭头帧左上角

	/** 4 个刀片状态灯：每盏的 x（y 同一行），素材区 4 列状态帧 */
	private static final int[] LAMP_X = {45, 63, 99, 117};
	private static final int LAMP_Y = 92, LAMP_SIZE = 18;
	private static final int LAMP_U = 230, LAMP_V = 0;              // 状态帧起点，每状态右移一帧
	private static final int LAMP_STEP = 18;                        // 状态帧之间的水平间距

	/** 红色开关：命中区与绘制位置，素材区两帧（关/开） */
	private static final int SWITCH_X = 72, SWITCH_Y = 112, SWITCH_W = 35, SWITCH_H = 15;
	private static final int SWITCH_U = 230, SWITCH_V = 72;         // 关帧位置
	private static final int SWITCH_ON_DX = 35;                     // 开帧与关帧的 x 间距（按图量）

	private TileEntityAlloyShredder shredder;

	public void click() {
		mc.getSoundHandler().playSound(net.minecraft.client.audio.PositionedSoundRecord.func_147674_a(
			new ResourceLocation("gui.button.press"), 1.0F));
	}

	public GUIAlloyShredder(InventoryPlayer invPlayer, TileEntityAlloyShredder te) {
		super(new ContainerAlloyShredder(invPlayer, te));
		this.shredder = te;
		this.xSize = 230;   // 只显示功能区（1:1 采样，无拉伸）
		this.ySize = 256;
	}

	@Override
	protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
		this.fontRendererObj.drawString(I18n.format("container.alloyShredder"), 44, 27, 4210752);
		this.fontRendererObj.drawString(I18n.format("container.inventory"), 44, 160, 4210752);
		drawHoverTips(mouseX, mouseY);
	}

	@Override
	protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
		GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
		Minecraft.getMinecraft().getTextureManager().bindTexture(TEXTURE);

		// 背景：只取贴图 0~230 列（1:1，素材区绝不参与）
		drawTex(guiLeft+2, guiTop+21, 0, 0, 230, 256);

		// 电力条
		int p = (int) shredder.getPowerScaled(ENERGY_H);
		if(p > 0) {
			drawTex(guiLeft + ENERGY_X, guiTop + ENERGY_Y + ENERGY_H - p,
					ENERGY_U, ENERGY_V + (ENERGY_H - p), ENERGY_W, p);
		}

		// 进度条
		int j = shredder.getProgressScaled(PROGRESS_W);
		if(j > 0) {
			drawTex(guiLeft + PROGRESS_X, guiTop + PROGRESS_Y,
					PROGRESS_U, PROGRESS_V, j, PROGRESS_H);
		}
		
		// ---- 刀片指示
		for(int i = 0; i < 4; i++) {
			if(shredder.getBladeState(i) == 0) continue;      
			int u = (i < 2) ? 230 : 248;
			drawTex(guiLeft + LAMP_X[i], guiTop + LAMP_Y, u, LAMP_V, 16, 16);
		}

		// ---- 开关：
		drawTex(guiLeft + SWITCH_X, guiTop + SWITCH_Y,
				SWITCH_U + (shredder.spread ? 0 : SWITCH_ON_DX), SWITCH_V, SWITCH_W, SWITCH_H);
	}

	/**
	 * 悬停提示画在前景层：它在物品图标之后绘制，且原版 drawHoveringText 内部
	 * 把 zLevel 提到 300（物品是 200），不会被槽内物品遮挡。
	 * 前景层 GL 已平移 (guiLeft, guiTop)，所以传坐标要减掉这个偏移。
	 */
	private void drawHoverTips(int mouseX, int mouseY) {
		int sx = mouseX - guiLeft;
		int sy = mouseY - guiTop;

		if(sx >= SWITCH_X && sx < SWITCH_X + SWITCH_W && sy >= SWITCH_Y && sy < SWITCH_Y + SWITCH_H) {
			List<String> tip = new ArrayList<String>();
			tip.add("均衡分配");
			tip.add(shredder.spread ? "§a开" : "§c关§r");
			this.func_146283_a(tip, sx, sy);
		}

		if(sx >= ENERGY_X && sx < ENERGY_X + ENERGY_W && sy >= ENERGY_Y && sy < ENERGY_Y + ENERGY_H) {
			List<String> tip = new ArrayList<String>();
			tip.add(BobMathUtil.getShortNumber(shredder.getPower()) + "/" + BobMathUtil.getShortNumber(shredder.getMaxPower()) + "HE");
			this.func_146283_a(tip, sx, sy);
		}
	}

	/**
	 * 贴图真实像素版 drawTexturedModalRect：x/y 是屏幕位置，u/v/w/h 是贴图真实像素。
	 * UV 换算 U=u/320、V=v/256 —— 这是修掉"素材区混进背景+比例失调"的核心。
	 */
	private void drawTex(double x, double y, double u, double v, double w, double h) {
		Tessellator t = Tessellator.instance;
		t.startDrawingQuads();
		t.addVertexWithUV(x,      y + h, this.zLevel, u / TEX_W,           (v + h) / 256.0);
		t.addVertexWithUV(x + w,  y + h, this.zLevel, (u + w) / TEX_W,     (v + h) / 256.0);
		t.addVertexWithUV(x + w,  y,     this.zLevel, (u + w) / TEX_W,     v / 256.0);
		t.addVertexWithUV(x,      y,     this.zLevel, u / TEX_W,           v / 256.0);
		t.draw();
	}

	@Override
	protected void mouseClicked(int x, int y, int button) {
		super.mouseClicked(x, y, button);

		// 命中开关区域 -> 发控制包给服务端翻转均分布尔
		if(button == 0 && this.hitRect(x, y, SWITCH_X, SWITCH_Y, SWITCH_W, SWITCH_H)) {
			this.click();                                    // ← 加这行，按钮音
			NBTTagCompound data = new NBTTagCompound();
			data.setBoolean("spread", true);
			PacketDispatcher.wrapper.sendToServer(
					new NBTControlPacket(data, shredder.xCoord, shredder.yCoord, shredder.zCoord));
		}
	}

	/** 鼠标点是否落在窗口内某矩形（坐标是相对窗口的，先换算） */
	private boolean hitRect(int mouseX, int mouseY, int rx, int ry, int rw, int rh) {
		int lx = mouseX - guiLeft;
		int ly = mouseY - guiTop;
		return lx >= rx && lx < rx + rw && ly >= ry && ly < ry + rh;
	}
}
