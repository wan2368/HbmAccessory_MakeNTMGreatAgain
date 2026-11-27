package com.makentmgreatagain.blocks;

import java.util.List;
import java.util.Random;

import com.hbm.handler.radiation.ChunkRadiationManager;
import com.makentmgreatagain.ModCreativeTabs;
import com.makentmgreatagain.init.ModItems;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.EntityFX;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.IIcon;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

/**
 * 稳定X晶体簇（5 阶段：
 * meta 0 = 完全体，
 * meta 1 = 幼苗期（橙），
 * meta 2 = 成长期 I（橙黄），
 * meta 3 = 成长期 II（黄），
 * meta 4 = 临熟期（等待喂食亚稳态X晶体））。
 */
public class BlockXenCrystal extends Block {

	public static final int META_MATURE = 0;
	public static final int META_SEED = 1;
	public static final int META_GROW_1 = 2;
	public static final int META_GROW_2 = 3;
	public static final int META_READY = 4;
	public static final float REQUIRED_RADIATION = 600.0F;
	/** 每个吸收阶段（幼苗 → 成长 I → 成长 II → 临熟）所需累计吸收的辐射剂量 */
	public static final float STAGE_ABSORB_DOSE = 2000.0F;

	@SideOnly(Side.CLIENT) public static IIcon iconCrystal;
	@SideOnly(Side.CLIENT) public static IIcon iconStage1;
	@SideOnly(Side.CLIENT) public static IIcon iconStage2;
	@SideOnly(Side.CLIENT) public static IIcon iconStage3;
	@SideOnly(Side.CLIENT) public static IIcon iconStage4;

	public BlockXenCrystal() {
		super(Material.rock);

		this.setBlockName("xen_crystal_cluster");
		this.setHardness(1.2F);
		this.setResistance(0.5F);
		this.setStepSound(soundTypeGlass);
		this.setHarvestLevel("pickaxe", 3);
		this.setBlockBounds(0.16F, 0.0F, 0.16F, 0.84F, 0.90F, 0.84F);
		this.setCreativeTab(ModCreativeTabs.TAB_BLOCKS);
	}

	@Override
	public boolean hasTileEntity(int metadata) {
		return metadata >= META_SEED && metadata < META_READY;
	}

	@Override
	public TileEntity createTileEntity(World world, int metadata) {
		return (metadata >= META_SEED && metadata < META_READY) ? new TileEntityXenCrystal() : null;
	}

	@Override
	public int getRenderType() {
		return 1;
	}

	@Override
	public boolean isOpaqueCube() {
		return false;
	}

	@Override
	public boolean renderAsNormalBlock() {
		return false;
	}

	@Override
	@SideOnly(Side.CLIENT)
	public void registerBlockIcons(IIconRegister reg) {
		iconCrystal = reg.registerIcon("makentmgreatagain:xen_crystal");
		iconStage1 = reg.registerIcon("makentmgreatagain:xen_crystal_stage1");
		iconStage2 = reg.registerIcon("makentmgreatagain:xen_crystal_stage2");
		iconStage3 = reg.registerIcon("makentmgreatagain:xen_crystal_stage3");
		iconStage4 = reg.registerIcon("makentmgreatagain:xen_crystal_stage4");
		this.blockIcon = iconCrystal;
	}

	@Override
	@SideOnly(Side.CLIENT)
	public IIcon getIcon(int side, int meta) {
		switch(meta) {
			case META_SEED: return iconStage1 != null ? iconStage1 : iconCrystal;
			case META_GROW_1: return iconStage2 != null ? iconStage2 : iconCrystal;
			case META_GROW_2: return iconStage3 != null ? iconStage3 : iconCrystal;
			case META_READY: return iconStage4 != null ? iconStage4 : iconCrystal;
			default: return iconCrystal;
		}
	}

	@Override
	@SideOnly(Side.CLIENT)
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public void getSubBlocks(Item item, CreativeTabs tab, List list) {
		list.add(new ItemStack(item, 1, META_MATURE));
	}

