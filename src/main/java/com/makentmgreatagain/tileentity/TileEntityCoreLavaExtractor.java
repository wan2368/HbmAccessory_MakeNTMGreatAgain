package com.makentmgreatagain.tileentity;

import com.hbm.inventory.fluid.FluidType;
import com.hbm.inventory.fluid.Fluids;
import com.hbm.inventory.fluid.tank.FluidTank;
import com.hbm.lib.Library;
import com.hbm.tileentity.TileEntityMachineBase;
import com.hbm.tileentity.TilePort.PortDef;

import api.hbm.energymk2.IBatteryItem;
import api.hbm.energymk2.IEnergyReceiverMK2;
import api.hbm.fluidmk2.IFluidStandardTransceiverMK2;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraftforge.common.util.ForgeDirection;

/**
 * 地核熔岩开采机（5×5×6 多方块核心，界面对应 {@code gui/core_lava_extractor.png}）。
 *
 * <h3>罐与槽</h3>
 * <ul>
 *   <li>输入罐 32000 mB：罐压固定 5 PU，按本体默认逻辑只收 5 PU 的高温液态铅，
 *       其它压力/其它流体一律拒收；只能管道进，没有桶槽；</li>
 *   <li>输出罐 ×2 各 32000 mB：1 = 高密度地核原浆（10 mB/t），2 = 熔融地核混合物（5 mB/t）；
 *       任一输出满自动停机；桶槽可用；</li>
 *   <li>槽 0 人造X晶体（不消耗，不在位无法开机）；槽 1 电池；
 *       槽 2/3 输出罐 1/2 的空容器；槽 4/5 接好的满容器。</li>
 * </ul>
 *
 * <h3>三灯</h3>
 * <p>电力（power &gt; 0）/ 输入流体（输入罐有料）/ X晶体（槽 0 是 crystal_xen）。
 * 灯的黄闪绿亮由 GUI 画，这里只给条件。</p>
 */
