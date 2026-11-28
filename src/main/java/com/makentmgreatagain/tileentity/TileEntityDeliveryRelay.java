package com.makentmgreatagain.tileentity;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.Vec3;
import net.minecraftforge.common.util.ForgeDirection;

/**
 * 投送中继节点 TileEntity：
 * - 无 tick 开销（canUpdate() == false），仅在加载/卸载时自动登记到 DeliveryRelayNetwork；
 * - 支持 6 方向贴面安装，360° 全向光学核心位于贴面法线方向 0.625 格处。
 */
public class TileEntityDeliveryRelay extends TileEntity {

	@Override
	public boolean canUpdate() {
		return false; // 零每 tick 开销！
	}

	@Override
	public void validate() {
		super.validate();
		if(worldObj != null) {
			DeliveryRelayNetwork.register(this);
		}
	}

	@Override
	public void invalidate() {
		DeliveryRelayNetwork.unregister(this);
		super.invalidate();
	}

	@Override
	public void onChunkUnload() {
		DeliveryRelayNetwork.unregister(this);
		super.onChunkUnload();
	}

	public ForgeDirection getFacing() {
		int meta = this.worldObj != null ? this.getBlockMetadata() : 1;
		return ForgeDirection.getOrientation(meta % 6);
	}

	/** 光学棱镜核心在世界坐标中的位置（用于寻路测距与多跳激光折点）。 */
	public Vec3 getCoreWorldPos() {
		ForgeDirection f = getFacing();
		double coreHeight = 0.625D;
		return Vec3.createVectorHelper(
				xCoord + 0.5D - f.offsetX * (0.5D - coreHeight),
				yCoord + 0.5D - f.offsetY * (0.5D - coreHeight),
				zCoord + 0.5D - f.offsetZ * (0.5D - coreHeight));
	}
}
