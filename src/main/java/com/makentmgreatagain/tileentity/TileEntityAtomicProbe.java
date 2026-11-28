package com.makentmgreatagain.tileentity;

import com.hbm.interfaces.IControlReceiver;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.ISidedInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.Vec3;
import net.minecraftforge.common.util.ForgeDirection;

import java.util.ArrayList;
import java.util.List;

/**
 * 投送探头 TileEntity：
 * <ul>
 *   <li>无内部缓存，直接检测并读写宿主容器/机器接口。</li>
 *   <li>支持自定义名称，支持 GUI 连接管理页一键勾选/取消连接。</li>
 *   <li>仅保留距离与贴面角度限制（允许激光穿透方块，无阻隔检测、无音效）。</li>
 *   <li>单次最大发送上限支持自定义（默认 1 组 = 64，最大 4 组 = 256）。</li>
 * </ul>
 */
public class TileEntityAtomicProbe extends TileEntity implements IControlReceiver {

	public static final double DEFAULT_MAX_RANGE = 32.0D;
	public static final int FIRE_DURATION = 8;
	public static final int FILTER_SLOTS = 9;
	public static final int MAX_TARGETS = 16;
	public static final int DEFAULT_MAX_SEND = 64;
	public static final int HARD_MAX_SEND = 256; // 最大 4 组

	public static final byte STATE_IDLE = 0;
	public static final byte STATE_ALIGNING = 1;
	public static final byte STATE_FIRING = 2;
	public static final byte STATE_OUT_OF_RANGE = 4;
	public static final byte STATE_BLIND_SPOT = 5;
	public static final byte STATE_TARGET_FULL = 6;
	public static final byte STATE_UNBOUND = 7;

	public static final int[] POLL_PRESETS = { 1, 10, 20, 40, 80 };

	// 自定义名称
	public String probeName = "";

	// 模式与配置
	public boolean isReceiver = false;
	public int pollInterval = 20;
	/** 发送端定量上限：0 = 有货就发；>0 时源容器必须至少凑齐该数量才发货（非过滤定量模式下）。 */
	public int sendThreshold = 0;
	/** 发送端单次最大发送量：默认 64（1 组），最大 256（4 组）。 */
	public int maxSendPerPulse = DEFAULT_MAX_SEND;
	/** 接收端库存上限：0 = 无限制；>0 时目标容器已有同类物品 >= 上限则拒收。 */
	public int stockLimit = 0;
	public boolean whitelist = true;
	public boolean batchMode = false;
	public boolean roundRobin = false;

	// 8 格幽灵过滤样本
	public final ItemStack[] filters = new ItemStack[FILTER_SLOTS];

	// 绑定的接收端列表
	public final List<int[]> targets = new ArrayList<int[]>();
	public int rrIndex = 0;

	// 当前锁定的目标接收端 + 中间经过的中继节点路径（同步给客户端用于云台对准与多段激光渲染）
	public boolean hasActiveTarget = false;
	public int activeTargetX, activeTargetY, activeTargetZ;
	public final List<int[]> activeRouteRelays = new ArrayList<int[]>();
	private int activeTargetListIdx = -1;

	// 路径缓存随中继拓扑和探头朝向失效（包含不可达结果）。
	private int cachedNetVersion = -1;
	private ForgeDirection cachedRouteFacing;
	private final java.util.Map<String, DeliveryRelayNetwork.RouteResult> routeCache =
			new java.util.HashMap<String, DeliveryRelayNetwork.RouteResult>();

	// 待发批次
	private ItemStack pendingSample = null;
	private int pendingCount = 0;
	private boolean pendingExact = false;

	// 运行态
	public byte state = STATE_UNBOUND;
	public int fireTicks = 0;
	public int alignTicks = 0;
	public int lockHoldTicks = 0;
	/** 连续传输光束保持计时：>0 时激光持续常亮不断续，无货可发或目标满时立即归零熄灭。 */
	public int beamHoldTicks = 0;
	private int pollTimer = 0;
	private int backoffTicks = 0;

	// 接收端当前锁定的发送端（多源并发时切入 omniReceiveMode 全向接收模式）
	public boolean hasActiveSender = false;
	public boolean omniReceiveMode = false;
	public int activeSenderX, activeSenderY, activeSenderZ;
	private int activeSenderTimeout = 0;

	// 客户端云台平滑角度
	public float yaw = 0F, prevYaw = 0F;
	public float pitch = 0F, prevPitch = 0F;
	public boolean clientAligned = false;

	public double getMaxRange() {
		return DEFAULT_MAX_RANGE;
	}

	public String getDisplayLabel() {
		if(probeName != null && !probeName.trim().isEmpty()) {
			return probeName.trim();
		}
		return "(" + xCoord + "," + yCoord + "," + zCoord + ")";
	}

	public ForgeDirection getFacing() {
		int meta = this.worldObj != null ? this.getBlockMetadata() : 1;
		return ForgeDirection.getOrientation(meta % 6);
	}

	public ForgeDirection getAttachDir() {
		return getFacing().getOpposite();
	}

	public Vec3 getLensWorldPos() {
		ForgeDirection f = getFacing();
		double pivotHeight = 0.375D;
		return Vec3.createVectorHelper(
				xCoord + 0.5D - f.offsetX * (0.5D - pivotHeight),
				yCoord + 0.5D - f.offsetY * (0.5D - pivotHeight),
				zCoord + 0.5D - f.offsetZ * (0.5D - pivotHeight));
	}

	/** 激光落点：全向接收模式时上移至镜头正上方全向汇聚球中心 (局部 Y=0.78)，避免水平激光穿入侧面外壳。 */
	public Vec3 getBeamEndpointWorldPos() {
		if(isReceiver && omniReceiveMode) {
			ForgeDirection f = getFacing();
			double orbHeight = 0.78D;
			return Vec3.createVectorHelper(
					xCoord + 0.5D + f.offsetX * (orbHeight - 0.5D),
					yCoord + 0.5D + f.offsetY * (orbHeight - 0.5D),
					zCoord + 0.5D + f.offsetZ * (orbHeight - 0.5D));
		}
		return getLensWorldPos();
	}

	@Override
	public void updateEntity() {
		if(worldObj == null) return;

		if(worldObj.isRemote) {
			updateClientGimbal();
			return;
		}

		if(isReceiver) {
			tickReceiver();
		} else {
			tickSender();
		}
	}

	private static class ActiveSenderEntry {
		int x, y, z;
		long expireTick;

