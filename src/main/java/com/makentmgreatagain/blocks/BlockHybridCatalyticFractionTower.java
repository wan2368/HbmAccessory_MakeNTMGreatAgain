package com.makentmgreatagain.blocks;

import com.hbm.blocks.BlockDummyable;
import com.hbm.tileentity.TileEntityProxyCombo;
import com.makentmgreatagain.MakeNTMGreatAgain;
import com.makentmgreatagain.tileentity.TileEntityHybridCatalyticFractionTower;
import com.makentmgreatagain.tileentity.TileEntityHybridTowerPort;

import cpw.mods.fml.common.network.internal.FMLNetworkHandler;
import net.minecraft.block.material.Material;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

/**
 * 混合催化分馏塔（5×5×6）。
 *
 * <p>加工逻辑照抄原版分馏塔（{@code MachineFractionTower}），区别：</p>
 * <ul>
 *   <li>足迹 3×3×3 → 5×5×6，核心仍在最底层；</li>
 *   <li>流体口 4 个 → 8 个，每面 2 个，位置对应模型上的 8 个检修口；</li>
 *   <li>向上叠放时检测的是 y+6 而不是 y+3；</li>
 *   <li>右键直接开界面（槽位、流体条都做了 GUI），不再用外部流体识别码交互。</li>
 * </ul>
 */
public class BlockHybridCatalyticFractionTower extends BlockDummyable {

	/** 结构总高（格） */
	public static final int HEIGHT = 6;
	/** 水平半径（格）：5×5 足迹 */
	public static final int RADIUS = 2;

	public BlockHybridCatalyticFractionTower() {
		super(Material.iron);
	}

	@Override
	public TileEntity createNewTileEntity(World world, int meta) {

		if(meta >= 12)
			return new TileEntityHybridCatalyticFractionTower();
		if(meta >= extra)
			return new TileEntityHybridTowerPort();

		return null;
	}

	@Override
	public int[] getDimensions() {
		// {上, 下, 朝向+, 朝向-, 左, 右}
		return new int[] {HEIGHT - 1, 0, RADIUS, RADIUS, RADIUS, RADIUS};
	}

	@Override
	public int getOffset() {
		return RADIUS;
	}

	@Override
	public int[][] getAllPorts(ForgeDirection dir) {
		// 8 个流体口：四个平面各 2 个，正好对应模型上每面的 2 个检修口
		return rotatePorts(new int[][] {
			{-1, 0, -RADIUS, ForgeDirection.NORTH.ordinal()},
			{ 1, 0, -RADIUS, ForgeDirection.NORTH.ordinal()},
			{-1, 0,  RADIUS, ForgeDirection.SOUTH.ordinal()},
			{ 1, 0,  RADIUS, ForgeDirection.SOUTH.ordinal()},
			{ RADIUS, 0, -1, ForgeDirection.EAST.ordinal()},
			{ RADIUS, 0,  1, ForgeDirection.EAST.ordinal()},
			{-RADIUS, 0, -1, ForgeDirection.WEST.ordinal()},
			{-RADIUS, 0,  1, ForgeDirection.WEST.ordinal()}
		}, dir);
	}

	@Override
	public void fillSpace(World world, int x, int y, int z, ForgeDirection dir, int o) {
		super.fillSpace(world, x, y, z, dir, o);

		for(int[] offset : getAllPorts(dir)) {
			this.makeExtra(world, x + offset[0], y + offset[1], z + offset[2]);
		}
	}

	@Override
	public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player, int side, float hitX, float hitY, float hitZ) {

		if(world.isRemote) return true;

		int[] pos = this.findCore(world, x, y, z);

		if(pos == null) return false;

		if(!(world.getTileEntity(pos[0], pos[1], pos[2]) instanceof TileEntityHybridCatalyticFractionTower))
			return false;

		FMLNetworkHandler.openGui(player, MakeNTMGreatAgain.instance,
				MakeNTMGreatAgain.GUI_HYBRID_CATALYTIC_FRACTION_TOWER, world, pos[0], pos[1], pos[2]);

		return true;
	}
}
