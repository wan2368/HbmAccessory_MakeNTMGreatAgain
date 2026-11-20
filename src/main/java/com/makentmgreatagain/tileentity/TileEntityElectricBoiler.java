package com.makentmgreatagain.tileentity;

import com.hbm.inventory.fluid.Fluids;
import com.hbm.inventory.fluid.tank.FluidTank;
import com.hbm.lib.Library;
import com.hbm.tileentity.TileEntityMachineBase;
import com.hbm.tileentity.TilePort.PortDef;
import com.makentmgreatagain.blocks.BlockElectricBoiler;
import com.makentmgreatagain.recipes.ElectricBoilerRecipes;

import api.hbm.energymk2.IBatteryItem;
import api.hbm.energymk2.IEnergyReceiverMK2;
import api.hbm.fluidmk2.IFluidStandardTransceiverMK2;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.ForgeDirection;

/**
 * 电锅炉（单方块，完整移植自 HBM 1.0.27 X4775 的 TileEntityMachineBoilerElectric）。
 *
 * <p>行为与原版逐条一致：电力缓存 10k HE、热量 2000~80000 TU；
 * 每 tick 耗电 150 HE，按「剩余电力/10000×300，封顶 150」产热；
 * 断电 -100 TU/t，自然散热 -30 TU/t；
 * 沸腾配方 OIL→HOTOIL、CRACKOIL→HOTCRACKOIL，每次 5mB→5mB，沸点热值 35000；
 * 每 tick 迭代 heat/35000 次，首次耗热 35、后续每次 50。</p>
 *
 * <p>槽位：0/1 输入罐装卸、2/3 输入罐装罐、4 电池、5/6 输出罐卸罐。
 * 双罐各 16000mB。原版 NBT 键 water/steam 保留（燃煤版遗留命名）。</p>
 *
 * <p>网络：原版旧电力/流体网络在当前本体已移除，改用 mk2 端口
 * （六面各一个「电力+流体入+流体出」通用口），行为等价。</p>
 */
public class TileEntityElectricBoiler extends TileEntityMachineBase implements IEnergyReceiverMK2, IFluidStandardTransceiverMK2 {

	public static final long MAX_POWER = 10_000L;
	public static final int MAX_HEAT = 80_000;
	public static final int MIN_HEAT = 2_000;
	public static final int TANK_CAPACITY = 16_000;

	public static final int SLOT_BATTERY = 4;

	public long power;
	public int heat = MIN_HEAT;

	public final FluidTank[] tanks = new FluidTank[2];

	public TileEntityElectricBoiler() {
		super(7);
		this.tanks[0] = new FluidTank(Fluids.OIL, TANK_CAPACITY);
		this.tanks[1] = new FluidTank(Fluids.HOTOIL, TANK_CAPACITY);
	}

	@Override
	public String getName() {
		return "container.machineElectricBoiler";
	}

	/** 原版 isUseableByPlayer 的 8 格范围（基类默认 12，这里覆写回原版） */
	@Override
	public double getUseRange() {
		return 8D;
	}

	/* ================= 每 tick（原版 updateEntity 逐条移植） ================= */

	@Override
	public void updateEntity() {

		if(worldObj.isRemote) return;

		this.setupAllPorts(this.getPorts());
		this.updateAllPorts();
		this.receivePower();
		this.receiveFluid(new FluidTank[] { this.tanks[0] });
		this.provideFluid(new FluidTank[] { this.tanks[1] });

		this.power = Library.chargeTEFromItems(this.slots, SLOT_BATTERY, this.power, MAX_POWER);

		this.tanks[0].setType(0, 1, this.slots);
		this.tanks[0].loadTank(2, 3, this.slots);

		ElectricBoilerRecipes.Entry recipe = ElectricBoilerRecipes.get(this.tanks[0].getTankType());
		if(recipe == null) this.tanks[1].setTankType(Fluids.NONE);
		else this.tanks[1].setTankType(recipe.output);

		this.tanks[1].unloadTank(5, 6, this.slots);

		if(this.heat > MIN_HEAT) this.heat -= 30;

		if(this.power >= 150L) {
			this.power -= 150L;
			this.heat = (int) (this.heat + Math.min(this.power / 10000.0D * 300.0D, 150.0D));
		} else {
			this.heat -= 100;
		}

		boolean mark = false;
		if(this.power <= 0L && worldObj.getBlock(xCoord, yCoord, zCoord) == com.makentmgreatagain.init.ModBlocks.machine_electric_boiler_on) {
			this.power = 0L;
			BlockElectricBoiler.updateBlockState(false, worldObj, xCoord, yCoord, zCoord);
			mark = true;
		}

		if(this.heat > MAX_HEAT) this.heat = MAX_HEAT;

		if(this.power > 0L && worldObj.getBlock(xCoord, yCoord, zCoord) == com.makentmgreatagain.init.ModBlocks.machine_electric_boiler_off) {
			BlockElectricBoiler.updateBlockState(true, worldObj, xCoord, yCoord, zCoord);
			mark = true;
		}

		if(recipe != null) {
			for(int i = 0; i < this.heat / recipe.boilingHeat; i++) {
				if(this.tanks[0].getFill() >= recipe.amountReq
						&& this.tanks[1].getFill() + recipe.amountProduced <= this.tanks[1].getMaxFill()) {
					this.tanks[0].setFill(this.tanks[0].getFill() - recipe.amountReq);
					this.tanks[1].setFill(this.tanks[1].getFill() + recipe.amountProduced);
					if(i == 0) this.heat -= 35; else this.heat -= 50;
				}
			}
		}

		if(this.heat < MIN_HEAT) this.heat = MIN_HEAT;

		this.networkPackNT(50);
		if(mark) this.markDirty();
	}

