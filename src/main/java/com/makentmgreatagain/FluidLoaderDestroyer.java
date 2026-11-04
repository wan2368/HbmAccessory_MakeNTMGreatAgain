package com.makentmgreatagain;

import com.hbm.inventory.fluid.FluidType;
import com.hbm.inventory.fluid.Fluids;
import com.hbm.inventory.fluid.tank.FluidLoadingHandler;
import com.hbm.inventory.fluid.tank.FluidTank;
import com.makentmgreatagain.items.ItemFluidDestroyer;

import net.minecraft.item.ItemStack;

/**
 * 销毁罐的流体交互逻辑。
 *
 * 挂在 FluidTank.loadingHandlers 末尾，只对 ItemFluidDestroyer 生效：
 *   fillItem  = 输出槽方向（罐 → 物品），这里抽干罐子、必要时删掉罐子
 *   emptyItem = 输入槽方向（物品 → 罐），什么都不做，只声明“已处理”
 *
 * 注意：位于 com.hbm.inventory.fluid.tank 之外，pressure 是 protected，
 * 必须走 getPressure()，不能直接读字段。
 */
public class FluidLoaderDestroyer extends FluidLoadingHandler {

	@Override
	public boolean fillItem(ItemStack[] slots, int in, int out, FluidTank tank) {

		ItemStack stack = slots[in];
		if(stack == null || !(stack.getItem() instanceof ItemFluidDestroyer)) return false;

		ItemFluidDestroyer item = (ItemFluidDestroyer) stack.getItem();
		FluidType type = tank.getTankType();

		// 空罐 / 罐里没东西：不动作
		if(type == null || type == Fluids.NONE || tank.getFill() <= 0) return true;

		// 反物质：只有史诗罐能动，其余罐子拒绝交互（也不会因此损毁）
		if(type.isAntimatter() && !item.canDestroyAntimatter) return true;

		// 被腐蚀吃掉：罐子直接消失，流体原样留在罐里
		if(item.isDestroyedBy(type)) {
			stack.stackSize--;
			if(stack.stackSize <= 0) slots[in] = null;
			return true;
		}

		// 加压罐不接，和 HBM 其它可填充物品保持一致
		if(tank.getPressure() != 0) return true;

		int drain = Math.min(tank.getFill(), item.getRateFor(type));
		tank.setFill(tank.getFill() - drain);

		return true;
	}

	@Override
	public boolean emptyItem(ItemStack[] slots, int in, int out, FluidTank tank) {
		ItemStack stack = slots[in];
		return stack != null && stack.getItem() instanceof ItemFluidDestroyer;
	}
}
