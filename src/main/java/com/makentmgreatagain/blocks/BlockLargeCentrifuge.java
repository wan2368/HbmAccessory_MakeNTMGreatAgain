package com.makentmgreatagain.blocks;

import java.util.ArrayList;
import java.util.List;

import com.hbm.blocks.BlockDummyable;
import com.hbm.tileentity.TileEntityProxyCombo;
import com.makentmgreatagain.MakeNTMGreatAgain;
import com.makentmgreatagain.tileentity.TileEntityLargeCentrifuge;

import cpw.mods.fml.common.network.internal.FMLNetworkHandler;
import net.minecraft.block.material.Material;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

/**
 * 大型离心机：5×5×3 多方块。
 *
 * <p>模型与贴图直接沿用旧版回旋加速器（cyclotron）的原始导出，不改 UV、不重做模型。
 * 目前只做实装，右键不做任何交互（不开 GUI、不装部件）。</p>
 */
public class BlockLargeCentrifuge extends BlockDummyable {

	public BlockLargeCentrifuge() {
		super(Material.iron);

		// 5×5 占地、3 格高的完整碰撞/选中框，以核心格中心为基准
		this.bounding.add(AxisAlignedBB.getBoundingBox(-2.5D, 0D, -2.5D, 2.5D, 3D, 2.5D));
	}

	@Override
	public int[] getDimensions() {
		// {上, 下, +X, -X, +Z, -Z}
		return new int[] {2, 0, 2, 2, 2, 2};
	}

	@Override
	public int getOffset() {
		return 2;
	}

	/** 接口相对核心的偏移：四个侧面中间层各 3 个，共 12 个 */
	public static List<int[]> getPortOffsets(ForgeDirection dir) {

		ForgeDirection rot = dir.getRotation(ForgeDirection.DOWN);
		List<int[]> ports = new ArrayList<int[]>();

		for(int i = -1; i <= 1; i++) {
			ports.add(new int[] {rot.offsetX * i + dir.offsetX * 2, 0, rot.offsetZ * i + dir.offsetZ * 2, dir.ordinal()});
			ports.add(new int[] {rot.offsetX * i - dir.offsetX * 2, 0, rot.offsetZ * i - dir.offsetZ * 2, dir.getOpposite().ordinal()});
			ports.add(new int[] {rot.offsetX * 2 + dir.offsetX * i, 0, rot.offsetZ * 2 + dir.offsetZ * i, rot.ordinal()});
			ports.add(new int[] {-rot.offsetX * 2 + dir.offsetX * i, 0, -rot.offsetZ * 2 + dir.offsetZ * i, rot.getOpposite().ordinal()});
		}

		return ports;
	}

	@Override
	public int[][] getAllPorts(ForgeDirection dir) {

		List<int[]> offsets = getPortOffsets(dir);
		int[][] ports = new int[offsets.size()][4];

		for(int i = 0; i < offsets.size(); i++) {
			int[] p = offsets.get(i);
			ports[i][0] = p[0] - dir.offsetX * getOffset();
			ports[i][1] = p[1];
			ports[i][2] = p[2] - dir.offsetZ * getOffset();
			ports[i][3] = p[3];
		}

		return ports;
	}

	@Override
	public void fillSpace(World world, int x, int y, int z, ForgeDirection dir, int o) {

		super.fillSpace(world, x, y, z, dir, o);

		for(int[] port : getAllPorts(dir)) {
			this.makeExtra(world, x + port[0], y + port[1], z + port[2]);
		}
	}

	@Override
	public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player, int side, float hitX, float hitY, float hitZ) {

		if(world.isRemote) return true;

		int[] pos = findCore(world, x, y, z);
		if(pos == null) return false;

		FMLNetworkHandler.openGui(player, MakeNTMGreatAgain.instance, MakeNTMGreatAgain.GUI_LARGE_CENTRIFUGE, world, pos[0], pos[1], pos[2]);
		return true;
	}

	@Override
	public TileEntity createNewTileEntity(World world, int meta) {

		if(meta >= 12) return new TileEntityLargeCentrifuge();
		// 接口格：物品 + 电力 + 流体三合一
		if(meta >= 6) return new TileEntityProxyCombo().inventory().power().fluid();

		return null;
	}
}
