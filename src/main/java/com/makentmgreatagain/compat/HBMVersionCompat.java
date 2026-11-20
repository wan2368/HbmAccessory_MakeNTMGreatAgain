package com.makentmgreatagain.compat;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.hbm.blocks.ModSoundType;
import com.hbm.inventory.UpgradeManagerNT;
import com.hbm.inventory.fluid.FluidType;
import com.hbm.inventory.fluid.Fluids;
import com.hbm.inventory.fluid.tank.FluidTank;
import com.hbm.inventory.fluid.trait.FT_Corrosive;
import com.hbm.inventory.fluid.trait.FluidTrait;
import com.hbm.inventory.fluid.trait.FluidTrait.FluidReleaseType;
import com.hbm.inventory.material.NTMMaterial;
import com.hbm.inventory.material.NTMMaterial.MatTraits;
import com.hbm.inventory.recipes.loader.GenericRecipe;
import com.hbm.inventory.recipes.loader.GenericRecipes;
import com.hbm.inventory.recipes.loader.SerializableRecipe;
import com.hbm.items.machine.ItemMachineUpgrade.UpgradeType;

import net.minecraft.block.Block;
import net.minecraft.world.World;

/**
 * 跨 HBM 版本兼容层（编译目标 X5852+ ⇄ 旧版 X5687 等）。
 *
 * <p>背景：附属按新版 HBM 源码编译，但部分 API 在稍旧的构建里不存在或签名不同，
 * 直接引用会在旧版上抛 {@code NoSuchFieldError}/{@code NoSuchMethodError}。
 * 本类把这些差异收敛成「两版都存在的 API + 反射探测」，调用方一律走这里，
 * 不要再直接引用下列新版成员：</p>
 * <ul>
 *   <li>{@code ModSoundTypes.platemetal}（旧版只有 grate/pipe）→ {@link #plateMetalSound()}</li>
 *   <li>{@code FluidType.isHighlyCorrosive()}（旧版在 FT_Corrosive 上）→ {@link #isHighlyCorrosive(FluidType)}</li>
 *   <li>{@code FluidTrait.onRelease(...)}（旧版只有实例方法 onFluidRelease）→ {@link #onFluidRelease}</li>
 *   <li>{@code UpgradeManagerNT.getLevel} 返回 int（旧版返回 Integer）→ {@link #upgradeLevel}</li>
 *   <li>{@code NTMMaterial.m()/.n()}（旧版无此二法）→ {@link #markMetal}/{@link #markNonMetal}（setTraits 两版都有）</li>
 *   <li>{@code GenericRecipes.nameToRecipeGlobal}（旧版无全局索引）→ {@link #findRecipeByName}/{@link #removeGlobalIndex}</li>
 * </ul>
 *
 * <p>探测全部用反射 + 一次性缓存，探测失败自动走旧版等价路径；
 * 新版上行为与直接调用完全一致。</p>
 */
public final class HBMVersionCompat {

	private HBMVersionCompat() { }

	// ================= 音效：金属板 =================

	private static Block.SoundType plateMetalSound;
	private static boolean plateMetalSoundResolved;

	/** 金属板脚步声。新版取 ModSoundTypes.platemetal；旧版没有该字段，
	 *  用两版都存在的 placeBreakStep 工厂按同名音效事件构造，听感一致。 */
	public static Block.SoundType plateMetalSound() {
		if(!plateMetalSoundResolved) {
			plateMetalSoundResolved = true;
			try {
				java.lang.reflect.Field f = Class.forName("com.hbm.blocks.ModSoundTypes").getField("platemetal");
				plateMetalSound = (Block.SoundType) f.get(null);
			} catch(Throwable t) {
				plateMetalSound = ModSoundType.placeBreakStep(
						"hbm:block.platemetalPlace", "hbm:block.platemetalPlace", "hbm:step.platemetal", 1.0F, 1.0F);
			}
		}
		return plateMetalSound;
	}

	// ================= 流体：强腐蚀判定 =================

	/** 新版 FluidType.isHighlyCorrosive() 的等价实现：
	 *  直接查 FT_Corrosive（两版都有 isHighlyCorrosive，阈值 rating>50）。 */
	public static boolean isHighlyCorrosive(FluidType type) {
		if(type == null) return false;
		return type.hasTrait(FT_Corrosive.class) && type.getTrait(FT_Corrosive.class).isHighlyCorrosive();
	}

	// ================= 流体：释放结算（辐射/污染） =================

	/** 新版静态 FluidTrait.onRelease 的等价实现：遍历该流体全部 trait，
	 *  逐个调实例方法 onFluidRelease（两版签名一致）。 */
	public static void onFluidRelease(World world, int x, int y, int z, FluidType type, FluidTank tank, FluidReleaseType release, int mB) {
		if(type == null) return;
		for(Object traitObj : type.traits.values()) {
			if(traitObj instanceof FluidTrait) {
				((FluidTrait) traitObj).onFluidRelease(world, x, y, z, tank, mB, release);
			}
		}
	}

