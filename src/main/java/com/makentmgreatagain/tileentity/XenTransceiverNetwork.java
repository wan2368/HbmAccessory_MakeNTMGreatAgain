package com.makentmgreatagain.tileentity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 不稳态 X 晶体传送器跨维度全局注册表（服务端内存态，面向百台级高并发零分配查询）。
 *
 * <p>按共振频率（{@code 0 ~ 9999}，界面显示为 {@code 000.0 ~ 999.9 MHz}）索引所有已加载的
 * 流体/物品传送器节点，内部维护只读快照列表，仅在节点增减或失效清理时重建，
 * 避免 tick 循环产生堆内存分配。</p>
 */
public final class XenTransceiverNetwork {

	private static final class FluidBucket {
		final Set<TileEntityXenFluidTransceiver> set = new LinkedHashSet<TileEntityXenFluidTransceiver>();
		final List<TileEntityXenFluidTransceiver> snapshot = new ArrayList<TileEntityXenFluidTransceiver>();
		boolean dirty = false;

		List<TileEntityXenFluidTransceiver> getValidList(int freq) {
			if(!dirty) {
				for(int i = 0, n = snapshot.size(); i < n; i++) {
					TileEntityXenFluidTransceiver te = snapshot.get(i);
					if(te == null || te.isInvalid() || te.getWorldObj() == null || te.frequency != freq) {
						dirty = true;
						break;
					}
				}
			}
			if(dirty) {
				snapshot.clear();
				Iterator<TileEntityXenFluidTransceiver> it = set.iterator();
				while(it.hasNext()) {
					TileEntityXenFluidTransceiver te = it.next();
					if(te == null || te.isInvalid() || te.getWorldObj() == null
							|| te.getWorldObj().getTileEntity(te.xCoord, te.yCoord, te.zCoord) != te
							|| te.frequency != freq) {
						it.remove();
						continue;
					}
					snapshot.add(te);
				}
				dirty = false;
			}
			return snapshot;
		}
	}

	private static final class ItemBucket {
		final Set<TileEntityXenItemTransceiver> set = new LinkedHashSet<TileEntityXenItemTransceiver>();
		final List<TileEntityXenItemTransceiver> snapshot = new ArrayList<TileEntityXenItemTransceiver>();
		boolean dirty = false;

		List<TileEntityXenItemTransceiver> getValidList(int freq) {
			if(!dirty) {
				for(int i = 0, n = snapshot.size(); i < n; i++) {
					TileEntityXenItemTransceiver te = snapshot.get(i);
					if(te == null || te.isInvalid() || te.getWorldObj() == null || te.frequency != freq) {
						dirty = true;
						break;
					}
				}
			}
			if(dirty) {
				snapshot.clear();
				Iterator<TileEntityXenItemTransceiver> it = set.iterator();
				while(it.hasNext()) {
					TileEntityXenItemTransceiver te = it.next();
					if(te == null || te.isInvalid() || te.getWorldObj() == null
							|| te.getWorldObj().getTileEntity(te.xCoord, te.yCoord, te.zCoord) != te
							|| te.frequency != freq) {
						it.remove();
						continue;
					}
					snapshot.add(te);
				}
				dirty = false;
			}
			return snapshot;
		}
	}

	private static final Map<Integer, FluidBucket> FLUID_NODES = new HashMap<Integer, FluidBucket>();
	private static final Map<Integer, ItemBucket> ITEM_NODES = new HashMap<Integer, ItemBucket>();

	private XenTransceiverNetwork() {
	}

	/* ========================== 流体节点 ========================== */

	public static void registerFluid(TileEntityXenFluidTransceiver te, int frequency) {
		if(te == null || te.getWorldObj() == null || te.getWorldObj().isRemote) return;
		FluidBucket bucket = FLUID_NODES.get(frequency);
		if(bucket == null) {
			bucket = new FluidBucket();
			FLUID_NODES.put(frequency, bucket);
		}
		if(bucket.set.add(te)) {
			bucket.dirty = true;
		}
	}

	public static void unregisterFluid(TileEntityXenFluidTransceiver te, int frequency) {
		FluidBucket bucket = FLUID_NODES.get(frequency);
		if(bucket == null) return;
		if(bucket.set.remove(te)) {
			bucket.dirty = true;
		}
		if(bucket.set.isEmpty()) FLUID_NODES.remove(frequency);
	}

	public static List<TileEntityXenFluidTransceiver> getFluidPeers(int frequency) {
		FluidBucket bucket = FLUID_NODES.get(frequency);
		if(bucket == null || bucket.set.isEmpty()) return Collections.emptyList();
		List<TileEntityXenFluidTransceiver> list = bucket.getValidList(frequency);
		if(bucket.set.isEmpty()) {
			FLUID_NODES.remove(frequency);
			return Collections.emptyList();
		}
		return list;
	}

	/* ========================== 物品节点 ========================== */

	public static void registerItem(TileEntityXenItemTransceiver te, int frequency) {
		if(te == null || te.getWorldObj() == null || te.getWorldObj().isRemote) return;
		ItemBucket bucket = ITEM_NODES.get(frequency);
		if(bucket == null) {
			bucket = new ItemBucket();
			ITEM_NODES.put(frequency, bucket);
		}
		if(bucket.set.add(te)) {
			bucket.dirty = true;
		}
	}

	public static void unregisterItem(TileEntityXenItemTransceiver te, int frequency) {
		ItemBucket bucket = ITEM_NODES.get(frequency);
		if(bucket == null) return;
		if(bucket.set.remove(te)) {
			bucket.dirty = true;
		}
		if(bucket.set.isEmpty()) ITEM_NODES.remove(frequency);
	}

	public static List<TileEntityXenItemTransceiver> getItemPeers(int frequency) {
		ItemBucket bucket = ITEM_NODES.get(frequency);
		if(bucket == null || bucket.set.isEmpty()) return Collections.emptyList();
		List<TileEntityXenItemTransceiver> list = bucket.getValidList(frequency);
		if(bucket.set.isEmpty()) {
			ITEM_NODES.remove(frequency);
			return Collections.emptyList();
		}
		return list;
	}
}
