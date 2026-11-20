package com.makentmgreatagain.tileentity;

import com.hbm.tileentity.TileEntityProxyCombo;

import net.minecraft.nbt.NBTTagCompound;

/**
 * 混合催化分馏塔的接口方块（8 个检修口共用）。
 *
 * <p>同时转发<b>流体</b>和<b>库存</b>，所以一个口既能接流体管道，也能接物品物流。</p>
 *
 * <p>额外覆盖 {@link #readFromNBT}：老存档里的接口方块写入过
 * {@code inv=false}（当时还只支持流体），读档后必须重新把库存转发打开，
 * 否则已放好的机器永远认不出物品接口，必须拆了重建。</p>
 */
public class TileEntityHybridTowerPort extends TileEntityProxyCombo {

	public TileEntityHybridTowerPort() {
		super();
		this.inventory().fluid();
	}

	@Override
	public void readFromNBT(NBTTagCompound nbt) {
		super.readFromNBT(nbt);
		this.inventory().fluid();
	}
}
