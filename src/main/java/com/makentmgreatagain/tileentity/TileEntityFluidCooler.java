package com.makentmgreatagain.tileentity;

import com.hbm.blocks.BlockDummyable;
import com.hbm.inventory.fluid.FluidType;
import com.hbm.inventory.fluid.Fluids;
import com.hbm.inventory.fluid.tank.FluidTank;
import com.hbm.inventory.fluid.trait.FT_Coolable;
import com.hbm.inventory.fluid.trait.FT_Heatable;
import com.hbm.items.machine.IItemFluidIdentifier;
import com.hbm.tileentity.TileEntityMachineBase;
import com.hbm.tileentity.TilePort.PortDef;
import com.makentmgreatagain.recipes.FluidCoolerRecipes;

import api.hbm.fluidmk2.IFluidStandardTransceiverMK2;
import io.netty.buffer.ByteBuf;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.AxisAlignedBB;
import net.minecraftforge.common.util.ForgeDirection;

/**
 * 流体冷却机核心。
 *
 * <h3>四个储罐</h3>
 * <pre>
 *   tanks[0] 冷却剂输入       识别码槽 0 设定，只收「可加热」流体（冷却液、液态钠、四氟甲烷…）
 *   tanks[1] 被冷却流体输入   识别码槽 1 设定，只收「可冷却」流体（热冷却液、热四氟甲烷、热钠…）
 *   tanks[2] 冷却后流体输出   = tanks[1] 的冷却产物（FT_Coolable#coolsTo）
 *   tanks[3] 加热后冷却剂输出 = tanks[0] 的加热产物（FT_Heatable 第一步的 typeProduced）
 * </pre>
 *
 * <p>两侧的配对<b>不允许直接读 trait</b>，统一走 {@link FluidCoolerRecipes}：
 * 它在 postInit 扫描一遍 {@link Fluids#metaOrder} 收录 HBM 全部配对，
 * 再补上 HBM 没登记的（四氟甲烷 → 冷四氟甲烷）。原因有两条：</p>
 * <ul>
 *   <li>HBM 的 {@link FT_Coolable} 里没有「四氟甲烷 → 冷四氟甲烷」，
 *       而冷四氟甲烷是 HBM 真实存在的深冷介质（-150°C），本机把它补成可冷却产物；</li>
 *   <li>液态钠、铅、重水、钍盐只登记了 PWR / ICF 效率，{@code HEATEXCHANGER} 是 0，
 *       按 trait 判定会被直接挡在门外，表里统一按 100% 兜底。</li>
 * </ul>
 *
 * <h3>换热计算</h3>
 * <p>两侧按「份」推进，每份的热量分别取 {@code heatEnergy × 效率}（放热）与
 * {@code heatReq ÷ 效率}（吸热），两者之比就是需要的冷却剂份数。任何一侧
 * 缺料或输出罐满了就自动降速，不会溢出、不会凭空空烧。</p>
 *
 * <h3>容量与速度</h3>
 * <p>四个罐各 32,000 mB；单 tick 最多处理 16,000 mB（按被冷却流体计）。</p>
 */
public class TileEntityFluidCooler extends TileEntityMachineBase implements IFluidStandardTransceiverMK2 {

	/** 四个储罐统一容量 */
	public static final int TANK_CAPACITY = 32_000;
	/** 单 tick 最大处理量（mB，按被冷却流体计） */
	public static final int MAX_RATE = 16_000;

	/** NBT 里额外存的罐型名前缀，绕开 FluidTank 自身的读写不一致，见 readFromNBT */
	private static final String TYPE_KEY = "fluidCoolerType";

	/** 0 冷却剂输入 / 1 被冷却流体输入 / 2 冷却后输出 / 3 加热后冷却剂输出 */
	public final FluidTank[] tanks = new FluidTank[4];

