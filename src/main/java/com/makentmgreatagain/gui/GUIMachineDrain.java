package com.makentmgreatagain.gui;

import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

import com.hbm.packet.PacketDispatcher;
import com.hbm.packet.toserver.NBTControlPacket;
import com.hbm.util.i18n.I18nUtil;
import com.makentmgreatagain.container.ContainerMachineDrain;
import com.makentmgreatagain.tileentity.TileEntityMachineDrain;

import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.ResourceLocation;

/**
 * 可调节排液管设置界面：直接输入排液流量上限（mB/t）与流体压强（PU）。
 */
public class GUIMachineDrain extends GuiContainer {

	private static final int PANEL_W = 196;
	private static final int PANEL_H = 124;

	private final TileEntityMachineDrain te;
	private GuiTextField flowField;
	private GuiTextField pressureField;
	private GuiButton applyButton;

	public GUIMachineDrain(TileEntityMachineDrain te) {
		super(new ContainerMachineDrain(te));
		this.te = te;
		this.xSize = PANEL_W;
		this.ySize = PANEL_H;
	}

	@Override
	@SuppressWarnings("unchecked")
	public void initGui() {
		super.initGui();
		Keyboard.enableRepeatEvents(true);

		this.flowField = new GuiTextField(this.fontRendererObj, guiLeft + 98, guiTop + 42, 64, 16);
		this.flowField.setMaxStringLength(4);
		this.flowField.setText(String.valueOf(this.te.flowLimit));
		this.flowField.setFocused(true);

		this.pressureField = new GuiTextField(this.fontRendererObj, guiLeft + 98, guiTop + 66, 64, 16);
		this.pressureField.setMaxStringLength(2);
		this.pressureField.setText(String.valueOf(this.te.tank.getPressure()));

		this.buttonList.clear();
		this.applyButton = new GuiButton(0, guiLeft + 58, guiTop + 94, 80, 20, "应用设置") {
			@Override
			public void drawButton(net.minecraft.client.Minecraft mc, int mouseX, int mouseY) {
				if(!this.visible) return;
				this.field_146123_n = mouseX >= this.xPosition && mouseY >= this.yPosition
						&& mouseX < this.xPosition + this.width && mouseY < this.yPosition + this.height;
				int bg = this.field_146123_n ? 0xFF727272 : 0xFF555555;
				drawRect(this.xPosition, this.yPosition, this.xPosition + this.width, this.yPosition + this.height, 0xFF1C1C1C);
				drawRect(this.xPosition + 1, this.yPosition + 1, this.xPosition + this.width - 1, this.yPosition + this.height - 1, 0xFF868686);
				drawRect(this.xPosition + 2, this.yPosition + 2, this.xPosition + this.width - 1, this.yPosition + this.height - 1, 0xFF494949);
				drawRect(this.xPosition + 2, this.yPosition + 2, this.xPosition + this.width - 2, this.yPosition + this.height - 2, bg);
				int textColor = this.field_146123_n ? 0xFFFFFF : 0xDCDCDC;
				this.drawCenteredString(mc.fontRenderer, this.displayString,
						this.xPosition + this.width / 2, this.yPosition + (this.height - 8) / 2, textColor);
			}
		};
		this.buttonList.add(this.applyButton);
	}

	@Override
	public void onGuiClosed() {
		super.onGuiClosed();
		Keyboard.enableRepeatEvents(false);
	}

	@Override
	public void updateScreen() {
		super.updateScreen();
		if(this.flowField != null) this.flowField.updateCursorCounter();
		if(this.pressureField != null) this.pressureField.updateCursorCounter();
	}

	@Override
	protected void actionPerformed(GuiButton button) {
		if(button.id == 0) {
			this.applyChanges();
		}
	}

	@Override
	protected void keyTyped(char typedChar, int keyCode) {
		if(keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER) {
			this.applyChanges();
			return;
		}
		if(keyCode == Keyboard.KEY_TAB) {
			boolean flowFocused = this.flowField.isFocused();
			this.flowField.setFocused(!flowFocused);
			this.pressureField.setFocused(flowFocused);
			return;
		}
		if(this.flowField.isFocused()) {
			if(isNumericKey(typedChar, keyCode)) this.flowField.textboxKeyTyped(typedChar, keyCode);
			return;
		}
		if(this.pressureField.isFocused()) {
			if(isNumericKey(typedChar, keyCode)) this.pressureField.textboxKeyTyped(typedChar, keyCode);
			return;
		}
		super.keyTyped(typedChar, keyCode);
	}

