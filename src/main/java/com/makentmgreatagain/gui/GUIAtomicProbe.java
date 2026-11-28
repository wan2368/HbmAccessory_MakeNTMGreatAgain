package com.makentmgreatagain.gui;

import com.hbm.inventory.gui.GuiInfoContainer;
import com.hbm.packet.PacketDispatcher;
import com.hbm.packet.toserver.NBTControlPacket;
import com.makentmgreatagain.MakeNTMGreatAgain;
import com.makentmgreatagain.inventory.ContainerAtomicProbe;
import com.makentmgreatagain.tileentity.DeliveryRelayNetwork.ReachablePeer;
import com.makentmgreatagain.tileentity.TileEntityAtomicProbe;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * 投送探头 GUI（176×196）：
 * - 支持自定义名称（所有探头均可改名）
 * - 额外「连接管理」子页面：直接列出范围内所有对端探头（按名称与距离排序），一键连接/断开
 * - 主设置页：8 格过滤样本、定量模式、优先补满/轮询调度、轮询间隔、定量/接收上限、单次发送上限（1~256）
 */
public class GUIAtomicProbe extends GuiInfoContainer {

	private static final ResourceLocation TEX = new ResourceLocation(
			MakeNTMGreatAgain.MODID, "textures/gui/gui_atomic_probe.png");
	private static final ResourceLocation CLICK_SFX = new ResourceLocation("gui.button.press");

	private static final int FILTER_X = 8;
	private static final int FILTER_Y = 57;
	private static final int LINKS_PER_PAGE = 4;
	private static final int LINK_LABEL_X = 10;
	private static final int LINK_METRICS_X = 58;
	private static final int LINK_BUTTON_X = 139;

	private final TileEntityAtomicProbe probe;
	private GuiTextField nameField;
	private GuiTextField limitField;
	private GuiTextField maxSendField;

	private String lastSyncedName = "";
	private int lastSyncedLimit = -1;
	private int lastSyncedMaxSend = -1;
	private boolean lastSyncedReceiver = false;

	private boolean linkTab = false;
	private int linkPage = 0;
	private final List<ReachablePeer> nearbyPeers =
			new ArrayList<ReachablePeer>();

	public GUIAtomicProbe(InventoryPlayer invPlayer, TileEntityAtomicProbe probe) {
		super(new ContainerAtomicProbe(invPlayer, probe));
		this.probe = probe;
		this.xSize = 176;
		this.ySize = 196;
	}

	private int currentLimitValue() {
		return probe.isReceiver ? probe.stockLimit : probe.sendThreshold;
	}

	@Override
	public void initGui() {
		super.initGui();
		Keyboard.enableRepeatEvents(true);

		this.nameField = new GuiTextField(this.fontRendererObj, guiLeft + 34, guiTop + 33, 135, 12);
		this.nameField.setMaxStringLength(24);
		this.nameField.setEnableBackgroundDrawing(true);
		this.nameField.setTextColor(0xFFE8F4F8);
		this.lastSyncedName = probe.probeName == null ? "" : probe.probeName;
		this.nameField.setText(this.lastSyncedName);

		this.limitField = new GuiTextField(this.fontRendererObj, guiLeft + 34, guiTop + 93, 44, 12);
		this.limitField.setMaxStringLength(5);
		this.limitField.setEnableBackgroundDrawing(true);
		this.limitField.setTextColor(0xFFE8F4F8);

		this.maxSendField = new GuiTextField(this.fontRendererObj, guiLeft + 111, guiTop + 93, 58, 12);
		this.maxSendField.setMaxStringLength(3);
		this.maxSendField.setEnableBackgroundDrawing(true);
		this.maxSendField.setTextColor(0xFFE8F4F8);

		syncFieldsFromProbe();
		refreshNearbyPeers();
	}

	private void syncFieldsFromProbe() {
		this.lastSyncedReceiver = probe.isReceiver;
		this.lastSyncedLimit = currentLimitValue();
		this.lastSyncedMaxSend = probe.maxSendPerPulse;
		this.limitField.xPosition = guiLeft + (probe.isReceiver ? 111 : 34);
		this.limitField.width = probe.isReceiver ? 58 : 44;
		this.limitField.setText(String.valueOf(this.lastSyncedLimit));
		this.maxSendField.setText(String.valueOf(this.lastSyncedMaxSend));
	}

