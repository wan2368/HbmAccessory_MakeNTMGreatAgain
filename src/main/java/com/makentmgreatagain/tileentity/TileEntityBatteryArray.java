package com.makentmgreatagain.tileentity;

import java.util.ArrayList;
import java.util.List;

import com.hbm.tileentity.machine.storage.TileEntityBatteryBase;
import com.hbm.tileentity.TilePort.PortDef;
import com.hbm.util.fauxpointtwelve.BlockPos;
import com.hbm.util.fauxpointtwelve.DirPos;
import com.makentmgreatagain.blocks.BatteryCellSpec;
import com.makentmgreatagain.blocks.BatteryPackStats;
import com.makentmgreatagain.blocks.IBatteryCell;
import com.makentmgreatagain.init.ModBlocks;

import api.hbm.energymk2.IBatteryItem;
import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.ForgeDirection;

/**
 * 大型电池组中心控制器。
 *
 * <h2>交互完全照电池座</h2>
 * <p>继承 {@link TileEntityBatteryBase}：红石高/低模式、连接优先级、控制包
 * （low/high/priority 三个按钮）、比较器输出、电力节点全部免费获得，
 * 与现版本电池座一模一样。GUI 用附属自绘贴图，槽位/按钮坐标同电池座。</p>
 *
 * <h2>容量与速率来自结构内元件（PWR 式成型）</h2>
 * <p>成型时泛洪收集电池/电容，用 {@link BatteryPackStats} 聚合：
 * 容量=电池之和、基础速率=最低电池、电容大幅提速可堆叠。
 * 槽 0 仍可放电池物品（同电池座），其容量/速率叠加进整组。</p>
 *
 * <h2>电力经端口</h2>
 * <p>成型时记录端口位置，{@code setupPowerPorts} + 收/发电，纯电力接口，
 * 不接流体。端口方块被替换成成型壳后，电力仍由本控制器经端口位置转发。</p>
 */
public class TileEntityBatteryArray extends TileEntityBatteryBase {

	public boolean assembled;
	public long power;
	public long delta;

	private long maxPower;
	private long chargeRate;
	private long dischargeRate;

	/** 成型时记录的端口位置（电力转发用）。 */
	private final List<BlockPos> portPositions = new ArrayList<BlockPos>();
	private PortDef[] cachedPorts;

	private final long[] log = new long[20];

	public TileEntityBatteryArray() {
		super(1);
		// 默认「缓冲」：既接收也输出。基类默认 redLow=0(只输入)/redHigh=2(只输出)，
		// 那样成型后无红石时只能充不能放，实测表现为「接线正常但不工作」。
		this.redLow = mode_buffer;
		this.redHigh = mode_buffer;
	}

	@Override
	public String getName() {
		return "container.mnga_battery_array";
	}

	@Override
	public void updateEntity() {
		long prev = power;

		// 基类已经用 getPortPos()/getConPos()（本类返回的是端口坐标）建好 PowerNode，
		// 并按红石模式 addReceiver/addProvider。这里<b>不能</b>再调 setupPowerPorts
		// 建第二套节点：同一个 owner 被两套网重复登记会互相覆盖，实测就是充不进电。
		super.updateEntity();

		if(!worldObj.isRemote) {
			long avg = (power + prev) / 2;
			delta = avg - log[0];
			for(int i = 1; i < log.length; i++) log[i - 1] = log[i];
			log[19] = avg;
		}
	}

	/** 成型后由方块调用：记录端口、聚合容量。 */
	public void setup(List<BlockPos> ports, BatteryPackStats stats) {
		portPositions.clear();
		portPositions.addAll(ports);
		cachedPorts = null;
		applyStats(stats);
		assembled = true;
		rebuildNode();
		markDirty();
	}

	public void disassemble() {
		assembled = false;
		portPositions.clear();
		cachedPorts = null;
		maxPower = 0;
		chargeRate = 0;
		dischargeRate = 0;
		power = 0;
		rebuildNode();
		markDirty();
	}

