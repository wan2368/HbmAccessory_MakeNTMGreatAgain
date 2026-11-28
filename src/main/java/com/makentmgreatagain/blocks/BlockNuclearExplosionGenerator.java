package com.makentmgreatagain.blocks;

import com.hbm.blocks.BlockDummyable;
import com.hbm.entity.projectile.EntityMeteor;
import com.hbm.handler.MultiblockHandlerXR;
import com.makentmgreatagain.init.ModNetwork;
import com.makentmgreatagain.packet.NukeOpenGuiPacket;
import com.makentmgreatagain.tileentity.TileEntityNuclearExplosionGenerator;
import com.makentmgreatagain.tileentity.TileEntityNuclearExplosionGeneratorPort;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

/**
 * 可控核爆炸发生器（13×13×17 多方块）。
 *
 * <p>外观是 Blender 里那个 16 格宽、17 格高的圆塔：底座 13×13×2 的警示带裙板，
 * 四个侧面每面 5 个接口，正好落在警示带每个 2 格方格的正中。
 * 塔身外侧的机臂/管束会伸出结构外 5.5 格，那部分只是视觉外伸，不占方块。</p>
 *
 * <h3>接口</h3>
 * <p>共 20 个（4 面 × 5 个），高度都在核心上方 1 格，和接口贴片所在的方块层一致。
 * 每个接口都是一个 {@link TileEntityProxyCombo} 代理方块，
 * 同时转发<b>流体</b>、<b>物品</b>、<b>电力</b>，所以 HBM 的三种管网都能直接接。</p>
 */
public class BlockNuclearExplosionGenerator extends BlockDummyable {

	/** 结构总高（格）：底台 y=0 到顶台 y=18，共 19 格 */
	public static final int HEIGHT = 19;
	/** 水平半径（格）：15×15 足迹（[-7, +7]） */
	public static final int RADIUS = 7;
	/** 接口相对核心的高度：底台 y=0 与顶台 y=18 */
	public static final int[] PORT_YS = {0, 18};
	/** 接口沿每条边的位置：±3，与模型底台/顶台每面 2 个整格接口一一对应 */
	public static final int[] PORT_T = {-3, 3};

	public BlockNuclearExplosionGenerator() {
		super(Material.iron);
	}

	@Override
	public float getExplosionResistance(Entity exploder, World world, int x, int y, int z, double explosionX, double explosionY, double explosionZ) {
		if(exploder instanceof EntityMeteor) return 1_000_000_000F;
		return super.getExplosionResistance(exploder, world, x, y, z, explosionX, explosionY, explosionZ);
	}

	@Override
	public TileEntity createNewTileEntity(World world, int meta) {

		if(meta >= 12)
			return new TileEntityNuclearExplosionGenerator();
		if(meta >= extra)
			return new TileEntityNuclearExplosionGeneratorPort();

		return null;
	}

	@Override
	public int[] getDimensions() {
		// {上, 下, 朝向+, 朝向-, 左, 右}
		return new int[] {HEIGHT - 1, 0, RADIUS, RADIUS, RADIUS, RADIUS};
	}

	@Override
	public int[][] getAllDimensions() {
		return new int[][] {
			getDimensions()
		};
	}

	@Override
	public int getOffset() {
		return RADIUS;
	}

	@Override
	public int[][] getAllPorts(ForgeDirection dir) {
		// 16 个接口：底台(y=0) 与顶台(y=18) 四面各 2 个（±3），与模型 16 个整格接口一一对应
		int[][] ports = new int[PORT_YS.length * PORT_T.length * 4][4];
		int i = 0;

		for(int py : PORT_YS) {
			for(int t : PORT_T) {
				ports[i++] = new int[] { t, py, -RADIUS, ForgeDirection.NORTH.ordinal()};
				ports[i++] = new int[] { t, py,  RADIUS, ForgeDirection.SOUTH.ordinal()};
				ports[i++] = new int[] { RADIUS, py, t, ForgeDirection.EAST.ordinal()};
				ports[i++] = new int[] {-RADIUS, py, t, ForgeDirection.WEST.ordinal()};
			}
		}

		return rotatePorts(ports, dir);
	}

	/**
	 * HBM 的 {@code MultiblockHandlerXR.checkSpace} 里硬编码了 2000 格上限，
	 * 而 15×15×19 = 4275 格，走原版流程永远判定"放不下"。这里自己实现一遍，去掉上限。
	 */
	@Override
	protected boolean checkRequirement(World world, int x, int y, int z, ForgeDirection dir, int o) {

		int cx = x + dir.offsetX * o, cy = y + dir.offsetY * o, cz = z + dir.offsetZ * o;
		int[] rot = MultiblockHandlerXR.rotate(getDimensions(), dir);

		for(int a = cx - rot[4]; a <= cx + rot[5]; a++) {
			for(int b = cy - rot[1]; b <= cy + rot[0]; b++) {
				for(int c = cz - rot[2]; c <= cz + rot[3]; c++) {

					// 刚放下的这一格算空的
					if(a == x && b == y && c == z) continue;

					if(!world.getBlock(a, b, c).isReplaceable(world, a, b, c)) return false;
				}
			}
		}

		return true;
	}