		ActiveSenderEntry(int x, int y, int z, long expireTick) {
			this.x = x;
			this.y = y;
			this.z = z;
			this.expireTick = expireTick;
		}
	}

	/** 接收端最近活跃的来源列表（用于判定单源追踪 vs 多源并发全向接收） */
	private final List<ActiveSenderEntry> recentSenders = new ArrayList<ActiveSenderEntry>();

	private void tickReceiver() {
		if(worldObj != null && !recentSenders.isEmpty()) {
			long now = worldObj.getTotalWorldTime();
			java.util.Iterator<ActiveSenderEntry> it = recentSenders.iterator();
			while(it.hasNext()) {
				if(it.next().expireTick < now) {
					it.remove();
				}
			}
			updateReceiverAimMode();
		} else if(hasActiveSender || omniReceiveMode) {
			hasActiveSender = false;
			omniReceiveMode = false;
			syncState();
		}
		if(state != STATE_IDLE) {
			setState(STATE_IDLE);
		}
	}

	/**
	 * 方案一 + 方案A：
	 * - 仅 1 个活跃来源时，接收端云台精准对准该来源；
	 * - >= 2 个不同来源并发投送时，切回全向接收姿态（垂直朝外并在镜头正上方升起全向汇聚球），避免多对一左右乱窜与侧面穿模。
	 */
	private void updateReceiverAimMode() {
		int activeCount = recentSenders.size();
		if(activeCount == 1) {
			ActiveSenderEntry only = recentSenders.get(0);
			boolean changed = !hasActiveSender || omniReceiveMode
					|| activeSenderX != only.x || activeSenderY != only.y || activeSenderZ != only.z;
			hasActiveSender = true;
			omniReceiveMode = false;
			activeSenderX = only.x;
			activeSenderY = only.y;
			activeSenderZ = only.z;
			if(changed) syncState();
		} else if(activeCount >= 2) {
			boolean changed = hasActiveSender || !omniReceiveMode;
			hasActiveSender = false;
			omniReceiveMode = true;
			if(changed) syncState();
		} else {
			if(hasActiveSender || omniReceiveMode) {
				hasActiveSender = false;
				omniReceiveMode = false;
				syncState();
			}
		}
	}

	private void tickSender() {
		if(targets.isEmpty()) {
			clearActivePlan();
			setState(STATE_UNBOUND);
			return;
		}

		if(lockHoldTicks > 0 && state != STATE_ALIGNING && state != STATE_FIRING) {
			lockHoldTicks--;
			if(lockHoldTicks == 0) {
				hasActiveTarget = false;
				beamHoldTicks = 0;
				syncState();
			}
		}
		if(beamHoldTicks > 0 && state != STATE_FIRING) {
			beamHoldTicks--;
			if(beamHoldTicks == 0) {
				syncState();
			}
		}

		// 正在初次对准锁定（支持直连或经中继节点多跳）
		if(state == STATE_ALIGNING) {
			TileEntityAtomicProbe receiver = getLoadedReceiver(activeTargetX, activeTargetY, activeTargetZ);
			if(receiver == null || pendingSample == null || pendingCount <= 0) {
				clearActivePlan();
				setState(STATE_IDLE);
				return;
			}
			int hold = Math.max(50, pollInterval + 30);
			lockHoldTicks = hold;
			notifyReceiverHop(receiver, activeRouteRelays, hold);
			alignTicks--;
			if(alignTicks <= 0) {
				DeliveryRelayNetwork.RouteResult route = getCachedRoute(receiver);
				if(route == null) {
					clearActivePlan();
					setState(getRouteFailureState(receiver));
					pollTimer = (pollInterval == 1) ? 0 : 20;
					return;
				}
				int room = receiver.computeAcceptRoom(pendingSample, pendingCount);
				if(room <= 0 || (pendingExact && room < pendingCount)) {
					clearActivePlan();
					setState(STATE_TARGET_FULL);
					pollTimer = (pollInterval == 1) ? 0 : pollInterval;
					return;
				}
				setState(STATE_FIRING);
				executeDirectTransfer(receiver);
			}
			return;
		}

		// 1t 极速档：零退避、零延迟，每 tick 直接扫描并传输；其他档位沿用性能友好节拍
		if(pollInterval > 1 && pollTimer > 0) {
			pollTimer--;
			return;
		}

		int count = targets.size();
		if(rrIndex >= count) rrIndex = 0;
		int startIdx = roundRobin ? rrIndex : 0;

		byte failReason = STATE_IDLE;
		boolean foundAnyWork = false;
		boolean hasReachableTarget = false;

		for(int step = 0; step < count; step++) {
			int idx = (startIdx + step) % count;
			int[] pos = targets.get(idx);
			TileEntityAtomicProbe receiver = getLoadedReceiver(pos[0], pos[1], pos[2]);
			if(receiver == null) {
				if(failReason == STATE_IDLE) failReason = STATE_UNBOUND;
				continue;
			}

			DeliveryRelayNetwork.RouteResult route = getCachedRoute(receiver);
			if(route == null) {
				if(failReason == STATE_IDLE) {
					failReason = getRouteFailureState(receiver);
				}
				continue;
			}

			hasReachableTarget = true;
			TransferPlan plan = planTransferTo(receiver);
			if(plan == null) continue;

			boolean sameTargetLocked = (lockHoldTicks > 0 && hasActiveTarget
					&& activeTargetX == pos[0] && activeTargetY == pos[1] && activeTargetZ == pos[2]
					&& isSameRelayList(activeRouteRelays, route.relayCoords));
			boolean routeChanged = !hasActiveTarget
					|| activeTargetX != pos[0] || activeTargetY != pos[1] || activeTargetZ != pos[2]
					|| !isSameRelayList(activeRouteRelays, route.relayCoords);

			hasActiveTarget = true;
			activeTargetX = pos[0];
			activeTargetY = pos[1];
			activeTargetZ = pos[2];
			activeRouteRelays.clear();
			for(int[] rc : route.relayCoords) {
				activeRouteRelays.add(new int[] { rc[0], rc[1], rc[2] });
			}
			activeTargetListIdx = idx;
			pendingSample = plan.sample;
			pendingCount = plan.count;
			pendingExact = plan.exact;

			int hold = Math.max(50, pollInterval + 30);
			lockHoldTicks = hold;
			backoffTicks = 0;
			notifyReceiverHop(receiver, activeRouteRelays, hold);

			if(sameTargetLocked) {
				setState(STATE_FIRING);
				executeDirectTransfer(receiver);
			} else {
				alignTicks = 6;
				setState(STATE_ALIGNING);
				if(routeChanged) syncState();
			}
			foundAnyWork = true;
			break;
		}

		if(!foundAnyWork) {
			boolean hadBeam = beamHoldTicks > 0;
			beamHoldTicks = 0;
			if(pollInterval == 1) {
				// 1t 极速模式不做退避休眠
				backoffTicks = 0;
				pollTimer = 0;
			} else {
				backoffTicks = Math.min(40, backoffTicks + 10);
				pollTimer = pollInterval + backoffTicks;
			}
			// 某个目标不可达不代表整机没有可用路径。
			setState(hasReachableTarget ? STATE_TARGET_FULL : failReason);
			if(hadBeam) syncState();
		}
	}

