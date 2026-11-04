package com.makentmgreatagain.packet;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/**
 * 香烟全屏效果通知包（服务端 -> 单个客户端）。
 *
 * <p>服务端在玩家抽完一支烟且计数递增后，把当前档位（1~5）发给该玩家；
 * 客户端处理器只做一个动作：向 CPU 总线投递 CigaretteScreenEvent，
 * 由客户端渲染监听器（ScreenEffectManager）实际执行效果。</p>
 */
public class CigaretteDrxEffectPacket implements IMessage {

	private int stage;

	public CigaretteDrxEffectPacket() { }

	public CigaretteDrxEffectPacket(int stage) {
		this.stage = stage;
	}

	@Override
	public void fromBytes(ByteBuf buf) {
		this.stage = buf.readInt();
	}

	@Override
	public void toBytes(ByteBuf buf) {
		buf.writeInt(this.stage);
	}

	/** 双向安全：不引用任何 @SideOnly(CLIENT) 的类，只写一个 volatile 状态槽。 */
	public static class Handler implements IMessageHandler<CigaretteDrxEffectPacket, IMessage> {
		@Override
		public IMessage onMessage(CigaretteDrxEffectPacket msg, MessageContext ctx) {
			if (ctx.side.isClient() && msg.stage > 0) {
				// 可能跑在 netty 线程：只写标记，渲染管理器在主线程下一帧读取
				CigaretteScreenEvent.pendingStage = msg.stage;
			}
			return null;
		}
	}
}