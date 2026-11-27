package com.makentmgreatagain.tileentity;

import com.hbm.inventory.fluid.tank.FluidTank;
import com.hbm.tileentity.TileEntityProxyCombo;

import api.hbm.energymk2.IEnergyProviderMK2;
import api.hbm.fluidmk2.IFluidStandardSenderMK2;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.ForgeDirection;

/**
 * 大型火力发电站的接口方块：库存和流体输入由代理基类提供，
 * 电力与流体输出委托给实现对应发送接口的核心。
 */
public class TileEntityLargeThermalPowerStationPort extends TileEntityProxyCombo
		implements IEnergyProviderMK2, IFluidStandardSenderMK2 {

	public TileEntityLargeThermalPowerStationPort() {
		super();
		this.inventory().fluid().power();
	}

	@Override
	public void readFromNBT(NBTTagCompound nbt) {
		super.readFromNBT(nbt);
		this.inventory().fluid().power();
	}

	@Override
	public boolean canConnect(ForgeDirection dir) {
		return dir != ForgeDirection.UNKNOWN;
	}

	@Override
	public long getReceiverSpeed() {
		return 0;
	}

	@Override
	public long transferPower(long power) {
		return power;
	}

	@Override
	public long getPower() {
		TileEntity core = this.getTile();
		return core instanceof IEnergyProviderMK2 ? ((IEnergyProviderMK2) core).getPower() : 0;
	}

	@Override
	public void setPower(long power) {
		TileEntity core = this.getTile();
		if(core instanceof IEnergyProviderMK2) ((IEnergyProviderMK2) core).setPower(power);
	}

	@Override
	public long getMaxPower() {
		TileEntity core = this.getTile();
		return core instanceof IEnergyProviderMK2 ? ((IEnergyProviderMK2) core).getMaxPower() : 0;
	}

	@Override
	public FluidTank[] getSendingTanks() {
		TileEntity core = this.getTile();
		return core instanceof IFluidStandardSenderMK2
				? ((IFluidStandardSenderMK2) core).getSendingTanks()
				: new FluidTank[0];
	}
}
