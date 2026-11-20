package com.makentmgreatagain.tileentity;

import com.hbm.inventory.fluid.tank.FluidTank;
import com.hbm.tileentity.TileEntityProxyCombo;

import api.hbm.energymk2.IEnergyProviderMK2;
import api.hbm.fluidmk2.IFluidStandardSenderMK2;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;

/**
 * 可控核爆炸发生器的接口方块（20 个接口共用）。
 *
 * <p>一个口同时转发<b>流体</b>、<b>物品</b>、<b>电力</b>，并且都做成双向：
 * 接收侧由 {@link TileEntityProxyCombo} 自带（转发到核心的实现），
 * 发送侧在这里补上 {@link IEnergyProviderMK2} 与 {@link IFluidStandardSenderMK2}，
 * 让 HBM 的管道、线缆既能灌进来也能抽出去。</p>
 *
 * <p>核心由 {@link com.hbm.blocks.BlockDummyable#findCore} 自动定位，不需要额外记录。</p>
 */
public class TileEntityNuclearExplosionGeneratorPort extends TileEntityProxyCombo
		implements IEnergyProviderMK2, IFluidStandardSenderMK2 {

	public TileEntityNuclearExplosionGeneratorPort() {
		super();
		this.inventory().power().fluid();
	}

	@Override
	public void readFromNBT(NBTTagCompound nbt) {
		super.readFromNBT(nbt);
		this.inventory().power().fluid();
	}

	/** 发送罐直接取自核心（核心是 IFluidStandardTransceiverMK2） */
	@Override
	public FluidTank[] getSendingTanks() {

		TileEntity core = this.getTile();

		if(core instanceof IFluidStandardSenderMK2)
			return ((IFluidStandardSenderMK2) core).getSendingTanks();

		return new FluidTank[0];
	}
}
