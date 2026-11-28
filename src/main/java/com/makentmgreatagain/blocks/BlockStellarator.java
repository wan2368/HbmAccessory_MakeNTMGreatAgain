package com.makentmgreatagain.blocks;

import java.util.List;

import com.hbm.blocks.BlockDummyable;
import com.hbm.entity.projectile.EntityMeteor;
import com.hbm.handler.MultiblockHandlerXR;
import com.hbm.tileentity.TileEntityProxyCombo;
import com.makentmgreatagain.MakeNTMGreatAgain;
import com.makentmgreatagain.tileentity.TileEntityStellarator;

import cpw.mods.fml.common.network.internal.FMLNetworkHandler;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

/**
 * 仿星器：31×31×9 外观多方块。
 *
 * <p>模型沿 X 覆盖 ±14.89、沿 Z 覆盖 ±13.94、高 8.806，原点在核心格底面中心，
 * 因此核心格位于机器正中央；结构按 {上 8, 下 0, 北 15, 南 15, 西 15, 东 15} 铺开。</p>
 *
 * <p>接口共 10 个，全部落在整格方块中心：底面 5 个（中心 + 沿 X/Z 各 ±2）、
 * 顶面 5 个（同一组坐标抬高 8 格）。接口格挂 {@code TileEntityProxyCombo().power().fluid()}，
 * 电与水/蒸汽/增殖流体都由这里透传到核心。</p>
 *
 * <p>结构只填<b>碰撞圆环</b>那部分格子（{@link #isRingCell}）；四角、圆环内部的空腔都不放
 * dummy 方块，是真正的空气，可以直接放方块、也能让等离子加热器的无碰撞桶管伸进来。
 * 占格形状和碰撞形状完全一致：有碰撞的格子才占格，没碰撞的格子都能放方块。
 * 元数据链只在圆环内部连通；上层顶板的链会穿过空腔，所以本类关掉了基类的
 * 「链断就删孤儿」逻辑（{@link #onNeighborBlockChange}），拆机由 {@link #breakBlock}
 * 的整机清场负责。</p>
 *
 * <p>右键打在任意一个有碰撞的格子上都打开核心的界面（见 {@link #findCoreSafe}）。
 * 机器没有手动开关：等离子、电、水都到位就自动点火运行。</p>
 */
public class BlockStellarator extends BlockDummyable {

	public BlockStellarator() {
		super(Material.iron);
	}

	@Override
	public float getExplosionResistance(Entity exploder, World world, int x, int y, int z, double explosionX, double explosionY, double explosionZ) {
		if(exploder instanceof EntityMeteor) return 1_000_000_000F;
		return super.getExplosionResistance(exploder, world, x, y, z, explosionX, explosionY, explosionZ);
	}

	@Override
	public int[] getDimensions() {
		// {上, 下, 北, 南, 西, 东}：31×31×9，四个方向对称，
		// 模型（±14.89 / ±13.94）完整落在结构内，任何朝向放置都不会压到玩家
		return new int[] {8, 0, 15, 15, 15, 15};
	}

	@Override
	public int getOffset() {
		// 四个方向半长都是 15，核心格恒在机器中心
		return 15;
	}

	/** 10 个接口相对核心格的坐标（朝南基准），全部是整格方块中心。 */
	private static final int[][] PORTS_MODEL = {
			{ 0, 0,  0, ForgeDirection.DOWN.ordinal()},
			{ 2, 0,  0, ForgeDirection.DOWN.ordinal()},
			{-2, 0,  0, ForgeDirection.DOWN.ordinal()},
			{ 0, 0,  2, ForgeDirection.DOWN.ordinal()},
			{ 0, 0, -2, ForgeDirection.DOWN.ordinal()},
			{ 0, 8,  0, ForgeDirection.UP.ordinal()},
			{ 2, 8,  0, ForgeDirection.UP.ordinal()},
			{-2, 8,  0, ForgeDirection.UP.ordinal()},
			{ 0, 8,  2, ForgeDirection.UP.ordinal()},
			{ 0, 8, -2, ForgeDirection.UP.ordinal()},
	};

