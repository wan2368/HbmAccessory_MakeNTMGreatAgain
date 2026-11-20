package com.makentmgreatagain.tileentity;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;

/**
 * 深海结核混合物方块实体。
 *
 * <p>只记录“这个结核还能采几次”。记录放在方块自身（TE 存档），
 * 所以拆装采集机、区块卸载重载都不会重置进度；拿镐子挖掉方块则记录随之消失。</p>
 */
public class TileEntityDeepSeaNodule extends TileEntity {

	/** 一个结核矿可开采的次数 */
	public static final int MAX_USES = 30;

	/** 剩余可开采次数 */
	public int uses = MAX_USES;

	@Override
	public void readFromNBT(NBTTagCompound nbt) {
		super.readFromNBT(nbt);
		if(nbt.hasKey("uses")) this.uses = nbt.getInteger("uses");
	}

	@Override
	public void writeToNBT(NBTTagCompound nbt) {
		super.writeToNBT(nbt);
		nbt.setInteger("uses", this.uses);
	}

	/** 是否已经采空 */
	public boolean isDepleted() {
		return this.uses <= 0;
	}
}
