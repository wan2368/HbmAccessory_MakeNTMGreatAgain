package com.makentmgreatagain.tileentity;

import com.hbm.inventory.fluid.Fluids;
import com.hbm.inventory.fluid.tank.FluidTank;
import com.hbm.main.MainRegistry;
import com.hbm.tileentity.TileEntityLoadedBase;
import com.hbm.tileentity.TilePort.PortDef;
import com.hbm.tileentity.TilePortShapes;
import com.makentmgreatagain.init.ModAchievements;
import com.makentmgreatagain.util.EnvironmentPumpMedia;
import com.makentmgreatagain.util.EnvironmentPumpMedia.Medium;

import api.hbm.energymk2.IEnergyReceiverMK2;
import api.hbm.fluidmk2.IFluidStandardTransceiverMK2;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.AxisAlignedBB;

/**
 * 环境地下泵的方块实体。
 *
 * 与 HBM 电动地下水泵的差别：
 *   1. 产出介质由所在群系决定，切换时先排空输出罐再换罐体类型；
 *   2. 以 5 秒（100 tick）为一个结算周期，而不是每 tick 结算；
 *   3. 取消 Y≤70 限制，改为区间外按档位叠加耗电惩罚；
 *   4. 支持 2 个输出罐（主产 + 副产）与 2 个输入罐，端口沿用 HBM 的 4 面通用布局。
 *
 * 罐体数组长度必须恒定：基类的 provideFluid / receiveFluid 用 ports.length != tanks.length 做守卫，
 * 长度一变端口就不再更新。
 */