public class TileEntityCoreLavaExtractor extends TileEntityMachineBase
		implements IEnergyReceiverMK2, IFluidStandardTransceiverMK2 {

	/** 内部最大储电量：500M HE */
	public static final long MAX_POWER = 500_000_000L;
	/** 运行耗电：15M HE/t */
	public static final long CONSUMPTION = 15_000_000L;
	/** 运行吃铅：20 mB/t */
	public static final int INPUT_PER_TICK = 20;
	/** 产出：高密度地核原浆 10 mB/t */
	public static final int PRODUCE_SLURRY = 10;
	/** 产出：熔融地核混合物 5 mB/t */
	public static final int PRODUCE_MELT = 5;
	/** 输入铅的最低供给压力（PU） */
	public static final int INPUT_PRESSURE = 5;
	public static final int CAPACITY = 32_000;

	public static final int SLOT_CRYSTAL = 0;
	public static final int SLOT_BATTERY = 1;
	public static final int SLOT_EMPTY_1 = 2;
	public static final int SLOT_EMPTY_2 = 3;
	public static final int SLOT_FULL_1 = 4;
	public static final int SLOT_FULL_2 = 5;

	public long power;
	public boolean running;

	/** 0 输入（高温铅，罐压固定 5 PU：本体默认逻辑只收与罐压完全同压的流体），
	 *  1 高密度地核原浆，2 熔融地核混合物 */
	public FluidTank[] tanks = new FluidTank[] {
		new FluidTank(Fluids.LEAD_HOT, CAPACITY).withPressure(INPUT_PRESSURE),
		new FluidTank(com.makentmgreatagain.init.ModFluids.CORE_SLURRY.type, CAPACITY),
		new FluidTank(com.makentmgreatagain.init.ModFluids.CORE_MELT.type, CAPACITY)
	};

	private static final int[] SLOTS_AUTOMATION = new int[] {SLOT_BATTERY, SLOT_EMPTY_1, SLOT_EMPTY_2, SLOT_FULL_1, SLOT_FULL_2};

	/**
	 * 顶层 5 个接口格的虚拟端口（电力 + 流体入/出）。
	 *
	 * <p>本体的电力/流体网络靠核心 TE 用 PortDef 订阅（TilePort.checkSubscribe），
	 * 不订阅就永远拒收——代理方块只转发物品，不管电网液网。</p>
	 */
	protected PortDef[] cachedPorts;

	public PortDef[] getPorts() {
		if(cachedPorts == null) {
			int top = yCoord + com.makentmgreatagain.blocks.BlockCoreLavaExtractor.HEIGHT - 1;
			cachedPorts = new PortDef[] {
				PortDef.make(xCoord, top, zCoord, ForgeDirection.UP),
				PortDef.make(xCoord + 1, top, zCoord, ForgeDirection.UP),
				PortDef.make(xCoord - 1, top, zCoord, ForgeDirection.UP),
				PortDef.make(xCoord, top, zCoord + 1, ForgeDirection.UP),
				PortDef.make(xCoord, top, zCoord - 1, ForgeDirection.UP)
			};
		}
		return cachedPorts;
	}

	public TileEntityCoreLavaExtractor() {
		super(6);
	}

	@Override
	public String getName() {
		return "container.core_lava_extractor";
	}

	/* ========================== 运行条件 ========================== */

	public boolean hasPower() {
		return power > 0;
	}

	public boolean hasInput() {
		return tanks[0].getFill() > 0;
	}

	public boolean hasCrystal() {
		return slots[SLOT_CRYSTAL] != null && slots[SLOT_CRYSTAL].getItem() == com.hbm.items.ModItems.crystal_xen;
	}

	/** 三灯全绿 */
	public boolean allGreen() {
		return hasPower() && hasInput() && hasCrystal();
	}

	// 裂隙不限定在世界生成的 y=0 层，手动放置在其他高度也有效。
	public boolean overFissure() {
		for(int y = yCoord - 1; y >= 0; y--) {
			if(worldObj.getBlock(xCoord, y, zCoord) == com.makentmgreatagain.init.ModBlocks.core_fissure)
				return true;
		}
		return false;
	}

	/** 开关请求：开必须三灯全绿，关随时允许；返回是否生效 */
	public boolean trySetRunning(boolean on) {
		if(on && !allGreen()) return false;
		if(running == on) return true;
		running = on;
		markDirty();
		worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
		return true;
	}

	/* ========================== tick ========================== */

	@Override
	public void updateEntity() {

		if(worldObj.isRemote) return;

		// 订阅电力/流体网络：setup 建端口，updatePortPIFIFO 每 tick 驱动节点订阅（缺它就不入网）
		this.setupAllPorts(getPorts());
		this.updatePortPIFIFO();

		power = Library.chargeTEFromItems(slots, SLOT_BATTERY, power, MAX_POWER);

		// 输出罐 → 容器：空桶进 2/3，满桶出 4/5
		for(int i = 0; i < 2; i++)
			tanks[1 + i].unloadTank(SLOT_EMPTY_1 + i, SLOT_FULL_1 + i, slots);

		if(running && allGreen() && overFissure()
				&& power >= CONSUMPTION && tanks[0].getFill() >= INPUT_PER_TICK) {

			// 任一输出罐满 → 自动停机，本 tick 不再产出
			if(tanks[1].getFill() >= CAPACITY || tanks[2].getFill() >= CAPACITY) {
				running = false;
				worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
			} else {
				power -= CONSUMPTION;
				tanks[0].setFill(tanks[0].getFill() - INPUT_PER_TICK);
				tanks[1].setFill(tanks[1].getFill() + Math.min(PRODUCE_SLURRY, CAPACITY - tanks[1].getFill()));
				tanks[2].setFill(tanks[2].getFill() + Math.min(PRODUCE_MELT, CAPACITY - tanks[2].getFill()));
			}
		}

		// 罐量/电力/开关每秒同步一次给客户端界面
		if(worldObj.getTotalWorldTime() % 20 == 0)
			worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);

	}

	/* ========================== 电力 ========================== */

	@Override
	public long getPower() {
		return power;
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
	public boolean canConnect(ForgeDirection dir) {
		return true;
	}

	/* ========================== 流体 ========================== */

	@Override
	public FluidTank[] getAllTanks() {
		return tanks;
	}

	@Override
	public FluidTank[] getSendingTanks() {
		return new FluidTank[] {tanks[1], tanks[2]};
	}

	@Override
	public FluidTank[] getReceivingTanks() {
		return new FluidTank[] {tanks[0]};
	}

	/* ========================== 流体 ==========================
	 * 收液不走自定义重写：本体 IFluidStandardReceiverMK2 默认实现按
	 * 「罐压 == 供给压」精确匹配投递，输入罐构造时已固定 5 PU，
	 * 因此只收 5 PU 的高温液态铅，tooltip 也照本体逻辑显示 Pressure: 5 PU。 */

	/* ========================== 物品 ========================== */

	@Override
	public int[] getAccessibleSlotsFromSide(int side) {
		return SLOTS_AUTOMATION;
	}

	@Override
	public boolean isItemValidForSlot(int slot, ItemStack stack) {
		if(stack == null) return false;
		switch(slot) {
		case SLOT_CRYSTAL: return stack.getItem() == com.hbm.items.ModItems.crystal_xen;
		case SLOT_BATTERY: return stack.getItem() instanceof IBatteryItem || stack.getItem() == com.hbm.items.ModItems.battery_creative;
		case SLOT_FULL_1:
		case SLOT_FULL_2: return false;
		default: return acceptsContainer(stack);
		}
	}

	/** 空容器槽准入：与容器 SlotContainerIn 同源——识别码 / 可灌装接口 / 原版桶 /
	 *  注册表里能被本机任一罐灌满的容器（HBM 流体单元等普通 Item 走这条），物流补罐不再被挡。 */
	private boolean acceptsContainer(ItemStack stack) {
		if(stack.getItem() instanceof com.hbm.items.machine.IItemFluidIdentifier) return true;
		if(stack.getItem() instanceof api.hbm.fluidmk2.IFillableItem) return true;
		if(stack.getItem() == net.minecraft.init.Items.bucket) return true;
		for(com.hbm.inventory.fluid.tank.FluidTank tank : tanks) {
			if(tank == null || tank.getTankType() == null) continue;
			if(com.hbm.inventory.FluidContainerRegistry.getFullContainer(stack, tank.getTankType()) != null) return true;
		}
		return false;
	}

	@Override
	public boolean canExtractItem(int slot, ItemStack stack, int side) {
		return true;
	}

	/* ========================== 存档 / 同步 ========================== */

	@Override
	public void writeToNBT(NBTTagCompound nbt) {
		super.writeToNBT(nbt);
		nbt.setLong("power", power);
		nbt.setBoolean("running", running);
		for(int i = 0; i < tanks.length; i++)
			tanks[i].writeToNBT(nbt, "tank" + i);
	}

	@Override
	public void readFromNBT(NBTTagCompound nbt) {
		super.readFromNBT(nbt);
		power = nbt.getLong("power");
		running = nbt.getBoolean("running");
		for(int i = 0; i < tanks.length; i++)
			tanks[i].readFromNBT(nbt, "tank" + i);
	}

	@Override
	public Packet getDescriptionPacket() {
		NBTTagCompound nbt = new NBTTagCompound();
		this.writeToNBT(nbt);
		return new S35PacketUpdateTileEntity(this.xCoord, this.yCoord, this.zCoord, 0, nbt);
	}

	@Override
	public void onDataPacket(NetworkManager net, S35PacketUpdateTileEntity packet) {
		this.readFromNBT(packet.func_148857_g());
	}

	/* ========================== 渲染范围 ========================== */

	@Override
	public AxisAlignedBB getRenderBoundingBox() {
		return INFINITE_EXTENT_AABB;
	}

	@Override
	public double getMaxRenderDistanceSquared() {
		return 65536.0D;
	}
}