	// ================= 升级等级（int/Integer 装箱差异） =================

	/** 旧版 getLevel 返回 Integer、新版返回 int，且 upgrades 字段的声明类型
	 *  两版不同（Map vs HashMap），直接引用字段/方法都会因描述符不匹配抛错。
	 *  这里反射按字段名取 upgrades 表（反射不校验声明类型），语义与 getLevel 一致（缺省 0）。 */
	@SuppressWarnings("rawtypes")
	public static int upgradeLevel(UpgradeManagerNT manager, UpgradeType type) {
		if(manager == null || type == null) return 0;
		try {
			java.lang.reflect.Field f = UpgradeManagerNT.class.getField("upgrades");
			Object value = ((Map) f.get(manager)).get(type);
			return value instanceof Number ? ((Number) value).intValue() : 0;
		} catch(Throwable t) {
			return 0;
		}
	}

	// ================= 材质金属/非金属标记 =================

	/** 等价于新版 .m()：setTraits 两版都存在且语义相同（往 traits 集合里加）。 */
	public static NTMMaterial markMetal(NTMMaterial mat) {
		return mat.setTraits(MatTraits.METAL);
	}

	/** 等价于新版 .n()。 */
	public static NTMMaterial markNonMetal(NTMMaterial mat) {
		return mat.setTraits(MatTraits.NONMETAL);
	}

	// ================= 全局配方索引 =================

	/** 新版有静态 nameToRecipeGlobal；旧版没有。
	 *  旧版路径：遍历 SerializableRecipe.recipeHandlers 里的 GenericRecipes 表按名查。 */
	@SuppressWarnings("rawtypes")
	public static GenericRecipe findRecipeByName(String name) {
		if(name == null) return null;

		Map global = globalIndex();
		if(global != null) return (GenericRecipe) global.get(name);

		for(SerializableRecipe handler : SerializableRecipe.recipeHandlers) {
			if(handler instanceof GenericRecipes) {
				GenericRecipe recipe = (GenericRecipe) ((GenericRecipes) handler).recipeNameMap.get(name);
				if(recipe != null) return recipe;
			}
		}
		return null;
	}

	/** 摘配方时同步清理全局索引（新版有、旧版无——旧版 register 不写全局索引，无需清理）。 */
	@SuppressWarnings("rawtypes")
	public static void removeGlobalIndex(String name) {
		Map global = globalIndex();
		if(global != null) global.remove(name);
	}

	private static Map<?, ?> globalIndexCache;
	private static boolean globalIndexResolved;

	@SuppressWarnings("rawtypes")
	private static Map globalIndex() {
		if(!globalIndexResolved) {
			globalIndexResolved = true;
			try {
				java.lang.reflect.Field f = GenericRecipes.class.getField("nameToRecipeGlobal");
				globalIndexCache = (Map) f.get(null);
			} catch(Throwable t) {
				globalIndexCache = null;
			}
		}
		return globalIndexCache;
	}

	// ================= 热解配方输入（旧版 in() 返回 void，不能链式） =================

	/** 等价于新版 PyroOvenRecipe.in(FluidStack)：直接写 public 字段，两版通用。 */
	public static com.hbm.inventory.recipes.PyroOvenRecipes.PyroOvenRecipe pyroIn(
			com.hbm.inventory.recipes.PyroOvenRecipes.PyroOvenRecipe recipe, com.hbm.inventory.FluidStack stack) {
		recipe.inputFluid = stack;
		return recipe;
	}

	/** 等价于新版 PyroOvenRecipe.in(AStack)：直接写 public 字段，两版通用。 */
	public static com.hbm.inventory.recipes.PyroOvenRecipes.PyroOvenRecipe pyroIn(
			com.hbm.inventory.recipes.PyroOvenRecipes.PyroOvenRecipe recipe, com.hbm.inventory.RecipesCommon.AStack stack) {
		recipe.inputItem = stack;
		return recipe;
	}

	// ================= 四氯化钛流体兜底 =================

	/** 新版 HBM 原生有 TITANIUM_TETRACHLORIDE（id 156），旧版没有。
	 *  一律经此方法取用，不要直接引用 Fluids.TITANIUM_TETRACHLORIDE。
	 *  补注册由 {@link com.makentmgreatagain.init.HBMCompat#ensureTitaniumTetrachloride()} 完成。 */
	public static FluidType titaniumTetrachloride() {
		return Fluids.fromName("TITANIUM_TETRACHLORIDE");
	}

	/** 兜底流体是否可用（未注册/注册失败时为 NONE，调用方应跳过相关配方）。 */
	public static boolean hasTitaniumTetrachloride() {
		FluidType type = titaniumTetrachloride();
		return type != null && type != Fluids.NONE;
	}
}