	private void refreshNearbyPeers() {
		nearbyPeers.clear();
		nearbyPeers.addAll(com.makentmgreatagain.tileentity.DeliveryRelayNetwork.findReachablePeers(probe));
		int maxPage = Math.max(0, (nearbyPeers.size() - 1) / LINKS_PER_PAGE);
		if(linkPage > maxPage) linkPage = maxPage;
	}

	@Override
	public void onGuiClosed() {
		commitAllFields();
		Keyboard.enableRepeatEvents(false);
		super.onGuiClosed();
	}

	@Override
	public void updateScreen() {
		super.updateScreen();
		if(nameField != null) {
			nameField.updateCursorCounter();
			String curName = probe.probeName == null ? "" : probe.probeName;
			if(!nameField.isFocused() && !curName.equals(lastSyncedName)) {
				nameField.setText(curName);
				lastSyncedName = curName;
			}
		}
		if(limitField != null && maxSendField != null) {
			limitField.updateCursorCounter();
			maxSendField.updateCursorCounter();
			int curLim = currentLimitValue();
			if(!limitField.isFocused() && !maxSendField.isFocused()
					&& (curLim != lastSyncedLimit || probe.maxSendPerPulse != lastSyncedMaxSend || probe.isReceiver != lastSyncedReceiver)) {
				syncFieldsFromProbe();
			}
		}
		if(linkTab && (mc.theWorld.getTotalWorldTime() % 20L == 0L)) {
			refreshNearbyPeers();
		}
	}

	private void commitAllFields() {
		commitNameInput();
		commitLimitInput();
		commitMaxSendInput();
	}

	private void commitNameInput() {
		if(nameField == null) return;
		String val = nameField.getText().trim();
		String cur = probe.probeName == null ? "" : probe.probeName;
		if(!val.equals(cur)) {
			NBTTagCompound data = new NBTTagCompound();
			data.setString("setName", val);
			sendControl(data);
		}
		lastSyncedName = val;
	}

	private void commitLimitInput() {
		if(limitField == null) return;
		String raw = limitField.getText().trim();
		int val = 0;
		if(!raw.isEmpty()) {
			try {
				val = Math.max(0, Math.min(65536, Integer.parseInt(raw)));
			} catch(NumberFormatException ignored) {
				val = currentLimitValue();
			}
		}
		limitField.setText(String.valueOf(val));
		if(val != currentLimitValue()) {
			NBTTagCompound data = new NBTTagCompound();
			data.setInteger("setLimitValue", val);
			sendControl(data);
		}
		lastSyncedLimit = val;
	}

	private void commitMaxSendInput() {
		if(maxSendField == null || probe.isReceiver) return;
		String raw = maxSendField.getText().trim();
		int val = TileEntityAtomicProbe.DEFAULT_MAX_SEND;
		if(!raw.isEmpty()) {
			try {
				val = Math.max(1, Math.min(TileEntityAtomicProbe.HARD_MAX_SEND, Integer.parseInt(raw)));
			} catch(NumberFormatException ignored) {
				val = probe.maxSendPerPulse;
			}
		}
		maxSendField.setText(String.valueOf(val));
		if(val != probe.maxSendPerPulse) {
			NBTTagCompound data = new NBTTagCompound();
			data.setInteger("setMaxSend", val);
			sendControl(data);
		}
		lastSyncedMaxSend = val;
	}

	private void sendControl(NBTTagCompound data) {
		PacketDispatcher.wrapper.sendToServer(
				new NBTControlPacket(data, probe.xCoord, probe.yCoord, probe.zCoord));
		mc.getSoundHandler().playSound(PositionedSoundRecord.func_147674_a(CLICK_SFX, 1.0F));
	}

	private boolean inRect(int mx, int my, int x, int y, int w, int h) {
		int rx = mx - guiLeft;
		int ry = my - guiTop;
		return rx >= x && rx < x + w && ry >= y && ry < y + h;
	}

