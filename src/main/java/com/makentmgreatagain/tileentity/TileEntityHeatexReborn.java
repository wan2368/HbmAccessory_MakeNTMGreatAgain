package com.makentmgreatagain.tileentity;

import com.hbm.blocks.BlockDummyable;
import com.hbm.interfaces.IControlReceiver;
import com.hbm.inventory.fluid.FluidType;
import com.hbm.inventory.fluid.Fluids;
import com.hbm.inventory.fluid.tank.FluidTank;
import com.hbm.inventory.fluid.trait.FT_Coolable;
import com.hbm.inventory.fluid.trait.FT_Coolable.CoolingType;
import com.hbm.inventory.fluid.trait.FT_Heatable;
import com.hbm.inventory.fluid.trait.FT_Heatable.HeatingType;
import com.hbm.items.machine.IItemFluidIdentifier;
import com.hbm.tileentity.TileEntityMachineBase;
import com.hbm.tileentity.TilePort;
import com.hbm.tileentity.TilePort.PortDef;

import api.hbm.fluidmk2.IFluidStandardTransceiverMK2;
import api.hbm.tile.IHeatSource;
import io.netty.buffer.ByteBuf;
import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraftforge.common.util.ForgeDirection;

/**
 * 集成式换热加热器核心。
 *
 * 两套流体回路，共用同一组端口位置（靠流体类型区分网络）：
 *   tanks[0] 换热回路输入：热流体，按 FT_Coolable 冷却并回收热量
 *   tanks[1] 换热回路输出：冷却后的流体
 *   tanks[2] 加热回路输入：被加热流体（如 水），消耗机内热量升温
 *   tanks[3] 加热回路输出：加热后的流体（如 蒸汽）
 *
 * 换热回收的热量比原版换热器多 25%（HEAT_BONUS），储存在 heatEnergy 里，
 * 一部分供加热回路消耗，剩下的由顶部 3×3 热面供热管抽取。
 */
public class TileEntityHeatexReborn extends TileEntityMachineBase implements IHeatSource, IFluidStandardTransceiverMK2, IControlReceiver {

	/** 换热回路储罐容量 */
	public static final int CAPACITY = 64_000;
	/** 加热回路*/
	public static final int CAPACITY_2 = 32_000;
	/** 加热后的流体输出罐容量：产物堆积空间比输入大得多 */
	public static final int HEATED_OUTPUT_CAPACITY = 3_200_000;
	/** 集成机型的热回收加成：比普通换热器多 25% */
	public static final double HEAT_BONUS = 1.25D;
	/** 机内热量超过这个值，站在机器顶部的玩家会被点燃 */
	public static final int IGNITE_HEAT = 12_800_000;

	/** 0 换热输入 / 1 换热输出 / 2 被加热流体 / 3 加热后流体 */
	public FluidTank[] tanks = new FluidTank[4];
	public int heatEnergy;
	/** 每个换热周期最多处理多少 mB 热流体 */
	public int amountToCool = 24_000;
	/** 换热周期的 tick 间隔 */
	public int tickDelay = 1;

	public TileEntityHeatexReborn() {
		super(1);
		this.tanks[0] = new FluidTank(Fluids.COOLANT_HOT, CAPACITY);
		this.tanks[1] = new FluidTank(Fluids.COOLANT, CAPACITY);
		this.tanks[2] = new FluidTank(Fluids.NONE, CAPACITY_2);
		this.tanks[3] = new FluidTank(Fluids.NONE, HEATED_OUTPUT_CAPACITY);
	}

	@Override
	public String getName() {
		return "container.heatex_reborn";
	}

	/** 0 号槽只接受流体识别码 */
	@Override
	public boolean isItemValidForSlot(int slot, ItemStack stack) {
		return slot == 0 && stack != null && stack.getItem() instanceof IItemFluidIdentifier;
	}