	/**
	 * 端口坐标变了必须让基类节点重建：基类只在 {@code node == null || expired} 时才重建，
	 * 否则电力仍连在成型前的旧坐标上，表现就是「成型后接了线却不充电」。
	 */
	private void rebuildNode() {
		if(worldObj == null || worldObj.isRemote) return;
		if(this.node != null) {
			com.hbm.uninos.UniNodespace.destroyNode(worldObj, xCoord, yCoord, zCoord,
					api.hbm.energymk2.Nodespace.THE_POWER_PROVIDER);
			this.node = null;
		}
	}

	private void applyStats(BatteryPackStats s) {
		maxPower = s.capacity;
		chargeRate = s.chargeSpeed;
		dischargeRate = s.dischargeSpeed;
		if(power > maxPower) power = maxPower;
	}

	/** 端口位置 → PortDef（朝外）。 */
	public PortDef[] getPorts() {
		if(cachedPorts == null) {
			List<PortDef> list = new ArrayList<PortDef>();
			for(BlockPos p : portPositions) {
				ForgeDirection dir = outwardDir(p);
				list.add(PortDef.make(p.getX(), p.getY(), p.getZ(), dir));
			}
			cachedPorts = list.toArray(new PortDef[0]);
		}
		return cachedPorts;
	}

	private ForgeDirection outwardDir(BlockPos p) {
		int dx = p.getX() - xCoord, dy = p.getY() - yCoord, dz = p.getZ() - zCoord;
		if(Math.abs(dx) >= Math.abs(dy) && Math.abs(dx) >= Math.abs(dz))
			return dx > 0 ? ForgeDirection.EAST : ForgeDirection.WEST;
		if(Math.abs(dy) >= Math.abs(dz))
			return dy > 0 ? ForgeDirection.UP : ForgeDirection.DOWN;
		return dz > 0 ? ForgeDirection.SOUTH : ForgeDirection.NORTH;
	}

	// ---- 电力接口（结构 + 槽0物品 叠加） ----

	/**
	 * 当前总电量。界面已删掉电池槽，容量完全由结构内元件决定，
	 * 所以不再叠加槽0物品（槽位仅为兼容基类而保留，不暴露到界面）。
	 */
	@Override
	public long getPower() {
		return power;
	}

	/**
	 * 写入<b>目标总电量</b>。
	 *
	 * <p><b>语义必须是「总量」而不是「增量」</b>：电力网充电走
	 * {@code IEnergyReceiverMK2.transferPower} → {@code setPower(当前总量 + 本次充入)}，
	 * 放电走 {@code usePower} → {@code setPower(当前总量 - 本次放出)}。
	 * 早先把入参当成增量、还会去改槽0物品，导致电量被反复抹掉 ——
	 * 表现就是「接线正常但充不进电」。</p>
	 *
	 * <p>界面已删掉电池槽，这里只有结构池一个储能体，钳到 [0, maxPower]。</p>
	 */
	@Override
	public void setPower(long total) {
		if(total < 0) total = 0;
		power = Math.min(total, maxPower);
	}

	@Override
	public long getMaxPower() {
		return maxPower;
	}

	@Override
	public long getProviderSpeed() {
		int mode = getRelevantMode(true);
		if(mode != mode_output && mode != mode_buffer) return 0;
		return dischargeRate;
	}

	@Override
	public long getReceiverSpeed() {
		int mode = getRelevantMode(true);
		if(mode != mode_input && mode != mode_buffer) return 0;
		return chargeRate;
	}

	// ---- 端口/连接位置（基类红石判定用） ----

	@Override
	public BlockPos[] getPortPos() {
		if(portPositions.isEmpty()) return new BlockPos[] { new BlockPos(xCoord, yCoord, zCoord) };
		return portPositions.toArray(new BlockPos[0]);
	}

