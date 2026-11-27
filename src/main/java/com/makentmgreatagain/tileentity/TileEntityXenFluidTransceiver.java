package com.makentmgreatagain.tileentity;

import java.util.ArrayList;
import java.util.List;

import com.hbm.blocks.network.IBlockFluidDuct;
import com.hbm.handler.radiation.ChunkRadiationManager;
import com.hbm.interfaces.IControlReceiver;
import com.hbm.inventory.fluid.FluidType;
import com.hbm.inventory.fluid.Fluids;
import com.hbm.inventory.fluid.tank.FluidTank;
import com.hbm.items.machine.IItemFluidIdentifier;
import com.hbm.tileentity.TileEntityMachineBase;
import com.hbm.tileentity.TilePort.PortDef;
import com.hbm.tileentity.network.TileEntityPipeBaseNT;
import com.hbm.tileentity.network.TileEntityPipelineBase;

import api.hbm.fluidmk2.IFluidConnectorBlockMK2;
import api.hbm.fluidmk2.IFluidConnectorMK2;
import api.hbm.fluidmk2.IFluidStandardTransceiverMK2;
import io.netty.buffer.ByteBuf;
import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.ForgeDirection;

/**
 * 流体传送器 TileEntity（无需供电、跨维度、双模式 2 路 / 6 路流体收发）。
 *
 * <ul>
 *   <li>稳态模式：启用 0~1 共 2 路流体通道，单路最高 16,000 mB/s（800 mB/t），零辐射；</li>
 *   <li>超频模式：启用 0~5 共 6 路流体通道，单路最高 64,000 mB/s（3,200 mB/t），无不稳定机制，仅持续向环境累加辐射（内部流体总量越多散发越快）；</li>
 *   <li>每路均配有独立流体识别码槽（{@code slots[0..5]}）、收发模式（关/发/收）、压强（0~5 PU，发送端严格按指定压强接收）与速率上限（mB/s）。</li>
 * </ul>
 */
