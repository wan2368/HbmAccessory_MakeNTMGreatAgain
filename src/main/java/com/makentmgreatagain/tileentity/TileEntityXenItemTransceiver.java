package com.makentmgreatagain.tileentity;

import java.util.List;

import com.hbm.handler.radiation.ChunkRadiationManager;
import com.hbm.interfaces.IControlReceiver;
import com.hbm.tileentity.TileEntityMachineBase;

import cpw.mods.fml.common.network.ByteBufUtils;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.oredict.OreDictionary;

/**
 * 物品传送器 TileEntity（无需供电、跨维度、带过滤、定量批次发送与强制占位）。
 *
 * <ul>
 *   <li>槽位 0~8：发送缓冲（外部自动化可输入）；</li>
 *   <li>槽位 9~17：接收缓冲（外部自动化可抽出）；</li>
 *   <li>稳态模式：1 组/秒（每 20 tick 1 组），零辐射；</li>
 *   <li>超频模式：4 组/秒（每 5 tick 1 组），移除不稳定，仅累加散发环境辐射（机内物品越多散发越快）；</li>
 *   <li>强制占位：自动按首次放入物品位置绑定槽位，同类物品满堆叠后拒绝溢出到其余空槽，防止单种物品堵塞产线；</li>
 *   <li>定量模式：过滤槽支持堆叠数量，集齐全部过滤槽对应物品及数量后一并发给对方（支持轮询调度）。</li>
 * </ul>
 */
public class TileEntityXenItemTransceiver extends TileEntityMachineBase implements IControlReceiver {

	public static final int SEND_SLOTS = 9;
	public static final int RECV_SLOTS = 9;
	public static final int TOTAL_SLOTS = SEND_SLOTS + RECV_SLOTS;
	public static final int FILTER_SLOTS = 6;

	public static final int IO_OFF = 0;
	public static final int IO_SEND = 1;
	public static final int IO_RECV = 2;
	public static final int IO_BOTH = 3;

	public static final int FILTER_OFF = 0;
	public static final int FILTER_WHITELIST = 1;
	public static final int FILTER_BLACKLIST = 2;

	public static final int DIST_ROUND_ROBIN = 0;
	public static final int DIST_PRIORITY = 1;

	private static final int STEADY_INTERVAL = 20;   // 1 组/秒
	private static final int OVERCLOCK_INTERVAL = 5; // 4 组/秒

	private static final int[] ALL_ACCESSIBLE;
	static {
		ALL_ACCESSIBLE = new int[TOTAL_ACCESSIBLE_LEN()];
		for(int i = 0; i < TOTAL_SLOTS; i++) ALL_ACCESSIBLE[i] = i;
	}

	private static int TOTAL_ACCESSIBLE_LEN() {
		return TOTAL_SLOTS;
	}

	/** 共振频率（0 ~ 9999 → 000.0 ~ 999.9 MHz） */
	public int frequency = 1000;
	private int registeredFreq = -1;

	/** 工作模式：0=关，1=仅发，2=仅收，3=双向 */
	public int ioMode = IO_BOTH;
	/** 超频模式 */
	public boolean overclocked = false;
	/** 分发策略：0=轮询，1=优先 */
	public int distMode = DIST_ROUND_ROBIN;

	/** 虚拟过滤槽（定量模式开启时可堆叠数量） */
	public final ItemStack[] filterSlots = new ItemStack[FILTER_SLOTS];
	/** 过滤模式：0=关，1=白名单，2=黑名单 */
	public int filterMode = FILTER_OFF;
	/** 过滤匹配元数据 / 矿辞 / NBT */
	public boolean matchMeta = true;
	public boolean matchOreDict = false;
	public boolean matchNbt = false;

	/** 定量模式（集齐过滤槽全部物品及数量后一并发出） */
	public boolean batchMode = false;

	/** 强制占位（发送端 / 接收端独立开关与槽位绑定模板） */
	public boolean sendSlotLock = false;
	public boolean recvSlotLock = false;
	public final ItemStack[] sendPlaceholders = new ItemStack[SEND_SLOTS];
	public final ItemStack[] recvPlaceholders = new ItemStack[RECV_SLOTS];

	/** 定额补货上限（0=不限制，>0=接收缓冲总物品数上限） */
	public int stockLimit = 0;

	/** 上次传输数量与辐射显示 */
	public int lastItemsMoved = 0;
	public float lastRadPerTick = 0.0F;
	public int peerSenders = 0;
	public int peerReceivers = 0;
	public boolean hasCrossDimPeer = false;

