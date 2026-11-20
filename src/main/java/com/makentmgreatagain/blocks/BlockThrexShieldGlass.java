package com.makentmgreatagain.blocks;

import com.hbm.blocks.generic.BlockNTMGlassCT;

import net.minecraft.block.material.Material;

/**
 * Threx 屏蔽玻璃：带 Threx 金属边框的连接式近透明观察窗。
 * 中心玻璃只保留极低透明度底色，连接纹理由 HBM 的 CT 系统统一拼接。
 */
public class BlockThrexShieldGlass extends BlockNTMGlassCT {

	public BlockThrexShieldGlass() {
		super(0, "makentmgreatagain:threx_shield_glass", Material.glass);
		setStepSound(soundTypeGlass);
		setHardness(8.0F);
		setResistance(25000.0F);
		setLightOpacity(0);
	}
}
