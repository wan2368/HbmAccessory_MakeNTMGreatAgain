package com.makentmgreatagain.gui;

import java.util.Arrays;

import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

import com.hbm.inventory.gui.GuiInfoContainer;
import com.hbm.packet.PacketDispatcher;
import com.hbm.packet.toserver.NBTControlPacket;
import com.makentmgreatagain.MakeNTMGreatAgain;
import com.makentmgreatagain.container.ContainerXenItemTransceiver;
import com.makentmgreatagain.tileentity.TileEntityXenItemTransceiver;

import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;

/**
 * 物品传送器 GUI（中英文适配、15 字自动换行提示、频率联动示波器、HBM 立体斜角按钮、强力占位与定量模式）。
 */
public class GUIXenItemTransceiver extends GuiInfoContainer {

	private static final ResourceLocation TEX_STEADY = new ResourceLocation(
			MakeNTMGreatAgain.MODID, "textures/gui/gui_xen_item_transceiver.png");
	private static final ResourceLocation TEX_OVERCLOCK = new ResourceLocation(
			MakeNTMGreatAgain.MODID, "textures/gui/gui_xen_item_transceiver_overclock.png");

	private static final int W = ContainerXenItemTransceiver.PANEL_W;
	private static final int H = ContainerXenItemTransceiver.PANEL_H;

	private static final int FILTER_X = 81;
	private static final int FILTER_Y = 74;

	private static final int[] FREQ_DELTAS = { -100, -10, -1, 1, 10, 100 };
	private static final String[] FREQ_LABELS = { "-10", "-1", "-.1", "+.1", "+1", "+10" };

	private final TileEntityXenItemTransceiver te;

	public GUIXenItemTransceiver(InventoryPlayer invPlayer, TileEntityXenItemTransceiver te) {
		super(new ContainerXenItemTransceiver(invPlayer, te));
		this.te = te;
		this.xSize = W;
		this.ySize = H;
	}

	private void sendCtrl(NBTTagCompound data) {
		PacketDispatcher.wrapper.sendToServer(new NBTControlPacket(data, te.xCoord, te.yCoord, te.zCoord));
		mc.getSoundHandler().playSound(PositionedSoundRecord.func_147674_a(new ResourceLocation("gui.button.press"), 1.0F));
	}

	@Override
	protected void mouseClicked(int mouseX, int mouseY, int button) {
		int rx = mouseX - guiLeft;
		int ry = mouseY - guiTop;
		boolean shift = Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT);

		// 虚拟过滤槽点击（定量模式下支持调节目标堆叠个数）
		for(int i = 0; i < TileEntityXenItemTransceiver.FILTER_SLOTS; i++) {
			int fx = FILTER_X + (i % 3) * 18;
			int fy = FILTER_Y + (i / 3) * 18;
			if(rx >= fx && rx < fx + 18 && ry >= fy && ry < fy + 18) {
				NBTTagCompound tag = new NBTTagCompound();
				tag.setInteger("setFilterSlot", i);
				ItemStack held = mc.thePlayer.inventory.getItemStack();
				ItemStack cur = te.filterSlots[i];

				if(button == 1) {
					if(te.batchMode && !shift && cur != null && cur.stackSize > 1) {
						tag.setInteger("filterCount", cur.stackSize - 1);
					}
				} else if(button == 0) {
					if(held != null) {
						if(te.batchMode && cur != null && cur.isItemEqual(held) && ItemStack.areItemStackTagsEqual(cur, held)) {
							int step = shift ? 16 : 1;
							tag.setInteger("filterCount", Math.min(cur.getMaxStackSize(), cur.stackSize + step));
						} else {
							ItemStack ghost = held.copy();
							if(!te.batchMode) ghost.stackSize = 1;
							NBTTagCompound itemTag = new NBTTagCompound();
							ghost.writeToNBT(itemTag);
							tag.setTag("filterItem", itemTag);
						}
					} else if(te.batchMode && cur != null) {
						int step = shift ? 16 : 1;
						tag.setInteger("filterCount", Math.min(cur.getMaxStackSize(), cur.stackSize + step));
					} else {
						return;
					}
				} else {
					return;
				}
				sendCtrl(tag);
				return;
			}
		}