	@Override
	protected void keyTyped(char typedChar, int keyCode) {
		if(nameField != null && nameField.isFocused()) {
			if(keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER || keyCode == Keyboard.KEY_ESCAPE) {
				commitNameInput();
				nameField.setFocused(false);
				return;
			}
			nameField.textboxKeyTyped(typedChar, keyCode);
			return;
		}
		if(handleNumericFieldKey(limitField, typedChar, keyCode, true)) return;
		if(handleNumericFieldKey(maxSendField, typedChar, keyCode, false)) return;
		super.keyTyped(typedChar, keyCode);
	}

	private boolean handleNumericFieldKey(GuiTextField field, char typedChar, int keyCode, boolean isLimit) {
		if(field == null || !field.isFocused()) return false;
		if(keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER || keyCode == Keyboard.KEY_ESCAPE) {
			if(isLimit) commitLimitInput();
			else commitMaxSendInput();
			field.setFocused(false);
			return true;
		}
		if(Character.isDigit(typedChar)
				|| keyCode == Keyboard.KEY_BACK
				|| keyCode == Keyboard.KEY_DELETE
				|| keyCode == Keyboard.KEY_LEFT
				|| keyCode == Keyboard.KEY_RIGHT
				|| keyCode == Keyboard.KEY_HOME
				|| keyCode == Keyboard.KEY_END) {
			field.textboxKeyTyped(typedChar, keyCode);
		}
		return true;
	}