	private byte getRouteFailureState(TileEntityAtomicProbe receiver) {
		return getDistanceTo(receiver) <= Math.min(getMaxRange(), DeliveryRelayNetwork.HOP_RANGE)
				? STATE_BLIND_SPOT : STATE_OUT_OF_RANGE;
	}

	private DeliveryRelayNetwork.RouteResult getCachedRoute(TileEntityAtomicProbe receiver) {
		int ver = DeliveryRelayNetwork.getVersion(worldObj);
		ForgeDirection facing = getFacing();
		if(ver != cachedNetVersion || facing != cachedRouteFacing) {
			routeCache.clear();
			cachedNetVersion = ver;
			cachedRouteFacing = facing;
		}
		String key = receiver.xCoord + "," + receiver.yCoord + "," + receiver.zCoord + "," + receiver.getFacing().ordinal();
		if(routeCache.containsKey(key)) {
			return routeCache.get(key);
		}
		DeliveryRelayNetwork.RouteResult res = DeliveryRelayNetwork.findRoute(this, receiver);
		routeCache.put(key, res);
		return res;
	}

	private void notifyReceiverHop(TileEntityAtomicProbe receiver, List<int[]> relays, int hold) {
		if(relays != null && !relays.isEmpty()) {
			int[] last = relays.get(relays.size() - 1);
			receiver.notifyActiveSender(last[0], last[1], last[2], hold);
		} else {
			receiver.notifyActiveSender(xCoord, yCoord, zCoord, hold);
		}
	}

	private static boolean isSameRelayList(List<int[]> a, List<int[]> b) {
		if(a.size() != b.size()) return false;
		for(int i = 0; i < a.size(); i++) {
			int[] pa = a.get(i), pb = b.get(i);
			if(pa[0] != pb[0] || pa[1] != pb[1] || pa[2] != pb[2]) return false;
		}
		return true;
	}

	private void clearActivePlan() {
		pendingSample = null;
		pendingCount = 0;
		pendingExact = false;
		activeTargetListIdx = -1;
	}

	/** 同一 tick 内直接从发送端宿主容器搬运至接收端宿主容器（支持最多 4 组 = 256 个分堆搬运，无音效，绝不吞货）。 */
	private void executeDirectTransfer(TileEntityAtomicProbe receiver) {
		boolean movedAny = false;
		if(pendingSample != null && pendingCount > 0) {
			int avail = countExtractableInHost(pendingSample);
			int room = receiver.computeAcceptRoom(pendingSample, pendingCount);
			int actual = Math.min(pendingCount, Math.min(avail, room));

			boolean ok = actual > 0 && (!pendingExact || actual >= pendingCount);
			if(ok) {
				List<ItemStack> stacks = extractMultiFromHost(pendingSample, actual);
				for(ItemStack s : stacks) {
					if(s == null || s.stackSize <= 0) continue;
					movedAny = true;
					ItemStack leftover = receiver.insertIntoHost(s);
					if(leftover != null && leftover.stackSize > 0) {
						insertIntoHost(leftover);
					}
				}
				if(movedAny && roundRobin && !targets.isEmpty() && activeTargetListIdx >= 0) {
					rrIndex = (activeTargetListIdx + 1) % targets.size();
				}
			}
		}
		beamHoldTicks = movedAny ? Math.max(16, pollInterval + 10) : 0;
		clearActivePlan();
		// 1t 极速档 pollTimer 置 0 实现严格每 1 tick 传输；其他档位保持原性能节拍
		pollTimer = (pollInterval == 1) ? 0 : pollInterval;
		if(!movedAny) {
			setState(STATE_IDLE);
		}
	}

	private TileEntityAtomicProbe getLoadedReceiver(int rx, int ry, int rz) {
		if(!worldObj.blockExists(rx, ry, rz)) return null;
		TileEntity te = worldObj.getTileEntity(rx, ry, rz);
		if(!(te instanceof TileEntityAtomicProbe)) return null;
		TileEntityAtomicProbe probe = (TileEntityAtomicProbe) te;
		return probe.isReceiver ? probe : null;
	}

	// ==================== 传输规划 ====================

	private static class TransferPlan {
		final ItemStack sample;
		final int count;
		final boolean exact;
		TransferPlan(ItemStack sample, int count, boolean exact) {
			this.sample = sample;
			this.count = count;
			this.exact = exact;
		}
	}

	private TransferPlan planTransferTo(TileEntityAtomicProbe receiver) {
		IInventory host = getHostInventory();
		if(host == null) return null;

		int pulseCap = Math.max(1, Math.min(HARD_MAX_SEND, maxSendPerPulse));

		if(batchMode) {
			for(int i = 0; i < filters.length; i++) {
				ItemStack f = filters[i];
				if(f == null || f.stackSize <= 0) continue;
				int req = Math.min(pulseCap, f.stackSize);
				if(!receiver.acceptsFilter(f)) continue;
				int avail = countExtractableInHost(f);
				if(avail < req) continue;
				int room = receiver.computeAcceptRoom(f, req);
				if(room < req) continue;
				ItemStack sample = findFirstExtractableMatching(f);
				if(sample != null) {
					return new TransferPlan(sample, req, true);
				}
			}
			return null;
		}

		int sideOnHost = getFacing().ordinal();
		TransferPlan p = planFromSingle(host, sideOnHost, receiver, pulseCap);
		if(p != null) return p;

		IInventory second = getSecondChest(host);
		if(second != null) {
			return planFromSingle(second, sideOnHost, receiver, pulseCap);
		}
		return null;
	}

