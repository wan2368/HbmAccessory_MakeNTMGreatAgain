package com.makentmgreatagain.blocks;

import java.util.Random;

import com.makentmgreatagain.MakeNTMGreatAgain;
import com.makentmgreatagain.tileentity.TileEntityElectricBoiler;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.block.Block;
import net.minecraft.block.BlockContainer;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.IIcon;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;

/**
 * 电锅炉方块（off/on 双态，完整移植自 HBM 1.0.27 X4775 的 MachineBoiler 电版分支）。
 *
 * <p>贴图沿用本体 hbm 域：正面 machine_boiler_electric_front(_lit)、
 * 顶面 machine_boiler_port、侧面 machine_boiler_side。
 * on 态亮度 1.0、冒红石粒子；off 态挂机器标签页。</p>
 *
 * <p>修正了原版 onBlockPlacedBy 把命名 TE 强转燃煤版的 ClassCastException 隐患。</p>
 */
public class BlockElectricBoiler extends BlockContainer {

	private final Random rand = new Random();
	private final boolean isActive;
	private static boolean keepInventory;

	@SideOnly(Side.CLIENT) private IIcon iconFront;
	@SideOnly(Side.CLIENT) private IIcon iconSide;

	public BlockElectricBoiler(boolean blockState) {
		super(Material.iron);
		this.isActive = blockState;
	}

	@Override
	@SideOnly(Side.CLIENT)
	public void registerBlockIcons(IIconRegister iconRegister) {
		
		this.iconFront = iconRegister.registerIcon("makentmgreatagain" + (this.isActive ? ":machine_boiler_electric_front_lit" : ":machine_boiler_electric_front"));
		this.blockIcon = iconRegister.registerIcon("makentmgreatagain:machine_boiler_port");
		this.iconSide = iconRegister.registerIcon("makentmgreatagain:machine_boiler_side");
	}

	@Override
	@SideOnly(Side.CLIENT)
	public IIcon getIcon(int side, int metadata) {
		if(side == 0 || side == 1) return this.blockIcon;
		return (metadata == 0 && side == 3) ? this.iconFront : ((side == metadata) ? this.iconFront : this.iconSide);
	}

	@Override
	public Item getItemDropped(int meta, Random random, int fortune) {
		return Item.getItemFromBlock(com.makentmgreatagain.init.ModBlocks.machine_electric_boiler_off);
	}

	@Override
	public void onBlockAdded(World world, int x, int y, int z) {
		super.onBlockAdded(world, x, y, z);
		this.setDefaultDirection(world, x, y, z);
	}

	private void setDefaultDirection(World world, int x, int y, int z) {
		if(!world.isRemote) {
			Block block1 = world.getBlock(x, y, z - 1);
			Block block2 = world.getBlock(x, y, z + 1);
			Block block3 = world.getBlock(x - 1, y, z);
			Block block4 = world.getBlock(x + 1, y, z);

			byte b0 = 3;
			if(block1.isOpaqueCube() && !block2.isOpaqueCube()) b0 = 3;
			if(block2.isOpaqueCube() && !block1.isOpaqueCube()) b0 = 2;
			if(block3.isOpaqueCube() && !block4.isOpaqueCube()) b0 = 5;
			if(block4.isOpaqueCube() && !block3.isOpaqueCube()) b0 = 4;

			world.setBlockMetadataWithNotify(x, y, z, b0, 2);
		}
	}

	@Override
	public void onBlockPlacedBy(World world, int x, int y, int z, EntityLivingBase player, ItemStack stack) {
		int i = MathHelper.floor_double(player.rotationYaw * 4.0F / 360.0F + 0.5D) & 3;

		if(i == 0) world.setBlockMetadataWithNotify(x, y, z, 2, 2);
		if(i == 1) world.setBlockMetadataWithNotify(x, y, z, 5, 2);
		if(i == 2) world.setBlockMetadataWithNotify(x, y, z, 3, 2);
		if(i == 3) world.setBlockMetadataWithNotify(x, y, z, 4, 2);

		if(stack.hasDisplayName()) {
			TileEntity te = world.getTileEntity(x, y, z);
			if(te instanceof TileEntityElectricBoiler) ((TileEntityElectricBoiler) te).setCustomName(stack.getDisplayName());
		}
	}

