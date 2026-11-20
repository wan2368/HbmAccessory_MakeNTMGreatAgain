package com.makentmgreatagain.packet;

import com.makentmgreatagain.MakeNTMGreatAgain;
import com.makentmgreatagain.tileentity.TileEntityNuclearExplosionGenerator;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

/**
 * 可控核爆炸发生器开界面（C→S）。
 *
 * <p>模型外壳比 13×13 的方块体积宽一大圈，玩家点外壳时命中的方块格常常在 6 格 reach 之外，
 * 原版的 C08 包会被服务端按距离直接丢掉，界面就永远打不开。
 * 这里由客户端在 {@code onBlockActivated} 里自己发一份核心坐标，服务端校验距离后开界面。</p>
 */
public class NukeOpenGuiPacket {

	/** 玩家到核心的最大距离（格）：结构 13×13×17、模型外伸到 ±12，给宽松一点 */
	private static final int MAX_DISTANCE = 32;

	public static class Message implements IMessage {

		public int x;
		public int y;
		public int z;

		public Message() { }

		public Message(int x, int y, int z) {
			this.x = x;
			this.y = y;
			this.z = z;
		}

		@Override
		public void fromBytes(ByteBuf buf) {
			this.x = buf.readInt();
			this.y = buf.readInt();
			this.z = buf.readInt();
		}

		@Override
		public void toBytes(ByteBuf buf) {
			buf.writeInt(x);
			buf.writeInt(y);
			buf.writeInt(z);
		}
	}

	public static class Handler implements IMessageHandler<Message, IMessage> {

		@Override
		public IMessage onMessage(final Message msg, final MessageContext ctx) {

			if(ctx.side != Side.SERVER) return null;

			final EntityPlayer player = ctx.getServerHandler().playerEntity;
			if(player == null) return null;

			NukeRunPacket.schedule(new Runnable() {
				@Override
				public void run() {

					World world = player.worldObj;
					if(world == null) return;

					TileEntity te = world.getTileEntity(msg.x, msg.y, msg.z);
					if(!(te instanceof TileEntityNuclearExplosionGenerator)) return;

					if(player.getDistanceSq(msg.x + 0.5D, msg.y + 0.5D, msg.z + 0.5D)
							> MAX_DISTANCE * MAX_DISTANCE) return;

					player.openGui(MakeNTMGreatAgain.instance,
							MakeNTMGreatAgain.GUI_NUCLEAR_EXPLOSION_GENERATOR,
							world, msg.x, msg.y, msg.z);
				}
			});

			return null;
		}
	}
}
