package com.makentmgreatagain.tileentity;

import com.hbm.lib.Library;
import com.hbm.tileentity.TilePort.PortDef;
import com.hbm.tileentity.machine.storage.TileEntityBarrel;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.util.AxisAlignedBB;

/**
 * 「巨尻-9000」储罐（重生版）的方块实体。
 *
 * 逐行复制自 HBM 已 @Deprecated 的 TileEntityMachineBAT9000，行为保持一致：
 *   - 容量 2,048,000 mB（介于普通储罐 256,000 与新版巨尻 16,000,000 之间）；
 *   - 8 个侧面端口（原 9000 的固定端口布局，不随朝向旋转）；
 *   - 装入反物质直接引爆。
 *
 * 界面不用重写：父类 TileEntityBarrel 实现了 IGUIProvider，
 * HBM 的 GUIHandler 是按接口通用分发的，右键核心格会自动拿到
 * 标准储罐界面（ContainerBarrel / GUIBarrel），含液体显示与进出模式切换。
 */
public class TileEntityBat9000Reborn extends TileEntityBarrel {

	public TileEntityBat9000Reborn() {
		super(2048000);
	}

	@Override
	public String getName() {
		return "container.bat9000_reborn";
	}

	@Override
	public void checkFluidInteraction() {

		if(tank.getTankType().isAntimatter()) {
			worldObj.func_147480_a(xCoord, yCoord, zCoord, false);
			worldObj.newExplosion(null, xCoord + 0.5, yCoord + 0.5, zCoord + 0.5, 10, true, true);
		}
	}

	@Override
	public PortDef[] getPorts() {
		if(cachedPorts == null) {
			cachedPorts = new PortDef[] {
					PortDef.make(xCoord + 1, yCoord, zCoord + 2, Library.POS_Z),
					PortDef.make(xCoord - 1, yCoord, zCoord + 2, Library.POS_Z),
					PortDef.make(xCoord + 1, yCoord, zCoord - 2, Library.NEG_Z),
					PortDef.make(xCoord - 1, yCoord, zCoord - 2, Library.NEG_Z),
					PortDef.make(xCoord + 2, yCoord, zCoord + 1, Library.POS_X),
					PortDef.make(xCoord - 2, yCoord, zCoord + 1, Library.NEG_X),
					PortDef.make(xCoord + 2, yCoord, zCoord - 1, Library.POS_X),
					PortDef.make(xCoord - 2, yCoord, zCoord - 1, Library.NEG_X)
			};
		}
		return cachedPorts;
	}

	AxisAlignedBB bb = null;

	@Override
	public AxisAlignedBB getRenderBoundingBox() {

		if(bb == null) {
			bb = AxisAlignedBB.getBoundingBox(
					xCoord - 2,
					yCoord,
					zCoord - 2,
					xCoord + 3,
					yCoord + 5,
					zCoord + 3
					);
		}

		return bb;
	}

	@Override
	@SideOnly(Side.CLIENT)
	public double getMaxRenderDistanceSquared() {
		return 65536.0D;
	}
}