	private TransferPlan planFromSingle(IInventory inv, int sideOnHost, TileEntityAtomicProbe receiver, int pulseCap) {
		int[] slots = getSlotsForSide(inv, sideOnHost);
		boolean isSided = inv instanceof ISidedInventory;

		for(int slot : slots) {
			ItemStack stack = inv.getStackInSlot(slot);
			if(stack == null || stack.stackSize <= 0) continue;
			if(isSided && !((ISidedInventory) inv).canExtractItem(slot, stack, sideOnHost)) continue;
			if(!this.acceptsFilter(stack) || !receiver.acceptsFilter(stack)) continue;

			int avail = countExtractableInHost(stack);
			if(sendThreshold > 0) {
				if(avail < sendThreshold) continue;
				int req = Math.min(pulseCap, sendThreshold);
				int room = receiver.computeAcceptRoom(stack, req);
				if(room < req) continue;
				return new TransferPlan(stack.copy(), req, true);
			} else {
				int maxTake = Math.min(pulseCap, avail);
				int room = receiver.computeAcceptRoom(stack, maxTake);
				if(room <= 0) continue;
				return new TransferPlan(stack.copy(), room, false);
			}
		}
		return null;
	}

	// ==================== 宿主容器读写 ====================

	private IInventory getHostInventory() {
		ForgeDirection attach = getAttachDir();
		TileEntity te = worldObj.getTileEntity(xCoord + attach.offsetX, yCoord + attach.offsetY, zCoord + attach.offsetZ);
		if(te instanceof TileEntityAtomicProbe) return null;
		return (te instanceof IInventory) ? (IInventory) te : null;
	}

	private IInventory getSecondChest(IInventory primary) {
		if(!(primary instanceof TileEntityChest)) return null;
		TileEntityChest c = (TileEntityChest) primary;
		c.checkForAdjacentChests();
		if(c.adjacentChestXNeg != null) return c.adjacentChestXNeg;
		if(c.adjacentChestXPos != null) return c.adjacentChestXPos;
		if(c.adjacentChestZNeg != null) return c.adjacentChestZNeg;
		if(c.adjacentChestZPos != null) return c.adjacentChestZPos;
		return null;
	}

	private static int[] getSlotsForSide(IInventory inv, int sideOnHost) {
		if(inv instanceof ISidedInventory) {
			return ((ISidedInventory) inv).getAccessibleSlotsFromSide(sideOnHost);
		}
		int[] slots = new int[inv.getSizeInventory()];
		for(int i = 0; i < slots.length; i++) slots[i] = i;
		return slots;
	}

	public boolean acceptsFilter(ItemStack stack) {
		if(stack == null) return false;
		boolean hasAny = false;
		boolean matched = false;
		for(int i = 0; i < filters.length; i++) {
			if(filters[i] != null) {
				hasAny = true;
				if(areItemsSameType(filters[i], stack)) {
					matched = true;
					break;
				}
			}
		}
		if(!hasAny) return true;
		return whitelist ? matched : !matched;
	}

	private int countExtractableInHost(ItemStack sample) {
		IInventory inv = getHostInventory();
		if(inv == null) return 0;
		int sideOnHost = getFacing().ordinal();
		int total = countExtractableSingle(inv, sideOnHost, sample);
		IInventory second = getSecondChest(inv);
		if(second != null) total += countExtractableSingle(second, sideOnHost, sample);
		return total;
	}

	private static int countExtractableSingle(IInventory inv, int sideOnHost, ItemStack sample) {
		int[] slots = getSlotsForSide(inv, sideOnHost);
		boolean isSided = inv instanceof ISidedInventory;
		int total = 0;
		for(int slot : slots) {
			ItemStack s = inv.getStackInSlot(slot);
			if(s == null || s.stackSize <= 0) continue;
			if(isSided && !((ISidedInventory) inv).canExtractItem(slot, s, sideOnHost)) continue;
			if(areItemsSameType(s, sample)) total += s.stackSize;
		}
		return total;
	}

	private ItemStack findFirstExtractableMatching(ItemStack sample) {
		IInventory inv = getHostInventory();
		if(inv == null) return null;
		int sideOnHost = getFacing().ordinal();
		ItemStack res = findFirstInSingle(inv, sideOnHost, sample);
		if(res != null) return res;
		IInventory second = getSecondChest(inv);
		return second != null ? findFirstInSingle(second, sideOnHost, sample) : null;
	}

	private static ItemStack findFirstInSingle(IInventory inv, int sideOnHost, ItemStack sample) {
		int[] slots = getSlotsForSide(inv, sideOnHost);
		boolean isSided = inv instanceof ISidedInventory;
		for(int slot : slots) {
			ItemStack s = inv.getStackInSlot(slot);
			if(s == null || s.stackSize <= 0) continue;
			if(isSided && !((ISidedInventory) inv).canExtractItem(slot, s, sideOnHost)) continue;
			if(areItemsSameType(s, sample)) return s.copy();
		}
		return null;
	}

	/** 从宿主容器提取最多 amount 个同类物品（支持 >64 跨多堆提取，最高 4 组）。 */
	private List<ItemStack> extractMultiFromHost(ItemStack sample, int amount) {
		List<ItemStack> out = new ArrayList<ItemStack>();
		IInventory inv = getHostInventory();
		if(inv == null || amount <= 0) return out;
		int sideOnHost = getFacing().ordinal();
		int rem = extractMultiSingle(inv, sideOnHost, sample, amount, out);
		if(rem > 0) {
			IInventory second = getSecondChest(inv);
			if(second != null) extractMultiSingle(second, sideOnHost, sample, rem, out);
		}
		return out;
	}

	private static int extractMultiSingle(IInventory inv, int sideOnHost, ItemStack sample, int need, List<ItemStack> out) {
		if(need <= 0) return 0;
		int[] slots = getSlotsForSide(inv, sideOnHost);
		boolean isSided = inv instanceof ISidedInventory;
		boolean dirty = false;
		for(int slot : slots) {
			if(need <= 0) break;
			ItemStack s = inv.getStackInSlot(slot);
			if(s == null || s.stackSize <= 0) continue;
			if(isSided && !((ISidedInventory) inv).canExtractItem(slot, s, sideOnHost)) continue;
			if(!areItemsSameType(s, sample)) continue;
			int take = Math.min(need, s.stackSize);
			ItemStack part = inv.decrStackSize(slot, take);
			if(part != null && part.stackSize > 0) {
				out.add(part);
				need -= part.stackSize;
				dirty = true;
			}
		}
		if(dirty) inv.markDirty();
		return need;
	}

