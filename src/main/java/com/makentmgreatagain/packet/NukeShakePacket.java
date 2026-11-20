package com.makentmgreatagain.packet;

import com.makentmgreatagain.render.ClientScreenShake;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;
import io.netty.buffer.ByteBuf;

/**
 * 核爆屏幕抖动（S→C）。
 *
 * <p>服务端在爆炸那一 tick 发给附近玩家，客户端在相机事件里抖镜头；
 * 时长与幅度由核心的当量决定，服务端算好后随包带过来。</p>
 */
public class NukeShakePacket {

	public static class Message implements IMessage {

		public int duration;
		public float intensity;

		public Message() { }

		public Message(int duration, float intensity) {
			this.duration = duration;
			this.intensity = intensity;
		}

		@Override
		public void fromBytes(ByteBuf buf) {
			this.duration = buf.readInt();
			this.intensity = buf.readFloat();
		}

		@Override
		public void toBytes(ByteBuf buf) {
			buf.writeInt(this.duration);
			buf.writeFloat(this.intensity);
		}
	}

	public static class Handler implements IMessageHandler<Message, IMessage> {

		@Override
		public IMessage onMessage(Message msg, MessageContext ctx) {
			if(ctx.side == Side.CLIENT) ClientScreenShake.start(msg.duration, msg.intensity);
			return null;
		}
	}
}
