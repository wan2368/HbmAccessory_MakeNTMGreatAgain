package com.makentmgreatagain.hook;

import java.lang.reflect.Method;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.hbm.inventory.FluidStack;
import com.hbm.inventory.RecipesCommon.AStack;
import com.hbm.inventory.fluid.FluidType;
import com.hbm.inventory.fluid.tank.FluidTank;
import com.hbm.inventory.recipes.ArcWelderRecipes;
import com.hbm.inventory.recipes.ArcWelderRecipes.ArcWelderRecipe;
import com.hbm.inventory.recipes.CentrifugeRecipes;
import com.hbm.inventory.recipes.CompressorRecipes;
import com.hbm.inventory.recipes.CompressorRecipes.CompressorRecipe;
import com.hbm.inventory.recipes.CrystallizerRecipes;
import com.hbm.inventory.recipes.CrystallizerRecipes.CrystallizerRecipe;
import com.hbm.inventory.recipes.ElectrolyserFluidRecipes;
import com.hbm.inventory.recipes.ElectrolyserFluidRecipes.ElectrolysisRecipe;
import com.hbm.inventory.recipes.ElectrolyserMetalRecipes;
import com.hbm.inventory.recipes.ElectrolyserMetalRecipes.ElectrolysisMetalRecipe;
import com.hbm.inventory.recipes.ExposureChamberRecipes;
import com.hbm.inventory.recipes.ExposureChamberRecipes.ExposureChamberRecipe;
import com.hbm.inventory.recipes.LiquefactionRecipes;
import com.hbm.inventory.recipes.MixerRecipes;
import com.hbm.inventory.recipes.MixerRecipes.MixerRecipe;
import com.hbm.inventory.recipes.PyroOvenRecipes.PyroOvenRecipe;
import com.hbm.inventory.recipes.SolderingRecipes;
import com.hbm.inventory.recipes.SolderingRecipes.SolderingRecipe;
import com.hbm.inventory.recipes.SolidificationRecipes;
import com.hbm.inventory.recipes.loader.GenericRecipe;
import com.hbm.inventory.recipes.loader.GenericRecipes.IOutput;
import com.hbm.module.machine.ModuleMachineBase;
import com.hbm.tileentity.machine.TileEntityElectrolyser;
import com.hbm.tileentity.machine.TileEntityMachineArcWelder;
import com.hbm.tileentity.machine.TileEntityMachineAssemblyMachine;
import com.hbm.tileentity.machine.TileEntityMachineCentrifuge;
import com.hbm.tileentity.machine.TileEntityMachineChemicalPlant;
import com.hbm.tileentity.machine.TileEntityMachineCompressorBase;
import com.hbm.tileentity.machine.TileEntityMachineCrystallizer;
import com.hbm.tileentity.machine.TileEntityMachineElectricFurnace;
import com.hbm.tileentity.machine.TileEntityMachineExposureChamber;
import com.hbm.tileentity.machine.TileEntityMachineMixer;
import com.hbm.tileentity.machine.TileEntityMachineSolderingStation;
import com.hbm.tileentity.machine.oil.TileEntityMachineLiquefactor;
import com.hbm.tileentity.machine.oil.TileEntityMachinePyroOven;
import com.hbm.tileentity.machine.oil.TileEntityMachineSolidifier;
import com.hbm.util.Tuple.Pair;
import com.makentmgreatagain.items.ItemParallelUpgrade;

import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.FurnaceRecipes;

/**
 * PEEK 并行运算升级芯片统一运行期钩子。
 *
 * <ul>
 *   <li>同机多枚芯片按等级叠加，最高 4x 并行（1 + sum(tier)，封顶 4）；</li>
 *   <li>当实际并行数 N &gt; 1 时，功耗按 {@code 1.25 * N} 缩放；</li>
 *   <li>输入材料、电量或输出槽/流体罐空间不足时自动向下兼容降级至可安全执行的 N（最低 1x），绝不吞产物。</li>
 * </ul>
 */
public final class ParallelUpgradeHook {

	private static final Logger LOG = LogManager.getLogger("MNGA-Parallel");
	private static final int MAX_PARALLEL = 4;
	private static final double POWER_PENALTY = 1.25D;

	private static final ThreadLocal<Integer> MODULE_EXTRA = new ThreadLocal<Integer>() {
		@Override protected Integer initialValue() { return 0; }
	};

