package com.makentmgreatagain.items;

import com.hbm.inventory.fluid.FluidType;

/**
 * 流体销毁罐。
 *
 * 本身不存流体、不实现 IFillableItem —— 交互全部由 FluidLoaderDestroyer 处理，
 * 因为只有 FluidLoadingHandler 才能拿到机器的 ItemStack[] 槽位、把罐子本身删掉。
 *
 * 说明文字走 {@link ItemWithDesc}：语言文件 item.inf_destroy.desc 里的 %s
 * 由构造函数交给基类填（就是下面的 rate），不必再自己重写 addInformation。
 *
 * 参数含义：
 *   rate                  每 tick 最多销毁多少 mB
 *   diesToCorrosive       遇到「普通腐蚀性」流体是否自毁
 *   diesToHighlyCorrosive 遇到「强腐蚀性」流体是否自毁
 *   canDestroyAntimatter  能否销毁反物质（不能则完全不理反物质）
 */
public class ItemFluidDestroyer extends ItemWithDesc {

	public final int rate;
	public final boolean diesToCorrosive;
	public final boolean diesToHighlyCorrosive;
	public final boolean canDestroyAntimatter;

	public ItemFluidDestroyer(int rate, boolean diesToCorrosive, boolean diesToHighlyCorrosive, boolean canDestroyAntimatter) {
		this.rate = rate;
		this.diesToCorrosive = diesToCorrosive;
		this.diesToHighlyCorrosive = diesToHighlyCorrosive;
		this.canDestroyAntimatter = canDestroyAntimatter;
		this.setMaxStackSize(1);
		// 描述里的 "%smB/tick" 用的就是这个 rate
		this.setDescArgs(rate);
	}

	/** 这种流体是否会把这个罐子吃掉 */
	public boolean isDestroyedBy(FluidType type) {
		if(type == null) return false;
		if(this.diesToHighlyCorrosive && type.isHighlyCorrosive()) return true;
		if(this.diesToCorrosive && type.isCorrosive()) return true;
		return false;
	}

	/** 对某种流体的实际销毁速率（反物质只有 10%） */
	public int getRateFor(FluidType type) {
		if(type != null && type.isAntimatter()) return Math.max(1, this.rate / 10);
		return this.rate;
	}
}
