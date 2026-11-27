package com.makentmgreatagain.tileentity;

import com.hbm.blocks.BlockDummyable;
import com.hbm.handler.pollution.PollutionHandler.PollutionType;
import com.hbm.inventory.OreDictManager.DictFrame;
import com.hbm.inventory.fluid.FluidType;
import com.hbm.inventory.fluid.Fluids;
import com.hbm.inventory.fluid.tank.FluidTank;
import com.hbm.items.ItemEnums.EnumAshType;
import com.hbm.items.ItemEnums.EnumCokeType;
import com.hbm.items.machine.ItemMachineUpgrade;
import com.hbm.lib.Library;
import com.hbm.tileentity.TilePort;
import com.hbm.tileentity.TilePort.PortDef;
import com.makentmgreatagain.blocks.BlockLargeThermalPowerStation;

import api.hbm.energymk2.IBatteryItem;
import api.hbm.energymk2.IEnergyConnectorMK2;
import api.hbm.energymk2.IEnergyProviderMK2;
import api.hbm.fluidmk2.IFluidStandardReceiverMK2;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraftforge.common.util.ForgeDirection;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * 大型火力发电站核心格。
 *
 * <h3>运行机制（每 tick，开机且有热值时）</h3>
 * <pre>
 * 消耗 3 热值，常态发电 20kHE；
 * 每烧 3 热值耗 10mB 压缩空气追加 50kHE，或耗 2mB 液氧追加 100kHE（随耗热同比）；
 * 每消耗 3 热值 +1 灰烬，攒满 500 在灰烬槽产 1 细烟灰（槽满则暂停积攒）。
 * 燃料槽物品按 {@link #getHeatValue} 映射成热值，热值上限 1,000,000。
 * 流体有且只认压缩空气/液氧：识别码其他流体不改罐型，管道其他流体不收。
 * </pre>
 *
 * <h3>槽位</h3>
 * 0 燃料 / 1 灰烬(出) / 2 桶入 / 3 桶出 / 4 流体识别码 / 5,6 升级(槽位保留，暂无运行效果) / 7 电池。
 */
public class TileEntityLargeThermalPowerStation extends com.hbm.tileentity.TileEntityMachinePolluting
		implements IEnergyProviderMK2, IEnergyConnectorMK2, IFluidStandardReceiverMK2,
		com.hbm.tileentity.IUpgradeInfoProvider {

    public static final int SLOT_FUEL = 0, SLOT_ASH = 1, SLOT_BUCKET_IN = 2, SLOT_BUCKET_OUT = 3;
    public static final int SLOT_FLUID_ID = 4, SLOT_UPGRADE_1 = 5, SLOT_UPGRADE_2 = 6, SLOT_BATTERY = 7;
    public static final int SLOT_COUNT = 8;
    public static final long MAX_POWER = 10_000_000L;
    public static final int MAX_HEAT = 1_000_000, MAX_FLUID = 64_000;

    public static final int HEAT_PER_TICK = 3;
    public static final long POWER_BASE = 20_000L;
    /** 每 3 热值消耗 10mB 压缩空气 / 2mB 液氧（随耗热同比缩放） */
    public static final int AIR_PER_HEAT = 10;
    public static final long POWER_AIR = 50_000L;
    public static final int OXY_PER_HEAT = 2;
    public static final long POWER_OXY = 100_000L;
    public static final int ASH_PER_SOOT = 500;

    public boolean enabled;
    public int heat;
    public long power;
    /** 灰烬进度 0~499，满 500 产一个细烟灰 */
    public int ash;
    /** 上 tick 实际发电/热值消耗，供 GUI 读数角标 */
    public long lastProduction;
    public int lastConsumption;
    /** 灰烬小数累加器：速度升级耗热>3 时按比例攒灰烬 */
    private double ashAcc;
    /** 流体消耗小数累加器：比例量不足1mB的部分攒到下一tick */
    private double fluidAcc;

    /** 只认压缩空气/液氧的单罐，默认识别压缩空气 */
    public final FluidTank[] tanks = new FluidTank[] { new FluidTank(Fluids.AIR, MAX_FLUID) };

    public TileEntityLargeThermalPowerStation() { super(SLOT_COUNT, 100_000); }

    @Override public String getName() { return "container.large_thermal_power_station"; }

    /* ========================== 燃料热值映射 ========================== */

    private static Item cokeItem() { return com.hbm.items.ModItems.coke; }
    private static Item cokeBlockItem() { return Item.getItemFromBlock(com.hbm.blocks.ModBlocks.block_coke); }

    /** 燃料物品 → 热值；非燃料返回 0。简介提示与燃料槽校验共用这一张表。 */
    public static int getHeatValue(ItemStack stack) {
        if(stack == null) return 0;
        Item item = stack.getItem();
        int meta = stack.getItemDamage();
        if(item == com.hbm.items.ModItems.powder_lignite) return 100;          // 褐煤粉
        if(item == com.hbm.items.ModItems.powder_coal) return 150;             // 煤粉
        if(item == cokeItem()) {                                               // 焦炭（meta 区分煤种）
            if(meta == EnumCokeType.LIGNITE.ordinal()) return 300;             // 褐煤焦炭
            if(meta == EnumCokeType.COAL.ordinal()) return 400;                // 煤焦炭
            if(meta == EnumCokeType.PETROLEUM.ordinal()) return 500;           // 石油焦炭
            return 0;
        }
        if(item == cokeBlockItem()) {                                          // 焦炭块 = 9 倍单焦热值
            if(meta == EnumCokeType.LIGNITE.ordinal()) return 2700;
            if(meta == EnumCokeType.COAL.ordinal()) return 3600;
            if(meta == EnumCokeType.PETROLEUM.ordinal()) return 4500;
            return 0;
        }
        if(item == com.hbm.items.ModItems.solid_fuel) return 1000;             // 固体燃料
        if(item == com.hbm.items.ModItems.solid_fuel_presto) return 3000;      // Presto固体燃料
        if(item == com.hbm.items.ModItems.solid_fuel_presto_triplet) return 14000; // 增强型presto
        if(item == com.hbm.items.ModItems.rocket_fuel) return 1500;            // 固体燃料(火箭推进剂)
        if(item == com.makentmgreatagain.init.ModItems.solid_fuel_adv) return 2500; // 高级固态燃料
        if(item == com.hbm.items.ModItems.solid_fuel_bf) return 50000;         // 野火固体燃料
        if(item == com.hbm.items.ModItems.solid_fuel_presto_bf) return 150000; // Presto野火
        if(item == com.hbm.items.ModItems.solid_fuel_presto_triplet_bf) return 600000; // 增强型presto野火

        // 没手动热值但有燃烧时间的物品：按燃烧时间 1/100 折算热值，最低 1。
        // 只改本查询，不修改任何燃料物品本身；与 HBM 熔炉同用 getItemBurnTime，
        // 模组经 FuelHandler 注册的燃料也能查到
        int burnTime = net.minecraft.tileentity.TileEntityFurnace.getItemBurnTime(stack);
        if(burnTime > 0) return Math.max(1, burnTime / 100);
        return 0;
    }

    /* ========================== 主循环 ========================== */

    @Override public void updateEntity() {

        if(worldObj.isRemote) {
            updateAudio();
            return;
        }

        // 电力输出：4 个接口全部作为供电端口
        this.setupPowerPorts(this.getPowerPorts());
        for(TilePort port : this.powerPorts) port.update(worldObj);
        this.providePower();

        // 流体收发端口：收压缩空气/液氧，发烟雾（接排气管→烟囱冒烟）
        this.setupFluidPorts(this.getPowerPorts());
        this.updatePortFIFO();

        // 流体输入端口（罐型变化时重建，参照工业燃气轮机）
        updateFluidInputPorts();

        // 识别码：只认压缩空气/液氧，其他流体不改变罐型
        if(slots[SLOT_FLUID_ID] != null && slots[SLOT_FLUID_ID].getItem() instanceof com.hbm.items.machine.IItemFluidIdentifier) {
            FluidType f = ((com.hbm.items.machine.IItemFluidIdentifier) slots[SLOT_FLUID_ID].getItem())
                    .getType(worldObj, xCoord, yCoord, zCoord, slots[SLOT_FLUID_ID]);
            if(isBoostFluid(f)) ensureTankType(tanks[0], f);
        }

        // 桶入 / 满桶出
        tanks[0].loadTank(SLOT_BUCKET_IN, SLOT_BUCKET_OUT, slots);
        if(slots[SLOT_BUCKET_IN] == null || slots[SLOT_BUCKET_IN].getItem() != com.hbm.items.ModItems.fluid_barrel_infinite) {
            tanks[0].unloadTank(SLOT_BUCKET_IN, SLOT_BUCKET_OUT, slots);
        }

        // 燃料 → 热值（上限封顶，剩余空间不足时不吞燃料）
        ItemStack fuel = slots[SLOT_FUEL];
        int heatValue = getHeatValue(fuel);
        if(heatValue > 0 && heat <= MAX_HEAT - heatValue) {
            heat += heatValue;
            fuel.stackSize--;
            if(fuel.stackSize <= 0) slots[SLOT_FUEL] = null;
        }

        // 燃烧发电（速度升级提耗热与产量，效率升级只提产量）
        lastProduction = 0;
        lastConsumption = 0;
        int burn = heatPerTick();
        if(enabled && heat >= burn) {
            heat -= burn;
            lastConsumption = burn;
            long prod = POWER_BASE;

            // 流体助推：烧 3 热值耗 10mB 压缩空气 / 2mB 液氧，按耗热同比；
            // 攒够整数 mB 且罐里够才消耗并助推，比例发电量不缩放
            FluidType type = tanks[0].getTankType();
            int perHeat = sameFluid(type, Fluids.AIR) ? AIR_PER_HEAT
                    : sameFluid(type, Fluids.OXYGEN) ? OXY_PER_HEAT : 0;
            if(perHeat > 0) {
                double nextAcc = fluidAcc + burn * perHeat / (double) HEAT_PER_TICK;
                int need = (int) Math.floor(nextAcc);
                if(need > 0 && tanks[0].getFill() >= need) {
                    fluidAcc = nextAcc - need;
                    tanks[0].setFill(tanks[0].getFill() - need);
                    prod += sameFluid(type, Fluids.AIR) ? POWER_AIR : POWER_OXY;
                } else if(need > 0) {
                    // 缺流体时清零小数累计，避免干烧期间攒出欠债，复供时一口气吞光罐内流体
                    fluidAcc = 0;
                } else {
                    fluidAcc = nextAcc;
                }
            } else {
                fluidAcc = 0;
            }

            prod = (long) (prod * speedPowerMult() * (1D + efficiencyBonus()));
            power = Math.min(MAX_POWER, power + prod);
            lastProduction = prod;

            // 污染：燃烧期间每秒 0.32 烟炱，不排重金属。
            // 烟雾进罐经接口/排气管送烟囱冒烟；不接烟囱时罐满溢出直排，同样污染周围
            if(worldObj.getTotalWorldTime() % 20 == 0) {
                this.pollute(PollutionType.SOOT, 0.32F);
            }

            // 灰烬：每 3 热值 +1（与耗热同比）；灰烬槽满时暂停积攒，避免吞进度
            ashAcc += burn / (double) HEAT_PER_TICK;
            int whole = (int) Math.floor(ashAcc);
            ashAcc -= whole;
            for(int i = 0; i < whole; i++) {
                if(ash < ASH_PER_SOOT - 1) {
                    ash++;
                } else if(canInsertSoot()) {
                    insertSoot();
                    ash = 0;
                } else {
                    break;
                }
            }
        }

        // 电池充电
        power = Library.chargeItemsFromTE(slots, SLOT_BATTERY, power, MAX_POWER);

        // 周期同步（热值/电量/灰烬/开关/罐），范围 64 覆盖机身最远端口
        this.networkPackNT(64);
        if(worldObj.getTotalWorldTime() % 20 == 0) markDirty();
    }

    /* ========================== 升级（速度/效率，1~3 级） ========================== */

    private int upgradeTier(ItemMachineUpgrade.UpgradeType type) {
        int tier = 0;
        for(int s : new int[] {SLOT_UPGRADE_1, SLOT_UPGRADE_2}) {
            ItemStack st = slots[s];
            if(st != null && st.getItem() instanceof ItemMachineUpgrade) {
                ItemMachineUpgrade up = (ItemMachineUpgrade) st.getItem();
                if(up.type == type) tier = Math.max(tier, up.tier);
            }
        }
        return tier;
    }

    /** 每 tick 耗热：常态3，速度1~3 = 4/6/8 */
    public int heatPerTick() {
        switch(upgradeTier(ItemMachineUpgrade.UpgradeType.SPEED)) {
        case 1: return 4;
        case 2: return 6;
        case 3: return 8;
        default: return HEAT_PER_TICK;
        }
    }

    /** 速度1~3 产电 ×1.4/2.0/3.0 */
    public double speedPowerMult() {
        switch(upgradeTier(ItemMachineUpgrade.UpgradeType.SPEED)) {
        case 1: return 1.4D;
        case 2: return 2.0D;
        case 3: return 3.0D;
        default: return 1D;
        }
    }

    /** 效率1~3 产电 +20%/40%/80%，不增耗 */
    public double efficiencyBonus() {
        switch(upgradeTier(ItemMachineUpgrade.UpgradeType.EFFECT)) {
        case 1: return 0.2D;
        case 2: return 0.4D;
        case 3: return 0.8D;
        default: return 0D;
        }
    }

    /* ========================== 升级简介（仅本机器 GUI 悬停升级时显示） ========================== */

    @Override
    public boolean canProvideInfo(ItemMachineUpgrade.UpgradeType type, int level, boolean extendedInfo) {
        return type == ItemMachineUpgrade.UpgradeType.SPEED || type == ItemMachineUpgrade.UpgradeType.EFFECT;
    }

    @Override
    public void provideInfo(ItemMachineUpgrade.UpgradeType type, int level, java.util.List<String> info, boolean extendedInfo) {
        info.add(com.hbm.tileentity.IUpgradeInfoProvider.getStandardLabel(com.makentmgreatagain.init.ModBlocks.large_thermal_power_station));

        int tier = Math.max(1, Math.min(3, level));
        if(type == ItemMachineUpgrade.UpgradeType.SPEED) {
            int burn = tier == 1 ? 4 : tier == 2 ? 6 : 8;
            String pct = tier == 1 ? "+40%" : tier == 2 ? "+100%" : "+200%";
            info.add(net.minecraft.util.EnumChatFormatting.RED + com.hbm.util.i18n.I18nUtil.resolveKey("mnga.thermal_station.upgrade.cons", burn));
            info.add(net.minecraft.util.EnumChatFormatting.GREEN + com.hbm.util.i18n.I18nUtil.resolveKey("mnga.thermal_station.upgrade.power", pct));
        } else {
            String pct = tier == 1 ? "+20%" : tier == 2 ? "+40%" : "+80%";
            info.add(net.minecraft.util.EnumChatFormatting.GREEN + com.hbm.util.i18n.I18nUtil.resolveKey("mnga.thermal_station.upgrade.power", pct));
        }
    }

    @Override
    public java.util.HashMap<ItemMachineUpgrade.UpgradeType, Integer> getValidUpgrades() {
        java.util.HashMap<ItemMachineUpgrade.UpgradeType, Integer> map = new java.util.HashMap<ItemMachineUpgrade.UpgradeType, Integer>();
        map.put(ItemMachineUpgrade.UpgradeType.SPEED, 3);
        map.put(ItemMachineUpgrade.UpgradeType.EFFECT, 3);
        return map;
    }

    private boolean canInsertSoot() {
        ItemStack ashStack = slots[SLOT_ASH];
        if(ashStack == null) return true;
        return ashStack.getItem() == com.hbm.items.ModItems.powder_ash
                && ashStack.getItemDamage() == EnumAshType.SOOT.ordinal()
                && ashStack.stackSize < ashStack.getMaxStackSize();
    }

    private void insertSoot() {
        ItemStack soot = DictFrame.fromOne(com.hbm.items.ModItems.powder_ash, EnumAshType.SOOT);
        if(slots[SLOT_ASH] == null) slots[SLOT_ASH] = soot;
        else slots[SLOT_ASH].stackSize++;
    }

    /* ========================== 燃烧音效（客户端，火箱式随机噼啪） ========================== */

    private void updateAudio() {
        if(!enabled || heat < heatPerTick()) return;

        // 与 HBM 火箱同款：燃烧期间平均约每秒一次火焰噼啪，音调随机偏低沉。
        // 燃烧视觉是 GUI 内 (192,0)→(105,34) 的火焰块覆盖，不在世界里喷粒子
        if(worldObj.rand.nextInt(15) == 0 && !muffled) {
            worldObj.playSoundEffect(xCoord + 0.5D, yCoord + 0.5D, zCoord + 0.5D,
                    "fire.fire", 1.0F, 0.5F + worldObj.rand.nextFloat() * 0.5F);
        }
    }

    /* ========================== 流体端口 / 白名单 ========================== */

    protected PortDef[] cachedPowerPorts;
    public PortDef[] getPowerPorts() {
        if(cachedPowerPorts == null) {
            ForgeDirection dir = ForgeDirection.getOrientation(this.getBlockMetadata() - 10);
            int[][] ports = ((BlockLargeThermalPowerStation) this.getBlockType()).getPortsAtCore(dir);
            cachedPowerPorts = new PortDef[ports.length];
            for(int i = 0; i < ports.length; i++) {
                cachedPowerPorts[i] = PortDef.make(xCoord + ports[i][0], yCoord + ports[i][1], zCoord + ports[i][2],
                        ForgeDirection.getOrientation(ports[i][3]));
            }
        }
        return cachedPowerPorts;
    }

    private TilePort[] fluidInputPorts;
    private FluidType[] configuredFluidTypes;

    private void updateFluidInputPorts() {
        boolean rebuild = fluidInputPorts == null || configuredFluidTypes == null;
        if(!rebuild) {
            for(int i = 0; i < tanks.length; i++) {
                if(!sameFluid(configuredFluidTypes[i], tanks[i].getTankType())) { rebuild = true; break; }
            }
        }
        if(rebuild) {
            if(fluidInputPorts != null) for(TilePort port : fluidInputPorts) port.disableIfPresent(worldObj);
            PortDef[] ports = getPowerPorts();
            java.util.List<FluidType> types = new java.util.ArrayList<FluidType>();
            for(FluidTank tank : tanks) {
                FluidType type = tank.getTankType();
                if(type == null || sameFluid(type, Fluids.NONE)) continue;
                boolean found = false;
                for(FluidType existing : types) if(sameFluid(existing, type)) found = true;
                if(!found) types.add(type);
            }
            fluidInputPorts = new TilePort[types.size() * ports.length];
            for(int t = 0; t < types.size(); t++) {
                for(int p = 0; p < ports.length; p++) {
                    PortDef def = ports[p];
                    TilePort port = new TilePort().setupOwner(this)
                            .setupPositions(def.portPositions)
                            .setupConnections(def.portConnections);
                    port.setupType(types.get(t).getNetworkProvider());
                    fluidInputPorts[t * ports.length + p] = port;
                }
            }
            configuredFluidTypes = new FluidType[tanks.length];
            for(int i = 0; i < tanks.length; i++) configuredFluidTypes[i] = tanks[i].getTankType();
        }
        for(TilePort port : fluidInputPorts) port.update(worldObj);
        for(TilePort port : fluidInputPorts) if(!port.needsRebuild) port.checkSubscribe(worldObj);
    }

    private void disableFluidInputPorts() {
        if(fluidInputPorts != null) {
            for(TilePort port : fluidInputPorts) port.disableIfPresent(worldObj);
            fluidInputPorts = null;
        }
    }

    @Override public void onChunkUnload() { disableFluidInputPorts(); super.onChunkUnload(); }

    @Override public void invalidate() { disableFluidInputPorts(); super.invalidate(); }

    /** 同一种流体：引用相等优先，宿主双实例时按本地化名兜底 */
    private static boolean sameFluid(FluidType a, FluidType b) {
        if(a == b) return true;
        if(a == null || b == null) return false;
        String ua = a.getUnlocalizedName();
        return ua != null && ua.equals(b.getUnlocalizedName());
    }

    public static boolean isBoostFluid(FluidType type) {
        return sameFluid(type, Fluids.AIR) || sameFluid(type, Fluids.OXYGEN);
    }

    /** 只在非同种流体时改罐型，避免 setTankType 的引用比较反复清罐 */
    private static void ensureTankType(FluidTank tank, FluidType type) {
        if(type == null || sameFluid(type, Fluids.NONE)) return;
        if(!sameFluid(tank.getTankType(), type)) tank.setTankType(type);
    }

    /* ========================== 电力 / 流体接口 ========================== */

    @Override public long getPower() { return power; }
    @Override public void setPower(long p) { this.power = Math.min(p, MAX_POWER); }
    @Override public long getMaxPower() { return MAX_POWER; }
    @Override public boolean canConnect(ForgeDirection dir) { return true; }

    @Override public FluidTank[] getAllTanks() { return tanks; }
    @Override public FluidTank[] getReceivingTanks() { return tanks; }
    @Override public FluidTank[] getSendingTanks() { return getSmokeTanks(); }

    @Override public boolean canConnect(FluidType type, ForgeDirection dir) {
        // 烟雾是外送流体：连接判定必须放行，排气管/烟囱的连接纹理才画得出来
        // （实际接收仍由 getDemand/transferFluid 的白名单把关，不会收进烟雾）
        if(type == Fluids.SMOKE || type == Fluids.SMOKE_LEADED || type == Fluids.SMOKE_POISON) return true;
        return isBoostFluid(type) && getDemand(type, tanks[0].getPressure()) > 0;
    }

    @Override public long getDemand(FluidType type, int pressure) {
        if(!isBoostFluid(type)) return 0;
        if(sameFluid(tanks[0].getTankType(), type) && tanks[0].getPressure() == pressure)
            return Math.max(0, tanks[0].getMaxFill() - tanks[0].getFill());
        // 空罐可认领两种助推流体之一
        if(sameFluid(tanks[0].getTankType(), Fluids.NONE) && tanks[0].getFill() == 0
                && pressure >= 0 && pressure <= HIGHEST_VALID_PRESSURE)
            return tanks[0].getMaxFill();
        return 0;
    }

    @Override public long transferFluid(FluidType type, int pressure, long amount) {
        if(amount <= 0 || !isBoostFluid(type)) return amount;
        if(sameFluid(tanks[0].getTankType(), Fluids.NONE) && tanks[0].getFill() == 0
                && pressure >= 0 && pressure <= HIGHEST_VALID_PRESSURE) {
            ensureTankType(tanks[0], type);
            tanks[0].withPressure(pressure);
        }
        if(sameFluid(tanks[0].getTankType(), type) && tanks[0].getPressure() == pressure) {
            int add = (int) Math.min(amount, Math.max(0, tanks[0].getMaxFill() - tanks[0].getFill()));
            tanks[0].setFill(tanks[0].getFill() + add);
            amount -= add;
        }
        return amount;
    }

    @Override public int[] getReceivingPressureRange(FluidType type) {
        if(!isBoostFluid(type)) return DEFAULT_PRESSURE_RANGE;
        if(sameFluid(tanks[0].getTankType(), type)) return new int[] { tanks[0].getPressure(), tanks[0].getPressure() };
        if(sameFluid(tanks[0].getTankType(), Fluids.NONE)) return new int[] { 0, HIGHEST_VALID_PRESSURE };
        return DEFAULT_PRESSURE_RANGE;
    }

    /* ========================== 物品 / 自动化 ========================== */

    @Override public int[] getAccessibleSlotsFromSide(int side) {
        return new int[] {SLOT_FUEL, SLOT_ASH, SLOT_BUCKET_IN, SLOT_BUCKET_OUT, SLOT_BATTERY};
    }

    @Override public boolean isItemValidForSlot(int slot, ItemStack stack) {
        if(stack == null) return false;
        switch(slot) {
        case SLOT_FUEL: return getHeatValue(stack) > 0;
        case SLOT_ASH: case SLOT_BUCKET_OUT: return false;
        case SLOT_BUCKET_IN:
            return stack.getItem() == com.hbm.items.ModItems.fluid_barrel_infinite
                || stack.getItem() instanceof api.hbm.fluidmk2.IFillableItem
                || com.hbm.inventory.FluidContainerRegistry.getEmptyContainer(stack) != null;
        case SLOT_FLUID_ID: return stack.getItem() instanceof com.hbm.items.machine.IItemFluidIdentifier;
        case SLOT_UPGRADE_1: case SLOT_UPGRADE_2:
            if(stack.getItem() instanceof ItemMachineUpgrade) {
                ItemMachineUpgrade.UpgradeType type = ((ItemMachineUpgrade) stack.getItem()).type;
                return type == ItemMachineUpgrade.UpgradeType.SPEED || type == ItemMachineUpgrade.UpgradeType.EFFECT;
            }
            return false;
        case SLOT_BATTERY:
            return stack.getItem() instanceof IBatteryItem || stack.getItem() == com.hbm.items.ModItems.battery_creative;
        default: return false;
        }
    }

    /** 漏斗只能抽产出/电池/空桶，不能抽走燃料、识别码、升级 */
    @Override public boolean canExtractItem(int slot, ItemStack stack, int side) {
        return slot == SLOT_ASH || slot == SLOT_BUCKET_OUT || slot == SLOT_BATTERY;
    }

    public void setEnabled(boolean value) {
        if(enabled == value) return;
        enabled = value;
        markDirty();
    }

    /* ========================== 存档 / 同步 ========================== */

    @Override public void writeToNBT(NBTTagCompound nbt) {
        super.writeToNBT(nbt);
        nbt.setBoolean("stationEnabled", enabled);
        nbt.setInteger("stationHeat", heat);
        nbt.setLong("stationPower", power);
        nbt.setInteger("stationAsh", ash);
        for(int i = 0; i < tanks.length; i++) tanks[i].writeToNBT(nbt, "stationTank" + i);
    }

    @Override public void readFromNBT(NBTTagCompound nbt) {
        super.readFromNBT(nbt);
        enabled = nbt.getBoolean("stationEnabled");
        heat = Math.max(0, Math.min(MAX_HEAT, nbt.getInteger("stationHeat")));
        power = Math.max(0L, Math.min(MAX_POWER, nbt.getLong("stationPower")));
        ash = Math.max(0, Math.min(ASH_PER_SOOT - 1, nbt.getInteger("stationAsh")));
        for(int i = 0; i < tanks.length; i++) tanks[i].readFromNBT(nbt, "stationTank" + i);
    }

    @Override public void serialize(io.netty.buffer.ByteBuf buf) {
        super.serialize(buf);
        buf.writeBoolean(enabled);
        buf.writeInt(heat);
        buf.writeLong(power);
        buf.writeInt(ash);
        buf.writeLong(lastProduction);
        buf.writeInt(lastConsumption);
        for(FluidTank t : tanks) t.serialize(buf);
    }

    @Override public void deserialize(io.netty.buffer.ByteBuf buf) {
        super.deserialize(buf);
        enabled = buf.readBoolean();
        heat = buf.readInt();
        power = buf.readLong();
        ash = buf.readInt();
        lastProduction = buf.readLong();
        lastConsumption = buf.readInt();
        for(FluidTank t : tanks) t.deserialize(buf);
    }

    @Override public Packet getDescriptionPacket() {
        NBTTagCompound nbt = new NBTTagCompound();
        writeToNBT(nbt);
        return new S35PacketUpdateTileEntity(xCoord, yCoord, zCoord, 0, nbt);
    }

    @Override public void onDataPacket(NetworkManager net, S35PacketUpdateTileEntity packet) {
        readFromNBT(packet.func_148857_g());
    }

    @Override @SideOnly(Side.CLIENT)
    public AxisAlignedBB getRenderBoundingBox() {
        return AxisAlignedBB.getBoundingBox(xCoord - 2, yCoord - 1, zCoord - 4, xCoord + 3, yCoord + 6, zCoord + 4);
    }

    @Override @SideOnly(Side.CLIENT)
    public double getMaxRenderDistanceSquared() { return 65536.0D; }
}