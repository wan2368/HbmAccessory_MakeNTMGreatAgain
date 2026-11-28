package com.makentmgreatagain.tileentity;

import java.util.ArrayList;
import java.util.List;

import com.hbm.blocks.BlockDummyable;
import com.hbm.blocks.ModBlocks;
import com.hbm.blocks.generic.BlockNTMFlower;
import com.hbm.blocks.generic.BlockNTMFlower.EnumFlowerType;
import com.hbm.blocks.generic.BlockTallPlant;
import com.hbm.blocks.generic.BlockTallPlant.EnumTallFlower;
import com.hbm.inventory.UpgradeManagerNT;
import com.hbm.inventory.fluid.FluidType;
import com.hbm.inventory.fluid.Fluids;
import com.hbm.inventory.fluid.tank.FluidTank;
import com.hbm.items.ModItems;
import com.hbm.items.machine.IItemFluidIdentifier;
import com.hbm.items.machine.ItemMachineUpgrade;
import com.hbm.items.machine.ItemMachineUpgrade.UpgradeType;
import com.hbm.lib.Library;
import com.hbm.tileentity.TileEntityMachineBase;
import com.hbm.tileentity.IUpgradeInfoProvider;
import com.hbm.tileentity.TilePort.PortDef;
import com.makentmgreatagain.blocks.BlockIndustrialCultivationStation;

import api.hbm.energymk2.IBatteryItem;
import api.hbm.energymk2.IEnergyReceiverMK2;
import api.hbm.fluidmk2.IFluidStandardTransceiverMK2;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.netty.buffer.ByteBuf;
import net.minecraft.block.Block;
import net.minecraft.block.BlockCactus;
import net.minecraft.block.BlockCrops;
import net.minecraft.block.BlockMushroom;
import net.minecraft.block.BlockNetherWart;
import net.minecraft.block.BlockReed;
import net.minecraft.block.BlockSapling;
import net.minecraft.block.BlockStem;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemSeedFood;
import net.minecraft.item.ItemSeeds;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.AxisAlignedBB;
import net.minecraftforge.common.EnumPlantType;
import net.minecraftforge.common.IPlantable;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.oredict.OreDictionary;

/**
 * 工业种植站 TileEntity：
 * - 6 个作物槽（每格限堆叠 1 个，自动识别原版与模组作物/向日葵/甘蔗/地狱疣/仙人掌/可可豆/蘑菇，严格禁止任何树苗与芥子柳）；
 * - 支持电力输入、HBM 流体识别码切换内部流体（限定为水 WATER 或二氧化碳 CARBONDIOXIDE）、HBM 分级肥料加速与增产、HBM 升级槽；
 * - 3×3 物品输出口，自动识别成熟产物且绝不掉落多余种子（仅产出额外作物）；
 * - 顶部补光灯与双散热风扇仅通电时工作；实时同步 6 格作物与生长进度至客户端 TESR 展示。
 */
