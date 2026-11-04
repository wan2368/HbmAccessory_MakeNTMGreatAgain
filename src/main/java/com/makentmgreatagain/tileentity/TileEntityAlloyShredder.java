package com.makentmgreatagain.tileentity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.hbm.inventory.recipes.ShredderRecipes;
import com.hbm.items.machine.ItemBlades;
import com.hbm.items.machine.ItemMachineUpgrade;
import com.hbm.items.machine.ItemMachineUpgrade.UpgradeType;
import com.hbm.interfaces.IControlReceiver;
import com.hbm.lib.Library;
import com.hbm.tileentity.IUpgradeInfoProvider;
import com.hbm.tileentity.TileEntityMachineBase;
import com.hbm.uninos.UniNodespace;
import com.hbm.util.fauxpointtwelve.BlockPos;
import com.hbm.util.fauxpointtwelve.DirPos;

import api.hbm.energymk2.IBatteryItem;
import api.hbm.energymk2.IEnergyConductorMK2;
import api.hbm.energymk2.IEnergyReceiverMK2;
import api.hbm.energymk2.Nodespace;
import api.hbm.energymk2.Nodespace.PowerNode;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumChatFormatting;
import net.minecraftforge.common.util.ForgeDirection;

import com.hbm.main.NTMSounds;
/**
 * 高级合金粉碎机（2×2×2）实体：45 槽。
 *
 * 槽位：0~14 输入(5×3)、15~18 刀片(4)、19~42 输出(4×6)、43 电池、44 升级(仅红色速度插件)。
 * 配方直接调用 HBM 公开静态表 ShredderRecipes.getShredderResult()。
 * 均分开关：GUI 点击 -> receiveControl 翻转 spread；spread 开启后每 20 tick 自动保持均分。
 */
public class TileEntityAlloyShredder extends TileEntityMachineBase implements IEnergyReceiverMK2, IEnergyConductorMK2, IControlReceiver, IUpgradeInfoProvider {

	public static final int SLOT_INPUT_START = 0;
	public static final int SLOT_INPUT_END = 14;
	public static final int SLOT_BLADE_START = 15;
	public static final int SLOT_BLADE_END = 18;
	public static final int SLOT_OUTPUT_START = 19;
	public static final int SLOT_OUTPUT_END = 42;
	public static final int SLOT_BATTERY = 43;
	public static final int SLOT_UPGRADE = 44;

	public long power;
	public int progress;
	public int soundCycle = 0;
	public boolean spread = false;

	public static final long maxPower = 1000000;          // 最大缓存 1M HE
	public static final long powerPerTick = 5000;         // 每 tick 耗电 5000 HE
	public static final int baseSpeed = 40;               // 基础速度：每配方 40 tick

	/** 能量网络节点（照搬电池座机制） */
	protected PowerNode node;

	public TileEntityAlloyShredder() {
		super(45);
	}

	@Override
	public String getName() {
		return "container.alloyShredder";
	}

	/* ================= 每 tick ================= */

	@Override
	public void updateEntity() {

		if(!worldObj.isRemote) {
			// ==== 能量节点维护（照搬 TileEntityBatteryBase.updateEntity 的节点部分）====
			if(this.node == null || this.node.expired) {
				this.node = (PowerNode) UniNodespace.getNode(worldObj, xCoord, yCoord, zCoord, Nodespace.THE_POWER_PROVIDER);

				if(this.node == null || this.node.expired) {
					this.node = this.createNode();
					UniNodespace.createNode(worldObj, this.node);
				}
			}

			if(this.node != null && this.node.hasValidNet()) {
				this.node.net.addReceiver(this);   // 粉碎机只受电（它不是 Provider，不能 removeProvider）
			}

			// 电池充能（槽43）
			power = Library.chargeTEFromItems(slots, SLOT_BATTERY, power, maxPower);

			if(canProcess()) {
				progress++;
				power -= getPowerPerTick();

				if(progress >= getSpeedTicks()) {
					progress = 0;

					for(int i = SLOT_BLADE_START; i <= SLOT_BLADE_END; i++) {
						if(slots[i] != null && slots[i].getMaxDamage() > 0) {
							slots[i].setItemDamage(slots[i].getItemDamage() + 1);
						}
					}

					processItem();
				}

				// ==== 运行音效（原版粉碎机同款写法）====
				// 服务端调用 World.playSoundEffect 会自动广播给附近所有玩家
				if(this.soundCycle == 0)
					this.worldObj.playSoundEffect(this.xCoord, this.yCoord, this.zCoord, NTMSounds.VANILLA_MINECART, 1.0F, 0.75F);
				this.soundCycle++;
				if(this.soundCycle >= 50) this.soundCycle = 0;
			} else {
				progress = 0;
				this.soundCycle = 0;      // 停机时归零，下次启动立刻出声
			}

			// 均分：开关开着就周期性把输入区保持均分布局（幂等，已均分时不改动）
			if(spread && worldObj.getTotalWorldTime() % 20 == 0) {
				redistribute();
			}

			networkPackNT(50);
		}
	}