	private static Method mModuleConsume;
	private static Method mModuleProduce;
	private static Method mCentrifugeProcess;
	private static Method mCrystallizerProcess;
	private static Method mElectricFurnaceProcess;

	private ParallelUpgradeHook() { }

	/* ========================== 通用基础计算 ========================== */

	public static int getMaxParallel(ItemStack[] slots, int start, int end) {
		if(slots == null) return 1;
		int bonus = 0;
		int lo = Math.max(0, start);
		int hi = Math.min(slots.length - 1, end);
		for(int i = lo; i <= hi; i++) {
			ItemStack st = slots[i];
			if(st != null && st.getItem() instanceof ItemParallelUpgrade) {
				bonus += ((ItemParallelUpgrade) st.getItem()).parallelTier;
			}
		}
		return Math.min(MAX_PARALLEL, 1 + bonus);
	}

	public static long scalePowerLong(long base, int n) {
		return n <= 1 ? base : (long) Math.ceil(base * POWER_PENALTY * n);
	}

	public static int scalePowerInt(int base, int n) {
		return n <= 1 ? base : (int) Math.ceil(base * POWER_PENALTY * n);
	}

	private static int fitItemSlot(ItemStack slot, ItemStack out, int maxN) {
		if(out == null || out.stackSize <= 0) return maxN;
		int maxStack = out.getMaxStackSize();
		if(slot == null) {
			return Math.min(maxN, maxStack / out.stackSize);
		}
		if(slot.getItem() != out.getItem() || slot.getItemDamage() != out.getItemDamage()) return 0;
		int room = Math.min(slot.getMaxStackSize(), maxStack) - slot.stackSize;
		return room <= 0 ? 0 : Math.min(maxN, room / out.stackSize);
	}

	private static int fitFluidTank(FluidTank tank, FluidType type, int amountPerOp, int maxN) {
		if(amountPerOp <= 0) return maxN;
		if(tank.getFill() > 0 && type != null && tank.getTankType() != type) return 0;
		int room = tank.getMaxFill() - tank.getFill();
		return room <= 0 ? 0 : Math.min(maxN, room / amountPerOp);
	}

	private static int fitPowerLong(long currentPower, long baseCost, int maxN) {
		if(baseCost <= 0) return maxN;
		for(int n = maxN; n >= 2; n--) {
			if(currentPower >= scalePowerLong(baseCost, n)) return n;
		}
		return 1;
	}

	private static void addOutputToSlot(ItemStack[] slots, int idx, ItemStack out, int times) {
		if(out == null || times <= 0) return;
		int add = out.stackSize * times;
		if(slots[idx] == null) {
			ItemStack copy = out.copy();
			copy.stackSize = add;
			slots[idx] = copy;
		} else {
			slots[idx].stackSize += add;
		}
	}

	/* ========================== 1. ModuleMachineBase（化工厂 / 装配机） ========================== */

	private static int getModuleMaxParallel(ModuleMachineBase mod) {
		if(mod == null || mod.battery == null) return 1;
		if(mod.battery instanceof TileEntityMachineChemicalPlant) {
			return getMaxParallel(((TileEntityMachineChemicalPlant) mod.battery).slots, 2, 3);
		}
		if(mod.battery instanceof TileEntityMachineAssemblyMachine) {
			return getMaxParallel(((TileEntityMachineAssemblyMachine) mod.battery).slots, 2, 3);
		}
		return 1;
	}

