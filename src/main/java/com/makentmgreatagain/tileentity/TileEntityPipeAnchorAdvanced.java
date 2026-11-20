package com.makentmgreatagain.tileentity;

import com.hbm.tileentity.network.TileEntityFluidPipeAnchor;

/**
 * 高级管锚的方块实体。
 *
 * <p>只覆盖最大连接距离，其余（流体节点、类型切换、NBT、渲染所需数据）全部沿用本体。
 * 本体 {@code TileEntityPipelineBase.canConnect} 使用两端 {@code getMaxPipeLength()} 的较小值，
 * 因此高级锚与普通 10m 管锚混连时自动回到 10m，不需要额外改连接判定。</p>
 */
public class TileEntityPipeAnchorAdvanced extends TileEntityFluidPipeAnchor {

	@Override
	public double getMaxPipeLength() {
		return 25D;
	}
}
