package com.makentmgreatagain.init;

import com.makentmgreatagain.packet.CigaretteDrxEffectPacket;
import com.makentmgreatagain.packet.HoverJetpackModePacket;

import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import cpw.mods.fml.relauncher.Side;

public class ModNetwork {

	public static SimpleNetworkWrapper NETWORK;

	public static void init() {
		NETWORK = NetworkRegistry.INSTANCE.newSimpleChannel("MNTGA|drx");
		NETWORK.registerMessage(
				CigaretteDrxEffectPacket.Handler.class,
				CigaretteDrxEffectPacket.class,
				0,
				Side.CLIENT);
		// 悬浮背包：模式切换请求（C→S）与权威确认（S→C）
		NETWORK.registerMessage(
				HoverJetpackModePacket.HandlerServer.class,
				HoverJetpackModePacket.Request.class,
				1,
				Side.SERVER);
		NETWORK.registerMessage(
				HoverJetpackModePacket.HandlerClient.class,
				HoverJetpackModePacket.Ack.class,
				2,
				Side.CLIENT);
	}
}
