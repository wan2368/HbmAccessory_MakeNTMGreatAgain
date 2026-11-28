package com.makentmgreatagain.hook;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.hbm.inventory.FluidStack;
import com.hbm.inventory.fluid.FluidType;
import com.hbm.inventory.fluid.tank.FluidTank;
import com.hbm.inventory.recipes.CompressorRecipes;
import com.hbm.inventory.recipes.CompressorRecipes.CompressorRecipe;
import com.hbm.inventory.recipes.CrystallizerRecipes;
import com.hbm.inventory.recipes.CrystallizerRecipes.CrystallizerRecipe;
import com.hbm.inventory.recipes.LiquefactionRecipes;
import com.hbm.inventory.recipes.MixerRecipes;
import com.hbm.inventory.recipes.MixerRecipes.MixerRecipe;
import com.hbm.inventory.recipes.SolidificationRecipes;
import com.hbm.tileentity.machine.TileEntityMachineCompressorBase;
import com.hbm.tileentity.machine.TileEntityMachineCrystallizer;
import com.hbm.tileentity.machine.TileEntityMachineElectricFurnace;
import com.hbm.tileentity.machine.TileEntityMachineMixer;
import com.hbm.tileentity.machine.oil.TileEntityMachineLiquefactor;
import com.hbm.tileentity.machine.oil.TileEntityMachineSolidifier;
import com.hbm.util.Tuple.Pair;
import com.makentmgreatagain.items.ItemParallelUpgrade;

import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.FurnaceRecipes;

/**
 * PEEK 并行运算升级芯片统一运行期钩子（覆盖 6 台目标机器：结晶器、混合机、压缩机、液化器、固化器、电炉）。
 *
 * <ul>
 *   <li>同机多枚芯片按等级叠加，最高 4x 并行（1 + sum(tier)，封顶 4）；</li>
 *   <li>当实际并行数 N &gt; 1 时，功耗按 {@code 1.25 * N} 缩放；</li>
 *   <li>运行期记录每台机器实际按电量/原料/空间结算的并行数 N，完成瞬间严格不超过该 N，杜绝低电白嫖并行与吞 NBT/超堆叠。</li>
 * </ul>
 */
public final class ParallelUpgradeHook {

	private static final Logger LOG = LogManager.getLogger("MNGA-Parallel");
	private static final int MAX_PARALLEL = 4;
	private static final double POWER_PENALTY = 1.25D;

	/** 记录每台机器当前 tick 实际按功耗缩放生效的并行数 N（WeakHashMap 避免区块卸载内存泄漏） */
	private static final Map<Object, Integer> PAID_PARALLEL =
			Collections.synchronizedMap(new WeakHashMap<Object, Integer>());

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

	private static void recordPaidParallel(Object te, int n) {
		if(te == null) return;
		if(n <= 1) {
			PAID_PARALLEL.remove(te);
		} else {
			PAID_PARALLEL.put(te, n);
		}
	}

	private static int getPaidParallel(Object te, int maxP) {
		if(te == null || maxP <= 1) return 1;
		Integer paid = PAID_PARALLEL.get(te);
		if(paid == null || paid <= 1) return 1;
		return Math.min(maxP, paid);
	}

	public static long scalePowerLong(long base, int n) {
		return n <= 1 ? base : (long) Math.ceil(base * POWER_PENALTY * n);
	}

	public static int scalePowerInt(int base, int n) {
		return n <= 1 ? base : (int) Math.ceil(base * POWER_PENALTY * n);
	}

	public static int fitItemSlot(ItemStack slot, ItemStack out, int maxN) {
		if(out == null || out.stackSize <= 0) return maxN;
		int maxStack = out.getMaxStackSize();
		if(slot == null) {
			return Math.min(maxN, maxStack / out.stackSize);
		}
		if(slot.getItem() != out.getItem()
				|| slot.getItemDamage() != out.getItemDamage()
				|| !ItemStack.areItemStackTagsEqual(slot, out)) {
			return 0;
		}
		int room = Math.min(slot.getMaxStackSize(), maxStack) - slot.stackSize;
		return room <= 0 ? 0 : Math.min(maxN, room / out.stackSize);
	}

	private static int fitFluidTank(FluidTank tank, FluidType type, int amountPerOp, int maxN) {
		if(amountPerOp <= 0) return maxN;
		if(tank.getFill() > 0 && type != null && tank.getTankType() != type) return 0;
		int room = tank.getMaxFill() - tank.getFill();
		return room <= 0 ? 0 : Math.min(maxN, room / amountPerOp);
	}

