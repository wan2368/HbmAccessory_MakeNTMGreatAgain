package com.makentmgreatagain.tileentity;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import net.minecraftforge.event.world.ChunkEvent;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * 投送中继节点网络（按 World 实例隔离，区分服务端/客户端，版本号驱动缓存，零常驻寻路开销）。
 *
 * <p>节点加载时自动注册，卸载/拆除时自动注销并递增版本号。
 * 任意两个节点间距 ≤ 32 格即自动连通（360° 全向棱镜，无视角死角），
 * 发送端与接收端分别在 ≤ 32 格且满足自身贴面视角时接入网络，支持无限跳数叠加。</p>
 */
public final class DeliveryRelayNetwork {

	public static final double HOP_RANGE = 32.0D;

	private static final Map<World, Set<TileEntityDeliveryRelay>> RELAYS = new WeakHashMap<World, Set<TileEntityDeliveryRelay>>();
	private static final Map<World, Integer> VERSIONS = new WeakHashMap<World, Integer>();

	private DeliveryRelayNetwork() { }

	/** 已有的中继在区块重新载入时不一定再次调用 validate。 */
	public static final class ChunkListener {
		@SubscribeEvent
		public void onChunkLoad(ChunkEvent.Load event) {
			for(Object tile : event.getChunk().chunkTileEntityMap.values()) {
				if(tile instanceof TileEntityDeliveryRelay && !((TileEntityDeliveryRelay) tile).isInvalid()) {
					register((TileEntityDeliveryRelay) tile);
				}
			}
		}

		@SubscribeEvent
		public void onChunkUnload(ChunkEvent.Unload event) {
			for(Object tile : event.getChunk().chunkTileEntityMap.values()) {
				if(tile instanceof TileEntityDeliveryRelay) unregister((TileEntityDeliveryRelay) tile);
			}
		}
	}

	public static void register(TileEntityDeliveryRelay relay) {
		World w = relay.getWorldObj();
		if(w == null) return;
		Set<TileEntityDeliveryRelay> set = RELAYS.get(w);
		if(set == null) {
			set = new HashSet<TileEntityDeliveryRelay>();
			RELAYS.put(w, set);
		}
		if(set.add(relay)) {
			bumpVersion(w);
		}
	}

	public static void unregister(TileEntityDeliveryRelay relay) {
		World w = relay.getWorldObj();
		if(w == null) return;
		Set<TileEntityDeliveryRelay> set = RELAYS.get(w);
		if(set != null && set.remove(relay)) {
			bumpVersion(w);
		}
	}

	private static void bumpVersion(World w) {
		Integer v = VERSIONS.get(w);
		VERSIONS.put(w, v == null ? 1 : (v + 1));
	}

	public static int getVersion(World w) {
		if(w == null) return 0;
		Integer v = VERSIONS.get(w);
		return v == null ? 0 : v;
	}

	private static List<TileEntityDeliveryRelay> getValidRelays(World w) {
		List<TileEntityDeliveryRelay> out = new ArrayList<TileEntityDeliveryRelay>();
		Set<TileEntityDeliveryRelay> set = RELAYS.get(w);
		if(set == null || set.isEmpty()) return out;
		for(TileEntityDeliveryRelay r : new ArrayList<TileEntityDeliveryRelay>(set)) {
			if(r == null || r.isInvalid() || r.getWorldObj() != w || !w.blockExists(r.xCoord, r.yCoord, r.zCoord)) {
				if(set.remove(r)) bumpVersion(w);
			} else {
				out.add(r);
			}
		}
		return out;
	}

	/** 寻路结果：中间经过的中继节点坐标列表（0 跳直连时为空列表）与总路径长度。 */
	public static class RouteResult {
		public final List<int[]> relayCoords;
		public final double totalDistance;
		public final int hops; // 1 = 直连, 2 = 经 1 个节点, ...

		public RouteResult(List<int[]> relayCoords, double totalDistance) {
			this.relayCoords = relayCoords;
			this.totalDistance = totalDistance;
			this.hops = relayCoords.size() + 1;
		}
	}