	/* ================= 速度升级（槽44，仅红色SPEED插件） ================= */

	/** 槽里的速度插件等级：0=无/无效，1/2/3 */
	public int getUpgradeTier() {
		ItemStack up = slots[SLOT_UPGRADE];
		if(up != null && up.getItem() instanceof ItemMachineUpgrade) {
			ItemMachineUpgrade u = (ItemMachineUpgrade) up.getItem();
			if(u.type == UpgradeType.SPEED) return u.tier;
		}
		return 0;
	}

	/** 处理 1 个物品所需 tick：基础40；1级-25%=30；2级-33%=27；3级-50%=20 */
	public int getSpeedTicks() {
		switch(getUpgradeTier()) {
			case 1: return 30;
			case 2: return 27;
			case 3: return 20;
			default: return baseSpeed;
		}
	}

	/** 每 tick 耗电：基础5000；1级+100%=10000；2级+200%=15000；3级+300%=20000（与悬停文案一致） */
	public long getPowerPerTick() {
		return powerPerTick * (1 + getUpgradeTier());
	}

	/* ================= 加工逻辑 ================= */

	public boolean canProcess() {
		// 4 个刀片齐全且未完全损坏（状态1/2可用，0缺失、3磨满不可用）
		for(int i = SLOT_BLADE_START; i <= SLOT_BLADE_END; i++) {
			int state = getBladeState(i - SLOT_BLADE_START);
			if(state == 0 || state == 3) return false;
		}
		if(power < getPowerPerTick()) return false;   // 按 tick 耗电，必须付得起整 tick 才工作

		for(int i = SLOT_INPUT_START; i <= SLOT_INPUT_END; i++) {
			if(slots[i] != null && slots[i].stackSize > 0 && hasSpace(slots[i])) return true;
		}
		return false;
	}

	public void processItem() {
		for(int inp = SLOT_INPUT_START; inp <= SLOT_INPUT_END; inp++) {
			if(slots[inp] == null || !hasSpace(slots[inp])) continue;

			ItemStack out = ShredderRecipes.getShredderResult(slots[inp]);
			if(out == null) continue;

			boolean merged = false;
			for(int o = SLOT_OUTPUT_START; o <= SLOT_OUTPUT_END; o++) {
				if(slots[o] != null && slots[o].getItem() == out.getItem() && slots[o].getItemDamage() == out.getItemDamage()
						&& slots[o].stackSize + out.stackSize <= out.getMaxStackSize()) {
					slots[o].stackSize += out.stackSize;
					merged = true;
					break;
				}
			}
			if(!merged) {
				for(int o = SLOT_OUTPUT_START; o <= SLOT_OUTPUT_END; o++) {
					if(slots[o] == null) {
						slots[o] = out.copy();
						break;
					}
				}
			}
			slots[inp].stackSize -= 1;
			if(slots[inp].stackSize <= 0) slots[inp] = null;
		}
	}