	private int rrCursor = 0;
	private int transferCooldown = 0;
	private int tickPhase = -1;
	private boolean forceSync = true;

	/** 复用工作数组，避免 tick 堆分配 */
	private final int[] simCounts = new int[RECV_SLOTS];
	private final ItemStack[] simTypes = new ItemStack[RECV_SLOTS];
	private final ItemStack[] batchTemplates = new ItemStack[FILTER_SLOTS];
	private final int[] batchRequired = new int[FILTER_SLOTS];

	public TileEntityXenItemTransceiver() {
		super(TOTAL_SLOTS);
	}

	@Override
	public String getName() {
		return "container.xen_item_transceiver";
	}

	public boolean canSendNow() {
		return ioMode == IO_SEND || ioMode == IO_BOTH;
	}

	public boolean canRecvNow() {
		return ioMode == IO_RECV || ioMode == IO_BOTH;
	}

	private int getPhase() {
		if(this.tickPhase < 0) {
			this.tickPhase = Math.abs((xCoord * 73856093) ^ (yCoord * 19349663) ^ (zCoord * 83492791));
		}
		return this.tickPhase;
	}

	@Override
	public int[] getAccessibleSlotsFromSide(int side) {
		return ALL_ACCESSIBLE;
	}

	@Override
	public boolean canInsertItem(int slot, ItemStack stack, int side) {
		return slot >= 0 && slot < SEND_SLOTS && isItemValidForSlot(slot, stack);
	}

	@Override
	public boolean canExtractItem(int slot, ItemStack stack, int side) {
		return slot >= SEND_SLOTS && slot < TOTAL_SLOTS;
	}

	@Override
	public void setInventorySlotContents(int slot, ItemStack stack) {
		super.setInventorySlotContents(slot, stack);
		if(worldObj != null && !worldObj.isRemote && stack != null) {
			if(this.sendSlotLock && slot >= 0 && slot < SEND_SLOTS && this.sendPlaceholders[slot] == null) {
				ItemStack ph = stack.copy();
				ph.stackSize = 1;
				this.sendPlaceholders[slot] = ph;
			} else if(this.recvSlotLock && slot >= SEND_SLOTS && slot < TOTAL_SLOTS) {
				int idx = slot - SEND_SLOTS;
				if(this.recvPlaceholders[idx] == null) {
					ItemStack ph = stack.copy();
					ph.stackSize = 1;
					this.recvPlaceholders[idx] = ph;
				}
			}
			this.forceSync = true;
		}
	}

	@Override
	public boolean isItemValidForSlot(int slot, ItemStack stack) {
		if(slot < 0 || slot >= TOTAL_SLOTS || stack == null) return false;
		if(slot < SEND_SLOTS) {
			if(!passesFilter(stack)) return false;
			if(this.sendSlotLock) {
				return canPlaceIntoLockedSlot(slot, stack, this.sendPlaceholders);
			}
			return true;
		}
		if(this.recvSlotLock) {
			return canPlaceIntoLockedSlot(slot - SEND_SLOTS, stack, this.recvPlaceholders);
		}
		return true;
	}

	/**
	 * 强制占位校验：
	 * <ul>
	 *   <li>若该槽已有占位模板，则只允许同种（含 NBT）物品进入；</li>
	 *   <li>若该槽未占位，仅当该物品在其余槽位尚未拥有占位时才允许占用此新槽，防止同种物品溢出占满整箱。</li>
	 * </ul>
	 */
	private static boolean canPlaceIntoLockedSlot(int localIdx, ItemStack stack, ItemStack[] placeholders) {
		ItemStack existing = placeholders[localIdx];
		if(existing != null) {
			return sameItemExact(existing, stack);
		}
		for(int i = 0; i < placeholders.length; i++) {
			if(placeholders[i] != null && sameItemExact(placeholders[i], stack)) {
				return false;
			}
		}
		return true;
	}

	private static boolean sameItemExact(ItemStack a, ItemStack b) {
		return a != null && b != null && a.isItemEqual(b) && ItemStack.areItemStackTagsEqual(a, b);
	}

	public boolean passesFilter(ItemStack stack) {
		if(stack == null) return false;
		if(filterMode == FILTER_OFF) return true;

		boolean matched = false;
		for(int i = 0; i < FILTER_SLOTS; i++) {
			ItemStack f = filterSlots[i];
			if(f == null) continue;
			if(matchesTemplate(stack, f)) {
				matched = true;
				break;
			}
		}
		return (filterMode == FILTER_WHITELIST) ? matched : !matched;
	}

