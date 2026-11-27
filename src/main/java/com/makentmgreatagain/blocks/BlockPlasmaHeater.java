package com.makentmgreatagain.blocks;

import java.util.List;

import com.hbm.blocks.BlockDummyable;
import com.hbm.blocks.ITooltipProvider;
import com.hbm.tileentity.TileEntityProxyCombo;
import com.makentmgreatagain.MakeNTMGreatAgain;
import com.makentmgreatagain.tileentity.TileEntityPlasmaHeater;

import cpw.mods.fml.common.network.internal.FMLNetworkHandler;
import net.minecraft.block.material.Material;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

/**
 * 等离子加热器：16×7×7 占格多方块（Blender 模型 1 单位 = 1 格实量，桶管段模型伸出占格区外）。
 *
 * <p>模型沿长轴（OBJ +X）覆盖 -10.813..10、机体宽 ±2.95（底部裙边 ±3.316）、高 7，
 * 原点在核心格底面中心，长轴正中就是核心格，<b>头部（桶管/输出端）朝 +X</b>。
 * 结构按 {上 6, 下 0, 北 5, 南 10, 西 3, 东 3} 铺开（16×7×7 格），
 * 放置时瞄准的那一格是<b>尾部</b>，核心格落在瞄准点前方（远离玩家）10 格，
 * 机身顺着视线向前延伸，桶管口在瞄准点前方 20 格处（见
 * {@link com.makentmgreatagain.client.RenderPlasmaHeater}）。</p>
 *
 * <p><b>接口</b>：10 个，在尾部端面 5 列（z -2..2）× 2 行（y 1/3），
 * 面板贴在后端方块（x -10）的外侧面 x=-10.5 上、面朝 -X，挂
 * {@code TileEntityProxyCombo().inventory().power().fluid()}，电、流体桶/管道都从这里进出。</p>
 *
 * <p><b>碰撞与占格</b>：整机在 {@code bounding} 里只给了<b>机体</b>的 AABB（长轴 -10.5..+5、
 * 宽 ±2.95、高 7）。尾部接口面板正好落在 -10.5 这个方块分界面上，所以管道接在
 * 后面那格时接口正好贴合；底部外凸裙边（宽 ±3.316、向后伸到 -10.813）和
 * 头部桶管段（+5..+10）都<b>没有碰撞</b>。这些没有碰撞的地方也<b>不占格</b>：
 * 结构只填到 -10..+5、宽 ±3、高 0..6，桶管伸出去的那几格是空气，
 * 可以直接在周围/下方放方块。</p>
 *
 * <p>右键任意格子打开核心界面，配方选择与聚变堆同款（配方选择器 + 蓝图池）。</p>
 */
public class BlockPlasmaHeater extends BlockDummyable implements ITooltipProvider {

	/** 桶管口（模型 +X 最前端）距核心格的格数，也是“前端是否伸进仿星器”的采样距离 */
	public static final int MUZZLE_DISTANCE = 10;

	public BlockPlasmaHeater() {
		super(Material.iron);

		// 分段贴合机体的碰撞与射线包围盒（相对核心格中心 XZ / 底面 Y）：
		// 1. 后段主箱体 + 底部外凸裙边（覆盖至 z ±3.32，防止玩家穿进裙边卡住并保证侧裙面可右键打开 GUI）
		this.bounding.add(AxisAlignedBB.getBoundingBox(-10.5D, 0.0D, -3.32D, -3.5D, 6.65D, 3.32D));
		// 2. 后段顶部凸起结构
		this.bounding.add(AxisAlignedBB.getBoundingBox(-10.5D, 6.65D, -2.25D, -4.5D, 7.0D, 2.95D));
		// 3. 中前段收窄机身（贴合模型宽度 ±2.60，避免空气处出现多余隐形碰撞）
		this.bounding.add(AxisAlignedBB.getBoundingBox(-3.5D, 0.0D, -2.60D, 4.5D, 6.15D, 2.60D));
		// 4. 前端 x=+5 收口段（补全至 +5.5 占位方块边界，修复前端面无法右键互动 GUI 的问题）
		this.bounding.add(AxisAlignedBB.getBoundingBox(4.5D, 0.0D, -2.60D, 5.5D, 4.50D, 2.60D));
	}

	@Override
	public int[] getDimensions() {
		// {上, 下, 北, 南, 西, 东}
		// 长轴：尾部 S=10（尾部接口格，一直留到 -10），头部 N=5（机体到 +5 为止）；
		//   再往前的 +6..+10 只是模型里的桶管，没有碰撞、也不占格，可以直接放方块。
		// 短轴：W=3/E=3（机体宽 ±2.95，最外一圈格只被机体压住 0.45，仍然算占格）。
		return new int[] {6, 0, 5, 10, 3, 3};
	}