	@Override
	protected void mouseClicked(int mx, int my, int btn) {
		boolean nameWas = nameField != null && nameField.isFocused();
		boolean limWas = limitField != null && limitField.isFocused();
		boolean maxWas = maxSendField != null && maxSendField.isFocused();

		if(nameField != null) nameField.mouseClicked(mx, my, btn);
		if(!linkTab && limitField != null) limitField.mouseClicked(mx, my, btn);
		if(!linkTab && !probe.isReceiver && maxSendField != null) maxSendField.mouseClicked(mx, my, btn);

		if(nameWas && !nameField.isFocused()) commitNameInput();
		if(limWas && !limitField.isFocused()) commitLimitInput();
		if(maxWas && !maxSendField.isFocused()) commitMaxSendInput();

		if((nameField != null && nameField.isFocused())
				|| (!linkTab && limitField != null && limitField.isFocused())
				|| (!linkTab && !probe.isReceiver && maxSendField != null && maxSendField.isFocused())) {
			return;
		}

		// Row 1: 模式切换 (63,17,34,12) + 连接管理页按钮 (99,17,42,12) + 清空 (143,17,26,12)
		if(inRect(mx, my, 63, 17, 34, 12)) {
			commitAllFields();
			NBTTagCompound data = new NBTTagCompound();
			data.setBoolean("toggleMode", true);
			sendControl(data);
			refreshNearbyPeers();
			return;
		}
		if(inRect(mx, my, 99, 17, 42, 12)) {
			commitAllFields();
			linkTab = !linkTab;
			if(linkTab) refreshNearbyPeers();
			mc.getSoundHandler().playSound(PositionedSoundRecord.func_147674_a(CLICK_SFX, 1.0F));
			return;
		}
		if(!probe.isReceiver && !probe.targets.isEmpty() && inRect(mx, my, 143, 17, 26, 12)) {
			NBTTagCompound data = new NBTTagCompound();
			data.setBoolean("unbind", true);
			sendControl(data);
			return;
		}

		// ===== 连接管理子页面点击 =====
		if(linkTab) {
			int start = linkPage * LINKS_PER_PAGE;
			for(int i = 0; i < LINKS_PER_PAGE && start + i < nearbyPeers.size(); i++) {
				int ry = 50 + i * 13;
				if(inRect(mx, my, LINK_BUTTON_X, ry, 28, 12)) {
					TileEntityAtomicProbe peer = nearbyPeers.get(start + i).peer;
					NBTTagCompound data = new NBTTagCompound();
					data.setInteger("toggleLinkX", peer.xCoord);
					data.setInteger("toggleLinkY", peer.yCoord);
					data.setInteger("toggleLinkZ", peer.zCoord);
					sendControl(data);
					return;
				}
			}
			// 翻页按钮 (y=103? 放在底部 y=102 紧凑条? 列表 50..102 占 52px，放 3 行 + 翻页更宽裕 -> 调整行高 12px 4 行 = 48px, 翻页在 y=98)
			int maxPage = Math.max(0, (nearbyPeers.size() - 1) / LINKS_PER_PAGE);
			if(inRect(mx, my, 7, 102, 24, 9) && linkPage > 0) {
				linkPage--;
				mc.getSoundHandler().playSound(PositionedSoundRecord.func_147674_a(CLICK_SFX, 1.0F));
				return;
			}
			if(inRect(mx, my, 145, 102, 24, 9) && linkPage < maxPage) {
				linkPage++;
				mc.getSoundHandler().playSound(PositionedSoundRecord.func_147674_a(CLICK_SFX, 1.0F));
				return;
			}
			super.mouseClicked(mx, my, btn);
			return;
		}

		// ===== 主设置页点击 =====
		boolean shift = Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT);
		for(int i = 0; i < TileEntityAtomicProbe.FILTER_SLOTS; i++) {
			int fx = FILTER_X + i * 18;
			if(inRect(mx, my, fx, FILTER_Y, 16, 16)) {
				NBTTagCompound data = new NBTTagCompound();
				data.setInteger("setFilterSlot", i);
				ItemStack held = mc.thePlayer.inventory.getItemStack();
				ItemStack cur = probe.filters[i];
				boolean quantActive = (!probe.isReceiver && probe.batchMode);

				if(btn == 1) {
					if(quantActive && !shift && cur != null && cur.stackSize > 1) {
						data.setInteger("filterCount", cur.stackSize - 1);
					}
				} else if(btn == 0) {
					if(held != null) {
						if(quantActive && cur != null && cur.isItemEqual(held) && ItemStack.areItemStackTagsEqual(cur, held)) {
							int step = shift ? 16 : 1;
							data.setInteger("filterCount", Math.min(TileEntityAtomicProbe.HARD_MAX_SEND, cur.stackSize + step));
						} else {
							ItemStack ghost = held.copy();
							if(!quantActive) ghost.stackSize = 1;
							NBTTagCompound itemTag = new NBTTagCompound();
							ghost.writeToNBT(itemTag);
							data.setTag("filterItem", itemTag);
						}
					} else if(quantActive && cur != null) {
						int step = shift ? 16 : 1;
						data.setInteger("filterCount", Math.min(TileEntityAtomicProbe.HARD_MAX_SEND, cur.stackSize + step));
					} else if(cur == null) {
						return;
					}
				} else {
					return;
				}
				sendControl(data);
				return;
			}
		}

		if(inRect(mx, my, 7, 77, 34, 12)) {
			NBTTagCompound data = new NBTTagCompound();
			data.setBoolean("toggleWhitelist", true);
			sendControl(data);
			return;
		}
		if(!probe.isReceiver && inRect(mx, my, 43, 77, 42, 12)) {
			NBTTagCompound data = new NBTTagCompound();
			data.setBoolean("toggleBatchMode", true);
			sendControl(data);
			return;
		}
		if(!probe.isReceiver && inRect(mx, my, 87, 77, 44, 12)) {
			NBTTagCompound data = new NBTTagCompound();
			data.setBoolean("toggleRoundRobin", true);
			sendControl(data);
			return;
		}
		if(!probe.isReceiver && inRect(mx, my, 133, 77, 36, 12)) {
			NBTTagCompound data = new NBTTagCompound();
			data.setBoolean("cyclePoll", true);
			sendControl(data);
			return;
		}

