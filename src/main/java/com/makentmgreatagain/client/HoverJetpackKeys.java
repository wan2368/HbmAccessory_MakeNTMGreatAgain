package com.makentmgreatagain.client;

import org.lwjgl.input.Keyboard;

import com.makentmgreatagain.items.ItemHoverJetPack;
import com.makentmgreatagain.packet.HoverJetpackModePacket;

import cpw.mods.fml.client.registry.ClientRegistry;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.InputEvent.KeyInputEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

/**
 * 悬浮作业背包的模式切换按键（客户端）。
 *
 * 默认键位左 Ctrl（可在"选项→控制"里改，与 HBM 的 C 背包开关互不影响）。
 * 按下后只向服务端发一次请求，模式值以服务端回发为准（见 HoverJetpackModePacket），
 * 避免客户端本地先改造成双端不一致。
 */
@SideOnly(Side.CLIENT)
public class HoverJetpackKeys {

	public static final KeyBinding modeKey =
			new KeyBinding("makentmgreatagain.key.hoverMode", Keyboard.KEY_LCONTROL, "makentmgreatagain.key.group");

	public static void register() {
		ClientRegistry.registerKeyBinding(modeKey);
	}

	/** tooltip 里显示的当前绑定键名（玩家自定义后自动跟着变） */
	public static String getBoundKeyDisplay() {
		try {
			return org.lwjgl.input.Keyboard.getKeyName(modeKey.getKeyCode());
		} catch (Throwable t) {
			return "LCtrl";
		}
	}

	@SubscribeEvent
	public void onKeyInput(KeyInputEvent event) {

		if (!modeKey.isPressed()) return;                    // isPressed = 按下沿（消耗式）

		EntityPlayer player = Minecraft.getMinecraft().thePlayer;
		if (player == null) return;

		if (!hasJetpack(player)) return;                     // 身上没有悬浮背包 → 不发包

		com.makentmgreatagain.init.ModNetwork.NETWORK.sendToServer(new HoverJetpackModePacket.Request());
	}

	/** 胸甲槽（单穿或装入胸甲的模组）或物品栏里是否有悬浮背包 */
	private static boolean hasJetpack(EntityPlayer player) {

		ItemStack chest = player.getCurrentArmor(2);         // 2 = 胸甲槽
		if (chest != null) {
			if (chest.getItem() instanceof ItemHoverJetPack) return true;
			ItemStack[] mods = com.hbm.handler.ArmorModHandler.pryMods(chest);
			if (mods != null && mods[com.hbm.handler.ArmorModHandler.plate_only] != null
					&& mods[com.hbm.handler.ArmorModHandler.plate_only].getItem() instanceof ItemHoverJetPack)
				return true;
		}

		for (ItemStack s : player.inventory.mainInventory) {
			if (s != null && s.getItem() instanceof ItemHoverJetPack) return true;
		}
		return false;
	}
}