	private static int fitPowerLong(long currentPower, long baseCost, int maxN, boolean strictGreater) {
		if(baseCost <= 0) return maxN;
		for(int n = maxN; n >= 2; n--) {
			long req = scalePowerLong(baseCost, n);
			if(strictGreater ? (currentPower > req) : (currentPower >= req)) return n;
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

	/* ========================== 1. 结晶器 (Crystallizer) ========================== */

	public static int scaleCrystallizerPower(TileEntityMachineCrystallizer te, int base) {
		if(te == null || te.slots[0] == null) {
			recordPaidParallel(te, 1);
			return base;
		}
		int maxP = getMaxParallel(te.slots, 5, 6);
		if(maxP <= 1) {
			recordPaidParallel(te, 1);
			return base;
		}
		CrystallizerRecipe res = CrystallizerRecipes.getOutput(te.slots[0], te.tank.getTankType());
		if(res == null) {
			recordPaidParallel(te, 1);
			return base;
		}
		int n = fitPowerLong(te.power, base, maxP, false);
		n = Math.min(n, te.slots[0].stackSize / Math.max(1, res.itemAmount));
		int reqAcid = te.getRequiredAcid(res.acidAmount);
		if(reqAcid > 0) n = Math.min(n, te.tank.getFill() / reqAcid);
		n = Math.min(n, fitItemSlot(te.slots[2], res.output, n));
		n = Math.max(1, n);
		recordPaidParallel(te, n);
		return scalePowerInt(base, n);
	}

	public static void completeCrystallizerExtra(TileEntityMachineCrystallizer te) {
		if(te == null) return;
		int maxP = getMaxParallel(te.slots, 5, 6);
		int limit = getPaidParallel(te, maxP);
		if(limit <= 1) return;
		try {
			if(mCrystallizerProcess == null) {
				mCrystallizerProcess = TileEntityMachineCrystallizer.class.getDeclaredMethod("processItem");
				mCrystallizerProcess.setAccessible(true);
			}
			for(int k = 1; k < limit; k++) {
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

	/* ========================== 2. 混合机 (Mixer) ========================== */

	public static int scaleMixerConsumption(TileEntityMachineMixer te, int base) {
		if(te == null) return base;
		int maxP = getMaxParallel(te.slots, 3, 4);
		if(maxP <= 1) {
			recordPaidParallel(te, 1);
			return base;
		}
		MixerRecipe[] recipes = MixerRecipes.getOutput(te.tanks[2].getTankType());
		if(recipes == null || recipes.length <= 0) {
			recordPaidParallel(te, 1);
			return base;
		}
		MixerRecipe rec = recipes[te.recipeIndex % recipes.length];
		if(rec == null) {
			recordPaidParallel(te, 1);
			return base;
		}
		int n = fitPowerLong(te.power, base, maxP, false);
		if(rec.input1 != null) n = Math.min(n, te.tanks[0].getFill() / Math.max(1, rec.input1.fill));
		if(rec.input2 != null) n = Math.min(n, te.tanks[1].getFill() / Math.max(1, rec.input2.fill));
		if(rec.solidInput != null) {
			if(te.slots[1] == null || !rec.solidInput.matchesRecipe(te.slots[1], true)) {
				recordPaidParallel(te, 1);
				return base;
			}
			n = Math.min(n, te.slots[1].stackSize / Math.max(1, rec.solidInput.stacksize));
		}
		n = Math.min(n, fitFluidTank(te.tanks[2], te.tanks[2].getTankType(), rec.output, n));
		n = Math.max(1, n);
		recordPaidParallel(te, n);
		return scalePowerInt(base, n);
	}

	public static void completeMixerExtra(TileEntityMachineMixer te) {
		if(te == null) return;
		int maxP = getMaxParallel(te.slots, 3, 4);
		int limit = getPaidParallel(te, maxP);
		boolean didExtra = false;
		for(int k = 1; k < limit; k++) {
			if(!te.canProcess()) break;
			MixerRecipe[] recipes = MixerRecipes.getOutput(te.tanks[2].getTankType());
			if(recipes == null || recipes.length <= 0) break;
			MixerRecipe rec = recipes[te.recipeIndex % recipes.length];
			if(rec == null) break;
			if(rec.input1 != null) te.tanks[0].setFill(te.tanks[0].getFill() - rec.input1.fill);
			if(rec.input2 != null) te.tanks[1].setFill(te.tanks[1].getFill() - rec.input2.fill);
			if(rec.solidInput != null) te.decrStackSize(1, rec.solidInput.stacksize);
			te.tanks[2].setFill(te.tanks[2].getFill() + rec.output);
			didExtra = true;
		}
		if(didExtra) te.markDirty();
	}

	/* ========================== 3. 压缩机 (CompressorBase) ========================== */

	public static int scaleCompressorPower(TileEntityMachineCompressorBase te, int base) {
		if(te == null) return base;
		int maxP = getMaxParallel(te.slots, 2, 3);
		if(maxP <= 1) {
			recordPaidParallel(te, 1);
			return base;
		}
		CompressorRecipe rec = CompressorRecipes.recipes.get(new Pair<FluidType, Integer>(te.tanks[0].getTankType(), te.tanks[0].getPressure()));
		int inAmt = rec == null ? 1000 : rec.inputAmount;
		int outAmt = rec == null ? 1000 : rec.output.fill;
		// 压缩机 canProcess() 判停条件为 power <= powerRequirement，故这里必须使用严格大于 (strictGreater = true)
		int n = fitPowerLong(te.power, base, maxP, true);
		n = Math.min(n, te.tanks[0].getFill() / Math.max(1, inAmt));
		n = Math.min(n, fitFluidTank(te.tanks[1], te.tanks[1].getTankType(), outAmt, n));
		n = Math.max(1, n);
		recordPaidParallel(te, n);
		return scalePowerInt(base, n);
	}

	public static void completeCompressorExtra(TileEntityMachineCompressorBase te) {
		if(te == null) return;
		int maxP = getMaxParallel(te.slots, 2, 3);
		int limit = getPaidParallel(te, maxP);
		for(int k = 1; k < limit; k++) {
			if(!te.canProcess()) break;
			te.process();
		}
	}

	/* ========================== 4. 液化器 (Liquefactor) ========================== */

	public static int scaleLiquefactorUsage(TileEntityMachineLiquefactor te, int base) {
		if(te == null || te.slots[0] == null) {
			recordPaidParallel(te, 1);
			return base;
		}
		int maxP = getMaxParallel(te.slots, 2, 3);
		if(maxP <= 1) {
			recordPaidParallel(te, 1);
			return base;
		}
		FluidStack out = LiquefactionRecipes.getOutput(te.slots[0]);
		if(out == null) {
			recordPaidParallel(te, 1);
			return base;
		}
		int n = fitPowerLong(te.power, base, maxP, false);
		n = Math.min(n, te.slots[0].stackSize);
		n = Math.min(n, fitFluidTank(te.tank, out.type, out.fill, n));
		n = Math.max(1, n);
		recordPaidParallel(te, n);
		return scalePowerInt(base, n);
	}

	public static void completeLiquefactorExtra(TileEntityMachineLiquefactor te) {
		if(te == null) return;
		int maxP = getMaxParallel(te.slots, 2, 3);
		int limit = getPaidParallel(te, maxP);
		for(int k = 1; k < limit; k++) {
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

	/* ========================== 5. 固化器 (Solidifier) ========================== */

	public static int scaleSolidifierUsage(TileEntityMachineSolidifier te, int base) {
		if(te == null) return base;
		int maxP = getMaxParallel(te.slots, 2, 3);
		if(maxP <= 1) {
			recordPaidParallel(te, 1);
			return base;
		}
		Pair<Integer, ItemStack> out = SolidificationRecipes.getOutput(te.tank.getTankType());
		if(out == null) {
			recordPaidParallel(te, 1);
			return base;
		}
		int n = fitPowerLong(te.power, base, maxP, false);
		n = Math.min(n, te.tank.getFill() / Math.max(1, out.getKey()));
		n = Math.min(n, fitItemSlot(te.slots[0], out.getValue(), n));
		n = Math.max(1, n);
		recordPaidParallel(te, n);
		return scalePowerInt(base, n);
	}

	public static void completeSolidifierExtra(TileEntityMachineSolidifier te) {
		if(te == null) return;
		int maxP = getMaxParallel(te.slots, 2, 3);
		int limit = getPaidParallel(te, maxP);
		for(int k = 1; k < limit; k++) {
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

	/* ========================== 6. 电炉 (ElectricFurnace) ========================== */

	public static int scaleElectricFurnaceConsumption(TileEntityMachineElectricFurnace te, int base) {
		if(te == null || te.slots[1] == null) {
			recordPaidParallel(te, 1);
			return base;
		}
		int maxP = getMaxParallel(te.slots, 3, 3);
		if(maxP <= 1) {
			recordPaidParallel(te, 1);
			return base;
		}
		ItemStack res = FurnaceRecipes.smelting().getSmeltingResult(te.slots[1]);
		if(res == null) {
			recordPaidParallel(te, 1);
			return base;
		}
		int n = fitPowerLong(te.power, base, maxP, false);
		n = Math.min(n, te.slots[1].stackSize);
		n = Math.min(n, fitItemSlot(te.slots[2], res, n));
		n = Math.max(1, n);
		recordPaidParallel(te, n);
		return scalePowerInt(base, n);
	}

	public static void completeElectricFurnaceExtra(TileEntityMachineElectricFurnace te) {
		if(te == null) return;
		int maxP = getMaxParallel(te.slots, 3, 3);
		int limit = getPaidParallel(te, maxP);
		if(limit <= 1) return;
		try {
			if(mElectricFurnaceProcess == null) {
				mElectricFurnaceProcess = TileEntityMachineElectricFurnace.class.getDeclaredMethod("processItem");
				mElectricFurnaceProcess.setAccessible(true);
			}
			for(int k = 1; k < limit; k++) {
				if(te.slots[1] == null || !te.canProcess()) break;
				ItemStack res = FurnaceRecipes.smelting().getSmeltingResult(te.slots[1]);
				// 校验单次产出数量与 NBT，防止多产熔炼配方把输出槽堆超过 64 或吞 NBT
				if(res == null || fitItemSlot(te.slots[2], res, 1) < 1) break;
				mElectricFurnaceProcess.invoke(te);
			}
		} catch(Exception e) {
			LOG.warn("[MNGA-Parallel] ElectricFurnace 并行结算异常", e);
		}
	}
}