	public boolean hasSpace(ItemStack stack) {
		ItemStack result = ShredderRecipes.getShredderResult(stack);
		if(result == null) return false;

		for(int i = SLOT_OUTPUT_START; i <= SLOT_OUTPUT_END; i++) {
			if(slots[i] == null) return true;
			if(slots[i].getItem() == result.getItem() && slots[i].getItemDamage() == result.getItemDamage()
					&& slots[i].stackSize + result.stackSize <= result.getMaxStackSize()) return true;
		}
		return false;
	}

	/* ================= 均分（15 输入格重写版） ================= */

	/**
	 * 把输入区 15 格按种类重新均分（幂等：已经均分好时结果不变）：
	 * 1) 收拢：按（物品+元数据）分组统计总量；
	 * 2) 格数分配：每种先保底 1 格，剩余格子逐格发给"单格压力(count/格数)"最大的种类
	 *    ——数量多的种类自动多拿格子，格子总数恒为 15；
	 * 3) 铺格：种类在自己的格子间平均分配（余数前几格各多 1），单格封顶堆叠上限；
	 * 4) 兜底：种类>15 或数量超出格子总容量时，先叠同类再找空格，
	 *    仍装不下则丢到机器脚下，绝不吞物品。
	 */
	public void redistribute() {

		Map<String, int[]> totals = new LinkedHashMap<String, int[]>();   // key -> [count, meta]
		Map<String, Item> items = new LinkedHashMap<String, Item>();

		for(int i = SLOT_INPUT_START; i <= SLOT_INPUT_END; i++) {
			ItemStack s = slots[i];
			if(s == null) continue;
			String key = Item.getIdFromItem(s.getItem()) + ":" + s.getItemDamage();
			int[] t = totals.get(key);
			if(t == null) { t = new int[]{0, s.getItemDamage()}; totals.put(key, t); items.put(key, s.getItem()); }
			t[0] += s.stackSize;
			slots[i] = null;
		}

		if(totals.isEmpty()) return;

		List<String> keys = new ArrayList<String>(totals.keySet());
		int n = keys.size();

		int[] counts = new int[n];
		int[] metas = new int[n];
		Item[] kinds = new Item[n];
		int[] order = new int[n];
		for(int g = 0; g < n; g++) {
			int[] t = totals.get(keys.get(g));
			counts[g] = t[0];
			metas[g] = t[1];
			kinds[g] = items.get(keys.get(g));
			order[g] = g;
		}

		// 按数量降序（插入排序，数量相同保持先出现顺序；Java7 下无匿名类捕获问题）
		for(int g = 1; g < n; g++) {
			for(int h = g; h > 0 && counts[order[h]] > counts[order[h - 1]]; h--) {
				int tmp = order[h]; order[h] = order[h - 1]; order[h - 1] = tmp;
			}
		}

		int groups = Math.min(n, 15);

		// 格数分配：保底 1 格 + 贪心逐格补
		int[] cells = new int[groups];
		for(int g = 0; g < groups; g++) cells[g] = 1;
		for(int c = groups; c < 15; c++) {
			int best = 0;
			double bestLoad = -1.0;
			for(int g = 0; g < groups; g++) {
				double load = (double) counts[order[g]] / (double) cells[g];
				if(load > bestLoad) { bestLoad = load; best = g; }
			}
			cells[best]++;
		}

		// 铺格
		List<int[]> leftovers = new ArrayList<int[]>();   // [种类索引, 没放下的数量]
		int idx = 0;
		for(int g = 0; g < groups; g++) {
			int oi = order[g];
			int count = counts[oi];
			int meta = metas[oi];
			Item it = kinds[oi];
			int limit = it.getItemStackLimit(new ItemStack(it, 1, meta));
			if(limit <= 0) limit = 64;

			int per = count / cells[g];
			int extra = count % cells[g];

			for(int j = 0; j < cells[g]; j++) {
				int num = per + (j < extra ? 1 : 0);
				while(num > 0 && idx < 15) {
					int put = Math.min(num, limit);
					slots[SLOT_INPUT_START + idx] = new ItemStack(it, put, meta);
					idx++;
					num -= put;
				}
				if(num > 0) leftovers.add(new int[]{ oi, num });
			}
		}

		// 种类数超过 15 的滞留组，整体进兜底
		for(int g = groups; g < n; g++) {
			leftovers.add(new int[]{ order[g], counts[order[g]] });
		}

		// 兜底：先叠同类，再找空格，仍剩则丢地上
		for(int[] lo : leftovers) {
			int oi = lo[0];
			int remaining = putBack(kinds[oi], metas[oi], lo[1]);
			if(remaining > 0) dropOverflow(kinds[oi], metas[oi], remaining);
		}
	}