	private boolean matchesTemplate(ItemStack a, ItemStack b) {
		if(a == null || b == null) return false;
		if(a.getItem() == b.getItem()) {
			boolean metaOk = !matchMeta || a.getItemDamage() == b.getItemDamage();
			boolean nbtOk = !matchNbt || ItemStack.areItemStackTagsEqual(a, b);
			if(metaOk && nbtOk) {
				return true;
			}
		}
		if(matchOreDict) {
			int[] idsA = OreDictionary.getOreIDs(a);
			int[] idsB = OreDictionary.getOreIDs(b);
			for(int idA : idsA) {
				for(int idB : idsB) {
					if(idA == idB) return true;
				}
			}
		}
		return false;
	}

	public int getReceiveBufferCount() {
		int total = 0;
		for(int i = SEND_SLOTS; i < TOTAL_SLOTS; i++) {
			if(slots[i] != null) total += slots[i].stackSize;
		}
		return total;
	}

	private boolean hasAnySendItem() {
		for(int i = 0; i < SEND_SLOTS; i++) {
			if(slots[i] != null && slots[i].stackSize > 0) return true;
		}
		return false;
	}

	private void snapshotSendPlaceholders() {
		for(int i = 0; i < SEND_SLOTS; i++) {
			if(slots[i] != null) {
				ItemStack ph = slots[i].copy();
				ph.stackSize = 1;
				sendPlaceholders[i] = ph;
			} else {
				sendPlaceholders[i] = null;
			}
		}
	}

	private void snapshotRecvPlaceholders() {
		for(int i = 0; i < RECV_SLOTS; i++) {
			if(slots[SEND_SLOTS + i] != null) {
				ItemStack ph = slots[SEND_SLOTS + i].copy();
				ph.stackSize = 1;
				recvPlaceholders[i] = ph;
			} else {
				recvPlaceholders[i] = null;
			}
		}
	}

	@Override
	public void invalidate() {
		super.invalidate();
		if(this.registeredFreq >= 0) {
			XenTransceiverNetwork.unregisterItem(this, this.registeredFreq);
			this.registeredFreq = -1;
		}
	}

	@Override
	public void onChunkUnload() {
		super.onChunkUnload();
		if(this.registeredFreq >= 0) {
			XenTransceiverNetwork.unregisterItem(this, this.registeredFreq);
			this.registeredFreq = -1;
		}
	}

	@Override
	public void updateEntity() {
		if(worldObj.isRemote) return;

		long time = worldObj.getTotalWorldTime();
		int phase = getPhase();

		if(this.registeredFreq != this.frequency) {
			if(this.registeredFreq >= 0) {
				XenTransceiverNetwork.unregisterItem(this, this.registeredFreq);
			}
			XenTransceiverNetwork.registerItem(this, this.frequency);
			this.registeredFreq = this.frequency;
			this.forceSync = true;
		}

		// 发送槽若开启强制占位，确保玩家或自动化放入的物品立即锁定对应槽位
		if(this.sendSlotLock) {
			for(int i = 0; i < SEND_SLOTS; i++) {
				if(this.sendPlaceholders[i] == null && slots[i] != null) {
					ItemStack ph = slots[i].copy();
					ph.stackSize = 1;
					this.sendPlaceholders[i] = ph;
					this.forceSync = true;
				}
			}
		}

		if(this.transferCooldown > 0) {
			this.transferCooldown--;
		}

		boolean didMove = false;
		if(canSendNow() && this.transferCooldown <= 0 && hasAnySendItem()) {
			int interval = this.overclocked ? OVERCLOCK_INTERVAL : STEADY_INTERVAL;
			if(this.batchMode && hasAnyFilterItem()) {
				int moved = performBatchTransfer();
				if(moved > 0) {
					this.lastItemsMoved = moved;
					// 按基础 1 组/s（超频 4 组/s）计算批次冷却
					int minTickPer64 = this.overclocked ? 5 : 20;
					this.transferCooldown = Math.max(interval, (moved * minTickPer64 + 63) / 64);
					didMove = true;
				} else {
					this.lastItemsMoved = 0;
					// 未凑齐或无接收空间，稍等 5 tick 再检以节省 CPU
					this.transferCooldown = 5;
				}
			} else {
				int moved = performSingleStackTransfer();
				this.lastItemsMoved = moved;
				this.transferCooldown = interval;
				didMove = moved > 0;
			}
		} else if(!canSendNow() || !hasAnySendItem()) {
			this.lastItemsMoved = 0;
		}

		// 每 20 tick（错峰）更新辐射与节点统计
		if(((time + phase) % 20L) == 0L) {
			updateRadiation();
			updatePeerStats();
		}

		boolean sendPacket = this.forceSync
				|| (didMove && ((time + phase) % 4L == 0L))
				|| (((time + phase) % 20L) == 0L);
		if(sendPacket) {
			this.forceSync = false;
			networkPackNT(64);
		}
	}

