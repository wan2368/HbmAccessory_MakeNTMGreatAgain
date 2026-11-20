package com.makentmgreatagain.gui;

import org.lwjgl.opengl.GL11;

import com.makentmgreatagain.MakeNTMGreatAgain;
import com.makentmgreatagain.container.ContainerIndustrialGasTurbine;
import com.makentmgreatagain.init.ModNetwork;
import com.makentmgreatagain.packet.TurbineControlPacket;
import com.makentmgreatagain.tileentity.TileEntityIndustrialGasTurbine;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.util.ResourceLocation;

/**
 * 工业燃气轮机 GUI（底图 {@code gui/industial_gas_turbine.png}，256×256，背景只画 176×194）。
 *
 * <p>素材区：电力条截面 (176,0)16×52、开关点亮 (176,52)18×18、滑阀控件 (176,70)4×8。
 * 润滑罐(8,12)16×52、燃料罐(60,12)16×52、电力条(152,13)16×52。
 * 开关(105,23)18×18；滑阀轨(98,57)32×2，控件4×8，10挡0~50mB/t。</p>
 */
public class GUIIndustrialGasTurbine extends com.hbm.inventory.gui.GuiInfoContainer {

	private static final ResourceLocation TEXTURE = new ResourceLocation(
			MakeNTMGreatAgain.MODID, "textures/gui/industial_gas_turbine.png");

	private static final int W = 176, H = 194;

	private TileEntityIndustrialGasTurbine te;
	private boolean dragging;

	public GUIIndustrialGasTurbine(InventoryPlayer invPlayer, TileEntityIndustrialGasTurbine te) {
		super(new ContainerIndustrialGasTurbine(invPlayer, te));
		this.te = te;
		this.xSize = W;
		this.ySize = H;
	}

	@Override
	protected void drawGuiContainerBackgroundLayer(float interp, int mouseX, int mouseY) {

		GL11.glColor4f(1F, 1F, 1F, 1F);
		Minecraft.getMinecraft().getTextureManager().bindTexture(TEXTURE);
		this.drawTexturedModalRect(guiLeft, guiTop, 0, 0, W, H);

		// 双液柱（y 为液面底部，向上画）
		te.tanks[0].renderTank(guiLeft + 8, guiTop + 12 + 52, this.zLevel, 16, 52);
		te.tanks[1].renderTank(guiLeft + 60, guiTop + 12 + 52, this.zLevel, 16, 52);

		Minecraft.getMinecraft().getTextureManager().bindTexture(TEXTURE);

		// 电力条自底向上
		if(te.power > 0) {
			int h = (int) (te.power * 52 / TileEntityIndustrialGasTurbine.MAX_POWER);
			if(h > 52) h = 52;
			if(h > 0) this.drawTexturedModalRect(guiLeft + 152, guiTop + 13 + 52 - h, 176, 52 - h, 16, h);
		}

		// 开关点亮
		if(te.running) this.drawTexturedModalRect(guiLeft + 105, guiTop + 23, 176, 52, 18, 18);

		// 滑阀控件：10挡，97..129
		int knobX = 97 + (int) Math.round(te.throttle * (32D - 4D) / 9D);
		this.drawTexturedModalRect(guiLeft + knobX, guiTop + 54, 176, 70, 4, 8);
	}

	@Override
	public void drawScreen(int mouseX, int mouseY, float interp) {
		super.drawScreen(mouseX, mouseY, interp);

		te.tanks[0].renderTankInfo(this, mouseX, mouseY, guiLeft + 8, guiTop + 12, 16, 52);
		te.tanks[1].renderTankInfo(this, mouseX, mouseY, guiLeft + 60, guiTop + 12, 16, 52);
		this.drawElectricityInfo(this, mouseX, mouseY, guiLeft + 152, guiTop + 13, 16, 52, te.power, te.getMaxPower());

		int rx = mouseX - guiLeft, ry = mouseY - guiTop;

		// 涡轮运行读数提示
		if(rx >= 46 && rx < 54 && ry >= 70 && ry < 78) {
			java.util.List<String> tooltip = new java.util.ArrayList<String>();
			tooltip.add(I18n.format("mnga.turbine.readout.flow", String.format(java.util.Locale.ROOT, "%.2f", te.lastConsumption)));
			tooltip.add(I18n.format("mnga.turbine.readout.power", te.lastProduction));
			this.drawHoveringText(tooltip, mouseX, mouseY, this.fontRendererObj);
		}

		// 开关悬停
		if(rx >= 105 && rx < 123 && ry >= 23 && ry < 41)
			this.drawCreativeTabHoveringText(I18n.format(te.running ? "mnga.turbine.off" : "mnga.turbine.on"), mouseX, mouseY);

		// 滑阀悬停：当前挡位流量
		if(rx >= 95 && rx < 131 && ry >= 52 && ry < 64)
			this.drawCreativeTabHoveringText(I18n.format("mnga.turbine.throttle", te.throttle * 5), mouseX, mouseY);

		if(dragging) updateSlider(mouseX);
	}

	@Override
	protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
		this.fontRendererObj.drawString(I18n.format("mnga.turbine.title"), 95, 5, 4210752);
		this.fontRendererObj.drawString(I18n.format("container.inventory"), 8, this.ySize - 92, 4210752);
	}

	@Override
	protected void mouseClicked(int mx, int my, int button) {
		int rx = mx - guiLeft, ry = my - guiTop;

		if(button == 0 && rx >= 105 && rx < 123 && ry >= 23 && ry < 41) {
			this.mc.getSoundHandler().playSound(net.minecraft.client.audio.PositionedSoundRecord.func_147674_a(
					new ResourceLocation("hbm:block.leverLarge"), 1.0F));
			ModNetwork.NETWORK.sendToServer(new TurbineControlPacket(te.xCoord, te.yCoord, te.zCoord, 0, te.running ? 0 : 1));
			return;
		}

		if(button == 0 && rx >= 95 && rx < 131 && ry >= 52 && ry < 64) {
			dragging = true;
			updateSlider(mx);
			return;
		}

		super.mouseClicked(mx, my, button);
	}

	@Override
	protected void mouseClickMove(int mx, int my, int button, long time) {
		if(dragging) updateSlider(mx);
		super.mouseClickMove(mx, my, button, time);
	}

	/** 照原版内燃机：松开鼠标即解除锁定，避免滑阀一直跟随鼠标 */
	@Override
	protected void mouseMovedOrUp(int mx, int my, int button) {
		super.mouseMovedOrUp(mx, my, button);
		if(dragging && (button == 0 || button == 1)) dragging = false;
	}

	@Override
	public void onGuiClosed() {
		dragging = false;
		super.onGuiClosed();
	}

	private void updateSlider(int mx) {
		int rx = mx - guiLeft;
		int knob = Math.max(97, Math.min(129, rx - 2));
		int det = (int) Math.round((knob - 97) * 9D / (32D - 4D));
		if(det != te.throttle) {
			te.throttle = det; // 客户端预显，服务端以包为准
			ModNetwork.NETWORK.sendToServer(new TurbineControlPacket(te.xCoord, te.yCoord, te.zCoord, 1, det));
		}
	}
}
