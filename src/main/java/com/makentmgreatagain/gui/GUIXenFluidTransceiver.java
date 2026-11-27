package com.makentmgreatagain.gui;

import java.util.Arrays;

import org.lwjgl.opengl.GL11;

import com.hbm.inventory.gui.GuiInfoContainer;
import com.hbm.packet.PacketDispatcher;
import com.hbm.packet.toserver.NBTControlPacket;
import com.makentmgreatagain.MakeNTMGreatAgain;
import com.makentmgreatagain.container.ContainerXenFluidTransceiver;
import com.makentmgreatagain.tileentity.TileEntityXenFluidTransceiver;

import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;

/**
 * 流体传送器 GUI（中英文适配、15 字自动换行提示、频率联动示波器、HBM 立体斜角按钮）。
 */
public class GUIXenFluidTransceiver extends GuiInfoContainer {

	/** 客户端剪贴板频率，便于跨维度一键复制/粘贴 */
	public static int copiedFrequency = 1000;

	private static final ResourceLocation TEX_STEADY = new ResourceLocation(
			MakeNTMGreatAgain.MODID, "textures/gui/gui_xen_fluid_transceiver.png");
	private static final ResourceLocation TEX_OVERCLOCK = new ResourceLocation(
			MakeNTMGreatAgain.MODID, "textures/gui/gui_xen_fluid_transceiver_overclock.png");

	private static final int W = ContainerXenFluidTransceiver.PANEL_W;
	private static final int H = ContainerXenFluidTransceiver.PANEL_H;

	private static final int COL_X0 = ContainerXenFluidTransceiver.COL_START_X;
	private static final int COL_STEP = ContainerXenFluidTransceiver.COL_STEP_X;
	private static final int COL_W = 36;
	private static final int TANK_Y = 86;
	private static final int TANK_H = 34;

	private static final int[] FREQ_DELTAS = { -100, -10, -1, 1, 10, 100 };
	private static final String[] FREQ_LABELS = { "-10", "-1", "-.1", "+.1", "+1", "+10" };

	private final TileEntityXenFluidTransceiver te;

