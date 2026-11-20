package com.makentmgreatagain.blocks;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;

/**
 * 空深海结核混合物。
 *
 * <p>结核矿被采空（30 次）后留下的空壳。海面立管钻机停在空结核上方时，
 * 会先继续开采它周围范围内的其他结核；周围采空后才重新向下钻。</p>
 */
public class BlockEmptyDeepSeaNodule extends Block {

	public BlockEmptyDeepSeaNodule() {
		super(Material.rock);
		this.setStepSound(soundTypeGravel);
		this.setHardness(1.5F);
		this.setResistance(4.0F);
	}
}
