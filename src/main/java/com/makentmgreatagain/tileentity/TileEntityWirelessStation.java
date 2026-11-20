package com.makentmgreatagain.tileentity;

import java.util.IdentityHashMap;
import java.util.UUID;

import com.hbm.tileentity.TileEntityLoadedBase;
import com.hbm.tileentity.TilePort.PortDef;

import api.hbm.energymk2.IBatteryItem;
import api.hbm.energymk2.IEnergyReceiverMK2;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.ForgeDirection;

/**
 * 无线充电站：只做两件事，从电网吃电、把电交给「认得出自己」的玩家。
 *
 * <h3>为什么由玩家侧发起</h3>
 * <p>站子不扫玩家、不扫附近机器，只等着被人查。装了连接器的靴子每 tick 在
 * {@code ItemWirelessConnector#modUpdate} 里按 NBT 里的坐标查<b>这一台</b>
 * （{@code world.blockExists} 先挡一次，未加载的区块不会被强行读取），
 * 查到了才调 {@link #chargePlayer}。所以离线玩家、没穿靴子的玩家、区块没加载的
 * 座子都不会消耗任何算力。</p>
 *
 * <h3>缓冲与扣除口径</h3>
 * <p>缓冲只用于平滑电网波动，不做储能。扣电严格按「实际充进电池的 HE」走：
 * 先算本 tick 预算（{@link #PLAYER_LIMIT} 与缓冲余额取小），充进多少就扣多少，
 * 没有电池需要补电时一分不扣。断网且缓冲见底后 {@code power} 归零，自然停机。</p>
 *
 * <h3>绑定标识</h3>
 * <p>{@link #uid} 是这台座子的随机 UUID，写在方块 NBT 里。连接器记的是这个值而不是
 * 坐标，所以「拆掉原座、同一位置另放一台」时新座的 uid 不同，旧连接器自动失效，
 * 必须重新配对。</p>
 */
public class TileEntityWirelessStation extends TileEntityLoadedBase implements IEnergyReceiverMK2 {

	/** 站内缓冲上限；实际网络输入受待充电需求和 INPUT_LIMIT 双重限制。 */
	public static final long BUFFER = 5_000_000L;
	public static final long INPUT_LIMIT = 10_000L;
	/** 每位玩家每 tick 的供给上限，背包与护甲共用这一份。 */
	public static final long PLAYER_LIMIT = 10_000L;
	/** 输电灯的余辉 tick 数：停止取电后灯还会亮这么久再转琥珀。 */
	public static final int LAMP_GLOW = 5;

	public long power;
	/** 绑定标识，首次访问时生成。 */
	public String uid;
	/**
	 * 本 tick 实际送出的合计 HE，右键查表用。
	 *
	 * <p>一台站可以被任意多个连接器指向，同一 tick 里会有多次 {@link #chargePlayer}
	 * 调用，所以这里必须累加而不是覆盖，否则多人同时取电时读数只反映最后一个人。</p>
	 */
	public long lastMoved;
	/** {@link #lastMoved} 所属的 world tick，跨 tick 时清零。 */
	private long movedTickStamp = -1L;
	/** 最后一次真正送出电的 world tick。 */
	private long lastTransmit;
	/** 客户端收到的灯态，-1 表示还没同步过。 */
	private int clientLamp = -1;
	private long demandTickStamp = -1L;
	private final IdentityHashMap<EntityPlayer, Boolean> chargingPlayers = new IdentityHashMap<EntityPlayer, Boolean>();
	private final IdentityHashMap<EntityPlayer, Boolean> playerMoved = new IdentityHashMap<EntityPlayer, Boolean>();

	protected PortDef[] cachedPorts;

	public String getUid() {
		if(uid == null || uid.isEmpty()) {
			uid = UUID.randomUUID().toString();
		}
		return uid;
	}

	public PortDef[] getPorts() {
		if(cachedPorts == null) {
			cachedPorts = new PortDef[] {
				PortDef.make(xCoord, yCoord, zCoord, ForgeDirection.DOWN),
				PortDef.make(xCoord, yCoord, zCoord, ForgeDirection.UP),
				PortDef.make(xCoord, yCoord, zCoord, ForgeDirection.NORTH),
				PortDef.make(xCoord, yCoord, zCoord, ForgeDirection.SOUTH),
				PortDef.make(xCoord, yCoord, zCoord, ForgeDirection.WEST),
				PortDef.make(xCoord, yCoord, zCoord, ForgeDirection.EAST)
			};
		}
		return cachedPorts;
	}

	@Override
	public void updateEntity() {
		if(worldObj.isRemote) return;

		this.setupPowerPorts(getPorts());
		this.updateAllPorts();
		this.receivePower();

		// 灯态与缓冲量给客户端：渲染器读 lampState 决定灯条颜色，右键也读 power
		this.networkPackNT(20);
	}