	/** 本 tick 是否完成了换热，仅用于界面状态与风扇转动 */
	public boolean isOn;
	/** 上一 tick 实际处理的被冷却流体量（mB），仅用于界面显示 */
	public int lastRate;

	/** 风扇当前角度；只在客户端推进，服务端恒为 0，渲染时插值用 prevFanSpin */
	public float fanSpin;
	public float prevFanSpin;

	public TileEntityFluidCooler() {
		super(2);

		// 默认给一组最常用的配对：冷却液 × 热四氟甲烷
		this.tanks[0] = new FluidTank(Fluids.COOLANT, TANK_CAPACITY);
		this.tanks[1] = new FluidTank(Fluids.PERFLUOROMETHYL_HOT, TANK_CAPACITY);
		this.tanks[2] = new FluidTank(Fluids.PERFLUOROMETHYL, TANK_CAPACITY);
		this.tanks[3] = new FluidTank(Fluids.COOLANT_HOT, TANK_CAPACITY);
	}

	@Override
	public String getName() {
		return "container.fluid_cooler";
	}

	/** 0 = 冷却剂识别码，1 = 被冷却流体识别码，两个槽都只收识别码 */
	@Override
	public boolean isItemValidForSlot(int slot, ItemStack stack) {
		return slot < 2 && stack != null && stack.getItem() instanceof IItemFluidIdentifier;
	}

	@Override
	public void updateEntity() {

		// 风扇只在客户端转：与本体 RenderCompressorCompact 一致，每 tick 45°，
		// 转到 360° 时把 prevFanSpin 一起减掉，避免插值瞬间倒转一圈
		this.prevFanSpin = this.fanSpin;

		if(worldObj.isRemote) {
			if(this.isOn) {
				this.fanSpin += 45F;
				if(this.fanSpin >= 360F) {
					this.prevFanSpin -= 360F;
					this.fanSpin -= 360F;
				}
			}
			return;
		}

		this.applySlotIdentifiers();
		this.setupTankTypes();

		this.setupAllPorts(getPorts());
		this.updatePortPIFIFO();

		this.exchange();

		// networkPackNT 的参数是同步半径（格）不是间隔：5 会让离核心稍远的玩家收不到包，
		// GUI 液面不刷新。按本模组多方块惯例给 64
		this.networkPackNT(64);
	}

	/* ---- 识别码 ---- */

	/**
	 * 两个槽各自绑定一个输入罐。类型没变就不动，
	 * 因为 {@code FluidTank#setTankType} 换型会清空存量，每 tick 重复设置会把罐里已有的液体清掉。
	 *
	 * <p>识别码指向的流体不在配对表里时直接忽略：罐型保持原样，也不弹提示。
	 * 玩家插错识别码只是「没反应」，不会把已经设好的罐子清空。</p>
	 *
	 * <p><b>两侧互斥</b>：设成与另一侧相同的流体时同样静默忽略。六个端口共用同一批坐标，
	 * 每个接收罐靠自己的类型挂到对应流体网络，两侧类型一旦相同，两个罐会并进同一张网，
	 * 进料无法区分该进哪个罐。所以这里不做任何提示，只是「改不动」。</p>
	 */
	protected void applySlotIdentifiers() {

		FluidType coolant = readIdentifier(slots[0]);
		if(coolant != null && isCoolant(coolant) && coolant != tanks[0].getTankType()) {
			if(canSetCoolant(coolant)) tanks[0].setTankType(coolant);
		}

		FluidType target = readIdentifier(slots[1]);
		if(target != null && isCoolable(target) && target != tanks[1].getTankType()) {
			if(canSetCoolable(target)) tanks[1].setTankType(target);
		}
	}

	/**
	 * 能否把 {@code type} 设为冷却剂：必须在配对表里，且不能与被冷却流体构成转换对。
	 *
	 * <p>不满足时调用方应静默忽略，不给任何提示。</p>
	 */
	public boolean canSetCoolant(FluidType type) {

		if(type == null || !isCoolant(type)) return false;

		return !sidesInverse(type, tanks[1].getTankType());
	}