	/** 把 (item, meta, count) 塞回输入区：先叠加同类未满格，再放空格；返回剩余数量 */
	private int putBack(Item it, int meta, int count) {
		for(int i = SLOT_INPUT_START; i <= SLOT_INPUT_END && count > 0; i++) {
			ItemStack s = slots[i];
			if(s != null && s.getItem() == it && s.getItemDamage() == meta && s.stackSize < s.getMaxStackSize()) {
				int add = Math.min(count, s.getMaxStackSize() - s.stackSize);
				s.stackSize += add;
				count -= add;
			}
		}
		for(int i = SLOT_INPUT_START; i <= SLOT_INPUT_END && count > 0; i++) {
			if(slots[i] == null) {
				int limit = it.getItemStackLimit(new ItemStack(it, 1, meta));
				if(limit <= 0) limit = 64;
				int put = Math.min(count, limit);
				slots[i] = new ItemStack(it, put, meta);
				count -= put;
			}
		}
		return count;
	}

	/** 均分兜底：15 格实在装不下时丢到机器脚下，绝不吞玩家物品 */
	private void dropOverflow(Item it, int meta, int count) {
		ItemStack stack = new ItemStack(it, count, meta);
		net.minecraft.entity.item.EntityItem ent = new net.minecraft.entity.item.EntityItem(
				worldObj, xCoord + 0.5, yCoord + 1.5, zCoord + 0.5, stack);
		worldObj.spawnEntityInWorld(ent);
	}

	/* ================= 槽位准入与自动化 ================= */

	@Override
	public boolean isItemValidForSlot(int i, ItemStack stack) {
		if(stack == null) return false;
		if(i >= SLOT_INPUT_START && i <= SLOT_INPUT_END) {
			return ShredderRecipes.getShredderResult(stack) != null && !(stack.getItem() instanceof ItemBlades);
		}
		if(i >= SLOT_BLADE_START && i <= SLOT_BLADE_END) return stack.getItem() instanceof ItemBlades;
		if(i == SLOT_BATTERY) return stack.getItem() instanceof IBatteryItem;
		if(i == SLOT_UPGRADE) {
			if(stack.getItem() instanceof ItemMachineUpgrade) {
				ItemMachineUpgrade u = (ItemMachineUpgrade) stack.getItem();
				return u.type == UpgradeType.SPEED && u.tier >= 1 && u.tier <= 3;   // 只收红色速度插件
			}
			return false;
		}
		return false;   // 输出槽 19~42 任何形式都不可放入
	}

	private static final int[] SLOTS_AUTO = new int[] {
			0,1,2,3,4,5,6,7,8,9,10,11,12,13,14,
			19,20,21,22,23,24,25,26,27,28,29,30,31,32,33,34,35,36,37,38,39,40,41,42
	};

	@Override
	public int[] getAccessibleSlotsFromSide(int side) {
		return SLOTS_AUTO;
	}

	@Override
	public boolean canInsertItem(int slot, ItemStack stack, int side) {
		return isItemValidForSlot(slot, stack);
	}

	@Override
	public boolean canExtractItem(int slot, ItemStack stack, int side) {
		return slot >= SLOT_OUTPUT_START && slot <= SLOT_OUTPUT_END;
	}

	/* ================= 刀片状态 ================= */