	private boolean hasAnyFilterItem() {
		for(int i = 0; i < FILTER_SLOTS; i++) {
			if(filterSlots[i] != null) return true;
		}
		return false;
	}

	/** 常规单组传输（稳态 1 组/s，超频 4 组/s） */
	private int performSingleStackTransfer() {
		List<TileEntityXenItemTransceiver> peers = XenTransceiverNetwork.getItemPeers(this.frequency);
		int pn = peers.size();
		if(pn <= 1) return 0;

		for(int s = 0; s < SEND_SLOTS; s++) {
			ItemStack src = slots[s];
			if(src == null || src.stackSize <= 0) continue;
			if(!passesFilter(src)) continue;

			int start = (distMode == DIST_ROUND_ROBIN) ? (Math.abs(rrCursor) % pn) : 0;
			for(int k = 0; k < pn; k++) {
				int idx = (start + k) % pn;
				TileEntityXenItemTransceiver target = peers.get(idx);
				if(target == this || !target.canRecvNow()) continue;
				if(!target.passesFilter(src)) continue;

				int allowed = src.stackSize;
				if(target.stockLimit > 0) {
					int remainingStock = target.stockLimit - target.getReceiveBufferCount();
					if(remainingStock <= 0) continue;
					allowed = Math.min(allowed, remainingStock);
				}

				int inserted = target.insertIntoReceiveBuffer(src, allowed);
				if(inserted > 0) {
					src.stackSize -= inserted;
					if(src.stackSize <= 0) slots[s] = null;
					if(distMode == DIST_ROUND_ROBIN) {
						this.rrCursor = (idx + 1) % pn;
					}
					this.markDirty();
					target.markDirty();
					this.forceSync = true;
					target.forceSync = true;
					return inserted;
				}
			}
		}
		return 0;
	}

	/**
	 * 定量批次传输：
	 * 仅当发送缓冲中凑齐所有非空过滤槽指定的物品与个数时，才将整批物品原子发送给同一个满足条件的接收端；
	 * 若开启轮询，则按频率内接收端顺序依次轮流发送整批。
	 */
	private int performBatchTransfer() {
		int reqKinds = 0;
		int totalReqItems = 0;

		for(int f = 0; f < FILTER_SLOTS; f++) {
			ItemStack fs = filterSlots[f];
			if(fs == null || fs.stackSize <= 0) continue;
			int mergedIdx = -1;
			for(int k = 0; k < reqKinds; k++) {
				if(matchesTemplate(batchTemplates[k], fs)) {
					mergedIdx = k;
					break;
				}
			}
			if(mergedIdx >= 0) {
				batchRequired[mergedIdx] += fs.stackSize;
			} else {
				batchTemplates[reqKinds] = fs;
				batchRequired[reqKinds] = fs.stackSize;
				reqKinds++;
			}
			totalReqItems += fs.stackSize;
		}
		if(reqKinds == 0 || totalReqItems <= 0) return 0;

		// 1. 检查输入端（slots[0..8]）是否全部达到指定个数
		for(int k = 0; k < reqKinds; k++) {
			int found = 0;
			ItemStack tpl = batchTemplates[k];
			for(int s = 0; s < SEND_SLOTS; s++) {
				ItemStack src = slots[s];
				if(src != null && src.stackSize > 0 && matchesTemplate(src, tpl)) {
					found += src.stackSize;
					if(found >= batchRequired[k]) break;
				}
			}
			if(found < batchRequired[k]) return 0;
		}

		// 2. 查找可完整接纳本批次的接收端（轮询或优先）
		List<TileEntityXenItemTransceiver> peers = XenTransceiverNetwork.getItemPeers(this.frequency);
		int pn = peers.size();
		if(pn <= 1) return 0;

		int start = (distMode == DIST_ROUND_ROBIN) ? (Math.abs(rrCursor) % pn) : 0;
		for(int off = 0; off < pn; off++) {
			int idx = (start + off) % pn;
			TileEntityXenItemTransceiver target = peers.get(idx);
			if(target == this || !target.canRecvNow()) continue;
			if(target.stockLimit > 0 && target.getReceiveBufferCount() + totalReqItems > target.stockLimit) {
				continue;
			}
			if(!canTargetAcceptWholeBatch(target, reqKinds)) {
				continue;
			}

			// 3. 原子扣除发送端并写入接收端
			int totalSent = 0;
			for(int k = 0; k < reqKinds; k++) {
				int need = batchRequired[k];
				ItemStack tpl = batchTemplates[k];
				for(int s = 0; s < SEND_SLOTS && need > 0; s++) {
					ItemStack src = slots[s];
					if(src == null || src.stackSize <= 0 || !matchesTemplate(src, tpl)) continue;
					int take = Math.min(need, src.stackSize);
					int inserted = target.insertIntoReceiveBuffer(src, take);
					if(inserted > 0) {
						src.stackSize -= inserted;
						if(src.stackSize <= 0) slots[s] = null;
						need -= inserted;
						totalSent += inserted;
					}
				}
			}
			if(totalSent > 0) {
				if(distMode == DIST_ROUND_ROBIN) {
					this.rrCursor = (idx + 1) % pn;
				}
				this.markDirty();
				target.markDirty();
				this.forceSync = true;
				target.forceSync = true;
			}
			return totalSent;
		}
		return 0;
	}