	public static double scaleModulePower(ModuleMachineBase mod, GenericRecipe recipe, double powerMult) {
		MODULE_EXTRA.set(0);
		if(mod == null || recipe == null) return powerMult;
		int maxP = getModuleMaxParallel(mod);
		if(maxP <= 1) return powerMult;

		int n = maxP;
		// 电量检查
		long curPow = mod.battery.getPower();
		while(n > 1 && curPow < (long) (recipe.power * powerMult * POWER_PENALTY * n)) n--;
		// 输入物品
		if(recipe.inputItem != null) {
			for(int i = 0; i < Math.min(recipe.inputItem.length, mod.inputSlots.length) && n > 1; i++) {
				ItemStack st = mod.slots[mod.inputSlots[i]];
				int req = Math.max(1, recipe.inputItem[i].stacksize);
				if(st == null || !recipe.inputItem[i].matchesRecipe(st, false)) { n = 1; break; }
				n = Math.min(n, st.stackSize / req);
			}
		}
		// 输入流体
		if(recipe.inputFluid != null) {
			for(int i = 0; i < Math.min(recipe.inputFluid.length, mod.inputTanks.length) && n > 1; i++) {
				int req = Math.max(1, recipe.inputFluid[i].fill);
				n = Math.min(n, mod.inputTanks[i].getFill() / req);
			}
		}
		// 输出物品
		if(recipe.outputItem != null) {
			for(int i = 0; i < Math.min(recipe.outputItem.length, mod.outputSlots.length) && n > 1; i++) {
				IOutput out = recipe.outputItem[i];
				if(out.possibleMultiOutput()) { n = 1; break; }
				n = Math.min(n, fitItemSlot(mod.slots[mod.outputSlots[i]], out.getSingle(), n));
			}
		}
		// 输出流体
		if(recipe.outputFluid != null) {
			for(int i = 0; i < Math.min(recipe.outputFluid.length, mod.outputTanks.length) && n > 1; i++) {
				n = Math.min(n, fitFluidTank(mod.outputTanks[i], recipe.outputFluid[i].type, recipe.outputFluid[i].fill, n));
			}
		}

		if(n <= 1) return powerMult;
		MODULE_EXTRA.set(n - 1);
		return powerMult * POWER_PENALTY * n;
	}

	public static void onModuleComplete(ModuleMachineBase mod, GenericRecipe recipe, double powerMult) {
		int extra = MODULE_EXTRA.get();
		MODULE_EXTRA.set(0);
		if(extra <= 0 || mod == null || recipe == null) return;
		try {
			if(mModuleConsume == null) {
				mModuleConsume = ModuleMachineBase.class.getDeclaredMethod("consumeInput", GenericRecipe.class);
				mModuleConsume.setAccessible(true);
				mModuleProduce = ModuleMachineBase.class.getDeclaredMethod("produceItem", GenericRecipe.class);
				mModuleProduce.setAccessible(true);
			}
			for(int k = 0; k < extra; k++) {
				if(!mod.canProcess(recipe, 1.0D, 0.0D)) break;
				mModuleConsume.invoke(mod, recipe);
				mModuleProduce.invoke(mod, recipe);
			}
		} catch(Exception e) {
			LOG.warn("[MNGA-Parallel] ModuleMachineBase 并行结算异常", e);
		}
	}

	/* ========================== 2. 电弧焊机 (ArcWelder) ========================== */

	public static long scaleArcWelderConsumption(TileEntityMachineArcWelder te, long base) {
		if(te == null) return base;
		ArcWelderRecipe recipe = ArcWelderRecipes.getRecipe(te.slots[0], te.slots[1], te.slots[2]);
		int n = computeArcWelderN(te, recipe, base);
		return scalePowerLong(base, n);
	}

	private static int computeArcWelderN(TileEntityMachineArcWelder te, ArcWelderRecipe recipe, long base) {
		if(te == null || recipe == null) return 1;
		int maxP = getMaxParallel(te.slots, 6, 7);
		if(maxP <= 1) return 1;
		int n = fitPowerLong(te.power, base, maxP);
		if(recipe.fluid != null && recipe.fluid.fill > 0) {
			if(te.tank.getTankType() != recipe.fluid.type) return 1;
			n = Math.min(n, te.tank.getFill() / recipe.fluid.fill);
		}
		for(AStack aStack : recipe.ingredients) {
			int avail = 0;
			for(int i = 0; i < 3; i++) {
				if(aStack.matchesRecipe(te.slots[i], true)) avail += te.slots[i].stackSize;
			}
			n = Math.min(n, avail / Math.max(1, aStack.stacksize));
		}
		n = Math.min(n, fitItemSlot(te.slots[3], recipe.output, n));
		return Math.max(1, n);
	}