	/** 0 无刀片/全坏，1 良好，2 半磨损，3 将坏 */
	public int getBladeState(int bladeIndex) {
		ItemStack blade = slots[SLOT_BLADE_START + bladeIndex];
		if(blade == null || !(blade.getItem() instanceof ItemBlades)) return 0;
		if(blade.getMaxDamage() == 0) return 1;
		if(blade.getItemDamage() < blade.getMaxDamage() / 2) return 1;
		if(blade.getItemDamage() < blade.getMaxDamage()) return 2;
		return 3;
	}

	/* ================= 能量接口 ================= */

	@Override public void setPower(long i) { this.power = i; }
	@Override public long getPower() { return this.power; }
	@Override public long getMaxPower() { return maxPower; }

	public long getPowerScaled(long i) { return (power * i) / maxPower; }
	public int getProgressScaled(int i) { return (progress * i) / getSpeedTicks(); }

	/**
	 * 能量节点定义（照搬电池座）：端口=下层4格（核心+其后2格+右1格），
	 * 接线位=四侧外面各2格，共8个，与方块类 getAllPorts 的 8 个黄点一一对应。
	 */
	@Override
	public PowerNode createNode() {
		return new PowerNode(getPortPos()).setConnections(getConPos());
	}

	public BlockPos[] getPortPos() {
		ForgeDirection dir = ForgeDirection.getOrientation(worldObj.getBlockMetadata(xCoord, yCoord, zCoord) - 10);
		ForgeDirection rot = dir.getRotation(ForgeDirection.UP);
		return new BlockPos[] {
				new BlockPos(xCoord, yCoord, zCoord),
				new BlockPos(xCoord - dir.offsetX, yCoord, zCoord - dir.offsetZ),
				new BlockPos(xCoord + rot.offsetX, yCoord, zCoord + rot.offsetZ),
				new BlockPos(xCoord - dir.offsetX + rot.offsetX, yCoord, zCoord - dir.offsetZ + rot.offsetZ)
		};
	}

	public DirPos[] getConPos() {
		ForgeDirection dir = ForgeDirection.getOrientation(worldObj.getBlockMetadata(xCoord, yCoord, zCoord) - 10);
		ForgeDirection rot = dir.getRotation(ForgeDirection.UP);
		return new DirPos[] {
				new DirPos(xCoord + dir.offsetX, yCoord, zCoord + dir.offsetZ, dir),
				new DirPos(xCoord + dir.offsetX + rot.offsetX, yCoord, zCoord + dir.offsetZ + rot.offsetZ, dir),

				new DirPos(xCoord - dir.offsetX * 2, yCoord, zCoord - dir.offsetZ * 2, dir.getOpposite()),
				new DirPos(xCoord - dir.offsetX * 2 + rot.offsetX, yCoord, zCoord - dir.offsetZ * 2 + rot.offsetZ, dir.getOpposite()),

				new DirPos(xCoord + rot.offsetX * 2, yCoord, zCoord + rot.offsetZ * 2, rot),
				new DirPos(xCoord + rot.offsetX * 2 - dir.offsetX, yCoord, zCoord + rot.offsetZ * 2 - dir.offsetZ, rot),

				new DirPos(xCoord - rot.offsetX, yCoord, zCoord - rot.offsetZ, rot.getOpposite()),
				new DirPos(xCoord - rot.offsetX - dir.offsetX, yCoord, zCoord - rot.offsetZ - dir.offsetZ, rot.getOpposite())
		};
	}

	@Override
	public void invalidate() {
		super.invalidate();
		if(!worldObj.isRemote) {
			UniNodespace.destroyNode(worldObj, xCoord, yCoord, zCoord, Nodespace.THE_POWER_PROVIDER);
		}
	}

	/* ================= 升级信息悬停（物品提示框，与电弧焊机同款样式） ================= */

	@Override
	public boolean canProvideInfo(UpgradeType type, int level, boolean extendedInfo) {
		return type == UpgradeType.SPEED;
	}