	@Override
	public void updateEntity() {

		if(worldObj.isRemote) return;

		// 0 号槽里的识别码只用来切换换热回路的热流体
		this.applySlotIdentifier();

		this.setupFluidPorts();

		this.setupTanks();
		this.setupHeating();

		this.heatEnergy *= 0.999;
		this.tryConvert();
		this.tryHeat();

		// 顶部热面过热：站上机器顶部的玩家会被点燃
		if(this.heatEnergy > IGNITE_HEAT) this.igniteStandingPlayers();

		this.networkPackNT(25);
	}

	/**
	 * 站上机器顶部的玩家着火。机器沿朝向 5 格、侧向 3 格，
	 * 只检查脚底贴着顶面的实体，每 5 tick 查一次即可。
	 */
	protected void igniteStandingPlayers() {

		if(worldObj.getTotalWorldTime() % 5 != 0) return;

		ForgeDirection dir = ForgeDirection.getOrientation(this.getBlockMetadata() - BlockDummyable.offset);
		ForgeDirection rot = dir.getRotation(ForgeDirection.UP);

		double halfX = Math.abs(dir.offsetX) * 2.5D + Math.abs(rot.offsetX) * 1.5D;
		double halfZ = Math.abs(dir.offsetZ) * 2.5D + Math.abs(rot.offsetZ) * 1.5D;

		AxisAlignedBB box = AxisAlignedBB.getBoundingBox(
			xCoord + 0.5D - halfX, yCoord + 1.0D, zCoord + 0.5D - halfZ,
			xCoord + 0.5D + halfX, yCoord + 1.2D, zCoord + 0.5D + halfZ
		);

		List<EntityPlayer> players = worldObj.getEntitiesWithinAABB(EntityPlayer.class, box);
		for(EntityPlayer player : players) {
			if(player.boundingBox.minY <= yCoord + 1.05D) player.setFire(10);
		}
	}

	/** 缓存的端口坐标：输入口在核心 -2 朝向，输出口在核心 +2 朝向，各三个 */
	protected PortDef[] cachedPorts;

	public PortDef[] getPorts() {

		if(cachedPorts == null) {
			ForgeDirection dir = ForgeDirection.getOrientation(this.getBlockMetadata() - BlockDummyable.offset);
			ForgeDirection rot = dir.getRotation(ForgeDirection.UP);

			PortDef[] in = new PortDef[3];
			PortDef[] out = new PortDef[3];

			for(int a = -1; a <= 1; a++) {
				// 同一端三个口：每个储罐各占一个端口，靠流体类型区分网络
				out[a + 1] = PortDef.make(
					xCoord + dir.offsetX * 2 + rot.offsetX * a,
					yCoord,
					zCoord + dir.offsetZ * 2 + rot.offsetZ * a,
					dir);
				in[a + 1] = PortDef.make(
					xCoord - dir.offsetX * 2 + rot.offsetX * a,
					yCoord,
					zCoord - dir.offsetZ * 2 + rot.offsetZ * a,
					dir.getOpposite());
			}

			cachedPorts = new PortDef[] { PortDef.combine(in), PortDef.combine(out) };
		}

		return cachedPorts;
	}

	protected void setupFluidPorts() {

		if(this.fluidInPorts == null) {
			PortDef[] ports = getPorts();
			// 两组端口共用同一批坐标，各自挂一个储罐
			this.fluidInPorts = TilePort.manyToMany(this, ports[0], ports[0]);
			this.fluidOutPorts = TilePort.manyToMany(this, ports[1], ports[1]);
		}

		this.updatePortFIFO();
	}

