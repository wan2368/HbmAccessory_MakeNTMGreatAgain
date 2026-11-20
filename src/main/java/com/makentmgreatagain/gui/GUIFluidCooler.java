package com.makentmgreatagain.gui;

import org.lwjgl.opengl.GL11;

import com.hbm.inventory.gui.GuiInfoContainer;
import com.makentmgreatagain.MakeNTMGreatAgain;
import com.makentmgreatagain.container.ContainerFluidCooler;
import com.makentmgreatagain.tileentity.TileEntityFluidCooler;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.util.ResourceLocation;

/**
 * 流体冷却机 GUI。
 *
 * <p>底图 {@code gui_advanced_thermostat.png}（176×204）上四个竖液柱从左到右依次是
 * 冷却剂输入、被冷却流体输入、冷却后输出、加热后冷却剂输出；两个识别码槽在
 * 左下方，分别绑定前两个输入罐。</p>
 *
 * <p>液柱坐标按底图实测：外框 x = 21/52/103/134 与 40/71/122/153，
 * 内部 18 像素宽，居中画 16 像素液面，因此左上角 X = 23/54/105/136；
 * 顶部边框 y = 15、底部 y = 78，液面取 18 起、60 高。</p>
 */
public class GUIFluidCooler extends GuiInfoContainer {

	private static final ResourceLocation TEXTURE = new ResourceLocation(
			MakeNTMGreatAgain.MODID, "textures/gui/gui_advanced_thermostat.png");

	/** 四个液柱的左上角 X */
	private static final int[] TANK_X = {23, 54, 105, 136};
	private static final int TANK_Y = 18;
	private static final int TANK_W = 16;
	private static final int TANK_H = 58;

	private TileEntityFluidCooler cooler;

	public GUIFluidCooler(InventoryPlayer invPlayer, TileEntityFluidCooler te) {
		super(new ContainerFluidCooler(invPlayer, te));
		this.cooler = te;
		this.xSize = 176;
		this.ySize = 204;
	}

	@Override
	protected void drawGuiContainerBackgroundLayer(float interp, int mouseX, int mouseY) {

		GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
		Minecraft.getMinecraft().getTextureManager().bindTexture(TEXTURE);
		this.drawTexturedModalRect(guiLeft, guiTop, 0, 0, xSize, ySize);

		// renderTank 的 y 参数是液面底部，向上画 height 像素
		for(int i = 0; i < 4; i++) {
			cooler.tanks[i].renderTank(
					guiLeft + TANK_X[i], guiTop + TANK_Y + TANK_H, this.zLevel, TANK_W, TANK_H);
		}
	}

	@Override
	public void drawScreen(int mouseX, int mouseY, float interp) {

		super.drawScreen(mouseX, mouseY, interp);

		// renderTankInfo 的 y 参数是液面顶部
		for(int i = 0; i < 4; i++) {
			cooler.tanks[i].renderTankInfo(this, mouseX, mouseY,
					guiLeft + TANK_X[i], guiTop + TANK_Y, TANK_W, TANK_H);
		}

		// 识别码指向的流体不在配对表里时不做任何提示：
		// 按约定，装不进去的流体就是「不生效」，罐型保持原样。
	}

	@Override
	protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {

		// 底图顶部是盘管装饰，不放标题，只在物品栏上方写标签
		this.fontRendererObj.drawString(
				I18n.format("container.inventory"), 8, this.ySize - 96 + 2, 4210752);
	}
}
