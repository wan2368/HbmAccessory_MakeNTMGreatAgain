package com.makentmgreatagain.tileentity;

import com.hbm.blocks.BlockDummyable;
import com.hbm.inventory.fluid.FluidType;
import com.hbm.inventory.fluid.Fluids;
import com.hbm.inventory.fluid.tank.FluidTank;
import com.hbm.main.MainRegistry;
import com.hbm.sound.AudioWrapper;
import com.hbm.tileentity.TileEntityMachineBase;
import com.hbm.tileentity.TilePort.PortDef;
import com.makentmgreatagain.blocks.BlockSupercriticalMHDGenerator;
import com.makentmgreatagain.init.ModFluids;

import api.hbm.energymk2.IEnergyProviderMK2;
import api.hbm.fluidmk2.IFluidStandardTransceiverMK2;
import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraftforge.common.util.ForgeDirection;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * 超临界磁流体膨胀发电机核心 TileEntity（无 GUI、不存电仅实时直发，参考利维坦 {@code TileEntityTurbineBase}）。
 *
 * <p>每 1 mB 超临界蒸汽 → 10M HE 实时供电 + 10 mB 液态水闭环回流。</p>
 */
public class TileEntitySupercriticalMHDGenerator extends TileEntityMachineBase
		implements IEnergyProviderMK2, IFluidStandardTransceiverMK2 {

	public static final long HE_PER_MB = 10_000_000L;
	public static final int WATER_PER_MB = 10;

	/** 与利维坦一致的 10 亿 mB 级无上限流道缓冲，不人为限制最大发电量 */
	public static final int STEAM_CAPACITY = 1_000_000_000;
	public static final int WATER_CAPACITY = 1_000_000_000;

	/** 当 tick 实时发电输出（不储能，无发电上限，每 tick 随当前内部超临界蒸汽量动态变化） */
	public long powerBuffer;
	public boolean operational;
	/** 同步给客户端 HUD 显示的消耗前当 tick 内部超临界蒸汽量（与利维坦 TileEntityTurbineBase 一致） */
	private int syncedSteamFill;

	public final FluidTank[] tanks = new FluidTank[] {
		new FluidTank(supercriticalSteamType(), STEAM_CAPACITY),
		new FluidTank(Fluids.WATER, WATER_CAPACITY)
	};

	private PortDef[] cachedFluidPorts;
	private PortDef[] cachedPowerPorts;

	/** 客户端转子惯性动画与电弧状态 */
	@SideOnly(Side.CLIENT)
	public float rotorAngle;
	@SideOnly(Side.CLIENT)
	public float lastRotorAngle;
	@SideOnly(Side.CLIENT)
	public float rotorSpeed;
	@SideOnly(Side.CLIENT)
	public float loadFactor;
	@SideOnly(Side.CLIENT)
	public int clientTicks;
	@SideOnly(Side.CLIENT)
	public int arcActiveTicks;
	@SideOnly(Side.CLIENT)
	public long arcSeed;
	@SideOnly(Side.CLIENT)
	private int arcCooldown;

	/** 客户端 3D 双层叠加声景：超导磁体共鸣 + 超临界膨胀喷流 */
	@SideOnly(Side.CLIENT)
	private AudioWrapper audioMagHum;
	@SideOnly(Side.CLIENT)
	private AudioWrapper audioFlow;

	public TileEntitySupercriticalMHDGenerator() {
		super(0);
	}

	public static FluidType supercriticalSteamType() {
		FluidType t = ModFluids.SUPERCRITICAL_STEAM.type;
		return t == null ? Fluids.NONE : t;
	}

	@Override
	public String getName() {
		return "container.supercritical_mhd_generator";
	}

	private PortDef[] buildPorts(int[][] relPorts) {
		PortDef[] out = new PortDef[relPorts.length];
		for(int i = 0; i < relPorts.length; i++) {
			out[i] = PortDef.make(
					xCoord + relPorts[i][0],
					yCoord + relPorts[i][1],
					zCoord + relPorts[i][2],
					ForgeDirection.getOrientation(relPorts[i][3]));
		}
		return out;
	}

	public PortDef[] getFluidPorts() {
		if(cachedFluidPorts == null && this.getBlockType() instanceof BlockSupercriticalMHDGenerator) {
			ForgeDirection dir = ForgeDirection.getOrientation(this.getBlockMetadata() - BlockDummyable.offset);
			cachedFluidPorts = buildPorts(((BlockSupercriticalMHDGenerator) this.getBlockType()).getFluidPortsAtCore(dir));
		}
		return cachedFluidPorts == null ? new PortDef[0] : cachedFluidPorts;
	}

	public PortDef[] getPowerPorts() {
		if(cachedPowerPorts == null && this.getBlockType() instanceof BlockSupercriticalMHDGenerator) {
			ForgeDirection dir = ForgeDirection.getOrientation(this.getBlockMetadata() - BlockDummyable.offset);
			cachedPowerPorts = buildPorts(((BlockSupercriticalMHDGenerator) this.getBlockType()).getPowerPortsAtCore(dir));
		}
		return cachedPowerPorts == null ? new PortDef[0] : cachedPowerPorts;
	}

	@Override
	public void updateEntity() {
		if(worldObj.isRemote) {
			updateClientAnimationAndAudio();
			return;
		}

		FluidType scs = supercriticalSteamType();
		if(scs != Fluids.NONE && tanks[0].getTankType() != scs) {
			tanks[0].setTankType(scs);
		}
		if(tanks[1].getTankType() != Fluids.WATER) {
			tanks[1].setTankType(Fluids.WATER);
		}
		if(tanks[0].getMaxFill() != STEAM_CAPACITY) {
			tanks[0].changeTankSize(STEAM_CAPACITY);
		}
		if(tanks[1].getMaxFill() != WATER_CAPACITY) {
			tanks[1].changeTankSize(WATER_CAPACITY);
		}

		// 与 TileEntityTurbineBase 一致：先通过端口向外供电与收发流体，再清空 powerBuffer（不储能）
		this.setupPowerPorts(getPowerPorts());
		this.setupFluidPorts(getFluidPorts());
		this.updatePortPOFIFO();

		this.powerBuffer = 0L;
		this.operational = false;

		// 记录本 tick 消耗前的内部超临界蒸汽量，供 HUD 显示与本 tick 实时发电结算（无最大发电量上限）
		int steamAvail = tanks[0].getFill();
		this.syncedSteamFill = steamAvail;

		int waterOps = (tanks[1].getMaxFill() - tanks[1].getFill()) / WATER_PER_MB;
		int ops = Math.min(steamAvail, waterOps);

		if(ops > 0) {
			tanks[0].setFill(steamAvail - ops);
			tanks[1].setFill(tanks[1].getFill() + ops * WATER_PER_MB);
			this.powerBuffer = (long) ops * HE_PER_MB;
			this.operational = true;
			this.markDirty();
		}

		this.networkPackNT(150);
	}

	/* ========================== IEnergyProviderMK2（不存电，与利维坦一致） ========================== */

	@Override
	public long getPower() {
		return this.powerBuffer;
	}

	@Override
	public void setPower(long power) {
		this.powerBuffer = power;
	}

	@Override
	public long getMaxPower() {
		return this.powerBuffer;
	}

	/* ========================== IFluidStandardTransceiverMK2 ========================== */

	@Override
	public FluidTank[] getReceivingTanks() {
		return new FluidTank[] { tanks[0] };
	}

	@Override
	public FluidTank[] getSendingTanks() {
		return new FluidTank[] { tanks[1] };
	}

	@Override
	public FluidTank[] getAllTanks() {
		return tanks;
	}

	/* ========================== 网络同步 & NBT ========================== */

	@Override
	public void serialize(ByteBuf buf) {
		super.serialize(buf);
		buf.writeLong(this.powerBuffer);
		buf.writeBoolean(this.operational);
		int actualSteam = tanks[0].getFill();
		tanks[0].setFill(this.syncedSteamFill);
		tanks[0].serialize(buf);
		tanks[0].setFill(actualSteam);
		tanks[1].serialize(buf);
	}

	@Override
	public void deserialize(ByteBuf buf) {
		super.deserialize(buf);
		this.powerBuffer = buf.readLong();
		this.operational = buf.readBoolean();
		tanks[0].deserialize(buf);
		tanks[1].deserialize(buf);
	}

	@Override
	public void readFromNBT(NBTTagCompound nbt) {
		super.readFromNBT(nbt);
		this.powerBuffer = nbt.getLong("power");
		this.operational = nbt.getBoolean("operational");
		tanks[0].readFromNBT(nbt, "steam");
		tanks[1].readFromNBT(nbt, "water");
		if(tanks[0].getMaxFill() != STEAM_CAPACITY) {
			tanks[0].changeTankSize(STEAM_CAPACITY);
		}
		if(tanks[1].getMaxFill() != WATER_CAPACITY) {
			tanks[1].changeTankSize(WATER_CAPACITY);
		}
	}

	@Override
	public void writeToNBT(NBTTagCompound nbt) {
		super.writeToNBT(nbt);
		nbt.setLong("power", this.powerBuffer);
		nbt.setBoolean("operational", this.operational);
		tanks[0].writeToNBT(nbt, "steam");
		tanks[1].writeToNBT(nbt, "water");
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

	@Override
	@SideOnly(Side.CLIENT)
	public AxisAlignedBB getRenderBoundingBox() {
		return AxisAlignedBB.getBoundingBox(xCoord - 9, yCoord, zCoord - 9, xCoord + 10, yCoord + 8, zCoord + 10);
	}

	@Override
	@SideOnly(Side.CLIENT)
	public double getMaxRenderDistanceSquared() {
		return 65536.0D;
	}

	/* ========================== 客户端动画与 3D 叠加音效 ========================== */

	@SideOnly(Side.CLIENT)
	private void updateClientAnimationAndAudio() {
		// 1. 负载系数（对数映射，避免高发电量时转速与音调飙升过快）
		this.clientTicks++;
		float load = 0.0F;
		if(this.operational && this.powerBuffer > 0L) {
			load = (float) Math.min(1.0D, Math.log10(1.0D + (double) this.powerBuffer / (double) HE_PER_MB) / 4.0D);
		}
		this.loadFactor += (load - this.loadFactor) * 0.12F;

		// 2. 前置内嵌叶轮转子惯性加减速（无倒转跳变）
		if(this.operational) {
			float targetSpeed = 16.0F + load * 12.0F;
			this.rotorSpeed += (targetSpeed - this.rotorSpeed) * 0.08F;
		} else {
			this.rotorSpeed *= 0.94F;
			if(this.rotorSpeed < 0.05F) {
				this.rotorSpeed = 0.0F;
			}
		}

		this.lastRotorAngle = this.rotorAngle;
		this.rotorAngle += this.rotorSpeed;
		if(this.rotorAngle >= 360.0F) {
			this.rotorAngle -= 360.0F;
			this.lastRotorAngle -= 360.0F;
		}

		// 3. 高压过载电弧脉冲（随负载提升触发频率，与视觉电弧同步噼啪声）
		float spinNorm = Math.min(1.25F, this.rotorSpeed / 18.0F);
		boolean arcTriggeredThisTick = false;
		if(this.operational && spinNorm > 0.35F) {
			if(this.arcActiveTicks > 0) {
				this.arcActiveTicks--;
			} else {
				if(this.arcCooldown <= 0) {
					this.arcActiveTicks = 6 + this.worldObj.rand.nextInt(5);
					this.arcSeed = this.worldObj.rand.nextLong();
					int baseWait = (int) (22 - load * 12.0F);
					this.arcCooldown = Math.max(6, baseWait) + this.worldObj.rand.nextInt(14);
					arcTriggeredThisTick = true;
				} else {
					this.arcCooldown--;
				}
			}
		} else {
			this.arcActiveTicks = 0;
			if(this.arcCooldown < 8) {
				this.arcCooldown = 8;
			}
		}

		// 4. 3D 双层循环声景 + 电弧同步微噼啪声
		if(MainRegistry.proxy.me() == null) return;

		float centerX = this.xCoord + 0.5F;
		float centerY = this.yCoord + 3.5F;
		float centerZ = this.zCoord + 0.5F;
		float range = 36.0F;
		double distSq = MainRegistry.proxy.me().getDistanceSq(centerX, centerY, centerZ);

		if(distSq > 48.0D * 48.0D) {
			this.stopAudio();
			return;
		}

		if(spinNorm > 0.02F) {
			float amp = Math.min(1.0F, spinNorm);

			// 第一层：超导磁流体共鸣低频嗡鸣（随转子惯性与发电负载平滑调频）
			float magPitch = 0.48F + amp * 0.62F + load * 0.14F;
			float magVol = amp * 0.95F;
			if(this.audioMagHum == null) {
				this.audioMagHum = MainRegistry.proxy.getLoopedSound(
						"makentmgreatagain:block.supercriticalMhdMag",
						centerX, centerY, centerZ,
						magVol, range, magPitch, 20);
				this.audioMagHum.startSound();
			} else {
				this.audioMagHum.updateVolume(magVol);
				this.audioMagHum.updatePitch(magPitch);
				this.audioMagHum.keepAlive();
			}

			// 第二层：超临界蒸汽高压膨胀气流声（停机时先于磁体惯性回落）
			float flowPitch = 0.55F + amp * 0.65F + load * 0.18F;
			float flowVol = amp * (this.operational ? 0.65F : 0.30F);
			if(this.audioFlow == null) {
				this.audioFlow = MainRegistry.proxy.getLoopedSound(
						"makentmgreatagain:block.supercriticalMhdFlow",
						centerX, centerY, centerZ,
						flowVol, range, flowPitch, 20);
				this.audioFlow.startSound();
			} else {
				this.audioFlow.updateVolume(flowVol);
				this.audioFlow.updatePitch(flowPitch);
				this.audioFlow.keepAlive();
			}

			// 第三层：与高压电弧同步的电晕噼啪声
			if(arcTriggeredThisTick && distSq < 32.0D * 32.0D) {
				float arcPitch = 1.30F + this.worldObj.rand.nextFloat() * 0.35F;
				this.worldObj.playSound(
						centerX, this.yCoord + 4.8D, centerZ,
						"makentmgreatagain:block.supercriticalMhdArc",
						0.22F, arcPitch, false);
			}
		} else {
			this.stopAudio();
		}
	}

	@SideOnly(Side.CLIENT)
	private void stopAudio() {
		if(this.audioMagHum != null) {
			this.audioMagHum.stopSound();
			this.audioMagHum = null;
		}
		if(this.audioFlow != null) {
			this.audioFlow.stopSound();
			this.audioFlow = null;
		}
	}

	@Override
	public void onChunkUnload() {
		super.onChunkUnload();
		if(this.worldObj != null && this.worldObj.isRemote) {
			this.stopAudio();
		}
	}

	@Override
	public void invalidate() {
		super.invalidate();
		if(this.worldObj != null && this.worldObj.isRemote) {
			this.stopAudio();
		}
	}
}
