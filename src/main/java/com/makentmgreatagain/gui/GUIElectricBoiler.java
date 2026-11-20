package com.makentmgreatagain.gui;

import com.hbm.inventory.fluid.Fluids;
import com.hbm.inventory.gui.GuiInfoContainer;
import com.makentmgreatagain.container.ContainerElectricBoiler;
import com.makentmgreatagain.tileentity.TileEntityElectricBoiler;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.opengl.GL11;

/**
 * 电锅炉界面（完整移植原版 GUIMachineBoilerElectric，176×168）。
 * 通电指示 18×18 @(+97,+34)；热量条 6×17 @(+103,·)；电力条 7×34 @(+123,·)；
 * 双流体罐 16×52 @+62/+134；左侧信息面板 16×16（产热/沸腾速率/错误）。
 * 悬停说明沿用原版英文文案。
 */
public class GUIElectricBoiler extends GuiInfoContainer {

	public static final ResourceLocation texture = new ResourceLocation("makentmgreatagain:textures/gui/gui_boiler_electric.png");

	private TileEntityElectricBoiler boiler;

	public GUIElectricBoiler(InventoryPlayer invPlayer, TileEntityElectricBoiler te) {
		super(new ContainerElectricBoiler(invPlayer, te));
		this.boiler = te;
		this.xSize = 176;
		this.ySize = 168;
	}

	@Override
	public void drawScreen(int mouseX, int mouseY, float f) {
		super.drawScreen(mouseX, mouseY, f);

		this.boiler.tanks[0].renderTankInfo(this, mouseX, mouseY, this.guiLeft + 62, this.guiTop + 69 - 52, 16, 52);
		this.boiler.tanks[1].renderTankInfo(this, mouseX, mouseY, this.guiLeft + 134, this.guiTop + 69 - 52, 16, 52);

		drawCustomInfoStat(mouseX, mouseY, this.guiLeft + 102, this.guiTop + 16, 8, 18, mouseX, mouseY,
				new String[] { String.valueOf((int) (this.boiler.heat / 100.0D)) + "°C" });

		// 产热/耗热说明（文案走 lang，单键内用 $ 分行，与附属既有约定一致）
		String[] text = langLines("makentmgreatagain.electric_boiler.heat_info");
		drawCustomInfoStat(mouseX, mouseY, this.guiLeft - 16, this.guiTop + 36, 16, 16, this.guiLeft - 8, this.guiTop + 36 + 16, text);

		String[] text1 = langLines("makentmgreatagain.electric_boiler.rate_info");
		drawCustomInfoStat(mouseX, mouseY, this.guiLeft - 16, this.guiTop + 36 + 16, 16, 16, this.guiLeft - 8, this.guiTop + 36 + 16, text1);

		if(this.boiler.tanks[1].getTankType().name().equals(Fluids.NONE.name())) {
			String[] text2 = langLines("makentmgreatagain.electric_boiler.error_info");
			drawCustomInfoStat(mouseX, mouseY, this.guiLeft - 16, this.guiTop + 36 + 32, 16, 16, this.guiLeft - 8, this.guiTop + 36 + 16 + 32, text2);
		}

		drawElectricityInfo(this, mouseX, mouseY, this.guiLeft + 123, this.guiTop + 69 - 34, 7, 34, this.boiler.power, TileEntityElectricBoiler.MAX_POWER);
	}

	@Override
	protected void drawGuiContainerForegroundLayer(int i, int j) {
		String name = this.boiler.hasCustomInventoryName() ? this.boiler.getInventoryName() : I18n.format(this.boiler.getInventoryName());
		this.fontRendererObj.drawString(name, this.xSize / 2 - this.fontRendererObj.getStringWidth(name) / 2, 6, 4210752);
		this.fontRendererObj.drawString(I18n.format("container.inventory"), 8, this.ySize - 96 + 2, 4210752);
	}

	@Override
	protected void drawGuiContainerBackgroundLayer(float f, int i, int j) {
		GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
		Minecraft.getMinecraft().getTextureManager().bindTexture(texture);
		this.drawTexturedModalRect(this.guiLeft, this.guiTop, 0, 0, this.xSize, this.ySize);

		if(this.boiler.getWorldObj().getTileEntity(this.boiler.xCoord, this.boiler.yCoord, this.boiler.zCoord) instanceof TileEntityElectricBoiler)
			this.boiler = (TileEntityElectricBoiler) this.boiler.getWorldObj().getTileEntity(this.boiler.xCoord, this.boiler.yCoord, this.boiler.zCoord);

		if(this.boiler.power > 0L) this.drawTexturedModalRect(this.guiLeft + 97, this.guiTop + 34, 176, 0, 18, 18);

		int heat = this.boiler.getHeatScaled(17);
		this.drawTexturedModalRect(this.guiLeft + 103, this.guiTop + 33 - heat, 194, 16 - heat, 6, heat);

		int power = (int) this.boiler.getPowerScaled(34);
		this.drawTexturedModalRect(this.guiLeft + 123, this.guiTop + 69 - power, 200, 34 - power, 7, power);

		drawInfoPanel(this.guiLeft - 16, this.guiTop + 36, 16, 16, 2);
		drawInfoPanel(this.guiLeft - 16, this.guiTop + 36 + 16, 16, 16, 3);
		if(this.boiler.tanks[1].getTankType().name().equals(Fluids.NONE.name())) drawInfoPanel(this.guiLeft - 16, this.guiTop + 36 + 32, 16, 16, 6);

		this.boiler.tanks[0].renderTank(this.guiLeft + 62, this.guiTop + 69, this.zLevel, 16, 52);
		this.boiler.tanks[1].renderTank(this.guiLeft + 134, this.guiTop + 69, this.zLevel, 16, 52);
	}

	/** 读一条 lang 键并按 $ 拆成多行（1.7.10 的 lang 不转义 \n，附属约定用 $ 分行） */
	private static String[] langLines(String key) {
		return I18n.format(key).split("\\$", -1);
	}
}