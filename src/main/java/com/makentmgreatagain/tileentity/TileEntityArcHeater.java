package com.makentmgreatagain.tileentity;

import com.hbm.blocks.BlockDummyable;
import com.hbm.main.MainRegistry;
import com.hbm.main.NTMSounds;
import com.hbm.sound.AudioWrapper;
import com.hbm.tileentity.IBufPacketReceiver;
import com.hbm.tileentity.TileEntityLoadedBase;
import com.hbm.tileentity.TilePort.PortDef;

import api.hbm.energymk2.IEnergyReceiverMK2;
import api.hbm.tile.IHeatSource;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.AxisAlignedBB;
import net.minecraftforge.common.util.ForgeDirection;

/**
 * 电弧加热器核心。
 *
 * <p>逻辑照搬原版电加热器（{@code TileEntityHeaterElectric}）：螺丝刀切 0~10 档，
 * 每 tick 扣电、往 {@link #heatEnergy} 里加同样档位的热量，热量每 tick 自然损耗 0.1%。
 * 相邻方块（如组合炉、坩埚、工业锅炉）从正下方的 {@link IHeatSource} 抽热，
 * 所以本机的 dummy 全部挂了 heatSource 代理，放在机体正上方也能抽到核心的热量。</p>
 *
 * <h3>数值怎么改</h3>
 * <p>所有平衡数值集中在下面四个常量，改完重新编译即可，不用动别处：</p>
 * <ul>
 * <li>{@link #MAX_CONSUMPTION}：第 10 档的耗电，单位 HE/t。原版第 10 档约 5,023 HE/t，
 * 这里按 20 倍取整成 100,000。</li>
 * <li>{@link #MAX_HEAT_GEN}：第 10 档的产热，单位 TU/t。原版是 1,000 TU/t，这里 10 倍。</li>
 * <li>{@link #POWER_CURVE}：耗电曲线指数。沿用原版的 1.4，低档位更省电、高档位更陡；
 * 想让各档位线性变化就改成 1.0。</li>
 * <li>{@link #POWER_BUFFER_TICKS}：内部电容能存多少 tick 的耗电（原版是 20 tick）。</li>
 * </ul>
 * <p>产热按档位线性分配（第 10 档 = {@link #MAX_HEAT_GEN}，第 5 档 = 一半），
 * 想改成别的曲线就改 {@link #getHeatGen()}。</p>
 */
public class TileEntityArcHeater extends TileEntityLoadedBase implements IHeatSource, IEnergyReceiverMK2, IBufPacketReceiver {

	/** 最高档位，和原版一样 0~10（0 档 = 关机） */
	public static final int MAX_SETTING = 10;
	/** 第 10 档耗电（HE/t）：原版 5,023 的 20 倍，取整到 100,000 */
	public static final long MAX_CONSUMPTION = 100_000L;
	/** 第 10 档产热（TU/t）：原版 1,000 的 10 倍 */
	public static final int MAX_HEAT_GEN = 10_000;
	/** 耗电曲线指数，原版 1.4 */
	public static final float POWER_CURVE = 1.4F;
	/** 内部电容 = 多少 tick 的耗电，原版 20 */
	public static final long POWER_BUFFER_TICKS = 20L;
	/** 热量上限（TU），与 TileEntityHeatexReborn 同口径，防满档空转时 int 溢出 */
	public static final int MAX_HEAT = 10_000_000;

	public long power;
	public int heatEnergy;
	public boolean isOn;
	/** 0 = 关机，1~10 = 档位；螺丝刀切换 */
	protected int setting = 0;

	private AudioWrapper audio;

	protected PortDef[] cachedPorts;

	/** 电源口：核心格本身就是接线箱格，插座面朝机器正面（-dir） */
	public PortDef[] getPorts() {

		if(cachedPorts == null) {
			ForgeDirection dir = ForgeDirection.getOrientation(this.getBlockMetadata() - BlockDummyable.offset);
			cachedPorts = new PortDef[] {
				PortDef.make(xCoord, yCoord, zCoord, dir.getOpposite())
			};
		}

		return cachedPorts;
	}

