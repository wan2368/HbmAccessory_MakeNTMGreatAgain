package com.makentmgreatagain.tileentity;

import com.hbm.blocks.BlockDummyable;
import com.hbm.inventory.FluidStack;
import com.hbm.inventory.fluid.FluidType;
import com.hbm.inventory.fluid.Fluids;
import com.hbm.inventory.fluid.tank.FluidTank;
import com.hbm.inventory.recipes.ElectrolyserFluidRecipes;
import com.hbm.inventory.recipes.ElectrolyserFluidRecipes.ElectrolysisRecipe;
import com.hbm.tileentity.TileEntityMachineBase;
import com.hbm.tileentity.TileEntityProxyCombo;
import com.hbm.tileentity.TilePort;
import com.hbm.tileentity.TilePort.PortDef;
import com.hbm.util.InventoryUtil;

import api.hbm.energymk2.IEnergyReceiverMK2;
import api.hbm.fluidmk2.IFluidStandardTransceiverMK2;
import io.netty.buffer.ByteBuf;

import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.ISidedInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraftforge.common.util.ForgeDirection;

/**
 * 小型电解机核心。
 *
 * <p>2×2×3 多方块，四个底部插座全部接流体与电力：模型 +Z 面的两个是进料口（共用输入罐），
 * -Z 面的两个是出料口（两个输出罐各占一个，方便把氢、氧分开接走）。</p>
 *
 * <p>配方直接读 {@link ElectrolyserFluidRecipes#recipes}，每 tick 现查，
 * 所以 /ntmreload 之后原版与附属新增的电解配方立刻生效；只接受纯流体配方
 * （无物品副产、且两路输出都是流体），带物品或单流体输出的配方会被筛掉。
 * 识别码只改输入罐类型。</p>
 *
 * <p>速度写在 {@link #speedMultiplier} 里：1.0 就是配方写的 duration，
 * 2.0 就是耗时减半（每秒处理量翻倍）。耗电按同一倍率放大，单次电解的总能耗不变。</p>
 */
public class TileEntityElectrolyzerReborn extends TileEntityMachineBase implements IEnergyReceiverMK2, IFluidStandardTransceiverMK2 {

	/** 三个罐的容量，和提示里的 32,000 mB 对应 */
	public static final int TANK_CAPACITY = 32_000;
	/** 内部副产缓冲：配方最多也就三种副产，9 格足够周转 */
	public static final int BYPRODUCT_SLOTS = 9;
	/** 蓄电上限 */
	public static final long MAX_POWER = 2_000_000L;
	/** 基础耗电（HE/t，倍率 1.0 时） */
	public static final int POWER_PER_TICK = 5_000;

	/**
	 * 电解速度倍率，直接改这个字段即可调速。
	 * 1.0 = 配方 duration 原样执行；2.0 = 速度翻倍；0.5 = 速度减半。
	 */
	public static float speedMultiplier = 1.0F;

	/** 0 输入 / 1 输出一 / 2 输出二 */
	public FluidTank[] tanks = new FluidTank[3];
	public long power;
	public int progress;

	public TileEntityElectrolyzerReborn() {
		super(BYPRODUCT_SLOTS);
		this.tanks[0] = new FluidTank(Fluids.WATER, TANK_CAPACITY);
		this.tanks[1] = new FluidTank(Fluids.NONE, TANK_CAPACITY);
		this.tanks[2] = new FluidTank(Fluids.NONE, TANK_CAPACITY);
	}

	@Override
	public String getName() {
		return "container.electrolyzer_reborn";
	}

	/** 只有副产缓冲，不允许从任何面塞东西进来（要出料就给料斗抽） */
	@Override
	public boolean isItemValidForSlot(int slot, ItemStack stack) {
		return false;
	}

	@Override
	public boolean canExtractItem(int slot, ItemStack stack, int side) {
		return true;
	}

	@Override
	public int[] getAccessibleSlotsFromSide(int side) {
		int[] all = new int[this.slots.length];
		for(int i = 0; i < all.length; i++) all[i] = i;
		return all;
	}

	@Override
	public void updateEntity() {

		if(worldObj.isRemote) return;

		ElectrolysisRecipe recipe = this.getRecipe();
		this.syncOutputTanks(recipe);

		this.setupPorts();
		this.updatePortPIFIFO();

		if(recipe != null && this.canProcess(recipe)) {
			this.power -= this.getUsage();
			this.progress++;

			if(this.progress >= this.getDuration(recipe)) {
				this.process(recipe);
				this.progress = 0;
				this.markChanged();
			}
		}

		// 副产不用每 tick 都找一遍邻居
		if(worldObj.getTotalWorldTime() % 5 == 0) this.pushByproducts();

		this.networkPackNT(25);
	}