	public int computeAcceptRoom(ItemStack sample, int requested) {
		if(sample == null || requested <= 0) return 0;
		IInventory host = getHostInventory();
		if(host == null) return 0;

		int cap = requested;
		if(stockLimit > 0) {
			int existing = countInHost(sample);
			cap = Math.min(cap, Math.max(0, stockLimit - existing));
			if(cap <= 0) return 0;
		}

		int sideOnHost = getFacing().ordinal();
		int space = computeInsertSpaceSingle(host, sideOnHost, sample, cap);
		if(space < cap) {
			IInventory second = getSecondChest(host);
			if(second != null) {
				space += computeInsertSpaceSingle(second, sideOnHost, sample, cap - space);
			}
		}
		return Math.min(cap, space);
	}

	private static int computeInsertSpaceSingle(IInventory inv, int sideOnHost, ItemStack sample, int maxNeeded) {
		int[] slots = getSlotsForSide(inv, sideOnHost);
		boolean isSided = inv instanceof ISidedInventory;
		int invLimit = inv.getInventoryStackLimit();
		int space = 0;

		for(int slot : slots) {
			if(space >= maxNeeded) break;
			if(!inv.isItemValidForSlot(slot, sample)) continue;
			if(isSided && !((ISidedInventory) inv).canInsertItem(slot, sample, sideOnHost)) continue;
			ItemStack existing = inv.getStackInSlot(slot);
			if(existing == null) {
				space += Math.min(sample.getMaxStackSize(), invLimit);
			} else if(areItemStacksMergeable(existing, sample)) {
				int max = Math.min(existing.getMaxStackSize(), invLimit);
				if(existing.stackSize < max) space += (max - existing.stackSize);
			}
		}
		return Math.min(maxNeeded, space);
	}

	private int countInHost(ItemStack sample) {
		IInventory inv = getHostInventory();
		if(inv == null) return 0;
		int total = countInSingleInventory(inv, sample);
		IInventory second = getSecondChest(inv);
		if(second != null) total += countInSingleInventory(second, sample);
		return total;
	}

	private static int countInSingleInventory(IInventory inv, ItemStack sample) {
		int count = 0;
		int size = inv.getSizeInventory();
		for(int i = 0; i < size; i++) {
			ItemStack s = inv.getStackInSlot(i);
			if(s != null && areItemsSameType(s, sample)) count += s.stackSize;
		}
		return count;
	}

	public ItemStack insertIntoHost(ItemStack stack) {
		IInventory host = getHostInventory();
		if(host == null || stack == null) return stack;
		int sideOnHost = getFacing().ordinal();
		ItemStack rem = insertIntoSingle(host, sideOnHost, stack.copy());
		if(rem == null || rem.stackSize <= 0) return null;
		IInventory second = getSecondChest(host);
		if(second != null) rem = insertIntoSingle(second, sideOnHost, rem);
		return (rem == null || rem.stackSize <= 0) ? null : rem;
	}

	private static ItemStack insertIntoSingle(IInventory inv, int sideOnHost, ItemStack stack) {
		int[] slots = getSlotsForSide(inv, sideOnHost);
		boolean isSided = inv instanceof ISidedInventory;
		int invLimit = inv.getInventoryStackLimit();
		boolean dirty = false;

		for(int slot : slots) {
			if(stack.stackSize <= 0) break;
			if(!inv.isItemValidForSlot(slot, stack)) continue;
			if(isSided && !((ISidedInventory) inv).canInsertItem(slot, stack, sideOnHost)) continue;
			ItemStack existing = inv.getStackInSlot(slot);
			if(existing != null && areItemStacksMergeable(existing, stack)) {
				int max = Math.min(existing.getMaxStackSize(), invLimit);
				int move = Math.min(stack.stackSize, max - existing.stackSize);
				if(move > 0) {
					existing.stackSize += move;
					stack.stackSize -= move;
					dirty = true;
				}
			}
		}

		for(int slot : slots) {
			if(stack.stackSize <= 0) break;
			if(!inv.isItemValidForSlot(slot, stack)) continue;
			if(isSided && !((ISidedInventory) inv).canInsertItem(slot, stack, sideOnHost)) continue;
			if(inv.getStackInSlot(slot) == null) {
				int max = Math.min(stack.getMaxStackSize(), invLimit);
				int move = Math.min(stack.stackSize, max);
				inv.setInventorySlotContents(slot, stack.splitStack(move));
				dirty = true;
			}
		}

		if(dirty) inv.markDirty();
		return stack.stackSize > 0 ? stack : null;
	}

	private static boolean areItemsSameType(ItemStack a, ItemStack b) {
		if(a == null || b == null) return false;
		if(a.getItem() != b.getItem()) return false;
		if(a.getHasSubtypes() && a.getItemDamage() != b.getItemDamage()) return false;
		return true;
	}

	private static boolean areItemStacksMergeable(ItemStack a, ItemStack b) {
		if(a == null || b == null) return false;
		return a.getItem() == b.getItem()
				&& a.getItemDamage() == b.getItemDamage()
				&& ItemStack.areItemStackTagsEqual(a, b);
	}

	// ==================== 角度与距离 ====================

	public void notifyActiveSender(int sx, int sy, int sz, int timeout) {
		if(worldObj == null || worldObj.isRemote) return;
		long now = worldObj.getTotalWorldTime();
		long expire = now + Math.max(40, timeout);

		boolean found = false;
		java.util.Iterator<ActiveSenderEntry> it = recentSenders.iterator();
		while(it.hasNext()) {
			ActiveSenderEntry e = it.next();
			if(e.x == sx && e.y == sy && e.z == sz) {
				e.expireTick = expire;
				found = true;
			} else if(e.expireTick < now) {
				it.remove();
			}
		}
		if(!found) {
			recentSenders.add(new ActiveSenderEntry(sx, sy, sz, expire));
		}
		updateReceiverAimMode();
	}

	public double getDistanceTo(TileEntityAtomicProbe other) {
		double dx = other.xCoord - this.xCoord;
		double dy = other.yCoord - this.yCoord;
		double dz = other.zCoord - this.zCoord;
		return Math.sqrt(dx * dx + dy * dy + dz * dz);
	}

	public boolean isInFieldOfView(TileEntityAtomicProbe other) {
		return isPointInFieldOfView(other.getLensWorldPos());
	}