	@Override
	public AxisAlignedBB getCollisionBoundingBoxFromPool(World world, int x, int y, int z) {
		return AxisAlignedBB.getBoundingBox(
			x + 0.16D, y + 0.0D, z + 0.16D,
			x + 0.84D, y + 0.90D, z + 0.84D);
	}

	@Override
	@SideOnly(Side.CLIENT)
	public AxisAlignedBB getSelectedBoundingBoxFromPool(World world, int x, int y, int z) {
		return AxisAlignedBB.getBoundingBox(
			x + 0.05D, y + 0.0D, z + 0.05D,
			x + 0.95D, y + 1.0D, z + 0.95D);
	}

	@Override
	public boolean canPlaceBlockAt(World world, int x, int y, int z) {
		return super.canPlaceBlockAt(world, x, y, z)
			&& world.isSideSolid(x, y - 1, z, ForgeDirection.UP);
	}

	@Override
	public void onNeighborBlockChange(World world, int x, int y, int z, Block neighbor) {
		if(world.isRemote) return;

		if(!world.isSideSolid(x, y - 1, z, ForgeDirection.UP)) {
			this.dropBlockAsItem(world, x, y, z, world.getBlockMetadata(x, y, z), 0);
			world.setBlockToAir(x, y, z);
		}
	}