		super.mouseClicked(mouseX, mouseY, button);

		// 频率微调
		if(button == 0 && ry >= 18 && ry < 30) {
			for(int i = 0; i < 6; i++) {
				int bx = 104 + i * 17;
				if(rx >= bx && rx < bx + 16) {
					NBTTagCompound tag = new NBTTagCompound();
					tag.setInteger("freqDelta", FREQ_DELTAS[i]);
					sendCtrl(tag);
					return;
				}
			}
		}

		// 复制 / 粘贴 / 收发模式
		if(button == 0 && ry >= 31 && ry < 43) {
			if(rx >= 104 && rx < 129) {
				GUIXenFluidTransceiver.copiedFrequency = te.frequency;
				mc.getSoundHandler().playSound(PositionedSoundRecord.func_147674_a(new ResourceLocation("gui.button.press"), 1.2F));
				return;
			}
			if(rx >= 131 && rx < 156) {
				NBTTagCompound tag = new NBTTagCompound();
				tag.setInteger("freq", GUIXenFluidTransceiver.copiedFrequency);
				sendCtrl(tag);
				return;
			}
			if(rx >= 158 && rx < 206) {
				NBTTagCompound tag = new NBTTagCompound();
				tag.setBoolean("cycleIo", true);
				sendCtrl(tag);
				return;
			}
		}

		// 超频主开关 + 分发模式
		if(button == 0 && ry >= 44 && ry < 57) {
			if(rx >= 104 && rx < 166) {
				NBTTagCompound tag = new NBTTagCompound();
				tag.setBoolean("toggleOverclock", true);
				sendCtrl(tag);
				return;
			}
			if(rx >= 168 && rx < 206) {
				NBTTagCompound tag = new NBTTagCompound();
				tag.setBoolean("toggleDist", true);
				sendCtrl(tag);
				return;
			}
		}

		// 左右 3×3 下方：强制占位开关
		if((button == 0 || button == 1) && ry >= 130 && ry < 144) {
			if(rx >= 13 && rx < 67) {
				NBTTagCompound tag = new NBTTagCompound();
				tag.setBoolean(button == 1 ? "clearSendLock" : "toggleSendLock", true);
				sendCtrl(tag);
				return;
			}
			if(rx >= 147 && rx < 201) {
				NBTTagCompound tag = new NBTTagCompound();
				tag.setBoolean(button == 1 ? "clearRecvLock" : "toggleRecvLock", true);
				sendCtrl(tag);
				return;
			}
		}

		// 中部过滤控制按钮
		if(button == 0 && ry >= 111 && ry < 123) {
			if(rx >= 70 && rx < 100) {
				NBTTagCompound tag = new NBTTagCompound();
				tag.setBoolean("cycleFilter", true);
				sendCtrl(tag);
				return;
			}
			if(rx >= 101 && rx < 115) {
				NBTTagCompound tag = new NBTTagCompound();
				tag.setBoolean("toggleMeta", true);
				sendCtrl(tag);
				return;
			}
			if(rx >= 116 && rx < 130) {
				NBTTagCompound tag = new NBTTagCompound();
				tag.setBoolean("toggleOre", true);
				sendCtrl(tag);
				return;
			}
			if(rx >= 131 && rx < 145) {
				NBTTagCompound tag = new NBTTagCompound();
				tag.setBoolean("toggleNbt", true);
				sendCtrl(tag);
				return;
			}
		}

		// 定量模式开关
		if(button == 0 && rx >= 70 && rx < 145 && ry >= 124 && ry < 136) {
			NBTTagCompound tag = new NBTTagCompound();
			tag.setBoolean("toggleBatch", true);
			sendCtrl(tag);
			return;
		}