	/* ---- 端口 ---- */

	protected PortDef[] cachedPorts;

	public PortDef[] getPorts() {

		if(cachedPorts == null) {
			ForgeDirection dir = ForgeDirection.getOrientation(this.getBlockMetadata() - BlockDummyable.offset);
			ForgeDirection rot = dir.getRotation(ForgeDirection.UP);

			// 模型 +X = -dir、+Z = -rot：
			// 输出口（模型 -Z）在 +rot 侧，侧排两格
			PortDef out1 = PortDef.make(xCoord + rot.offsetX, yCoord, zCoord + rot.offsetZ, rot);
			PortDef out2 = PortDef.make(xCoord - dir.offsetX + rot.offsetX, yCoord, zCoord - dir.offsetZ + rot.offsetZ, rot);
			// 输入口（模型 +Z）在 -rot 侧，核心格 + 沿 dir 后一格
			PortDef in1 = PortDef.make(xCoord, yCoord, zCoord, rot.getOpposite());
			PortDef in2 = PortDef.make(xCoord - dir.offsetX, yCoord, zCoord - dir.offsetZ, rot.getOpposite());

			cachedPorts = new PortDef[] {out1, out2, in1, in2};
		}

		return cachedPorts;
	}

	protected void setupPorts() {

		if(this.powerPorts != null) return;

		PortDef[] ports = getPorts();

		// 四个插座都能接电
		this.setupPowerPorts(ports);
		// 两个出料口各绑一个输出罐，管道才能把氢和氧分开
		this.fluidOutPorts = TilePort.manyToMany(this, ports[0], ports[1]);
		// 两个进料口共用输入罐
		this.fluidInPorts = TilePort.oneToMany(this, 1, PortDef.combine(ports[2], ports[3]));
	}

	/* ---- 配方 ---- */

	public ElectrolysisRecipe getRecipe() {

		ElectrolysisRecipe recipe = ElectrolyserFluidRecipes.getRecipe(this.tanks[0].getTankType());

		// 这台机器只走纯流体流程：无固体副产，且必须有两路流体输出
		return isFluidOnlyRecipe(recipe) ? recipe : null;
	}

	/** 筛掉带物品副产、以及只有一路流体输出的配方 */
	public static boolean isFluidOnlyRecipe(ElectrolysisRecipe recipe) {

		if(recipe == null) return false;
		if(recipe.byproduct != null && recipe.byproduct.length > 0) return false;
		if(recipe.output1 == null || recipe.output1.type == Fluids.NONE || recipe.output1.fill <= 0) return false;
		if(recipe.output2 == null || recipe.output2.type == Fluids.NONE || recipe.output2.fill <= 0) return false;

		return true;
	}

	/** 换配方：只动输入罐，旧产物留在输出罐里等管道抽走 */
	public boolean setInputType(FluidType type) {

		if(!isFluidOnlyRecipe(type == null ? null : ElectrolyserFluidRecipes.getRecipe(type))) return false;

		if(this.tanks[0].getTankType() != type) {
			this.tanks[0].setTankType(type);
			this.progress = 0;
			this.markChanged();
		}

		return true;
	}

	/**
	 * 输出罐空着才改类型，避免切配方时把已经析出的氢、氧直接抹掉。
	 * 旧液体没排空之前 canProcess 会一直返回 false，机器只是暂停，不会吐掉产物。
	 */
	protected void syncOutputTanks(ElectrolysisRecipe recipe) {

		if(recipe == null) return;

		setTypeWhenEmpty(this.tanks[1], recipe.output1.type);
		setTypeWhenEmpty(this.tanks[2], recipe.output2.type);
	}

	private static void setTypeWhenEmpty(FluidTank tank, FluidType type) {
		if(tank.getFill() <= 0 && tank.getTankType() != type) tank.setTankType(type);
	}

	/* ---- 运行 ---- */

	public long getUsage() {
		return (long) Math.ceil(POWER_PER_TICK * effectiveSpeed());
	}

	public int getDuration(ElectrolysisRecipe recipe) {
		return Math.max(1, (int) Math.ceil(recipe.duration / effectiveSpeed()));
	}

	private static float effectiveSpeed() {
		return speedMultiplier < 0.01F ? 0.01F : speedMultiplier;
	}