	/**
	 * 0 号槽的识别码只切换换热回路的热流体：
	 * 只接受可冷却的流体，其余一律退回 NONE。类型没有变化就不动，避免每 tick 反复重建端口。
	 */
	protected void applySlotIdentifier() {

		if(slots[0] == null || !(slots[0].getItem() instanceof IItemFluidIdentifier)) return;

		FluidType type = ((IItemFluidIdentifier) slots[0].getItem()).getType(worldObj, xCoord, yCoord, zCoord, slots[0]);
		if(type == null || type == tanks[0].getTankType()) return;

		tanks[0].setTankType(isCoolable(type) ? type : Fluids.NONE);
	}

	/** 换热回路只接受带「可冷却」属性、且换热效率大于 0 的流体 */
	public static boolean isCoolable(FluidType type) {
		if(type == null || !type.hasTrait(FT_Coolable.class)) return false;
		return type.getTrait(FT_Coolable.class).getEfficiency(CoolingType.HEATEXCHANGER) > 0;
	}

	/** 换热侧：热流体必须能冷却，输出端换成它的冷却产物 */
	protected void setupTanks() {

		if(isCoolable(tanks[0].getTankType())) {
			FT_Coolable trait = tanks[0].getTankType().getTrait(FT_Coolable.class);
			tanks[1].setTankType(trait.coolsTo);
			return;
		}

		tanks[0].setTankType(Fluids.NONE);
		tanks[1].setTankType(Fluids.NONE);
	}

	/** 加热侧：被加热流体必须可加热，输出端换成它加热后的产物 */
	protected void setupHeating() {

		if(isHeatable(tanks[2].getTankType())) {
			FT_Heatable trait = tanks[2].getTankType().getTrait(FT_Heatable.class);
			tanks[3].setTankType(trait.getFirstStep().typeProduced);
			return;
		}

		tanks[2].setTankType(Fluids.NONE);
		tanks[3].setTankType(Fluids.NONE);
	}

	/** 判断某流体能不能当「被加热流体」，外部设置识别码时也用这个校验 */
	public static boolean isHeatable(FluidType type) {
		if(type == null || !type.hasTrait(FT_Heatable.class)) return false;
		return type.getTrait(FT_Heatable.class).getEfficiency(HeatingType.HEATEXCHANGER) > 0;
	}

	/** 换热回路：热流体降温，回收的热量额外 +25% */
	protected void tryConvert() {

		if(!tanks[0].getTankType().hasTrait(FT_Coolable.class)) return;

		// GUI 里的「每次换热量 / 循环间隔」直接控制换热速率
		if(tickDelay < 1) tickDelay = 1;
		if(worldObj.getTotalWorldTime() % tickDelay != 0) return;

		FT_Coolable trait = tanks[0].getTankType().getTrait(FT_Coolable.class);

		int inputOps = tanks[0].getFill() / trait.amountReq;
		int outputOps = (tanks[1].getMaxFill() - tanks[1].getFill()) / trait.amountProduced;
		int ops = Math.min(inputOps, Math.min(outputOps, amountToCool));

		if(ops <= 0) return;

		tanks[0].setFill(tanks[0].getFill() - trait.amountReq * ops);
		tanks[1].setFill(tanks[1].getFill() + trait.amountProduced * ops);
		this.heatEnergy += (int) (trait.heatEnergy * ops * trait.getEfficiency(CoolingType.HEATEXCHANGER) * HEAT_BONUS);
		this.markChanged();
	}

	/** 加热回路：消耗机内热量把被加热流体升温 */
	protected void tryHeat() {

		if(!isHeatable(tanks[2].getTankType())) return;

		FT_Heatable trait = tanks[2].getTankType().getTrait(FT_Heatable.class);
		FT_Heatable.HeatingStep step = trait.getFirstStep();

		// 效率越高，每份流体要的热量越少
		int heatPerOp = (int) Math.max(step.heatReq / trait.getEfficiency(HeatingType.HEATEXCHANGER), 1);

		int inputOps = tanks[2].getFill() / step.amountReq;
		int outputOps = (tanks[3].getMaxFill() - tanks[3].getFill()) / step.amountProduced;
		int heatOps = this.heatEnergy / heatPerOp;

		int ops = Math.min(inputOps, Math.min(outputOps, heatOps));
		if(ops <= 0) return;

		tanks[2].setFill(tanks[2].getFill() - step.amountReq * ops);
		tanks[3].setFill(tanks[3].getFill() + step.amountProduced * ops);
		this.heatEnergy -= heatPerOp * ops;
		this.markChanged();
	}

