package com.makentmgreatagain.blocks;

import java.util.ArrayList;
import java.util.List;

import com.hbm.blocks.BlockDummyable;
import com.hbm.blocks.IPersistentInfoProvider;
import com.hbm.handler.MultiblockHandlerXR;
import com.hbm.inventory.fluid.FluidType;
import com.hbm.inventory.fluid.Fluids;
import com.hbm.inventory.fluid.tank.FluidTank;
import com.hbm.items.machine.IItemFluidIdentifier;
import com.hbm.main.MainRegistry;
import com.hbm.tileentity.IPersistentNBT;
import com.hbm.tileentity.TileEntityProxyCombo;
import com.makentmgreatagain.tileentity.TileEntityBat9000Reborn;

import cpw.mods.fml.common.network.internal.FMLNetworkHandler;
import net.minecraft.block.material.Material;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.ChatStyle;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

/**
 * 「巨尻-9000」储罐（重生版）。
 *
 * 逐行复制自 HBM 已 @Deprecated 的 com.hbm.blocks.machine.MachineBigAssTank9000，
 * 结构（5×5×5，占地 5x5、高 4）与端口布局完全一致。
 *
 * 与原版的差异只有三处：
 *   1. 方块实体换成本包的 TileEntityBat9000Reborn；
 *   2. openGui 仍走 HBM 的 MainRegistry.instance —— HBM 的 GUIHandler 按 IGUIProvider
 *      接口通用分发，储罐界面（ContainerBarrel / GUIBarrel）无需在本包重写一份；
 *   3. 渲染绑定本包 resources 里的模型/贴图副本（见 RenderBat9000Reborn），
 *      这样附属包 jar 不再依赖 assets/hbm 里的遗留资源是否存在。
 */
public class BlockBat9000Reborn extends BlockDummyable implements IPersistentInfoProvider {

	public BlockBat9000Reborn() {
		super(Material.iron);
	}

	@Override
	public TileEntity createNewTileEntity(World world, int meta) {

		if(meta >= 12) return new TileEntityBat9000Reborn();
		if(meta >= 6) return new TileEntityProxyCombo(false, false, true);
		return null;
	}

	@Override
	public int[] getDimensions() {
		return new int[] {4, 0, 2, 2, 1, 1};
	}

	/**
	 * 幽灵预览（手持本方块时的绿色线框）只画这里返回的框。
	 *
	 * 原版 MachineBigAssTank9000 漏了这个重写，基类只给主框，于是预览比实物的
	 * 5x5 罐体小一圈 —— 就是「体积严重不符」的成因。三块与 checkRequirement 逐一对应。
	 */
	@Override
	public int[][] getAllDimensions() {
		return new int[][] {
			new int[] {4, 0, 2, 2, 1, 1},
			new int[] {4, 0, 1, 1, 2, -2},
			new int[] {4, 0, 1, 1, -2, 2}
		};
	}

	@Override
	public int getOffset() {
		return 2;
	}

	/**
	 * 黄色端口提示框的数据源。
	 *
	 * 原版 9000 也没重写它（基类返回空表），所以以前只有管道能连、没有任何端口标记。
	 * 下面 8 条与 TileEntityBat9000Reborn.getPorts() 的端口一一对应；这组
	 * {(±1,±2),(±2,±1)} 坐标集在 90° 旋转与镜像下都封闭，因此 rotatePorts 无论
	 * 按哪种旋转约定展开，结果都落在真实端口格上、面朝向外。
	 */
	@Override
	public int[][] getAllPorts(ForgeDirection dir) {
		return rotatePorts(new int[][] {
			{ 1, 0,  2, ForgeDirection.SOUTH.ordinal()},
			{-1, 0,  2, ForgeDirection.SOUTH.ordinal()},
			{ 1, 0, -2, ForgeDirection.NORTH.ordinal()},
			{-1, 0, -2, ForgeDirection.NORTH.ordinal()},
			{ 2, 0,  1, ForgeDirection.EAST.ordinal()},
			{-2, 0,  1, ForgeDirection.WEST.ordinal()},
			{ 2, 0, -1, ForgeDirection.EAST.ordinal()},
			{-2, 0, -1, ForgeDirection.WEST.ordinal()}
		}, dir);
	}