		super.mouseClicked(mx, my, btn);
	}

	@Override
	protected void drawGuiContainerBackgroundLayer(float partialTicks, int mx, int my) {
		GL11.glColor4f(1F, 1F, 1F, 1F);
		int x = guiLeft;
		int y = guiTop;

		Minecraft.getMinecraft().getTextureManager().bindTexture(TEX);
		this.drawTexturedModalRect(x, y, 0, 0, xSize, ySize);

		// Row 1: 模式 + 连接管理页 + 清空
		boolean modeHover = inRect(mx, my, 63, 17, 34, 12);
		if(probe.isReceiver) {
			XenGuiHelper.drawHbmButton(x + 63, y + 17, 34, 12,
					0xFF141414, 0xFF7AD8E6, 0xFF1B4B55, 0xFF3A7B88, modeHover ? 0xFF2E7E8E : 0xFF236674);
		} else {
			XenGuiHelper.drawHbmButton(x + 63, y + 17, 34, 12,
					0xFF141414, 0xFFE6B46E, 0xFF5A3710, 0xFF8C5B20, modeHover ? 0xFF9C641C : 0xFF825014);
		}
		XenGuiHelper.drawToggleButton(x + 99, y + 17, 42, 12, linkTab, inRect(mx, my, 99, 17, 42, 12), false);
		if(!probe.isReceiver && !probe.targets.isEmpty()) {
			XenGuiHelper.drawSmallButton(x + 143, y + 17, 26, 12, inRect(mx, my, 143, 17, 26, 12), false);
		}

		// Row 2: 名称输入框
		if(nameField != null) nameField.drawTextBox();

		if(linkTab) {
			// 连接管理页用底色遮盖主页面的 9 格过滤槽区域，再绘制列表暗色底框
			drawRect(x + 6, y + 46, x + 170, y + 111, 0xFF666666);
			drawRect(x + 7, y + 48, x + 169, y + 101, 0xFF1A2026);
			int start = linkPage * LINKS_PER_PAGE;
			for(int i = 0; i < LINKS_PER_PAGE && start + i < nearbyPeers.size(); i++) {
				TileEntityAtomicProbe peer = nearbyPeers.get(start + i).peer;
				boolean linked = isLinkedTo(peer);
				int ry = 50 + i * 13;
				XenGuiHelper.drawToggleButton(x + LINK_BUTTON_X, y + ry, 28, 12, linked,
						inRect(mx, my, LINK_BUTTON_X, ry, 28, 12), false);
			}
			// 翻页小按钮
			XenGuiHelper.drawSmallButton(x + 7, y + 102, 24, 9, inRect(mx, my, 7, 102, 24, 9), false);
			XenGuiHelper.drawSmallButton(x + 145, y + 102, 24, 9, inRect(mx, my, 145, 102, 24, 9), false);
		} else {
			// 9 格过滤槽已直接绘制在 gui_atomic_probe.png 上（x=7..169，与下方 9 格背包严格齐平）
			// 控制条
			XenGuiHelper.drawToggleButton(x + 7, y + 77, 34, 12, probe.whitelist, inRect(mx, my, 7, 77, 34, 12), false);
			if(!probe.isReceiver) {
				XenGuiHelper.drawToggleButton(x + 43, y + 77, 42, 12, probe.batchMode, inRect(mx, my, 43, 77, 42, 12), false);
				XenGuiHelper.drawSmallButton(x + 87, y + 77, 44, 12, inRect(mx, my, 87, 77, 44, 12), false);
				XenGuiHelper.drawSmallButton(x + 133, y + 77, 36, 12, inRect(mx, my, 133, 77, 36, 12), false);
			}
			// 数值输入框
			if(limitField != null) limitField.drawTextBox();
			if(!probe.isReceiver && maxSendField != null) maxSendField.drawTextBox();
		}
		// 恢复 GL 贴图与颜色状态，避免后续绘制背包/手持物品时贴图消失
		GL11.glEnable(GL11.GL_TEXTURE_2D);
		GL11.glColor4f(1F, 1F, 1F, 1F);
	}

	private boolean isLinkedTo(TileEntityAtomicProbe peer) {
		if(!probe.isReceiver && peer.isReceiver) {
			return probe.isBoundTo(peer.xCoord, peer.yCoord, peer.zCoord);
		}
		if(probe.isReceiver && !peer.isReceiver) {
			return peer.isBoundTo(probe.xCoord, probe.yCoord, probe.zCoord);
		}
		return false;
	}

	@Override
	protected void drawGuiContainerForegroundLayer(int mx, int my) {
		XenGuiHelper.drawCenteredFit(fontRendererObj, XenGuiHelper.tr("mnga.probe.title"), 0, 4, xSize, 11, 0xFFE6F4F8);
		XenGuiHelper.drawCenteredFit(fontRendererObj, getStateText(), 7, 16, 54, 14, getStateColor());

		XenGuiHelper.drawCenteredFit(fontRendererObj,
				XenGuiHelper.tr(probe.isReceiver ? "mnga.probe.mode.recv" : "mnga.probe.mode.send"),
				63, 17, 34, 12, 0xFFFFFFFF);
		String tabTxt = linkTab
				? XenGuiHelper.tr("mnga.probe.btn.back")
				: XenGuiHelper.tr("mnga.probe.btn.links", countLinks());
		XenGuiHelper.drawCenteredFit(fontRendererObj, tabTxt, 99, 17, 42, 12, 0xFFFFFFFF);
		if(!probe.isReceiver && !probe.targets.isEmpty()) {
			XenGuiHelper.drawCenteredFit(fontRendererObj, XenGuiHelper.tr("mnga.probe.btn.clear"), 143, 17, 26, 12, 0xFFFFCCCC);
		}

		fontRendererObj.drawString(XenGuiHelper.tr("mnga.probe.label.name"), 7, 35, 0x222222);

		if(linkTab) {
			if(nearbyPeers.isEmpty()) {
				String emptyMsg = XenGuiHelper.tr(probe.isReceiver ? "mnga.probe.links.none_send" : "mnga.probe.links.none_recv");
				XenGuiHelper.drawCenteredFit(fontRendererObj, emptyMsg, 7, 66, 162, 12, 0xFF9AA4AE);
			} else {
				int start = linkPage * LINKS_PER_PAGE;
				for(int i = 0; i < LINKS_PER_PAGE && start + i < nearbyPeers.size(); i++) {
					ReachablePeer entry = nearbyPeers.get(start + i);
					TileEntityAtomicProbe peer = entry.peer;
					boolean linked = isLinkedTo(peer);
					int ry = 50 + i * 13;
					int color = !entry.reachable ? 0xFFFF8866 : (linked ? 0xFF66EE88 : 0xFFDCE4EC);
					String label = peer.getDisplayLabel();
					int labelWidth = LINK_METRICS_X - LINK_LABEL_X - 3;
					if(fontRendererObj.getStringWidth(label) > labelWidth) {
						String ellipsis = "...";
						label = fontRendererObj.trimStringToWidth(label, labelWidth - fontRendererObj.getStringWidth(ellipsis)) + ellipsis;
					}
					fontRendererObj.drawString(label, LINK_LABEL_X, ry + 2, color);
					String metrics = Math.round(entry.distance) + "m " + XenGuiHelper.tr("mnga.probe.links.hops",
							entry.reachable ? Integer.toString(entry.relayCount) : "—");
					XenGuiHelper.drawCenteredFit(fontRendererObj, metrics, LINK_METRICS_X, ry,
							LINK_BUTTON_X - LINK_METRICS_X - 2, 12, color);
					XenGuiHelper.drawCenteredFit(fontRendererObj,
							XenGuiHelper.tr(linked ? "mnga.probe.btn.linked" : "mnga.probe.btn.link"),
							LINK_BUTTON_X, ry, 28, 12, 0xFFFFFFFF);
				}
			}
			int maxPage = Math.max(0, (nearbyPeers.size() - 1) / LINKS_PER_PAGE);
			XenGuiHelper.drawCenteredFit(fontRendererObj, "<", 7, 102, 24, 9, 0xFFE8EEF2);
			XenGuiHelper.drawCenteredFit(fontRendererObj, (linkPage + 1) + "/" + (maxPage + 1), 35, 102, 106, 9, 0xFF222222);
			XenGuiHelper.drawCenteredFit(fontRendererObj, ">", 145, 102, 24, 9, 0xFFE8EEF2);
		} else {
			String filterTitle = XenGuiHelper.tr((!probe.isReceiver && probe.batchMode)
					? "mnga.probe.label.batch_samples" : "mnga.probe.label.filter_samples");
			fontRendererObj.drawString(filterTitle, 7, 47, 0x222222);

			RenderHelper.enableGUIStandardItemLighting();
			GL11.glEnable(GL12.GL_RESCALE_NORMAL);
			boolean quantActive = (!probe.isReceiver && probe.batchMode);
			for(int i = 0; i < TileEntityAtomicProbe.FILTER_SLOTS; i++) {
				ItemStack sample = probe.filters[i];
				if(sample != null) {
					int fx = FILTER_X + i * 18;
					itemRender.renderItemAndEffectIntoGUI(fontRendererObj, mc.getTextureManager(), sample, fx, FILTER_Y);
					if(quantActive && sample.stackSize > 1) {
						itemRender.renderItemOverlayIntoGUI(fontRendererObj, mc.getTextureManager(),
								sample, fx, FILTER_Y, String.valueOf(sample.stackSize));
					}
				}
			}
			RenderHelper.disableStandardItemLighting();
			GL11.glDisable(GL11.GL_LIGHTING);

			XenGuiHelper.drawCenteredFit(fontRendererObj,
					XenGuiHelper.tr(probe.whitelist ? "mnga.probe.btn.whitelist" : "mnga.probe.btn.blacklist"),
					7, 77, 34, 12, 0xFFFFFFFF);
			if(!probe.isReceiver) {
				XenGuiHelper.drawCenteredFit(fontRendererObj,
						XenGuiHelper.tr(probe.batchMode ? "mnga.probe.btn.batch_on" : "mnga.probe.btn.batch_off"),
						43, 77, 42, 12, 0xFFFFFFFF);
				XenGuiHelper.drawCenteredFit(fontRendererObj,
						XenGuiHelper.tr(probe.roundRobin ? "mnga.probe.btn.rr" : "mnga.probe.btn.priority"),
						87, 77, 44, 12, 0xFFE8EEF2);
				XenGuiHelper.drawCenteredFit(fontRendererObj, probe.pollInterval + "t", 133, 77, 36, 12, 0xFFE8EEF2);
				fontRendererObj.drawString(XenGuiHelper.tr("mnga.probe.label.threshold"), 7, 95, 0x222222);
				fontRendererObj.drawString(XenGuiHelper.tr("mnga.probe.label.max_send"), 84, 95, 0x222222);
			} else {
				fontRendererObj.drawString(XenGuiHelper.tr("mnga.probe.label.recv_limit"), 7, 95, 0x222222);
			}
		}
		// 恢复标准 GUI 物品光照与贴图状态，确保鼠标抓取物品正常渲染
		GL11.glEnable(GL11.GL_TEXTURE_2D);
		GL11.glColor4f(1F, 1F, 1F, 1F);
		RenderHelper.enableGUIStandardItemLighting();
	}

	private int countLinks() {
		if(!probe.isReceiver) return probe.targets.size();
		int c = 0;
		for(ReachablePeer entry : nearbyPeers) {
			if(entry.peer.isBoundTo(probe.xCoord, probe.yCoord, probe.zCoord)) c++;
		}
		return c;
	}

	@Override
	public void drawScreen(int mx, int my, float partialTicks) {
		super.drawScreen(mx, my, partialTicks);
		List<String> tip = new ArrayList<String>();
		if(inRect(mx, my, 99, 17, 42, 12)) {
			tip.add(XenGuiHelper.tr("mnga.probe.tip.links.1"));
			tip.add(XenGuiHelper.tr("mnga.probe.tip.links.2"));
		} else if(!linkTab && !probe.isReceiver && inRect(mx, my, 34, 93, 44, 12)) {
			tip.add(XenGuiHelper.tr("mnga.probe.tip.threshold.1"));
			tip.add(XenGuiHelper.tr("mnga.probe.tip.threshold.2"));
		} else if(!linkTab && !probe.isReceiver && inRect(mx, my, 111, 93, 58, 12)) {
			tip.add(XenGuiHelper.tr("mnga.probe.tip.max_send.1"));
			tip.add(XenGuiHelper.tr("mnga.probe.tip.max_send.2"));
		} else if(!linkTab && probe.isReceiver && inRect(mx, my, 111, 93, 58, 12)) {
			tip.add(XenGuiHelper.tr("mnga.probe.tip.recv_limit.1"));
			tip.add(XenGuiHelper.tr("mnga.probe.tip.recv_limit.2"));
		} else if(!linkTab && !probe.isReceiver && inRect(mx, my, 133, 77, 36, 12)) {
			tip.add(XenGuiHelper.tr("mnga.probe.tip.poll"));
		} else if(linkTab) {
			int start = linkPage * LINKS_PER_PAGE;
			for(int i = 0; i < LINKS_PER_PAGE && start + i < nearbyPeers.size(); i++) {
				if(inRect(mx, my, 7, 50 + i * 13, 162, 12)) {
					ReachablePeer entry = nearbyPeers.get(start + i);
					TileEntityAtomicProbe peer = entry.peer;
					tip.add(peer.getDisplayLabel());
					if(peer.probeName != null && !peer.probeName.trim().isEmpty()) {
						tip.add("(" + peer.xCoord + ", " + peer.yCoord + ", " + peer.zCoord + ")");
					}
					if(!entry.reachable) tip.add(XenGuiHelper.tr("mnga.probe.links.no_route"));
					break;
				}
			}
		} else {
			for(int i = 0; i < TileEntityAtomicProbe.FILTER_SLOTS; i++) {
				if(inRect(mx, my, FILTER_X + i * 18, FILTER_Y, 16, 16) && probe.filters[i] != null) {
					tip.add(probe.filters[i].getDisplayName() + (probe.batchMode && !probe.isReceiver ? " §ex" + probe.filters[i].stackSize : ""));
					tip.add(XenGuiHelper.tr(probe.batchMode && !probe.isReceiver
							? "mnga.probe.tip.filter_batch" : "mnga.probe.tip.filter_normal"));
					break;
				}
			}
		}
		if(!tip.isEmpty()) {
			drawHoveringText(tip, mx, my, fontRendererObj);
		}
	}

	private String getStateText() {
		if(probe.isReceiver) {
			return XenGuiHelper.tr(probe.hasActiveSender ? "mnga.probe.state.recv_locked" : "mnga.probe.state.recv_idle");
		}
		switch(probe.state) {
			case TileEntityAtomicProbe.STATE_IDLE: return XenGuiHelper.tr("mnga.probe.state.idle");
			case TileEntityAtomicProbe.STATE_ALIGNING: return XenGuiHelper.tr("mnga.probe.state.aligning");
			case TileEntityAtomicProbe.STATE_FIRING: return XenGuiHelper.tr("mnga.probe.state.firing");
			case TileEntityAtomicProbe.STATE_OUT_OF_RANGE: return XenGuiHelper.tr("mnga.probe.state.out_of_range");
			case TileEntityAtomicProbe.STATE_BLIND_SPOT: return XenGuiHelper.tr("mnga.probe.state.blind_spot");
			case TileEntityAtomicProbe.STATE_TARGET_FULL: return XenGuiHelper.tr("mnga.probe.state.target_full");
			case TileEntityAtomicProbe.STATE_UNBOUND:
			default: return XenGuiHelper.tr("mnga.probe.state.unbound");
		}
	}

	private int getStateColor() {
		if(probe.isReceiver) {
			return probe.hasActiveSender ? 0xFF66F0FF : 0xFF66EE88;
		}
		switch(probe.state) {
			case TileEntityAtomicProbe.STATE_IDLE: return 0xFF66EE88;
			case TileEntityAtomicProbe.STATE_ALIGNING: return 0xFFFFE066;
			case TileEntityAtomicProbe.STATE_FIRING: return 0xFF66F0FF;
			case TileEntityAtomicProbe.STATE_TARGET_FULL: return 0xFFFFBB44;
			default: return 0xFFFF6666;
		}
	}
}