		// 定额补货按钮
		if((button == 0 || button == 1) && ry >= 137 && ry < 150) {
			int step = button == 1 ? 1 : 16;
			if(rx >= 70 && rx < 86) {
				NBTTagCompound tag = new NBTTagCompound();
				tag.setInteger("stockDelta", -step);
				sendCtrl(tag);
				return;
			}
			if(rx >= 129 && rx < 145) {
				NBTTagCompound tag = new NBTTagCompound();
				tag.setInteger("stockDelta", step);
				sendCtrl(tag);
				return;
			}
		}
	}

	private void showWrappedTip(int mouseX, int mouseY, String... lines) {
		this.func_146283_a(XenGuiHelper.wrapTooltipLines(Arrays.asList(lines)), mouseX, mouseY);
	}

	@Override
	public void drawScreen(int mouseX, int mouseY, float interp) {
		super.drawScreen(mouseX, mouseY, interp);

		int rx = mouseX - guiLeft;
		int ry = mouseY - guiTop;

		// 频率微调提示
		if(ry >= 18 && ry < 30 && rx >= 104 && rx < 206) {
			showWrappedTip(mouseX, mouseY,
					XenGuiHelper.tr("mnga.xen.tip.freq.1"),
					XenGuiHelper.tr("mnga.xen.tip.freq.item"));
			return;
		}

		// 复制/粘贴/收发模式提示
		if(ry >= 31 && ry < 43) {
			if(rx >= 104 && rx < 129) {
				showWrappedTip(mouseX, mouseY, XenGuiHelper.tr("mnga.xen.tip.copy"));
				return;
			}
			if(rx >= 131 && rx < 156) {
				showWrappedTip(mouseX, mouseY, XenGuiHelper.tr("mnga.xen.tip.paste",
						String.format("%05.1f", GUIXenFluidTransceiver.copiedFrequency / 10.0F)));
				return;
			}
			if(rx >= 158 && rx < 206) {
				showWrappedTip(mouseX, mouseY, XenGuiHelper.tr("mnga.xen.tip.io"));
				return;
			}
		}

		// 超频与分发模式提示
		if(ry >= 44 && ry < 57) {
			if(rx >= 104 && rx < 166) {
				if(te.overclocked) {
					showWrappedTip(mouseX, mouseY,
							XenGuiHelper.tr("mnga.xen.tip.item.oc_on.1"),
							XenGuiHelper.tr("mnga.xen.tip.item.oc_on.2", String.format("%.2f", te.lastRadPerTick), String.format("%.1f", te.lastRadPerTick * 20.0F)),
							XenGuiHelper.tr("mnga.xen.tip.item.oc_on.3"),
							XenGuiHelper.tr("mnga.xen.tip.item.oc_on.4"));
				} else {
					showWrappedTip(mouseX, mouseY,
							XenGuiHelper.tr("mnga.xen.tip.item.oc_off.1"),
							XenGuiHelper.tr("mnga.xen.tip.item.oc_off.2"),
							XenGuiHelper.tr("mnga.xen.tip.item.oc_off.3"));
				}
				return;
			}
			if(rx >= 168 && rx < 206) {
				if(te.distMode == TileEntityXenItemTransceiver.DIST_ROUND_ROBIN) {
					showWrappedTip(mouseX, mouseY,
							XenGuiHelper.tr("mnga.xen.tip.dist.rr.1"),
							XenGuiHelper.tr("mnga.xen.tip.dist.rr.item"));
				} else {
					showWrappedTip(mouseX, mouseY,
							XenGuiHelper.tr("mnga.xen.tip.dist.prio.1"),
							XenGuiHelper.tr("mnga.xen.tip.dist.prio.item"));
				}
				return;
			}
		}

		// 强力占位按钮提示
		if(ry >= 130 && ry < 144) {
			if(rx >= 13 && rx < 67) {
				String state = XenGuiHelper.tr(te.sendSlotLock ? "mnga.xen.tip.item.state_on" : "mnga.xen.tip.item.state_off");
				showWrappedTip(mouseX, mouseY,
						XenGuiHelper.tr("mnga.xen.tip.item.lock_send.1", state),
						XenGuiHelper.tr("mnga.xen.tip.item.lock_send.2"),
						XenGuiHelper.tr("mnga.xen.tip.item.lock_send.3"));
				return;
			}
			if(rx >= 147 && rx < 201) {
				String state = XenGuiHelper.tr(te.recvSlotLock ? "mnga.xen.tip.item.state_on" : "mnga.xen.tip.item.state_off");
				showWrappedTip(mouseX, mouseY,
						XenGuiHelper.tr("mnga.xen.tip.item.lock_recv.1", state),
						XenGuiHelper.tr("mnga.xen.tip.item.lock_recv.2"),
						XenGuiHelper.tr("mnga.xen.tip.item.lock_recv.3"));
				return;
			}
		}

		// 过滤与标签按钮提示
		if(ry >= 111 && ry < 123) {
			if(rx >= 70 && rx < 100) {
				showWrappedTip(mouseX, mouseY, XenGuiHelper.tr("mnga.xen.tip.item.filter"));
				return;
			}
			if(rx >= 101 && rx < 115) {
				String st = XenGuiHelper.tr(te.matchMeta ? "mnga.xen.tip.item.state_on" : "mnga.xen.tip.item.state_off");
				showWrappedTip(mouseX, mouseY, XenGuiHelper.tr("mnga.xen.tip.item.meta", st));
				return;
			}
			if(rx >= 116 && rx < 130) {
				String st = XenGuiHelper.tr(te.matchOreDict ? "mnga.xen.tip.item.state_on" : "mnga.xen.tip.item.state_off");
				showWrappedTip(mouseX, mouseY, XenGuiHelper.tr("mnga.xen.tip.item.ore", st));
				return;
			}
			if(rx >= 131 && rx < 145) {
				String st = XenGuiHelper.tr(te.matchNbt ? "mnga.xen.tip.item.state_on" : "mnga.xen.tip.item.state_off");
				showWrappedTip(mouseX, mouseY, XenGuiHelper.tr("mnga.xen.tip.item.nbt", st));
				return;
			}
		}

		// 定量按钮提示
		if(rx >= 70 && rx < 145 && ry >= 124 && ry < 136) {
			String st = XenGuiHelper.tr(te.batchMode ? "mnga.xen.tip.item.state_on" : "mnga.xen.tip.item.state_off");
			showWrappedTip(mouseX, mouseY,
					XenGuiHelper.tr("mnga.xen.tip.item.batch.1", st),
					XenGuiHelper.tr("mnga.xen.tip.item.batch.2"),
					XenGuiHelper.tr("mnga.xen.tip.item.batch.3"));
			return;
		}

		// 定额提示
		if(rx >= 70 && rx < 145 && ry >= 137 && ry < 150) {
			showWrappedTip(mouseX, mouseY,
					XenGuiHelper.tr("mnga.xen.tip.item.stock.1"),
					XenGuiHelper.tr("mnga.xen.tip.item.stock.2"),
					XenGuiHelper.tr("mnga.xen.tip.item.stock.3"));
		}
	}

	@Override
	protected void drawGuiContainerBackgroundLayer(float interp, int mouseX, int mouseY) {
		GL11.glColor4f(1F, 1F, 1F, 1F);
		boolean oc = te.overclocked;
		int x = guiLeft;
		int y = guiTop;

		Minecraft.getMinecraft().getTextureManager().bindTexture(oc ? TEX_OVERCLOCK : TEX_STEADY);
		this.drawTexturedModalRect(x, y, 0, 0, W, H);

		if(oc) {
			int shift = (int) ((System.currentTimeMillis() / 90L) % 12L);
			XenGuiHelper.drawHazardBar(x + 4, y + 3, W - 8, 3, shift);
			XenGuiHelper.drawHazardBar(x + 4, y + H - 6, W - 8, 3, -shift);
		}

		boolean linked = (te.peerSenders > 0 && te.peerReceivers > 0);
		XenGuiHelper.drawOscilloscope(x + 10, y + 18, 90, 38, oc, linked, te.frequency);

		int rx = mouseX - guiLeft;
		int ry = mouseY - guiTop;
		for(int i = 0; i < 6; i++) {
			int bx = 104 + i * 17;
			XenGuiHelper.drawSmallButton(x + bx, y + 18, 16, 12, rx >= bx && rx < bx + 16 && ry >= 18 && ry < 30, oc);
		}
		XenGuiHelper.drawSmallButton(x + 104, y + 31, 25, 12, rx >= 104 && rx < 129 && ry >= 31 && ry < 43, oc);
		XenGuiHelper.drawSmallButton(x + 131, y + 31, 25, 12, rx >= 131 && rx < 156 && ry >= 31 && ry < 43, oc);
		XenGuiHelper.drawSmallButton(x + 158, y + 31, 48, 12, rx >= 158 && rx < 206 && ry >= 31 && ry < 43, oc);

		boolean ocHover = rx >= 104 && rx < 166 && ry >= 44 && ry < 57;
		if(oc) {
			float pulse = 0.5F + 0.5F * (float) Math.sin(System.currentTimeMillis() / 160.0D);
			int r = (int) (175 + pulse * 55);
			int g = (int) (45 + pulse * 40);
			int btnFill = (0xFF << 24) | (r << 16) | (g << 8) | 0x18;
			XenGuiHelper.drawHbmButton(x + 104, y + 44, 62, 13, 0xFF1E0404, 0xFFFFAA44, 0xFF6B1408, 0xFFB84420, ocHover ? 0xFFFF6E24 : btnFill);
		} else {
			XenGuiHelper.drawHbmButton(x + 104, y + 44, 62, 13, 0xFF141414, 0xFF9EB0B8, 0xFF343E42, 0xFF566870, ocHover ? 0xFF58727C : 0xFF485C64);
		}
		XenGuiHelper.drawSmallButton(x + 168, y + 44, 38, 13, rx >= 168 && rx < 206 && ry >= 44 && ry < 57, oc);

		XenGuiHelper.drawToggleButton(x + 13, y + 130, 54, 14, te.sendSlotLock, rx >= 13 && rx < 67 && ry >= 130 && ry < 144, oc);
		XenGuiHelper.drawToggleButton(x + 147, y + 130, 54, 14, te.recvSlotLock, rx >= 147 && rx < 201 && ry >= 130 && ry < 144, oc);

		if(te.sendSlotLock) {
			for(int i = 0; i < TileEntityXenItemTransceiver.SEND_SLOTS; i++) {
				if(te.slots[i] == null && te.sendPlaceholders[i] != null) {
					int sx = ContainerXenItemTransceiver.SEND_X + (i % 3) * 18;
					int sy = ContainerXenItemTransceiver.GRID_Y + (i / 3) * 18;
					drawGhostSlotIcon(te.sendPlaceholders[i], sx, sy);
				}
			}
		}
		if(te.recvSlotLock) {
			for(int i = 0; i < TileEntityXenItemTransceiver.RECV_SLOTS; i++) {
				if(te.slots[TileEntityXenItemTransceiver.SEND_SLOTS + i] == null && te.recvPlaceholders[i] != null) {
					int sx = ContainerXenItemTransceiver.RECV_X + (i % 3) * 18;
					int sy = ContainerXenItemTransceiver.GRID_Y + (i / 3) * 18;
					drawGhostSlotIcon(te.recvPlaceholders[i], sx, sy);
				}
			}
		}

		for(int i = 0; i < TileEntityXenItemTransceiver.FILTER_SLOTS; i++) {
			if(te.filterSlots[i] != null) {
				int fx = FILTER_X + (i % 3) * 18;
				int fy = FILTER_Y + (i / 3) * 18;
				this.renderItem(te.filterSlots[i], fx, fy);
				if(te.batchMode && te.filterSlots[i].stackSize > 1) {
					itemRender.renderItemOverlayIntoGUI(this.fontRendererObj, this.mc.getTextureManager(),
							te.filterSlots[i], x + fx, y + fy, String.valueOf(te.filterSlots[i].stackSize));
				}
			}
		}
		RenderHelper.disableStandardItemLighting();
		GL11.glDisable(GL11.GL_LIGHTING);
		GL11.glColor4f(1F, 1F, 1F, 1F);

		XenGuiHelper.drawSmallButton(x + 70, y + 111, 30, 12, rx >= 70 && rx < 100 && ry >= 111 && ry < 123, oc);
		XenGuiHelper.drawToggleButton(x + 101, y + 111, 14, 12, te.matchMeta, rx >= 101 && rx < 115 && ry >= 111 && ry < 123, oc);
		XenGuiHelper.drawToggleButton(x + 116, y + 111, 14, 12, te.matchOreDict, rx >= 116 && rx < 130 && ry >= 111 && ry < 123, oc);
		XenGuiHelper.drawToggleButton(x + 131, y + 111, 14, 12, te.matchNbt, rx >= 131 && rx < 145 && ry >= 111 && ry < 123, oc);

		XenGuiHelper.drawToggleButton(x + 70, y + 124, 75, 12, te.batchMode, rx >= 70 && rx < 145 && ry >= 124 && ry < 136, oc);

		XenGuiHelper.drawSmallButton(x + 70, y + 137, 16, 13, rx >= 70 && rx < 86 && ry >= 137 && ry < 150, oc);
		XenGuiHelper.drawHbmButton(x + 87, y + 137, 41, 13,
				oc ? 0xFF1A0404 : 0xFF141414,
				oc ? 0xFF3A100D : 0xFF343434,
				oc ? 0xFF9E382A : 0xFF8C8C8C,
				oc ? 0xFF561814 : 0xFF585858,
				oc ? 0xFF280D0C : 0xFF2B2B2B);
		XenGuiHelper.drawSmallButton(x + 129, y + 137, 16, 13, rx >= 129 && rx < 145 && ry >= 137 && ry < 150, oc);
	}

	private void drawGhostSlotIcon(ItemStack stack, int relX, int relY) {
		this.renderItem(stack, relX, relY);
		GL11.glDisable(GL11.GL_LIGHTING);
		GL11.glDisable(GL11.GL_DEPTH_TEST);
		drawRect(guiLeft + relX, guiTop + relY, guiLeft + relX + 16, guiTop + relY + 16, 0x8838444E);
		GL11.glEnable(GL11.GL_DEPTH_TEST);
		GL11.glColor4f(1F, 1F, 1F, 1F);
	}

	@Override
	protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
		boolean oc = te.overclocked;

		String title = XenGuiHelper.tr(oc ? "mnga.xen.item.title_oc" : "mnga.xen.item.title_steady");
		XenGuiHelper.drawCenteredFit(this.fontRendererObj, title, 0, 5, W, 11, oc ? 0xFFFF9640 : 0xFFE6F4F8);

		String freqText = String.format("%05.1f MHz", te.frequency / 10.0F);
		this.fontRendererObj.drawString(freqText, 13, 21, oc ? 0xFFFFAA44 : 0xFF66F0FF);
		if(oc) {
			this.fontRendererObj.drawString(XenGuiHelper.tr("mnga.xen.status.rad_short", String.format("%.1f", te.lastRadPerTick)),
					13, 33, 0xFFFF7744);
		} else {
			this.fontRendererObj.drawString(XenGuiHelper.tr(te.hasCrossDimPeer ? "mnga.xen.status.cross_lock" : "mnga.xen.status.idle"),
					13, 33, 0xFF77B8C8);
		}
		String peerText = XenGuiHelper.tr("mnga.xen.status.peers", te.peerSenders, te.peerReceivers);
		this.fontRendererObj.drawString(peerText, 13, 45, oc ? 0xFFFFB899 : 0xFFAAD8E6);

		for(int i = 0; i < 6; i++) {
			XenGuiHelper.drawCenteredFit(this.fontRendererObj, FREQ_LABELS[i], 104 + i * 17, 18, 16, 12, 0xFFE8E8E8);
		}
		XenGuiHelper.drawCenteredFit(this.fontRendererObj, XenGuiHelper.tr("mnga.xen.copy"), 104, 31, 25, 12, 0xFFD8D8D8);
		XenGuiHelper.drawCenteredFit(this.fontRendererObj, XenGuiHelper.tr("mnga.xen.paste"), 131, 31, 25, 12, 0xFFD8D8D8);

		String ioKey = te.ioMode == TileEntityXenItemTransceiver.IO_SEND ? "mnga.xen.io.send"
				: (te.ioMode == TileEntityXenItemTransceiver.IO_RECV ? "mnga.xen.io.recv"
				: (te.ioMode == TileEntityXenItemTransceiver.IO_BOTH ? "mnga.xen.io.both" : "mnga.xen.io.off"));
		XenGuiHelper.drawCenteredFit(this.fontRendererObj, XenGuiHelper.tr(ioKey), 158, 31, 48, 12, 0xFFE8E8E8);

		XenGuiHelper.drawCenteredFit(this.fontRendererObj, XenGuiHelper.tr(oc ? "mnga.xen.oc.on_item" : "mnga.xen.oc.off_item"),
				104, 44, 62, 13, oc ? 0xFFFFF0D0 : 0xFFB8E8F4);
		XenGuiHelper.drawCenteredFit(this.fontRendererObj,
				XenGuiHelper.tr(te.distMode == TileEntityXenItemTransceiver.DIST_ROUND_ROBIN ? "mnga.xen.dist.rr_short" : "mnga.xen.dist.prio_short"),
				168, 44, 38, 13, 0xFFE8E8E8);

		XenGuiHelper.drawCenteredFit(this.fontRendererObj, XenGuiHelper.tr("mnga.xen.grid.send"), 13, 60, 54, 12, 0xFFAEE8F5);
		XenGuiHelper.drawCenteredFit(this.fontRendererObj, XenGuiHelper.tr("mnga.xen.filter"), 80, 60, 54, 12, 0xFFE8E8E8);
		XenGuiHelper.drawCenteredFit(this.fontRendererObj, XenGuiHelper.tr("mnga.xen.grid.recv"), 147, 60, 54, 12, 0xFFA8F0B8);

		XenGuiHelper.drawCenteredFit(this.fontRendererObj, XenGuiHelper.tr(te.sendSlotLock ? "mnga.xen.lock.on" : "mnga.xen.lock.off"),
				13, 130, 54, 14, te.sendSlotLock ? 0xFFB8FFD0 : 0xFFE0E0E0);
		XenGuiHelper.drawCenteredFit(this.fontRendererObj, XenGuiHelper.tr(te.recvSlotLock ? "mnga.xen.lock.on" : "mnga.xen.lock.off"),
				147, 130, 54, 14, te.recvSlotLock ? 0xFFB8FFD0 : 0xFFE0E0E0);

		String fKey = te.filterMode == TileEntityXenItemTransceiver.FILTER_WHITELIST ? "mnga.xen.filter.white"
				: (te.filterMode == TileEntityXenItemTransceiver.FILTER_BLACKLIST ? "mnga.xen.filter.black" : "mnga.xen.filter.off");
		XenGuiHelper.drawCenteredFit(this.fontRendererObj, XenGuiHelper.tr(fKey), 70, 111, 30, 12, 0xFFF0F0F0);
		XenGuiHelper.drawCenteredFit(this.fontRendererObj, "M", 101, 111, 14, 12, te.matchMeta ? 0xFFB8FFD0 : 0xFFB0B0B0);
		XenGuiHelper.drawCenteredFit(this.fontRendererObj, XenGuiHelper.tr("mnga.xen.match.ore"), 116, 111, 14, 12, te.matchOreDict ? 0xFFB8FFD0 : 0xFFB0B0B0);
		XenGuiHelper.drawCenteredFit(this.fontRendererObj, "N", 131, 111, 14, 12, te.matchNbt ? 0xFFB8FFD0 : 0xFFB0B0B0);

		XenGuiHelper.drawCenteredFit(this.fontRendererObj, XenGuiHelper.tr(te.batchMode ? "mnga.xen.batch.on" : "mnga.xen.batch.off"),
				70, 124, 75, 12, te.batchMode ? 0xFFB8FFD0 : 0xFFE0E0E0);

		XenGuiHelper.drawCenteredFit(this.fontRendererObj, "-", 70, 137, 16, 13, 0xFFF0F0F0);
		String stockStr = te.stockLimit == 0 ? XenGuiHelper.tr("mnga.xen.stock.off") : XenGuiHelper.tr("mnga.xen.stock.val", te.stockLimit);
		XenGuiHelper.drawCenteredFit(this.fontRendererObj, stockStr, 87, 137, 41, 13, 0xFFFFE090);
		XenGuiHelper.drawCenteredFit(this.fontRendererObj, "+", 129, 137, 16, 13, 0xFFF0F0F0);
	}
}
