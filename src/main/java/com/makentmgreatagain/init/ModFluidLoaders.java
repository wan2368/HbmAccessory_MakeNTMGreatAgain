package com.makentmgreatagain.init;

import com.hbm.inventory.fluid.tank.FluidLoadingHandler;
import com.hbm.inventory.fluid.tank.FluidTank;
import com.makentmgreatagain.FluidLoaderDestroyer;

/** 往 HBM 的全局流体加载器列表里挂本模组的 handler。 */
public class ModFluidLoaders {

	public static void init() {

		// 防止 /reload 或异常情况下重复挂
		for(FluidLoadingHandler handler : FluidTank.loadingHandlers) {
			if(handler instanceof FluidLoaderDestroyer) return;
		}

		// 追加在末尾：Standard / FillableItem / Infinite 都不认识这个物品，会自然落到这里
		FluidTank.loadingHandlers.add(new FluidLoaderDestroyer());
	}
}