	/**
	 * 给玩家的背包与护甲充电。
	 *
	 * <p>额度是按<b>玩家</b>算的，不是按站算：一台站同时被 N 个连接器指上，
	 * 这一 tick 就会产生 N 次独立调用，每次各自拿到最多 {@link #PLAYER_LIMIT}，
	 * 全部从同一份 {@link #BUFFER} 里扣。缓冲不够时先到先得，后面的人只拿剩下的。</p>
	 *
	 * @return 本次实际送出的 HE，同时已从缓冲扣除
	 */
	public long chargePlayer(EntityPlayer player) {
		if(player == null || worldObj == null) return 0L;

		long now = worldObj.getTotalWorldTime();
		if(now != this.movedTickStamp) {
			this.movedTickStamp = now;
			this.lastMoved = 0L;
		}
		if(now != this.demandTickStamp) {
			this.demandTickStamp = now;
			this.playerMoved.clear();
			this.chargingPlayers.clear();
		}

		if(this.playerMoved.containsKey(player)) return 0L;
		this.playerMoved.put(player, Boolean.TRUE);
		long demand = getChargingDemand(player);
		if(demand <= 0L) return 0L;
		this.chargingPlayers.put(player, Boolean.TRUE);

		long budget = Math.min(PLAYER_LIMIT, this.power);
		long moved = 0L;
		if(budget > 0L) {
			moved += chargeSlots(player.inventory.mainInventory, budget - moved);
			moved += chargeSlots(player.inventory.armorInventory, budget - moved);
		}

		if(moved > 0L) {
			this.power -= moved;
			this.lastTransmit = now;
			this.lastMoved += moved;
		}
		return moved;
	}

	private long getChargingDemand(EntityPlayer player) {
		return Math.min(PLAYER_LIMIT, getChargingDemand(player.inventory.mainInventory) + getChargingDemand(player.inventory.armorInventory));
	}

	private long getChargingDemand(ItemStack[] slots) {
		if(slots == null) return 0L;
		long demand = 0L;
		for(ItemStack stack : slots) {
			if(stack == null || !(stack.getItem() instanceof IBatteryItem)) continue;
			IBatteryItem battery = (IBatteryItem) stack.getItem();
			long need = Math.max(0L, battery.getMaxCharge(stack) - battery.getCharge(stack));
			demand += Math.min(need, Math.max(0L, battery.getChargeRate(stack)));
			if(demand >= PLAYER_LIMIT) return PLAYER_LIMIT;
		}
		return demand;
	}

	private long chargeSlots(ItemStack[] slots, long budget) {
		if(slots == null || budget <= 0) return 0;

		long moved = 0;
		for(int i = 0; i < slots.length && moved < budget; i++) {
			moved += chargeOne(slots[i], budget - moved);
		}
		return moved;
	}

	private long chargeOne(ItemStack stack, long budget) {
		if(stack == null || stack.stackSize <= 0) return 0;
		if(!(stack.getItem() instanceof IBatteryItem)) return 0;

		IBatteryItem battery = (IBatteryItem) stack.getItem();
		long need = battery.getMaxCharge(stack) - battery.getCharge(stack);
		if(need <= 0) return 0;

		// 三边取小：缺额、物品自身上限、本 tick 剩余预算
		long amount = Math.min(need, Math.min(battery.getChargeRate(stack), budget));
		if(amount <= 0) return 0;

		battery.chargeBattery(stack, amount);
		return amount;
	}

	/** 0 = 断电灰灯，1 = 待机琥珀，2 = 正在输电青蓝。 */
	public int getLampState() {
		if(worldObj == null) return this.power > 0 ? 1 : 0;
		if(worldObj.isRemote) return this.clientLamp < 0 ? 0 : this.clientLamp;

		if(worldObj.getTotalWorldTime() - this.lastTransmit <= LAMP_GLOW) return 2;
		return this.power > 0 ? 1 : 0;
	}

	/** 供右键信息用：本 tick 是否处于输电状态。 */
	public boolean isTransmitting() {
		return this.getLampState() == 2;
	}

	@Override public long getPower() { return power; }
	@Override public void setPower(long power) { this.power = Math.min(BUFFER, Math.max(0L, power)); }
	@Override public long getMaxPower() { return BUFFER; }
	@Override public long getReceiverSpeed() {
		if(worldObj == null || worldObj.isRemote || worldObj.getTotalWorldTime() != this.demandTickStamp) return 0L;
		long demand = 0L;
		for(EntityPlayer player : this.chargingPlayers.keySet()) {
			demand += getChargingDemand(player);
			if(demand >= INPUT_LIMIT + this.power) break;
		}
		long unmet = Math.max(0L, demand - this.power);
		return Math.min(Math.min(INPUT_LIMIT, unmet), Math.max(0L, BUFFER - this.power));
	}

	@Override
	public void serialize(ByteBuf buf) {
		super.serialize(buf);
		buf.writeLong(this.power);
		buf.writeByte(this.getLampState());
	}

	@Override
	public void deserialize(ByteBuf buf) {
		super.deserialize(buf);
		this.setPower(buf.readLong());
		this.clientLamp = buf.readByte();
	}

	@Override
	public void readFromNBT(NBTTagCompound nbt) {
		super.readFromNBT(nbt);
		this.setPower(nbt.getLong("power"));
		// 没有 uid 的老存档按新座子处理，下次写盘时固化
		this.uid = nbt.hasKey("uid") ? nbt.getString("uid") : null;
	}

	@Override
	public void writeToNBT(NBTTagCompound nbt) {
		super.writeToNBT(nbt);
		nbt.setLong("power", this.power);
		nbt.setString("uid", this.getUid());
	}
}