public class TileEntityIndustrialCultivationStation extends TileEntityMachineBase
		implements IEnergyReceiverMK2, IFluidStandardTransceiverMK2, IUpgradeInfoProvider {

	public static final long MAX_POWER = 1_000_000L;
	public static final int TANK_CAPACITY = 16_000;
	public static final int MAX_PROGRESS = 600;
	public static final int MAX_FERTILIZER = 1000;
	public static final int FERTILIZER_PER_ITEM = 100;
	public static final int FERTILIZER_PER_HARVEST = 10;

	public static final int BASE_POWER_PER_TICK = 200;
	public static final int FLUID_PER_CROP_TICK = 5;

	/* ========================== 槽位索引 ========================== */
	public static final int SLOT_BATTERY = 0;
	public static final int SLOT_FLUID_ID = 1;
	public static final int SLOT_CROP_START = 2;
	public static final int CROP_COUNT = 6;
	public static final int SLOT_FERTILIZER = 8;
	public static final int SLOT_UPGRADE = 9;
	public static final int SLOT_OUTPUT_START = 10;
	public static final int OUTPUT_COUNT = 9;
	public static final int TOTAL_SLOTS = 19;

	private static final int[] ACCESSIBLE_SLOTS = {
		SLOT_FERTILIZER, 10, 11, 12, 13, 14, 15, 16, 17, 18
	};

	/** 科学限定的温室内部流体（仅通过 HBM 流体识别码切换）：水（基础灌溉） / 二氧化碳（温室气体施肥） */
	public static final FluidType[] SUPPORTED_FLUIDS = {
		Fluids.WATER,
		Fluids.CARBONDIOXIDE
	};

	public long power;
	public final FluidTank tank = new FluidTank(Fluids.WATER, TANK_CAPACITY);
	private final FluidTank[] receivingTanks = new FluidTank[] { tank };
	private final FluidTank[] sendingTanks = new FluidTank[0];

	public final int[] growthProgress = new int[CROP_COUNT];
	private final float[] fluidCostRemainder = new float[CROP_COUNT];
	public int fertilizerBuffer;
	public float fertilizerSpeed = 1.0F;
	public int fertilizerBonus = 0;
	public int activePowerCost;
	public float activeSpeedMultiplier = 1.0F;

	/** 客户端渲染用的 6 格作物快照（未打开 GUI 时也能在 3D 温室里实时显示对应作物与生长阶段） */
	public final ItemStack[] syncedCrops = new ItemStack[CROP_COUNT];

	public final UpgradeManagerNT upgradeManager = new UpgradeManagerNT(this);
	private PortDef[] cachedPorts;

	@SideOnly(Side.CLIENT)
	public float fanAngle;
	@SideOnly(Side.CLIENT)
	public float lastFanAngle;

	public TileEntityIndustrialCultivationStation() {
		super(TOTAL_SLOTS);
	}

	@Override
	public String getName() {
		return "container.industrial_cultivation_station";
	}

	public boolean isPowered() {
		return this.power > 0L;
	}

	public int getPowerScaled(int maxPixels) {
		if(MAX_POWER <= 0L) return 0;
		return (int) Math.min(maxPixels, (this.power * maxPixels) / MAX_POWER);
	}

	public float getSlotGrowthRatio(int idx) {
		if(idx < 0 || idx >= CROP_COUNT) return 0.0F;
		return Math.max(0.0F, Math.min(1.0F, (float) growthProgress[idx] / (float) MAX_PROGRESS));
	}

	public ItemStack getDisplayCropStack(int idx) {
		if(idx < 0 || idx >= CROP_COUNT) return null;
		if(worldObj != null && worldObj.isRemote) {
			return syncedCrops[idx] != null ? syncedCrops[idx] : slots[SLOT_CROP_START + idx];
		}
		return slots[SLOT_CROP_START + idx];
	}

	/* ========================== 流体加成（按 HBM 原版流体识别码切换） ========================== */

	public static float getFluidSpeedMultiplier(FluidType type) {
		if(type == Fluids.CARBONDIOXIDE) return 1.5F;
		if(type == Fluids.WATER) return 1.0F;
		return 0.0F;
	}

	public static float getFluidBonusChance(FluidType type) {
		if(type == Fluids.CARBONDIOXIDE) return 0.25F;
		return 0.0F;
	}

	public static boolean isSupportedFluid(FluidType type) {
		return type == Fluids.WATER || type == Fluids.CARBONDIOXIDE;
	}

	public void setCultivationFluid(FluidType nextType) {
		if(nextType == null || !isSupportedFluid(nextType)) return;
		if(tank.getTankType() != nextType) {
			tank.setTankType(nextType);
			tank.setFill(0);
			this.markDirty();
		}
	}

	/* ========================== 作物自动识别与树苗排除 ========================== */

	public static boolean isSapling(ItemStack stack) {
		if(stack == null || stack.getItem() == null) return false;
		Item item = stack.getItem();
		if(item instanceof ItemBlock) {
			Block b = ((ItemBlock) item).field_150939_a;
			if(b instanceof BlockSapling) return true;
			if(b != null) {
				String bn = b.getUnlocalizedName();
				String cn = b.getClass().getSimpleName();
				if((bn != null && bn.toLowerCase().contains("sapling"))
						|| cn.toLowerCase().contains("sapling")) {
					return true;
				}
			}
		}
		if(item instanceof IPlantable) {
			Block pb = ((IPlantable) item).getPlant(null, 0, 0, 0);
			if(pb instanceof BlockSapling) return true;
			if(pb != null && (pb.getClass().getSimpleName().toLowerCase().contains("sapling")
					|| (pb.getUnlocalizedName() != null && pb.getUnlocalizedName().toLowerCase().contains("sapling")))) {
				return true;
			}
		}
		String unloc = item.getUnlocalizedName(stack);
		if(unloc != null && unloc.toLowerCase().contains("sapling")) return true;

		int[] oreIds = OreDictionary.getOreIDs(stack);
		for(int id : oreIds) {
			String name = OreDictionary.getOreName(id);
			if(name != null && (name.equals("treeSapling") || name.toLowerCase().contains("sapling"))) {
				return true;
			}
		}
		return false;
	}

	public static boolean isValidCrop(ItemStack stack) {
		if(stack == null || stack.getItem() == null) return false;
		if(isSapling(stack)) return false;
		// 芥子柳已移出本机的适配件：这里必须显式拒绝，否则它会从下面 IPlantable 的
		// 通用分支（PlantType.Crop）重新混进来
		if(isHbmMustardWillow(stack)) return false;

		Item item = stack.getItem();
		if(item instanceof ItemSeeds || item instanceof ItemSeedFood) return true;
		if(item == Items.reeds || item == Items.nether_wart) return true;
		if(item == Items.dye && stack.getItemDamage() == 3) return true; // 可可豆
		if(isVanillaSunflower(stack)) return true; // 原版向日葵（双格花）

		if(item instanceof ItemBlock) {
			Block b = ((ItemBlock) item).field_150939_a;
			if(b instanceof BlockCactus || b instanceof BlockReed || b instanceof BlockNetherWart
					|| b instanceof BlockCrops || b instanceof BlockMushroom) {
				return true;
			}
			// 适配 HBM 大麻（WEED）
			if(isHbmHemp(stack)) {
				return true;
			}
		}

		if(item instanceof IPlantable) {
			IPlantable plantable = (IPlantable) item;
			Block b = plantable.getPlant(null, 0, 0, 0);
			if(b instanceof BlockCrops || b instanceof BlockStem || b instanceof BlockReed
					|| b instanceof BlockNetherWart || b instanceof BlockCactus || b instanceof BlockMushroom) {
				return true;
			}
			EnumPlantType pt = plantable.getPlantType(null, 0, 0, 0);
			if(pt == EnumPlantType.Crop || pt == EnumPlantType.Nether
					|| pt == EnumPlantType.Desert || pt == EnumPlantType.Water || pt == EnumPlantType.Beach) {
				return b != null && !(b instanceof BlockSapling);
			}
		}
		return false;
	}

	/** 判断是否为 HBM 大麻（plant_flower WEED 或 plant_tall WEED） */
	public static boolean isHbmHemp(ItemStack stack) {
		if(stack == null || !(stack.getItem() instanceof ItemBlock)) return false;
		Block b = ((ItemBlock) stack.getItem()).field_150939_a;
		int meta = stack.getItemDamage();
		if(b == ModBlocks.plant_flower && meta == EnumFlowerType.WEED.ordinal()) return true;
		if(b == ModBlocks.plant_tall && (meta % 8) == EnumTallFlower.WEED.ordinal()) return true;
		return false;
	}

	/**
	 * 判断是否为 HBM 芥子柳（plant_flower CD0/CD1 或 plant_tall CD2/CD3/CD4）。
	 *
	 * <p>工业种植站已不再适配芥子柳：此方法现在只用于把它挡在作物槽之外，
	 * 3D 温室也不再为它做专属生长动画。</p>
	 */
	public static boolean isHbmMustardWillow(ItemStack stack) {
		if(stack == null || !(stack.getItem() instanceof ItemBlock)) return false;
		Block b = ((ItemBlock) stack.getItem()).field_150939_a;
		int meta = stack.getItemDamage();
		if(b == ModBlocks.plant_flower && (meta == EnumFlowerType.CD0.ordinal() || meta == EnumFlowerType.CD1.ordinal())) {
			return true;
		}
		if(b == ModBlocks.plant_tall) {
			int rec = meta % 8;
			return rec == EnumTallFlower.CD2.ordinal()
					|| rec == EnumTallFlower.CD3.ordinal()
					|| rec == EnumTallFlower.CD4.ordinal();
		}
		return false;
	}

	/**
	 * 判断是否为原版向日葵。
	 *
	 * <p>1.7.10 的双格花统一是 {@code Blocks.double_plant}，{@code damage & 7 == 0} 即向日葵
	 * （1 矢车菊 / 2 高草 / 3 蕨 / 4 玫瑰 / 5 牡丹）。物品形态的 damage 只取 0~5，
	 * 世界里上半格的 meta 会带 8，所以这里按 {@code & 7} 判断。</p>
	 */
	public static boolean isVanillaSunflower(ItemStack stack) {
		if(stack == null || stack.getItem() == null) return false;
		if(Block.getBlockFromItem(stack.getItem()) != Blocks.double_plant) return false;
		return (stack.getItemDamage() & 7) == 0;
	}

	/** 解析作物在 3D 温室里渲染对应的方块 */
	public static Block resolvePlantBlock(ItemStack stack) {
		if(stack == null || stack.getItem() == null) return null;
		Item item = stack.getItem();
		if(item == Items.wheat_seeds) return Blocks.wheat;
		if(item == Items.carrot) return Blocks.carrots;
		if(item == Items.potato) return Blocks.potatoes;
		if(item == Items.melon_seeds) return Blocks.melon_stem;
		if(item == Items.pumpkin_seeds) return Blocks.pumpkin_stem;
		if(item == Items.reeds) return Blocks.reeds;
		if(item == Items.nether_wart) return Blocks.nether_wart;
		if(item == Items.dye && stack.getItemDamage() == 3) return Blocks.cocoa;
		if(item instanceof ItemBlock) return ((ItemBlock) item).field_150939_a;
		if(item instanceof IPlantable) return ((IPlantable) item).getPlant(null, 0, 0, 0);
		return null;
	}

	/** 返回不同肥料的生长速度倍率（<=0 表示不是有效肥料）：HBM 工业化肥 > 磷 > 压缩生物质 > 骨粉/生物质 > 灰烬 */
	public static float getFertilizerSpeed(ItemStack stack) {
		if(stack == null || stack.getItem() == null) return 0.0F;
		Item item = stack.getItem();
		if(item == ModItems.powder_fertilizer) return 3.0F;
		if(item == ModItems.crystal_phosphorus || item == ModItems.ingot_phosphorus) return 2.2F;
		if(item == ModItems.biomass_compressed) return 2.0F;
		if(item == ModItems.biomass || (item == Items.dye && stack.getItemDamage() == 15)) return 1.5F;
		if(item == ModItems.powder_ash) return 1.3F;

		String unloc = item.getUnlocalizedName(stack);
		if(unloc != null) {
			String lower = unloc.toLowerCase();
			if(lower.contains("fertilizer")) return 2.5F;
			if(lower.contains("nitre") || lower.contains("saltpeter") || lower.contains("phosphorus")) return 1.8F;
		}
		int[] oreIds = OreDictionary.getOreIDs(stack);
		for(int id : oreIds) {
			String name = OreDictionary.getOreName(id);
			if(name != null) {
				String lower = name.toLowerCase();
				if(lower.contains("fertilizer")) return 2.5F;
				if(lower.contains("saltpeter") || lower.contains("nitre")) return 1.8F;
			}
		}
		return 0.0F;
	}

	public static int getFertilizerBonusYield(ItemStack stack) {
		if(stack == null || stack.getItem() == null) return 0;
		if(stack.getItem() == ModItems.powder_fertilizer) return 2;
		return getFertilizerSpeed(stack) > 0.0F ? 1 : 0;
	}

	public static int getFertilizerCharge(ItemStack stack) {
		if(stack == null || stack.getItem() == null) return 0;
		if(stack.getItem() == ModItems.powder_fertilizer) return 120;
		if(stack.getItem() == ModItems.biomass_compressed || stack.getItem() == ModItems.crystal_phosphorus) return 100;
		if(stack.getItem() == ModItems.powder_ash) return 60;
		return getFertilizerSpeed(stack) > 0.0F ? 80 : 0;
	}

	public static boolean isValidFertilizer(ItemStack stack) {
		return getFertilizerSpeed(stack) > 0.0F;
	}

	/* ========================== 自动识别产物（绝不掉落多余种子，仅产作物） ========================== */

	private List<ItemStack> computeHarvestDrops(ItemStack seed, int fertBonus, float bonusChance) {
		List<ItemStack> drops = new ArrayList<ItemStack>();
		if(seed == null || seed.getItem() == null) return drops;

		Item item = seed.getItem();
		int extra = Math.max(0, fertBonus) + (worldObj.rand.nextFloat() < bonusChance ? 1 : 0);

		if(item == Items.wheat_seeds) {
			drops.add(new ItemStack(Items.wheat, 2 + extra));
			return drops;
		}
		if(item == Items.carrot) {
			drops.add(new ItemStack(Items.carrot, 3 + extra));
			return drops;
		}
		if(item == Items.potato) {
			drops.add(new ItemStack(Items.potato, 3 + extra));
			return drops;
		}
		if(item == Items.melon_seeds) {
			drops.add(new ItemStack(Items.melon, 4 + extra * 2));
			return drops;
		}
		if(item == Items.pumpkin_seeds) {
			drops.add(new ItemStack(Blocks.pumpkin, 1 + extra));
			return drops;
		}
		if(item == Items.reeds) {
			drops.add(new ItemStack(Items.reeds, 3 + extra));
			return drops;
		}
		if(item == Items.nether_wart) {
			drops.add(new ItemStack(Items.nether_wart, 3 + extra));
			return drops;
		}
		if(item == Items.dye && seed.getItemDamage() == 3) {
			drops.add(new ItemStack(Items.dye, 3 + extra, 3));
			return drops;
		}
		// HBM 大麻 -> 产出大麻植株
		if(isHbmHemp(seed)) {
			drops.add(new ItemStack(ModBlocks.plant_flower, 2 + extra, EnumFlowerType.WEED.ordinal()));
			return drops;
		}
		// 原版向日葵 -> 产出向日葵本体（与原版一致：一株只掉 1 个，不产种子）
		if(isVanillaSunflower(seed)) {
			drops.add(new ItemStack(Blocks.double_plant, 1 + extra, 0));
			return drops;
		}
		if(item instanceof ItemBlock) {
			Block b = ((ItemBlock) item).field_150939_a;
			if(b instanceof BlockCactus || b instanceof BlockMushroom) {
				drops.add(new ItemStack(b, 2 + extra, seed.getItemDamage()));
				return drops;
			}
		}

		// 通用模组作物自动识别：查询成熟态（meta=7）掉落表，过滤掉所有种子类物品
		Block plantBlock = resolvePlantBlock(seed);
		if(plantBlock != null && worldObj != null) {
			ArrayList<ItemStack> raw = plantBlock.getDrops(worldObj, xCoord, yCoord + 1, zCoord, 7, 0);
			if(raw != null && !raw.isEmpty()) {
				List<ItemStack> nonSeeds = new ArrayList<ItemStack>();
				for(ItemStack d : raw) {
					if(d == null || d.getItem() == null || d.stackSize <= 0) continue;
					if(isSeedDrop(d, seed)) continue;
					nonSeeds.add(d.copy());
				}
				if(!nonSeeds.isEmpty()) {
					// 仅保留非种子作物产物，并追加肥料/营养液增产
					nonSeeds.get(0).stackSize += extra;
					return nonSeeds;
				}
				// 如果该作物本身就是种子（如无独立种子的模组根茎/草药），保留首个产物作为作物产出
				ItemStack first = raw.get(0).copy();
				first.stackSize = Math.max(1, first.stackSize) + extra;
				drops.add(first);
				return drops;
			}
		}

		// 兜底：返回 1 + extra 个原物品
		ItemStack fallback = seed.copy();
		fallback.stackSize = 1 + extra;
		drops.add(fallback);
		return drops;
	}

	private static boolean isSeedDrop(ItemStack drop, ItemStack plantedSeed) {
		if(drop.getItem() instanceof ItemSeeds) return true;
		String unloc = drop.getItem().getUnlocalizedName(drop);
		if(unloc != null && unloc.toLowerCase().contains("seed")) return true;
		// 若种植的是专门的种子类，过滤与种子相同的掉落
		if(plantedSeed.getItem() instanceof ItemSeeds
				&& drop.getItem() == plantedSeed.getItem()
				&& drop.getItemDamage() == plantedSeed.getItemDamage()) {
			return true;
		}
		return false;
	}

	private boolean canInsertAllOutputs(List<ItemStack> items) {
		ItemStack[] sim = new ItemStack[OUTPUT_COUNT];
		for(int i = 0; i < OUTPUT_COUNT; i++) {
			ItemStack cur = slots[SLOT_OUTPUT_START + i];
			sim[i] = (cur != null) ? cur.copy() : null;
		}
		for(ItemStack add : items) {
			if(add == null || add.stackSize <= 0) continue;
			int rem = add.stackSize;
			for(int i = 0; i < OUTPUT_COUNT && rem > 0; i++) {
				if(sim[i] != null && sim[i].isItemEqual(add) && ItemStack.areItemStackTagsEqual(sim[i], add)) {
					int space = Math.min(getInventoryStackLimit(), sim[i].getMaxStackSize()) - sim[i].stackSize;
					if(space > 0) {
						int take = Math.min(space, rem);
						sim[i].stackSize += take;
						rem -= take;
					}
				}
			}
			for(int i = 0; i < OUTPUT_COUNT && rem > 0; i++) {
				if(sim[i] == null) {
					int take = Math.min(Math.min(getInventoryStackLimit(), add.getMaxStackSize()), rem);
					sim[i] = add.copy();
					sim[i].stackSize = take;
					rem -= take;
				}
			}
			if(rem > 0) return false;
		}
		return true;
	}

	private void insertAllOutputs(List<ItemStack> items) {
		for(ItemStack add : items) {
			if(add == null || add.stackSize <= 0) continue;
			int rem = add.stackSize;
			for(int i = 0; i < OUTPUT_COUNT && rem > 0; i++) {
				ItemStack cur = slots[SLOT_OUTPUT_START + i];
				if(cur != null && cur.isItemEqual(add) && ItemStack.areItemStackTagsEqual(cur, add)) {
					int space = Math.min(getInventoryStackLimit(), cur.getMaxStackSize()) - cur.stackSize;
					if(space > 0) {
						int take = Math.min(space, rem);
						cur.stackSize += take;
						rem -= take;
					}
				}
			}
			for(int i = 0; i < OUTPUT_COUNT && rem > 0; i++) {
				if(slots[SLOT_OUTPUT_START + i] == null) {
					int take = Math.min(Math.min(getInventoryStackLimit(), add.getMaxStackSize()), rem);
					ItemStack placed = add.copy();
					placed.stackSize = take;
					slots[SLOT_OUTPUT_START + i] = placed;
					rem -= take;
				}
			}
		}
	}

	/* ========================== 端口与主循环 ========================== */

	public PortDef[] getPorts() {
		if(cachedPorts == null && this.getBlockType() instanceof BlockIndustrialCultivationStation) {
			ForgeDirection dir = ForgeDirection.getOrientation(this.getBlockMetadata() - BlockDummyable.offset);
			int[][] rel = ((BlockIndustrialCultivationStation) this.getBlockType()).getPortsAtCore(dir);
			cachedPorts = new PortDef[rel.length];
			for(int i = 0; i < rel.length; i++) {
				cachedPorts[i] = PortDef.make(
						xCoord + rel[i][0],
						yCoord + rel[i][1],
						zCoord + rel[i][2],
						ForgeDirection.getOrientation(rel[i][3]));
			}
		}
		return cachedPorts == null ? new PortDef[0] : cachedPorts;
	}

	@Override
	public void updateEntity() {
		if(worldObj.isRemote) {
			this.lastFanAngle = this.fanAngle;
			if(isPowered()) {
				this.fanAngle += 8.0F;
				if(this.fanAngle >= 360.0F) {
					this.fanAngle -= 360.0F;
					this.lastFanAngle -= 360.0F;
				}
			}
			return;
		}

		// 1. 端口收电、收液、电池充电
		PortDef[] ports = getPorts();
		this.setupPowerPorts(ports);
		this.setupFluidPorts(ports);
		this.updatePortPIFIFO();

		this.power = Library.chargeTEFromItems(slots, SLOT_BATTERY, this.power, MAX_POWER);

		// 2. 流体识别码自动切换流体类型（兼容 HBM 流体识别码）
		ItemStack idStack = slots[SLOT_FLUID_ID];
		if(idStack != null && idStack.getItem() instanceof IItemFluidIdentifier) {
			FluidType target = ((IItemFluidIdentifier) idStack.getItem()).getType(worldObj, xCoord, yCoord, zCoord, idStack);
			if(isSupportedFluid(target)) {
				setCultivationFluid(target);
			}
		}

		// 3. 肥料槽自动补充肥料缓冲（不同 HBM 肥料提供不同增速与增产档位）
		ItemStack fertStack = slots[SLOT_FERTILIZER];
		if(fertStack != null && isValidFertilizer(fertStack)) {
			int charge = getFertilizerCharge(fertStack);
			if(fertilizerBuffer <= MAX_FERTILIZER - charge) {
				float spd = getFertilizerSpeed(fertStack);
				int bonus = getFertilizerBonusYield(fertStack);
				decrStackSize(SLOT_FERTILIZER, 1);
				fertilizerBuffer = Math.min(MAX_FERTILIZER, fertilizerBuffer + charge);
				fertilizerSpeed = spd;
				fertilizerBonus = bonus;
				this.markDirty();
			}
		}
		// 4. HBM 升级芯片解析（单升级槽支持节能 POWER / 速度 SPEED / 效能 EFFECT 三类芯片）
		upgradeManager.checkSlots(slots, SLOT_UPGRADE, SLOT_UPGRADE);
		int speedLv = Math.min(upgradeManager.getLevel(UpgradeType.SPEED), 3);
		int powerLv = Math.min(upgradeManager.getLevel(UpgradeType.POWER), 3);
		int efficiencyLv = Math.min(upgradeManager.getLevel(UpgradeType.EFFECT), 3);

		float fluidSpeed = getFluidSpeedMultiplier(tank.getTankType());
		float fluidBonus = getFluidBonusChance(tank.getTankType());
		float fluidCostMultiplier = Math.max(0.4F, (1.0F + speedLv * 0.25F) * (1.0F - efficiencyLv * 0.2F));
		int fertilizerCost = Math.max(1, Math.round(FERTILIZER_PER_HARVEST * fluidCostMultiplier));
		boolean hasFert = fertilizerBuffer >= fertilizerCost;
		if(!hasFert) {
			fertilizerSpeed = 1.0F;
			fertilizerBonus = 0;
		}
		float fertMul = hasFert ? Math.max(1.0F, fertilizerSpeed) : 1.0F;
		int fertYield = hasFert ? fertilizerBonus : 0;

		float totalSpeed = fluidSpeed * (1.0F + speedLv * 0.5F) * fertMul;
		int powerCost = Math.max(20, Math.round(BASE_POWER_PER_TICK * (1.0F + speedLv * 0.6F) * (1.0F - powerLv * 0.2F)));

		this.activeSpeedMultiplier = totalSpeed;
		this.activePowerCost = powerCost;

		// 5. 逐格推进 6 个作物槽生长与自动收获
		int growingCount = 0;
		for(int i = 0; i < CROP_COUNT; i++) {
			ItemStack crop = slots[SLOT_CROP_START + i];
			if(crop == null || !isValidCrop(crop)) {
				growthProgress[i] = 0;
				fluidCostRemainder[i] = 0.0F;
				continue;
			}
			// 强制单格堆叠为 1
			if(crop.stackSize > 1) crop.stackSize = 1;

			if(growthProgress[i] >= MAX_PROGRESS) {
				List<ItemStack> drops = computeHarvestDrops(crop, fertYield, fluidBonus);
				if(canInsertAllOutputs(drops)) {
					insertAllOutputs(drops);
					if(hasFert) {
						fertilizerBuffer = Math.max(0, fertilizerBuffer - fertilizerCost);
						hasFert = fertilizerBuffer >= fertilizerCost;
						if(!hasFert) {
							fertilizerSpeed = 1.0F;
							fertilizerBonus = 0;
							fertYield = 0;
						}
					}
					growthProgress[i] = 0;
					this.markDirty();
				}
				continue;
			}

			float accumulatedFluidCost = fluidCostRemainder[i] + FLUID_PER_CROP_TICK * fluidCostMultiplier;
			int fluidCost = Math.max(0, (int) Math.floor(accumulatedFluidCost));
			if(this.power >= powerCost && fluidSpeed > 0.0F && tank.getFill() >= fluidCost) {
				int step = Math.max(1, Math.round(totalSpeed));
				growthProgress[i] = Math.min(MAX_PROGRESS, growthProgress[i] + step);
				fluidCostRemainder[i] = accumulatedFluidCost - fluidCost;
				tank.setFill(tank.getFill() - fluidCost);
				growingCount++;
			}
		}

		if(growingCount > 0) {
			this.power = Math.max(0L, this.power - powerCost);
			this.markDirty();
		}

		this.networkPackNT(150);
	}

	/* ========================== IEnergyReceiverMK2 ========================== */

	@Override
	public long getPower() {
		return this.power;
	}

	@Override
	public void setPower(long power) {
		this.power = Math.max(0L, Math.min(power, MAX_POWER));
	}

	@Override
	public long getMaxPower() {
		return MAX_POWER;
	}

	/* ========================== IFluidStandardTransceiverMK2 ========================== */

	@Override
	public FluidTank[] getReceivingTanks() {
		return this.receivingTanks;
	}

	@Override
	public FluidTank[] getSendingTanks() {
		return this.sendingTanks;
	}

	@Override
	public FluidTank[] getAllTanks() {
		return this.receivingTanks;
	}

	@Override
	public boolean canProvideInfo(UpgradeType type, int level, boolean extendedInfo) {
		return type == UpgradeType.POWER || type == UpgradeType.SPEED || type == UpgradeType.EFFECT;
	}

	@Override
	public void provideInfo(UpgradeType type, int level, List<String> info, boolean extendedInfo) {
		info.add(IUpgradeInfoProvider.getStandardLabel(com.makentmgreatagain.init.ModBlocks.industrial_cultivation_station));
		int tier = Math.max(1, Math.min(3, level));
		if(type == UpgradeType.SPEED) {
			info.add(net.minecraft.util.EnumChatFormatting.GREEN + com.hbm.util.i18n.I18nUtil.resolveKey(IUpgradeInfoProvider.KEY_SPEED, "+" + tier * 50 + "%"));
			info.add(net.minecraft.util.EnumChatFormatting.RED + com.hbm.util.i18n.I18nUtil.resolveKey(IUpgradeInfoProvider.KEY_CONSUMPTION, "+" + tier * 25 + "%"));
		} else if(type == UpgradeType.EFFECT) {
			info.add(net.minecraft.util.EnumChatFormatting.GREEN + com.hbm.util.i18n.I18nUtil.resolveKey(IUpgradeInfoProvider.KEY_EFFICIENCY, "-" + tier * 20 + "%"));
		} else {
			info.add(net.minecraft.util.EnumChatFormatting.GREEN + com.hbm.util.i18n.I18nUtil.resolveKey(IUpgradeInfoProvider.KEY_CONSUMPTION, "-" + tier * 20 + "%"));
		}
	}

	@Override
	public java.util.HashMap<UpgradeType, Integer> getValidUpgrades() {
		java.util.HashMap<UpgradeType, Integer> upgrades = new java.util.HashMap<UpgradeType, Integer>();
		upgrades.put(UpgradeType.POWER, 3);
		upgrades.put(UpgradeType.SPEED, 3);
		upgrades.put(UpgradeType.EFFECT, 3);
		return upgrades;
	}

	/* ========================== ISidedInventory ========================== */

	@Override
	public int[] getAccessibleSlotsFromSide(int side) {
		return ACCESSIBLE_SLOTS;
	}

	@Override
	public boolean isItemValidForSlot(int slot, ItemStack stack) {
		if(stack == null) return false;
		if(slot == SLOT_BATTERY) return stack.getItem() instanceof IBatteryItem;
		if(slot == SLOT_FLUID_ID) return stack.getItem() instanceof IItemFluidIdentifier;
		if(slot >= SLOT_CROP_START && slot < SLOT_CROP_START + CROP_COUNT) return isValidCrop(stack);
		if(slot == SLOT_FERTILIZER) return isValidFertilizer(stack);
		if(slot == SLOT_UPGRADE) return isValidUpgrade(stack);
		return false;
	}

	public static boolean isValidUpgrade(ItemStack stack) {
		if(stack == null || !(stack.getItem() instanceof ItemMachineUpgrade)) return false;
		UpgradeType type = ((ItemMachineUpgrade) stack.getItem()).type;
		return type == UpgradeType.POWER || type == UpgradeType.SPEED || type == UpgradeType.EFFECT;
	}

	@Override
	public boolean canInsertItem(int slot, ItemStack stack, int side) {
		return slot == SLOT_FERTILIZER && isValidFertilizer(stack);
	}

	@Override
	public boolean canExtractItem(int slot, ItemStack stack, int side) {
		return slot >= SLOT_OUTPUT_START && slot < SLOT_OUTPUT_START + OUTPUT_COUNT;
	}

	/* ========================== 网络同步 & NBT ========================== */

	@Override
	public void serialize(ByteBuf buf) {
		super.serialize(buf);
		buf.writeLong(this.power);
		tank.serialize(buf);
		buf.writeInt(this.fertilizerBuffer);
		buf.writeFloat(this.fertilizerSpeed);
		buf.writeInt(this.fertilizerBonus);
		buf.writeInt(this.activePowerCost);
		buf.writeFloat(this.activeSpeedMultiplier);
		for(int i = 0; i < CROP_COUNT; i++) {
			buf.writeInt(this.growthProgress[i]);
			ItemStack st = slots[SLOT_CROP_START + i];
			if(st != null && st.getItem() != null) {
				buf.writeBoolean(true);
				buf.writeInt(Item.getIdFromItem(st.getItem()));
				buf.writeShort(st.getItemDamage());
			} else {
				buf.writeBoolean(false);
			}
		}
	}

	@Override
	public void deserialize(ByteBuf buf) {
		super.deserialize(buf);
		this.power = buf.readLong();
		tank.deserialize(buf);
		this.fertilizerBuffer = buf.readInt();
		this.fertilizerSpeed = buf.readFloat();
		this.fertilizerBonus = buf.readInt();
		this.activePowerCost = buf.readInt();
		this.activeSpeedMultiplier = buf.readFloat();
		for(int i = 0; i < CROP_COUNT; i++) {
			this.growthProgress[i] = buf.readInt();
			boolean has = buf.readBoolean();
			if(has) {
				Item item = Item.getItemById(buf.readInt());
				int dmg = buf.readShort();
				this.syncedCrops[i] = (item != null) ? new ItemStack(item, 1, dmg) : null;
			} else {
				this.syncedCrops[i] = null;
			}
		}
	}

	@Override
	public void readFromNBT(NBTTagCompound nbt) {
		super.readFromNBT(nbt);
		this.power = nbt.getLong("power");
		tank.readFromNBT(nbt, "tank");
		this.fertilizerBuffer = nbt.getInteger("fertilizer");
		this.fertilizerSpeed = nbt.hasKey("fertSpd") ? nbt.getFloat("fertSpd") : 1.0F;
		this.fertilizerBonus = nbt.getInteger("fertBonus");
		for(int i = 0; i < CROP_COUNT; i++) {
			fluidCostRemainder[i] = nbt.getFloat("fluidCostRemainder" + i);
		}
		int[] prog = nbt.getIntArray("growth");
		for(int i = 0; i < CROP_COUNT; i++) {
			growthProgress[i] = (prog != null && i < prog.length) ? prog[i] : 0;
		}
	}

	@Override
	public void writeToNBT(NBTTagCompound nbt) {
		super.writeToNBT(nbt);
		nbt.setLong("power", this.power);
		tank.writeToNBT(nbt, "tank");
		nbt.setInteger("fertilizer", this.fertilizerBuffer);
		nbt.setFloat("fertSpd", this.fertilizerSpeed);
		nbt.setInteger("fertBonus", this.fertilizerBonus);
		for(int i = 0; i < CROP_COUNT; i++) {
			nbt.setFloat("fluidCostRemainder" + i, fluidCostRemainder[i]);
		}
		nbt.setIntArray("growth", this.growthProgress);
	}

	@Override
	@SideOnly(Side.CLIENT)
	public AxisAlignedBB getRenderBoundingBox() {
		return AxisAlignedBB.getBoundingBox(xCoord - 4, yCoord, zCoord - 4, xCoord + 5, yCoord + 6, zCoord + 5);
	}

	@Override
	@SideOnly(Side.CLIENT)
	public double getMaxRenderDistanceSquared() {
		return 65536.0D;
	}
}
