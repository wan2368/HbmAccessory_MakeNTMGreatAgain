package com.makentmgreatagain.tileentity;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;

/**
 * 成型壳的 TE：记录被替换前的原零件方块与控制器坐标，供拆解还原。
 *
 * <p>每 20 tick 自检：控制器未成型或已不存在→把自身还原成原零件（照 PWR）。</p>
 */
public class TileEntityBatteryArrayShell extends TileEntity implements api.hbm.energymk2.IEnergyConnectorMK2 {

	public Block orig;
	public int coreX, coreY, coreZ;

	/**
	 * 线缆连接判定第二层（{@code Library.canConnect} 查完 Block 再查 TE）。
	 * 只有端口（meta=1）允许连；实际输电由控制器的 PowerNode 在端口坐标完成。
	 */
	@Override
	public boolean canConnect(net.minecraftforge.common.util.ForgeDirection dir) {
		return dir != net.minecraftforge.common.util.ForgeDirection.UNKNOWN
				&& this.getBlockMetadata() == 1;
	}

	@Override
	public void updateEntity() {
		if(worldObj.isRemote) return;
		if(worldObj.getTotalWorldTime() % 20 != 0 || orig == null) return;

		TileEntity core = worldObj.getTileEntity(coreX, coreY, coreZ);
		boolean gone;
		if(core instanceof TileEntityBatteryArray) {
			gone = !((TileEntityBatteryArray) core).assembled;
		} else {
			gone = worldObj.getChunkProvider().chunkExists(coreX >> 4, coreZ >> 4);
		}
		if(gone) {
			this.getBlockType().breakBlock(worldObj, xCoord, yCoord, zCoord, this.getBlockType(), this.getBlockMetadata());
		}
	}

	@Override
	public void readFromNBT(NBTTagCompound nbt) {
		super.readFromNBT(nbt);
		orig = Block.getBlockById(nbt.getInteger("orig"));
		if(orig == Blocks.air) orig = null;
		coreX = nbt.getInteger("cX");
		coreY = nbt.getInteger("cY");
		coreZ = nbt.getInteger("cZ");
	}

	@Override
	public void writeToNBT(NBTTagCompound nbt) {
		super.writeToNBT(nbt);
		if(orig != null) nbt.setInteger("orig", Block.getIdFromBlock(orig));
		nbt.setInteger("cX", coreX);
		nbt.setInteger("cY", coreY);
		nbt.setInteger("cZ", coreZ);
	}

	@Override
	public void markDirty() {
		if(worldObj != null) worldObj.markTileEntityChunkModified(xCoord, yCoord, zCoord, this);
	}
}