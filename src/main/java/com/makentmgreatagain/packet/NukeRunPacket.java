package com.makentmgreatagain.packet;

import java.util.concurrent.ConcurrentLinkedQueue;

import com.makentmgreatagain.tileentity.TileEntityNuclearExplosionGenerator;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

/**
 * 可控核爆炸发生器运行键（C→S）。
 *
 * <p>按下去就发一次请求，服务端 {@link TileEntityNuclearExplosionGenerator#tryStart()}
 * 只在空闲时接受；20 秒周期内无法取消，所以这里不带任何"停止"参数。</p>
 *
 * <p>SimpleNetworkWrapper 的 handler 跑在 netty 线程，世界操作入队由
 * {@link ServerTaskPump} 搬回服务端主线程（和地核熔岩开采机同一套线程模型）。</p>
 */
public class NukeRunPacket {

	private static final ConcurrentLinkedQueue<Runnable> MAIN_THREAD_TASKS =
			new ConcurrentLinkedQueue<Runnable>();

	/** 服务端 tick 泵：由 CommonProxy 注册到 FML 总线 */
	public static class ServerTaskPump {
		@SubscribeEvent
		public void onServerTick(TickEvent.ServerTickEvent event) {
			Runnable task;
			while((task = MAIN_THREAD_TASKS.poll()) != null) {
				task.run();
			}
		}
	}

	/** 把任务排到服务端主线程，其它包也可以复用这个泵 */
	public static void schedule(Runnable task) {
		MAIN_THREAD_TASKS.add(task);
	}

	/** C → S：运行键。普通点击 = 启动 / 取消，Shift 点击 = 开关自动模式 */
	public static class Request implements IMessage {

		public int x;
		public int y;
		public int z;
		public boolean shift;

		public Request() { }

		public Request(int x, int y, int z, boolean shift) {
			this.x = x;
			this.y = y;
			this.z = z;
			this.shift = shift;
		}

		@Override
		public void fromBytes(ByteBuf buf) {
			this.x = buf.readInt();
			this.y = buf.readInt();
			this.z = buf.readInt();
			this.shift = buf.readBoolean();
		}

		@Override
		public void toBytes(ByteBuf buf) {
			buf.writeInt(x);
			buf.writeInt(y);
			buf.writeInt(z);
			buf.writeBoolean(shift);
		}
	}

	public static class Handler implements IMessageHandler<Request, IMessage> {

		@Override
		public IMessage onMessage(final Request msg, final MessageContext ctx) {

			if(ctx.side != Side.SERVER) return null;

			final EntityPlayer player = ctx.getServerHandler().playerEntity;
			if(player == null) return null;

			MAIN_THREAD_TASKS.add(new Runnable() {
				@Override
				public void run() {
					World world = player.worldObj;
					if(world == null) return;
					TileEntity te = world.getTileEntity(msg.x, msg.y, msg.z);
					if(te instanceof TileEntityNuclearExplosionGenerator
							&& MachinePacketSecurity.canOperate(player, te)) {
						TileEntityNuclearExplosionGenerator nuke = (TileEntityNuclearExplosionGenerator) te;
						if(msg.shift) nuke.toggleAuto();
						else nuke.onRunKey();
					}
				}
			});

			return null;
		}
	}
}