	/**
	 * 逐层碰撞轮廓（格坐标系，原点在核心格中心）。
	 *
	 * <p>每层是 {@code {外半径, 内半径}}：格心到核心格中心的距离落在两者之间才有碰撞、
	 * 才算被射线打中。整个碰撞体是「上下同心的圆盘/圆环叠出来的圆台」——四个角天生是
	 * 圆弧，前后左右完全对称，不会跟着模型细节长出不规则的缺口和孤立格。</p>
	 *
	 * <p>半径是照模型量的：把 {@code stellarator.obj} 逐层体素化，按半径统计顶面高度，
	 * 取「顶面 ≥ 该层 + 1」的最大半径当外径（脚本 {@code build/tmp/verify/ring_top.py}）。
	 * 中央 R6 以内模型只有 y=0（底板）、y=4（中板）、y=8（顶板）三块横板，其余是空的，
	 * 所以 y=1~3、5~7 把内圆挖掉，碰撞就和模型一样是「三块板夹两道空腔」。</p>
	 *
	 * <p>挖空不会困人：坑壁正好是 y=5/6/7 三级 1 格高的台阶，掉到中板上能一级级跳出来。</p>
	 */
	private static final double[][] RING = {
			// {外径, 内径}
			{14.4, 0.0},	// y=0 底板
			{14.4, 6.0},	// y=1
			{14.4, 6.0},	// y=2
			{14.4, 6.0},	// y=3
			{14.4, 0.0},	// y=4 中板：模型在这一层又是一整片
			{14.0, 6.0},	// y=5 外缘开始收
			{13.0, 6.0},	// y=6
			{13.0, 6.5},	// y=7 环体上缘
			{3.0,  0.0},	// y=8 顶板只剩中心
	};

	/**
	 * 该格（相对核心格的格坐标）是否落在碰撞圆环里。占格、碰撞、射线全用这一个判据，
	 * 保证「有碰撞的格子才占格、没碰撞的格子都是空气」。
	 */
	public boolean isRingCell(int dy, int dx, int dz) {

		if(dy < 0 || dy >= RING.length) return false;

		double r = Math.sqrt(dx * dx + dz * dz);
		double[] ring = RING[dy];
		return r <= ring[0] && r >= ring[1];
	}

	private static int[] rotateXZ(int x, int z, ForgeDirection dir) {
		switch(dir) {
		case EAST:	return new int[] { z, -x};
		case NORTH:	return new int[] {-x, -z};
		case WEST:	return new int[] {-z,  x};
		case SOUTH:
		default:	return new int[] { x,  z};
		}
	}

	private static ForgeDirection rotateFace(ForgeDirection face, ForgeDirection dir) {
		int[] r = rotateXZ(face.offsetX, face.offsetZ, dir);
		for(ForgeDirection d : new ForgeDirection[] {ForgeDirection.NORTH, ForgeDirection.SOUTH, ForgeDirection.WEST, ForgeDirection.EAST}) {
			if(d.offsetX == r[0] && d.offsetZ == r[1]) return d;
		}
		return face;
	}

	/** 10 个接口相对核心格的坐标（已按机器朝向旋转）。 */
	public int[][] getPortsAtCore(ForgeDirection dir) {
		int[][] out = new int[PORTS_MODEL.length][4];
		for(int i = 0; i < PORTS_MODEL.length; i++) {
			int[] p = rotateXZ(PORTS_MODEL[i][0], PORTS_MODEL[i][2], dir);
			ForgeDirection face = rotateFace(ForgeDirection.getOrientation(PORTS_MODEL[i][3]), dir);
			out[i] = new int[] {p[0], PORTS_MODEL[i][1], p[1], face.ordinal()};
		}
		return out;
	}

	@Override
	public int[][] getAllPorts(ForgeDirection dir) {
		int[][] ports = getPortsAtCore(dir);
		for(int[] port : ports) {
			port[0] -= dir.offsetX * getOffset();
			port[2] -= dir.offsetZ * getOffset();
		}
		return ports;
	}