	public static void completeArcWelderExtra(TileEntityMachineArcWelder te) {
		if(te == null) return;
		ArcWelderRecipe recipe = ArcWelderRecipes.getRecipe(te.slots[0], te.slots[1], te.slots[2]);
		if(recipe == null) return;
		int maxP = getMaxParallel(te.slots, 6, 7);
		for(int k = 1; k < maxP; k++) {
			boolean hasInputs = true;
			for(AStack aStack : recipe.ingredients) {
				boolean found = false;
				for(int i = 0; i < 3; i++) {
					ItemStack st = te.slots[i];
					if(aStack.matchesRecipe(st, true) && st.stackSize >= aStack.stacksize) {
						found = true; break;
					}
				}
				if(!found) { hasInputs = false; break; }
			}
			if(!hasInputs) break;
			if(recipe.fluid != null && (te.tank.getTankType() != recipe.fluid.type || te.tank.getFill() < recipe.fluid.fill)) break;
			if(fitItemSlot(te.slots[3], recipe.output, 1) < 1) break;
			te.consumeItems(recipe);
			addOutputToSlot(te.slots, 3, recipe.output, 1);
		}
	}

	/* ========================== 3. 焊接台 (SolderingStation) ========================== */

	public static long scaleSolderingConsumption(TileEntityMachineSolderingStation te, long base) {
		if(te == null) return base;
		SolderingRecipe recipe = SolderingRecipes.getRecipe(new ItemStack[] {
				te.slots[0], te.slots[1], te.slots[2], te.slots[3], te.slots[4], te.slots[5]
		});
		int n = computeSolderingN(te, recipe, base);
		return scalePowerLong(base, n);
	}

	private static int computeSolderingN(TileEntityMachineSolderingStation te, SolderingRecipe recipe, long base) {
		if(te == null || recipe == null) return 1;
		int maxP = getMaxParallel(te.slots, 9, 10);
		if(maxP <= 1) return 1;
		int n = fitPowerLong(te.power, base, maxP);
		if(recipe.fluid != null && recipe.fluid.fill > 0) {
			if(te.tank.getTankType() != recipe.fluid.type) return 1;
			n = Math.min(n, te.tank.getFill() / recipe.fluid.fill);
		}
		n = Math.min(n, maxMatchInRange(te.slots, 0, 3, recipe.toppings));
		n = Math.min(n, maxMatchInRange(te.slots, 3, 5, recipe.pcb));
		n = Math.min(n, maxMatchInRange(te.slots, 5, 6, recipe.solder));
		n = Math.min(n, fitItemSlot(te.slots[6], recipe.output, n));
		return Math.max(1, n);
	}

	private static int maxMatchInRange(ItemStack[] slots, int start, int endExcl, AStack[] reqs) {
		if(reqs == null) return MAX_PARALLEL;
		int n = MAX_PARALLEL;
		for(AStack aStack : reqs) {
			int avail = 0;
			for(int i = start; i < endExcl; i++) {
				if(aStack.matchesRecipe(slots[i], true)) avail += slots[i].stackSize;
			}
			n = Math.min(n, avail / Math.max(1, aStack.stacksize));
		}
		return n;
	}

	public static void completeSolderingExtra(TileEntityMachineSolderingStation te) {
		if(te == null) return;
		SolderingRecipe recipe = SolderingRecipes.getRecipe(new ItemStack[] {
				te.slots[0], te.slots[1], te.slots[2], te.slots[3], te.slots[4], te.slots[5]
		});
		if(recipe == null) return;
		int maxP = getMaxParallel(te.slots, 9, 10);
		for(int k = 1; k < maxP; k++) {
			if(recipe.fluid != null && (te.tank.getTankType() != recipe.fluid.type || te.tank.getFill() < recipe.fluid.fill)) break;
			if(maxMatchInRange(te.slots, 0, 3, recipe.toppings) < 1) break;
			if(maxMatchInRange(te.slots, 3, 5, recipe.pcb) < 1) break;
			if(maxMatchInRange(te.slots, 5, 6, recipe.solder) < 1) break;
			if(fitItemSlot(te.slots[6], recipe.output, 1) < 1) break;
			te.consumeItems(recipe);
			addOutputToSlot(te.slots, 6, recipe.output, 1);
		}
	}

	/* ========================== 4. 曝光室 (ExposureChamber) ========================== */

	public static int scaleExposureConsumption(TileEntityMachineExposureChamber te, int base) {
		if(te == null || te.slots[1] == null || te.savedParticles <= 0 || te.slots[3] == null) return base;
		int maxP = getMaxParallel(te.slots, 6, 7);
		if(maxP <= 1) return base;
		ExposureChamberRecipe recipe = te.getRecipe(te.slots[1], te.slots[3]);
		if(recipe == null) return base;
		int n = fitPowerLong(te.power, base, maxP);
		n = Math.min(n, te.savedParticles);
		n = Math.min(n, te.slots[3].stackSize);
		n = Math.min(n, fitItemSlot(te.slots[4], recipe.output, n));
		return scalePowerInt(base, Math.max(1, n));
	}

