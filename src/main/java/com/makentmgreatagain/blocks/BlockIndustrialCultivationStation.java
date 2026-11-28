package com.makentmgreatagain.blocks;

import com.hbm.blocks.BlockDummyable;
import com.hbm.tileentity.TileEntityProxyCombo;
import com.makentmgreatagain.MakeNTMGreatAgain;
import com.makentmgreatagain.tileentity.TileEntityIndustrialCultivationStation;

import cpw.mods.fml.common.network.internal.FMLNetworkHandler;
import net.minecraft.block.material.Material;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

/**
 * 工业种植站（7×7×5 多方块结构，支持 6 个标准 HBM 端口：电力输入、流体输入/输出、物品输入/输出）。
 *
 * <p>模型坐标系（默认朝南 SOUTH 时）：</p>
 * <ul>
 *   <li>正面 (+Z)：(-2, 0, +3) 流体口、(0, 0, +3) 物品口</li>
 *   <li>背面 (-Z)：(-2, 0, -3) 流体口、(0, 0, -3) 电力口</li>
 *   <li>左侧 (-X)：(-3, 0, +2) 流体口、(-3, 0, -2) 物品口</li>
 * </ul>
 */
public class BlockIndustrialCultivationStation extends BlockDummyable {

	/** 6 个外置标准接口（模型空间坐标：相对核心 {dx, dy, dz, faceOrdinal}，SOUTH 为 0° 基准） */
	private static final int[][] ALL_PORTS_MODEL = {
		{ -2, 0,  3, ForgeDirection.SOUTH.ordinal() }, // 正面左：流体
		{  0, 0,  3, ForgeDirection.SOUTH.ordinal() }, // 正面中：物品
		{ -2, 0, -3, ForgeDirection.NORTH.ordinal() }, // 背面左：流体
		{  0, 0, -3, ForgeDirection.NORTH.ordinal() }, // 背面中：电力
		{ -3, 0,  2, ForgeDirection.WEST.ordinal()  }, // 左侧前：流体
		{ -3, 0, -2, ForgeDirection.WEST.ordinal()  }  // 左侧后：物品
	};

	public BlockIndustrialCultivationStation() {
		super(Material.iron);
		this.bounding.add(AxisAlignedBB.getBoundingBox(-3.5D, 0.0D, -3.5D, 3.5D, 5.0D, 3.5D));
	}

	@Override
	public int[] getDimensions() {
		// {上, 下, 北, 南, 西, 东} -> 7×7×5
		return new int[] { 4, 0, 3, 3, 3, 3 };
	}

	@Override
	public int getOffset() {
		return 3;
	}

	/** 与 RenderIndustrialCultivationStation 中的 glRotatef 严格一致的旋转变换 */
	private static int[] rotateModelXZ(int mx, int mz, ForgeDirection dir) {
		switch(dir) {
		case EAST:  return new int[] {  mz, -mx }; // 90°
		case NORTH: return new int[] { -mx, -mz }; // 180°
		case WEST:  return new int[] { -mz,  mx }; // 270°
		case SOUTH:
		default:    return new int[] {  mx,  mz }; // 0°
		}
	}

	private static ForgeDirection rotateModelFace(ForgeDirection face, ForgeDirection dir) {
		int[] r = rotateModelXZ(face.offsetX, face.offsetZ, dir);
		for(ForgeDirection d : new ForgeDirection[] {
				ForgeDirection.NORTH, ForgeDirection.SOUTH, ForgeDirection.WEST, ForgeDirection.EAST }) {
			if(d.offsetX == r[0] && d.offsetZ == r[1]) return d;
		}
		return face;
	}

	/** 返回相对于核心方块坐标 (xCoord, yCoord, zCoord) 的 6 个端口坐标与朝向 */
	public int[][] getPortsAtCore(ForgeDirection dir) {
		int[][] out = new int[ALL_PORTS_MODEL.length][4];
		for(int i = 0; i < ALL_PORTS_MODEL.length; i++) {
			int[] p = rotateModelXZ(ALL_PORTS_MODEL[i][0], ALL_PORTS_MODEL[i][2], dir);
			ForgeDirection face = rotateModelFace(ForgeDirection.getOrientation(ALL_PORTS_MODEL[i][3]), dir);
			out[i] = new int[] { p[0], ALL_PORTS_MODEL[i][1], p[1], face.ordinal() };
		}
		return out;
	}

	@Override
	public int[][] getAllPorts(ForgeDirection dir) {
		int[][] corePorts = getPortsAtCore(dir);
		int[][] out = new int[corePorts.length][4];
		int o = getOffset();
		for(int i = 0; i < corePorts.length; i++) {
			out[i] = new int[] {
				corePorts[i][0] - dir.offsetX * o,
				corePorts[i][1],
				corePorts[i][2] - dir.offsetZ * o,
				corePorts[i][3]
			};
		}
		return out;
	}

	@Override
	public void fillSpace(World world, int x, int y, int z, ForgeDirection dir, int o) {
		super.fillSpace(world, x, y, z, dir, o);

		for(int[] port : getAllPorts(dir)) {
			this.makeExtra(world, x + port[0], y + port[1], z + port[2]);
		}

		int cx = x + dir.offsetX * o;
		int cy = y + dir.offsetY * o;
		int cz = z + dir.offsetZ * o;
		world.markBlockForUpdate(cx, cy, cz);
	}

	@Override
	public TileEntity createNewTileEntity(World world, int meta) {
		if(meta >= 12) return new TileEntityIndustrialCultivationStation();
		if(hasExtra(meta)) return new TileEntityProxyCombo().inventory().power().fluid();
		return null;
	}

	@Override
	public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player,
			int side, float hitX, float hitY, float hitZ) {
		if(world.isRemote) return true;

		int[] pos = this.findCore(world, x, y, z);
		if(pos == null) return false;

		FMLNetworkHandler.openGui(player, MakeNTMGreatAgain.instance,
				MakeNTMGreatAgain.GUI_INDUSTRIAL_CULTIVATION_STATION, world, pos[0], pos[1], pos[2]);
		return true;
	}
}
