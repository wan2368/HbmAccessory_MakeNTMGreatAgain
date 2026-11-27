package com.makentmgreatagain.init;

import com.makentmgreatagain.packet.CigaretteDrxEffectPacket;
import com.makentmgreatagain.packet.ExtractorSwitchPacket;
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
		// 地核熔岩开采机：开关切换请求（C→S）
		NETWORK.registerMessage(
				ExtractorSwitchPacket.Handler.class,
				ExtractorSwitchPacket.Request.class,
				3,
				Side.SERVER);
		// 可控核爆炸发生器：运行键请求（C→S），20 秒周期内不可取消
		NETWORK.registerMessage(
				com.makentmgreatagain.packet.NukeRunPacket.Handler.class,
				com.makentmgreatagain.packet.NukeRunPacket.Request.class,
				4,
				Side.SERVER);
		// 可控核爆炸发生器：核爆屏幕抖动（S→C）
		NETWORK.registerMessage(
				com.makentmgreatagain.packet.NukeShakePacket.Handler.class,
				com.makentmgreatagain.packet.NukeShakePacket.Message.class,
				5,
				Side.CLIENT);
		// 可控核爆炸发生器：右键开界面（C→S）。模型外壳超出方块体积，不能靠原版 C08 的距离检查
		NETWORK.registerMessage(
				com.makentmgreatagain.packet.NukeOpenGuiPacket.Handler.class,
				com.makentmgreatagain.packet.NukeOpenGuiPacket.Message.class,
				6,
				Side.SERVER);
		// 工业燃气轮机：开关/节流阀控制（C→S）
		NETWORK.registerMessage(
				com.makentmgreatagain.packet.TurbineControlPacket.Handler.class,
				com.makentmgreatagain.packet.TurbineControlPacket.class,
				7,
				Side.SERVER);
		// 大型火力发电站：GUI 开关控制（C→S）
		NETWORK.registerMessage(
				com.makentmgreatagain.packet.LargeThermalPowerStationControlPacket.Handler.class,
				com.makentmgreatagain.packet.LargeThermalPowerStationControlPacket.class,
				8,
				Side.SERVER);
		// 成就走 Minecraft 原生的 S37PacketStatistics，不再需要自定义包
	}
}