	public static void completeExposureExtra(TileEntityMachineExposureChamber te) {
		if(te == null || te.slots[1] == null) return;
		int maxP = getMaxParallel(te.slots, 6, 7);
		for(int k = 1; k < maxP; k++) {
			if(te.savedParticles <= 0 || te.slots[3] == null) break;
			ExposureChamberRecipe recipe = te.getRecipe(te.slots[1], te.slots[3]);
			if(recipe == null || fitItemSlot(te.slots[4], recipe.output, 1) < 1) break;
			te.savedParticles--;
			te.decrStackSize(3, 1);
			addOutputToSlot(te.slots, 4, recipe.output, 1);
		}
	}

	/* ========================== 5. 普通离心机 (Centrifuge) ========================== */

	public static int scaleCentrifugeConsumption(TileEntityMachineCentrifuge te, int base) {
		if(te == null || te.slots[0] == null) return base;
		int maxP = getMaxParallel(te.slots, 6, 7);
		if(maxP <= 1) return base;
		ItemStack[] out = CentrifugeRecipes.getOutput(te.slots[0]);
		if(out == null) return base;
		int n = fitPowerLong(te.power, base, maxP);
		n = Math.min(n, te.slots[0].stackSize);
		for(int i = 0; i < Math.min(4, out.length) && n > 1; i++) {
			if(out[i] != null) n = Math.min(n, fitItemSlot(te.slots[i + 2], out[i], n));
		}
		return scalePowerInt(base, Math.max(1, n));
	}

	public static void completeCentrifugeExtra(TileEntityMachineCentrifuge te) {
		if(te == null) return;
		int maxP = getMaxParallel(te.slots, 6, 7);
		if(maxP <= 1) return;
		try {
			if(mCentrifugeProcess == null) {
				mCentrifugeProcess = TileEntityMachineCentrifuge.class.getDeclaredMethod("processItem");
				mCentrifugeProcess.setAccessible(true);
			}
			for(int k = 1; k < maxP; k++) {
				if(!te.canProcess()) break;
				mCentrifugeProcess.invoke(te);
			}
		} catch(Exception e) {
			LOG.warn("[MNGA-Parallel] Centrifuge 并行结算异常", e);
		}
	}

	/* ========================== 6. 结晶器 (Crystallizer) ========================== */

	public static int scaleCrystallizerPower(TileEntityMachineCrystallizer te, int base) {
		if(te == null || te.slots[0] == null) return base;
		int maxP = getMaxParallel(te.slots, 5, 6);
		if(maxP <= 1) return base;
		CrystallizerRecipe res = CrystallizerRecipes.getOutput(te.slots[0], te.tank.getTankType());
		if(res == null) return base;
		int n = fitPowerLong(te.power, base, maxP);
		n = Math.min(n, te.slots[0].stackSize / Math.max(1, res.itemAmount));
		int reqAcid = te.getRequiredAcid(res.acidAmount);
		if(reqAcid > 0) n = Math.min(n, te.tank.getFill() / reqAcid);
		n = Math.min(n, fitItemSlot(te.slots[2], res.output, n));
		return scalePowerInt(base, Math.max(1, n));
	}

	public static void completeCrystallizerExtra(TileEntityMachineCrystallizer te) {
		if(te == null) return;
		int maxP = getMaxParallel(te.slots, 5, 6);
		if(maxP <= 1) return;
		try {
			if(mCrystallizerProcess == null) {
				mCrystallizerProcess = TileEntityMachineCrystallizer.class.getDeclaredMethod("processItem");
				mCrystallizerProcess.setAccessible(true);
			}
			for(int k = 1; k < maxP; k++) {
				CrystallizerRecipe res = CrystallizerRecipes.getOutput(te.slots[0], te.tank.getTankType());
				if(res == null || te.slots[0] == null || te.slots[0].stackSize < res.itemAmount) break;
				if(te.tank.getFill() < te.getRequiredAcid(res.acidAmount)) break;
				if(fitItemSlot(te.slots[2], res.output, 1) < 1) break;
				mCrystallizerProcess.invoke(te);
			}
		} catch(Exception e) {
			LOG.warn("[MNGA-Parallel] Crystallizer 并行结算异常", e);
		}
	}

