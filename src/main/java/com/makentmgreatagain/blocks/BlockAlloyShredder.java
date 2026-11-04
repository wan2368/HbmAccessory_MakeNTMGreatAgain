package com.makentmgreatagain.blocks;

import com.hbm.blocks.BlockDummyable;
import com.hbm.tileentity.TileEntityProxyCombo;
import com.makentmgreatagain.MakeNTMGreatAgain;
import com.makentmgreatagain.render.IconSub;
import com.makentmgreatagain.tileentity.TileEntityAlloyShredder;

import cpw.mods.fml.common.network.internal.FMLNetworkHandler;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.IIcon;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

public class BlockAlloyShredder extends BlockDummyable {

	@SideOnly(Side.CLIENT)
	private IIcon iconTop;
	@SideOnly(Side.CLIENT)
	private IIcon iconBottom;
	@SideOnly(Side.CLIENT)
	private IIcon iconSideUpper;   // 所有侧面上半
	@SideOnly(Side.CLIENT)
	private IIcon iconLowerA;      // 前/后 下半（side_down）
	@SideOnly(Side.CLIENT)
	private IIcon iconLowerB;      // 左/右 下半（side2_down）
	/** 顶/底 32×32 大图切成的 2×2 个 16×16 子块：[row][col]，row0=上排 */
	@SideOnly(Side.CLIENT)
	private IIcon[] iconTopSub = new IIcon[4];
	@SideOnly(Side.CLIENT)
	private IIcon[] iconBottomSub = new IIcon[4];

	public BlockAlloyShredder() {
		super(Material.iron);
	}

	@Override
	public TileEntity createNewTileEntity(World world, int meta) {
		// 照搬电池座：核心 >=12；端口格(6~11)挂三重代理
		if(meta >= 12) return new TileEntityAlloyShredder();
		if(meta >= 6) return new TileEntityProxyCombo().inventory().power().conductor();
		return null;
	}
	@Override
	public int[] getDimensions() {
		return new int[] {1, 0, 1, 0, 1, 0};
	}

	@Override
	public int getOffset() {
		return 0;
	}
	
	@Override
	public int[][] getAllPorts(ForgeDirection dir) {
		return rotatePorts(new int[][] {
			{1, 0, 0, ForgeDirection.SOUTH.ordinal()},
			{0, 0, 0, ForgeDirection.SOUTH.ordinal()},
			{0, 0, -1, ForgeDirection.NORTH.ordinal()},
			{1, 0, -1, ForgeDirection.NORTH.ordinal()},
			{1, 0, 0, ForgeDirection.EAST.ordinal()},
			{1, 0, -1, ForgeDirection.EAST.ordinal()},
			{0, 0, 0, ForgeDirection.WEST.ordinal()},
			{0, 0, -1, ForgeDirection.WEST.ordinal()}
		}, dir);
	}

	@Override
	protected void fillSpace(World world, int x, int y, int z, ForgeDirection dir, int o) {
		super.fillSpace(world, x, y, z, dir, o);

		for(int[] offset : getAllPorts(dir)) {
			this.makeExtra(world, x + offset[0], y + offset[1], z + offset[2]);
		}
	}
	@Override
	public int getRenderType() { return 0; }
	@Override
	public boolean isOpaqueCube() { return true; }
	@Override
	public boolean renderAsNormalBlock() { return true; }

	@Override
	@SideOnly(Side.CLIENT)
	public void registerBlockIcons(IIconRegister reg) {
		this.iconTop = reg.registerIcon("makentmgreatagain:machines/machine_advanced_shredder_top_alt");
		this.iconBottom = reg.registerIcon("makentmgreatagain:machines/machine_advanced_shredder_bottom");
		this.iconSideUpper = reg.registerIcon("makentmgreatagain:machines/machine_advanced_shredder_side_top");
		this.iconLowerA = reg.registerIcon("makentmgreatagain:machines/machine_advanced_shredder_side_down");
		this.iconLowerB = reg.registerIcon("makentmgreatagain:machines/machine_advanced_shredder_side2_down");
		
		for(int i = 0; i < 4; i++) {
			this.iconTopSub[i]    = new IconSub(this.iconTop,    i % 2, i / 2, TOP_FLIP_U[i], TOP_FLIP_V[i]);
			this.iconBottomSub[i] = new IconSub(this.iconBottom, i % 2, i / 2, BOT_FLIP_U[i], BOT_FLIP_V[i]);
		}
	}
	
	private static final boolean[] TOP_FLIP_U = {false, false, false, false};
	private static final boolean[] TOP_FLIP_V = {false, false, false, false};
	private static final boolean[] BOT_FLIP_U = {false, false, false, false};
	private static final boolean[] BOT_FLIP_V = {false, false, false, false};

	/** 物品/无世界上下文时的兜底 */
	@Override
	@SideOnly(Side.CLIENT)
	public IIcon getIcon(int side, int meta) {
		if(side == 0) return this.iconBottom;
		if(side == 1) return this.iconTop;
		return this.iconSideUpper;
	}

