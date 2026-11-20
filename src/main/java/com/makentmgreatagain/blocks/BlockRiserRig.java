package com.makentmgreatagain.blocks;

import java.util.List;

import com.hbm.blocks.BlockDummyable;
import com.hbm.blocks.ITooltipProvider;
import com.hbm.tileentity.TileEntityProxyCombo;
import com.makentmgreatagain.MakeNTMGreatAgain;
import com.makentmgreatagain.tileentity.TileEntityRiserRig;

import cpw.mods.fml.common.network.internal.FMLNetworkHandler;
import net.minecraft.block.material.Material;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

/**
 * 海面立管钻机：7×7 平台、16 格高桁架井架。
 *
 * <p>核心格位于 7×7 足迹的中心、结构最底层；模型原点就是核心格底面中心，
 * 所以渲染时不需要额外偏移。放置时通过 {@link #getOffset()} 把整体向远离玩家的
 * 方向推 3 格，使足迹边缘贴着放置格，玩家不会被卡进机器内部。</p>
 *
 * <p>接口共 6 个：±X 两面各 3 个（核心相对 z = -3 / 0 / +3），全部朝外。
 * 每个接口同时挂电力、流体与物品物流代理，因此同一格既能接电、也能接流体输入输出；
 * 核心方块实体则同时实现收电、输电、流体收发与物品库存。</p>
 */
public class BlockRiserRig extends BlockDummyable implements ITooltipProvider {

	public BlockRiserRig() {
		super(Material.iron);

		// 碰撞/选中框：自下而上每两格缩一圈，贴着井架的实际收分
		this.bounding.add(AxisAlignedBB.getBoundingBox(-3.5D, 0D, -3.5D, 3.5D, 2D, 3.5D));
		this.bounding.add(AxisAlignedBB.getBoundingBox(-3.0D, 2D, -3.0D, 3.0D, 4D, 3.0D));
		this.bounding.add(AxisAlignedBB.getBoundingBox(-2.6D, 4D, -2.6D, 2.6D, 6D, 2.6D));
		this.bounding.add(AxisAlignedBB.getBoundingBox(-2.2D, 6D, -2.2D, 2.2D, 8D, 2.2D));
		this.bounding.add(AxisAlignedBB.getBoundingBox(-1.8D, 8D, -1.8D, 1.8D, 10D, 1.8D));
		this.bounding.add(AxisAlignedBB.getBoundingBox(-1.5D, 10D, -1.5D, 1.5D, 12D, 1.5D));
		this.bounding.add(AxisAlignedBB.getBoundingBox(-1.2D, 12D, -1.2D, 1.2D, 14D, 1.2D));
		this.bounding.add(AxisAlignedBB.getBoundingBox(-1.0D, 14D, -1.0D, 1.0D, 16D, 1.0D));
	}

	@Override
	public int[] getDimensions() {
		// {上, 下, 模型+X, 模型-X, 模型-Z, 模型+Z}：核心格在底面中心，向上 15 格、四周各 3 格
		return new int[] {15, 0, 3, 3, 3, 3};
	}

	/**
	 * 放置时把结构整体向玩家这一侧挪 3 格，使 7×7 足迹的最外沿正好贴着
	 * 放置格，玩家站在旁边就处于碰撞体积边缘，不会被卡进机器内部。
	 */
	@Override
	public int getOffset() {
		return 3;
	}

	@Override
	public TileEntity createNewTileEntity(World world, int meta) {

		if(meta >= 12) return new TileEntityRiserRig();

		// 6 个接口格：电力 + 流体 + 物品物流代理（实际收发由核心的 TileEntityRiserRig 负责）
		if(hasExtra(meta)) return new TileEntityProxyCombo().inventory().power().fluid();

		return null;
	}

	@Override
	public int[][] getAllPorts(ForgeDirection dir) {
		return rotatePorts(new int[][] {
			{ 3, 0, -3, ForgeDirection.EAST.ordinal()},
			{ 3, 0,  0, ForgeDirection.EAST.ordinal()},
			{ 3, 0,  3, ForgeDirection.EAST.ordinal()},
			{-3, 0, -3, ForgeDirection.WEST.ordinal()},
			{-3, 0,  0, ForgeDirection.WEST.ordinal()},
			{-3, 0,  3, ForgeDirection.WEST.ordinal()}
		}, dir);
	}

	/**
	 * 以核心格为原点的端口坐标，供核心方块实体注册端口使用。
	 *
	 * <p>{@link #getAllPorts} 返回的是「放置格相对坐标」：{@code rotatePorts} 内部
	 * 已经把 {@code -getOffset()} 的放置偏移算进去了，而核心方块实体手里只有核心坐标，
	 * 所以这里要把这个偏移加回去。</p>
	 */
	public int[][] getPortsAtCore(ForgeDirection dir) {
		int[][] ports = getAllPorts(dir);
		int o = getOffset();
		for(int[] port : ports) {
			port[0] += dir.offsetX * o;
			port[2] += dir.offsetZ * o;
		}
		return ports;
	}

	@Override
	public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player, int side, float hitX, float hitY, float hitZ) {
		if(world.isRemote) {
			return true;
		} else if(!player.isSneaking()) {
			int[] pos = this.findCore(world, x, y, z);
			if(pos == null) return false;
			FMLNetworkHandler.openGui(player, MakeNTMGreatAgain.instance, MakeNTMGreatAgain.GUI_RISER_RIG, world, pos[0], pos[1], pos[2]);
			return true;
		}
		return true;
	}

	@Override
	public void fillSpace(World world, int x, int y, int z, ForgeDirection dir, int o) {
		super.fillSpace(world, x, y, z, dir, o);

		for(int[] port : getAllPorts(dir)) {
			this.makeExtra(world, x + port[0], y + port[1], z + port[2]);
		}
	}

	@Override
	@SuppressWarnings("unchecked")
	public void addInformation(ItemStack stack, EntityPlayer player, List list, boolean ext) {
		this.addStandardInfo(stack, player, list, ext);
	}
}