	public boolean isPointInFieldOfView(Vec3 targetPos) {
		Vec3 a = this.getLensWorldPos();
		Vec3 dir = Vec3.createVectorHelper(targetPos.xCoord - a.xCoord, targetPos.yCoord - a.yCoord, targetPos.zCoord - a.zCoord).normalize();
		ForgeDirection f = getFacing();
		double dot = dir.xCoord * f.offsetX + dir.yCoord * f.offsetY + dir.zCoord * f.offsetZ;
		return dot >= -0.20D;
	}

	// ==================== 客户端云台平滑追踪 ====================

	private void updateClientGimbal() {
		prevYaw = yaw;
		prevPitch = pitch;

		if(lockHoldTicks > 0 && state != STATE_ALIGNING && state != STATE_FIRING) {
			lockHoldTicks--;
		}
		if(beamHoldTicks > 0 && state != STATE_FIRING) {
			beamHoldTicks--;
		}

		float targetYaw = 0F;
		float targetPitch = 0F;
		boolean hasAim = false;
		double tx = 0, ty = 0, tz = 0;

		if(!isReceiver && hasActiveTarget && (state == STATE_ALIGNING || state == STATE_FIRING || lockHoldTicks > 0)) {
			hasAim = true;
			// 有中继路径时对准第一跳节点核心，否则直连对准接收端
			if(!activeRouteRelays.isEmpty()) {
				int[] first = activeRouteRelays.get(0);
				TileEntity te = worldObj.getTileEntity(first[0], first[1], first[2]);
				if(te instanceof TileEntityDeliveryRelay) {
					Vec3 p = ((TileEntityDeliveryRelay) te).getCoreWorldPos();
					tx = p.xCoord; ty = p.yCoord; tz = p.zCoord;
				} else {
					tx = first[0] + 0.5D; ty = first[1] + 0.5D; tz = first[2] + 0.5D;
				}
			} else {
				TileEntity te = worldObj.getTileEntity(activeTargetX, activeTargetY, activeTargetZ);
				if(te instanceof TileEntityAtomicProbe) {
					Vec3 p = ((TileEntityAtomicProbe) te).getBeamEndpointWorldPos();
					tx = p.xCoord; ty = p.yCoord; tz = p.zCoord;
				} else {
					tx = activeTargetX + 0.5D; ty = activeTargetY + 0.5D; tz = activeTargetZ + 0.5D;
				}
			}
		} else if(isReceiver && hasActiveSender) {
			hasAim = true;
			TileEntity te = worldObj.getTileEntity(activeSenderX, activeSenderY, activeSenderZ);
			if(te instanceof TileEntityDeliveryRelay) {
				Vec3 p = ((TileEntityDeliveryRelay) te).getCoreWorldPos();
				tx = p.xCoord; ty = p.yCoord; tz = p.zCoord;
			} else if(te instanceof TileEntityAtomicProbe) {
				Vec3 p = ((TileEntityAtomicProbe) te).getLensWorldPos();
				tx = p.xCoord; ty = p.yCoord; tz = p.zCoord;
			} else {
				tx = activeSenderX + 0.5D; ty = activeSenderY + 0.5D; tz = activeSenderZ + 0.5D;
			}
		}

		if(hasAim) {
			Vec3 src = getLensWorldPos();
			double wx = tx - src.xCoord;
			double wy = ty - src.yCoord;
			double wz = tz - src.zCoord;
			double len = Math.sqrt(wx * wx + wy * wy + wz * wz);
			if(len > 1e-4) {
				wx /= len; wy /= len; wz /= len;
				double[] local = worldDirToLocal(wx, wy, wz, getFacing());
				double lx = local[0], ly = local[1], lz = local[2];
				targetYaw = (float) Math.toDegrees(Math.atan2(lx, lz));
				double horiz = Math.sqrt(lx * lx + lz * lz);
				targetPitch = (float) Math.toDegrees(Math.atan2(horiz, Math.max(-0.2D, ly)));
				if(targetPitch > 95F) targetPitch = 95F;
			}
		}

		float dy = wrapDegrees(targetYaw - yaw);
		float dp = targetPitch - pitch;
		// 提高云台伺服转速：对准/投送期间 45°/t（最多 4 tick 转完 180°），回正 24°/t
		float maxStep = (state == STATE_ALIGNING || state == STATE_FIRING || hasAim) ? 45F : 24F;
		yaw = wrapDegrees(yaw + clamp(dy, -maxStep, maxStep));
		pitch += clamp(dp, -maxStep, maxStep);
		clientAligned = hasAim
				&& Math.abs(wrapDegrees(targetYaw - yaw)) <= 4.0F
				&& Math.abs(targetPitch - pitch) <= 4.0F;
	}

	public static double[] worldDirToLocal(double wx, double wy, double wz, ForgeDirection facing) {
		switch(facing) {
			case UP:    return new double[] {  wx,  wy,  wz };
			case DOWN:  return new double[] {  wx, -wy, -wz };
			case SOUTH: return new double[] {  wx,  wz, -wy };
			case NORTH: return new double[] { -wx, -wz, -wy };
			case EAST:  return new double[] { -wz,  wx, -wy };
			case WEST:  return new double[] {  wz, -wx, -wy };
			default:    return new double[] {  wx,  wy,  wz };
		}
	}

	private static float wrapDegrees(float deg) {
		deg %= 360F;
		if(deg >= 180F) deg -= 360F;
		if(deg < -180F) deg += 360F;
		return deg;
	}

	private static float clamp(float v, float min, float max) {
		return Math.max(min, Math.min(max, v));
	}

	// ==================== 多目标绑定与 GUI 控制 ====================

	public boolean isBoundTo(int tx, int ty, int tz) {
		for(int[] p : targets) {
			if(p[0] == tx && p[1] == ty && p[2] == tz) return true;
		}
		return false;
	}

	public boolean addTarget(int tx, int ty, int tz) {
		if(isBoundTo(tx, ty, tz) || targets.size() >= MAX_TARGETS) return false;
		this.isReceiver = false;
		targets.add(new int[] { tx, ty, tz });
		this.pollTimer = 0;
		this.backoffTicks = 0;
		setState(STATE_IDLE);
		markDirty();
		syncState();
		return true;
	}