	/* ========================== 7. 电解槽 (Electrolyser) ========================== */

	public static int scaleElectrolyserFluidUsage(TileEntityElectrolyser te, int base) {
		if(te == null) return base;
		int maxP = getMaxParallel(te.slots, 1, 2);
		if(maxP <= 1) return base;
		ElectrolysisRecipe rec = ElectrolyserFluidRecipes.recipes.get(te.tanks[0].getTankType());
		if(rec == null) return base;
		int n = fitPowerLong(te.power, base, maxP);
		n = Math.min(n, te.tanks[0].getFill() / Math.max(1, rec.amount));
		n = Math.min(n, fitFluidTank(te.tanks[1], rec.output1.type, rec.output1.fill, n));
		n = Math.min(n, fitFluidTank(te.tanks[2], rec.output2.type, rec.output2.fill, n));
		if(rec.byproduct != null) {
			for(int i = 0; i < rec.byproduct.length && n > 1; i++) {
				n = Math.min(n, fitItemSlot(te.slots[11 + i], rec.byproduct[i], n));
			}
		}
		return scalePowerInt(base, Math.max(1, n));
	}

	public static int scaleElectrolyserOreUsage(TileEntityElectrolyser te, int base) {
		if(te == null || te.slots[14] == null) return base;
		int maxP = getMaxParallel(te.slots, 1, 2);
		if(maxP <= 1) return base;
		ElectrolysisMetalRecipe rec = ElectrolyserMetalRecipes.getRecipe(te.slots[14]);
		if(rec == null) return base;
		int n = fitPowerLong(te.power, base, maxP);
		n = Math.min(n, te.slots[14].stackSize);
		n = Math.min(n, te.tanks[3].getFill() / 100);
		if(te.leftStack != null && rec.output1 != null) {
			if(rec.output1.material != te.leftStack.material) return base;
			n = Math.min(n, (te.maxMaterial - te.leftStack.amount) / Math.max(1, rec.output1.amount));
		}
		if(te.rightStack != null && rec.output2 != null) {
			if(rec.output2.material != te.rightStack.material) return base;
			n = Math.min(n, (te.maxMaterial - te.rightStack.amount) / Math.max(1, rec.output2.amount));
		}
		if(rec.byproduct != null) {
			for(int i = 0; i < rec.byproduct.length && n > 1; i++) {
				n = Math.min(n, fitItemSlot(te.slots[15 + i], rec.byproduct[i], n));
			}
		}
		return scalePowerInt(base, Math.max(1, n));
	}

	public static void completeElectrolyserFluidsExtra(TileEntityElectrolyser te) {
		if(te == null) return;
		int maxP = getMaxParallel(te.slots, 1, 2);
		for(int k = 1; k < maxP; k++) {
			if(!te.canProcessFluid()) break;
			te.processFluids();
		}
	}

	public static void completeElectrolyserMetalExtra(TileEntityElectrolyser te) {
		if(te == null) return;
		int maxP = getMaxParallel(te.slots, 1, 2);
		for(int k = 1; k < maxP; k++) {
			if(!te.canProcessMetal()) break;
			te.processMetal();
		}
	}

	/* ========================== 8. 混合机 (Mixer) ========================== */

	public static int scaleMixerConsumption(TileEntityMachineMixer te, int base) {
		if(te == null) return base;
		int maxP = getMaxParallel(te.slots, 3, 4);
		if(maxP <= 1) return base;
		MixerRecipe[] recipes = MixerRecipes.getOutput(te.tanks[2].getTankType());
		if(recipes == null || recipes.length <= 0) return base;
		MixerRecipe rec = recipes[te.recipeIndex % recipes.length];
		if(rec == null) return base;
		int n = fitPowerLong(te.power, base, maxP);
		if(rec.input1 != null) n = Math.min(n, te.tanks[0].getFill() / Math.max(1, rec.input1.fill));
		if(rec.input2 != null) n = Math.min(n, te.tanks[1].getFill() / Math.max(1, rec.input2.fill));
		if(rec.solidInput != null) {
			if(te.slots[1] == null || !rec.solidInput.matchesRecipe(te.slots[1], true)) return base;
			n = Math.min(n, te.slots[1].stackSize / Math.max(1, rec.solidInput.stacksize));
		}
		n = Math.min(n, fitFluidTank(te.tanks[2], te.tanks[2].getTankType(), rec.output, n));
		return scalePowerInt(base, Math.max(1, n));
	}

