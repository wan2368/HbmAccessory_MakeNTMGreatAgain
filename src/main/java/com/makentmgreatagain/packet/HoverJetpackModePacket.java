package com.makentmgreatagain.packet;

import com.hbm.handler.ArmorModHandler;
import com.makentmgreatagain.items.ItemHoverJetPack;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;

/**
 * 悬浮作业背包模式切换协议（一对包）。
 *
 * Request（客户端→服务端）：玩家按下了模式键，请求翻转。
 * Ack（服务端→客户端）：服务端定位并写入权威模式后回发，客户端据此刷新显示。
 * 客户端不预改值，以服务端结果为准，避免双端不一致。
 */
public class HoverJetpackModePacket {

	/** C → S：请求切换 */
	public static class Request implements IMessage {
		public Request() { }
		@Override public void fromBytes(ByteBuf buf) { }
		@Override public void toBytes(ByteBuf buf) { }
	}

	/** S → C：回发权威模式（0 矢量 / 1 悬浮） */
	public static class Ack implements IMessage {

		public int mode;

		public Ack() { }
		public Ack(int mode) { this.mode = mode; }

		@Override
		public void fromBytes(ByteBuf buf) { this.mode = buf.readByte(); }
		@Override
		public void toBytes(ByteBuf buf) { buf.writeByte(this.mode); }
	}

	/** 服务端处理：定位玩家身上的悬浮背包，翻转模式并回发 */
	public static class HandlerServer implements IMessageHandler<Request, IMessage> {

		@Override
		public IMessage onMessage(Request msg, MessageContext ctx) {

			if (ctx.side != Side.SERVER) return null;

			EntityPlayer player = ctx.getServerHandler().playerEntity;
			if (player == null) return null;

			int mode;

			ItemStack chest = player.getCurrentArmor(2);     // 2 = 胸甲槽

			if (chest != null && chest.getItem() instanceof ItemHoverJetPack) {
				// 单穿
				mode = toggle(chest);
				player.inventoryContainer.detectAndSendChanges();
			} else if (chest != null && ArmorModHandler.hasMods(chest)) {
				// 装在胸甲里：pryMods 得到的是副本，改完必须 applyMod 写回
				ItemStack[] mods = ArmorModHandler.pryMods(chest);
				ItemStack jet = mods == null ? null : mods[ArmorModHandler.plate_only];
				if (jet == null || !(jet.getItem() instanceof ItemHoverJetPack)) return null;
				mode = toggle(jet);
				ArmorModHandler.applyMod(chest, jet);
				player.inventoryContainer.detectAndSendChanges();
			} else {
				// 没穿在身上：允许改揣在物品栏里的那件
				ItemStack held = findInInventory(player);
				if (held == null) return null;
				mode = toggle(held);
				player.inventoryContainer.detectAndSendChanges();
			}

			inform(player, mode);
			return new Ack(mode);
		}

		/** 切换成功后在左上角提示（复用 HBM 的背包提示槽 ID_JETPACK，与 C 键开关同位置） */
		private static void inform(EntityPlayer player, int mode) {

			if (!(player instanceof net.minecraft.entity.player.EntityPlayerMP)) return;

			String msg = mode == ItemHoverJetPack.MODE_HOVER
					? EnumChatFormatting.AQUA + "悬浮模式" + EnumChatFormatting.GRAY + "：空格上升 / Shift下降"
					: EnumChatFormatting.GREEN + "矢量模式" + EnumChatFormatting.GRAY + "：沿视线方向机动";

			com.hbm.packet.PacketDispatcher.wrapper.sendTo(
					new com.hbm.packet.toclient.PlayerInformPacket(msg, com.hbm.main.MainRegistry.proxy.ID_JETPACK, 2000),
					(net.minecraft.entity.player.EntityPlayerMP) player);
		}

		private static int toggle(ItemStack stack) {
			int next = ItemHoverJetPack.getMode(stack) == ItemHoverJetPack.MODE_HOVER
					? ItemHoverJetPack.MODE_VECTORIZED : ItemHoverJetPack.MODE_HOVER;
			ItemHoverJetPack.setMode(stack, next);
			return next;
		}

		private static ItemStack findInInventory(EntityPlayer player) {
			for (ItemStack s : player.inventory.mainInventory) {
				if (s != null && s.getItem() instanceof ItemHoverJetPack) return s;
			}
			return null;
		}
	}

	/** 客户端处理：把权威模式写回本地那份背包（tooltip 下一帧即正确） */
	public static class HandlerClient implements IMessageHandler<Ack, IMessage> {

		@Override
		public IMessage onMessage(Ack msg, MessageContext ctx) {

			if (ctx.side != Side.CLIENT) return null;

			EntityPlayer player = net.minecraft.client.Minecraft.getMinecraft().thePlayer;
			if (player == null) return null;

			ItemStack chest = player.getCurrentArmor(2);

			if (chest != null && chest.getItem() instanceof ItemHoverJetPack) {
				ItemHoverJetPack.setMode(chest, msg.mode);
				return null;
			}
			if (chest != null && ArmorModHandler.hasMods(chest)) {
				ItemStack[] mods = ArmorModHandler.pryMods(chest);
				ItemStack jet = mods == null ? null : mods[ArmorModHandler.plate_only];
				if (jet != null && jet.getItem() instanceof ItemHoverJetPack) {
					ItemHoverJetPack.setMode(jet, msg.mode);
					ArmorModHandler.applyMod(chest, jet);
					return null;
				}
			}
			for (ItemStack s : player.inventory.mainInventory) {
				if (s != null && s.getItem() instanceof ItemHoverJetPack) {
					ItemHoverJetPack.setMode(s, msg.mode);
					break;
				}
			}
			return null;
		}
	}
}