	public boolean canProcess(ElectrolysisRecipe recipe) {

		if(this.power < this.getUsage()) return false;
		if(this.tanks[0].getFill() < recipe.amount) return false;
		if(!canStore(this.tanks[1], recipe.output1)) return false;
		if(!canStore(this.tanks[2], recipe.output2)) return false;

		return canStoreByproducts(recipe.byproduct);
	}

	private static boolean canStore(FluidTank tank, FluidStack out) {

		if(out == null || out.type == Fluids.NONE || out.fill <= 0) return true;
		if(tank.getTankType() != out.type) return false;

		return tank.getFill() + out.fill <= tank.getMaxFill();
	}

	protected void process(ElectrolysisRecipe recipe) {

		this.tanks[0].setFill(this.tanks[0].getFill() - recipe.amount);

		if(recipe.output1 != null && recipe.output1.type != Fluids.NONE && recipe.output1.fill > 0) {
			this.tanks[1].setTankType(recipe.output1.type);
			this.tanks[1].setFill(this.tanks[1].getFill() + recipe.output1.fill);
		}

		if(recipe.output2 != null && recipe.output2.type != Fluids.NONE && recipe.output2.fill > 0) {
			this.tanks[2].setTankType(recipe.output2.type);
			this.tanks[2].setFill(this.tanks[2].getFill() + recipe.output2.fill);
		}

		if(recipe.byproduct != null) {
			for(ItemStack byproduct : recipe.byproduct) {
				if(byproduct == null) continue;
				InventoryUtil.tryAddItemToInventory(this.slots, byproduct.copy());
			}
		}
	}

	/* ---- 副产 ---- */

	protected boolean canStoreByproducts(ItemStack[] byproducts) {

		if(byproducts == null || byproducts.length == 0) return true;

		ItemStack[] sim = new ItemStack[this.slots.length];
		for(int i = 0; i < sim.length; i++) {
			sim[i] = this.slots[i] == null ? null : this.slots[i].copy();
		}

		for(ItemStack byproduct : byproducts) {
			if(byproduct == null || byproduct.stackSize <= 0) continue;
			if(InventoryUtil.tryAddItemToInventory(sim, byproduct.copy()) != null) return false;
		}

		return true;
	}

	protected void pushByproducts() {

		for(int i = 0; i < this.slots.length; i++) {
			ItemStack stack = this.slots[i];
			if(stack == null) continue;

			for(ForgeDirection side : ForgeDirection.VALID_DIRECTIONS) {
				TileEntity te = worldObj.getTileEntity(
					xCoord + side.offsetX,
					yCoord + side.offsetY,
					zCoord + side.offsetZ);

				if(te == null || te == this || te instanceof TileEntityProxyCombo) continue;
				if(!(te instanceof IInventory)) continue;

				// ISidedInventory 的 side 参数约定是「目标容器的哪个面进料」：
				// 机器在我们 side 方向的邻格，对我们来说的 side 对它来说就是反面
				ItemStack remaining = insertInto((IInventory) te, side.getOpposite(), stack);
				this.slots[i] = remaining;
				this.markChanged();

				if(remaining == null) break;
				stack = remaining;
			}
		}
	}

	/** 把 stack 塞进目标容器，返回塞不进的部分 */
	private static ItemStack insertInto(IInventory inv, ForgeDirection side, ItemStack stack) {

		int[] accessible;

		if(inv instanceof ISidedInventory) {
			accessible = ((ISidedInventory) inv).getAccessibleSlotsFromSide(side.ordinal());
		} else {
			accessible = new int[inv.getSizeInventory()];
			for(int i = 0; i < accessible.length; i++) accessible[i] = i;
		}

		// 先叠到同类堆上
		for(int slot : accessible) {
			if(!canInsert(inv, slot, side, stack)) continue;

			ItemStack in = inv.getStackInSlot(slot);
			if(in == null || !in.isItemEqual(stack) || !ItemStack.areItemStackTagsEqual(in, stack)) continue;

			int limit = Math.min(inv.getInventoryStackLimit(), Math.min(in.getMaxStackSize(), stack.getMaxStackSize()));
			int move = Math.min(limit - in.stackSize, stack.stackSize);
			if(move <= 0) continue;

			in.stackSize += move;
			stack.stackSize -= move;
			inv.markDirty();

			if(stack.stackSize <= 0) return null;
		}

		// 再找空槽
		for(int slot : accessible) {
			if(!canInsert(inv, slot, side, stack)) continue;
			if(inv.getStackInSlot(slot) != null) continue;

			int limit = Math.min(inv.getInventoryStackLimit(), stack.getMaxStackSize());
			int move = Math.min(limit, stack.stackSize);

			ItemStack copy = stack.copy();
			copy.stackSize = move;
			inv.setInventorySlotContents(slot, copy);
			stack.stackSize -= move;
			inv.markDirty();

			if(stack.stackSize <= 0) return null;
		}

		return stack.stackSize > 0 ? stack : null;
	}

