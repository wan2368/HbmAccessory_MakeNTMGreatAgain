package com.makentmgreatagain.tileentity;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.hbm.inventory.FluidStack;
import com.hbm.inventory.fluid.FluidType;
import com.hbm.inventory.fluid.Fluids;
import com.hbm.inventory.fluid.tank.FluidTank;
import com.hbm.inventory.recipes.CrackingRecipes;
import com.hbm.inventory.recipes.FractionRecipes;
import com.hbm.inventory.recipes.RadiolysisRecipes;
import com.hbm.items.machine.IItemFluidIdentifier;
import com.hbm.lib.Library;
import com.hbm.tileentity.IBufPacketReceiver;
import com.hbm.tileentity.TileEntityMachineBase;
import com.hbm.tileentity.TilePort.PortDef;
import com.hbm.util.Tuple.Pair;
import com.makentmgreatagain.recipes.HybridFractionRecipes;

import api.hbm.fluidmk2.IFluidStandardTransceiverMK2;
import io.netty.buffer.ByteBuf;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;

/**
 * 混合催化分馏塔的 TileEntity。
 *
 * <p>储罐：6 个，每个 24,000 mB。0/1 是进料，2~5 是出料，
 * 顺序与界面右侧四根流体条一一对应。</p>
 *
 * <p>配方：先查 {@link HybridFractionRecipes}（双流体进 → 4 流体 + 1 物品），
 * 查不到再退回原版 {@link FractionRecipes}（1 进 2 出）。</p>
 *
 * <h3>堆叠</h3>
 * <p>上下叠一样的塔时，整列当成一台机器：</p>
 * <ul>
 *   <li>只有最底层能改流体类型，上方各段每 tick 强制继承下方那段的罐型；</li>
 *   <li>进料由最底层收集后<b>平均分配</b>给整列，叠 N 台就每台 1/N；</li>
 *   <li>各段加工完的产物统一往下压，最终汇总在最底层排出。</li>
 * </ul>
 *
 * <h3>槽位编号</h3>
 * <pre>
 *  0  识别码 1        1  识别码 2
 *  2  进料桶 1        3  空桶出 1
 *  4  进料桶 2        5  空桶出 2
 *  6  催化剂          7  物品输出
 *  8  出料桶进 1      9  满桶出 1
 * 10  出料桶进 2     11  满桶出 2
 * 12  出料桶进 3     13  满桶出 3
 * 14  出料桶进 4     15  满桶出 4
 * </pre>
 */