	@Override
	public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player,
			int side, float hitX, float hitY, float hitZ) {
		int meta = world.getBlockMetadata(x, y, z);
		if(meta != META_READY) return false;

		ItemStack held = player.getCurrentEquippedItem();
		if(held == null || held.getItem() != ModItems.crystal_xen_metastable) return false;

		if(!world.isRemote) {
			if(!player.capabilities.isCreativeMode) {
				held.stackSize--;
				if(held.stackSize <= 0) {
					player.inventory.setInventorySlotContents(player.inventory.currentItem, null);
				}
			}
			world.setBlockMetadataWithNotify(x, y, z, META_MATURE, 2);
			world.removeTileEntity(x, y, z);
			world.playSoundEffect(x + 0.5D, y + 0.5D, z + 0.5D, "mob.endermen.portal", 0.8F, 1.2F);
		}
		return true;
	}

	@Override
	public Item getItemDropped(int meta, Random rand, int fortune) {
		return meta == META_MATURE ? ModItems.crystal_xen : ModItems.crystal_xen_unstable;
	}

	@Override
	public int damageDropped(int meta) {
		return 0;
	}

	@Override
	public int quantityDropped(int meta, int fortune, Random rand) {
		if(meta != META_MATURE) return 1;
		int base = 2 + rand.nextInt(3);
		int bonus = fortune > 0 ? rand.nextInt(fortune + 1) : 0;
		return base + bonus;
	}

	@Override
	public boolean canSilkHarvest(World world, EntityPlayer player, int x, int y, int z, int meta) {
		return meta == META_MATURE;
	}

	@Override
	public ItemStack getPickBlock(net.minecraft.util.MovingObjectPosition target, World world, int x, int y, int z) {
		return new ItemStack(this, 1, world.getBlockMetadata(x, y, z));
	}

	/**
	 * 种植阶段（幼苗期 → 成长期 I → 成长期 II → 临熟期）TileEntity：
	 * <ul>
	 *   <li>服务端每秒检测环境辐射并同步给客户端；meta 1~3 均需 {@code >= 600 RAD} 才会逐步吸收生长，辐射越高越快；</li>
	 *   <li>客户端仅在存在辐射时生成由外部向晶体内部汇聚的高亮蓝色威尔逊云室径迹。</li>
	 * </ul>
	 */
	public static class TileEntityXenCrystal extends TileEntity {
		private int tickCounter = 0;
		private float syncedRad = 0.0F;
		private float absorbedRad = 0.0F;

		@Override
		public void updateEntity() {
			if(worldObj == null) return;

			int meta = getBlockMetadata();
			if(meta < META_SEED || meta >= META_READY) {
				if(!worldObj.isRemote) {
					worldObj.removeTileEntity(xCoord, yCoord, zCoord);
				}
				return;
			}

			if(worldObj.isRemote) {
				if(syncedRad > 0.1F) {
					spawnClientCloudChamberTracks();
				}
				return;
			}

			tickCounter++;
			if(tickCounter < 20) return;
			tickCounter = 0;

			float rad = ChunkRadiationManager.proxy.getRadiation(worldObj, xCoord, yCoord, zCoord);
			float absorbedNow = 0.0F;
			if(rad > 0.1F) {
				// 吸收并缓慢降低周围环境辐射（20 ~ 250 RAD/s，随环境辐射提升并封顶）
				absorbedNow = Math.min(rad, Math.min(250.0F, 20.0F + rad * 0.04F));
				ChunkRadiationManager.proxy.decrementRad(worldObj, xCoord, yCoord, zCoord, absorbedNow);
			}
			if((rad > 0.1F) != (syncedRad > 0.1F) || Math.abs(rad - syncedRad) >= 10.0F) {
				syncedRad = rad;
				worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
			}

			if(rad >= REQUIRED_RADIATION) {
				absorbedRad += absorbedNow;
				markDirty();
				if(absorbedRad >= STAGE_ABSORB_DOSE) {
					absorbedRad = 0.0F;
					int nextMeta = meta + 1;
					worldObj.setBlockMetadataWithNotify(xCoord, yCoord, zCoord, nextMeta, 2);
					if(nextMeta >= META_READY) {
						worldObj.removeTileEntity(xCoord, yCoord, zCoord);
					}
				}
			}
		}

		@SideOnly(Side.CLIENT)
		private void spawnClientCloudChamberTracks() {
			Random rand = worldObj.rand;
			// 按对数曲线随辐射增加频率与单次径迹条数，上限封顶避免高辐射下掉帧
			float norm = Math.min(1.0F, (float) (Math.log10(1.0D + syncedRad) / 4.0D));
			float spawnChance = 0.05F + norm * 0.95F;
			if(rand.nextFloat() >= spawnChance) return;

			int tracks = 1 + (int) (norm * 4.0F); // 1 ~ 5 条/tick 上限
			for(int t = 0; t < tracks; t++) {
				spawnInwardTrack(rand);
			}
		}

		@SideOnly(Side.CLIENT)
		private void spawnInwardTrack(Random rand) {
			// 方向向量指向晶体内部（从外围半径 0.9~1.3 格处向晶体中心汇聚）
			double cx = xCoord + 0.5D;
			double cy = yCoord + 0.42D;
			double cz = zCoord + 0.5D;

			double dx = rand.nextGaussian();
			double dy = rand.nextGaussian() * 0.65D;
			double dz = rand.nextGaussian();
			double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
			if(len < 1.0E-4D) return;
			dx /= len;
			dy /= len;
			dz /= len;

			double radius = 0.90D + rand.nextDouble() * 0.40D;
			double sx = cx + dx * radius;
			double sy = cy + dy * radius;
			double sz = cz + dz * radius;

			// 向内速度向量
			double ix = -dx;
			double iy = -dy;
			double iz = -dz;

			int points = 9 + rand.nextInt(6);
			double step = radius / points;
			int maxAge = 10 + rand.nextInt(7);

			for(int i = 0; i < points; i++) {
				double px = sx + ix * (i * step);
				double py = sy + iy * (i * step);
				double pz = sz + iz * (i * step);
				Minecraft.getMinecraft().effectRenderer.addEffect(
					new CloudChamberParticleFX(worldObj, px, py, pz,
						ix * 0.014D, iy * 0.014D, iz * 0.014D, maxAge));
			}

			// 25% 概率在外段产生一条同步向内收束的次级电离分叉
			if(rand.nextInt(4) == 0) {
				double bx = sx + ix * (3 * step);
				double by = sy + iy * (3 * step);
				double bz = sz + iz * (3 * step);
				double sdx = ix + (rand.nextDouble() - 0.5D) * 0.7D;
				double sdy = iy + (rand.nextDouble() - 0.5D) * 0.7D;
				double sdz = iz + (rand.nextDouble() - 0.5D) * 0.7D;
				double slen = Math.sqrt(sdx * sdx + sdy * sdy + sdz * sdz);
				if(slen > 1.0E-4D) {
					sdx /= slen;
					sdy /= slen;
					sdz /= slen;
					for(int j = 1; j <= 5; j++) {
						Minecraft.getMinecraft().effectRenderer.addEffect(
							new CloudChamberParticleFX(worldObj,
								bx + sdx * (j * step),
								by + sdy * (j * step),
								bz + sdz * (j * step),
								sdx * 0.014D, sdy * 0.014D, sdz * 0.014D, maxAge - 2));
					}
				}
			}
		}

		@Override
		public void writeToNBT(NBTTagCompound nbt) {
			super.writeToNBT(nbt);
			nbt.setFloat("SyncedRad", syncedRad);
			nbt.setFloat("AbsorbedRad", absorbedRad);
		}

		@Override
		public void readFromNBT(NBTTagCompound nbt) {
			super.readFromNBT(nbt);
			syncedRad = nbt.getFloat("SyncedRad");
			absorbedRad = nbt.getFloat("AbsorbedRad");
		}

		@Override
		public Packet getDescriptionPacket() {
			NBTTagCompound nbt = new NBTTagCompound();
			nbt.setFloat("SyncedRad", syncedRad);
			return new S35PacketUpdateTileEntity(xCoord, yCoord, zCoord, 0, nbt);
		}

		@Override
		public void onDataPacket(NetworkManager net, S35PacketUpdateTileEntity pkt) {
			this.syncedRad = pkt.func_148857_g().getFloat("SyncedRad");
		}
	}

	/**
	 * 威尔逊云室高亮蓝色电离径迹粒子（全亮渲染，暗处依然清晰可见）。
	 */
	@SideOnly(Side.CLIENT)
	public static class CloudChamberParticleFX extends EntityFX {
		public CloudChamberParticleFX(World world, double x, double y, double z,
				double vx, double vy, double vz, int maxAge) {
			super(world, x, y, z, 0.0D, 0.0D, 0.0D);
			this.motionX = vx;
			this.motionY = vy;
			this.motionZ = vz;
			this.noClip = true;
			this.particleScale = 0.35F + world.rand.nextFloat() * 0.15F;
			this.particleMaxAge = Math.max(4, maxAge);
			this.particleRed = 0.25F;
			this.particleGreen = 0.85F;
			this.particleBlue = 1.00F;
			this.particleAlpha = 0.95F;
			this.setParticleTextureIndex(0);
		}

		@Override
		public int getBrightnessForRender(float partialTicks) {
			return 15728880;
		}

		@Override
		public void onUpdate() {
			this.prevPosX = this.posX;
			this.prevPosY = this.posY;
			this.prevPosZ = this.posZ;
			if(this.particleAge++ >= this.particleMaxAge) {
				this.setDead();
				return;
			}
			this.moveEntity(this.motionX, this.motionY, this.motionZ);
			this.motionX *= 0.94D;
			this.motionY *= 0.94D;
			this.motionZ *= 0.94D;
			this.particleAlpha = Math.max(0.0F, 0.95F * (1.0F - (float) this.particleAge / (float) this.particleMaxAge));
		}
	}

	/**
	 * 支持子元数据名称与图标的 ItemBlock，让 Waila/中键按阶段正确显示名称和贴图。
	 */
	public static class ItemBlockXenCrystal extends ItemBlock {
		public ItemBlockXenCrystal(Block block) {
			super(block);
			this.setHasSubtypes(true);
		}

		@Override
		public int getMetadata(int damage) {
			return damage;
		}

		@Override
		public String getUnlocalizedName(ItemStack stack) {
			int meta = stack != null ? stack.getItemDamage() : 0;
			switch(meta) {
				case META_SEED: return "tile.xen_crystal_cluster_seed";
				case META_GROW_1: return "tile.xen_crystal_cluster_grow1";
				case META_GROW_2: return "tile.xen_crystal_cluster_grow2";
				case META_READY: return "tile.xen_crystal_cluster_ready";
				default: return "tile.xen_crystal_cluster";
			}
		}

		@Override
		@SideOnly(Side.CLIENT)
		public IIcon getIconFromDamage(int meta) {
			return this.field_150939_a.getIcon(0, meta);
		}
	}
}