	public boolean removeTarget(int tx, int ty, int tz) {
		for(int i = 0; i < targets.size(); i++) {
			int[] p = targets.get(i);
			if(p[0] == tx && p[1] == ty && p[2] == tz) {
				targets.remove(i);
				if(hasActiveTarget && activeTargetX == tx && activeTargetY == ty && activeTargetZ == tz) {
					hasActiveTarget = false;
					lockHoldTicks = 0;
					clearActivePlan();
				}
				if(targets.isEmpty() && !isReceiver) setState(STATE_UNBOUND);
				markDirty();
				syncState();
				return true;
			}
		}
		return false;
	}

	public void clearTargets() {
		targets.clear();
		hasActiveTarget = false;
		lockHoldTicks = 0;
		rrIndex = 0;
		clearActivePlan();
		if(!isReceiver) setState(STATE_UNBOUND);
		markDirty();
		syncState();
	}

	private void setState(byte newState) {
		if(this.state != newState) {
			this.state = newState;
			syncState();
		}
	}

	public void syncState() {
		if(worldObj != null && !worldObj.isRemote) {
			worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
		}
	}

	@Override
	public boolean hasPermission(EntityPlayer player) {
		return player.getDistanceSq(xCoord + 0.5D, yCoord + 0.5D, zCoord + 0.5D) <= 64.0D;
	}

	@Override
	public void receiveControl(EntityPlayer player, NBTTagCompound data) {
		if(data.hasKey("setName")) {
			String s = data.getString("setName");
			if(s.length() > 24) s = s.substring(0, 24);
			this.probeName = s.trim();
		}
		if(data.hasKey("toggleMode")) {
			this.isReceiver = !this.isReceiver;
			this.hasActiveSender = false;
			this.hasActiveTarget = false;
			this.lockHoldTicks = 0;
			this.fireTicks = 0;
			clearActivePlan();
			this.state = isReceiver ? STATE_IDLE : (targets.isEmpty() ? STATE_UNBOUND : STATE_IDLE);
		}
		if(data.hasKey("toggleWhitelist")) {
			this.whitelist = !this.whitelist;
		}
		if(data.hasKey("toggleBatchMode")) {
			this.batchMode = !this.batchMode;
		}
		if(data.hasKey("toggleRoundRobin")) {
			this.roundRobin = !this.roundRobin;
		}
		if(data.hasKey("cyclePoll")) {
			int next = POLL_PRESETS[0];
			for(int i = 0; i < POLL_PRESETS.length; i++) {
				if(POLL_PRESETS[i] == pollInterval) {
					next = POLL_PRESETS[(i + 1) % POLL_PRESETS.length];
					break;
				}
			}
			this.pollInterval = next;
			this.pollTimer = Math.min(this.pollTimer, this.pollInterval);
		}
		if(data.hasKey("setLimitValue")) {
			int val = Math.max(0, Math.min(65536, data.getInteger("setLimitValue")));
			if(this.isReceiver) {
				this.stockLimit = val;
			} else {
				this.sendThreshold = val;
			}
		}
		if(data.hasKey("setMaxSend")) {
			this.maxSendPerPulse = Math.max(1, Math.min(HARD_MAX_SEND, data.getInteger("setMaxSend")));
		}
		if(data.hasKey("unbind")) {
			clearTargets();
			return;
		}
		// GUI 连接管理页直接勾选/取消连接某个指定探头
		if(data.hasKey("toggleLinkX")) {
			int lx = data.getInteger("toggleLinkX");
			int ly = data.getInteger("toggleLinkY");
			int lz = data.getInteger("toggleLinkZ");
			if(worldObj.blockExists(lx, ly, lz)) {
				TileEntity otherTe = worldObj.getTileEntity(lx, ly, lz);
				if(otherTe instanceof TileEntityAtomicProbe && otherTe != this) {
					TileEntityAtomicProbe other = (TileEntityAtomicProbe) otherTe;
					if(!this.isReceiver && other.isReceiver) {
						if(this.isBoundTo(lx, ly, lz)) this.removeTarget(lx, ly, lz);
						else this.addTarget(lx, ly, lz);
					} else if(this.isReceiver && !other.isReceiver) {
						if(other.isBoundTo(xCoord, yCoord, zCoord)) other.removeTarget(xCoord, yCoord, zCoord);
						else other.addTarget(xCoord, yCoord, zCoord);
					}
				}
			}
		}
		if(data.hasKey("setFilterSlot")) {
			int idx = data.getInteger("setFilterSlot");
			if(idx >= 0 && idx < filters.length) {
				if(data.hasKey("filterCount")) {
					if(filters[idx] != null) {
						int c = Math.max(1, Math.min(HARD_MAX_SEND, data.getInteger("filterCount")));
						filters[idx].stackSize = c;
					}
				} else if(data.hasKey("filterItem")) {
					ItemStack sample = ItemStack.loadItemStackFromNBT(data.getCompoundTag("filterItem"));
					if(sample != null) {
						sample.stackSize = Math.max(1, Math.min(HARD_MAX_SEND, sample.stackSize));
					}
					filters[idx] = sample;
				} else {
					filters[idx] = null;
				}
			}
		}
		markDirty();
		syncState();
	}

	// ==================== 渲染包围盒 & NBT ====================

	@Override
	public AxisAlignedBB getRenderBoundingBox() {
		if(!isReceiver && hasActiveTarget && (state == STATE_FIRING || beamHoldTicks > 0)) {
			int minX = Math.min(xCoord, activeTargetX);
			int minY = Math.min(yCoord, activeTargetY);
			int minZ = Math.min(zCoord, activeTargetZ);
			int maxX = Math.max(xCoord, activeTargetX);
			int maxY = Math.max(yCoord, activeTargetY);
			int maxZ = Math.max(zCoord, activeTargetZ);
			for(int[] r : activeRouteRelays) {
				if(r[0] < minX) minX = r[0];
				if(r[1] < minY) minY = r[1];
				if(r[2] < minZ) minZ = r[2];
				if(r[0] > maxX) maxX = r[0];
				if(r[1] > maxY) maxY = r[1];
				if(r[2] > maxZ) maxZ = r[2];
			}
			return AxisAlignedBB.getBoundingBox(minX - 1, minY - 1, minZ - 1, maxX + 2, maxY + 2, maxZ + 2);
		}
		return AxisAlignedBB.getBoundingBox(xCoord, yCoord, zCoord, xCoord + 1, yCoord + 1, zCoord + 1);
	}

	@Override
	public boolean shouldRenderInPass(int pass) {
		return pass == 0 || pass == 1;
	}