	/** 能否把 {@code type} 设为被冷却流体：必须在配对表里，且不能与冷却剂构成转换对。 */
	public boolean canSetCoolable(FluidType type) {

		if(type == null || !isCoolable(type)) return false;

		return !sidesInverse(tanks[0].getTankType(), type);
	}

	/** 两侧是否构成自循环：为 true 时 {@link #exchange()} 直接返回。 */
	public boolean sidesConflict() {

		return sidesInverse(tanks[0].getTankType(), tanks[1].getTankType());
	}

	/**
	 * 两侧是否「互为转换」，这类组合必须禁止。
	 *
	 * <h3>为什么只拦同型不够</h3>
	 * <p>把两侧设成同一种流体只是最浅的一种撞车。真正会出问题、而且玩家很容易凑出来的是
	 * <b>一对互为产物的流体</b>：例如冷却剂吃「冷却液」，升温成「热冷却液」；
	 * 被冷却侧吃「热冷却液」，降温成「冷却液」。单独看每一步都是合法的，
	 * 但把两个产物分别接回对方的入口，就闭合成一个自循环——机器一边加热、一边冷却，
	 * 中间没有外部热源也没有实际产出，纯粹是自己和自己换热。</p>
	 *
	 * <p>判定覆盖三个方向：两侧同型、冷却剂的加热产物 == 被冷却侧输入、
	 * 被冷却侧的冷却产物 == 冷却剂输入。两条方向都查，是因为
	 * {@link FluidCoolerRecipes} 里有手工补的配对（四氟甲烷 → 冷四氟甲烷），
	 * 两侧的表并不严格互为镜像。</p>
	 */
	public static boolean sidesInverse(FluidType coolant, FluidType coolable) {

		if(coolant == null || coolable == null) return false;
		if(coolant == Fluids.NONE || coolable == Fluids.NONE) return false;
		if(coolant == coolable) return true;

		FluidCoolerRecipes.Heating heat = FluidCoolerRecipes.heating(coolant);
		if(heat != null && heat.output == coolable) return true;

		FluidCoolerRecipes.Cooling cool = FluidCoolerRecipes.cooling(coolable);
		return cool != null && cool.output == coolant;
	}

	protected FluidType readIdentifier(ItemStack stack) {

		if(stack == null || !(stack.getItem() instanceof IItemFluidIdentifier)) return null;

		return ((IItemFluidIdentifier) stack.getItem()).getType(worldObj, xCoord, yCoord, zCoord, stack);
	}

	/** 冷却剂：在 {@link FluidCoolerRecipes} 的加热表里查得到即可（液态钠/铅这类效率为 0 的也收） */
	public static boolean isCoolant(FluidType type) {
		return FluidCoolerRecipes.isCoolant(type);
	}

	/** 被冷却流体：在 {@link FluidCoolerRecipes} 的冷却表里查得到即可 */
	public static boolean isCoolable(FluidType type) {
		return FluidCoolerRecipes.isCoolable(type);
	}

	/** 按两侧输入罐的类型推导两个输出罐的类型，类型没变就不碰（避免清空存量） */
	protected void setupTankTypes() {

		FluidCoolerRecipes.Heating heat = FluidCoolerRecipes.heating(tanks[0].getTankType());
		FluidType coolantOut = heat != null ? heat.output : Fluids.NONE;
		if(tanks[3].getTankType() != coolantOut) tanks[3].setTankType(coolantOut);

		FluidCoolerRecipes.Cooling cool = FluidCoolerRecipes.cooling(tanks[1].getTankType());
		FluidType cooledOut = cool != null ? cool.output : Fluids.NONE;
		if(tanks[2].getTankType() != cooledOut) tanks[2].setTankType(cooledOut);
	}

	/* ---- 换热 ---- */

