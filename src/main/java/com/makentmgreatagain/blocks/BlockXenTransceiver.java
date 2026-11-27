package com.makentmgreatagain.blocks;

import com.makentmgreatagain.MakeNTMGreatAgain;
import com.makentmgreatagain.ModCreativeTabs;
import com.makentmgreatagain.tileentity.TileEntityXenFluidTransceiver;
import com.makentmgreatagain.tileentity.TileEntityXenItemTransceiver;

import cpw.mods.fml.common.network.internal.FMLNetworkHandler;
import net.minecraft.block.Block;
import net.minecraft.block.BlockContainer;
import net.minecraft.block.material.Material;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

/**
 * 空间传送器单方块（流体传送器 / 物品传送器共用）。
 *
 * <p>外观采用 3D OBJ 框架 + 中心悬浮旋转不稳态X晶体（{@code RenderXenTransceiver}），
 * 无需供电；右键打开对应 GUI。</p>
 */
public class BlockXenTransceiver extends BlockContainer {

	public final boolean isFluid;

	public BlockXenTransceiver(boolean isFluid) {
		super(Material.iron);
		this.isFluid = isFluid;
		this.setHardness(5.0F);
		this.setResistance(20.0F);
		this.setHarvestLevel("pickaxe", 2);
		this.setStepSound(soundTypeMetal);
		this.setBlockTextureName("minecraft:anvil_base");
		this.setCreativeTab(ModCreativeTabs.TAB_MACHINES);
	}

	@Override
	public TileEntity createNewTileEntity(World world, int meta) {
		return isFluid ? new TileEntityXenFluidTransceiver() : new TileEntityXenItemTransceiver();
	}

	@Override
	public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player,
			int side, float hitX, float hitY, float hitZ) {
		if(world.isRemote) return true;
		if(!player.isSneaking()) {
			int guiId = this.isFluid
					? MakeNTMGreatAgain.GUI_XEN_FLUID_TRANSCEIVER
					: MakeNTMGreatAgain.GUI_XEN_ITEM_TRANSCEIVER;
			FMLNetworkHandler.openGui(player, MakeNTMGreatAgain.instance, guiId, world, x, y, z);
			return true;
		}
		return false;
	}

	@Override
	public void breakBlock(World world, int x, int y, int z, Block block, int meta) {
		TileEntity te = world.getTileEntity(x, y, z);
		if(te instanceof IInventory) {
			IInventory inv = (IInventory) te;
			for(int i = 0; i < inv.getSizeInventory(); i++) {
				ItemStack stack = inv.getStackInSlot(i);
				if(stack != null) {
					EntityItem ei = new EntityItem(world, x + 0.5, y + 0.5, z + 0.5, stack.copy());
					world.spawnEntityInWorld(ei);
				}
			}
		}
		super.breakBlock(world, x, y, z, block, meta);
	}

	@Override
	public void onNeighborBlockChange(World world, int x, int y, int z, Block neighborBlock) {
		if(!world.isRemote && this.isFluid) {
			TileEntity te = world.getTileEntity(x, y, z);
			if(te instanceof TileEntityXenFluidTransceiver) {
				((TileEntityXenFluidTransceiver) te).updateConnectedMask();
			}
		}
	}

	@Override
	public int getRenderType() {
		return -1;
	}

	@Override
	public boolean isOpaqueCube() {
		return false;
	}

	@Override
	public boolean renderAsNormalBlock() {
		return false;
	}
}
