package com.makentmgreatagain.tileentity;

import com.hbm.tileentity.TileEntityProxyCombo;

import net.minecraft.nbt.NBTTagCompound;

/**
 * 大型催化化工厂的接口方块（顶面 12 个 + 前后 24 个，共 36 个）。
 *
 * <p>同时转发<b>流体</b>、<b>库存</b>与<b>电力</b>，所以一个接口既能接流体管道，
 * 也能接物品物流，还能接电线。</p>
 *
 * <p>覆盖 {@link #readFromNBT}：代理的三种转发能力是运行时字段，不写进存档的话
 * 读档后接口会退化成「什么都不通」，必须把三个开关重新打开。</p>
 */
public class TileEntityLargeCatalyticChemicalFactoryPort extends TileEntityProxyCombo {

	public TileEntityLargeCatalyticChemicalFactoryPort() {
		super();
		this.inventory().power().fluid();
	}

	@Override
	public void readFromNBT(NBTTagCompound nbt) {
		super.readFromNBT(nbt);
		this.inventory().power().fluid();
	}
}