	/**
	 * 从 sender 到 receiver 的最短路径（Dijkstra）：
	 * 1) 若距离 ≤ 32 且双方视角互通，直接返回 0 跳直连；
	 * 2) 否则通过已加载中继节点自动寻路（每跳 ≤ 32 格，总距离无上限）。
	 * 返回 null 表示当前不可达。
	 */
	public static RouteResult findRoute(TileEntityAtomicProbe sender, TileEntityAtomicProbe receiver) {
		if(sender == null || receiver == null || sender.getWorldObj() != receiver.getWorldObj()) return null;
		World w = sender.getWorldObj();
		if(w == null) return null;

		double maxHop = Math.min(sender.getMaxRange(), HOP_RANGE);
		double directDist = sender.getDistanceTo(receiver);
		if(directDist <= maxHop && sender.isInFieldOfView(receiver) && receiver.isInFieldOfView(sender)) {
			return new RouteResult(Collections.<int[]>emptyList(), directDist);
		}

		List<TileEntityDeliveryRelay> relays = getValidRelays(w);
		int n = relays.size();
		if(n == 0) return null;

		Vec3 sPos = sender.getLensWorldPos();
		Vec3 rPos = receiver.getLensWorldPos();
		Vec3[] rCenters = new Vec3[n];
		for(int i = 0; i < n; i++) rCenters[i] = relays.get(i).getCoreWorldPos();

		double[] dist = new double[n];
		int[] prev = new int[n];
		boolean[] visited = new boolean[n];
		for(int i = 0; i < n; i++) {
			dist[i] = Double.POSITIVE_INFINITY;
			prev[i] = -1;
		}

		PriorityQueue<int[]> pq = new PriorityQueue<int[]>(Math.max(4, n), new Comparator<int[]>() {
			@Override
			public int compare(int[] a, int[] b) {
				return Double.compare(dist[a[0]], dist[b[0]]);
			}
		});

		// 入口边：sender -> 满足距离与发送端视角的中继节点
		for(int i = 0; i < n; i++) {
			double d = sPos.distanceTo(rCenters[i]);
			if(d <= maxHop && sender.isPointInFieldOfView(rCenters[i])) {
				dist[i] = d;
				pq.add(new int[] { i });
			}
		}

		double bestTotal = Double.POSITIVE_INFINITY;
		int bestEnd = -1;

		while(!pq.isEmpty()) {
			int u = pq.poll()[0];
			if(visited[u]) continue;
			visited[u] = true;
			if(dist[u] >= bestTotal) break;

			// 检查 u -> receiver 出口边
			double toRecv = rCenters[u].distanceTo(rPos);
			if(toRecv <= maxHop && receiver.isPointInFieldOfView(rCenters[u])) {
				double total = dist[u] + toRecv;
				if(total < bestTotal) {
					bestTotal = total;
					bestEnd = u;
				}
			}

			// 节点 u -> 其他相邻节点 v（距离 ≤ HOP_RANGE）
			for(int v = 0; v < n; v++) {
				if(visited[v]) continue;
				double edge = rCenters[u].distanceTo(rCenters[v]);
				if(edge <= HOP_RANGE) {
					double nd = dist[u] + edge;
					if(nd < dist[v]) {
						dist[v] = nd;
						prev[v] = u;
						pq.add(new int[] { v });
					}
				}
			}
		}

		if(bestEnd < 0) return null;

		List<int[]> path = new ArrayList<int[]>();
		for(int cur = bestEnd; cur >= 0; cur = prev[cur]) {
			TileEntityDeliveryRelay r = relays.get(cur);
			path.add(new int[] { r.xCoord, r.yCoord, r.zCoord });
		}
		Collections.reverse(path);
		return new RouteResult(path, bestTotal);
	}

	/** 供 GUI 连接管理页使用：搜出当前探头直连或经中继网可达的所有对端探头及路径信息。 */
	public static class ReachablePeer {
		public final TileEntityAtomicProbe peer;
		public final double distance;
		public final int relayCount; // 0 = 直连，-1 = 没有可用路径
		public final boolean reachable;

		public ReachablePeer(TileEntityAtomicProbe peer, double distance, int relayCount, boolean reachable) {
			this.peer = peer;
			this.distance = distance;
			this.relayCount = relayCount;
			this.reachable = reachable;
		}
	}

	@SuppressWarnings("unchecked")
	public static List<ReachablePeer> findReachablePeers(TileEntityAtomicProbe self) {
		List<ReachablePeer> out = new ArrayList<ReachablePeer>();
		if(self == null || self.getWorldObj() == null) return out;
		World w = self.getWorldObj();

		List<TileEntity> loaded = new ArrayList<TileEntity>(w.loadedTileEntityList);
		for(TileEntity te : loaded) {
			if(!(te instanceof TileEntityAtomicProbe) || te == self) continue;
			TileEntityAtomicProbe other = (TileEntityAtomicProbe) te;
			if(other.isReceiver == self.isReceiver) continue;

			TileEntityAtomicProbe s = self.isReceiver ? other : self;
			TileEntityAtomicProbe r = self.isReceiver ? self : other;
			RouteResult route = findRoute(s, r);
			if(route != null) {
				out.add(new ReachablePeer(other, route.totalDistance, route.relayCoords.size(), true));
			} else if(self.getDistanceTo(other) <= self.getMaxRange()) {
				// 32 格内但视角死角也列出来并标红提示
				out.add(new ReachablePeer(other, self.getDistanceTo(other), -1, false));
			}
		}

		Collections.sort(out, new Comparator<ReachablePeer>() {
			@Override
			public int compare(ReachablePeer a, ReachablePeer b) {
				return Double.compare(a.distance, b.distance);
			}
		});
		return out;
	}
}