	@Override
	public void updateEntity() {

		if(!worldObj.isRemote) {

			this.setupPowerPorts(this.getPorts());
			this.updateAllPorts();
			this.receivePower();

			// 和原版一样：不用热就慢慢散掉
			this.heatEnergy *= 0.999;

			this.isOn = false;
			if(this.setting > 0 && this.power >= this.getConsumption()) {
				this.power -= this.getConsumption();
				this.heatEnergy = (int) Math.min(MAX_HEAT, (long) this.heatEnergy + this.getHeatGen());
				this.isOn = true;
			}

			networkPackNT(25);

		} else {

			if(isOn) {

				if(audio == null) {
					audio = createAudioLoop();
					audio.startSound();
				} else if(!audio.isPlaying()) {
					audio = rebootAudio(audio);
				}

				audio.updateVolume(getVolume(1F));
				audio.keepAlive();

			} else {

				if(audio != null) {
					audio.stopSound();
					audio = null;
				}
			}
		}
	}

	@Override
	public AudioWrapper createAudioLoop() {
		return MainRegistry.proxy.getLoopedSound(NTMSounds.ELECTRIC_HUM_LOOP, xCoord, yCoord, zCoord, 0.25F, 7.5F, 1.0F, 20);
	}

	@Override
	public void onChunkUnload() {

		if(audio != null) {
			audio.stopSound();
			audio = null;
		}
	}

	@Override
	public void invalidate() {

		super.invalidate();

		if(audio != null) {
			audio.stopSound();
			audio = null;
		}
	}

	@Override
	public void serialize(ByteBuf buf) {
		buf.writeBoolean(this.muffled);
		buf.writeByte(this.setting);
		buf.writeInt(this.heatEnergy);
		buf.writeBoolean(this.isOn);
	}

	@Override
	public void deserialize(ByteBuf buf) {
		this.muffled = buf.readBoolean();
		this.setting = buf.readByte();
		this.heatEnergy = buf.readInt();
		this.isOn = buf.readBoolean();
	}

	@Override
	public void readFromNBT(NBTTagCompound nbt) {
		super.readFromNBT(nbt);

		this.power = nbt.getLong("power");
		this.setting = nbt.getInteger("setting");
		this.heatEnergy = nbt.getInteger("heatEnergy");
	}

	@Override
	public void writeToNBT(NBTTagCompound nbt) {
		super.writeToNBT(nbt);

		nbt.setLong("power", power);
		nbt.setInteger("setting", setting);
		nbt.setInteger("heatEnergy", heatEnergy);
	}

	public void toggleSetting() {
		setting++;

		if(setting > MAX_SETTING)
			setting = 0;

		markDirty();
	}

	/* ---- 数值 ---- */

	/** 当前档位的耗电量（HE/t），0 档为 0 */
	public long getConsumption() {

		if(this.setting <= 0) return 0;

		double ratio = (double) this.setting / (double) MAX_SETTING;
		return (long) Math.ceil(Math.pow(ratio, POWER_CURVE) * (double) MAX_CONSUMPTION);
	}

	/** 当前档位的产热量（TU/t），按档位线性分配 */
	public int getHeatGen() {
		return this.setting * (MAX_HEAT_GEN / MAX_SETTING);
	}

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
		return this.getConsumption() * POWER_BUFFER_TICKS;
	}

	@Override
	public int getHeatStored() {
		return heatEnergy;
	}

	@Override
	public void useUpHeat(int heat) {
		this.heatEnergy = Math.max(0, this.heatEnergy - heat);
	}

	AxisAlignedBB bb = null;

	/**
	 * 模型占 4×3×2，核心格只是其中一个角，默认的单格包围盒会让模型在特定视角突然消失；
	 * 这里给一个覆盖整机（并向四周留余量）的包围盒。
	 */
	@Override
	public AxisAlignedBB getRenderBoundingBox() {

		if(bb == null) {
			bb = AxisAlignedBB.getBoundingBox(
				xCoord - 4,
				yCoord - 1,
				zCoord - 4,
				xCoord + 4,
				yCoord + 3,
				zCoord + 4);
		}

		return bb;
	}

	@Override
	@SideOnly(Side.CLIENT)
	public double getMaxRenderDistanceSquared() {
		return 65536.0D;
	}
}