	/**
	 * 两侧按份配对推进：
	 * <pre>
	 *   被冷却侧每份放出  qOut = heatEnergy × 换热效率
	 *   冷却剂侧每份吸收  qIn  = heatReq ÷ 换热效率
	 *   需要冷却剂份数 = 被冷却份数 × qOut ÷ qIn
	 * </pre>
	 * 先按输入存量、输出空间、单 tick 上限算出两侧各自能跑多少份，再按热量配比取小值。
	 */
	protected void exchange() {

		this.isOn = false;
		this.lastRate = 0;

		// 两侧同型时两个接收罐并网、进料无法区分，换热也没有意义，直接不启动（无提示）
		if(this.sidesConflict()) return;

		FluidCoolerRecipes.Heating heat = FluidCoolerRecipes.heating(tanks[0].getTankType());
		FluidCoolerRecipes.Cooling cool = FluidCoolerRecipes.cooling(tanks[1].getTankType());

		// 两侧都必须有配对、有料，少于 1 mB 不启动
		if(heat == null || cool == null) return;
		if(tanks[0].getFill() < 1 || tanks[1].getFill() < 1) return;

		double qOut = cool.heatEnergy * cool.efficiency;   // 每份被冷却流体放出的 TU
		double qIn = heat.heatReq / heat.efficiency;       // 每份冷却剂吸收的 TU

		if(qOut <= 0D || qIn <= 0D) return;

		double ratio = qOut / qIn;                          // 每处理 1 份被冷却流体需要的冷却剂份数

		// 被冷却侧能处理的份数：存量 + 输出空间 + 单 tick 上限
		int coolOps = Math.min(
			tanks[1].getFill() / cool.amountReq,
			(tanks[2].getMaxFill() - tanks[2].getFill()) / cool.amountProduced);
		coolOps = Math.min(coolOps, MAX_RATE / cool.amountReq);

		// 冷却剂侧能提供的份数
		int heatOps = Math.min(
			tanks[0].getFill() / heat.amountReq,
			(tanks[3].getMaxFill() - tanks[3].getFill()) / heat.amountProduced);
		heatOps = Math.min(heatOps, MAX_RATE / heat.amountReq);

		if(coolOps <= 0 || heatOps <= 0) return;

		// 热量配比：冷却剂不够就把被冷却侧一起降下来
		double maxCoolByHeat = heatOps / ratio;
		if(maxCoolByHeat < coolOps) coolOps = (int) Math.floor(maxCoolByHeat);
		if(coolOps <= 0) return;

		int heatOpsUsed = (int) Math.min(heatOps, Math.ceil(coolOps * ratio));
		if(heatOpsUsed <= 0) return;

		int consumed = cool.amountReq * coolOps;
		tanks[1].setFill(tanks[1].getFill() - consumed);
		tanks[2].setFill(tanks[2].getFill() + cool.amountProduced * coolOps);

		tanks[0].setFill(tanks[0].getFill() - heat.amountReq * heatOpsUsed);
		tanks[3].setFill(tanks[3].getFill() + heat.amountProduced * heatOpsUsed);

		this.isOn = true;
		this.lastRate = consumed;
		this.markChanged();
	}

	/* ---- 端口 ---- */

	protected PortDef[] cachedPorts;

