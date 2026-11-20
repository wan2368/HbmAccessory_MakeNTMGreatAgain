package com.makentmgreatagain.hook;

import net.minecraft.block.Block;

/**
 * PRISM 辐射阻力的材质加权。
 *
 * <h2>为什么需要这个补丁</h2>
 * {@code ChunkRadiationHandlerPRISM.SubChunk#rebuild} 里每个非空气方块贡献的阻力写死成
 * {@code Math.min(block.getExplosionResistance(...), 100)}。也就是说<b>任何爆炸抗性 ≥ 100
 * 的方块在辐射面前完全等价</b>：黑曜石（1200）、加固 CMB 钢砖块（50000）、Threx（450000）
 * 三者算出来的阻力一模一样，全是 100。
 *
 * <p>这会让 Threx 这个专门为「隔绝辐射」设计、造价远超 CMB 的方块，在抗辐射维度上和
 * 普通高抗爆方块没有任何区别 —— 与设定直接冲突。</p>
 *
 * <h2>做法</h2>
 * 用 ASM 在 {@code rebuild} 里插一条 {@code DUP}（把方块引用留在栈底），再把
 * {@code Math.min(float,float)} 换成 {@link #apply(Block, float)}。这样：
 * <ul>
 *   <li>HBM 原版方块走进来，{@code apply} 原样返回被截断过的值 —— <b>HBM 的平衡一点没动</b>；</li>
 *   <li>Threx 走进来，返回 {@link #THREX_RESISTANCE}，绕开 100 的截断。</li>
 * </ul>
 *
 * <p>没有用「直接把上限从 100 改成更大的数」那种写法，因为那会连带把 HBM 所有高抗性方块
 * （加固 CMB、双中子素、Schrabidium 等）一起加强，等于重做 HBM 的辐射平衡。</p>
 *
 * <p>补丁本体见 {@code core/PrismThrexResistanceTransformer}。</p>
 *
 * <p><b>方向切片补偿</b>：PRISM 的 {@code getResistanceFromArray} 会把最靠近子区块
 * 边界的一层按 {@code 1/15} 权重计入，而传播衰减使用
 * {@code e^(-resistance / 10000)}。若直接返回 600，最坏方向的屏蔽砖实际只有
 * {@code 600 / 15 = 40} 阻力，墙体不同位置会出现明显方向差。下方补偿只应用于
 * Threx 系列，保留砖、玻璃、实心块三档比例；HBM 原版方块仍使用原算法。</p>
 */
public final class ThrexRadResistance {

	/**
	 * Threx 单格在 PRISM 里贡献的辐射阻力。
	 *
	 * <p>原版上限是 100，取 1000 即「同样厚度下，一块 Threx 相当于 10 块加固 CMB 钢砖块」。
	 * 这个数不是凭空定的：PRISM 的衰减是 {@code e^(-Σ阻力/10000)}，而单层墙只落在
	 * 16 个 slice 里的一个，最低权重是 {@code 1/15}。要让「几块 Threx 就能压住一整个
	 * 子区块的侧向扩散」，单块阻力必须在 10² 量级往上一档，100 做不到。</p>
	 *
	 * <p>要调强弱改这一个常量即可。改大 → 更接近「一层即完全隔绝」；改回 100 → Threx
	 * 退化成和黑曜石同级。</p>
	 */
	public static final float THREX_RESISTANCE = 1000F;
	/** Threx 屏蔽砖的辐射阻力，约为实心 Threx 的六成。 */
	public static final float THREX_SHIELD_BRICK_RESISTANCE = 600F;
	/** Threx 屏蔽玻璃的辐射阻力，低于不透明建材以保留观察窗取舍。 */
	public static final float THREX_SHIELD_GLASS_RESISTANCE = 180F;

	/**
	 * PRISM 方向切片补偿。
	 *
	 * <p>取 1,500 的依据是：最坏方向只有 1/15 权重，且 PRISM 衰减
	 * 分母为 10,000；屏蔽砖的最小有效阻力约为
	 * {@code 600 * 1500 / 15 = 60,000}，单层穿透率约为 {@code e^-6}。
	 * 屏蔽玻璃仍按 180/600 的比例明显弱于砖。</p>
	 */
	public static final float PRISM_DIRECTIONAL_COMPENSATION = 1_500F;

	/**
	 * Threx 方块实例。
	 *
	 * <p>不直接写 {@code ModBlocks.block_threx} 是有意的：{@code rebuild} 在世界加载后
	 * 才会被调用，那时字段一定就绪；但如果哪天有人在更早的时机触发了一次重建，
	 * 读到 {@code ModBlocks} 的类初始化链就会在非预期线程炸开。改由
	 * {@code CommonProxy#postInit} 显式注入，注入前这里为 {@code null}，
	 * 退化成原版行为，不会报错。</p>
	 */
	public static Block threxBlock;
	public static Block threxShieldBrickBlock;
	public static Block threxShieldGlassBlock;

	private ThrexRadResistance() {
	}

	/**
	 * 替换 {@code Math.min(block.getExplosionResistance(...), 100)} 的结果。
	 *
	 * <p>栈上形态：{@code DUP} 之后是 {@code [Block, float, 100.0F]}，{@code Math.min}
	 * 吃掉两个 float 压回一个，于是变成 {@code [Block, float]}，正好是这个方法的入参。
	 * 返回值仍是一个 float，对上层 {@code xResist[iX] += resistance} 完全透明。</p>
	 *
	 * @param block   该格方块
	 * @param vanilla 原版算出来的阻力（已经被截到 100 以内）
	 * @return Threx 返回加权值，其余原样返回
	 */
	public static float apply(Block block, float vanilla) {
		if(block == null) return vanilla;
		if(block == threxBlock) return calibrated(THREX_RESISTANCE);
		if(block == threxShieldBrickBlock) return calibrated(THREX_SHIELD_BRICK_RESISTANCE);
		if(block == threxShieldGlassBlock) return calibrated(THREX_SHIELD_GLASS_RESISTANCE);
		return vanilla;
	}

	/** 把材料档位换算成 PRISM 切片阻力，其他方块不经过这里。 */
	private static float calibrated(float nominal) {
		return nominal * PRISM_DIRECTIONAL_COMPENSATION;
	}

	/**
	 * 把 PRISM 漏掉的最外层切片按最近一层的权重补回。
	 *
	 * <p>只有含有 Threx 系列高阻力的切片才补偿，普通 HBM 方块继续保持原算法。</p>
	 */
	public static float includeBoundarySlice(float[] resistance, boolean reverse, float calculated) {
		if(resistance == null || resistance.length == 0) return calculated;
		int boundary = reverse ? resistance.length - 1 : 0;
		float edge = resistance[boundary];
		float threxGlass = THREX_SHIELD_GLASS_RESISTANCE * PRISM_DIRECTIONAL_COMPENSATION;
		if(edge < threxGlass * 0.5F) return calculated;
		return calculated + edge / 15F;
	}
}
