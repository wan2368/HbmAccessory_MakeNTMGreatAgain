package com.makentmgreatagain.blocks;

import com.hbm.blocks.BlockDummyable;
import com.hbm.tileentity.TileEntityProxyCombo;
import com.makentmgreatagain.MakeNTMGreatAgain;
import com.makentmgreatagain.tileentity.TileEntityCoreLavaExtractor;

import cpw.mods.fml.common.network.internal.FMLNetworkHandler;
import net.minecraft.block.material.Material;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

/**
 * 地核熔岩开采机（5×5×6 多方块，移植自旧版 NTM 1.0.27 的开发中机器「AMS发射极」）。
 *
 * <p>接口布局照搬旧版 {@code BlockAMSEmitter.onBlockPlacedBy}：
 * 结构<b>顶层中间 1 格 + 相邻 4 格</b>共 5 个接口，每个口都是
 * 物品 / 流体 / 电力三合一（{@link TileEntityProxyCombo}），
 * 物品可进出、流体可进出、电力可输入，全部转发到核心 TE。</p>
 *
 * <p>不做 GUI：右键无交互。</p>
 */
public class BlockCoreLavaExtractor extends BlockDummyable {

	/** 结构总高（格），核心在最底层 */
	public static final int HEIGHT = 6;
	/** 水平半径（格）：5×5 足迹 */
	public static final int RADIUS = 2;

	public BlockCoreLavaExtractor() {
		super(Material.iron);

		// 5×5 占地、6 格高的完整碰撞/选中框，以核心格中心为基准
		this.bounding.add(AxisAlignedBB.getBoundingBox(-2.5D, 0D, -2.5D, 2.5D, HEIGHT, 2.5D));
	}

	@Override
	public TileEntity createNewTileEntity(World world, int meta) {

		if(meta >= 12) return new TileEntityCoreLavaExtractor();
		// 接口格：物品 + 流体 + 电力三合一
		if(meta >= extra) return new TileEntityProxyCombo().inventory().power().fluid();

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
		// 顶层中间 + 四个相邻格，共 5 个口，全部朝上
		return rotatePorts(new int[][] {
			{ 0, HEIGHT - 1,  0, ForgeDirection.UP.ordinal()},
			{ 1, HEIGHT - 1,  0, ForgeDirection.UP.ordinal()},
			{-1, HEIGHT - 1,  0, ForgeDirection.UP.ordinal()},
			{ 0, HEIGHT - 1,  1, ForgeDirection.UP.ordinal()},
			{ 0, HEIGHT - 1, -1, ForgeDirection.UP.ordinal()}
		}, dir);
	}

	@Override
	public void fillSpace(World world, int x, int y, int z, ForgeDirection dir, int o) {
		super.fillSpace(world, x, y, z, dir, o);

		for(int[] port : getAllPorts(dir)) {
			this.makeExtra(world, x + port[0], y + port[1], z + port[2]);
		}
	}

	/** 右键开操作界面（核心格定位后转发 GUI_CORE_LAVA_EXTRACTOR） */
	@Override
	public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player, int side, float hitX, float hitY, float hitZ) {

		if(world.isRemote) return true;

		int[] pos = this.findCore(world, x, y, z);
		if(pos == null) return false;

		if(!(world.getTileEntity(pos[0], pos[1], pos[2]) instanceof TileEntityCoreLavaExtractor))
			return false;

		FMLNetworkHandler.openGui(player, MakeNTMGreatAgain.instance,
				MakeNTMGreatAgain.GUI_CORE_LAVA_EXTRACTOR, world, pos[0], pos[1], pos[2]);

		return true;
	}
}