	/** 零分配预演接收端是否能一次性装下全部待发批次 */
	private boolean canTargetAcceptWholeBatch(TileEntityXenItemTransceiver target, int reqKinds) {
		for(int i = 0; i < RECV_SLOTS; i++) {
			ItemStack st = target.slots[SEND_SLOTS + i];
			target.simTypes[i] = st != null ? st : (target.recvSlotLock ? target.recvPlaceholders[i] : null);
			target.simCounts[i] = st != null ? st.stackSize : 0;
		}

		for(int k = 0; k < reqKinds; k++) {
			int need = batchRequired[k];
			ItemStack tpl = batchTemplates[k];
			for(int s = 0; s < SEND_SLOTS && need > 0; s++) {
				ItemStack src = slots[s];
				if(src == null || src.stackSize <= 0 || !matchesTemplate(src, tpl)) continue;
				if(!target.passesFilter(src)) return false;

				int left = Math.min(need, src.stackSize);
				need -= left;

				int maxStack = Math.min(src.getMaxStackSize(), target.getInventoryStackLimit());
				// 模拟合入现有/已占位槽位
				boolean hasLockedSlot = false;
				for(int i = 0; i < RECV_SLOTS; i++) {
					ItemStack t = target.simTypes[i];
					if(t != null && sameItemExact(t, src)) {
						hasLockedSlot = true;
						int space = maxStack - target.simCounts[i];
						if(space > 0 && left > 0) {
							int add = Math.min(space, left);
							target.simCounts[i] += add;
							left -= add;
						}
					}
				}
				if(left > 0 && (!target.recvSlotLock || !hasLockedSlot)) {
					for(int i = 0; i < RECV_SLOTS && left > 0; i++) {
						if(target.simTypes[i] == null) {
							target.simTypes[i] = src;
							int add = Math.min(maxStack, left);
							target.simCounts[i] = add;
							left -= add;
							if(target.recvSlotLock) break; // 强制占位仅允许占 1 个新槽，满堆叠即止
						}
					}
				}
				if(left > 0) return false;
			}
			if(need > 0) return false;
		}
		return true;
	}