public class TileEntityEnvironmentPump extends TileEntityLoadedBase
		implements IFluidStandardTransceiverMK2, IEnergyReceiverMK2 {

	public static final long MAX_POWER = 40_000_000L;
	public static final int TANK_CAPACITY = 64_000;
	/** 群系检测间隔：10 秒 */
	public static final int BIOME_CHECK_INTERVAL = 200;

	public FluidTank[] inputTanks = new FluidTank[] {
			new FluidTank(Fluids.NONE, TANK_CAPACITY), new FluidTank(Fluids.NONE, TANK_CAPACITY) };
	public FluidTank[] outputTanks = new FluidTank[] {
			new FluidTank(Fluids.NONE, TANK_CAPACITY), new FluidTank(Fluids.NONE, TANK_CAPACITY) };

	private Medium active = EnvironmentPumpMedia.WATER;
	private Medium pending = null;
	/** 罐体类型是否已按 active 落过一次。初始 active 就是 WATER，updateMedium 会直接 return，所以必须单独补一次 */
	private boolean tanksReady;

	public long power;
	public boolean isOn;
	public boolean draining;
	public int progress;
	public boolean onGround;
	public int groundCheckDelay;
	public int biomeCheckDelay;
	public float rotor;
	public float lastRotor;

	protected PortDef[] cachedPorts;

	public PortDef[] getPorts() {
		if(cachedPorts == null) cachedPorts = TilePortShapes.flare(xCoord, yCoord, zCoord);
		return cachedPorts;
	}

	@Override
	public void updateEntity() {

		if(!worldObj.isRemote) {

			this.setupAllPorts(getPorts());
			this.updatePortPIFIFO();

			if(!tanksReady) { tanksReady = true; applyTankTypes(active); }

			if(groundCheckDelay > 0) groundCheckDelay--; else { groundCheckDelay = 20; onGround = checkGround(); }
			if(biomeCheckDelay > 0) biomeCheckDelay--; else { biomeCheckDelay = BIOME_CHECK_INTERVAL; updateMedium(); }

			// 切换介质时先等输出罐排空，避免旧液体凭空消失
			if(draining) {
				if(outputTanks[0].getFill() <= 0 && outputTanks[1].getFill() <= 0) {
					applyMedium(pending);
				}
			}

			this.isOn = false;

			if(active != null && onGround && !draining) {
				long cost = getEffectivePower();

				// 产出已被高度惩罚压到 0 时直接停机，免得白烧电
				if(power >= cost && getEffectiveOutputAmount() > 0 && hasRoom() && hasInput()) {
					this.isOn = true;
					this.power -= cost;
					this.progress++;
					if(this.progress >= EnvironmentPumpMedia.CYCLE_TICKS) {
						this.progress = 0;
						produce();
					}
				} else if(getEffectiveOutputAmount() <= 0) {
					// 「她罢工了」：必须是「产出被高度惩罚压到 0」这一种停机，
					// 缺电 / 缺料 / 罐满都不算 —— 那些是玩家没喂饱她，不是她不干。
					checkStrike();
				}
			}

			this.networkPackNT(150);

		} else {

			this.lastRotor = this.rotor;
			if(this.isOn) this.rotor += 10F;

			if(this.rotor >= 360F) {
				this.rotor -= 360F;
				this.lastRotor -= 360F;
				MainRegistry.proxy.playSoundClient(xCoord, yCoord, zCoord, "hbm:block.steamEngineOperate", 0.5F, 0.75F);
				MainRegistry.proxy.playSoundClient(xCoord, yCoord, zCoord, "game.neutral.swim.splash", 1F, 0.5F);
			}
		}
	}

	// ---------------- 群系与介质 ----------------

	private void updateMedium() {
		Medium target = EnvironmentPumpMedia.sample(worldObj, xCoord, yCoord, zCoord);
		if(target == active) {
			// 排空途中群系又变回当前介质：取消换型，否则排空结束后
			// 会用过期的 pending 把刚切回来的介质再切走一次
			if(draining) { this.pending = null; this.draining = false; }
			return;
		}

		// 同一种流体只换参数（如三种辐射坑），不打断产出
		if(active != null && active.output == target.output && active.output2 == target.output2) {
			this.active = target;
			return;
		}

		if(outputTanks[0].getFill() > 0 || outputTanks[1].getFill() > 0) {
			this.pending = target;
			this.draining = true;
			return;
		}

		applyMedium(target);
	}

	private void applyMedium(Medium m) {
		this.active = m;
		this.pending = null;
		this.draining = false;
		this.progress = 0;
		applyTankTypes(m);
	}

	/** 只改罐体类型，不动进度与排空状态：切换介质和首次初始化共用这一段 */
	private void applyTankTypes(Medium m) {
		if(m == null) return;

		// 输入介质不能与任一输出介质相同，否则同一位置的端口会争夺同一个网络节点
		boolean useSecond = m.output2 != null && m.output2 != m.output;
		boolean useInput = m.input != null && m.input != m.output && (!useSecond || m.input != m.output2);

		outputTanks[0].setTankType(m.output);
		outputTanks[1].setTankType(useSecond ? m.output2 : Fluids.NONE);
		inputTanks[0].setTankType(useInput ? m.input : Fluids.NONE);
		inputTanks[1].setTankType(Fluids.NONE);
	}

	/** 罐体类型是唯一事实来源：切换失败或未启用时这里自然为 false */
	private boolean inputActive() {
		return active != null && active.input != null && inputTanks[0].getTankType() == active.input;
	}

	private boolean secondaryActive() {
		return active != null && active.output2 != null && active.output2Amount > 0
				&& outputTanks[1].getTankType() == active.output2;
	}

	// ---------------- 运行 ----------------

	/** 每 tick 实际耗电 = 基础耗电 × 高度惩罚系数 */
	public long getEffectivePower() {
		if(active == null) return 0L;
		return (long) Math.ceil(active.power * EnvironmentPumpMedia.penaltyMultiplier(worldObj, yCoord));
	}

	/** 当前档位下每次结算实际要吃的输入量（越界越多） */
	public int getEffectiveInputAmount() {
		if(active == null || active.inputAmount <= 0) return 0;
		return (int) Math.ceil(active.inputAmount * EnvironmentPumpMedia.penaltyInputFactor(worldObj, yCoord));
	}

	/** 当前档位下每次结算实际产出的主产物（越界越少，可为 0） */
	public int getEffectiveOutputAmount() {
		if(active == null) return 0;
		return (int) Math.floor(active.outputAmount * EnvironmentPumpMedia.penaltyOutputFactor(worldObj, yCoord));
	}

	/** 同上，副产物 */
	public int getEffectiveOutput2Amount() {
		if(active == null || active.output2Amount <= 0) return 0;
		return (int) Math.floor(active.output2Amount * EnvironmentPumpMedia.penaltyOutputFactor(worldObj, yCoord));
	}

	private boolean hasInput() {
		if(!inputActive()) return true;
		return inputTanks[0].getFill() >= getEffectiveInputAmount();
	}

	private boolean hasRoom() {
		int out = getEffectiveOutputAmount();
		if(outputTanks[0].getMaxFill() - outputTanks[0].getFill() < out) return false;
		if(secondaryActive()) {
			if(outputTanks[1].getMaxFill() - outputTanks[1].getFill() < getEffectiveOutput2Amount()) return false;
		}
		return true;
	}

	private void produce() {
		int input = getEffectiveInputAmount();
		if(inputActive() && input > 0) {
			inputTanks[0].setFill(Math.max(0, inputTanks[0].getFill() - input));
		}
		add(outputTanks[0], getEffectiveOutputAmount());
		if(secondaryActive()) add(outputTanks[1], getEffectiveOutput2Amount());

		checkOverworked();
	}

	/**
	 * 「吃力不讨好」：偏离工作高度带 10 档以上还在转，且玩家就在 24 格内看着。
	 *
	 * <p>挂在结算点上而不是每 tick 判——{@code getClosestPlayer} 要遍历在线玩家，
	 * 5 秒一次已经足够（玩家总是要站一会儿才会发现产出少了一半）。</p>
	 */
	private void checkOverworked() {

		if(EnvironmentPumpMedia.penaltySteps(worldObj, yCoord) < ModAchievements.PUMP_PENALTY_STEPS) return;

		EntityPlayer player = worldObj.getClosestPlayer(
				xCoord + 0.5D, yCoord + 0.5D, zCoord + 0.5D, 24D);
		if(player != null) ModAchievements.unlock(player, ModAchievements.pump_overworked);
	}

	/**
	 * 「她罢工了」（隐藏成就）：泵被架到产出归零的档位（20 档 = 偏离高度带 100 格），
	 * 机器直接停机不干活，且玩家就在 24 格内看着。
	 *
	 * <p>与「吃力不讨好」的区别：那条是「还在转但亏」，这条是「彻底不转了」。
	 * 判据用产出归零而不是单看档数，这样换了配方 / 改了惩罚系数也仍然对得上。</p>
	 *
	 * <p>挂在停机分支上，每 tick 都会问一次 {@code getClosestPlayer}。
	 * 但它只在「产出为 0」这个罕见状态下才走到，正常运行的泵根本不进这里，开销可忽略。</p>
	 */
	private void checkStrike() {

		if(EnvironmentPumpMedia.penaltySteps(worldObj, yCoord) < ModAchievements.PUMP_STRIKE_STEPS) return;

		EntityPlayer player = worldObj.getClosestPlayer(
				xCoord + 0.5D, yCoord + 0.5D, zCoord + 0.5D, 24D);
		if(player != null) ModAchievements.unlock(player, ModAchievements.pump_strike);
	}

	private void add(FluidTank tank, int amount) {
		tank.setFill(Math.min(tank.getMaxFill(), tank.getFill() + amount));
	}

	/**
	 * 地基检测。原版用 hasNoSky 直接否决下界/末地，这里改为只看脚下一层是否满实体，
	 * 从而在原版与 HBM 的所有维度都成立。
	 */
	protected boolean checkGround() {
		for(int x = -1; x <= 1; x++) {
			for(int z = -1; z <= 1; z++) {
				if(!worldObj.getBlock(xCoord + x, yCoord - 1, zCoord + z).isNormalCube()) return false;
			}
		}
		return true;
	}

	// ---------------- 对外接口 ----------------

	@Override public FluidTank[] getSendingTanks() { return outputTanks; }
	@Override public FluidTank[] getReceivingTanks() { return inputTanks; }

	@Override
	public FluidTank[] getAllTanks() {
		return new FluidTank[] { inputTanks[0], inputTanks[1], outputTanks[0], outputTanks[1] };
	}

	@Override public long getPower() { return power; }
	@Override public void setPower(long power) { this.power = Math.min(MAX_POWER, power); }
	@Override public long getMaxPower() { return MAX_POWER; }

	public Medium getMedium() { return active; }
	public Medium getPending() { return pending; }

	// ---------------- 同步与存档 ----------------

	@Override
	public void serialize(ByteBuf buf) {
		super.serialize(buf);
		buf.writeLong(power);
		buf.writeBoolean(isOn);
		buf.writeBoolean(draining);
		buf.writeBoolean(onGround);
		buf.writeInt(progress);
		buf.writeInt(active == null ? 0 : active.index);
		for(int i = 0; i < inputTanks.length; i++) inputTanks[i].serialize(buf);
		for(int i = 0; i < outputTanks.length; i++) outputTanks[i].serialize(buf);
	}

	@Override
	public void deserialize(ByteBuf buf) {
		super.deserialize(buf);
		this.power = buf.readLong();
		this.isOn = buf.readBoolean();
		this.draining = buf.readBoolean();
		this.onGround = buf.readBoolean();
		this.progress = buf.readInt();
		this.active = EnvironmentPumpMedia.byIndex(buf.readInt());
		for(int i = 0; i < inputTanks.length; i++) inputTanks[i].deserialize(buf);
		for(int i = 0; i < outputTanks.length; i++) outputTanks[i].deserialize(buf);
	}

	@Override
	public void readFromNBT(NBTTagCompound nbt) {
		super.readFromNBT(nbt);
		this.power = nbt.getLong("power");
		this.progress = nbt.getInteger("progress");
		this.active = EnvironmentPumpMedia.byId(nbt.getString("medium"));
		// 排空/换型状态也要落盘：不恢复 pending 的话，排空结束后机器会停在旧介质，
		// 违背「先排空再换型」的设计；draining 不恢复则读档后立刻恢复产出，旧罐液体直接蒸发
		if(nbt.hasKey("pending")) this.pending = EnvironmentPumpMedia.byId(nbt.getString("pending"));
		this.draining = nbt.getBoolean("draining");
		for(int i = 0; i < inputTanks.length; i++) inputTanks[i].readFromNBT(nbt, "in" + i);
		for(int i = 0; i < outputTanks.length; i++) outputTanks[i].readFromNBT(nbt, "out" + i);
	}

	@Override
	public void writeToNBT(NBTTagCompound nbt) {
		super.writeToNBT(nbt);
		nbt.setLong("power", power);
		nbt.setInteger("progress", progress);
		nbt.setString("medium", active == null ? "water" : active.id);
		if(pending != null) nbt.setString("pending", pending.id);
		nbt.setBoolean("draining", draining);
		for(int i = 0; i < inputTanks.length; i++) inputTanks[i].writeToNBT(nbt, "in" + i);
		for(int i = 0; i < outputTanks.length; i++) outputTanks[i].writeToNBT(nbt, "out" + i);
	}

	// ---------------- 渲染 ----------------

	AxisAlignedBB bb = null;

	@Override
	public AxisAlignedBB getRenderBoundingBox() {
		if(bb == null) {
			bb = AxisAlignedBB.getBoundingBox(xCoord - 1, yCoord, zCoord - 1, xCoord + 2, yCoord + 5, zCoord + 2);
		}
		return bb;
	}

	@Override
	@SideOnly(Side.CLIENT)
	public double getMaxRenderDistanceSquared() {
		return 65536.0D;
	}
}
