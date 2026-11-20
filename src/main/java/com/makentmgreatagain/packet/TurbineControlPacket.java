package com.makentmgreatagain.packet;

import com.makentmgreatagain.tileentity.TileEntityIndustrialGasTurbine;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

/**
 * 工业燃气轮机控制包（C→S）：kind0=开关，kind1=节流阀挡位。
 * 世界操作入队由 {@link ExtractorSwitchPacket.ServerTaskPump} 搬回主线程。
 */
public class TurbineControlPacket implements IMessage {

	public int x, y, z;
	public int kind;
	public int value;

	public TurbineControlPacket() { }

	public TurbineControlPacket(int x, int y, int z, int kind, int value) {
		this.x = x; this.y = y; this.z = z; this.kind = kind; this.value = value;
	}

	@Override
	public void fromBytes(ByteBuf buf) {
		x = buf.readInt(); y = buf.readInt(); z = buf.readInt();
		kind = buf.readInt(); value = buf.readInt();
	}

	@Override
	public void toBytes(ByteBuf buf) {
		buf.writeInt(x); buf.writeInt(y); buf.writeInt(z);
		buf.writeInt(kind); buf.writeInt(value);
	}

	public static class Handler implements IMessageHandler<TurbineControlPacket, IMessage> {
		@Override
		public IMessage onMessage(final TurbineControlPacket msg, final MessageContext ctx) {
			if(ctx.side != Side.SERVER) return null;
			final EntityPlayer player = ctx.getServerHandler().playerEntity;
			if(player == null) return null;

			ExtractorSwitchPacket.MAIN_THREAD_TASKS.add(new Runnable() {
				@Override
				public void run() {
					World world = player.worldObj;
					if(world == null) return;
					TileEntity te = world.getTileEntity(msg.x, msg.y, msg.z);
					if(te instanceof TileEntityIndustrialGasTurbine
							&& MachinePacketSecurity.canOperate(player, te)) {
						TileEntityIndustrialGasTurbine t = (TileEntityIndustrialGasTurbine) te;
						if(msg.kind == 0) t.trySetRunning(msg.value != 0);
						else if(msg.kind == 1) t.setThrottle(msg.value);
					}
				}
			});
			return null;
		}
	}
}