	public GUIXenFluidTransceiver(InventoryPlayer invPlayer, TileEntityXenFluidTransceiver te) {
		super(new ContainerXenFluidTransceiver(invPlayer, te));
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
		super.mouseClicked(mouseX, mouseY, button);

		int rx = mouseX - guiLeft;
		int ry = mouseY - guiTop;

		// 1. 频率微调按钮
		if(button == 0 && ry >= 18 && ry < 31) {
			for(int i = 0; i < 6; i++) {
				int bx = 128 + i * 18;
				if(rx >= bx && rx < bx + 17) {
					NBTTagCompound tag = new NBTTagCompound();
					tag.setInteger("freqDelta", FREQ_DELTAS[i]);
					sendCtrl(tag);
					return;
				}
			}
		}

		// 2. 复制 / 粘贴 / 分发模式
		if(button == 0 && ry >= 32 && ry < 44) {
			if(rx >= 128 && rx < 155) {
				copiedFrequency = te.frequency;
				mc.getSoundHandler().playSound(PositionedSoundRecord.func_147674_a(new ResourceLocation("gui.button.press"), 1.2F));
				return;
			}
			if(rx >= 157 && rx < 184) {
				NBTTagCompound tag = new NBTTagCompound();
				tag.setInteger("freq", copiedFrequency);
				sendCtrl(tag);
				return;
			}
			if(rx >= 186 && rx < 238) {
				NBTTagCompound tag = new NBTTagCompound();
				tag.setBoolean("toggleDist", true);
				sendCtrl(tag);
				return;
			}
		}

		// 3. 超频模式总开关
		if(button == 0 && rx >= 128 && rx < 238 && ry >= 45 && ry < 58) {
			NBTTagCompound tag = new NBTTagCompound();
			tag.setBoolean("toggleOverclock", true);
			sendCtrl(tag);
			return;
		}

		// 4. 6 路通道独立控制
		int active = te.getActiveChannelCount();
		for(int i = 0; i < active; i++) {
			int cx = COL_X0 + i * COL_STEP;
			if(rx < cx + 2 || rx >= cx + COL_W - 2) continue;

			if(ry >= 122 && ry < 132 && button == 0) {
				NBTTagCompound tag = new NBTTagCompound();
				tag.setInteger("chMode", i);
				sendCtrl(tag);
				return;
			}
			if(ry >= 133 && ry < 143 && (button == 0 || button == 1)) {
				NBTTagCompound tag = new NBTTagCompound();
				tag.setInteger("chPress", i);
				tag.setInteger("delta", button == 1 ? -1 : 1);
				sendCtrl(tag);
				return;
			}
			if(ry >= 144 && ry < 154 && (button == 0 || button == 1)) {
				NBTTagCompound tag = new NBTTagCompound();
				if(button == 1) {
					int next = Math.max(1000, te.channelRate[i] / 2);
					tag.setInteger("chRate", i);
					tag.setInteger("rateVal", next);
				} else {
					tag.setInteger("chRateCycle", i);
				}
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

		int active = te.getActiveChannelCount();
		for(int i = 0; i < active; i++) {
			int cx = COL_X0 + i * COL_STEP;
			te.tanks[i].renderTankInfo(this, mouseX, mouseY, guiLeft + cx + 10, guiTop + TANK_Y, 16, TANK_H);
		}

		int rx = mouseX - guiLeft;
		int ry = mouseY - guiTop;

		// 频率微调提示
		if(ry >= 18 && ry < 31 && rx >= 128 && rx < 235) {
			showWrappedTip(mouseX, mouseY,
					XenGuiHelper.tr("mnga.xen.tip.freq.1"),
					XenGuiHelper.tr("mnga.xen.tip.freq.fluid"));
			return;
		}

		// 复制 / 粘贴 / 分发策略提示
		if(ry >= 32 && ry < 44) {
			if(rx >= 128 && rx < 155) {
				showWrappedTip(mouseX, mouseY, XenGuiHelper.tr("mnga.xen.tip.copy"));
				return;
			}
			if(rx >= 157 && rx < 184) {
				showWrappedTip(mouseX, mouseY, XenGuiHelper.tr("mnga.xen.tip.paste", String.format("%05.1f", copiedFrequency / 10.0F)));
				return;
			}
			if(rx >= 186 && rx < 238) {
				if(te.distMode == TileEntityXenFluidTransceiver.DIST_ROUND_ROBIN) {
					showWrappedTip(mouseX, mouseY,
							XenGuiHelper.tr("mnga.xen.tip.dist.rr.1"),
							XenGuiHelper.tr("mnga.xen.tip.dist.rr.fluid"));
				} else {
					showWrappedTip(mouseX, mouseY,
							XenGuiHelper.tr("mnga.xen.tip.dist.prio.1"),
							XenGuiHelper.tr("mnga.xen.tip.dist.prio.fluid"));
				}
				return;
			}
		}

		// 超频按钮悬停说明
		if(rx >= 128 && rx < 238 && ry >= 45 && ry < 58) {
			if(te.overclocked) {
				showWrappedTip(mouseX, mouseY,
						XenGuiHelper.tr("mnga.xen.tip.fluid.oc_on.1"),
						XenGuiHelper.tr("mnga.xen.tip.fluid.oc_on.2", String.format("%.2f", te.lastRadPerTick), String.format("%.1f", te.lastRadPerTick * 20.0F)),
						XenGuiHelper.tr("mnga.xen.tip.fluid.oc_on.3"),
						XenGuiHelper.tr("mnga.xen.tip.fluid.oc_on.4"),
						XenGuiHelper.tr("mnga.xen.tip.fluid.oc_on.5"));
			} else {
				showWrappedTip(mouseX, mouseY,
						XenGuiHelper.tr("mnga.xen.tip.fluid.oc_off.1"),
						XenGuiHelper.tr("mnga.xen.tip.fluid.oc_off.2"),
						XenGuiHelper.tr("mnga.xen.tip.fluid.oc_off.3"),
						XenGuiHelper.tr("mnga.xen.tip.fluid.oc_off.4"));
			}
			return;
		}

		// 通道按钮提示
		for(int i = 0; i < TileEntityXenFluidTransceiver.MAX_CHANNELS; i++) {
			int cx = COL_X0 + i * COL_STEP;
			if(rx >= cx && rx < cx + COL_W && ry >= 61 && ry < 155) {
				if(i >= active) {
					showWrappedTip(mouseX, mouseY, XenGuiHelper.tr("mnga.xen.tip.fluid.ch_lock", (i + 1)));
					return;
				}
				if(ry >= 122 && ry < 132) {
					showWrappedTip(mouseX, mouseY, XenGuiHelper.tr("mnga.xen.tip.fluid.ch_mode"));
				} else if(ry >= 133 && ry < 143) {
					int p = te.channelPressure[i];
					String roleKey = te.channelMode[i] == TileEntityXenFluidTransceiver.MODE_SEND
							? "mnga.xen.tip.fluid.press_send"
							: (te.channelMode[i] == TileEntityXenFluidTransceiver.MODE_RECV
									? "mnga.xen.tip.fluid.press_recv"
									: "mnga.xen.tip.fluid.press_off");
					showWrappedTip(mouseX, mouseY,
							XenGuiHelper.tr(roleKey, p),
							XenGuiHelper.tr("mnga.xen.tip.fluid.press_op"));
				} else if(ry >= 144 && ry < 154) {
					int eff = Math.min(te.channelRate[i], te.getMaxAllowedRate());
					showWrappedTip(mouseX, mouseY,
							XenGuiHelper.tr("mnga.xen.tip.fluid.rate.1", eff, (eff / 20)),
							XenGuiHelper.tr("mnga.xen.tip.fluid.rate.2", te.getMaxAllowedRate()),
							XenGuiHelper.tr("mnga.xen.tip.fluid.rate.3"));
				}
				return;
			}
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
		XenGuiHelper.drawOscilloscope(x + 10, y + 18, 114, 38, oc, linked, te.frequency);

		int rx = mouseX - guiLeft;
		int ry = mouseY - guiTop;
		for(int i = 0; i < 6; i++) {
			int bx = 128 + i * 18;
			boolean hover = rx >= bx && rx < bx + 17 && ry >= 18 && ry < 31;
			XenGuiHelper.drawSmallButton(x + bx, y + 18, 17, 13, hover, oc);
		}
		XenGuiHelper.drawSmallButton(x + 128, y + 32, 27, 12, rx >= 128 && rx < 155 && ry >= 32 && ry < 44, oc);
		XenGuiHelper.drawSmallButton(x + 157, y + 32, 27, 12, rx >= 157 && rx < 184 && ry >= 32 && ry < 44, oc);
		XenGuiHelper.drawSmallButton(x + 186, y + 32, 52, 12, rx >= 186 && rx < 238 && ry >= 32 && ry < 44, oc);

		boolean ocHover = rx >= 128 && rx < 238 && ry >= 45 && ry < 58;
		if(oc) {
			float pulse = 0.5F + 0.5F * (float) Math.sin(System.currentTimeMillis() / 160.0D);
			int r = (int) (175 + pulse * 55);
			int g = (int) (45 + pulse * 40);
			int btnFill = (0xFF << 24) | (r << 16) | (g << 8) | 0x18;
			XenGuiHelper.drawHbmButton(x + 128, y + 45, 110, 13, 0xFF1E0404, 0xFFFFAA44, 0xFF6B1408, 0xFFB84420, ocHover ? 0xFFFF6E24 : btnFill);
		} else {
			XenGuiHelper.drawHbmButton(x + 128, y + 45, 110, 13, 0xFF141414, 0xFF9EB0B8, 0xFF343E42, 0xFF566870, ocHover ? 0xFF58727C : 0xFF485C64);
		}

		int active = te.getActiveChannelCount();
		for(int i = 0; i < TileEntityXenFluidTransceiver.MAX_CHANNELS; i++) {
			int cx = x + COL_X0 + i * COL_STEP;
			boolean unlocked = i < active;

			if(unlocked) {
				te.tanks[i].renderTank(cx + 10, y + TANK_Y + TANK_H, this.zLevel, 16, TANK_H);
				GL11.glColor4f(1F, 1F, 1F, 1F);

				int mode = te.channelMode[i];
				if(mode == TileEntityXenFluidTransceiver.MODE_SEND) {
					XenGuiHelper.drawHbmButton(cx + 2, y + 122, COL_W - 4, 10, 0xFF121212, 0xFF3EA4CC, 0xFF123A4A, 0xFF206886, 0xFF1E6480);
				} else if(mode == TileEntityXenFluidTransceiver.MODE_RECV) {
					XenGuiHelper.drawHbmButton(cx + 2, y + 122, COL_W - 4, 10, 0xFF121212, 0xFF4CC472, 0xFF164225, 0xFF287842, 0xFF246E3C);
				} else {
					XenGuiHelper.drawSmallButton(cx + 2, y + 122, COL_W - 4, 10, false, oc);
				}

				if(te.channelPressure[i] > 0) {
					XenGuiHelper.drawHbmButton(cx + 2, y + 133, COL_W - 4, 10, 0xFF121212, 0xFFC89838, 0xFF42300E, 0xFF785A1C, 0xFF70541A);
				} else {
					XenGuiHelper.drawSmallButton(cx + 2, y + 133, COL_W - 4, 10, false, oc);
				}

				XenGuiHelper.drawSmallButton(cx + 2, y + 144, COL_W - 4, 10, false, oc);
			} else {
				drawRect(cx + 1, y + 61, cx + COL_W - 1, y + 154, 0xAA181818);
				for(int sy = 64; sy < 152; sy += 8) {
					drawRect(cx + 3, y + sy, cx + COL_W - 3, y + sy + 1, 0x44FF5522);
				}
			}
		}
	}

	@Override
	protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
		boolean oc = te.overclocked;

		String title = XenGuiHelper.tr(oc ? "mnga.xen.fluid.title_oc" : "mnga.xen.fluid.title_steady");
		XenGuiHelper.drawCenteredFit(this.fontRendererObj, title, 0, 5, W, 11, oc ? 0xFFFF9640 : 0xFFE6F4F8);

		String freqText = String.format("%05.1f MHz", te.frequency / 10.0F);
		this.fontRendererObj.drawString(freqText, 14, 21, oc ? 0xFFFFAA44 : 0xFF66F0FF);
		if(oc) {
			String radText = XenGuiHelper.tr("mnga.xen.status.rad_t", String.format("%.1f", te.lastRadPerTick));
			this.fontRendererObj.drawString(radText, 14, 33, 0xFFFF7744);
		} else {
			String tpText = te.lastThroughput > 0 ? (formatRate(te.lastThroughput) + "/s") : XenGuiHelper.tr("mnga.xen.status.idle");
			this.fontRendererObj.drawString(tpText, 14, 33, 0xFF77B8C8);
		}
		String peerText = XenGuiHelper.tr("mnga.xen.status.peers", te.peerSenders, te.peerReceivers)
				+ (te.hasCrossDimPeer ? XenGuiHelper.tr("mnga.xen.status.cross") : "");
		this.fontRendererObj.drawString(peerText, 14, 45, oc ? 0xFFFFB899 : 0xFFAAD8E6);

		for(int i = 0; i < 6; i++) {
			XenGuiHelper.drawCenteredFit(this.fontRendererObj, FREQ_LABELS[i], 128 + i * 18, 18, 17, 13, 0xFFE8E8E8);
		}
		XenGuiHelper.drawCenteredFit(this.fontRendererObj, XenGuiHelper.tr("mnga.xen.copy"), 128, 32, 27, 12, 0xFFD8D8D8);
		XenGuiHelper.drawCenteredFit(this.fontRendererObj, XenGuiHelper.tr("mnga.xen.paste"), 157, 32, 27, 12, 0xFFD8D8D8);
		XenGuiHelper.drawCenteredFit(this.fontRendererObj,
				XenGuiHelper.tr(te.distMode == TileEntityXenFluidTransceiver.DIST_ROUND_ROBIN
						? "mnga.xen.dist.rr_long" : "mnga.xen.dist.prio_long"),
				186, 32, 52, 12, 0xFFE8E8E8);

		String ocLbl = XenGuiHelper.tr(oc ? "mnga.xen.oc.on_fluid" : "mnga.xen.oc.off_fluid");
		XenGuiHelper.drawCenteredFit(this.fontRendererObj, ocLbl, 128, 45, 110, 13, oc ? 0xFFFFF0D0 : 0xFFB8E8F4);

		int active = te.getActiveChannelCount();
		for(int i = 0; i < TileEntityXenFluidTransceiver.MAX_CHANNELS; i++) {
			int cx = COL_X0 + i * COL_STEP;
			if(i >= active) {
				XenGuiHelper.drawCenteredFit(this.fontRendererObj, "CH" + (i + 1), cx, 86, COL_W, 12, 0xFF888888);
				XenGuiHelper.drawCenteredFit(this.fontRendererObj, XenGuiHelper.tr("mnga.xen.ch.oc_word"), cx, 100, COL_W, 10, 0xFFCC5533);
				XenGuiHelper.drawCenteredFit(this.fontRendererObj, XenGuiHelper.tr("mnga.xen.ch.unlock"), cx, 110, COL_W, 10, 0xFFCC5533);
				continue;
			}
			String modeKey = te.channelMode[i] == TileEntityXenFluidTransceiver.MODE_SEND ? "mnga.xen.ch.send"
					: (te.channelMode[i] == TileEntityXenFluidTransceiver.MODE_RECV ? "mnga.xen.ch.recv" : "mnga.xen.ch.off");
			XenGuiHelper.drawCenteredFit(this.fontRendererObj, XenGuiHelper.tr(modeKey), cx + 2, 122, COL_W - 4, 10, 0xFFF0F0F0);
			XenGuiHelper.drawCenteredFit(this.fontRendererObj, te.channelPressure[i] + " PU", cx + 2, 133, COL_W - 4, 10, 0xFFFFE090);
			int effRate = Math.min(te.channelRate[i], te.getMaxAllowedRate());
			XenGuiHelper.drawCenteredFit(this.fontRendererObj, formatRate(effRate) + "/s", cx + 2, 144, COL_W - 4, 10, oc ? 0xFFFFB070 : 0xFFAEE8F5);
		}

		XenGuiHelper.drawCenteredFit(this.fontRendererObj, XenGuiHelper.tr("mnga.xen.status.throughput"), 207, 162, 36, 10, 0xFFB8C8D0);
		XenGuiHelper.drawCenteredFit(this.fontRendererObj, formatRate(te.lastThroughput) + "/s", 207, 174, 36, 10, oc ? 0xFFFFB070 : 0xFF66F0FF);
		if(oc) {
			XenGuiHelper.drawCenteredFit(this.fontRendererObj, XenGuiHelper.tr("mnga.xen.status.rad"), 207, 192, 36, 10, 0xFFFF9977);
			XenGuiHelper.drawCenteredFit(this.fontRendererObj, String.format("%.1f/s", te.lastRadPerTick * 20.0F), 207, 204, 36, 10, 0xFFFF6644);
		}
	}

	private static String formatRate(int mb) {
		if(mb >= 1000) {
			return (mb / 1000) + "k";
		}
		return String.valueOf(mb);
	}
}