	/**
	 * 端口对外的连接点：<b>六向全开</b>。
	 *
	 * <p>电力网配对（{@code UniNodespace.checkConnection}）要求「连接点坐标相同 + 方向严格互反」。
	 * 线缆的连接点是「线缆坐标 + 朝向」，正好落在端口格上、方向指向端口。
	 * 早先这里用 {@code outwardDir}（按控制器到端口的位移估算面法线）只给一个方向，
	 * 端口在棱/角或控制器不在中心时方向就算错，配对失败 ——
	 * 表现为「线看起来连上了，却既不充电也不放电」。</p>
	 *
	 * <p>改成和线缆一样六向全开，任何一侧接线都能配对成功。</p>
	 */
	@Override
	public DirPos[] getConPos() {
		List<DirPos> list = new ArrayList<DirPos>();
		List<BlockPos> ps = portPositions.isEmpty()
				? java.util.Collections.singletonList(new BlockPos(xCoord, yCoord, zCoord))
				: portPositions;

		for(BlockPos p : ps) {
			for(ForgeDirection d : ForgeDirection.VALID_DIRECTIONS) {
				list.add(new DirPos(p.getX() + d.offsetX, p.getY() + d.offsetY, p.getZ() + d.offsetZ, d));
			}
		}
		return list.toArray(new DirPos[0]);
	}

	// ---- 序列化 ----

	@Override
	public void serialize(io.netty.buffer.ByteBuf buf) {
		super.serialize(buf);
		buf.writeBoolean(assembled);
		buf.writeLong(power);
		buf.writeLong(maxPower);
		buf.writeLong(chargeRate);
		buf.writeLong(dischargeRate);
		buf.writeLong(delta);
	}

	@Override
	public void deserialize(io.netty.buffer.ByteBuf buf) {
		super.deserialize(buf);
		assembled = buf.readBoolean();
		power = buf.readLong();
		maxPower = buf.readLong();
		chargeRate = buf.readLong();
		dischargeRate = buf.readLong();
		delta = buf.readLong();
	}

	@Override
	public void readFromNBT(NBTTagCompound nbt) {
		super.readFromNBT(nbt);
		assembled = nbt.getBoolean("assembled");
		power = nbt.getLong("power");
		maxPower = nbt.getLong("maxPower");
		chargeRate = nbt.getLong("chargeRate");
		dischargeRate = nbt.getLong("dischargeRate");
		portPositions.clear();
		NBTTagList list = nbt.getTagList("ports", 10);
		for(int i = 0; i < list.tagCount(); i++) {
			NBTTagCompound t = list.getCompoundTagAt(i);
			portPositions.add(new BlockPos(t.getInteger("x"), t.getInteger("y"), t.getInteger("z")));
		}
		cachedPorts = null;
	}

	@Override
	public void writeToNBT(NBTTagCompound nbt) {
		super.writeToNBT(nbt);
		nbt.setBoolean("assembled", assembled);
		nbt.setLong("power", power);
		nbt.setLong("maxPower", maxPower);
		nbt.setLong("chargeRate", chargeRate);
		nbt.setLong("dischargeRate", dischargeRate);
		NBTTagList list = new NBTTagList();
		for(BlockPos p : portPositions) {
			NBTTagCompound t = new NBTTagCompound();
			t.setInteger("x", p.getX());
			t.setInteger("y", p.getY());
			t.setInteger("z", p.getZ());
			list.appendTag(t);
		}
		nbt.setTag("ports", list);
	}

	/** 供 GUI 显示的结构参数。 */
	public long getStructMax() { return maxPower; }
	public long getStructCharge() { return chargeRate; }
	public long getStructDischarge() { return dischargeRate; }

	@Override
	public net.minecraft.inventory.Container provideContainer(int ID, net.minecraft.entity.player.EntityPlayer player, net.minecraft.world.World world, int x, int y, int z) {
		return new com.makentmgreatagain.container.ContainerBatteryArray(player.inventory, this);
	}

	@Override
	@cpw.mods.fml.relauncher.SideOnly(cpw.mods.fml.relauncher.Side.CLIENT)
	public Object provideGUI(int ID, net.minecraft.entity.player.EntityPlayer player, net.minecraft.world.World world, int x, int y, int z) {
		return new com.makentmgreatagain.gui.GUIBatteryArray(player.inventory, this);
	}
}