	/** 同上：{@code fillSpace} 也有 2000 格上限，超过就只填一半方块然后 return，所以整个重写 */
	@Override
	protected void fillSpace(World world, int x, int y, int z, ForgeDirection dir, int o) {

		int cx = x + dir.offsetX * o, cy = y + dir.offsetY * o, cz = z + dir.offsetZ * o;
		int[] rot = MultiblockHandlerXR.rotate(getDimensions(), dir);

		BlockDummyable.safeRem = true;

		for(int a = cx - rot[4]; a <= cx + rot[5]; a++) {
			for(int b = cy - rot[1]; b <= cy + rot[0]; b++) {
				for(int c = cz - rot[2]; c <= cz + rot[3]; c++) {

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

		for(int[] port : getAllPorts(dir)) {
			this.makeExtra(world, x + port[0], y + port[1], z + port[2]);
		}
	}

	/**
	 * 模型外壳的交互判定盒，坐标相对核心方块底部中心（和模型原点一致）。
	 * 15×15 实体外只有约 0.5 格管件外伸，保持紧凑范围即可。
	 */
	private static final AxisAlignedBB[] HITBOXES = {
			AxisAlignedBB.getBoundingBox(-8.0D, 0.0D, -8.0D, 8.0D, 4.0D, 8.0D)
	};

	/**
	 * 模型（OBJ）比 13×13 的方块体积宽得多，直接点看得见的塔身外壳会打空：
	 * 射线根本没穿过任何方块，{@code onBlockActivated} 不会被调用，拆机器也一样。
	 * 这里把模型外壳并进射线检测，命中点仍然归到当前这一格，
	 * 右键开界面 / 左键挖掘都按正常流程走。
	 */
	@Override
	public MovingObjectPosition collisionRayTrace(World world, int x, int y, int z, Vec3 start, Vec3 end) {

		MovingObjectPosition hit = super.collisionRayTrace(world, x, y, z, start, end);
		double best = hit == null ? Double.MAX_VALUE : start.distanceTo(hit.hitVec);

		int[] core = this.findCore(world, x, y, z);
		if(core == null) return hit;

		for(AxisAlignedBB template : HITBOXES) {

			AxisAlignedBB box = template.getOffsetBoundingBox(core[0] + 0.5D, core[1], core[2] + 0.5D);

			// 人就站在这个盒子里（例如贴着底座）时不参与判定，否则会挡住脚下的方块放置
			if(box.isVecInside(start)) continue;

			MovingObjectPosition mop = box.calculateIntercept(start, end);
			if(mop != null && start.distanceTo(mop.hitVec) < best) {
				hit = new MovingObjectPosition(x, y, z, mop.sideHit, mop.hitVec);
			}
		}

		return hit;
	}

	/**
	 * 任何一块被拆时通知正在充能的机器按装药引爆，然后走 HBM 原来的连锁拆除。
	 * 判定只查一张「正在充能」的弱引用表（通常 0~1 台），不再扫方块 / TileEntity，拆机不会卡。
	 */
	@Override
	public void breakBlock(World world, int x, int y, int z, Block block, int meta) {

		TileEntityNuclearExplosionGenerator.onAnyBlockBroken(world, x, y, z);

		super.breakBlock(world, x, y, z, block, meta);
	}

	/**
	 * 右键底部 4 格（核心 / 接口 / 底座 / 塔身下部）：打开界面。
	 *
	 * <p>外壳命中的方块格常常在 6 格 reach 之外，原版 C08 包会被服务端按距离丢掉，
	 * 所以开界面统一由客户端发 {@link NukeOpenGuiPacket}，服务端只校验距离。
	 * 服务端这里不再自己开，避免同一个右键开两次。</p>
	 *
	 * <p>只有底部 4 格高度能开：从塔身上部往下点会反复开界面，界面会闪烁。</p>
	 */
	@Override
	public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player, int side, float hitX, float hitY, float hitZ) {

		int[] core = this.findCore(world, x, y, z);
		if(core == null) return false;

		if(y - core[1] >= 4) return false;

		if(world.isRemote)
			ModNetwork.NETWORK.sendToServer(new NukeOpenGuiPacket.Message(core[0], core[1], core[2]));

		return true;
	}
}
