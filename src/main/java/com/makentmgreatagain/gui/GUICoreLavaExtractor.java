package com.makentmgreatagain.gui;

import org.lwjgl.opengl.GL11;

import com.hbm.inventory.gui.GuiInfoContainer;
import com.makentmgreatagain.MakeNTMGreatAgain;
import com.makentmgreatagain.container.ContainerCoreLavaExtractor;
import com.makentmgreatagain.init.ModNetwork;
import com.makentmgreatagain.packet.ExtractorSwitchPacket;
import com.makentmgreatagain.tileentity.TileEntityCoreLavaExtractor;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.util.ResourceLocation;

/**
 * 地核熔岩开采机 GUI（底图 {@code gui/core_lava_extractor.png}，210×196）。
 *
 * <p>底图 x≥210 的一列是素材区，不当背景画：电力条填充 (210,0)16×58、
 * 开关点亮图 (210,76)18×18、晶体座点亮图 (223,16)28×28、黄灯 (226,0)8×8、绿灯 (226,8)8×8，
 * 全部按需叠画。</p>
 *
 * <h3>叠画规则</h3>
 * <ul>
 *   <li>电力条：按存量自底向上截 (210,0) 那段画到 (186,8)；</li>
 *   <li>开关：running 时把 (210,76) 画到 (91,64)；鼠标点 (91,64)18×18 发切换包；</li>
 *   <li>晶体座：槽 0 有晶体时把 (223,16) 画到 (46,53)；</li>
 *   <li>三灯 (86,43)/(96,43)/(106,43)＝电力/输入流体/X晶体：条件满足画绿灯常亮，
 *       不满足画黄灯 0.5s 亮 0.5s 灭（世界时间 20 tick 一周期）。</li>
 * </ul>
 */
public class GUICoreLavaExtractor extends GuiInfoContainer {

	private static final ResourceLocation TEXTURE = new ResourceLocation(
			MakeNTMGreatAgain.MODID, "textures/gui/core_lava_extractor.png");

	private TileEntityCoreLavaExtractor te;

	public GUICoreLavaExtractor(InventoryPlayer invPlayer, TileEntityCoreLavaExtractor te) {
		super(new ContainerCoreLavaExtractor(invPlayer, te));
		this.te = te;
		this.xSize = 210;
		this.ySize = 196;
	}

	@Override
	protected void drawGuiContainerBackgroundLayer(float interp, int mouseX, int mouseY) {

		GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
		Minecraft.getMinecraft().getTextureManager().bindTexture(TEXTURE);

		// 背景只画 x<210 的部分，素材列不入背景
		this.drawTexturedModalRect(guiLeft, guiTop, 0, 0, xSize, ySize);

		// 三个液柱：renderTank 的 y 是液面底部，向上画
		te.tanks[0].renderTank(guiLeft + 8, guiTop + 8 + 57, this.zLevel, 16, 57);
		te.tanks[1].renderTank(guiLeft + 133, guiTop + 8 + 39, this.zLevel, 16, 39);
		te.tanks[2].renderTank(guiLeft + 151, guiTop + 8 + 39, this.zLevel, 16, 39);

		// renderTank 会改绑流体图标贴图，叠画素材前必须绑回底图，否则全采样成黑块
		Minecraft.getMinecraft().getTextureManager().bindTexture(TEXTURE);

		// 电力条：自底向上
		long power = te.power;
		if(power > 0) {
			int h = (int) (power * 58 / TileEntityCoreLavaExtractor.MAX_POWER);
			if(h > 58) h = 58;
			if(h > 0)
				this.drawTexturedModalRect(guiLeft + 186, guiTop + 8 + 58 - h, 210, 58 - h, 16, h);
		}

		// 开关点亮
		if(te.running)
			this.drawTexturedModalRect(guiLeft + 91, guiTop + 64, 210, 76, 18, 18);

		// 晶体座点亮
		if(te.hasCrystal())
			this.drawTexturedModalRect(guiLeft + 46, guiTop + 53, 226, 16, 28, 28);

		// 三灯：电力 / 输入流体 / X晶体
		boolean[] ok = {te.hasPower(), te.hasInput(), te.hasCrystal()};
		boolean blinkOn = Minecraft.getMinecraft().theWorld.getTotalWorldTime() % 20 < 10;
		for(int i = 0; i < 3; i++) {
			int lx = guiLeft + 86 + i * 10;
			int ly = guiTop + 43;
			if(ok[i]) {
				this.drawTexturedModalRect(lx, ly, 226, 8, 8, 8);
			} else if(blinkOn) {
				this.drawTexturedModalRect(lx, ly, 226, 0, 8, 8);
			}
		}
	}

	@Override
	public void drawScreen(int mouseX, int mouseY, float interp) {
		super.drawScreen(mouseX, mouseY, interp);

		te.tanks[0].renderTankInfo(this, mouseX, mouseY, guiLeft + 8, guiTop + 8, 16, 57);
		te.tanks[1].renderTankInfo(this, mouseX, mouseY, guiLeft + 133, guiTop + 8, 16, 39);
		te.tanks[2].renderTankInfo(this, mouseX, mouseY, guiLeft + 151, guiTop + 8, 16, 39);

		// 电力条悬停信息，照本体机器的写法
		this.drawElectricityInfo(this, mouseX, mouseY, guiLeft + 186, guiTop + 8, 16, 58, te.power, te.getMaxPower());
	}

	@Override
	protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
		this.fontRendererObj.drawString(I18n.format("container.inventory"), 25, this.ySize - 92, 4210752);
	}

	/** 点 (91,64) 的 18×18 开关区域：发切换请求，服务端校验三灯后才生效 */
	@Override
	protected void mouseClicked(int mx, int my, int button) {

		int rx = mx - guiLeft;
		int ry = my - guiTop;

		if(button == 0 && rx >= 91 && rx < 109 && ry >= 64 && ry < 82) {
			ModNetwork.NETWORK.sendToServer(
					new ExtractorSwitchPacket.Request(te.xCoord, te.yCoord, te.zCoord, !te.running));
			return;
		}

		super.mouseClicked(mx, my, button);
	}
}