	/* ================= 端口（单方块六面通用口） ================= */

	private PortDef[] getPorts() {
		PortDef[] ports = new PortDef[ForgeDirection.VALID_DIRECTIONS.length];
		for(int i = 0; i < ports.length; i++) {
			ForgeDirection dir = ForgeDirection.VALID_DIRECTIONS[i];
			ports[i] = PortDef.make(xCoord, yCoord, zCoord, dir);
		}
		return ports;
	}

	/* ================= 槽位准入 =================
	 * 原版只拦电池槽，0/2/5 流体槽是裸 Slot 谁都能塞。这里补齐：
	 * 0 识别码/装罐入口（setType+loadTank 用），2 只收装着输入罐流体的满容器，
	 * 5 只收能被输出罐灌满的空容器；1/3/6 只出不进。容器 shift 校验与本判定同源。 */

	@Override
	public boolean isItemValidForSlot(int i, ItemStack stack) {
		if(stack == null) return false;
		switch(i) {
		case SLOT_BATTERY: return stack.getItem() instanceof IBatteryItem;
		case 1: case 3: case 6: return false;
		case 0:
			return stack.getItem() instanceof com.hbm.items.machine.IItemFluidIdentifier
				|| stack.getItem() instanceof api.hbm.fluidmk2.IFillableItem
				|| stack.getItem() == net.minecraft.init.Items.bucket
				|| com.hbm.inventory.FluidContainerRegistry.getFluidContent(stack, this.tanks[0].getTankType()) > 0;
		case 2:
			return stack.getItem() instanceof api.hbm.fluidmk2.IFillableItem
				|| com.hbm.inventory.FluidContainerRegistry.getFluidContent(stack, this.tanks[0].getTankType()) > 0;
		case 5:
			return stack.getItem() instanceof api.hbm.fluidmk2.IFillableItem
				|| stack.getItem() == net.minecraft.init.Items.bucket
				|| com.hbm.inventory.FluidContainerRegistry.getFullContainer(stack, this.tanks[1].getTankType()) != null;
		default: return false;
		}
	}

	/* ================= 能量接口 ================= */

	@Override public void setPower(long i) { this.power = i; }
	@Override public long getPower() { return this.power; }
	@Override public long getMaxPower() { return MAX_POWER; }

	public int getHeatScaled(int i) { return this.heat * i / MAX_HEAT; }
	public long getPowerScaled(long i) { return this.power * i / MAX_POWER; }

	/* ================= 流体接口 ================= */

	@Override public FluidTank[] getSendingTanks() { return new FluidTank[] { this.tanks[1] }; }
	@Override public FluidTank[] getReceivingTanks() { return new FluidTank[] { this.tanks[0] }; }
	@Override public FluidTank[] getAllTanks() { return this.tanks; }

	/* ================= NBT（原版键名保留） ================= */

	@Override
	public void readFromNBT(NBTTagCompound nbt) {
		super.readFromNBT(nbt);
		this.heat = nbt.getInteger("heat");
		this.power = nbt.getLong("power");
		this.tanks[0].readFromNBT(nbt, "water");
		this.tanks[1].readFromNBT(nbt, "steam");
	}

	@Override
	public void writeToNBT(NBTTagCompound nbt) {
		super.writeToNBT(nbt);
		nbt.setInteger("heat", this.heat);
		nbt.setLong("power", this.power);
		this.tanks[0].writeToNBT(nbt, "water");
		this.tanks[1].writeToNBT(nbt, "steam");
	}

	/* ================= 客户端同步 ================= */

	@Override
	public void serialize(io.netty.buffer.ByteBuf buf) {
		super.serialize(buf);
		buf.writeLong(this.power);
		buf.writeInt(this.heat);
		this.tanks[0].serialize(buf);
		this.tanks[1].serialize(buf);
	}

	@Override
	public void deserialize(io.netty.buffer.ByteBuf buf) {
		super.deserialize(buf);
		this.power = buf.readLong();
		this.heat = buf.readInt();
		this.tanks[0].deserialize(buf);
		this.tanks[1].deserialize(buf);
	}
}