	private static boolean isNumericKey(char c, int keyCode) {
		return Character.isDigit(c)
				|| keyCode == Keyboard.KEY_BACK
				|| keyCode == Keyboard.KEY_DELETE
				|| keyCode == Keyboard.KEY_LEFT
				|| keyCode == Keyboard.KEY_RIGHT
				|| keyCode == Keyboard.KEY_HOME
				|| keyCode == Keyboard.KEY_END;
	}

	@Override
	protected void mouseClicked(int mouseX, int mouseY, int mouseButton) {
		super.mouseClicked(mouseX, mouseY, mouseButton);
		this.flowField.mouseClicked(mouseX, mouseY, mouseButton);
		this.pressureField.mouseClicked(mouseX, mouseY, mouseButton);
	}

	private void applyChanges() {
		int flow = this.te.flowLimit;
		int pressure = this.te.tank.getPressure();
		try {
			if(!this.flowField.getText().isEmpty()) {
				flow = Integer.parseInt(this.flowField.getText());
			}
		} catch(NumberFormatException ignored) { }
		try {
			if(!this.pressureField.getText().isEmpty()) {
				pressure = Integer.parseInt(this.pressureField.getText());
			}
		} catch(NumberFormatException ignored) { }

		flow = Math.max(this.te.minFlow, Math.min(this.te.maxFlow, flow));
		pressure = Math.max(0, Math.min(5, pressure));

		this.flowField.setText(String.valueOf(flow));
		this.pressureField.setText(String.valueOf(pressure));

		NBTTagCompound data = new NBTTagCompound();
		data.setInteger("flow", flow);
		data.setInteger("pressure", pressure);
		PacketDispatcher.wrapper.sendToServer(new NBTControlPacket(data, this.te.xCoord, this.te.yCoord, this.te.zCoord));

		mc.getSoundHandler().playSound(PositionedSoundRecord.func_147674_a(new ResourceLocation("gui.button.press"), 1.0F));
		this.mc.thePlayer.closeScreen();
	}

	@Override
	protected void drawGuiContainerBackgroundLayer(float interp, int mouseX, int mouseY) {
		GL11.glColor4f(1F, 1F, 1F, 1F);
		// 对齐 neutrino_locator.png 的工业中暗灰配色 (#676767 底色 + #868686 高光 + #555555 阴影 + #1C1C1C 外框)
		int x = guiLeft;
		int y = guiTop;
		int w = PANEL_W;
		int h = PANEL_H;

		// 外框（圆角切角 #1C1C1C）
		drawRect(x + 2, y, x + w - 2, y + h, 0xFF1C1C1C);
		drawRect(x, y + 2, x + w, y + h - 2, 0xFF1C1C1C);
		drawRect(x + 1, y + 1, x + w - 1, y + h - 1, 0xFF1C1C1C);

		// 左上亮边 (#868686) 与右下暗边 (#555555)
		drawRect(x + 2, y + 1, x + w - 3, y + h - 1, 0xFF868686);
		drawRect(x + 1, y + 2, x + w - 2, y + h - 3, 0xFF868686);
		drawRect(x + 3, y + 2, x + w - 1, y + h - 2, 0xFF555555);
		drawRect(x + 2, y + 3, x + w - 2, y + h - 1, 0xFF555555);
		drawRect(x + w - 3, y + 2, x + w - 2, y + h - 2, 0xFF555555);

		// 主体底色 (#676767)
		drawRect(x + 3, y + 3, x + w - 3, y + h - 3, 0xFF676767);

		this.flowField.drawTextBox();
		this.pressureField.drawTextBox();
	}

	@Override
	protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
		String title = I18nUtil.resolveKey(this.te.getBlockType().getUnlocalizedName() + ".name");
		this.fontRendererObj.drawString(title, (PANEL_W - this.fontRendererObj.getStringWidth(title)) / 2, 8, 0xE8E8E8);

		String status = this.te.tank.getTankType().getLocalizedName()
				+ " (" + this.te.tank.getFill() + "/" + this.te.tank.getMaxFill() + " mB, "
				+ this.te.tank.getPressure() + " PU)";
		this.fontRendererObj.drawString(status, (PANEL_W - this.fontRendererObj.getStringWidth(status)) / 2, 22, 0xC8C8C8);

		this.fontRendererObj.drawString("流量 (" + this.te.minFlow + "~" + this.te.maxFlow + "):", 14, 46, 0xE0E0E0);
		this.fontRendererObj.drawString("mB/t", 166, 46, 0xC8C8C8);

		this.fontRendererObj.drawString("压强 (0~5):", 14, 70, 0xE0E0E0);
		this.fontRendererObj.drawString("PU", 166, 70, 0xC8C8C8);
	}
}