	@Override
	public void fillSpace(World world, int x, int y, int z, ForgeDirection dir, int o) {
		super.fillSpace(world, x, y, z, dir, o);
		MultiblockHandlerXR.fillSpace(world, x + dir.offsetX * o , y + dir.offsetY * o, z + dir.offsetZ * o, new int[] {4, 0, 1, 1, 2, -2}, this, dir);
		MultiblockHandlerXR.fillSpace(world, x + dir.offsetX * o , y + dir.offsetY * o, z + dir.offsetZ * o, new int[] {4, 0, 1, 1, -2, 2}, this, dir);

		this.makeExtra(world, x + dir.offsetX * o + 1, y, z + dir.offsetZ * o + 2);
		this.makeExtra(world, x + dir.offsetX * o - 1, y, z + dir.offsetZ * o + 2);
		this.makeExtra(world, x + dir.offsetX * o + 1, y, z + dir.offsetZ * o - 2);
		this.makeExtra(world, x + dir.offsetX * o - 1, y, z + dir.offsetZ * o - 2);
		this.makeExtra(world, x + dir.offsetX * o + 2, y, z + dir.offsetZ * o + 1);
		this.makeExtra(world, x + dir.offsetX * o - 2, y, z + dir.offsetZ * o + 1);
		this.makeExtra(world, x + dir.offsetX * o + 2, y, z + dir.offsetZ * o - 1);
		this.makeExtra(world, x + dir.offsetX * o - 2, y, z + dir.offsetZ * o - 1);
	}

	@Override
	protected boolean checkRequirement(World world, int x, int y, int z, ForgeDirection dir, int o) {

		if(!MultiblockHandlerXR.checkSpace(world, x + dir.offsetX * o , y + dir.offsetY * o, z + dir.offsetZ * o, getDimensions(), x, y, z, dir)) return false;
		if(!MultiblockHandlerXR.checkSpace(world, x + dir.offsetX * o , y + dir.offsetY * o, z + dir.offsetZ * o, new int[] {4, 0, 1, 1, 2, -2}, x, y, z, dir)) return false;
		if(!MultiblockHandlerXR.checkSpace(world, x + dir.offsetX * o , y + dir.offsetY * o, z + dir.offsetZ * o, new int[] {4, 0, 1, 1, -2, 2}, x, y, z, dir)) return false;

		return true;
	}

	@Override
	public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player, int side, float hitX, float hitY, float hitZ) {

		if(world.isRemote) {
			return true;
		} else if(!player.isSneaking()) {

			int[] pos = this.findCore(world, x, y, z);

			if(pos == null)
				return false;

			FMLNetworkHandler.openGui(player, MainRegistry.instance, 0, world, pos[0], pos[1], pos[2]);
			return true;
		} else if(player.isSneaking()){
			int[] pos = this.findCore(world, x, y, z);

			if(pos == null)
				return false;

			TileEntityBat9000Reborn trialEntity = (TileEntityBat9000Reborn) world.getTileEntity(pos[0], pos[1], pos[2]);

			if(trialEntity != null) {
				if(player.getHeldItem() != null && player.getHeldItem().getItem() instanceof IItemFluidIdentifier) {
					FluidType type = ((IItemFluidIdentifier) player.getHeldItem().getItem()).getType(world, pos[0], pos[1], pos[2], player.getHeldItem());

					trialEntity.tank.setTankType(type);
					trialEntity.markDirty();
					player.addChatComponentMessage(new ChatComponentText("Changed type to ").setChatStyle(new ChatStyle().setColor(EnumChatFormatting.YELLOW)).appendSibling(new ChatComponentTranslation(type.getConditionalName())).appendSibling(new ChatComponentText("!")));
				}
			}

			return true;
		} else {
			return true;
		}
	}

	@Override
	public ArrayList<ItemStack> getDrops(World world, int x, int y, int z, int metadata, int fortune) {
		return IPersistentNBT.getDrops(world, x, y, z, this);
	}

	@Override
	public boolean hasComparatorInputOverride() {
		return true;
	}

	@Override
	public int getComparatorInputOverride(World world, int x, int y, int z, int side) {

		int meta = world.getBlockMetadata(x, y, z);

		if(meta >= 6) {
			int[] pos = this.findCore(world, x, y, z);
			if(pos == null) return 0;
			TileEntity te = world.getTileEntity(pos[0], pos[1], pos[2]);

			if(!(te instanceof TileEntityBat9000Reborn))
				return 0;

			TileEntityBat9000Reborn tank = (TileEntityBat9000Reborn) te;
			return tank.getComparatorPower();
		}

		return 0;
	}

	@Override
	public void addInformation(ItemStack stack, NBTTagCompound persistentTag, EntityPlayer player, List list, boolean ext) {
		FluidTank tank = new FluidTank(Fluids.NONE, 0);
		tank.readFromNBT(persistentTag, "tank");
		list.add(EnumChatFormatting.YELLOW + "" + tank.getFill() + "/" + tank.getMaxFill() + "mB " + tank.getTankType().getLocalizedName());
	}
}
