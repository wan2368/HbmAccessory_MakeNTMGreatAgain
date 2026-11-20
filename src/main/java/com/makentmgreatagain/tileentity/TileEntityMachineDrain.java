package com.makentmgreatagain.tileentity;

import com.hbm.blocks.ModBlocks;
import com.hbm.inventory.fluid.FluidType;
import com.hbm.inventory.fluid.Fluids;
import com.hbm.inventory.fluid.tank.FluidTank;
import com.hbm.inventory.fluid.trait.FT_Flammable;
import com.hbm.inventory.fluid.trait.FluidTrait;
import com.hbm.inventory.fluid.trait.FluidTrait.FluidReleaseType;
import com.hbm.inventory.fluid.trait.FluidTraitSimple.FT_Amat;
import com.hbm.inventory.fluid.trait.FluidTraitSimple.FT_Liquid;
import com.hbm.inventory.fluid.trait.FluidTraitSimple.FT_Viscous;
import com.hbm.main.MainRegistry;
import com.hbm.tileentity.IBufPacketReceiver;
import com.hbm.tileentity.TileEntityLoadedBase;
import com.hbm.tileentity.TilePort.PortDef;

import api.hbm.energymk2.IEnergyReceiverMK2.ConnectionPriority;
import api.hbm.fluidmk2.IFluidStandardReceiverMK2;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.netty.buffer.ByteBuf;
import net.minecraft.block.Block;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraftforge.common.util.ForgeDirection;

/**
 * 排液管核心，逻辑移植自本体 {@code TileEntityMachineDrain}。
 *
 * <p>罐容量由方块传入：钢制 1,000 mB、钨制 2,000 mB；罐里有东西就每 tick 排掉一半（至少 1 mB），
 * 排出的流体交给 {@link FluidTrait#onRelease} 走原版的溢流处理。
 * 反物质直接炸，粘稠可燃液体有概率在地面留下油污。</p>
 *
 * <p>接口规则与本体一致：{@link #canConnect} 拒绝顶面和底面，四个侧面都能接。
 * 液体特效的发射点改在方块正底部中心（原版是凸出排口的位置）。</p>
 */
public class TileEntityMachineDrain extends TileEntityLoadedBase implements IFluidStandardReceiverMK2, IBufPacketReceiver {

	/** 默认罐容量（钢制）；钨制方块会传入 2,000 */
	public static final int CAPACITY = 1_000;

	public FluidTank tank;

	public TileEntityMachineDrain() {
		this(CAPACITY);
	}

	public TileEntityMachineDrain(int capacity) {
		this.tank = new FluidTank(Fluids.NONE, capacity);
	}

	@Override
	public void updateEntity() {

		if(!worldObj.isRemote) {

			this.setupFluidPorts(getPorts());
			this.updatePortFIFO();

			networkPackNT(50);

			if(tank.getFill() > 0) {

				if(tank.getTankType().hasTrait(FT_Amat.class)) {
					worldObj.newExplosion(null, xCoord + 0.5, yCoord + 0.5, zCoord + 0.5, 10F, true, true);
					return;
				}

				int toSpill = Math.max(tank.getFill() / 2, 1);
				tank.setFill(tank.getFill() - toSpill);
				FluidTrait.onRelease(worldObj, xCoord, yCoord, zCoord, tank.getTankType(), tank, FluidReleaseType.SPILL, toSpill);
				this.markChanged();

				if(toSpill >= 100 && worldObj.rand.nextInt(20) == 0
						&& tank.getTankType().hasTrait(FT_Liquid.class)
						&& tank.getTankType().hasTrait(FT_Viscous.class)
						&& tank.getTankType().hasTrait(FT_Flammable.class)) {

					// 从方块正底部中心往下方喷，落点带随机散布（原版是从凸出排口往外喷）
					Vec3 start = Vec3.createVectorHelper(xCoord + 0.5, yCoord + 0.5, zCoord + 0.5);
					Vec3 end = start.addVector(worldObj.rand.nextGaussian() * 5, -25, worldObj.rand.nextGaussian() * 5);
					MovingObjectPosition mop = worldObj.func_147447_a(start, end, false, true, false);

					if(mop != null && mop.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK && mop.sideHit == 1) {
						Block block = worldObj.getBlock(mop.blockX, mop.blockY + 1, mop.blockZ);
						if(!block.getMaterial().isLiquid()
								&& block.isReplaceable(worldObj, mop.blockX, mop.blockY + 1, mop.blockZ)
								&& ModBlocks.oil_spill.canPlaceBlockAt(worldObj, mop.blockX, mop.blockY + 1, mop.blockZ)) {
							worldObj.setBlock(mop.blockX, mop.blockY + 1, mop.blockZ, ModBlocks.oil_spill);
						}
					}
				}
			}

		} else if(tank.getFill() > 0 && MainRegistry.proxy.me().getDistance(xCoord, yCoord, zCoord) < 100) {

			// 管口正下方被实心方块顶住时液体流不下去，只在管口滴少量粒子
			Block below = worldObj.getBlock(xCoord, yCoord - 1, zCoord);
			boolean blocked = below.isSideSolid(worldObj, xCoord, yCoord - 1, zCoord, ForgeDirection.UP);

			// 下方是完整方块时管口被彻底堵死，一点效果都不产生
			if(blocked && isFullBlock(below, xCoord, yCoord - 1, zCoord)) return;

			if(blocked && worldObj.rand.nextInt(4) != 0) return;

			NBTTagCompound data = new NBTTagCompound();
			if(hasTraitNamed(tank.getTankType(), "gaseous")) {
				data.setString("type", "tower");
				data.setFloat("lift", 0.5F);
				data.setFloat("base", 0.375F);
				data.setFloat("max", 3F);
				data.setInteger("life", 100 + worldObj.rand.nextInt(50));
			} else {
				data.setString("type", "splash");
			}

			data.setInteger("color", tank.getTankType().getColor());
			// 方块正底部中心（x+0.5 / z+0.5 / y），原版的凸出排口偏移已去掉；
			// 被挡住时让液滴在管口范围内随机散开，看起来像从孔里渗出来
			double offX = blocked ? (worldObj.rand.nextDouble() - 0.5D) * 0.4D : 0D;
			double offZ = blocked ? (worldObj.rand.nextDouble() - 0.5D) * 0.4D : 0D;
			data.setDouble("posX", xCoord + 0.5 + offX);
			data.setDouble("posZ", zCoord + 0.5 + offZ);
			data.setDouble("posY", yCoord);

			MainRegistry.proxy.effectNT(data);
		}
	}

