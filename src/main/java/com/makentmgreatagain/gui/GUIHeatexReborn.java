package com.makentmgreatagain.gui;

import java.util.Arrays;

import org.apache.commons.lang3.math.NumberUtils;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

import com.hbm.inventory.gui.GuiInfoContainer;
import com.hbm.packet.PacketDispatcher;
import com.hbm.packet.toserver.NBTControlPacket;
import com.makentmgreatagain.container.ContainerHeatexReborn;
import com.makentmgreatagain.tileentity.TileEntityHeatexReborn;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;

/**
 * 集成式换热加热器的 GUI。
 *
 * 直接复用原版 gui_heatex.png（176×204）：左右两个竖槽是换热回路的输入/输出储罐，
 * 中间沿用原版的两个输入框——「每次换热量」和「循环间隔」，用来调节换热流量。
 * 识别码槽位与原版相同（80,72），只接受流体识别码。
 */
public class GUIHeatexReborn extends GuiInfoContainer {

	private static final ResourceLocation TEXTURE =
		new ResourceLocation("hbm:textures/gui/machine/gui_heatex.png");

	private TileEntityHeatexReborn heatex;
	private GuiTextField fieldCycles;
	private GuiTextField fieldDelay;

	public GUIHeatexReborn(InventoryPlayer invPlayer, TileEntityHeatexReborn te) {
		super(new ContainerHeatexReborn(invPlayer, te));
		this.heatex = te;
		this.xSize = 176;
		this.ySize = 204;
	}

	@Override
	public void initGui() {

		super.initGui();

		Keyboard.enableRepeatEvents(true);
		this.fieldCycles = new GuiTextField(this.fontRendererObj, guiLeft + 73, guiTop + 31, 30, 10);
		initText(this.fieldCycles);
		this.fieldCycles.setText(String.valueOf(heatex.amountToCool));

		this.fieldDelay = new GuiTextField(this.fontRendererObj, guiLeft + 73, guiTop + 49, 30, 10);
		initText(this.fieldDelay);
		this.fieldDelay.setText(String.valueOf(heatex.tickDelay));
	}

	private void initText(GuiTextField field) {
		field.setTextColor(0x00ff00);
		field.setDisabledTextColour(0x00ff00);
		field.setEnableBackgroundDrawing(false);
		field.setMaxStringLength(5);
	}

	@Override
	public void drawScreen(int mouseX, int mouseY, float interp) {
		super.drawScreen(mouseX, mouseY, interp);

		heatex.tanks[0].renderTankInfo(this, mouseX, mouseY, guiLeft + 44, guiTop + 36, 16, 52);
		heatex.tanks[1].renderTankInfo(this, mouseX, mouseY, guiLeft + 116, guiTop + 36, 16, 52);

		if(this.inside(mouseX, mouseY, 70, 26, 36, 18)) {
			func_146283_a(Arrays.asList(I18n.format("info.heatex_reborn.amount_per_cycle")), mouseX, mouseY);
		}
		if(this.inside(mouseX, mouseY, 70, 44, 36, 18)) {
			func_146283_a(Arrays.asList(I18n.format("info.heatex_reborn.cycle_delay")), mouseX, mouseY);
		}
	}

	private boolean inside(int mouseX, int mouseY, int x, int y, int w, int h) {
		int lx = mouseX - guiLeft;
		int ly = mouseY - guiTop;
		return lx >= x && lx < x + w && ly >= y && ly < y + h;
	}

	@Override
	protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
		String name = I18n.format(this.heatex.getInventoryName());
		this.fontRendererObj.drawString(name, this.xSize / 2 - this.fontRendererObj.getStringWidth(name) / 2, 6, 4210752);
		this.fontRendererObj.drawString(I18n.format("container.inventory"), 8, this.ySize - 96 + 2, 4210752);
	}

	@Override
	protected void drawGuiContainerBackgroundLayer(float interp, int mouseX, int mouseY) {
		GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
		Minecraft.getMinecraft().getTextureManager().bindTexture(TEXTURE);
		this.drawTexturedModalRect(guiLeft, guiTop, 0, 0, xSize, ySize);

		// 换热回路：竖直液面
		heatex.tanks[0].renderTank(guiLeft + 44, guiTop + 88, this.zLevel, 16, 52);
		heatex.tanks[1].renderTank(guiLeft + 116, guiTop + 88, this.zLevel, 16, 52);

		this.fieldCycles.drawTextBox();
		this.fieldDelay.drawTextBox();
	}

	@Override
	protected void mouseClicked(int mouseX, int mouseY, int button) {
		super.mouseClicked(mouseX, mouseY, button);

		this.fieldCycles.mouseClicked(mouseX, mouseY, button);
		this.fieldDelay.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	protected void keyTyped(char c, int key) {

		if(this.fieldCycles.textboxKeyTyped(c, key)) {
			int cycles = Math.max(NumberUtils.toInt(this.fieldCycles.getText()), 1);
			NBTTagCompound data = new NBTTagCompound();
			data.setInteger("toCool", cycles);
			PacketDispatcher.wrapper.sendToServer(new NBTControlPacket(data, heatex.xCoord, heatex.yCoord, heatex.zCoord));
			return;
		}
		if(this.fieldDelay.textboxKeyTyped(c, key)) {
			int delay = Math.max(NumberUtils.toInt(this.fieldDelay.getText()), 1);
			NBTTagCompound data = new NBTTagCompound();
			data.setInteger("delay", delay);
			PacketDispatcher.wrapper.sendToServer(new NBTControlPacket(data, heatex.xCoord, heatex.yCoord, heatex.zCoord));
			return;
		}

		super.keyTyped(c, key);
	}

	@Override
	public void onGuiClosed() {
		super.onGuiClosed();
		Keyboard.enableRepeatEvents(false);
	}
}
