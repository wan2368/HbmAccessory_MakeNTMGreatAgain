package com.makentmgreatagain.fluid;

import com.hbm.inventory.fluid.trait.FluidTrait;

/**
 * 地核线的「只能用超大容器装」标记。
 *
 * <p>不重写 addInfo / addInfoHidden，也不注册进 {@code FluidTrait.traitList}，
 * 所以流体 tooltip 上不会多出任何条目 —— 它只是给
 * {@code CoreFluidTankHook} 用的一个判据。{@code FluidType.hasTrait} 走的是
 * {@code FluidType.traits} 映射，与那张 tooltip 驱动表无关，判定照常有效。</p>
 */
public class FT_CoreTank extends FluidTrait {
}