	/**
	 * 按注册名判断流体特性（"gaseous" 等）。不直接引用 FT_Gaseous 这类内部类：
	 * 航天版 HBM 删除/合并了部分简单特性类，硬引用会在运行时抛 NoClassDefFoundError；
	 * 特性注册名在各版本间稳定，按名查询两边都能跑。
	 */
	private static boolean hasTraitNamed(FluidType type, String traitName) {
		Class<? extends FluidTrait> clazz = FluidTrait.traitNameMap.get(traitName);
		return clazz != null && type.hasTrait(clazz);
	}

	/** 该位置的方块碰撞箱是否正好占满整格（台阶、栅栏这类不算） */
	private boolean isFullBlock(Block block, int x, int y, int z) {
		AxisAlignedBB box = block.getCollisionBoundingBoxFromPool(this.worldObj, x, y, z);
		return box != null
				&& box.minX <= x && box.maxX >= x + 1
				&& box.minY <= y && box.maxY >= y + 1
				&& box.minZ <= z && box.maxZ >= z + 1;
	}

	protected PortDef[] cachedPorts;

	/**
	 * 单方块，四个水平方向各一个连接点；顶面和底面不接。
	 * {@link #canConnect} 只在管网建立时做最终筛选，这里把四个侧面都登记上。
	 */
	public PortDef[] getPorts() {

		if(cachedPorts == null) {
			cachedPorts = new PortDef[] {
					PortDef.make(xCoord, yCoord, zCoord,
							ForgeDirection.NORTH, ForgeDirection.SOUTH, ForgeDirection.EAST, ForgeDirection.WEST)
			};
		}

		return cachedPorts;
	}

	@Override
	public void readFromNBT(NBTTagCompound nbt) {
		super.readFromNBT(nbt);
		this.tank.readFromNBT(nbt, "t");
	}

	@Override
	public void writeToNBT(NBTTagCompound nbt) {
		super.writeToNBT(nbt);
		this.tank.writeToNBT(nbt, "t");
	}

	@Override public void serialize(ByteBuf buf) { tank.serialize(buf); }
	@Override public void deserialize(ByteBuf buf) { tank.deserialize(buf); }

	@Override public FluidTank[] getAllTanks() { return new FluidTank[] {tank}; }
	@Override public FluidTank[] getReceivingTanks() { return new FluidTank[] {tank}; }
	@Override public ConnectionPriority getFluidPriority() { return ConnectionPriority.LOW; }

	@Override
	public boolean canConnect(FluidType type, ForgeDirection dir) {
		return dir != ForgeDirection.UP && dir != ForgeDirection.DOWN;
	}

	AxisAlignedBB bb = null;

	@Override
	public AxisAlignedBB getRenderBoundingBox() {

		if(bb == null) {
			bb = AxisAlignedBB.getBoundingBox(
					xCoord - 2,
					yCoord - 1,
					zCoord - 2,
					xCoord + 3,
					yCoord + 1,
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
