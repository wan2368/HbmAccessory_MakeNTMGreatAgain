package com.makentmgreatagain.tileentity;

import com.hbm.tileentity.TileEntityProxyCombo;

import net.minecraft.nbt.NBTTagCompound;

/**
 * 电力高炉的接口方块（底面外圈 8 个 + 背面烟道 1 个 + 顶部上料口 1 个）。
 *
 * <p>同时转发<b>库存</b>、<b>电力</b>与<b>流体</b>，所以一个接口既能接物品物流，
 * 也能接电线，还能接流体管道。</p>
 *
 * <p>覆盖 {@link #readFromNBT}：代理的三种转发能力是运行时字段，
 * 不写进存档的话读档后接口会退化成「什么都不通」。</p>
 */
public class TileEntityElectricBlastFurnacePort extends TileEntityProxyCombo {

	public TileEntityElectricBlastFurnacePort() {
		super();
		this.inventory().power().fluid();
	}

	@Override
	public void readFromNBT(NBTTagCompound nbt) {
		super.readFromNBT(nbt);
		this.inventory().power().fluid();
	}
}