	@Override
	public int getOffset() {
		// 正值：核心格落在瞄准点前方（远离玩家）10 格（o = -getOffset() = -10，facing 指向玩家）→
		// 玩家瞄准的那格成为尾部接口端，机身顺着视线向前延伸，桶管口在瞄准点前方 20 格处，
		// 不会再把机器铺到玩家和准星之间
		return 10;
	}

	/**
	 * 10 个接口格，<b>模型坐标系</b>（与渲染器同相位：S=90°、E=180°、N=270°、W=0°）：
	 * 尾部端面 x=-10 那格（方块外侧面在 -10.5），5 列（z -2..2）× 2 行（y 1/3），面朝 -X。
	 * 不用 {@code rotatePorts}——它内部会烘焙 {@code -getOffset()} 位移（返回相对放置格的坐标），
	 * 而这里要的是相对核心格的坐标。
	 */
	private static final int[][] PORTS_MODEL = buildPorts();

	private static int[][] buildPorts() {
		// 尾部端面：x = -10（方块的朝外面在 -10.5），5 列（z -2..2，间隔 1 格）× 2 行（y = 1、3，格心 1.5 / 3.5）
		// 面朝 -X：模型坐标里 -X 就是 WEST（渲染器 WEST=0°，与 rotateXZ 同相位），
		// 之前写成 NORTH，端口节点会注册到方块侧面，管道/线缆自然接不上
		int[] zs = {-2, -1, 0, 1, 2};
		int[] ys = {1, 3};
		int[][] out = new int[zs.length * ys.length][4];
		int i = 0;
		for(int y : ys) {
			for(int z : zs) {
				out[i++] = new int[] {-10, y, z, ForgeDirection.WEST.ordinal()};
			}
		}
		return out;
	}

	/** 与渲染器旋转表同相位的 XZ 旋转 */
	private static int[] rotateXZ(int x, int z, ForgeDirection dir) {
		switch(dir) {
		case SOUTH: return new int[] { z, -x};
		case EAST:  return new int[] {-x, -z};
		case NORTH: return new int[] {-z,  x};
		case WEST:
		default:    return new int[] { x,  z};
		}
	}

	private static ForgeDirection rotateFace(ForgeDirection face, ForgeDirection dir) {
		int[] r = rotateXZ(face.offsetX, face.offsetZ, dir);
		for(ForgeDirection d : new ForgeDirection[] {ForgeDirection.NORTH, ForgeDirection.SOUTH, ForgeDirection.WEST, ForgeDirection.EAST}) {
			if(d.offsetX == r[0] && d.offsetZ == r[1]) return d;
		}
		return face;
	}

	/** 10 个接口相对核心格的坐标（已按机器朝向旋转），供核心 TE 计算端口用 */
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
		// 接口坐标从「相对核心」换算成「相对放置格」，与 makeExtra 的入参一致
		int[][] ports = getPortsAtCore(dir);
		for(int[] port : ports) {
			port[0] -= dir.offsetX * getOffset();
			port[2] -= dir.offsetZ * getOffset();
		}
		return ports;
	}

	@Override
	public void fillSpace(World world, int x, int y, int z, ForgeDirection dir, int o) {
		super.fillSpace(world, x, y, z, dir, o);

		for(int[] port : getAllPorts(dir)) {
			this.makeExtra(world, x + port[0], y + port[1], z + port[2]);
		}

		// 核心格 TE 通知客户端，否则 TESR 拿不到渲染点（同仿星器）
		int cx = x + dir.offsetX * o;
		int cy = y + dir.offsetY * o;
		int cz = z + dir.offsetZ * o;
		world.markBlockForUpdate(cx, cy, cz);
	}

	@Override
	public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player, int side, float hitX, float hitY, float hitZ) {

		if(world.isRemote) return true;

		int[] pos = this.findCore(world, x, y, z);
		if(pos == null) return false;

		FMLNetworkHandler.openGui(player, MakeNTMGreatAgain.instance, MakeNTMGreatAgain.GUI_PLASMA_HEATER,
				world, pos[0], pos[1], pos[2]);

		return true;
	}

	@Override
	public TileEntity createNewTileEntity(World world, int meta) {
		if(meta >= 12) return new TileEntityPlasmaHeater();
		// 接口格：电、流体、物品都要能透传到核心
		if(hasExtra(meta)) return new TileEntityProxyCombo().inventory().power().fluid();
		return null;
	}

	@Override
	public void addInformation(ItemStack stack, EntityPlayer player, List list, boolean ext) {
	}
}