	@Override
	public double getMaxRenderDistanceSquared() {
		return 160.0D * 160.0D;
	}

	@Override
	public void writeToNBT(NBTTagCompound nbt) {
		super.writeToNBT(nbt);
		writeStateNBT(nbt);
	}

	@Override
	public void readFromNBT(NBTTagCompound nbt) {
		super.readFromNBT(nbt);
		readStateNBT(nbt);
	}

	private void writeStateNBT(NBTTagCompound nbt) {
		nbt.setString("probeName", probeName == null ? "" : probeName);
		nbt.setBoolean("isReceiver", isReceiver);
		nbt.setInteger("pollInterval", pollInterval);
		nbt.setInteger("sendThreshold", sendThreshold);
		nbt.setInteger("maxSendPerPulse", maxSendPerPulse);
		nbt.setInteger("stockLimit", stockLimit);
		nbt.setBoolean("whitelist", whitelist);
		nbt.setBoolean("batchMode", batchMode);
		nbt.setBoolean("roundRobin", roundRobin);
		nbt.setInteger("rrIndex", rrIndex);

		nbt.setBoolean("hasActiveTarget", hasActiveTarget);
		nbt.setInteger("activeTargetX", activeTargetX);
		nbt.setInteger("activeTargetY", activeTargetY);
		nbt.setInteger("activeTargetZ", activeTargetZ);

		nbt.setByte("state", state);
		nbt.setInteger("fireTicks", fireTicks);
		nbt.setInteger("lockHoldTicks", lockHoldTicks);
		nbt.setInteger("beamHoldTicks", beamHoldTicks);
		nbt.setBoolean("hasActiveSender", hasActiveSender);
		nbt.setBoolean("omniReceiveMode", omniReceiveMode);
		nbt.setInteger("activeSenderX", activeSenderX);
		nbt.setInteger("activeSenderY", activeSenderY);
		nbt.setInteger("activeSenderZ", activeSenderZ);

		NBTTagList tList = new NBTTagList();
		for(int[] p : targets) {
			NBTTagCompound tTag = new NBTTagCompound();
			tTag.setInteger("x", p[0]);
			tTag.setInteger("y", p[1]);
			tTag.setInteger("z", p[2]);
			tList.appendTag(tTag);
		}
		nbt.setTag("targets", tList);

		NBTTagList rList = new NBTTagList();
		for(int[] r : activeRouteRelays) {
			NBTTagCompound rTag = new NBTTagCompound();
			rTag.setInteger("x", r[0]);
			rTag.setInteger("y", r[1]);
			rTag.setInteger("z", r[2]);
			rList.appendTag(rTag);
		}
		nbt.setTag("activeRelays", rList);

		NBTTagList fList = new NBTTagList();
		for(int i = 0; i < filters.length; i++) {
			if(filters[i] != null) {
				NBTTagCompound fTag = new NBTTagCompound();
				fTag.setByte("slot", (byte) i);
				filters[i].writeToNBT(fTag);
				fList.appendTag(fTag);
			}
		}
		nbt.setTag("filters", fList);
	}

	private void readStateNBT(NBTTagCompound nbt) {
		probeName = nbt.getString("probeName");
		isReceiver = nbt.getBoolean("isReceiver");
		pollInterval = nbt.hasKey("pollInterval") ? Math.max(1, nbt.getInteger("pollInterval")) : 20;
		sendThreshold = Math.max(0, nbt.getInteger("sendThreshold"));
		maxSendPerPulse = nbt.hasKey("maxSendPerPulse")
				? Math.max(1, Math.min(HARD_MAX_SEND, nbt.getInteger("maxSendPerPulse")))
				: DEFAULT_MAX_SEND;
		stockLimit = Math.max(0, nbt.getInteger("stockLimit"));
		whitelist = !nbt.hasKey("whitelist") || nbt.getBoolean("whitelist");
		batchMode = nbt.getBoolean("batchMode");
		roundRobin = nbt.getBoolean("roundRobin");
		rrIndex = Math.max(0, nbt.getInteger("rrIndex"));

		hasActiveTarget = nbt.getBoolean("hasActiveTarget");
		activeTargetX = nbt.getInteger("activeTargetX");
		activeTargetY = nbt.getInteger("activeTargetY");
		activeTargetZ = nbt.getInteger("activeTargetZ");

		state = nbt.getByte("state");
		fireTicks = nbt.getInteger("fireTicks");
		lockHoldTicks = nbt.getInteger("lockHoldTicks");
		beamHoldTicks = nbt.getInteger("beamHoldTicks");
		hasActiveSender = nbt.getBoolean("hasActiveSender");
		omniReceiveMode = nbt.getBoolean("omniReceiveMode");
		activeSenderX = nbt.getInteger("activeSenderX");
		activeSenderY = nbt.getInteger("activeSenderY");
		activeSenderZ = nbt.getInteger("activeSenderZ");

		targets.clear();
		NBTTagList tList = nbt.getTagList("targets", 10);
		for(int i = 0; i < tList.tagCount(); i++) {
			NBTTagCompound tTag = tList.getCompoundTagAt(i);
			targets.add(new int[] { tTag.getInteger("x"), tTag.getInteger("y"), tTag.getInteger("z") });
		}

		activeRouteRelays.clear();
		NBTTagList rList = nbt.getTagList("activeRelays", 10);
		for(int i = 0; i < rList.tagCount(); i++) {
			NBTTagCompound rTag = rList.getCompoundTagAt(i);
			activeRouteRelays.add(new int[] { rTag.getInteger("x"), rTag.getInteger("y"), rTag.getInteger("z") });
		}

		for(int i = 0; i < filters.length; i++) filters[i] = null;
		NBTTagList fList = nbt.getTagList("filters", 10);
		for(int i = 0; i < fList.tagCount(); i++) {
			NBTTagCompound fTag = fList.getCompoundTagAt(i);
			int slot = fTag.getByte("slot") & 0xFF;
			if(slot < filters.length) {
				filters[slot] = ItemStack.loadItemStackFromNBT(fTag);
			}
		}
	}

	@Override
	public Packet getDescriptionPacket() {
		NBTTagCompound nbt = new NBTTagCompound();
		writeStateNBT(nbt);
		return new S35PacketUpdateTileEntity(xCoord, yCoord, zCoord, 0, nbt);
	}

	@Override
	public void onDataPacket(NetworkManager net, S35PacketUpdateTileEntity pkt) {
		readStateNBT(pkt.func_148857_g());
	}
}