	private int insertIntoReceiveBuffer(ItemStack incoming, int maxAmount) {
		int left = Math.min(maxAmount, incoming.stackSize);
		int inserted = 0;
		int maxStack = Math.min(incoming.getMaxStackSize(), getInventoryStackLimit());

		if(this.recvSlotLock) {
			boolean hasPlaceholder = false;
			for(int i = 0; i < RECV_SLOTS; i++) {
				if(this.recvPlaceholders[i] != null && sameItemExact(this.recvPlaceholders[i], incoming)) {
					hasPlaceholder = true;
					ItemStack dst = slots[SEND_SLOTS + i];
					int curCount = dst != null ? dst.stackSize : 0;
					int space = maxStack - curCount;
					if(space > 0 && left > 0) {
						int move = Math.min(space, left);
						if(dst == null) {
							ItemStack copy = incoming.copy();
							copy.stackSize = move;
							slots[SEND_SLOTS + i] = copy;
						} else {
							dst.stackSize += move;
						}
						left -= move;
						inserted += move;
					}
				}
			}
			// 若该物品尚无占位，只占用第一个未占位且为空的槽位，不再向其他空槽溢出
			if(!hasPlaceholder && left > 0) {
				for(int i = 0; i < RECV_SLOTS; i++) {
					if(this.recvPlaceholders[i] == null && slots[SEND_SLOTS + i] == null) {
						int move = Math.min(maxStack, left);
						ItemStack copy = incoming.copy();
						copy.stackSize = move;
						slots[SEND_SLOTS + i] = copy;

						ItemStack ph = incoming.copy();
						ph.stackSize = 1;
						this.recvPlaceholders[i] = ph;

						left -= move;
						inserted += move;
						break;
					}
				}
			}
			return inserted;
		}

		// 未开启接收端强制占位：先合并已有堆叠，再填普通空槽
		for(int i = SEND_SLOTS; i < TOTAL_SLOTS && left > 0; i++) {
			ItemStack dst = slots[i];
			if(dst == null) continue;
			if(sameItemExact(dst, incoming)) {
				int space = maxStack - dst.stackSize;
				if(space > 0) {
					int move = Math.min(space, left);
					dst.stackSize += move;
					left -= move;
					inserted += move;
				}
			}
		}
		for(int i = SEND_SLOTS; i < TOTAL_SLOTS && left > 0; i++) {
			if(slots[i] != null) continue;
			int move = Math.min(maxStack, left);
			ItemStack copy = incoming.copy();
			copy.stackSize = move;
			slots[i] = copy;
			left -= move;
			inserted += move;
		}

		return inserted;
	}

	/** 超频辐射逻辑：移除不稳定，仅持续向环境累加辐射（内部物品总数越多散发越快）。 */
	private void updateRadiation() {
		if(!this.overclocked) {
			this.lastRadPerTick = 0.0F;
			return;
		}

		int totalItems = 0;
		for(int i = 0; i < TOTAL_SLOTS; i++) {
			if(slots[i] != null) totalItems += slots[i].stackSize;
		}
		float fillRatio = Math.min(1.0F, totalItems / (float) (TOTAL_SLOTS * 64));
		float rad = 0.25F + fillRatio * 14.75F;
		this.lastRadPerTick = rad;
		ChunkRadiationManager.proxy.incrementRad(worldObj, xCoord, yCoord, zCoord, rad * 20.0F);
	}

	private void updatePeerStats() {
		List<TileEntityXenItemTransceiver> peers = XenTransceiverNetwork.getItemPeers(this.frequency);
		int s = 0, r = 0;
		boolean cross = false;
		int myDim = worldObj.provider.dimensionId;
		for(int i = 0, n = peers.size(); i < n; i++) {
			TileEntityXenItemTransceiver peer = peers.get(i);
			if(peer.canSendNow()) s++;
			if(peer.canRecvNow()) r++;
			if(peer != this && peer.getWorldObj() != null && peer.getWorldObj().provider.dimensionId != myDim) {
				cross = true;
			}
		}
		this.peerSenders = s;
		this.peerReceivers = r;
		this.hasCrossDimPeer = cross;
	}

	/* ========================== IControlReceiver ========================== */

	@Override
	public boolean hasPermission(EntityPlayer player) {
		return this.isUseableByPlayer(player);
	}

