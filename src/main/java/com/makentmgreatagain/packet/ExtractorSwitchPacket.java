package com.makentmgreatagain.packet;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

import com.makentmgreatagain.tileentity.TileEntityCoreLavaExtractor;

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
 * 地核熔岩开采机开关切换包（C→S）。
 *
 * <p>客户端不预改 running，以服务端 {@link TileEntityCoreLavaExtractor#trySetRunning} 的结果为准；
 * 界面状态靠 TE 每秒一次的 description packet 回同步。</p>
 *
 * <p>1.7.10 的 SimpleNetworkWrapper handler 跑在 netty 线程，世界操作入队由
 * {@link ServerTaskPump} 搬回服务端主线程（与悬浮背包包同一套线程模型）。</p>
 */
public class ExtractorSwitchPacket {

	public static final int MAX_PENDING_TASKS = 256;
	public static final int MAX_TASKS_PER_TICK = 32;
	public static final BlockingQueue<Runnable> MAIN_THREAD_TASKS =
			new ArrayBlockingQueue<Runnable>(MAX_PENDING_TASKS);

	public static boolean schedule(Runnable task) {
		return task != null && MAIN_THREAD_TASKS.offer(task);
	}

	/** 服务端 tick 泵：由 CommonProxy 注册到 FML 总线 */
	public static class ServerTaskPump {
		@SubscribeEvent
		public void onServerTick(TickEvent.ServerTickEvent event) {
			if(event.phase != TickEvent.Phase.END) return;
			Runnable task;
			for(int i = 0; i < MAX_TASKS_PER_TICK && (task = MAIN_THREAD_TASKS.poll()) != null; i++) {
				try {
					task.run();
				} catch(Throwable t) {
					com.makentmgreatagain.MakeNTMGreatAgain.LOG.error("Queued machine packet task failed", t);
				}
			}
		}
	}

	/** C → S：请求把 (x,y,z) 的机器开关设为 on */
	public static class Request implements IMessage {

		public int x;
		public int y;
		public int z;
		public boolean on;

		public Request() { }

		public Request(int x, int y, int z, boolean on) {
			this.x = x;
			this.y = y;
			this.z = z;
			this.on = on;
		}

		@Override
		public void fromBytes(ByteBuf buf) {
			this.x = buf.readInt();
			this.y = buf.readInt();
			this.z = buf.readInt();
			this.on = buf.readBoolean();
		}

		@Override
		public void toBytes(ByteBuf buf) {
			buf.writeInt(x);
			buf.writeInt(y);
			buf.writeInt(z);
			buf.writeBoolean(on);
		}
	}

	public static class Handler implements IMessageHandler<Request, IMessage> {

		@Override
		public IMessage onMessage(final Request msg, final MessageContext ctx) {

			if(ctx.side != Side.SERVER) return null;

			final EntityPlayer player = ctx.getServerHandler().playerEntity;
			if(player == null) return null;

			schedule(new Runnable() {
				@Override
				public void run() {
					World world = player.worldObj;
					if(world == null) return;
					TileEntity te = world.getTileEntity(msg.x, msg.y, msg.z);
					if(te instanceof TileEntityCoreLavaExtractor
							&& MachinePacketSecurity.canOperate(player, te))
						((TileEntityCoreLavaExtractor) te).trySetRunning(msg.on);
				}
			});

			return null;
		}
	}
}