	@Override
	@SideOnly(Side.CLIENT)
	public IIcon getIcon(IBlockAccess world, int x, int y, int z, int side) {

		int[] core = this.findCore(world, x, y, z);
		if(core == null) return this.iconSideUpper;

		int coreMeta = world.getBlockMetadata(core[0], core[1], core[2]);
		if(coreMeta < 12) return this.iconSideUpper;

		ForgeDirection dir = ForgeDirection.getOrientation(coreMeta - 10);   // 核心朝向（侧面分区用）
		ForgeDirection rot = dir.getRotation(ForgeDirection.UP);              // 机器局部"右"

		int fx = x - core[0];
		int fz = z - core[2];
		boolean upper = (y > core[1]);

		// ==== 顶/底：世界轴向定象限，图案固定 ====
		// 2×2 占地四角的 x/z 最小值 = 西北角；cellX∈{0,1} 向东，cellZ∈{0,1} 向南
		int x2 = core[0] - dir.offsetX, z2 = core[2] - dir.offsetZ;   // 核心"后"侧格
		int x3 = core[0] + rot.offsetX, z3 = core[2] + rot.offsetZ;   // 核心"右"侧格
		int x4 = x2 + rot.offsetX,     z4 = z2 + rot.offsetZ;         // 对角格
		int minX = Math.min(Math.min(core[0], x2), Math.min(x3, x4));
		int minZ = Math.min(Math.min(core[2], z2), Math.min(z3, z4));
		int dx = x - minX;
		int dz = z - minZ;
		// 本世界可能存在旧版结构残留（占地角点集不同），象限越界时回退整图，防崩溃
		if(dx < 0 || dz < 0 || dx > 1 || dz > 1) {
			if(side == 1) return this.iconTop;
			if(side == 0) return this.iconBottom;
		} else {
			int cell = dz * 2 + dx;   // 0=西北 1=东北 2=西南 3=东南（世界轴向，固定）
			if(side == 1) return this.iconTopSub[cell];
			if(side == 0) return this.iconBottomSub[cell];
		}

		// ==== 侧面：机器局部系 ====
		ForgeDirection s = ForgeDirection.getOrientation(side);
		int sf = s.offsetX * dir.offsetX + s.offsetZ * dir.offsetZ;   // 面朝向：±1=前/后
		int sr = s.offsetX * rot.offsetX + s.offsetZ * rot.offsetZ;   // 面朝向：±1=左/右

		if(upper) return this.iconSideUpper;
		if(sf != 0) return this.iconLowerA;   // 前/后 下半
		if(sr != 0) return this.iconLowerB;   // 左/右 下半
		return this.iconLowerB;
	}

	@Override
	public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player, int side, float hitX, float hitY, float hitZ) {
		if(world.isRemote) {
			return true;
		} else if(!player.isSneaking()) {
			int[] pos = this.findCore(world, x, y, z);
			if(pos == null) return false;
			FMLNetworkHandler.openGui(player, MakeNTMGreatAgain.instance, MakeNTMGreatAgain.GUI_ALLOY_SHREDDER, world, pos[0], pos[1], pos[2]);
			return true;
		} else {
			return true;
		}
	}
	@Override
	public void breakBlock(World world, int x, int y, int z, Block b, int meta) {

		if(!world.isRemote) {
			int[] core = findCore(world, x, y, z);
			if(core != null) {
				TileEntity te = world.getTileEntity(core[0], core[1], core[2]);
				if(te instanceof TileEntityAlloyShredder) {
					TileEntityAlloyShredder shredder = (TileEntityAlloyShredder) te;
					for(int i = 0; i < shredder.getSizeInventory(); i++) {
						ItemStack stack = shredder.getStackInSlot(i);
						if(stack != null) {
							dropStack(world, core[0], core[1], core[2], stack);
							shredder.setInventorySlotContents(i, null);
						}
					}
				}
			}
		}

		super.breakBlock(world, x, y, z, b, meta);
	}
	private void dropStack(World world, int x, int y, int z, ItemStack stack) {
		float f = world.rand.nextFloat() * 0.8F + 0.1F;
		float f1 = world.rand.nextFloat() * 0.8F + 0.1F;
		float f2 = world.rand.nextFloat() * 0.8F + 0.1F;

		while(stack.stackSize > 0) {
			int j1 = world.rand.nextInt(21) + 10;
			if(j1 > stack.stackSize) j1 = stack.stackSize;

			stack.stackSize -= j1;
			EntityItem ent = new EntityItem(world, x + f, y + f1, z + f2, new ItemStack(stack.getItem(), j1, stack.getItemDamage()));
			if(stack.hasTagCompound()) {
				ent.getEntityItem().setTagCompound((NBTTagCompound) stack.getTagCompound().copy());
			}
			ent.motionX = world.rand.nextGaussian() * 0.05;
			ent.motionY = world.rand.nextGaussian() * 0.05 + 0.2;
			ent.motionZ = world.rand.nextGaussian() * 0.05;
			world.spawnEntityInWorld(ent);
		}
	}
}
