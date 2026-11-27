package com.makentmgreatagain.blocks;

import com.hbm.blocks.BlockDummyable;
import com.hbm.tileentity.TileEntityProxyCombo;
import com.makentmgreatagain.tileentity.TileEntityLargeThermalPowerStation;
import com.makentmgreatagain.tileentity.TileEntityLargeThermalPowerStationPort;

import net.minecraft.block.material.Material;
import net.minecraft.entity.player.EntityPlayer;
import cpw.mods.fml.common.network.internal.FMLNetworkHandler;
import com.makentmgreatagain.MakeNTMGreatAgain;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

/**
 * 大型火力发电站：6×4×5 外观多方块。
 *
 * <p>模型沿 Z 轴长 6 格、X 轴宽 4 格、Y 轴高 5 格（底部 3 格厂房 + 顶部 2 格烟囱，
 * 烟囱不参与结构填充与碰撞）。</p>
 *
 * <p>两个长侧面各 2 个接口，位于最底层、Z 轴 −2 与 1 两格（沿长边数第 2、5 格，
 * 即方块中心，与模型上的接口图标严格同格）：
 * 每个接口由专用代理 TE 暴露物品、流体输入和电力输出接口；实际资源转发依赖核心
 * TE 实现相应库存、流体发送/接收与电力 provider 接口。右键打开操作 GUI。</p>
 */
public class BlockLargeThermalPowerStation extends BlockDummyable {

	public BlockLargeThermalPowerStation() {
		super(Material.iron);
	}

	@Override
	public int[] getDimensions() {
		// {上, 下, 北, 南, 西, 东}：宽 4 格、长 6 格、高 3 格
		// 模型 Z 覆盖 −3.5…2.5，与核心格（0）不对称，故北 3、南 2
		return new int[] {2, 0, 3, 2, 1, 2};
	}

	@Override
	public int getOffset() {
		// 点击正面（南侧）最外一列时，核心格在点击格向里 2 格处
		return 2;
	}

	/**
	 * 4 个接口相对核心格的偏移（朝南时）：x = −1 与 2 两个长侧面，
	 * z = −2 与 1 两格，全部在最底层 y = 0。
	 */
	private static final int[][] PORTS_MODEL = {
			{ 2, 0, -2, ForgeDirection.EAST.ordinal()},
			{ 2, 0,  1, ForgeDirection.EAST.ordinal()},
			{-1, 0, -2, ForgeDirection.WEST.ordinal()},
			{-1, 0,  1, ForgeDirection.WEST.ordinal()},
	};

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

	/** 4 个接口相对核心格的坐标（已按机器朝向旋转）。 */
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

	@Override
	public void fillSpace(World world, int x, int y, int z, ForgeDirection dir, int o) {
		super.fillSpace(world, x, y, z, dir, o);

		// 端口格必须显式写成 extra+朝向 的元数据，createNewTileEntity 才会生成代理 TE
		int cx = x + dir.offsetX * o, cy = y + dir.offsetY * o, cz = z + dir.offsetZ * o;
		for(int[] port : getPortsAtCore(dir)) {
			BlockDummyable.safeRem = true;
			world.setBlock(cx + port[0], cy + port[1], cz + port[2], this, extra + port[3], 3);
			BlockDummyable.safeRem = false;
		}
	}

	@Override
	public TileEntity createNewTileEntity(World world, int meta) {
		if(meta >= 12) return new TileEntityLargeThermalPowerStation();
		// 接口格：物品 + 流体接收代理，以及电力/流体发送桥接
		if(hasExtra(meta)) return new TileEntityLargeThermalPowerStationPort();
		return null;
	}

	@Override
	public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player, int side, float hitX, float hitY, float hitZ) {
		if(world.isRemote) return true;
		int[] core = findCore(world, x, y, z);
		if(core == null || !(world.getTileEntity(core[0], core[1], core[2]) instanceof TileEntityLargeThermalPowerStation)) return false;
		FMLNetworkHandler.openGui(player, MakeNTMGreatAgain.instance,
				MakeNTMGreatAgain.GUI_LARGE_THERMAL_POWER_STATION, world, core[0], core[1], core[2]);
		return true;
	}
}
