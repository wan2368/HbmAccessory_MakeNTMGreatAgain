package com.makentmgreatagain.fluid;

import java.util.List;

import com.hbm.inventory.fluid.trait.FluidTrait;
import com.hbm.util.i18n.I18nUtil;

import net.minecraft.util.EnumChatFormatting;

/**
 * 磁约束储罐限制特性：带此特性的流体只能存储在磁约束容器
 * （{@code barrel_antimatter} 磁约束反物质容器、{@code machine_orbus} 重型磁约束储罐）内，
 * 存入普通桶/储罐会触发超压炸裂或泄漏；与 {@code FT_Amat} 区分开，不会错误标注为“反物质”。
 */
public class FT_MagneticTank extends FluidTrait {

	public static final String TRAIT_NAME = "magnetictank";

	public FT_MagneticTank() {
	}

	// 提示文字已按需求移除：磁约束限制只保留 gameplay 逻辑（CoreFluidTankHook），不在 tooltip 显示
	@Override
	public void addInfo(List<String> info) {
	}

	@Override
	public void addInfoHidden(List<String> info) {
	}
}