	@Override
	public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player, int side, float hitX, float hitY, float hitZ) {
		if(world.isRemote) return true;
		if(!player.isSneaking()) {
			player.openGui(MakeNTMGreatAgain.instance, MakeNTMGreatAgain.GUI_ELECTRIC_BOILER, world, x, y, z);
			return true;
		}
		return false;
	}

	@Override
	public TileEntity createNewTileEntity(World world, int meta) {
		return new TileEntityElectricBoiler();
	}

	/** off/on 切换保留库存与朝向（原版同款） */
	public static void updateBlockState(boolean isProcessing, World world, int x, int y, int z) {
		int meta = world.getBlockMetadata(x, y, z);
		TileEntity entity = world.getTileEntity(x, y, z);
		keepInventory = true;

		if(isProcessing) world.setBlock(x, y, z, com.makentmgreatagain.init.ModBlocks.machine_electric_boiler_on);
		else world.setBlock(x, y, z, com.makentmgreatagain.init.ModBlocks.machine_electric_boiler_off);

		keepInventory = false;
		world.setBlockMetadataWithNotify(x, y, z, meta, 3);

		if(entity != null) {
			entity.validate();
			world.setTileEntity(x, y, z, entity);
		}
	}

	@Override
	public void breakBlock(World world, int x, int y, int z, Block block, int meta) {
		if(!keepInventory) {
			TileEntity te = world.getTileEntity(x, y, z);
			if(te instanceof TileEntityElectricBoiler) {
				TileEntityElectricBoiler boiler = (TileEntityElectricBoiler) te;
				for(int i = 0; i < boiler.getSizeInventory(); i++) {
					ItemStack stack = boiler.getStackInSlot(i);
					if(stack != null) {
						float f = this.rand.nextFloat() * 0.8F + 0.1F;
						float f1 = this.rand.nextFloat() * 0.8F + 0.1F;
						float f2 = this.rand.nextFloat() * 0.8F + 0.1F;

						while(stack.stackSize > 0) {
							int j = this.rand.nextInt(21) + 10;
							if(j > stack.stackSize) j = stack.stackSize;
							stack.stackSize -= j;

							EntityItem entityitem = new EntityItem(world, x + f, y + f1, z + f2, new ItemStack(stack.getItem(), j, stack.getItemDamage()));
							if(stack.hasTagCompound()) entityitem.getEntityItem().setTagCompound((NBTTagCompound) stack.getTagCompound().copy());

							float f3 = 0.05F;
							entityitem.motionX = (float) this.rand.nextGaussian() * f3;
							entityitem.motionY = (float) this.rand.nextGaussian() * f3 + 0.2F;
							entityitem.motionZ = (float) this.rand.nextGaussian() * f3;
							world.spawnEntityInWorld(entityitem);
						}
					}
				}
				world.func_147453_f(x, y, z, block);
			}
		}
		super.breakBlock(world, x, y, z, block, meta);
	}

	@Override
	@SideOnly(Side.CLIENT)
	public void randomDisplayTick(World world, int x, int y, int z, Random rand) {
		if(this.isActive) {
			int l = world.getBlockMetadata(x, y, z);
			float f = x + 0.5F;
			float f1 = y + 0.25F + rand.nextFloat() * 6.0F / 16.0F;
			float f2 = z + 0.5F;
			float f3 = 0.52F;
			float f4 = rand.nextFloat() * 0.6F - 0.3F;

			if(l == 4) world.spawnParticle("reddust", f - f3, f1, f2 + f4, 0.0D, 0.0D, 0.0D);
			else if(l == 5) world.spawnParticle("reddust", f + f3, f1, f2 + f4, 0.0D, 0.0D, 0.0D);
			else if(l == 2) world.spawnParticle("reddust", f + f4, f1, f2 - f3, 0.0D, 0.0D, 0.0D);
			else if(l == 3) world.spawnParticle("reddust", f + f4, f1, f2 + f3, 0.0D, 0.0D, 0.0D);
		}
	}
}