public class TileEntityHybridCatalyticFractionTower extends TileEntityMachineBase
		implements IBufPacketReceiver, IFluidStandardTransceiverMK2 {

	/** 结构总高（格），叠放间隔也是它 */
	public static final int HEIGHT = 6;
	/** 每个储罐的容量 */
	public static final int CAPACITY = 24_000;

	/** 原版分馏兜底：整批处理量（mB） */
	public static final int PROCESS_BATCH = 1_000;
	/** 原版分馏兜底：不足整批时的退档处理量（mB） */
	public static final int PROCESS_SMALL = 100;

	/** 0/1 进料，2~5 出料 */
	public FluidTank[] tanks = new FluidTank[6];

	/** 催化剂类型（同步到客户端选界面贴图）：0 无 / 1 原版 / 2 铂 / 3 CMB */
	public int catalystType;

	protected PortDef[] cachedPorts;

	public TileEntityHybridCatalyticFractionTower() {
		super(16);

		for(int i = 0; i < tanks.length; i++) {
			this.tanks[i] = new FluidTank(Fluids.NONE, CAPACITY);
		}
	}

	@Override
	public String getName() {
		return "container.hybrid_catalytic_fraction_tower";
	}

	@Override
	public boolean isItemValidForSlot(int slot, ItemStack stack) {

		if(stack == null) return false;

		switch(slot) {
		case 0:
		case 1: return stack.getItem() instanceof IItemFluidIdentifier;
		case 6: return HybridFractionRecipes.catalystTier(stack) > 0;
		case 3:
		case 5:
		case 7:
		case 9:
		case 11:
		case 13:
		case 15: return false;
		// 2/4 进料桶、8/10/12/14 出料空桶：只收能被本机罐装卸的容器，不再 default 放行一切。
		// 进料槽收「装着本机两路输入流体的满容器」，出料槽收「能被四路输出流体灌满的空容器」
		default: return acceptsBucketFor(slot, stack);
		}
	}

	private boolean acceptsBucketFor(int slot, ItemStack stack) {
		if(stack.getItem() instanceof api.hbm.fluidmk2.IFillableItem) return true;
		if(stack.getItem() == net.minecraft.init.Items.bucket) return true;
		if(slot == 2 || slot == 4) {
			FluidType in = tanks[slot == 2 ? 0 : 1].getTankType();
			// 罐型未定（NONE）时先放行，等识别码/首桶定型后自然收紧
			if(in == null || in == Fluids.NONE) return true;
			return com.hbm.inventory.FluidContainerRegistry.getFluidContent(stack, in) > 0;
		}
		for(int t = 2; t <= 5; t++) {
			FluidType out = tanks[t].getTankType();
			if(out != null && com.hbm.inventory.FluidContainerRegistry.getFullContainer(stack, out) != null) return true;
		}
		return false;
	}

	public PortDef[] getPorts() {
		if(cachedPorts == null) {
			cachedPorts = new PortDef[] {
				PortDef.make(xCoord - 1, yCoord, zCoord - 2, Library.NEG_Z),
				PortDef.make(xCoord + 1, yCoord, zCoord - 2, Library.NEG_Z),
				PortDef.make(xCoord - 1, yCoord, zCoord + 2, Library.POS_Z),
				PortDef.make(xCoord + 1, yCoord, zCoord + 2, Library.POS_Z),
				PortDef.make(xCoord + 2, yCoord, zCoord - 1, Library.POS_X),
				PortDef.make(xCoord + 2, yCoord, zCoord + 1, Library.POS_X),
				PortDef.make(xCoord - 2, yCoord, zCoord - 1, Library.NEG_X),
				PortDef.make(xCoord - 2, yCoord, zCoord + 1, Library.NEG_X)
			};
		}
		return cachedPorts;
	}

	/** 本段下面没有同型机器，就是整列的最底层 */
	private boolean isBottom() {
		return !(worldObj.getTileEntity(xCoord, yCoord - HEIGHT, zCoord) instanceof TileEntityHybridCatalyticFractionTower);
	}

	@Override
	public void updateEntity() {

		if(worldObj.isRemote) return;

		boolean bottom = isBottom();

		// 只有最底层能用识别码改罐型，上方各段一律继承下方
		if(bottom) {
			applyIdentifiers();
		}

		// 桶进桶出
		tanks[0].loadTank(2, 3, slots);
		tanks[1].loadTank(4, 5, slots);
		tanks[2].unloadTank(8, 9, slots);
		tanks[3].unloadTank(10, 11, slots);
		tanks[4].unloadTank(12, 13, slots);
		tanks[5].unloadTank(14, 15, slots);

		this.setupFluidPorts(getPorts());
		this.updatePortFIFO();

		// 罐型向下继承：必须先同步罐型，再分配进料，
		// 否则会拿上方的旧罐型去接下方的流体，出现「输出流体进到输入罐」的串味
		TileEntity below = worldObj.getTileEntity(xCoord, yCoord - HEIGHT, zCoord);
		if(below instanceof TileEntityHybridCatalyticFractionTower) {
			TileEntityHybridCatalyticFractionTower lower = (TileEntityHybridCatalyticFractionTower) below;
			for(int i = 0; i < tanks.length; i++) {
				if(tanks[i].getTankType() == lower.tanks[i].getTankType()) continue;
				tanks[i].setTankType(lower.tanks[i].getTankType());
			}
		}

		// 进料均分：最底层把整列的进料摊平，叠 N 台每台 1/N
		if(bottom) distributeInput();

		refreshOutputSlots();

		if(worldObj.getTotalWorldTime() % 10 == 0) craft();

		// 产物统一往下压，整列只在最底层出料
		pushOutputsDown();

		this.catalystType = resolveCatalystType();

		networkPackNT(50);
	}

	/** 8 个接口全部对外暴露，物流管道从任意一面都能接 */
	private static final int[] ACCESSIBLE = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15};

	@Override
	public int[] getAccessibleSlotsFromSide(int side) {
		return ACCESSIBLE;
	}

	/** 物流只能往输入类槽位塞：识别码、进料桶、催化剂、出料空桶 */
	@Override
	public boolean canInsertItem(int slot, ItemStack stack, int side) {

		switch(slot) {
		case 0:
		case 1:
		case 2:
		case 4:
		case 6:
		case 8:
		case 10:
		case 12:
		case 14:
			return isItemValidForSlot(slot, stack);
		default:
			return false;
		}
	}

	/** 物流只能从输出槽往外抽 */
	@Override
	public boolean canExtractItem(int slot, ItemStack stack, int side) {

		switch(slot) {
		case 3:
		case 5:
		case 7:
		case 9:
		case 11:
		case 13:
		case 15:
			return true;
		default:
			return false;
		}
	}

	/** 把整列的两个进料罐都摊平（0 号主进料、1 号第二进料） */
	private void distributeInput() {

		List<TileEntityHybridCatalyticFractionTower> column = new ArrayList<TileEntityHybridCatalyticFractionTower>();

		for(int dy = 0; yCoord + dy < worldObj.getHeight(); dy += HEIGHT) {
			TileEntity te = worldObj.getTileEntity(xCoord, yCoord + dy, zCoord);
			if(!(te instanceof TileEntityHybridCatalyticFractionTower)) break;
			column.add((TileEntityHybridCatalyticFractionTower) te);
		}

		int n = column.size();
		if(n <= 1) return;

		for(int tank = 0; tank < 2; tank++) {

			FluidType type = tanks[tank].getTankType();

			// 只摊平「罐型和最底层一致」的段。罐型不同的段一律不参与，
			// 否则会把下方的流体写进上方写着别的类型的罐里
			List<TileEntityHybridCatalyticFractionTower> group = new ArrayList<TileEntityHybridCatalyticFractionTower>();
			int total = 0;

			for(int i = 0; i < n; i++) {
				TileEntityHybridCatalyticFractionTower seg = column.get(i);
				if(seg.tanks[tank].getTankType() != type) continue;
				group.add(seg);
				total += seg.tanks[tank].getFill();
			}

			if(group.isEmpty()) continue;

			int each = total / group.size();
			int rest = total - each * group.size();

			for(int i = 0; i < group.size(); i++) {
				group.get(i).tanks[tank].setFill(i == 0 ? each + rest : each);
			}
		}
	}

	/** 把本段的产物往下一段压 */
	private void pushOutputsDown() {

		TileEntity below = worldObj.getTileEntity(xCoord, yCoord - HEIGHT, zCoord);
		if(!(below instanceof TileEntityHybridCatalyticFractionTower)) return;

		TileEntityHybridCatalyticFractionTower lower = (TileEntityHybridCatalyticFractionTower) below;

		for(int i = 2; i < tanks.length; i++) {
			int move = Math.min(tanks[i].getFill(), lower.tanks[i].getMaxFill() - lower.tanks[i].getFill());
			if(move <= 0) continue;
			tanks[i].setFill(tanks[i].getFill() - move);
			lower.tanks[i].setFill(lower.tanks[i].getFill() + move);
		}

		// 7 号槽的物品产物也往下压，最终汇总在最底层排出（与类注释的堆叠规则一致）
		ItemStack item = slots[7];
		if(item != null) {
			ItemStack lowerItem = lower.slots[7];
			if(lowerItem == null) {
				lower.slots[7] = item;
				slots[7] = null;
			} else if(lowerItem.getItem() == item.getItem()
					&& lowerItem.getItemDamage() == item.getItemDamage()
					&& ItemStack.areItemStackTagsEqual(lowerItem, item)) {
				int limit = Math.min(lowerItem.getMaxStackSize(), getInventoryStackLimit());
				int move = Math.min(limit - lowerItem.stackSize, item.stackSize);
				if(move > 0) {
					lowerItem.stackSize += move;
					item.stackSize -= move;
					if(item.stackSize <= 0) slots[7] = null;
				}
			}
			lower.markDirty();
		}
	}

	/**
	 * 应用两个识别码槽的罐型。
	 *
	 * <p>两路进料的<b>目标类型一起算、一起校验</b>，判据是「这台机器现在容不容得下两路产物」，
	 * 不是「识别码放在 0 号还是 1 号槽」，也不是「谁是先放的那一路」。</p>
	 *
	 * <ol>
	 *   <li>先把两路的目标类型合起来，看它们需要的出料罐数量，加上出料罐里
	 *       <b>还存着、且新组合用不到</b>的旧产物罐，是否仍然不超过 4 格；</li>
	 *   <li>装不下就<b>整个放弃这次变更</b>：罐型保持原样、识别码留在槽里不生效。
	 *       等出料罐被抽空，下一 tick 会自动重试，所以只是延后，不会永久卡死；</li>
	 *   <li>能装下才写入，而且只写真正变了的那个罐 —— {@code setTankType} 遇到同类型会直接
	 *       早返回，所以已经跑起来的那一路，罐里的进料不会被清空。</li>
	 * </ol>
	 *
	 * <p>这条规则要解决的就是「先开一路、产出成品之后再放第二路」的场景：
	 * 第二路塞不下时只会被<b>拒绝</b>，绝不会把已经存好的产物清掉。</p>
	 */
	private void applyIdentifiers() {

		// 槽里有识别码就用识别码；没有就保持当前罐型（拿走识别码不会把罐清空）
		FluidType want0 = identifierOr(0);
		FluidType want1 = identifierOr(1);

		if(want0 == tanks[0].getTankType() && want1 == tanks[1].getTankType()) return;

		if(!canAccommodate(want0, want1)) return;

		tanks[0].setTankType(want0);
		tanks[1].setTankType(want1);
	}

	/** 读识别码；槽里没有识别码时返回罐的当前类型，表示「这一路不变」 */
	private FluidType identifierOr(int slot) {

		FluidType type = readIdentifier(slot);

		return type != null ? type : tanks[slot].getTankType();
	}

	/** 读槽里的识别码类型，不是识别码返回 null */
	private FluidType readIdentifier(int slot) {

		ItemStack stack = slots[slot];

		if(stack == null || !(stack.getItem() instanceof IItemFluidIdentifier)) return null;

		return ((IItemFluidIdentifier) stack.getItem()).getType(worldObj, xCoord, yCoord, zCoord, stack);
	}

	/**
	 * 两路进料换成 {@code typeA} / {@code typeB} 之后，产物还塞得进 4 个出料罐吗。
	 *
	 * <p>判据：新组合需要的产物种类数 + 出料罐里「有存货、且新组合用不到」的罐数 ≤ 4。
	 * 后半截不能省 —— 那些罐里的液体还没被抽走，一旦换型 {@code setFill(0)} 就蒸发了。</p>
	 */
	private boolean canAccommodate(FluidType typeA, FluidType typeB) {

		Set<FluidType> needed = expectedOutputTypes(typeA, typeB);

		int stuck = 0;

		for(int i = 0; i < 4; i++) {
			FluidType type = tanks[2 + i].getTankType();
			if(type == Fluids.NONE || needed.contains(type)) continue;
			if(tanks[2 + i].getFill() > 0) stuck++;
		}

		return needed.size() + stuck <= 4;
	}

	/**
	 * 两路进料会产出哪些流体（只看种类，不看数量与落位）。
	 *
	 * <p>命中自定义配方就按它的 4 路出料算，否则按原版分馏 / 裂化的一进两出算。</p>
	 */
	private Set<FluidType> expectedOutputTypes(FluidType typeA, FluidType typeB) {

		Set<FluidType> set = new LinkedHashSet<FluidType>();

		// 必须带上当前催化剂槽：自定义配方几乎都要求铂 / CMB，不传催化剂会让 get() 一律返回 null，
		// 预校验就退化成按原版分馏算，出现「识别码放得进去、但产物其实塞不下」的误判。
		// 压力传 0 与本机进料罐的实际压力一致（罐压恒 0，见 HybridFractionContent 类注释）。
		HybridFractionRecipes.Recipe recipe = HybridFractionRecipes.get(typeA, 0, typeB, 0, slots[6]);

		if(recipe != null) {
			for(int i = 0; i < 4; i++) {
				if(recipe.out[i] != null) addType(set, recipe.out[i].type);
			}
			return set;
		}

		addFractionTypes(set, typeA);
		addFractionTypes(set, typeB);

		return set;
	}

	/** 把一路进料的馏分并进集合 */
	private void addFractionTypes(Set<FluidType> set, FluidType input) {

		Pair<FluidStack, FluidStack> split = splitOf(input);
		if(split == null) return;

		addType(set, split.getKey().type);
		addType(set, split.getValue().type);
	}

	private static void addType(Set<FluidType> set, FluidType type) {
		if(type != null && type != Fluids.NONE) set.add(type);
	}

	/** 把一种进料的两路馏分并进集合，放不下返回 false */
	private boolean mergeFractionOutputs(Map<FluidType, Integer> merged, FluidType input) {

		Pair<FluidStack, FluidStack> quart = splitOf(input);
		if(quart == null) return true;

		if(!mergeOutput(merged, quart.getKey().type, quart.getKey().fill)) return false;
		if(!mergeOutput(merged, quart.getValue().type, quart.getValue().fill)) return false;

		// 本机即便套用催化裂化塔的配方也不产低压蒸汽，所以这里只有两路产物（见 tryFraction 注释）
		return true;
	}

	/**
	 * 刷新 2~5 号出料罐的罐型。<b>只做增量，绝不重排。</b>
	 *
	 * <p>{@code FluidTank#setTankType} 换型时会 {@code setFill(0)} ——
	 * 只要因为「产量从大到小重新排序」给罐换了型，里面存着的产物就全没了。
	 * 以前每 tick 都按产量重排一次，放第二个识别码时产物被清空正是这么来的。
	 * 现在改成三条规则：</p>
	 *
	 * <ol>
	 *   <li>已经有罐型的产物<b>永远留在原罐</b>，产量怎么变都不换位置；</li>
	 *   <li>新出现的产物找一个当前为 NONE 的空罐落位；暂时没有空罐就先不落位，
	 *       那一路进料在 {@code craft()} 里会因为找不到出料罐而跳过，不会白吃料；</li>
	 *   <li>不再产出的罐型要等它<b>排空</b>（fill == 0）才归还成 NONE，
	 *       避免抽到一半时把剩下的产物蒸发掉。</li>
	 * </ol>
	 */
	private void refreshOutputSlots() {

		Map<FluidType, Integer> expected = expectedOutputAmounts();

		// 1) 归还：已经不再产出、而且已经排空的罐
		for(int i = 0; i < 4; i++) {
			FluidType type = tanks[2 + i].getTankType();
			if(type == Fluids.NONE || expected.containsKey(type)) continue;
			if(tanks[2 + i].getFill() > 0) continue;
			tanks[2 + i].setTankType(Fluids.NONE);
		}

		// 2) 落位：还没有出料罐的产物，按出现顺序找空罐
		for(FluidType type : expected.keySet()) {
			if(outputIndexFor(type) >= 0) continue;
			int free = freeOutputSlot();
			if(free < 0) continue;
			tanks[2 + free].setTankType(type);
		}
	}

	/**
	 * 当前进料会产出的全部流体及其单周期产量。
	 *
	 * <p>第二路进料是<b>全有或全无</b>：它的产物只要把种类数顶到 5 种以上，
	 * 这一路就整体不参与 —— 与 {@code craft()} 的判断口径保持一致。</p>
	 */
	private Map<FluidType, Integer> expectedOutputAmounts() {

		Map<FluidType, Integer> merged = new LinkedHashMap<FluidType, Integer>();

		HybridFractionRecipes.Recipe recipe = findRecipe();

		if(recipe != null) {

			for(int i = 0; i < 4; i++) {
				if(recipe.out[i] == null) continue;
				mergeOutput(merged, recipe.out[i].type, recipe.out[i].fill);
			}

			return merged;
		}

		// 0 号进料先占位，1 号进料要么全进、要么一个都不进
		if(!mergeFractionOutputs(merged, tanks[0].getTankType())) {
			merged.clear();
		}

		Map<FluidType, Integer> probe = new LinkedHashMap<FluidType, Integer>(merged);
		if(mergeFractionOutputs(probe, tanks[1].getTankType())) {
			merged = probe;
		}

		return merged;
	}

	/** 第一个没有罐型的出料罐下标；四个都占着返回 -1 */
	private int freeOutputSlot() {

		for(int i = 0; i < 4; i++) {
			if(tanks[2 + i].getTankType() == Fluids.NONE) return i;
		}

		return -1;
	}

	/**
	 * 同种流体累加，新种类最多占 4 个格子。
	 *
	 * @return 是否装得下（超出 4 种返回 false，且不做任何修改）
	 */
	private boolean mergeOutput(Map<FluidType, Integer> merged, FluidType type, int amount) {

		if(type == null || type == Fluids.NONE) return true;

		Integer existing = merged.get(type);

		if(existing != null) {
			merged.put(type, existing + amount);
			return true;
		}

		if(merged.size() >= 4) return false;

		merged.put(type, amount);
		return true;
	}

	/** 某种产物落在哪个出料罐；没有则 -1 */
	private int outputIndexFor(FluidType type) {

		for(int i = 0; i < 4; i++) {
			if(tanks[2 + i].getTankType() == type) return i;
		}

		return -1;
	}

	private HybridFractionRecipes.Recipe findRecipe() {
		return HybridFractionRecipes.get(
				tanks[0].getTankType(), tanks[0].getPressure(),
				tanks[1].getTankType(), tanks[1].getPressure(),
				slots[6]);
	}

	/** 催化剂槽里有没有可用的催化剂（原版催化转换器 / 铂 / CMB） */
	public boolean hasCatalyst() {
		return resolveCatalystType() != 0;
	}

	/**
	 * 催化剂槽里的等级：0 无 / 1 原版催化转换器 / 2 铂 / 3 CMB。
	 * 客户端据此切换四张界面底图；等级高的能顶替等级低的配方。
	 */
	public int resolveCatalystType() {
		return HybridFractionRecipes.catalystTier(slots[6]);
	}

	/**
	 * 立刻重算并广播催化剂类型。
	 *
	 * <p>由容器在检测到 6 号槽变化时调用，这样换催化剂后客户端下一 tick 就能换底图，
	 * 不用等 updateEntity 的下一轮。</p>
	 */
	public void syncCatalystType() {

		int type = resolveCatalystType();
		if(type == catalystType) return;

		this.catalystType = type;
		networkPackNT(50);
	}

	/**
	 * 查一种进料的「一进两出」配方。
	 *
	 * <p>放了催化转换器就先查催化裂化塔的 {@link CrackingRecipes}，
	 * 没有对应裂化配方（或没放催化剂）才退回原版分馏 {@link FractionRecipes}。</p>
	 *
	 * <p><b>注意</b>：套用裂化配方时本机<b>不</b>吃蒸汽、也<b>不</b>副产低压蒸汽——
	 * 裂化塔那边是「进料 + 蒸汽 → 馏分 + 低压蒸汽」，这里只有进料和两路馏分。
	 * 只借用它的产物表，不借用它的蒸汽账。</p>
	 */
	private Pair<FluidStack, FluidStack> splitOf(FluidType type) {

		// CMB 催化剂优先走辐射裂解
		if(resolveCatalystType() >= 3) {
			Pair<FluidStack, FluidStack> radiolysis = RadiolysisRecipes.getRadiolysis(type);
			if(radiolysis != null) return radiolysis;
		}

		if(hasCatalyst()) {
			Pair<FluidStack, FluidStack> cracking = CrackingRecipes.getCracking(type);
			if(cracking != null) return cracking;
		}

		return FractionRecipes.getFractions(type);
	}

	/** 每 10 tick 加工一次；任何一个出料口满了就整机停机 */
	private void craft() {

		if(isOutputFull()) return;

		HybridFractionRecipes.Recipe recipe = findRecipe();

		if(recipe != null) {
			craftCustom(recipe);
			return;
		}

		craftFraction();
	}

	/**
	 * 出料口是否已满。
	 *
	 * <p>四个出料罐里只要有一个（罐型不是 NONE）装满，或者 7 号物品输出槽堆到上限，
	 * 整台机器就停止加工，避免产物被吞。未启用的出料罐视为未满。</p>
	 */
	private boolean isOutputFull() {

		for(int i = 2; i < tanks.length; i++) {
			if(tanks[i].getTankType() == Fluids.NONE) continue;
			if(tanks[i].getFill() >= tanks[i].getMaxFill()) return true;
		}

		ItemStack out = slots[7];

		if(out != null) {
			int max = Math.min(out.getMaxStackSize(), getInventoryStackLimit());
			if(out.stackSize >= max) return true;
		}

		return false;
	}

	/** 自定义配方：双流体进，4 流体 + 1 物品出 */
	private void craftCustom(HybridFractionRecipes.Recipe recipe) {

		// 把两路需求映射到两个进料罐上：顺序无关，单流体配方放进哪个罐都能扣料。
		// 不再用「in1 一定在 0 号罐」的假设 —— 那个假设会让玩家把单流体配方放到 1 号罐时
		// 机器静默退回原版分馏，还看不出原因。
		FluidStack[] need = recipe.assignTo(tanks[0].getTankType(), tanks[1].getTankType());

		if(need[0] == null && need[1] == null) return;

		for(int i = 0; i < 2; i++) {
			if(need[i] == null) continue;
			if(tanks[i].getFill() < need[i].fill) return;
		}

		int[] amountPerTank = new int[4];

		for(int i = 0; i < 4; i++) {
			if(recipe.out[i] == null) continue;
			int index = outputIndexFor(recipe.out[i].type);
			if(index < 0) return;
			amountPerTank[index] += recipe.out[i].fill;
		}

		for(int i = 0; i < 4; i++) {
			if(amountPerTank[i] <= 0) continue;
			if(tanks[2 + i].getFill() + amountPerTank[i] > tanks[2 + i].getMaxFill()) return;
		}

		if(recipe.item != null && !canOutputItem(recipe.item)) return;

		for(int i = 0; i < 2; i++) {
			if(need[i] == null) continue;
			tanks[i].setFill(tanks[i].getFill() - need[i].fill);
		}

		for(int i = 0; i < 4; i++) {
			if(amountPerTank[i] <= 0) continue;
			tanks[2 + i].setFill(tanks[2 + i].getFill() + amountPerTank[i]);
		}

		if(recipe.item != null) outputItem(recipe.item);
	}

	/**
	 * 原版分馏兜底。
	 *
	 * <p>两路进料都能分馏时，<b>先处理 0 号</b>；处理完再把 1 号塞进剩下的格子，
	 * 塞不下（产物种类超过 4 种）那一路就整条跳过。产物按罐型落位，
	 * 不同进料产出同一种流体时自然累加在同一个罐里。</p>
	 */
	private void craftFraction() {

		for(int t = 0; t < 2; t++) {

			Pair<FluidStack, FluidStack> quart = splitOf(tanks[t].getTankType());
			if(quart == null) continue;

			FluidStack outA = quart.getKey();
			FluidStack outB = quart.getValue();

			if(tryFraction(t, PROCESS_BATCH, outA, outB, 10)) continue;

			tryFraction(t, PROCESS_SMALL, outA, outB, 1);
		}
	}

	/**
	 * 从 {@code inputTank} 扣 {@code consume} mB，按 {@code factor} 倍吐出两路馏分。
	 *
	 * <p><b>套用催化裂化塔配方时也不吃蒸汽、不产低压蒸汽。</b>原版裂化塔是
	 * 「每 100 mB 进料配 200 mB 蒸汽，副产 2 mB 低压蒸汽」，本机只借用它的产物表，
	 * 蒸汽那一半账直接砍掉：一路进料进、两路馏分出，另一个进料罐完全不用管。
	 * 所以这里既不再检查 {@code Fluids.STEAM}，也不会去找 {@code Fluids.SPENTSTEAM} 的出料罐。</p>
	 *
	 * @return 是否成功
	 */
	private boolean tryFraction(int inputTank, int consume, FluidStack outA, FluidStack outB, int factor) {

		int indexA = outputIndexFor(outA.type);
		int indexB = outputIndexFor(outB.type);

		if(indexA < 0 || indexB < 0) return false;
		if(tanks[inputTank].getFill() < consume) return false;

		int[] add = new int[4];
		add[indexA] += outA.fill * factor;
		add[indexB] += outB.fill * factor;

		for(int i = 0; i < 4; i++) {
			if(add[i] <= 0) continue;
			if(tanks[2 + i].getFill() + add[i] > tanks[2 + i].getMaxFill()) return false;
		}

		tanks[inputTank].setFill(tanks[inputTank].getFill() - consume);

		for(int i = 0; i < 4; i++) {
			if(add[i] <= 0) continue;
			tanks[2 + i].setFill(tanks[2 + i].getFill() + add[i]);
		}

		return true;
	}

	/* ========================== 物品输出（7 号槽） ========================== */

	private boolean canOutputItem(ItemStack result) {

		ItemStack slot = slots[7];

		if(slot == null) return true;
		if(slot.getItem() != result.getItem()) return false;
		if(slot.getItemDamage() != result.getItemDamage()) return false;
		return slot.stackSize + result.stackSize <= Math.min(slot.getMaxStackSize(), getInventoryStackLimit());
	}

	private void outputItem(ItemStack result) {

		if(slots[7] == null) {
			slots[7] = result.copy();
		} else {
			slots[7].stackSize += result.stackSize;
		}
	}

	/* ========================== 网络同步 / 存档 ========================== */

	@Override
	public FluidTank[] getAllTanks() {
		return tanks;
	}

	@Override
	public FluidTank[] getSendingTanks() {
		return new FluidTank[] {tanks[2], tanks[3], tanks[4], tanks[5]};
	}

	@Override
	public FluidTank[] getReceivingTanks() {
		return new FluidTank[] {tanks[0], tanks[1]};
	}

	@Override
	public void serialize(ByteBuf buf) {
		for(int i = 0; i < tanks.length; i++) tanks[i].serialize(buf);
		buf.writeByte(catalystType);
	}

	@Override
	public void deserialize(ByteBuf buf) {
		for(int i = 0; i < tanks.length; i++) tanks[i].deserialize(buf);
		catalystType = buf.readByte();
	}

	@Override
	public void writeToNBT(NBTTagCompound nbt) {
		super.writeToNBT(nbt);
		for(int i = 0; i < tanks.length; i++) tanks[i].writeToNBT(nbt, "tank" + i);
	}

	@Override
	public void readFromNBT(NBTTagCompound nbt) {
		super.readFromNBT(nbt);
		for(int i = 0; i < tanks.length; i++) tanks[i].readFromNBT(nbt, "tank" + i);
	}

	AxisAlignedBB bb = null;

	@Override
	public AxisAlignedBB getRenderBoundingBox() {

		if(bb == null) {
			bb = AxisAlignedBB.getBoundingBox(
					xCoord - 2,
					yCoord,
					zCoord - 2,
					xCoord + 3,
					yCoord + HEIGHT,
					zCoord + 3
					);
		}

		return bb;
	}
}