	private static boolean canInsert(IInventory inv, int slot, ForgeDirection side, ItemStack stack) {
		if(inv instanceof ISidedInventory) {
			return ((ISidedInventory) inv).canInsertItem(slot, stack, side.ordinal());
		}
		return inv.isItemValidForSlot(slot, stack);
	}

	/* ---- 电力与流体接口 ---- */

	@Override
	public long getPower() {
		return this.power;
	}

	@Override
	public void setPower(long power) {
		this.power = power;
	}

	@Override
	public long getMaxPower() {
		return MAX_POWER;
	}

	@Override
	public FluidTank[] getAllTanks() {
		return this.tanks;
	}

	@Override
	public FluidTank[] getSendingTanks() {
		return new FluidTank[] {this.tanks[1], this.tanks[2]};
	}

	@Override
	public FluidTank[] getReceivingTanks() {
		return new FluidTank[] {this.tanks[0]};
	}

	/** 只有 ±rot 两个侧面是真正的流体与电力接口 */
	@Override
	public boolean canConnect(FluidType type, ForgeDirection side) {
		ForgeDirection dir = ForgeDirection.getOrientation(this.getBlockMetadata() - BlockDummyable.offset);
		ForgeDirection rot = dir.getRotation(ForgeDirection.UP);
		return side == rot || side == rot.getOpposite();
	}

	/* ---- 存档与同步 ---- */

	@Override
	public void readFromNBT(NBTTagCompound nbt) {
		super.readFromNBT(nbt);

		for(int i = 0; i < this.tanks.length; i++) this.tanks[i].readFromNBT(nbt, String.valueOf(i));

		this.power = Math.max(0L, Math.min(MAX_POWER, nbt.getLong("power")));
		this.progress = Math.max(0, nbt.getInteger("progress"));
	}

	@Override
	public void writeToNBT(NBTTagCompound nbt) {
		super.writeToNBT(nbt);

		for(int i = 0; i < this.tanks.length; i++) this.tanks[i].writeToNBT(nbt, String.valueOf(i));

		nbt.setLong("power", this.power);
		nbt.setInteger("progress", this.progress);
	}

	@Override
	public void serialize(ByteBuf buf) {
		super.serialize(buf);

		for(FluidTank tank : this.tanks) tank.serialize(buf);
		buf.writeLong(this.power);
		buf.writeInt(this.progress);

		// 副产缓冲没有 GUI，但准星提示要能看到，所以随包带一份
		int count = 0;
		for(ItemStack stack : this.slots) if(stack != null) count++;
		buf.writeByte(count);

		for(ItemStack stack : this.slots) {
			if(stack == null) continue;
			buf.writeInt(net.minecraft.item.Item.getIdFromItem(stack.getItem()));
			buf.writeInt(stack.getItemDamage());
			buf.writeInt(stack.stackSize);
		}
	}

	@Override
	public void deserialize(ByteBuf buf) {
		super.deserialize(buf);

		for(FluidTank tank : this.tanks) tank.deserialize(buf);
		this.power = buf.readLong();
		this.progress = buf.readInt();

		java.util.Arrays.fill(this.slots, null);
		int count = buf.readByte();

		for(int i = 0; i < count; i++) {
			net.minecraft.item.Item item = net.minecraft.item.Item.getItemById(buf.readInt());
			int damage = buf.readInt();
			int size = buf.readInt();
			if(i < this.slots.length) this.slots[i] = new ItemStack(item, size, damage);
		}
	}

	AxisAlignedBB bb = null;

	@Override
	public AxisAlignedBB getRenderBoundingBox() {

		if(bb == null) {
			bb = AxisAlignedBB.getBoundingBox(
				xCoord - 1,
				yCoord - 1,
				zCoord - 1,
				xCoord + 2,
				yCoord + 4,
				zCoord + 2);
		}

		return bb;
	}

	@Override
	public double getMaxRenderDistanceSquared() {
		return 65536.0D;
	}
}