	/**
	 * HBM 的 {@code MultiblockHandlerXR.checkSpace} 里硬编码了 2000 格上限，
	 * 而本机 31×29×9 = 8091 格，走原版流程永远判定「放不下」。这里去掉上限重写一遍，
	 * 判定口径与基类保持一致（刚放下的那一格算空的）。
	 */
	@Override
	protected boolean checkRequirement(World world, int x, int y, int z, ForgeDirection dir, int o) {

		int cx = x + dir.offsetX * o, cy = y + dir.offsetY * o, cz = z + dir.offsetZ * o;
		int[] rot = MultiblockHandlerXR.rotate(getDimensions(), dir);

		for(int a = cx - rot[4]; a <= cx + rot[5]; a++) {
			for(int b = cy - rot[1]; b <= cy + rot[0]; b++) {
				for(int c = cz - rot[2]; c <= cz + rot[3]; c++) {

					if(a == x && b == y && c == z) continue;

					// 只有圆环里的格子会占格，四角和内部空腔不检查（那里可以已有方块）
					if(!this.isRingCell(b - cy, a - cx, c - cz)) continue;

					if(!world.getBlock(a, b, c).isReplaceable(world, a, b, c)) return false;
				}
			}
		}

		return true;
	}

	/** 同上：{@code fillSpace} 也有 2000 格上限，超过就只填一半方块然后 return，所以整个重写。 */
	@Override
	public void fillSpace(World world, int x, int y, int z, ForgeDirection dir, int o) {

		int cx = x + dir.offsetX * o, cy = y + dir.offsetY * o, cz = z + dir.offsetZ * o;
		int[] rot = MultiblockHandlerXR.rotate(getDimensions(), dir);

		BlockDummyable.safeRem = true;

		for(int a = cx - rot[4]; a <= cx + rot[5]; a++) {
			for(int b = cy - rot[1]; b <= cy + rot[0]; b++) {
				for(int c = cz - rot[2]; c <= cz + rot[3]; c++) {

					// 只填碰撞圆环：四角和内部空腔留空，可以直接放方块/让别的模型伸进来
					if(!this.isRingCell(b - cy, a - cx, c - cz)) continue;

					int meta;

					if(b < cy) meta = ForgeDirection.DOWN.ordinal();
					else if(b > cy) meta = ForgeDirection.UP.ordinal();
					else if(a < cx) meta = ForgeDirection.WEST.ordinal();
					else if(a > cx) meta = ForgeDirection.EAST.ordinal();
					else if(c < cz) meta = ForgeDirection.NORTH.ordinal();
					else if(c > cz) meta = ForgeDirection.SOUTH.ordinal();
					else continue; // 核心格由 onBlockPlacedBy 自己放

					world.setBlock(a, b, c, this, meta, 3);
				}
			}
		}

		BlockDummyable.safeRem = false;

		// 端口格升成 extra（元数据 +6）。这一步不做的话端口格只是个普通 dummy 方块、
		// createNewTileEntity 返回 null，管道/线缆/物品总线上门就没有 TE 可连。
		// 坐标用 getAllPorts(dir)，它已经换算成「相对放置格」，和 makeExtra 的入参一致。
		for(int[] port : this.getAllPorts(dir)) {
			this.makeExtra(world, x + port[0], y + port[1], z + port[2]);
		}

		// 核心格的 TE 必须通知客户端，否则客户端不会创建 TE，TESR 永远不会被调用。
		// 注意：接口格不要写 extra+朝向 的元数据 —— 那会落到 10~15，其中的 12~15
		// 与核心格的元数据区间重合，会导致接口格被当成核心（生成多余 TE、且拆机时不被清除）。
		world.markBlockForUpdate(cx, cy, cz);
	}