	public static void completeMixerExtra(TileEntityMachineMixer te) {
		if(te == null) return;
		int maxP = getMaxParallel(te.slots, 3, 4);
		for(int k = 1; k < maxP; k++) {
			if(!te.canProcess()) break;
			MixerRecipe[] recipes = MixerRecipes.getOutput(te.tanks[2].getTankType());
			MixerRecipe rec = recipes[te.recipeIndex % recipes.length];
			if(rec.input1 != null) te.tanks[0].setFill(te.tanks[0].getFill() - rec.input1.fill);
			if(rec.input2 != null) te.tanks[1].setFill(te.tanks[1].getFill() - rec.input2.fill);
			if(rec.solidInput != null) te.decrStackSize(1, rec.solidInput.stacksize);
			te.tanks[2].setFill(te.tanks[2].getFill() + rec.output);
		}
	}

	/* ========================== 9. 压缩机 (CompressorBase) ========================== */

	public static int scaleCompressorPower(TileEntityMachineCompressorBase te, int base) {
		if(te == null) return base;
		int maxP = getMaxParallel(te.slots, 1, 3);
		if(maxP <= 1) return base;
		CompressorRecipe rec = CompressorRecipes.recipes.get(new Pair<FluidType, Integer>(te.tanks[0].getTankType(), te.tanks[0].getPressure()));
		int inAmt = rec == null ? 1000 : rec.inputAmount;
		int outAmt = rec == null ? 1000 : rec.output.fill;
		int n = fitPowerLong(te.power, base, maxP);
		n = Math.min(n, te.tanks[0].getFill() / Math.max(1, inAmt));
		n = Math.min(n, fitFluidTank(te.tanks[1], te.tanks[1].getTankType(), outAmt, n));
		return scalePowerInt(base, Math.max(1, n));
	}

	public static void completeCompressorExtra(TileEntityMachineCompressorBase te) {
		if(te == null) return;
		int maxP = getMaxParallel(te.slots, 1, 3);
		for(int k = 1; k < maxP; k++) {
			if(!te.canProcess()) break;
			te.process();
		}
	}

	/* ========================== 10. 液化器 (Liquefactor) ========================== */

	public static int scaleLiquefactorUsage(TileEntityMachineLiquefactor te, int base) {
		if(te == null || te.slots[0] == null) return base;
		int maxP = getMaxParallel(te.slots, 2, 3);
		if(maxP <= 1) return base;
		FluidStack out = LiquefactionRecipes.getOutput(te.slots[0]);
		if(out == null) return base;
		int n = fitPowerLong(te.power, base, maxP);
		n = Math.min(n, te.slots[0].stackSize);
		n = Math.min(n, fitFluidTank(te.tank, out.type, out.fill, n));
		return scalePowerInt(base, Math.max(1, n));
	}

	public static void completeLiquefactorExtra(TileEntityMachineLiquefactor te) {
		if(te == null) return;
		int maxP = getMaxParallel(te.slots, 2, 3);
		for(int k = 1; k < maxP; k++) {
			if(te.slots[0] == null) break;
			FluidStack out = LiquefactionRecipes.getOutput(te.slots[0]);
			if(out == null) break;
			if(out.type != te.tank.getTankType() && te.tank.getFill() > 0) break;
			if(out.fill + te.tank.getFill() > te.tank.getMaxFill()) break;
			te.tank.setTankType(out.type);
			te.tank.setFill(te.tank.getFill() + out.fill);
			te.decrStackSize(0, 1);
		}
	}

	/* ========================== 11. 固化器 (Solidifier) ========================== */

	public static int scaleSolidifierUsage(TileEntityMachineSolidifier te, int base) {
		if(te == null) return base;
		int maxP = getMaxParallel(te.slots, 2, 3);
		if(maxP <= 1) return base;
		Pair<Integer, ItemStack> out = SolidificationRecipes.getOutput(te.tank.getTankType());
		if(out == null) return base;
		int n = fitPowerLong(te.power, base, maxP);
		n = Math.min(n, te.tank.getFill() / Math.max(1, out.getKey()));
		n = Math.min(n, fitItemSlot(te.slots[0], out.getValue(), n));
		return scalePowerInt(base, Math.max(1, n));
	}