	/**
	 * 六个接口，坐标与 {@code BlockFluidCooler#getAllPorts} 一一对应：
	 * 侧向 ±3 各一个（朝外），沿朝向 ±1 与侧向 ±1 组合出前后各两个（朝外）。
	 * 全部在核心上方一格（y + 1）。
	 */
	public PortDef[] getPorts() {

		if(cachedPorts == null) {
			ForgeDirection dir = ForgeDirection.getOrientation(this.getBlockMetadata() - BlockDummyable.offset);
			ForgeDirection rot = dir.getRotation(ForgeDirection.UP);
			int y = yCoord + 1;

			cachedPorts = new PortDef[] {
				PortDef.make(xCoord + rot.offsetX * 3, y, zCoord + rot.offsetZ * 3, rot),
				PortDef.make(xCoord - rot.offsetX * 3, y, zCoord - rot.offsetZ * 3, rot.getOpposite()),
				PortDef.make(xCoord + dir.offsetX - rot.offsetX, y, zCoord + dir.offsetZ - rot.offsetZ, dir),
				PortDef.make(xCoord + dir.offsetX + rot.offsetX, y, zCoord + dir.offsetZ + rot.offsetZ, dir),
				PortDef.make(xCoord - dir.offsetX - rot.offsetX, y, zCoord - dir.offsetZ - rot.offsetZ, dir.getOpposite()),
				PortDef.make(xCoord - dir.offsetX + rot.offsetX, y, zCoord - dir.offsetZ + rot.offsetZ, dir.getOpposite()),
			};
		}

		return cachedPorts;
	}

	@Override public FluidTank[] getAllTanks() { return this.tanks; }
	@Override public FluidTank[] getSendingTanks() { return new FluidTank[] {this.tanks[2], this.tanks[3]}; }
	@Override public FluidTank[] getReceivingTanks() { return new FluidTank[] {this.tanks[0], this.tanks[1]}; }

	/* ---- 存档与同步 ---- */

	@Override
	public void readFromNBT(NBTTagCompound nbt) {
		super.readFromNBT(nbt);

		for(int i = 0; i < this.tanks.length; i++) {
			FluidTank tank = this.tanks[i];
			// 先让 FluidTank 自己读（存量、容量、压力），此时罐型会被它重置成 NONE
			tank.readFromNBT(nbt, String.valueOf(i));

			// 再补回罐型。HBM 的 FluidTank#writeToNBT 写的是 type.getID()（int），
			// 而 readFromNBT 用 getString 去读，永远读不到 → 读档后罐型一律变 NONE。
			// 这里另存一份类型名，读完再把存量填回去（setTankType 会把 fill 清零）。
			int fill = tank.getFill();
			String name = nbt.getString(TYPE_KEY + i);
			if(name != null && !name.isEmpty()) tank.setTankType(Fluids.fromName(name));
			tank.setFill(fill);
		}

		this.isOn = nbt.getBoolean("isOn");
		this.lastRate = nbt.getInteger("lastRate");
	}

	@Override
	public void writeToNBT(NBTTagCompound nbt) {
		super.writeToNBT(nbt);

		for(int i = 0; i < this.tanks.length; i++) {
			this.tanks[i].writeToNBT(nbt, String.valueOf(i));
			nbt.setString(TYPE_KEY + i, this.tanks[i].getTankType().getName());
		}

		nbt.setBoolean("isOn", this.isOn);
		nbt.setInteger("lastRate", this.lastRate);
	}

	@Override
	public void serialize(ByteBuf buf) {
		super.serialize(buf);

		for(FluidTank tank : this.tanks) tank.serialize(buf);

		buf.writeBoolean(this.isOn);
		buf.writeInt(this.lastRate);
	}

	@Override
	public void deserialize(ByteBuf buf) {
		super.deserialize(buf);

		for(FluidTank tank : this.tanks) tank.deserialize(buf);

		this.isOn = buf.readBoolean();
		this.lastRate = buf.readInt();
	}

	AxisAlignedBB bb = null;

	@Override
	public AxisAlignedBB getRenderBoundingBox() {

		if(bb == null) {
			// 与本体一致：侧向各 3 格、沿朝向 1~3 格、高 3 格
			bb = AxisAlignedBB.getBoundingBox(
				xCoord - 3,
				yCoord,
				zCoord - 3,
				xCoord + 4,
				yCoord + 3,
				zCoord + 4);
		}

		return bb;
	}

	@Override
	public double getMaxRenderDistanceSquared() {
		return 65536.0D;
	}
}