	/**
	 * 右键打开 GUI（没有手动开关，料/电/水齐了就自动运行）。
	 *
	 * <p>整台机器是 31×31×9 的整片多方块，任何一格都能点（碰撞箱虽然只跟着模型走，
	 * 但射线检测仍按整格算，见 {@link #addCollisionBoxesToList}）：
	 * 先用元数据链 {@link #findCore} 反推核心，链断了再按整机包围盒兜底扫一次核心格
	 * （只有核心格的元数据 ≥ 12，扫起来很便宜）。</p>
	 *
	 * <p>开关切换<b>不再往聊天栏发提示</b>，状态在 GUI 的等离子罐与电力条上能直接看出来。</p>
	 */
	@Override
	public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player, int side,
			float hitX, float hitY, float hitZ) {

		if(world.isRemote) return true;

		int[] pos = this.findCoreSafe(world, x, y, z);
		if(pos == null) return false;

		TileEntity te = world.getTileEntity(pos[0], pos[1], pos[2]);
		if(!(te instanceof TileEntityStellarator)) return false;

		FMLNetworkHandler.openGui(player, MakeNTMGreatAgain.instance, MakeNTMGreatAgain.GUI_STELLARATOR,
				world, pos[0], pos[1], pos[2]);

		return true;
	}

	/**
	 * 碰撞只加在圆环轮廓里。
	 *
	 * <p>整机是 31×31×9 的整片 dummy 填充，四个方角上没有任何几何体。每格先用
	 * {@link #RING} 判一次半径，落在轮廓外的格子不加碰撞箱，于是碰撞边界是圆弧。</p>
	 *
	 * <p>格子本身仍然按整格算：格心落在圆环里就给满格的碰撞，不会出现半格台阶。</p>
	 */
	@Override
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public void addCollisionBoxesToList(World world, int x, int y, int z, AxisAlignedBB entityBounding, List list, Entity entity) {

		if(this.hasModelAt(world, x, y, z)) {
			super.addCollisionBoxesToList(world, x, y, z, entityBounding, list, entity);
		}
	}

	/**
	 * 射线也跟着圆环走。
	 *
	 * <p>只砍掉碰撞箱还不够：原版的方块描边（瞄准时那圈黑框）是拿射线打中的方块画整格
	 * 包围盒的，圆角外那些空格子仍然会被画出一个悬空的黑框，看着像机器缺了一块。
	 * 这里对轮廓外的格子直接返回 null，射线穿过去，描边只会落在有碰撞的格子上。</p>
	 *
	 * <p>副作用是「点圆角外的空气」不再打开界面——那些格子本来就在模型外面，
	 * 射线会继续往前打到后面的机器上，一样能开。轮廓内的格子行为不变。</p>
	 */
	@Override
	public MovingObjectPosition collisionRayTrace(World world, int x, int y, int z, Vec3 startVec, Vec3 endVec) {

		if(!this.hasModelAt(world, x, y, z)) return null;

		return super.collisionRayTrace(world, x, y, z, startVec, endVec);
	}

	/**
	 * 关掉基类的孤儿判定。
	 *
	 * <p>本机只填碰撞圆环，上层顶板的元数据链会经过被挖空的内部空腔；基类在邻居变化时
	 * 发现「朝核心方向的邻居不是自己」就会把该格删掉，顶板会被误删。
	 * 整机拆卸由 {@link #breakBlock} 的整片清场负责，不依赖链式反应，所以这里留空。</p>
	 */
	@Override
	public void onNeighborBlockChange(World world, int x, int y, int z, Block block) {
	}

	/**
	 * 该格是否落在圆环轮廓里。
	 *
	 * <p>圆环绕核心格中心旋转对称，所以不必管机器朝向：只比格心到核心格中心的水平距离。
	 * 拿不到核心（元数据链断了）时按实心算，宁可多挡一格也别让玩家掉进机器里。</p>
	 */
	private boolean hasModelAt(IBlockAccess world, int x, int y, int z) {

		int[] core = this.findCore(world, x, y, z);
		if(core == null) return true;

		return this.isRingCell(y - core[1], x - core[0], z - core[2]);
	}

	/**
	 * 找到包含 (x, y, z) 的核心格。优先走元数据链；链断了（例如经过内部空腔 y=1~3 / y=5~7 或
	 * 顶板 y=8）就在合法包围盒范围内（a∈x±15, b∈[y-8, y], c∈z±15）精确匹配包含该格的唯一核心格。
	 */
	private int[] findCoreSafe(World world, int x, int y, int z) {

		int[] pos = this.findCore(world, x, y, z);
		if(pos != null && world.getTileEntity(pos[0], pos[1], pos[2]) instanceof TileEntityStellarator) return pos;

		int[] best = null;
		int bestDistSq = Integer.MAX_VALUE;
		for(int a = x - 15; a <= x + 15; a++) {
			for(int b = y - 8; b <= y; b++) {
				for(int c = z - 15; c <= z + 15; c++) {
					if(world.getBlock(a, b, c) != this) continue;
					if(world.getBlockMetadata(a, b, c) < 12) continue;
					if(!(world.getTileEntity(a, b, c) instanceof TileEntityStellarator)) continue;
					if(!this.isRingCell(y - b, x - a, z - c)) continue;
					int dx = x - a, dy = y - b, dz = z - c;
					int distSq = dx * dx + dy * dy + dz * dz;
					if(distSq < bestDistSq) {
						bestDistSq = distSq;
						best = new int[] { a, b, c };
					}
				}
			}
		}

		return best;
	}

	@Override
	public void onBlockAdded(World world, int x, int y, int z) {
		super.onBlockAdded(world, x, y, z);

		// 客户端兜底：方块变化包不会替我们建 TE，这里补一个，保证 TESR 能拿到渲染点
		if(world.isRemote) {
			int meta = world.getBlockMetadata(x, y, z);
			if(meta >= 12 && world.getTileEntity(x, y, z) == null) {
				TileEntity te = createNewTileEntity(world, meta);
				if(te != null) {
					te.xCoord = x;
					te.yCoord = y;
					te.zCoord = z;
					world.setTileEntity(x, y, z, te);
				}
			}
		}
	}

	@Override
	public TileEntity createNewTileEntity(World world, int meta) {
		if(meta >= 12) return new TileEntityStellarator();
		// 端口格：电、流体、物品三样都要能透传到核心，缺一样管道就接不上
		if(hasExtra(meta)) return new TileEntityProxyCombo().inventory().power().fluid();
		return null;
	}

	/**
	 * 生存模式防刷：只有能定位到尚未掉落过的活跃核心 {@link TileEntityStellarator} 时才允许掉落 1 个仿星器。
	 * 即使旧存档有孤立残留方块，或连锁工具同 tick 挖掉多格，每台仿星器也绝不可能掉落超过 1 个。
	 */
	@Override
	public void onBlockHarvested(World world, int x, int y, int z, int meta, EntityPlayer player) {
		if(world.isRemote || player == null || player.capabilities.isCreativeMode) return;

		int[] core = (meta >= 12) ? new int[] { x, y, z } : this.findCoreSafe(world, x, y, z);
		if(core == null) return;

		TileEntity te = world.getTileEntity(core[0], core[1], core[2]);
		if(!(te instanceof TileEntityStellarator)) return;

		TileEntityStellarator stellarator = (TileEntityStellarator) te;
		if(stellarator.droppedByHarvest) return;
		stellarator.droppedByHarvest = true;

		harvesters.set(player);
		this.dropBlockAsItem(world, x, y, z, meta, 0);
		harvesters.set(null);
	}

	/**
	 * 拆机时按整机范围彻底清场：
	 * - 先判断本格是否核心（meta >= 12），否则走 {@link #findCoreSafe}（支持跨越内部空腔 y=1~3/y=5~7 与顶板 y=8 找到真正的核心）；
	 * - 找到核心后将整台 31×31×9 范围内的所有本方块一次清干净，不会留下任何残留；
	 * - 若本身是旧存档孤立残留（找不到核心），则清理其连通的孤立同方块，避免死砖残留。
	 */
	@Override
	public void breakBlock(World world, int x, int y, int z, Block b, int meta) {

		if(!world.isRemote && !BlockDummyable.safeRem) {

			int[] core = (meta >= 12) ? new int[] { x, y, z } : this.findCoreSafe(world, x, y, z);

			if(core == null) {
				for(ForgeDirection d : ForgeDirection.VALID_DIRECTIONS) {
					int nx = x + d.offsetX, ny = y + d.offsetY, nz = z + d.offsetZ;
					if(world.getBlock(nx, ny, nz) == this) {
						core = this.findCoreSafe(world, nx, ny, nz);
						if(core != null) break;
					}
				}
			}

			BlockDummyable.safeRem = true;
			try {
				if(core != null) {
					for(int a = core[0] - 15; a <= core[0] + 15; a++) {
						for(int bb = core[1]; bb <= core[1] + 8; bb++) {
							for(int c = core[2] - 15; c <= core[2] + 15; c++) {
								if(!this.isRingCell(bb - core[1], a - core[0], c - core[2])) continue;
								if(world.getBlock(a, bb, c) == this) {
									world.setBlockToAir(a, bb, c);
								}
							}
						}
					}
				}
			} finally {
				BlockDummyable.safeRem = false;
			}
		}

		super.breakBlock(world, x, y, z, b, meta);
	}
}