	@Override
	public void receiveControl(EntityPlayer player, NBTTagCompound data) {
		if(data.hasKey("freq")) {
			this.frequency = Math.max(0, Math.min(9999, data.getInteger("freq")));
		}
		if(data.hasKey("freqDelta")) {
			int next = this.frequency + data.getInteger("freqDelta");
			this.frequency = Math.max(0, Math.min(9999, next));
		}
		if(data.hasKey("toggleOverclock")) {
			this.overclocked = !this.overclocked;
			this.transferCooldown = 0;
		}
		if(data.hasKey("cycleIo")) {
			this.ioMode = (this.ioMode + 1) % 4;
		}
		if(data.hasKey("toggleDist")) {
			this.distMode = (this.distMode == DIST_ROUND_ROBIN) ? DIST_PRIORITY : DIST_ROUND_ROBIN;
		}
		if(data.hasKey("cycleFilter")) {
			this.filterMode = (this.filterMode + 1) % 3;
		}
		if(data.hasKey("toggleMeta")) {
			this.matchMeta = !this.matchMeta;
		}
		if(data.hasKey("toggleOre")) {
			this.matchOreDict = !this.matchOreDict;
		}
		if(data.hasKey("toggleNbt")) {
			this.matchNbt = !this.matchNbt;
		}
		if(data.hasKey("toggleBatch")) {
			this.batchMode = !this.batchMode;
			if(!this.batchMode) {
				for(int i = 0; i < FILTER_SLOTS; i++) {
					if(this.filterSlots[i] != null) this.filterSlots[i].stackSize = 1;
				}
			}
		}
		if(data.hasKey("toggleSendLock")) {
			this.sendSlotLock = !this.sendSlotLock;
			if(this.sendSlotLock) snapshotSendPlaceholders();
			else for(int i = 0; i < SEND_SLOTS; i++) this.sendPlaceholders[i] = null;
		}
		if(data.hasKey("clearSendLock")) {
			if(this.sendSlotLock) snapshotSendPlaceholders();
			else for(int i = 0; i < SEND_SLOTS; i++) this.sendPlaceholders[i] = null;
		}
		if(data.hasKey("toggleRecvLock")) {
			this.recvSlotLock = !this.recvSlotLock;
			if(this.recvSlotLock) snapshotRecvPlaceholders();
			else for(int i = 0; i < RECV_SLOTS; i++) this.recvPlaceholders[i] = null;
		}
		if(data.hasKey("clearRecvLock")) {
			if(this.recvSlotLock) snapshotRecvPlaceholders();
			else for(int i = 0; i < RECV_SLOTS; i++) this.recvPlaceholders[i] = null;
		}
		if(data.hasKey("stockDelta")) {
			int next = this.stockLimit + data.getInteger("stockDelta");
			this.stockLimit = Math.max(0, Math.min(576, next));
		}
		if(data.hasKey("setFilterSlot")) {
			int idx = data.getInteger("setFilterSlot");
			if(idx >= 0 && idx < FILTER_SLOTS) {
				if(data.hasKey("filterItem")) {
					ItemStack st = ItemStack.loadItemStackFromNBT(data.getCompoundTag("filterItem"));
					if(st != null) {
						int limit = this.batchMode ? Math.max(1, st.getMaxStackSize()) : 1;
						st.stackSize = Math.max(1, Math.min(limit, st.stackSize));
					}
					this.filterSlots[idx] = st;
				} else if(data.hasKey("filterCount")) {
					if(this.filterSlots[idx] != null) {
						int c = data.getInteger("filterCount");
						if(c <= 0) {
							this.filterSlots[idx] = null;
						} else {
							int limit = this.batchMode ? Math.max(1, this.filterSlots[idx].getMaxStackSize()) : 1;
							this.filterSlots[idx].stackSize = Math.min(limit, c);
						}
					}
				} else {
					this.filterSlots[idx] = null;
				}
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
		this.ioMode = nbt.hasKey("ioMode") ? (nbt.getInteger("ioMode") % 4) : IO_BOTH;
		this.overclocked = nbt.getBoolean("overclocked");
		this.distMode = nbt.getInteger("distMode") == DIST_PRIORITY ? DIST_PRIORITY : DIST_ROUND_ROBIN;
		this.filterMode = nbt.getInteger("filterMode") % 3;
		this.matchMeta = !nbt.hasKey("matchMeta") || nbt.getBoolean("matchMeta");
		this.matchOreDict = nbt.getBoolean("matchOreDict");
		this.matchNbt = nbt.getBoolean("matchNbt");
		this.batchMode = nbt.getBoolean("batchMode");
		this.sendSlotLock = nbt.getBoolean("sendSlotLock");
		this.recvSlotLock = nbt.getBoolean("recvSlotLock");
		this.stockLimit = Math.max(0, Math.min(576, nbt.getInteger("stockLimit")));

		readItemArrayFromNbt(nbt, "filterSlots", this.filterSlots);
		readItemArrayFromNbt(nbt, "sendPlaceholders", this.sendPlaceholders);
		readItemArrayFromNbt(nbt, "recvPlaceholders", this.recvPlaceholders);
	}

	@Override
	public void writeToNBT(NBTTagCompound nbt) {
		super.writeToNBT(nbt);
		nbt.setInteger("frequency", this.frequency);
		nbt.setInteger("ioMode", this.ioMode);
		nbt.setBoolean("overclocked", this.overclocked);
		nbt.setInteger("distMode", this.distMode);
		nbt.setInteger("filterMode", this.filterMode);
		nbt.setBoolean("matchMeta", this.matchMeta);
		nbt.setBoolean("matchOreDict", this.matchOreDict);
		nbt.setBoolean("matchNbt", this.matchNbt);
		nbt.setBoolean("batchMode", this.batchMode);
		nbt.setBoolean("sendSlotLock", this.sendSlotLock);
		nbt.setBoolean("recvSlotLock", this.recvSlotLock);
		nbt.setInteger("stockLimit", this.stockLimit);

		writeItemArrayToNbt(nbt, "filterSlots", this.filterSlots);
		writeItemArrayToNbt(nbt, "sendPlaceholders", this.sendPlaceholders);
		writeItemArrayToNbt(nbt, "recvPlaceholders", this.recvPlaceholders);
	}

	private static void readItemArrayFromNbt(NBTTagCompound nbt, String key, ItemStack[] dst) {
		for(int i = 0; i < dst.length; i++) dst[i] = null;
		NBTTagList list = nbt.getTagList(key, 10);
		for(int i = 0; i < list.tagCount(); i++) {
			NBTTagCompound tag = list.getCompoundTagAt(i);
			int idx = tag.getByte("slot") & 0xFF;
			if(idx < dst.length) {
				dst[idx] = ItemStack.loadItemStackFromNBT(tag);
			}
		}
	}

	private static void writeItemArrayToNbt(NBTTagCompound nbt, String key, ItemStack[] src) {
		NBTTagList list = new NBTTagList();
		for(int i = 0; i < src.length; i++) {
			if(src[i] != null) {
				NBTTagCompound tag = new NBTTagCompound();
				tag.setByte("slot", (byte) i);
				src[i].writeToNBT(tag);
				list.appendTag(tag);
			}
		}
		nbt.setTag(key, list);
	}

	@Override
	public void serialize(ByteBuf buf) {
		super.serialize(buf);
		buf.writeInt(this.frequency);
		buf.writeByte(this.ioMode);
		buf.writeBoolean(this.overclocked);
		buf.writeByte(this.distMode);
		buf.writeByte(this.filterMode);
		buf.writeBoolean(this.matchMeta);
		buf.writeBoolean(this.matchOreDict);
		buf.writeBoolean(this.matchNbt);
		buf.writeBoolean(this.batchMode);
		buf.writeBoolean(this.sendSlotLock);
		buf.writeBoolean(this.recvSlotLock);
		buf.writeShort(this.stockLimit);
		buf.writeInt(this.lastItemsMoved);
		buf.writeFloat(this.lastRadPerTick);
		buf.writeShort(this.peerSenders);
		buf.writeShort(this.peerReceivers);
		buf.writeBoolean(this.hasCrossDimPeer);
		for(int i = 0; i < FILTER_SLOTS; i++) {
			ByteBufUtils.writeItemStack(buf, this.filterSlots[i]);
		}
		for(int i = 0; i < SEND_SLOTS; i++) {
			ByteBufUtils.writeItemStack(buf, this.sendSlotLock ? this.sendPlaceholders[i] : null);
		}
		for(int i = 0; i < RECV_SLOTS; i++) {
			ByteBufUtils.writeItemStack(buf, this.recvSlotLock ? this.recvPlaceholders[i] : null);
		}
	}

	@Override
	public void deserialize(ByteBuf buf) {
		super.deserialize(buf);
		this.frequency = buf.readInt();
		this.ioMode = buf.readByte();
		this.overclocked = buf.readBoolean();
		this.distMode = buf.readByte();
		this.filterMode = buf.readByte();
		this.matchMeta = buf.readBoolean();
		this.matchOreDict = buf.readBoolean();
		this.matchNbt = buf.readBoolean();
		this.batchMode = buf.readBoolean();
		this.sendSlotLock = buf.readBoolean();
		this.recvSlotLock = buf.readBoolean();
		this.stockLimit = buf.readShort();
		this.lastItemsMoved = buf.readInt();
		this.lastRadPerTick = buf.readFloat();
		this.peerSenders = buf.readShort();
		this.peerReceivers = buf.readShort();
		this.hasCrossDimPeer = buf.readBoolean();
		for(int i = 0; i < FILTER_SLOTS; i++) {
			this.filterSlots[i] = ByteBufUtils.readItemStack(buf);
		}
		for(int i = 0; i < SEND_SLOTS; i++) {
			this.sendPlaceholders[i] = ByteBufUtils.readItemStack(buf);
		}
		for(int i = 0; i < RECV_SLOTS; i++) {
			this.recvPlaceholders[i] = ByteBufUtils.readItemStack(buf);
		}
	}
}