	public static void completeSolidifierExtra(TileEntityMachineSolidifier te) {
		if(te == null) return;
		int maxP = getMaxParallel(te.slots, 2, 3);
		for(int k = 1; k < maxP; k++) {
			Pair<Integer, ItemStack> out = SolidificationRecipes.getOutput(te.tank.getTankType());
			if(out == null) break;
			int req = out.getKey();
			ItemStack stack = out.getValue();
			if(req > te.tank.getFill()) break;
			if(fitItemSlot(te.slots[0], stack, 1) < 1) break;
			te.tank.setFill(te.tank.getFill() - req);
			addOutputToSlot(te.slots, 0, stack, 1);
		}
	}

	/* ========================== 12. 热解炉 (PyroOven) ========================== */

	public static int scalePyroOvenConsumption(TileEntityMachinePyroOven te, int base) {
		if(te == null) return base;
		int maxP = getMaxParallel(te.slots, 4, 5);
		if(maxP <= 1) return base;
		PyroOvenRecipe rec = te.getMatchingRecipe();
		if(rec == null) return base;
		int n = fitPowerLong(te.power, base, maxP);
		if(rec.inputFluid != null) n = Math.min(n, te.tanks[0].getFill() / Math.max(1, rec.inputFluid.fill));
		if(rec.inputItem != null) n = Math.min(n, (te.slots[1] == null ? 0 : te.slots[1].stackSize) / Math.max(1, rec.inputItem.stacksize));
		if(rec.outputFluid != null) n = Math.min(n, fitFluidTank(te.tanks[1], rec.outputFluid.type, rec.outputFluid.fill, n));
		if(rec.outputItem != null) n = Math.min(n, fitItemSlot(te.slots[2], rec.outputItem, n));
		return scalePowerInt(base, Math.max(1, n));
	}

	public static void completePyroOvenExtra(TileEntityMachinePyroOven te) {
		if(te == null) return;
		PyroOvenRecipe rec = te.getMatchingRecipe();
		if(rec == null) return;
		int maxP = getMaxParallel(te.slots, 4, 5);
		for(int k = 1; k < maxP; k++) {
			if(rec.inputFluid != null && te.tanks[0].getFill() < rec.inputFluid.fill) break;
			if(rec.inputItem != null && (te.slots[1] == null || te.slots[1].stackSize < rec.inputItem.stacksize)) break;
			if(rec.outputFluid != null && fitFluidTank(te.tanks[1], rec.outputFluid.type, rec.outputFluid.fill, 1) < 1) break;
			if(rec.outputItem != null && fitItemSlot(te.slots[2], rec.outputItem, 1) < 1) break;
			te.finishRecipe(rec);
		}
	}

	/* ========================== 13. 电炉 (ElectricFurnace) ========================== */

	public static int scaleElectricFurnaceConsumption(TileEntityMachineElectricFurnace te, int base) {
		if(te == null || te.slots[1] == null) return base;
		int maxP = getMaxParallel(te.slots, 3, 3);
		if(maxP <= 1) return base;
		ItemStack res = FurnaceRecipes.smelting().getSmeltingResult(te.slots[1]);
		if(res == null) return base;
		int n = fitPowerLong(te.power, base, maxP);
		n = Math.min(n, te.slots[1].stackSize);
		n = Math.min(n, fitItemSlot(te.slots[2], res, n));
		return scalePowerInt(base, Math.max(1, n));
	}

	public static void completeElectricFurnaceExtra(TileEntityMachineElectricFurnace te) {
		if(te == null) return;
		int maxP = getMaxParallel(te.slots, 3, 3);
		if(maxP <= 1) return;
		try {
			if(mElectricFurnaceProcess == null) {
				mElectricFurnaceProcess = TileEntityMachineElectricFurnace.class.getDeclaredMethod("processItem");
				mElectricFurnaceProcess.setAccessible(true);
			}
			for(int k = 1; k < maxP; k++) {
				if(!te.canProcess()) break;
				mElectricFurnaceProcess.invoke(te);
			}
		} catch(Exception e) {
			LOG.warn("[MNGA-Parallel] ElectricFurnace 并行结算异常", e);
		}
	}
}