	@Override
	public void provideInfo(UpgradeType type, int level, List<String> info, boolean extendedInfo) {
		if(type != UpgradeType.SPEED) return;
		info.add(IUpgradeInfoProvider.getStandardLabel(com.makentmgreatagain.init.ModBlocks.block_alloy_shredder));
		switch(level) {
			case 1: info.add(EnumChatFormatting.GREEN + "工作时间 -25%"); info.add(EnumChatFormatting.RED + "消耗 +100%"); break;
			case 2: info.add(EnumChatFormatting.GREEN + "工作时间 -33%"); info.add(EnumChatFormatting.RED + "消耗 +200%"); break;
			case 3: info.add(EnumChatFormatting.GREEN + "工作时间 -50%"); info.add(EnumChatFormatting.RED + "消耗 +300%"); break;
			default: info.add(EnumChatFormatting.GREEN + "工作时间 -25%"); info.add(EnumChatFormatting.RED + "消耗 +100%"); break;
		}
	}

	@Override
	public HashMap<UpgradeType, Integer> getValidUpgrades() {
		HashMap<UpgradeType, Integer> map = new HashMap<UpgradeType, Integer>();
		map.put(UpgradeType.SPEED, 3);
		return map;
	}

	/* ================= GUI 控制（均分开关） ================= */

	@Override
	public boolean hasPermission(EntityPlayer player) {
		return isUseableByPlayer(player);
	}

	@Override
	public void receiveControl(EntityPlayer player, NBTTagCompound data) {
		if(data.hasKey("spread")) {
			spread = !spread;
			if(spread) redistribute();
			else compactInput();          // 关闭均分：把输入区同类堆叠到上限
			markDirty();
		}
	}

	/**
	 * 均分的逆操作：输入区 15 格里的同类物品（物品+元数据相同）合并堆叠，
	 * 每格堆到该物品自己的堆叠上限，前面的格优先装满，空出的格留空。
	 * 物品总量不变，绝不丢失。
	 */
	public void compactInput() {
		Map<String, int[]> totals = new LinkedHashMap<String, int[]>();
		Map<String, Item> items = new LinkedHashMap<String, Item>();

		for(int i = SLOT_INPUT_START; i <= SLOT_INPUT_END; i++) {
			ItemStack s = slots[i];
			if(s == null) continue;
			String key = Item.getIdFromItem(s.getItem()) + ":" + s.getItemDamage();
			int[] t = totals.get(key);
			if(t == null) { t = new int[]{0, s.getItemDamage()}; totals.put(key, t); items.put(key, s.getItem()); }
			t[0] += s.stackSize;
			slots[i] = null;
		}

		int idx = SLOT_INPUT_START;
		for(Map.Entry<String, int[]> e : totals.entrySet()) {
			Item it = items.get(e.getKey());
			int meta = e.getValue()[1];
			int count = e.getValue()[0];
			int limit = it.getItemStackLimit(new ItemStack(it, 1, meta));
			if(limit <= 0) limit = 64;

			while(count > 0 && idx <= SLOT_INPUT_END) {
				int put = Math.min(count, limit);
				slots[idx++] = new ItemStack(it, put, meta);
				count -= put;
			}
			// 理论上不会走到这（合并后格数只会变少），保险：装不下的丢地上
			if(count > 0) dropOverflow(it, meta, count);
		}
	}

	/* ================= NBT 持久化 ================= */

	@Override
	public void readFromNBT(NBTTagCompound nbt) {
		super.readFromNBT(nbt);
		power = nbt.getLong("powerTime");
		spread = nbt.getBoolean("spread");
	}

	@Override
	public void writeToNBT(NBTTagCompound nbt) {
		super.writeToNBT(nbt);
		nbt.setLong("powerTime", power);
		nbt.setBoolean("spread", spread);
	}

	/* ================= 网络同步（GUI 动画用） ================= */

	@Override
	public void serialize(io.netty.buffer.ByteBuf buf) {
		super.serialize(buf);
		buf.writeLong(power);
		buf.writeInt(progress);
		buf.writeBoolean(spread);
	}

	@Override
	public void deserialize(io.netty.buffer.ByteBuf buf) {
		super.deserialize(buf);
		power = buf.readLong();
		progress = buf.readInt();
		spread = buf.readBoolean();
	}
}