	@Override
	public void readFromNBT(NBTTagCompound nbt) {
		super.readFromNBT(nbt);

		for(int i = 0; i < tanks.length; i++) this.tanks[i].readFromNBT(nbt, String.valueOf(i));
		this.heatEnergy = nbt.getInteger("heatEnergy");
		this.amountToCool = nbt.getInteger("toCool");
		this.tickDelay = nbt.getInteger("delay");
		if(this.amountToCool <= 0) this.amountToCool = 24_000;
		if(this.tickDelay <= 0) this.tickDelay = 1;
	}

	@Override
	public void writeToNBT(NBTTagCompound nbt) {
		super.writeToNBT(nbt);

		for(int i = 0; i < tanks.length; i++) this.tanks[i].writeToNBT(nbt, String.valueOf(i));
		nbt.setInteger("heatEnergy", heatEnergy);
		nbt.setInteger("toCool", amountToCool);
		nbt.setInteger("delay", tickDelay);
	}

	@Override
	public void serialize(ByteBuf buf) {
		super.serialize(buf);
		for(int i = 0; i < tanks.length; i++) this.tanks[i].serialize(buf);
		buf.writeInt(this.heatEnergy);
		buf.writeInt(this.amountToCool);
		buf.writeInt(this.tickDelay);
	}

	@Override
	public void deserialize(ByteBuf buf) {
		super.deserialize(buf);
		for(int i = 0; i < tanks.length; i++) this.tanks[i].deserialize(buf);
		this.heatEnergy = buf.readInt();
		this.amountToCool = buf.readInt();
		this.tickDelay = buf.readInt();
	}

	@Override
	public int getHeatStored() {
		return heatEnergy;
	}

	@Override
	public void useUpHeat(int heat) {
		this.heatEnergy = Math.max(0, this.heatEnergy - heat);
	}

	@Override
	public FluidTank[] getAllTanks() {
		return tanks;
	}

	@Override
	public FluidTank[] getSendingTanks() {
		return new FluidTank[] {tanks[1], tanks[3]};
	}

	@Override
	public FluidTank[] getReceivingTanks() {
		return new FluidTank[] {tanks[0], tanks[2]};
	}

	@Override
	public boolean canConnect(FluidType type, ForgeDirection dir) {
		ForgeDirection facing = ForgeDirection.getOrientation(this.getBlockMetadata() - BlockDummyable.offset);
		return dir == facing || dir == facing.getOpposite();
	}

	@Override
	public boolean hasPermission(EntityPlayer player) {
		return player.getDistance(xCoord + 0.5D, yCoord + 0.5D, zCoord + 0.5D) < 16.0D;
	}

	@Override
	public void receiveControl(EntityPlayer player, NBTTagCompound data) {
		if(data.hasKey("toCool")) this.amountToCool = MathHelper.clamp_int(data.getInteger("toCool"), 1, tanks[0].getMaxFill());
		if(data.hasKey("delay")) this.tickDelay = Math.max(data.getInteger("delay"), 1);

		this.markChanged();
	}

	AxisAlignedBB bb = null;

	@Override
	public AxisAlignedBB getRenderBoundingBox() {

		if(bb == null) {
			bb = AxisAlignedBB.getBoundingBox(
				xCoord - 3,
				yCoord,
				zCoord - 3,
				xCoord + 4,
				yCoord + 1,
				zCoord + 4
			);
		}

		return bb;
	}

	@Override
	public double getMaxRenderDistanceSquared() {
		return 65536.0D;
	}
}