public class TileEntityXenFluidTransceiver extends TileEntityMachineBase
		implements IFluidStandardTransceiverMK2, IControlReceiver {

	public static final int MAX_CHANNELS = 6;
	public static final int STEADY_CHANNELS = 2;
	public static final int TANK_CAPACITY = 64_000;

	/** 传输速率上限（单位：mB/s） */
	public static final int STEADY_MAX_RATE = 16_000;
	public static final int OVERCLOCK_MAX_RATE = 64_000;
	public static final int DEFAULT_RATE = 16_000;

	/** 每次网络分发周期（每 tick 结算，单 tick 额度 = rate / 20，消除 5 tick 脉冲卡顿） */
	private static final int TRANSFER_INTERVAL = 1;

	public static final int MODE_OFF = 0;
	public static final int MODE_SEND = 1;
	public static final int MODE_RECV = 2;

	public static final int DIST_ROUND_ROBIN = 0;
	public static final int DIST_PRIORITY = 1;

	private static final String TYPE_KEY = "xenFluidType";

	/** 共振频率（0 ~ 9999 → 000.0 ~ 999.9 MHz） */
	public int frequency = 1000;
	private int registeredFreq = -1;

	/** 是否处于超频模式（false=稳态 2 路，true=超频 6 路） */
	public boolean overclocked = false;

	/** 分发策略：0=轮询均分，1=优先填满 */
	public int distMode = DIST_ROUND_ROBIN;

	/** 6 路流体储罐 */
	public final FluidTank[] tanks = new FluidTank[MAX_CHANNELS];
	/** 每路模式：0=关，1=发，2=收 */
	public final int[] channelMode = new int[MAX_CHANNELS];
	/** 每路压强：0~5 PU */
	public final int[] channelPressure = new int[MAX_CHANNELS];
	/** 每路传输速率上限（mB/s） */
	public final int[] channelRate = new int[MAX_CHANNELS];

	/** 上一秒实测跨维度吞吐换算值（mB/s） */
	public int lastThroughput = 0;
	private int throughputWindow = 0;
	/** 当前每秒/每tick散发环境辐射（RAD/t） */
	public float lastRadPerTick = 0.0F;
	/** 同频在线发送端 / 接收端节点数与是否跨维度 */
	public int peerSenders = 0;
	public int peerReceivers = 0;
	public boolean hasCrossDimPeer = false;

	/** 6 面管道连接掩码（bit = ForgeDirection.ordinal()，供 3D 渲染器绘制内陷接口扩展连接管） */
	public byte connectedSidesMask = 0;

	/** 轮询游标 */
	private int rrCursor = 0;

	/** 性能优化：错峰相位、缓存坦克视图、复用目标列表、强制同步标志 */
	private int tickPhase = -1;
	private boolean tanksDirty = true;
	private boolean forceSync = true;
	private FluidTank[] cachedRecvTanks = FluidTank.EMPTY_ARRAY;
	private FluidTank[] cachedSendTanks = FluidTank.EMPTY_ARRAY;
	private final List<ReceiverSlot> tmpTargets = new ArrayList<ReceiverSlot>(16);
	private PortDef[] cachedPorts;

	public TileEntityXenFluidTransceiver() {
		super(MAX_CHANNELS);
		for(int i = 0; i < MAX_CHANNELS; i++) {
			this.tanks[i] = new FluidTank(Fluids.NONE, TANK_CAPACITY);
			this.channelMode[i] = (i == 0) ? MODE_SEND : (i == 1 ? MODE_RECV : MODE_OFF);
			this.channelPressure[i] = 0;
			this.channelRate[i] = DEFAULT_RATE;
		}
	}

	@Override
	public String getName() {
		return "container.xen_fluid_transceiver";
	}

	public int getActiveChannelCount() {
		return this.overclocked ? MAX_CHANNELS : STEADY_CHANNELS;
	}

	public int getMaxAllowedRate() {
		return this.overclocked ? OVERCLOCK_MAX_RATE : STEADY_MAX_RATE;
	}

	public boolean isChannelActive(int ch) {
		return ch >= 0 && ch < getActiveChannelCount();
	}

	public boolean hasActiveSendChannel() {
		int n = getActiveChannelCount();
		for(int i = 0; i < n; i++) {
			if(channelMode[i] == MODE_SEND) return true;
		}
		return false;
	}

	public boolean hasActiveRecvChannel() {
		int n = getActiveChannelCount();
		for(int i = 0; i < n; i++) {
			if(channelMode[i] == MODE_RECV) return true;
		}
		return false;
	}

	@Override
	public boolean isItemValidForSlot(int slot, ItemStack stack) {
		return slot >= 0 && slot < MAX_CHANNELS && stack != null && stack.getItem() instanceof IItemFluidIdentifier;
	}

	@Override
	public int[] getAccessibleSlotsFromSide(int side) {
		return new int[0];
	}

	@Override
	public void setInventorySlotContents(int slot, ItemStack stack) {
		super.setInventorySlotContents(slot, stack);
		if(worldObj != null && !worldObj.isRemote) {
			this.forceSync = true;
		}
	}

	@Override
	public void invalidate() {
		super.invalidate();
		if(this.registeredFreq >= 0) {
			XenTransceiverNetwork.unregisterFluid(this, this.registeredFreq);
			this.registeredFreq = -1;
		}
	}

	@Override
	public void onChunkUnload() {
		super.onChunkUnload();
		if(this.registeredFreq >= 0) {
			XenTransceiverNetwork.unregisterFluid(this, this.registeredFreq);
			this.registeredFreq = -1;
		}
	}

	/** 立即检测 6 面邻接方块，刷新内陷接口的管道视觉连接掩码。 */
	public void updateConnectedMask() {
		if(worldObj == null) return;
		byte mask = 0;
		for(int i = 0; i < ForgeDirection.VALID_DIRECTIONS.length; i++) {
			ForgeDirection dir = ForgeDirection.VALID_DIRECTIONS[i];
			int nx = xCoord + dir.offsetX;
			int ny = yCoord + dir.offsetY;
			int nz = zCoord + dir.offsetZ;
			Block b = worldObj.getBlock(nx, ny, nz);
			TileEntity te = worldObj.getTileEntity(nx, ny, nz);
			boolean conn = (te instanceof TileEntityPipeBaseNT)
					|| (te instanceof TileEntityPipelineBase)
					|| (b instanceof IBlockFluidDuct)
					|| (b instanceof IFluidConnectorBlockMK2)
					|| (te instanceof IFluidConnectorMK2);
			if(conn) {
				mask |= (1 << dir.ordinal());
			}
		}
		if(this.connectedSidesMask != mask) {
			this.connectedSidesMask = mask;
			this.forceSync = true;
		}
	}

	private int getPhase() {
		if(this.tickPhase < 0) {
			this.tickPhase = Math.abs((xCoord * 73856093) ^ (yCoord * 19349663) ^ (zCoord * 83492791));
		}
		return this.tickPhase;
	}

	private void rebuildTankCachesIfNeeded() {
		if(!this.tanksDirty) return;
		this.tanksDirty = false;

		int active = getActiveChannelCount();
		int recvCount = 0;
		int sendCount = 0;
		for(int i = 0; i < active; i++) {
			if(channelMode[i] == MODE_SEND) {
				// 防止同机“同种流体既发又收”通过外接管道自循环
				FluidType t = tanks[i].getTankType();
				if(t == Fluids.NONE || !hasActiveRecvFluid(t)) {
					recvCount++;
				}
			} else if(channelMode[i] == MODE_RECV) {
				sendCount++;
			}
		}
		FluidTank[] r = recvCount == 0 ? FluidTank.EMPTY_ARRAY : new FluidTank[recvCount];
		FluidTank[] s = sendCount == 0 ? FluidTank.EMPTY_ARRAY : new FluidTank[sendCount];
		int ri = 0, si = 0;
		for(int i = 0; i < active; i++) {
			if(channelMode[i] == MODE_SEND) {
				FluidType t = tanks[i].getTankType();
				if(t == Fluids.NONE || !hasActiveRecvFluid(t)) {
					r[ri++] = tanks[i];
				}
			} else if(channelMode[i] == MODE_RECV) {
				s[si++] = tanks[i];
			}
		}
		this.cachedRecvTanks = r;
		this.cachedSendTanks = s;
	}

	@Override
	public void updateEntity() {
		if(worldObj.isRemote) return;

		long time = worldObj.getTotalWorldTime();
		int phase = getPhase();

		if(this.registeredFreq != this.frequency) {
			if(this.registeredFreq >= 0) {
				XenTransceiverNetwork.unregisterFluid(this, this.registeredFreq);
			}
			XenTransceiverNetwork.registerFluid(this, this.frequency);
			this.registeredFreq = this.frequency;
			this.forceSync = true;
		}

		// 1. 应用流体识别码与压强设置
		applyIdentifiersAndPressure();

		// 2. 六面 HBM 管道拉取与推送
		rebuildTankCachesIfNeeded();
		this.setupAllPorts(getPorts());
		this.updatePortPIFIFO();

		// 3. 每 tick 执行跨维度网络传输，按精确 mB/t 速率平滑收发
		boolean activeMoved = false;
		if(hasFluidReadyToSend()) {
			int movedInTick = performNetworkTransfer();
			this.throughputWindow += movedInTick;
			activeMoved = movedInTick > 0;
		}

		// 4. 每 20 tick（错峰）刷新 1 秒实测吞吐量、连接掩码、环境辐射与同频节点统计
		if(((time + phase) % 20L) == 0L) {
			this.lastThroughput = this.throughputWindow;
			this.throughputWindow = 0;
			updateConnectedMask();
			updateRadiation();
			updatePeerStats();
		}

		// 5. 网络同步节流：有操作/连接变化立即发包，传输中每 2 tick，纯待机每 20 tick
		boolean sendPacket = this.forceSync
				|| (activeMoved && ((time + phase) % 2L == 0L))
				|| (((time + phase) % 20L) == 0L);
		if(sendPacket) {
			this.forceSync = false;
			networkPackNT(64);
		}
	}

	private boolean hasFluidReadyToSend() {
		int active = getActiveChannelCount();
		for(int i = 0; i < active; i++) {
			if(channelMode[i] == MODE_SEND && tanks[i].getFill() > 0 && tanks[i].getTankType() != Fluids.NONE) {
				return true;
			}
		}
		return false;
	}

	private boolean hasActiveSendFluid(FluidType type) {
		if(type == null || type == Fluids.NONE) return false;
		int active = getActiveChannelCount();
		for(int i = 0; i < active; i++) {
			if(channelMode[i] == MODE_SEND && tanks[i].getTankType() == type) return true;
		}
		return false;
	}

	private boolean hasActiveRecvFluid(FluidType type) {
		if(type == null || type == Fluids.NONE) return false;
		int active = getActiveChannelCount();
		for(int i = 0; i < active; i++) {
			if(channelMode[i] == MODE_RECV && tanks[i].getTankType() == type) return true;
		}
		return false;
	}

	private void applyIdentifiersAndPressure() {
		for(int i = 0; i < MAX_CHANNELS; i++) {
			ItemStack idStack = slots[i];
			if(idStack != null && idStack.getItem() instanceof IItemFluidIdentifier) {
				FluidType wanted = ((IItemFluidIdentifier) idStack.getItem()).getType(worldObj, xCoord, yCoord, zCoord, idStack);
				if(wanted != null && tanks[i].getTankType() != wanted) {
					tanks[i].setTankType(wanted);
					this.tanksDirty = true;
					this.destroyAllPorts();
					this.markDirty();
					this.forceSync = true;
				}
			}

			int p = Math.max(0, Math.min(5, channelPressure[i]));
			channelPressure[i] = p;
			if(tanks[i].getPressure() != p) {
				tanks[i].withPressure(p);
				this.forceSync = true;
			}
		}
	}

	private int performNetworkTransfer() {
		List<TileEntityXenFluidTransceiver> peers = XenTransceiverNetwork.getFluidPeers(this.frequency);
		if(peers.size() <= 1) return 0;

		int activeCount = getActiveChannelCount();
		int maxRateAllowed = getMaxAllowedRate();
		int totalMoved = 0;

		for(int ch = 0; ch < activeCount; ch++) {
			if(channelMode[ch] != MODE_SEND) continue;
			FluidTank src = tanks[ch];
			FluidType type = src.getTankType();
			if(type == null || type == Fluids.NONE || src.getFill() <= 0) continue;

			int sendPressure = src.getPressure();
			int effRatePerSec = Math.min(channelRate[ch], maxRateAllowed);
			int sliceBudget = Math.max(1, (effRatePerSec * TRANSFER_INTERVAL) / 20);
			int budget = Math.min(src.getFill(), sliceBudget);
			if(budget <= 0) continue;

			tmpTargets.clear();
			for(int pi = 0, pn = peers.size(); pi < pn; pi++) {
				TileEntityXenFluidTransceiver peer = peers.get(pi);
				if(peer == this || peer.hasActiveSendFluid(type)) continue;
				int peerActive = peer.getActiveChannelCount();
				int peerMaxRate = peer.getMaxAllowedRate();
				for(int rch = 0; rch < peerActive; rch++) {
					if(peer.channelMode[rch] != MODE_RECV) continue;
					FluidTank dst = peer.tanks[rch];
					if(dst.getPressure() != sendPressure) continue;
					if(dst.getTankType() != type) continue;
					int space = dst.getMaxFill() - dst.getFill();
					if(space <= 0) continue;
					int rRatePerSec = Math.min(peer.channelRate[rch], peerMaxRate);
					int rSlice = Math.max(1, (rRatePerSec * TRANSFER_INTERVAL) / 20);
					tmpTargets.add(new ReceiverSlot(peer, dst, Math.min(space, rSlice)));
				}
			}

			if(tmpTargets.isEmpty()) continue;

			int movedForChannel = 0;
			int count = tmpTargets.size();
			if(distMode == DIST_ROUND_ROBIN) {
				int remaining = budget;
				int start = Math.abs(rrCursor++) % count;
				int share = Math.max(1, remaining / count);
				for(int k = 0; k < count && remaining > 0; k++) {
					ReceiverSlot rs = tmpTargets.get((start + k) % count);
					int send = Math.min(remaining, Math.min(share, rs.cap));
					if(send > 0) {
						rs.tank.setFill(rs.tank.getFill() + send);
						rs.cap -= send;
						remaining -= send;
						movedForChannel += send;
						rs.peer.markDirty();
					}
				}
				for(int k = 0; k < count && remaining > 0; k++) {
					ReceiverSlot rs = tmpTargets.get((start + k) % count);
					int send = Math.min(remaining, rs.cap);
					if(send > 0) {
						rs.tank.setFill(rs.tank.getFill() + send);
						rs.cap -= send;
						remaining -= send;
						movedForChannel += send;
						rs.peer.markDirty();
					}
				}
			} else {
				int remaining = budget;
				for(int k = 0; k < count && remaining > 0; k++) {
					ReceiverSlot rs = tmpTargets.get(k);
					int send = Math.min(remaining, rs.cap);
					if(send > 0) {
						rs.tank.setFill(rs.tank.getFill() + send);
						remaining -= send;
						movedForChannel += send;
						rs.peer.markDirty();
					}
				}
			}

			if(movedForChannel > 0) {
				src.setFill(src.getFill() - movedForChannel);
				totalMoved += movedForChannel;
				this.markDirty();
			}
		}

		return totalMoved;
	}

	/** 超频辐射逻辑：仅移除不稳定后纯累加环境辐射，内部存量越多散发越快。 */
	private void updateRadiation() {
		if(!this.overclocked) {
			this.lastRadPerTick = 0.0F;
			return;
		}

		long totalStored = 0L;
		int active = getActiveChannelCount();
		for(int i = 0; i < active; i++) {
			totalStored += tanks[i].getFill();
		}
		float fillRatio = Math.min(1.0F, totalStored / (float) (MAX_CHANNELS * TANK_CAPACITY));
		// 基础 0.25 RAD/t，满罐 6 路 384,000 mB 时达 20.0 RAD/t（每 20 tick 累加 400 RAD）
		float rad = 0.25F + fillRatio * 19.75F;
		this.lastRadPerTick = rad;
		ChunkRadiationManager.proxy.incrementRad(worldObj, xCoord, yCoord, zCoord, rad * 20.0F);
	}

	private void updatePeerStats() {
		List<TileEntityXenFluidTransceiver> peers = XenTransceiverNetwork.getFluidPeers(this.frequency);
		int s = 0, r = 0;
		boolean cross = false;
		int myDim = worldObj.provider.dimensionId;
		for(int i = 0, n = peers.size(); i < n; i++) {
			TileEntityXenFluidTransceiver peer = peers.get(i);
			if(peer.hasActiveSendChannel()) s++;
			if(peer.hasActiveRecvChannel()) r++;
			if(peer != this && peer.getWorldObj() != null && peer.getWorldObj().provider.dimensionId != myDim) {
				cross = true;
			}
		}
		this.peerSenders = s;
		this.peerReceivers = r;
		this.hasCrossDimPeer = cross;
	}

	private PortDef[] getPorts() {
		if(cachedPorts == null) {
			cachedPorts = new PortDef[] {
					PortDef.make(xCoord, yCoord, zCoord, ForgeDirection.VALID_DIRECTIONS)
			};
		}
		return cachedPorts;
	}

	/* ========================== IFluidStandardTransceiverMK2 ========================== */

	@Override
	public FluidTank[] getAllTanks() {
		return this.tanks;
	}

	@Override
	public FluidTank[] getReceivingTanks() {
		rebuildTankCachesIfNeeded();
		return this.cachedRecvTanks;
	}

	@Override
	public FluidTank[] getSendingTanks() {
		rebuildTankCachesIfNeeded();
		return this.cachedSendTanks;
	}

	@Override
	public boolean canConnect(FluidType type, ForgeDirection dir) {
		return dir != ForgeDirection.UNKNOWN;
	}

	/* ========================== IControlReceiver ========================== */

	@Override
	public boolean hasPermission(EntityPlayer player) {
		return this.isUseableByPlayer(player);
	}

	@Override
	public void receiveControl(EntityPlayer player, NBTTagCompound data) {
		boolean portsChanged = false;
		if(data.hasKey("freq")) {
			this.frequency = Math.max(0, Math.min(9999, data.getInteger("freq")));
		}
		if(data.hasKey("freqDelta")) {
			int next = this.frequency + data.getInteger("freqDelta");
			if(next < 0) next = 0;
			if(next > 9999) next = 9999;
			this.frequency = next;
		}
		if(data.hasKey("toggleOverclock")) {
			this.overclocked = !this.overclocked;
			portsChanged = true;
		}
		if(data.hasKey("toggleDist")) {
			this.distMode = (this.distMode == DIST_ROUND_ROBIN) ? DIST_PRIORITY : DIST_ROUND_ROBIN;
		}
		if(data.hasKey("chMode")) {
			int ch = data.getInteger("chMode");
			if(ch >= 0 && ch < MAX_CHANNELS) {
				this.channelMode[ch] = (this.channelMode[ch] + 1) % 3;
				portsChanged = true;
			}
		}
		if(data.hasKey("chPress")) {
			int ch = data.getInteger("chPress");
			int delta = data.hasKey("delta") ? data.getInteger("delta") : 1;
			if(ch >= 0 && ch < MAX_CHANNELS) {
				int next = this.channelPressure[ch] + delta;
				if(next < 0) next = 5;
				if(next > 5) next = 0;
				this.channelPressure[ch] = next;
				this.tanks[ch].withPressure(next);
			}
		}
		if(data.hasKey("chRate")) {
			int ch = data.getInteger("chRate");
			int val = data.getInteger("rateVal");
			if(ch >= 0 && ch < MAX_CHANNELS) {
				this.channelRate[ch] = Math.max(1000, Math.min(OVERCLOCK_MAX_RATE, val));
			}
		}
		if(data.hasKey("chRateCycle")) {
			int ch = data.getInteger("chRateCycle");
			if(ch >= 0 && ch < MAX_CHANNELS) {
				int[] steps = this.overclocked
						? new int[] { 1000, 4000, 8000, 16000, 32000, 64000 }
						: new int[] { 1000, 4000, 8000, 16000 };
				int cur = Math.min(this.channelRate[ch], getMaxAllowedRate());
				int next = steps[0];
				for(int s : steps) {
					if(s > cur) {
						next = s;
						break;
					}
				}
				this.channelRate[ch] = next;
			}
		}
		if(portsChanged) {
			this.tanksDirty = true;
			if(worldObj != null && !worldObj.isRemote) {
				this.destroyAllPorts();
			}
		}
		this.forceSync = true;
		this.markDirty();
	}

	/* ========================== NBT & 网络同步 ========================== */

	@Override
	public void readFromNBT(NBTTagCompound nbt) {
		super.readFromNBT(nbt);
		this.frequency = Math.max(0, Math.min(9999, nbt.getInteger("frequency")));
		this.overclocked = nbt.getBoolean("overclocked");
		this.distMode = nbt.getInteger("distMode") == DIST_PRIORITY ? DIST_PRIORITY : DIST_ROUND_ROBIN;

		for(int i = 0; i < MAX_CHANNELS; i++) {
			FluidTank tank = this.tanks[i];
			tank.readFromNBT(nbt, "t" + i);
			int fill = tank.getFill();
			String typeName = nbt.getString(TYPE_KEY + i);
			if(typeName != null && !typeName.isEmpty()) {
				tank.setTankType(Fluids.fromName(typeName));
			}
			tank.setFill(fill);
			this.channelMode[i] = nbt.hasKey("cm" + i) ? nbt.getInteger("cm" + i) : (i == 0 ? MODE_SEND : (i == 1 ? MODE_RECV : MODE_OFF));
			this.channelPressure[i] = Math.max(0, Math.min(5, nbt.getInteger("cp" + i)));
			tank.withPressure(this.channelPressure[i]);
			this.channelRate[i] = nbt.hasKey("cr" + i) ? Math.max(1000, Math.min(OVERCLOCK_MAX_RATE, nbt.getInteger("cr" + i))) : DEFAULT_RATE;
		}
		this.tanksDirty = true;
	}

	@Override
	public void writeToNBT(NBTTagCompound nbt) {
		super.writeToNBT(nbt);
		nbt.setInteger("frequency", this.frequency);
		nbt.setBoolean("overclocked", this.overclocked);
		nbt.setInteger("distMode", this.distMode);

		for(int i = 0; i < MAX_CHANNELS; i++) {
			this.tanks[i].writeToNBT(nbt, "t" + i);
			nbt.setString(TYPE_KEY + i, this.tanks[i].getTankType().getName());
			nbt.setInteger("cm" + i, this.channelMode[i]);
			nbt.setInteger("cp" + i, this.channelPressure[i]);
			nbt.setInteger("cr" + i, this.channelRate[i]);
		}
	}

	@Override
	public void serialize(ByteBuf buf) {
		super.serialize(buf);
		buf.writeInt(this.frequency);
		buf.writeBoolean(this.overclocked);
		buf.writeByte(this.distMode);
		buf.writeByte(this.connectedSidesMask);
		buf.writeInt(this.lastThroughput);
		buf.writeFloat(this.lastRadPerTick);
		buf.writeShort(this.peerSenders);
		buf.writeShort(this.peerReceivers);
		buf.writeBoolean(this.hasCrossDimPeer);

		for(int i = 0; i < MAX_CHANNELS; i++) {
			this.tanks[i].serialize(buf);
			buf.writeByte(this.channelMode[i]);
			buf.writeByte(this.channelPressure[i]);
			buf.writeInt(this.channelRate[i]);
		}
	}

	@Override
	public void deserialize(ByteBuf buf) {
		super.deserialize(buf);
		this.frequency = buf.readInt();
		this.overclocked = buf.readBoolean();
		this.distMode = buf.readByte();
		this.connectedSidesMask = buf.readByte();
		this.lastThroughput = buf.readInt();
		this.lastRadPerTick = buf.readFloat();
		this.peerSenders = buf.readShort();
		this.peerReceivers = buf.readShort();
		this.hasCrossDimPeer = buf.readBoolean();

		for(int i = 0; i < MAX_CHANNELS; i++) {
			this.tanks[i].deserialize(buf);
			this.channelMode[i] = buf.readByte();
			this.channelPressure[i] = buf.readByte();
			this.channelRate[i] = buf.readInt();
		}
	}

	private static final class ReceiverSlot {
		final TileEntityXenFluidTransceiver peer;
		final FluidTank tank;
		int cap;

		ReceiverSlot(TileEntityXenFluidTransceiver peer, FluidTank tank, int cap) {
			this.peer = peer;
			this.tank = tank;
			this.cap = cap;
		}
	}
}
