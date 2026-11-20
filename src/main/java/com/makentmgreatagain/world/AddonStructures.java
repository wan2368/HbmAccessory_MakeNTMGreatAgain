package com.makentmgreatagain.world;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.hbm.blocks.ModBlocks;
import com.hbm.blocks.generic.BlockNTMSand.EnumSandType;
import com.hbm.config.GeneralConfig;
import com.hbm.config.MobConfig;
import com.hbm.items.ModItems;
import com.hbm.world.gen.nbt.JigsawPiece;
import com.hbm.world.gen.nbt.NBTStructure;
import com.hbm.world.gen.nbt.SpawnCondition;
import com.makentmgreatagain.MakeNTMGreatAgain;
import com.makentmgreatagain.config.ModConfig;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagInt;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.MathHelper;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.gen.structure.MapGenStructureIO;
import net.minecraft.world.gen.structure.StructureBoundingBox;
import net.minecraft.world.gen.structure.StructureComponent;
import net.minecraftforge.common.BiomeDictionary;
import net.minecraftforge.common.BiomeDictionary.Type;
import net.minecraftforge.common.util.Constants.NBT;

/**
 * 自然遗迹（NBT 结构）的注册与战利品填充。
 *
 * <h3>为什么复用 HBM 的 NBTStructure，而不是自己写 IWorldGenerator</h3>
 * <p>HBM 的 {@code NBTStructure} 已经把「什么时候在哪个区块生成」这件事做完了：
 * {@code NTMWorldGenerator} 会监听 {@code PopulateChunkEvent.Pre}，按 4~12 区块的网格挑槽位、
 * 按生物群系权重选结构、把结构拆成组件后只在当前区块范围内写方块。
 * 我们只要往它的静态注册表里塞一条 {@link SpawnCondition}，
 * HBM 的生成器就会顺手把我们的遗迹一起生成——不需要注册第二个 IWorldGenerator，
 * 也不会出现「HBM 和附属各跑一遍、同	一个位置生成两次」的问题。</p>
 *
 * <h3>密度（生成概率）怎么调</h3>
 * <p>我们没走 HBM 那套全局槽位，而是挂在自己的 {@link #isSpawnChunk}
 * （{@code SpawnCondition#checkCoordinates} 通道）：HBM 每问一个区块，我们按
 * 「网格 + 概率」两层回答。</p>
 * <ul>
 *   <li><b>网格</b>：{@link ModConfig#structureGridChunks} = N → 世界切成 N×N 区块的格子；
 *       默认 11 → 每 121 区块一个格子。</li>
 *   <li><b>概率</b>：{@link #SPAWN_CHANCE_PERCENT} → 命中落点的格子真正生成的比例；
 *       <b>默认已从 100 降到 50</b>，即平均约 242 区块一座。</li>
 * </ul>
 * <p>只想「降低遇到率」而不想动配置文件时，改 {@link #SPAWN_CHANCE_PERCENT} 一个数字即可，
 * 改完只影响还没生成的区块。</p>
 *
 * <h3>结构文件从哪来</h3>
 * <p>建筑是在存档里搭的，需要用 HBM 自带的导出方块 {@code hbm:tile.wand_structure}（meta 0 = 保存）
 * 存成 {@code .minecraft/structures/xxx.nbt}，再复制到
 * {@code src/main/resources/assets/makentmgreatagain/structures/xxx.nbt}。
 * 本类默认读 {@link #RUIN_NBT} 这一个路径；实际路径来自配置项
 * {@code structureNbtFile}（相对 {@code assets/makentmgreatagain/}），
 * 想在整合包里换建筑只改 cfg 即可，不用重编译。</p>
 *
 * <h3>箱子内容怎么随机</h3>
 * <p>导出结构时箱子里的物品照常存进 NBT（HBM 的 {@code saveArea} 会写 TE 的完整 NBT，
 * 并把物品 ID 重映射成注册名），这份内容在这里<b>只当作「模板」</b>：
 * 生成时逐格掷骰，按物品的稀有度和它原来在这一格的数量决定「留不留」，
 * 留下的再随机一个数量，最后把整箱清空、重新塞进<b>随机格子</b>里。</p>
 * <ul>
 *   <li><b>稀有度</b>：{@link #ITEM_RARITY}，见 {@link Rarity}。越稀有越容易消失；
 *       遗迹本身很稀有，所以基础保留率整体给得偏高（默认常见 90%、较稀有 78%、
 *       稀有 62%、极稀有 45%、传说 30%）。<b>这五个数字在配置里</b>
 *       （{@code structureLootKeepCommon} 等），改 cfg 立即生效、不用重编译。</li>
 *   <li><b>数量</b>：原来堆得越多越容易留下（每多 1 个 +1%，封顶 +15%），
 *       留下的数量在「{@code structureLootCountMinPercent}（默认 40%）~ 原数量」之间重新掷。</li>
 *   <li><b>位置</b>：默认整箱清空后随机挑选空格，所以不会像导出时那样按 0、1、2… 排成一列；
 *       {@code structureLootScatter=false} 时改为从第 0 格顺序摆放，保留模板位置。</li>
 * </ul>
 * <p>特殊物品：成书（{@code written_book}）带正文 NBT，原样保留、不掷骰；
 * 枪械/护甲/电池的 {@code tag}（弹药、充能、燃料）跟着 {@code ItemStack.copy()} 一起保留。</p>
 *
 * <h3>机器里的物品与流体</h3>
 * <p>同样是「结构 NBT 原样保存」——导出时机器里有什么，每个遗迹就有什么。
 * 想让某个值每次随机（例如反应堆燃料棒数量、罐里的液体量），
 * 在 {@link #applyMachineContent} 里按 TE 类型写分支。</p>
 *
 * <h3>结构劣化（概率替换方块）</h3>
 * <p>{@link #DECAY_TABLE} 让完好的建筑每次生成时随机破败一点：
 * 强化萤石变空气、混凝土砖裂成破碎砖、黑曜石砖掉成碎黑曜石之类。
 * 判定发生在 {@code afterGenerate} 里，范围已收紧到「本次 box ∩ 结构包围盒」，
 * 所以不会误伤区块里原本就有的同名方块。</p>
 *
 * <p><b>两个后处理都挂在 {@code RuinStart#generateStructure} 上</b>，而这个方法会被结构
 * 覆盖到的<b>每个区块各调一次</b>（原版传进来的 box 是「区块中心 ±8」的 16×16，相邻区块
 * 首尾相接）。这里一旦把 X/Z 的交集算错，整段后处理就会静默失效——
 * 表现就是「箱子内容永远固定、概率替换方块毫无效果」，且没有任何报错。</p>
 */
public final class AddonStructures {

    private static final Logger LOG = LogManager.getLogger("MNGA-Structures");

    /**
     * 默认的 .nbt 路径，相对 {@code assets/makentmgreatagain/}。
     *
     * <p>只是<b>默认值</b>：真正生效的是配置项 {@code structureNbtFile}
     * （{@link ModConfig#structureNbtFile}），这里留着当兜底，配置写空时回退到它。</p>
     */
    public static final String RUIN_NBT = "structures/mnga_structure_geocentric_laboratory.nbt";

    /** 注册名，必须全局唯一（HBM 用名字查重，重名会抛异常）。 */
    private static final String SPAWN_NAME = "mnga_geocentric_laboratory";
    private static final String PIECE_NAME = "mnga_geocentric_laboratory";

    /** 大型异虫巢的默认 NBT（相对 {@code assets/makentmgreatagain/}），配置为空时兜底。 */
    public static final String HIVE_NBT = "structures/mnga_structure_large_alien_insec.nbt";

    /** 大型异虫巢的注册名 / piece 名，同样必须全局唯一。 */
    private static final String HIVE_SPAWN_NAME = "mnga_large_alien_hive";
    private static final String HIVE_PIECE_NAME = "mnga_large_alien_hive";

    /** 日落六号的默认 NBT（相对 {@code assets/makentmgreatagain/}），配置为空时兜底。 */
    public static final String SUNSET_NBT = "structures/mnga_structure_sunset_six.nbt";

    /** 日落六号的注册名 / piece 名，同样必须全局唯一。 */
    private static final String SUNSET_SPAWN_NAME = "mnga_sunset_six";
    private static final String SUNSET_PIECE_NAME = "mnga_sunset_six";

    /**
     * 【遗迹生成概率】命中格子后真正生成的概率（%）。<b>只改这一个数字就能调稀/调密。</b>
     *
     * <p>默认网格是 {@code structureGridChunks = 11}，即每 11×11 = 121 区块划一个格子，
     * 每格由 hash 算出唯一落点。这个百分比是乘在网格上的第二道闸：</p>
     *
     * <table border="1">
     *   <tr><th>本值</th><th>效果</th><th>平均间隔（默认网格 121 区块/格）</th></tr>
     *   <tr><td>100</td><td>每个命中格都生成（改造前的原始行为）</td><td>约 121 区块一座</td></tr>
     *   <tr><td>50</td><td>一半的格子是空的（<b>当前默认</b>）</td><td>约 242 区块一座</td></tr>
     *   <tr><td>25</td><td>四分之一</td><td>约 484 区块一座</td></tr>
     *   <tr><td>10</td><td>十分之一，非常难遇到</td><td>约 1210 区块一座</td></tr>
     * </table>
     *
     * <p>它和 {@code structureGridChunks} 是乘法关系、互不干扰：网格决定「格子多大」，
     * 这里决定「格子空不空」。只想降密度、不想动配置文件时，改这一个常量即可。</p>
     *
     * <p><b>改完后只对尚未生成的区块生效</b>：已生成区块的 StructureStart 已经落盘，
     * 走过去不会重新掷骰。</p>
     */
    private static final int SPAWN_CHANCE_PERCENT = 50;

    /**
     * 生成后会被当成「战利品容器」的方块。
     *
     * <p>必须是真正实现了 {@code IInventory} 的容器方块，而且要有 TE。
     * 注意别把 {@code ModBlocks.crate}（HBM 的补给木箱）放进来——那是
     * {@code BlockCrate extends BlockFalling}，右键直接吐物品，不是容器、没有 TE。</p>
     */
    private static final Block[] LOOT_CONTAINERS = {
            Blocks.chest,
            Blocks.trapped_chest,
            ModBlocks.crate_iron,
            ModBlocks.crate_steel,   // 地心实验室里的 10 个箱子全是这个（BlockStorageCrate，36 格）
            ModBlocks.crate_desh
    };

    /**
     * 物品 → 稀有度。稀有度只影响「这一格内容留不留」，不影响数量。
     *
     * <p>没登记的物品一律按 {@link Rarity#COMMON} 处理（宁可多留，也不要让箱子莫名其妙空掉）。
     * 同一个 Item 有多个变体、价值差别大的（附魔金苹果），在
     * {@link #rarityOf(ItemStack)} 里按 meta 单独判。</p>
     */
    private static final Map<Item, Rarity> ITEM_RARITY = new HashMap<Item, Rarity>();

    static {
        // ---- 常见：工业耗材、建材、食物、废料。箱子里有几格就基本留几格 ----
        rate(Rarity.COMMON,
                Items.flint_and_steel, Items.nether_wart, Items.golden_carrot,
                Items.speckled_melon, Items.pumpkin_seeds, Items.melon_seeds, Items.potionitem,
                Blocks.obsidian, Blocks.grass,
                ModItems.ingot_steel, ModItems.plate_steel, ModItems.ingot_lead, ModItems.ingot_titanium,
                ModItems.pipe, ModItems.door_metal, ModItems.ball_fireclay,
                ModBlocks.pipe_anchor, ModBlocks.fluid_duct_neo, ModBlocks.fluid_duct_paintable,
                ModBlocks.machine_fluidtank, ModBlocks.barrel_steel, ModBlocks.waste_planks,
                ModBlocks.steel_scaffold, ModBlocks.steel_grate, ModBlocks.red_cable, ModBlocks.red_connector,
                ModItems.powder_coal, ModItems.powder_iron, ModItems.powder_ash,
                ModItems.powder_uranium, ModItems.powder_sodium,
                ModItems.syringe_metal_empty, ModItems.syringe_empty,
                ModItems.cheese, ModItems.twinkie, ModItems.glyphid_meat, ModItems.pill_herbal,
                ModItems.nuclear_waste, ModItems.nuclear_waste_tiny, ModItems.billet_nuclear_waste,
                ModItems.fluid_tank_full);

        // ---- 较稀有：加工品、防护服、医疗、弹药 ----
        rate(Rarity.UNCOMMON,
                Items.golden_apple, Items.ghast_tear,
                ModItems.ingot_dura_steel, ModItems.powder_niobium, ModItems.powder_titanium,
                ModItems.siox, ModItems.radaway, ModItems.iv_blood, ModItems.apple_lead,
                ModItems.pollution_detector, ModItems.gas_mask_filter, ModItems.gas_mask_filter_mono,
                ModItems.hazmat_paa_legs, ModItems.asbestos_helmet, ModItems.asbestos_plate,
                ModItems.asbestos_legs, ModItems.asbestos_boots,
                ModItems.ammo_standard);

        // ---- 稀有：核材料、机器、军械、机器升级 ----
        rate(Rarity.RARE,
                ModItems.billet_u235, ModItems.rod, ModItems.rod_dual, ModItems.rod_quad,
                ModItems.pwr_fuel, ModBlocks.machine_centrifuge,
                ModItems.upgrade_effect_1, ModItems.upgrade_speed_2,
                ModItems.gun_heavy_revolver, ModItems.gun_light_revolver, ModItems.gun_greasegun,
                ModItems.desh_pickaxe, ModItems.cobalt_sword,
                ModItems.battery_pack);

        // ---- 极稀有：动力装甲、蒸汽服 ----
        rate(Rarity.EPIC,
                ModItems.t51_helmet, ModItems.steamsuit_plate, ModItems.steamsuit_boots);
    }

    /**
     * 结构「劣化」规则：生成时按概率把完好的方块换成破败版本。
     *
     * <p>每个来源方块挂若干条结果，按数组顺序<b>依次判定、命中即停</b>——
     * 所以「耐火砖块 5% 变台阶、10% 变空气」的实际比例是 5% 台阶 + 9.5% 空气，
     * 不会被两条规则同时命中。</p>
     *
     * <p>只对<b>结构自己放下的方块</b>生效（范围已收到结构包围盒），
     * 不会碰到区块里原本就有的同名方块。</p>
     *
     * <p>这里的数字是「基准值」，实际概率会被配置倍率
     * {@link ModConfig#structureDecayScalePercent} 缩放（默认 100% = 原值），
     * 总开关是 {@link ModConfig#structureDecayEnabled}。要整体调破败程度改配置即可，
     * 想改「哪种方块烂成什么」才需要动这张表。</p>
     */
    private static final Decay[] DECAY_TABLE = {
            // 强化萤石 → 30% 空气
            new Decay(ModBlocks.reinforced_light,
                    new Outcome(null, 0, 30F)),

            // 耐火砖块 → 5% 耐火砖台阶、10% 空气
            // brick_slab 是 BlockMultiSlab，meta 就是构造参数里的下标，brick_fire 排第 7 个 → meta 6
            new Decay(ModBlocks.brick_fire,
                    new Outcome(ModBlocks.brick_slab, 6, 5F),
                    new Outcome(null, 0, 10F)),

            // 高密度贫铀混凝土砖块 → 20% 高密度贫铀混凝土、10% 铀沙
            // 「铀沙」是 ModBlocks.sand_mix 的 URANIUM 变体（lang 里叫 tile.sand_uranium）
            new Decay(ModBlocks.brick_ducrete,
                    new Outcome(ModBlocks.ducrete_smooth, 0, 20F),
                    new Outcome(ModBlocks.sand_mix, EnumSandType.URANIUM.ordinal(), 10F)),

            // 加固高密度贫铀混凝土 → 10% 高密度贫铀混凝土、5% 地狱岩
            new Decay(ModBlocks.reinforced_ducrete,
                    new Outcome(ModBlocks.ducrete_smooth, 0, 10F),
                    new Outcome(Blocks.netherrack, 0, 5F)),

            // 黑曜石砖 → 20% 黑曜石、4% 碎黑曜石
            new Decay(ModBlocks.brick_obsidian,
                    new Outcome(Blocks.obsidian, 0, 20F),
                    new Outcome(ModBlocks.gravel_obsidian, 0, 4F)),

            // 混凝土砖块 → 30% 开裂、30% 破碎、10% 空气
            new Decay(ModBlocks.brick_concrete,
                    new Outcome(ModBlocks.brick_concrete_cracked, 0, 30F),
                    new Outcome(ModBlocks.brick_concrete_broken, 0, 30F),
                    new Outcome(null, 0, 10F)),

            // 铁丝网围栏 → 10% 空气
            new Decay(ModBlocks.fence_metal,
                    new Outcome(null, 0, 10F)),
    };

    private AddonStructures() {
    }

    /**
     * 在 postInit 调用一次。结构文件不存在时只打一条日志并跳过，不会让游戏崩。
     *
     * <p>必须放在 postInit：HBM 在 init 阶段才把 {@code NBTStructure.Component} 注册进
     * {@code MapGenStructureIO}，世界加载又在这之后。</p>
     */
    public static void register() {

        // 创世神（WorldEdit）在 1.7.10 只认数字 ID，不能填 hbm:wand_air 这种名字，
        // 所以把 ID 打出来，导出建筑时用 `//replace air <ID>` 一键铺内部空腔。
        // ID 是运行时按注册顺序分配的，装了不同数量的模组就会变，所以只能打印、不能写死。
        int airId = Block.getIdFromBlock(ModBlocks.wand_air);
        int structureId = Block.getIdFromBlock(ModBlocks.wand_structure);
        debug("结构工具方块 ID（创世神填充用，仅本次加载有效）: "
                + "wand_air=" + airId + " → 填内部空气用 `//replace air " + airId + "`"
                + "；wand_structure=" + structureId + " → 手放保存方块用 `//set " + structureId + "`");

        // 三个结构各自有开关、互不影响：地心实验室在下界，大异虫巢在主世界，日落六号在下界。
        registerLaboratory();
        registerAlienHive();
        registerSunsetSix();
    }

    /** 地心实验室：下界、固定层位，带方块劣化与战利品随机化。 */
    private static void registerLaboratory() {

        if(!ModConfig.structureWorldgenEnabled) {
            debug("自然遗迹已在配置中关闭");
            return;
        }

        // 路径来自配置（structureNbtFile），默认就是自带的那个文件。
        // 统一分隔符并去掉前导斜杠，免得有人按 Windows 习惯写成 "structures\\xxx.nbt"。
        String nbtPath = normalizeNbtPath(ModConfig.structureNbtFile, RUIN_NBT);

        if(!assetExists(nbtPath)) {
            LOG.warn("[structure] 找不到 assets/" + MakeNTMGreatAgain.MODID + "/" + nbtPath
                    + "，跳过遗迹注册（检查配置 structureNbtFile；"
                    + "用 hbm:tile.wand_structure 导出后放进 assets/"
                    + MakeNTMGreatAgain.MODID + "/structures/ 即可）");
            return;
        }

        // 幂等：HBM 的 HbmWorld.init 已经调过一次，这里只是兜底，防止加载顺序变化导致 NPE
        NBTStructure.register();
        // 我们的 Start 是 NBTStructure.Start 的子类，必须单独登记，否则存档时查不到 id 会 NPE
        MapGenStructureIO.registerStructure(RuinStart.class, "MNGA_RuinStart");

        NBTStructure structure = new NBTStructure(new ResourceLocation(MakeNTMGreatAgain.MODID, nbtPath));

        SpawnCondition spawn = new SpawnCondition(SPAWN_NAME);
        spawn.canSpawn = AddonStructures::isValidBiome;
        spawn.structure = new JigsawPiece(PIECE_NAME, structure, ModConfig.structureHeightOffset);
        spawn.spawnWeight = ModConfig.structureSpawnWeight; // 自定义坐标模式下不参与抽签，留着只为日志可读
        spawn.minHeight = ModConfig.structureMinHeight;
        spawn.maxHeight = ModConfig.structureMaxHeight;

        // 自己排格子，不走 HBM 的全局结构网格。
        // HBM 的槽位间距是全局配置（默认 12 区块 = 每 144 区块一个），改它会把主世界的
        // 废墟/实验室一起改密；而 checkCoordinates 是「每个区块都问一次」的独立通道，
        // 我们自己决定哪一格生成，间距完全可控。
        spawn.checkCoordinates = AddonStructures::isSpawnChunk;

        // start 非空 → GenStructure 走我们的子类，生成完就地填战利品。
        // 注意 Quartet 的字段顺序是 (World, Random, chunkX, chunkZ)，所以 Random 取 getX()。
        spawn.start = data -> new RuinStart(data.getW(), data.getX(), spawn, data.getY(), data.getZ());

        NBTStructure.registerStructure(ModConfig.structureDimensionId, spawn);

        debug("遗迹已注册: dim=" + ModConfig.structureDimensionId
                + ", nbt=" + nbtPath
                + ", size=" + structure.getSizeX() + "x" + structure.getSizeY() + "x" + structure.getSizeZ()
                + ", y=" + spawn.minHeight + "~" + spawn.maxHeight
                + ", grid=" + ModConfig.structureGridChunks + " 区块（每 "
                + (ModConfig.structureGridChunks * ModConfig.structureGridChunks) + " 区块一格，"
                + SPAWN_CHANCE_PERCENT + "% 生成 → 平均每 "
                + averageDistance() + " 区块一处）"
                + ", 留边=" + ModConfig.structureGridInset
                + ", 劣化=" + (ModConfig.structureDecayEnabled
                        ? ModConfig.structureDecayScalePercent + "%" : "off")
                + ", 战利品随机化=" + (ModConfig.structureLootRandomize
                        ? (ModConfig.structureLootScatter ? "on(打散)" : "on(原位)") : "off"));
    }

    // =====================================================================================
    //  大型异虫巢（第二个结构，复刻 HBM 小虫巢的生成条件，概率 1/20）
    // =====================================================================================

    /**
     * 注册大型异虫巢。
     *
     * <p>生成条件与 HBM 的「小虫巢」逐条对齐（{@code HbmWorldGen} 里 {@code GlyphidHive}
     * 那一段）：</p>
     * <ul>
     *   <li><b>维度</b>：主世界（{@code structureAlienHiveDimensionId}，默认 0）；</li>
     *   <li><b>前置开关</b>：HBM 的 {@code GeneralConfig.enableDungeons}（含「地图特性」）
     *       与 {@code MobConfig.enableHives}，可用 {@code structureAlienHiveFollowHbm} 关掉；</li>
     *   <li><b>密度</b>：每 {@code MobConfig.hiveSpawn × 倍数} 区块一座，
     *       默认 256×20 = 5120，正好是小虫巢的 1/20；</li>
     *   <li><b>高度</b>：结构几何中心落在地表——小虫巢是 {@code y + j - 2}（j=0..4），
     *       中心正好在地表，这里用 {@link HiveStart} 复刻同一口径。</li>
     * </ul>
     *
     * <p>与小虫巢唯一的实现差异：它是在 populate 里直接 {@code setBlock}，
     * 我们走 HBM 的 {@code MapGenStructure} 通道（{@code NBTStructure} + 自定义 Start），
     * 跨区块写方块交给原版生成队列，不会因为邻区块还没加载而少一截。</p>
     *
     * <p>这个结构<b>不接</b>方块劣化与战利品随机化：它的箱子内容、
     * 刷怪笼、stalagmite 全部按 NBT 原样落地。</p>
     */
    private static void registerAlienHive() {

        if(!ModConfig.structureAlienHiveEnabled) {
            hiveDebug("大型异虫巢已在配置中关闭");
            return;
        }

        String nbtPath = normalizeNbtPath(ModConfig.structureAlienHiveNbtFile, HIVE_NBT);

        if(!assetExists(nbtPath)) {
            LOG.warn("[structure-hive] 找不到 assets/" + MakeNTMGreatAgain.MODID + "/" + nbtPath
                    + "，跳过大型异虫巢注册（检查配置 structureAlienHiveNbtFile；"
                    + "用 hbm:tile.wand_structure 导出后放进 assets/"
                    + MakeNTMGreatAgain.MODID + "/structures/ 即可）");
            return;
        }

        // 幂等：HBM 的 HbmWorld.init 已经调过一次，这里只是兜底
        NBTStructure.register();
        // Start 子类必须单独登记，否则存档时查不到 id 会 NPE
        MapGenStructureIO.registerStructure(HiveStart.class, "MNGA_HiveStart");

        NBTStructure structure = new NBTStructure(new ResourceLocation(MakeNTMGreatAgain.MODID, nbtPath));

        SpawnCondition spawn = new SpawnCondition(HIVE_SPAWN_NAME);
        spawn.structure = new JigsawPiece(HIVE_PIECE_NAME, structure, 0);
        spawn.minHeight = ModConfig.structureAlienHiveMinHeight;
        spawn.maxHeight = ModConfig.structureAlienHiveMaxHeight;

        // 每区块独立掷骰（不划格子）——小虫巢就是「每区块 1/hiveSpawn」，这样才是同一口径。
        spawn.checkCoordinates = AddonStructures::isAlienHiveChunk;

        // start 非空 → 走 HiveStart，自己把高度钉到地表。
        // Quartet 顺序是 (World, Random, chunkX, chunkZ)，Random 取 getX()。
        spawn.start = data -> new HiveStart(data.getW(), data.getX(), spawn, data.getY(), data.getZ());

        NBTStructure.registerStructure(ModConfig.structureAlienHiveDimensionId, spawn);

        int denom = alienHiveDenominator();
        int hiveSpawn = Math.max(1, MobConfig.hiveSpawn);
        hiveDebug("大型异虫巢已注册: dim=" + ModConfig.structureAlienHiveDimensionId
                + ", nbt=" + nbtPath
                + ", size=" + structure.getSizeX() + "x" + structure.getSizeY() + "x" + structure.getSizeZ()
                + ", 平均每 " + denom + " 区块一座"
                + "（HBM 小虫巢 " + hiveSpawn + " 区块一座 → 本结构 1/"
                + (denom / hiveSpawn) + "）"
                + ", y=" + spawn.minHeight + "~" + spawn.maxHeight
                + ", offset=" + ModConfig.structureAlienHiveHeightOffset
                + (ModConfig.structureAlienHiveCarveInterior ? ", 内腔挖空开" : ", 内腔挖空关")
                + (ModConfig.structureAlienHiveLootRandomize && ModConfig.structureLootRandomize
                        ? ", 箱子随机化开" : ", 箱子随机化关")
                + (ModConfig.structureAlienHiveFollowHbm ? ", 跟随 HBM 开关" : ", 只看本模组开关"));
    }

    // =====================================================================================
    //  日落六号（第三个结构，下界，每世界仅一个，必须埋在岩浆里且上方不暴露）
    // =====================================================================================

    /**
     * 注册日落六号。
     *
     * <p>生成逻辑复刻前两个遗迹（{@code NBTStructure} + {@code SpawnCondition} + 自定义 Start），
     * 但选址改成「每世界唯一」：</p>
     * <ul>
     *   <li><b>维度</b>：下界（{@code structureSunsetSixDimensionId}，默认 -1）；</li>
     *   <li><b>唯一性</b>：由种子算出唯一锚点（±5000 内），{@link SunsetSixSavedData}
     *       随存档记「已生成」，生成过一次就永久不再触发；</li>
     *   <li><b>选址</b>：锚点列上要求底座起至少 40 格连续岩浆（结构高 30 + 上方 10），
     *       整座浸在岩浆里且上方不暴露；</li>
     *   <li><b>原样</b>：不接方块劣化，{@code build(wipeExisting=false)} 只写 NBT 记录的方块；
     *       NBT 未记录的格子（原建筑的空气/内腔）生成后清成空气，还原空腔，不被岩浆填死；</li>
     *   <li><b>箱子</b>：复用前两个遗迹共用的 {@link #randomizeContainer}（按稀有度掷骰）。</li>
     * </ul>
     */
    private static void registerSunsetSix() {

        if(!ModConfig.structureSunsetSixEnabled) {
            sunsetDebug("日落六号已在配置中关闭");
            return;
        }

        String nbtPath = normalizeNbtPath(ModConfig.structureSunsetSixNbtFile, SUNSET_NBT);

        if(!assetExists(nbtPath)) {
            LOG.warn("[structure-sunset] 找不到 assets/" + MakeNTMGreatAgain.MODID + "/" + nbtPath
                    + "，跳过日落六号注册（检查配置 structureSunsetSixNbtFile；"
                    + "用 hbm:tile.wand_structure 导出后放进 assets/"
                    + MakeNTMGreatAgain.MODID + "/structures/ 即可）");
            return;
        }

        // 幂等：HBM 的 HbmWorld.init 已经调过一次，这里只是兜底
        NBTStructure.register();
        // Start 子类必须单独登记，否则存档时查不到 id 会 NPE
        MapGenStructureIO.registerStructure(SunsetStart.class, "MNGA_SunsetStart");

        NBTStructure structure = new NBTStructure(new ResourceLocation(MakeNTMGreatAgain.MODID, nbtPath));

        SpawnCondition spawn = new SpawnCondition(SUNSET_SPAWN_NAME);
        // 固定朝向 0（在 SunsetStart 里反射钉死），不接劣化/挖空，原样落地
        spawn.structure = new JigsawPiece(SUNSET_PIECE_NAME, structure, 0);
        spawn.minHeight = ModConfig.structureSunsetSixMinHeight;
        spawn.maxHeight = ModConfig.structureSunsetSixMaxHeight;

        // 每区块独立判定：只有「种子锚点区块 + 尚未生成 + 岩浆浸没」三条全满足才放行
        spawn.checkCoordinates = AddonStructures::isSunsetSixChunk;

        spawn.start = data -> new SunsetStart(data.getW(), data.getX(), spawn, data.getY(), data.getZ());

        NBTStructure.registerStructure(ModConfig.structureSunsetSixDimensionId, spawn);

        sunsetDebug("日落六号已注册: dim=" + ModConfig.structureSunsetSixDimensionId
                + ", nbt=" + nbtPath
                + ", size=" + structure.getSizeX() + "x" + structure.getSizeY() + "x" + structure.getSizeZ()
                + ", 每世界仅一个, 半径=" + SunsetSixSite.RANGE
                + ", y=" + spawn.minHeight + "~" + spawn.maxHeight
                + ", 箱子随机化=" + (ModConfig.structureSunsetSixLootRandomize && ModConfig.structureLootRandomize
                        ? "on" : "off"));
    }

    /**
     * 该区块是否要生成日落六号。判定顺序（全部满足才放行）：
     *
     * <ol>
     *   <li>总开关 + 维度匹配；</li>
     *   <li>本世界尚未生成过（{@link SunsetSixSavedData}，随存档持久化）；</li>
     *   <li>当前区块就是种子算出的唯一锚点区块（{@link SunsetSixSite#anchor}）；</li>
     *   <li>锚点处能把整座结构埋进岩浆、且上方不暴露（{@link #findSunsetBaseY}）。</li>
     * </ol>
     *
     * <p><b>为什么必须是纯函数式判定</b>：{@code canSpawnStructureAtCoords} 对同一区块会被
     * 反复询问，答案必须永远一致。这里只读种子与存档标记，不抽 {@code pos.rand}，
     * 不会连带改掉结构朝向。</p>
     */
    private static boolean isSunsetSixChunk(SpawnCondition.WorldCoordinate pos) {

        if(!ModConfig.structureSunsetSixEnabled) return false;

        World world = pos.world;
        if(world.provider.dimensionId != ModConfig.structureSunsetSixDimensionId) return false;

        // 每世界仅一个：生成过就永久不再触发
        if(SunsetSixSavedData.get(world).generated) return false;

        int cx = pos.coords.chunkXPos;
        int cz = pos.coords.chunkZPos;

        // 测试模式：固定刷在 (0,0) 区块，跳过岩浆深度校验，方便进游戏直接验证。
        // 关掉 structureSunsetSixTestMode 即恢复「种子随机锚点 + 严格岩浆浸没」的正式逻辑。
        if(ModConfig.structureSunsetSixTestMode) {
            return cx == 0 && cz == 0;
        }

        // 唯一锚点（区块角点，16 对齐）→ 换算成区块坐标
        int[] anchor = SunsetSixSite.anchor(world.getWorldInfo().getSeed(), world.provider.dimensionId);
        if(cx != (anchor[0] >> 4) || cz != (anchor[1] >> 4)) return false;

        // 岩浆浸没 + 上方覆盖：在锚点列上找一个能埋下整座结构的底座高度
        return findSunsetBaseY(world, anchor[0], anchor[1]) >= 0;
    }

    /**
     * 在锚点列上找一个能「埋进岩浆且上方不暴露」的底座高度。
     *
     * <p>从 {@code structureSunsetSixMinHeight} 到 {@code structureSunsetSixMaxHeight} 逐层试，
     * 取第一个满足 {@link SunsetSixSite#isBuriedInLava} 的 y。找不到返回 -1（该世界本次不生成，
     * 但锚点不变，等地形/岩浆变化后仍可能在同一格生成）。</p>
     *
     * <p>列编码与世界方块解耦：取样 {@code [0, 256)} 每一格，岩浆记 1、不透明实心记 2、其余记 0。
     * 结构高 {@link SunsetSixSite#STRUCT_HEIGHT}，顶部之上至少 {@link SunsetSixSite#MIN_COVER} 格覆盖。</p>
     */
    private static int findSunsetBaseY(World world, int x, int z) {

        int height = SunsetSixSite.STRUCT_HEIGHT;
        int[] column = new int[256];
        for(int y = 0; y < 256; y++) {
            Block block = world.getBlock(x, y, z);
            if(block == Blocks.lava || block == Blocks.flowing_lava) {
                column[y] = SunsetSixSite.LAVA;
            } else if(block.isOpaqueCube()) {
                column[y] = SunsetSixSite.SOLID;
            } else {
                column[y] = SunsetSixSite.AIR;
            }
        }

        int min = Math.max(1, ModConfig.structureSunsetSixMinHeight);
        int max = Math.min(256 - height - SunsetSixSite.MIN_COVER, ModConfig.structureSunsetSixMaxHeight);
        for(int baseY = min; baseY <= max; baseY++) {
            if(SunsetSixSite.isBuriedInLava(column, baseY, height)) return baseY;
        }
        return -1;
    }

    /** 日落六号的日志出口，前缀 {@code [structure-sunset]}，默认开（每世界仅一个，日志极少量）。 */
    private static void sunsetDebug(String message) {
        if(ModConfig.structureSunsetSixDebugLog) {
            LOG.info("[structure-sunset] " + message);
        }
    }

    /**
     * 分母：平均多少区块一座。
     *
     * <p>优先用 {@code structureAlienHiveDenominator}（写死）；为 -1 时跟随 HBM 的
     * {@code MobConfig.hiveSpawn}（默认 256）× {@code structureAlienHiveRarityMultiplier}
     * （默认 20）= 5120。跟随的好处是：玩家改了 HBM 的虫巢密度，
     * 这个结构会自动保持「小虫巢的 1/20」，不用再动本模组的配置。</p>
     */
    private static int alienHiveDenominator() {

        if(ModConfig.structureAlienHiveDenominator > 0) return ModConfig.structureAlienHiveDenominator;

        int hiveSpawn = Math.max(1, MobConfig.hiveSpawn);
        int mult = Math.max(1, ModConfig.structureAlienHiveRarityMultiplier);
        long denom = (long) hiveSpawn * mult;
        return (int) Math.min(Integer.MAX_VALUE, denom);
    }

    /**
     * 该区块是否要生成大型异虫巢。
     *
     * <p>判定顺序：总开关 → HBM 前置开关 → 群系过滤 → 逐区块掷骰。</p>
     *
     * <p><b>为什么掷骰用 hash 而不是 {@code pos.rand}</b>：同一个区块会被
     * {@code canSpawnStructureAtCoords} 反复询问，答案必须永远一致；
     * 并且 {@code pos.rand} 与 {@code getStructureStart} 共用随机流，
     * 在这里抽一次会连带改掉结构的朝向（小虫巢没有朝向问题，但 NBT 结构有）。</p>
     */
    private static boolean isAlienHiveChunk(SpawnCondition.WorldCoordinate pos) {

        if(!ModConfig.structureAlienHiveEnabled) return false;

        World world = pos.world;

        // 复刻 HBM 的前置条件：地牢开关 + 虫巢开关。
        // 关掉跟随之后只看本模组自己的开关，但「1/20」这个比例就不再成立了。
        if(ModConfig.structureAlienHiveFollowHbm) {
            if(!MobConfig.enableHives) return false;
            if(!dungeonsEnabled(world)) return false;
        }

        int cx = pos.coords.chunkXPos;
        int cz = pos.coords.chunkZPos;

        // 群系过滤：默认全放开（小虫巢本身不筛群系），只在玩家主动收窄时生效。
        boolean needBiome = !ModConfig.structureAlienHiveAllowOcean
                || ModConfig.structureAlienHiveMaxHeightVariation < 2.0F;
        if(needBiome) {
            BiomeGenBase biome = world.getBiomeGenForCoords(cx * 16 + 8, cz * 16 + 8);
            if(biome == null) return false;
            if(!ModConfig.structureAlienHiveAllowOcean) {
                if(BiomeDictionary.isBiomeOfType(biome, Type.WATER)) return false;
                if(BiomeDictionary.isBiomeOfType(biome, Type.OCEAN)) return false;
            }
            if(biome.heightVariation > ModConfig.structureAlienHiveMaxHeightVariation) return false;
        }

        int denominator = alienHiveDenominator();
        if(denominator <= 0) return false;

        long h = world.getWorldInfo().getSeed();
        h ^= (long) cx * 0x9E3779B97F4A7C15L;
        h ^= (long) cz * 0xC2B2AE3D27D4EB4FL;
        h ^= (long) world.provider.dimensionId * 0xD6E8FEB86659FD93L;
        h ^= 0x5D2F1A3C7B9E4D61L;   // salt：和地心实验室的落点 hash 区分开
        h = mix(h);

        return Math.floorMod(h, (long) denominator) == 0;
    }

    /**
     * HBM 的地牢总开关（原文见 {@code HbmWorldGen}）：
     * 默认跟着存档的「生成建筑」选项走，{@code GeneralConfig.enableDungeons}
     * 为 1 / 0 时强制开 / 关。
     */
    private static boolean dungeonsEnabled(World world) {

        boolean enable = world.getWorldInfo().isMapFeaturesEnabled();
        if(GeneralConfig.enableDungeons == 1) enable = true;
        else if(GeneralConfig.enableDungeons == 0) enable = false;
        return enable;
    }

    /** murmur3 收尾混合，把任意 hash 打散成均匀分布（概率门和落点都用它）。 */
    private static long mix(long h) {

        h ^= (h >>> 33);
        h *= 0xFF51AFD7ED558CCDL;
        h ^= (h >>> 33);
        h *= 0xC4CEB9FE1A85EC53L;
        h ^= (h >>> 33);
        return h;
    }

    /**
     * 配置里的路径 → 资源路径：空值回退默认，反斜杠统一成正斜杠，去掉前导斜杠。
     */
    private static String normalizeNbtPath(String configured, String fallback) {

        String path = configured;
        if(path == null || path.trim().isEmpty()) path = fallback;
        path = path.trim().replace('\\', '/');
        while(path.startsWith("/")) path = path.substring(1);
        return path;
    }

    /** 大型异虫巢的日志出口，前缀 {@code [structure-hive]}，默认关。 */
    private static void hiveDebug(String message) {

        if(ModConfig.structureAlienHiveDebugLog) {
            LOG.info("[structure-hive] " + message);
        }
    }

    /**
     * 平均多少区块才有一座遗迹 = 格子面积 ÷ 生成概率。只用于日志。
     */
    private static int averageDistance() {

        int cells = Math.max(1, ModConfig.structureGridChunks * ModConfig.structureGridChunks);
        int chance = MathHelper.clamp_int(SPAWN_CHANCE_PERCENT, 1, 100);
        return Math.max(1, Math.round(cells * 100F / chance));
    }

    /**
     * 该群系是否允许生成。三个条件全部来自配置：
     *
     * <ul>
     *   <li>{@link ModConfig#structureAllowOcean}：关着时 WATER / OCEAN 两类群系直接排除；</li>
     *   <li>{@link ModConfig#structureMaxHeightVariation}：地形起伏上限，
     *       默认 0.35（原版「平缓」的口径），山地与大峭壁会被挡掉；</li>
     *   <li>群系本身为 null（理论上不会发生）一律拒绝。</li>
     * </ul>
     *
     * <p>注意 {@code Type.RIVER} / {@code Type.BEACH} 不在 WATER 里，想要连河滩一起避开
     * 需要自己加判断；HBM 的废墟也是只挡 WATER + OCEAN，这里保持同一口径。</p>
     */
    private static boolean isValidBiome(BiomeGenBase biome) {

        if(biome == null) return false;

        if(!ModConfig.structureAllowOcean) {
            if(BiomeDictionary.isBiomeOfType(biome, Type.WATER)) return false;
            if(BiomeDictionary.isBiomeOfType(biome, Type.OCEAN)) return false;
        }

        return biome.heightVariation <= ModConfig.structureMaxHeightVariation;
    }

    /**
     * 自定义生成间距：把世界切成 {@code spacing × spacing} 区块的格子，每格由
     * 「世界种子 + 格子坐标」算 hash 决定唯一落点，所以同一个存档问多少次答案都一样，
     * 也不会像纯随机那样两座实验室贴在一起。
     *
     * <p>密度是两级相乘的：</p>
     * <ol>
     *   <li><b>网格</b>：{@link ModConfig#structureGridChunks} = N → 每 N² 区块一个格子；</li>
     *   <li><b>概率</b>：{@link #SPAWN_CHANCE_PERCENT} → 命中落点的格子还有多少比例真的生成。</li>
     * </ol>
     * <p>默认 11 区块 × 50% → 平均约 242 区块一座。只想降密度就改这个百分比，
     * 不用动配置、也不用改格距（改格距会让建筑更容易贴脸）。</p>
     *
     * <p><b>注意</b>：走这条通道时 HBM 不再调用 {@code canSpawn}，
     * 所以生物群系过滤必须在函数里自己做一遍。</p>
     */
    private static boolean isSpawnChunk(SpawnCondition.WorldCoordinate pos) {

        int spacing = Math.max(1, ModConfig.structureGridChunks);
        int cx = pos.coords.chunkXPos;
        int cz = pos.coords.chunkZPos;

        int gx = Math.floorDiv(cx, spacing);
        int gz = Math.floorDiv(cz, spacing);

        // 混一个稳定的 hash：世界种子 ⊕ 格子坐标 ⊕ 维度
        long h = pos.world.getWorldInfo().getSeed();
        h ^= (long) gx * 0x9E3779B97F4A7C15L;
        h ^= (long) gz * 0xC2B2AE3D27D4EB4FL;
        h ^= (long) pos.world.provider.dimensionId * 0xD6E8FEB86659FD93L;
        h = mix(h);

        int ox = baseOffset(spacing, h);
        int oz = baseOffset(spacing, h >>> 32);

        if(cx != gx * spacing + ox || cz != gz * spacing + oz) return false;

        // 命中落点 ≠ 一定生成：再掷一次骰子。SPAWN_CHANCE_PERCENT = 100 时这里直接放行。
        if(!rollSpawnChance(h)) return false;

        return isValidBiome(pos.world.getBiomeGenForCoords(cx * 16 + 8, cz * 16 + 8));
    }

    /**
     * 「降密度」的那一步：用落点 hash 再混一个常量掷骰，返回是否真的生成。
     *
     * <p><b>为什么必须是纯函数、不能碰 {@code pos.rand}</b>：{@code canSpawnStructureAtCoords}
     * 对同一个区块会被反复询问（生成、查询、存档校验各一次），答案一旦不一致，
     * 结构就会重复生成或者凭空消失。而 {@code pos.rand} 与后续
     * {@code getStructureStart} 共用同一条随机流，在这里抽一次会连带改掉所有建筑的朝向，
     * 以后排查「建筑为什么转了」会非常难受。hash 版本零副作用、跨客户端/服务端一致。</p>
     */
    private static boolean rollSpawnChance(long h) {

        if(SPAWN_CHANCE_PERCENT >= 100) return true;
        if(SPAWN_CHANCE_PERCENT <= 0) return false;

        // 换成另一个常量再哈希一次，让「概率门」和「落点」互不相关，
        // 否则会出现「只有西半边生成」这类分布偏斜。
        long x = mix(h ^ 0x2545F4914F6CDD1DL);

        return Math.floorMod(x, 100L) < SPAWN_CHANCE_PERCENT;
    }

    /**
     * 格子内的落点。留边量取自 {@link ModConfig#structureGridInset}，默认 2 区块：
     * 结构本体约 3×3 区块，落点若允许贴边，相邻两格的建筑会互相切进去。
     *
     * <p>留边 {@code i} 之后，可选范围是格子的 {@code [i, spacing-1-i]}，
     * 相邻两点的最小间距是 {@code spacing - (span - 1)}（{@code span = spacing - 2i}）。
     * 默认 spacing=11 / i=2 → span=7，最小间距 5 区块 = 80 格。</p>
     *
     * <p>留边被 {@link ModConfig} 的自愈逻辑限制在 {@code (spacing-1)/2} 以内，
     * 这里再 clamp 一次，防止有人手工改 cfg 绕过去把 {@code span} 压成 0。</p>
     */
    private static int baseOffset(int spacing, long hash) {

        int inset = MathHelper.clamp_int(ModConfig.structureGridInset, 0, Math.max(0, (spacing - 1) / 2));

        int span = Math.max(1, spacing - inset * 2);
        int base = Math.max(0, (spacing - span) / 2);
        return base + (int) Math.floorMod(hash, (long) span);
    }

    /** 附魔台之外的资源探测：直接问 classpath，开发环境与打包后都有效。 */
    private static boolean assetExists(String path) {
        return AddonStructures.class.getResource("/assets/" + MakeNTMGreatAgain.MODID + "/" + path) != null;
    }

    /**
     * 详细日志的唯一出口，统一加 {@code [structure] } 前缀。
     *
     * <p>受 {@link ModConfig#structureDebugLog} 控制，<b>默认关</b>——
     * 这些信息是给「遗迹到底生成了没有 / 劣化和箱子随机化有没有生效」排查用的，
     * 正常游玩时只会刷屏。需要时把 cfg 里的 {@code B:structureDebugLog} 改成 true 重启即可。</p>
     *
     * <p>WARN / ERROR 不走这里：文件缺失、配置越界这类问题必须始终可见。</p>
     */
    private static void debug(String message) {
        if(ModConfig.structureDebugLog) {
            LOG.info("[structure] " + message);
        }
    }

    /* ==================================================================================
     *  生成后的填充
     * ================================================================================== */

    /**
     * 在「本次 box ∩ 结构包围盒」的范围内做生成后处理：填箱子、给机器注入随机内容。
     *
     * <p>调用点见 {@code RuinStart#generateStructure}。范围必须同时满足两个条件：</p>
     * <ul>
     *   <li><b>X/Z 取交集</b>：结构跨区块时 {@code generateStructure} 会被每个区块各调一次，
     *       扫全图会让同一个箱子被反复抽奖。原版给的 box 是「区块中心 ±8」的 16×16，
     *       相邻区块首尾相接，所以每个方块格子恰好被扫到一次。</li>
     *   <li><b>Y 取组件包围盒</b>：区块包围盒的 Y 是 1~512 的兜底值，直接用会白扫 250 层。</li>
     * </ul>
     *
     * @return {@code {劣化方块数, 随机化的箱子数}}，只用来打日志
     */
    static int[] afterGenerate(World world, Random rand,
                              int minX, int maxX, int minZ, int maxZ, int minY, int maxY) {

        int fromY = Math.max(1, minY);
        int toY = Math.min(255, maxY);

        int decayed = 0;
        int containers = 0;

        for(int x = minX; x <= maxX; x++) {
            for(int z = minZ; z <= maxZ; z++) {
                for(int y = fromY; y <= toY; y++) {

                    Block block = world.getBlock(x, y, z);
                    if(block == Blocks.air) continue;

                    // 劣化：命中的方块直接换掉，换完就不再参与战利品扫描
                    if(applyDecay(world, rand, x, y, z, block)) {
                        decayed++;
                        continue;
                    }

                    // 墙/地板占绝大多数，先用 hasTileEntity 挡掉，省下 90% 的 getTileEntity 查找
                    if(!block.hasTileEntity(world.getBlockMetadata(x, y, z))) continue;

                    TileEntity te = world.getTileEntity(x, y, z);
                    if(te == null) continue;

                    if(isLootContainer(block) && te instanceof IInventory) {
                        if(ModConfig.structureLootRandomize) {
                            randomizeContainer((IInventory) te, rand);
                            containers++;
                        }
                    } else {
                        applyMachineContent(world, rand, x, y, z, te);
                    }
                }
            }
        }

        return new int[] {decayed, containers};
    }

    /**
     * 按 {@link #DECAY_TABLE} 判定一次。命中并成功换块返回 true。
     *
     * <p>顺序判定、命中即停：先掷第一条，没中再掷下一条。</p>
     *
     * <p>概率不是写死的常量，而是 {@code 表里的 chance × 配置倍率}：</p>
     * <ul>
     *   <li>{@link ModConfig#structureDecayEnabled} 为 false 时整段跳过；</li>
     *   <li>{@link ModConfig#structureDecayScalePercent} 是百分比的倍率（100 = 原值，
     *       0 = 等于关掉，200 = 概率翻倍）。</li>
     * </ul>
     */
    private static boolean applyDecay(World world, Random rand, int x, int y, int z, Block block) {

        if(!ModConfig.structureDecayEnabled) return false;

        Decay rule = findDecay(block);
        if(rule == null) return false;

        float scale = Math.max(0, ModConfig.structureDecayScalePercent) / 100F;
        if(scale <= 0F) return false;

        for(Outcome outcome : rule.outcomes) {
            // 乘上倍率后再判；超过 100 就是「必定命中」，不会溢出成负数
            if(rand.nextFloat() * 100F >= outcome.chance * scale) continue;

            Block target = outcome.to == null ? Blocks.air : outcome.to;
            // flag 2 = 只同步给客户端；光照不用自己补，World#setBlock 内部
            // （checkLight / func_147451_t）已经重算过了。
            world.setBlock(x, y, z, target, outcome.meta, 2);

            return true;
        }

        return false;
    }

    private static Decay findDecay(Block block) {
        for(Decay rule : DECAY_TABLE) {
            if(rule.from == block) return rule;
        }
        return null;
    }

    private static boolean isLootContainer(Block block) {
        for(Block candidate : LOOT_CONTAINERS) {
            if(block == candidate) return true;
        }
        return false;
    }

    /* ==================================================================================
     *  战利品随机化
     * ================================================================================== */

    /**
     * 把容器里「导出时摆好的内容」当成模板，重新掷一遍：
     *
     * <ol>
     *   <li>逐格判定：按 {@link #rarityOf(ItemStack)} 的稀有度和这一格原有的数量，
     *       决定这件物品留不留下（各档保留率来自配置 {@code structureLootKeep*}）；</li>
     *   <li>留下的随机一个新数量（{@code structureLootCountMinPercent} ~ 原数量）；</li>
     *   <li>整箱清空，按 {@code structureLootScatter} 决定「随机塞进空格」还是「从第 0 格顺序摆」。</li>
     * </ol>
     *
     * <p>用 {@code ItemStack.copy()} 迁移，物品自带的 NBT（枪的弹药、护甲的充能、
     * 电池的电量、成书的正文）全部跟着走。</p>
     *
     * <p>整个方法的入口还有一道 {@code ModConfig.structureLootRandomize} 开关，
     * 关掉时根本不进这里，箱子保持结构 NBT 里的原样。</p>
     */
    private static void randomizeContainer(IInventory inv, Random rand) {

        int size = inv.getSizeInventory();
        if(size <= 0) return;

        List<ItemStack> kept = new ArrayList<ItemStack>();

        for(int i = 0; i < size; i++) {
            ItemStack stack = inv.getStackInSlot(i);
            if(stack == null || stack.stackSize <= 0) continue;

            // 成书带正文 NBT，属于剧情物品：原样留下，不掷骰、不改数量
            if(stack.getItem() == Items.written_book) {
                kept.add(stack.copy());
                continue;
            }

            Rarity rarity = rarityOf(stack);
            if(rand.nextFloat() * 100F >= rarity.keepChance(stack.stackSize)) continue;

            ItemStack copy = stack.copy();
            if(copy.getMaxStackSize() > 1) {
                copy.stackSize = rerollCount(stack.stackSize, rand);
            }
            kept.add(copy);
        }

        for(int i = 0; i < size; i++) {
            inv.setInventorySlotContents(i, null);
        }

        if(ModConfig.structureLootScatter) {
            // 打乱顺序再放，配合「随机挑空格」，位置分布才真的是散的
            Collections.shuffle(kept, rand);
            for(ItemStack stack : kept) {
                placeScattered(inv, stack, rand);
            }
        } else {
            // 不打散：从第 0 格起顺序摆放，位置与模板一致，只有「留不留 / 几个」是随机的
            for(ItemStack stack : kept) {
                placeInOrder(inv, stack);
            }
        }
    }

    /**
     * 原数量的「配置下限百分比 ~ 原数量」，至少 1。
     *
     * <p>下限取自 {@link ModConfig#structureLootCountMinPercent}（默认 40%）。
     * 模板里只放 1 个的物品（枪、护甲、书）直接返回 1，不走这条。</p>
     */
    private static int rerollCount(int original, Random rand) {
        if(original <= 1) return 1;
        int percent = MathHelper.clamp_int(ModConfig.structureLootCountMinPercent, 0, 100);
        int min = Math.max(1, original * percent / 100);
        if(min > original) min = original;
        return min + rand.nextInt(original - min + 1);
    }

    /**
     * 把一个 stack 塞进<b>随机</b>空格；超过单格上限就拆成几堆，各自再随机找空格。
     * 箱子满了就把剩下的丢掉（结构不会为此回滚）。
     */
    private static void placeScattered(IInventory inv, ItemStack stack, Random rand) {

        if(stack == null || stack.stackSize <= 0) return;

        int limit = Math.min(inv.getInventoryStackLimit(), stack.getMaxStackSize());
        int remaining = stack.stackSize;

        while(remaining > 0) {
            int slot = randomEmptySlot(inv, rand);
            if(slot < 0) return;

            ItemStack copy = stack.copy();
            copy.stackSize = Math.min(remaining, limit);
            inv.setInventorySlotContents(slot, copy);
            remaining -= copy.stackSize;
        }
    }

    /**
     * 从第 0 格开始顺序摆放，超过单格上限就拆成几堆，箱子满了丢掉剩余。
     *
     * <p>只有 {@link ModConfig#structureLootScatter} 关掉时才会走到这里：
     * 物品保留模板里的相对位置（第几格还是第几格），区别只在于「留不留 / 几个」是掷出来的。</p>
     */
    private static void placeInOrder(IInventory inv, ItemStack stack) {

        if(stack == null || stack.stackSize <= 0) return;

        int limit = Math.min(inv.getInventoryStackLimit(), stack.getMaxStackSize());
        int size = inv.getSizeInventory();
        int remaining = stack.stackSize;

        for(int slot = 0; slot < size && remaining > 0; slot++) {
            if(inv.getStackInSlot(slot) != null) continue;

            ItemStack copy = stack.copy();
            copy.stackSize = Math.min(remaining, limit);
            inv.setInventorySlotContents(slot, copy);
            remaining -= copy.stackSize;
        }
    }

    /** 随机返回一个空格的下标；没有空格返回 -1。 */
    private static int randomEmptySlot(IInventory inv, Random rand) {

        int empty = 0;
        int size = inv.getSizeInventory();
        for(int i = 0; i < size; i++) {
            if(inv.getStackInSlot(i) == null) empty++;
        }
        if(empty <= 0) return -1;

        int pick = rand.nextInt(empty);
        for(int i = 0; i < size; i++) {
            if(inv.getStackInSlot(i) != null) continue;
            if(pick-- == 0) return i;
        }
        return -1;
    }

    /** 物品的稀有度。附魔金苹果按 meta 单独判（本体是 UNCOMMON，附魔版是 LEGENDARY）。 */
    private static Rarity rarityOf(ItemStack stack) {
        if(stack.getItem() == Items.golden_apple && stack.getItemDamage() == 1) return Rarity.LEGENDARY;
        Rarity rarity = ITEM_RARITY.get(stack.getItem());
        return rarity != null ? rarity : Rarity.COMMON;
    }

    /** 批量登记稀有度，{@code entries} 可以混着 {@link Item} 和 {@link Block}。 */
    private static void rate(Rarity rarity, Object... entries) {
        for(Object entry : entries) {
            Item item = entry instanceof Block ? Item.getItemFromBlock((Block) entry) : (Item) entry;
            if(item != null) ITEM_RARITY.put(item, rarity);
        }
    }

    /**
     * 机器内容的「每次随机」钩子。默认什么都不做——机器的物品与流体完全由结构 NBT 决定。
     *
     * <p>想随机化就在下面按 TE 类型写分支，例如：</p>
     * <pre>
     * if(te instanceof TileEntityMachineCentrifuge) {
     *     TileEntityMachineCentrifuge machine = (TileEntityMachineCentrifuge) te;
     *     machine.tanks[0].setTankType(Fluids.WATER);
     *     machine.tanks[0].setFill(4000 + rand.nextInt(8000));
     *     te.markDirty();
     * }
     * </pre>
     * <p>注意 {@code setTankType} 会清空存量，所以要先设类型再设量；
     * 类型没变时可以跳过，否则会把结构里预置的液体清掉。</p>
     */
    static void applyMachineContent(World world, Random rand, int x, int y, int z, TileEntity te) {
        // 默认不处理
    }

    /* ==================================================================================
     *  大型异虫巢：内腔几何（占据网格 + 泛洪填充）
     * ================================================================================== */

    /** 反射缓存：{@code NBTStructure$Component.piece}（包私有）。 */
    private static Field fieldComponentPiece;
    /** 反射缓存：HBM 补丁版 {@code StructureComponent.coordBaseMode}（protected int）。 */
    private static Field fieldComponentMode;

    /** 虫巢 NBT 的局部「内腔」格子（true = 该格清成空气）。缓存与 NBT 路径绑定。 */
    private static boolean[][][] hiveInterior;
    private static String hiveInteriorPath;

    /**
     * 解析虫巢 NBT，算出哪些格子属于「封闭内腔」。
     *
     * <p>原理：NBT 只记录实体方块（本虫巢 31×13×31 里记了 2138 格，其余 10355 格是空的）。
     * 从包围盒<b>边界</b>上所有空格出发做 6 邻域泛洪填充，能走到的空格是「外部」
     * （壳体与地形之间的缝隙，维持地形）；走不到的空格就是「内部」——自带虫巢是个
     * 完全封闭的穹顶，内部共 3614 格。</p>
     *
     * <p>这一步在本地坐标系里做，与旋转、世界位置无关；落到世界时由
     * {@code HiveStart} 按 HBM build 同款的 {@code rotateX/rotateZ} 映射过去。</p>
     *
     * @return 内腔格子矩阵 {@code [x][y][z]}；NBT 读不到或解析失败返回 null（跳过挖空）。
     */
    private static boolean[][][] getHiveInterior(String nbtPath) {

        String path = nbtPath == null ? "" : nbtPath.trim().replace('\\', '/');
        while(path.startsWith("/")) path = path.substring(1);

        if(hiveInterior != null && path.equals(hiveInteriorPath)) return hiveInterior;

        boolean[][][] result = null;

        // 与 NBTStructure 自己的加载方式一致：从 classpath 上的 assets 目录读
        InputStream in = AddonStructures.class.getResourceAsStream(
                "/assets/" + MakeNTMGreatAgain.MODID + "/" + path);
        if(in == null) {
            LOG.warn("[structure-hive] 找不到结构 NBT（assets/" + MakeNTMGreatAgain.MODID + "/" + path
                    + "），内腔挖空与箱子随机化跳过");
            return null;
        }

        try {
            NBTTagCompound data = CompressedStreamTools.readCompressed(in);

            // 尺寸
            NBTTagList sizeList = data.getTagList("size", NBT.TAG_INT);
            int sx = ((NBTTagInt) sizeList.tagList.get(0)).func_150287_d();
            int sy = ((NBTTagInt) sizeList.tagList.get(1)).func_150287_d();
            int sz = ((NBTTagInt) sizeList.tagList.get(2)).func_150287_d();

            // 调色板：名字 → 方块。解析不出来的名字当实心处理——宁可少挖一格，不可把壳挖穿。
            NBTTagList paletteList = data.getTagList("palette", NBT.TAG_COMPOUND);
            Block[] palette = new Block[paletteList.tagCount()];
            for(int i = 0; i < paletteList.tagCount(); i++) {
                String name = paletteList.getCompoundTagAt(i).getString("Name");
                Block block = Block.getBlockFromName(name);
                palette[i] = block != null ? block : Blocks.stone;
            }

            // 占据网格
            boolean[][][] occupied = new boolean[sx][sy][sz];
            NBTTagList blocksList = data.getTagList("blocks", NBT.TAG_COMPOUND);
            for(int i = 0; i < blocksList.tagCount(); i++) {
                NBTTagCompound block = blocksList.getCompoundTagAt(i);
                int state = block.getInteger("state");
                if(state < 0 || state >= palette.length) continue;
                if(palette[state] == Blocks.air) continue;   // 显式空气 = 空腔的一种

                NBTTagList pos = block.getTagList("pos", NBT.TAG_INT);
                int x = ((NBTTagInt) pos.tagList.get(0)).func_150287_d();
                int y = ((NBTTagInt) pos.tagList.get(1)).func_150287_d();
                int z = ((NBTTagInt) pos.tagList.get(2)).func_150287_d();
                if(x < 0 || x >= sx || y < 0 || y >= sy || z < 0 || z >= sz) continue;
                occupied[x][y][z] = true;
            }

            // 从边界泛洪：走得到的是「外部」，走不到的空格是「内腔」
            boolean[][][] exterior = new boolean[sx][sy][sz];
            ArrayDeque<int[]> queue = new ArrayDeque<int[]>();
            for(int x = 0; x < sx; x++) {
                for(int y = 0; y < sy; y++) {
                    for(int z = 0; z < sz; z++) {
                        if(occupied[x][y][z]) continue;
                        if(x > 0 && x < sx - 1 && y > 0 && y < sy - 1 && z > 0 && z < sz - 1) continue;
                        exterior[x][y][z] = true;
                        queue.add(new int[] {x, y, z});
                    }
                }
            }

            int[][] dirs = { {1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1} };
            while(!queue.isEmpty()) {
                int[] p = queue.poll();
                for(int[] d : dirs) {
                    int nx = p[0] + d[0], ny = p[1] + d[1], nz = p[2] + d[2];
                    if(nx < 0 || nx >= sx || ny < 0 || ny >= sy || nz < 0 || nz >= sz) continue;
                    if(occupied[nx][ny][nz] || exterior[nx][ny][nz]) continue;
                    exterior[nx][ny][nz] = true;
                    queue.add(new int[] {nx, ny, nz});
                }
            }

            result = new boolean[sx][sy][sz];
            int count = 0;
            for(int x = 0; x < sx; x++) {
                for(int y = 0; y < sy; y++) {
                    for(int z = 0; z < sz; z++) {
                        if(!occupied[x][y][z] && !exterior[x][y][z]) {
                            result[x][y][z] = true;
                            count++;
                        }
                    }
                }
            }

            LOG.info("[structure-hive] 内腔几何解析完成: " + path + " 尺寸 " + sx + "x" + sy + "x" + sz
                    + "，封闭内腔 " + count + " 格");

        } catch(Exception e) {
            LOG.warn("[structure-hive] 解析虫巢 NBT 失败，内腔挖空跳过: " + e);
        } finally {
            try { in.close(); } catch(Exception ignored) { }
        }

        // 只有解析成功才缓存：失败时返回 null 但不记账，下次还会重试
        //（与缺文件分支行为一致；缓存 null 会让失败永久化）
        if(result != null) {
            hiveInterior = result;
            hiveInteriorPath = path;
        }
        return result;
    }

    /* ==================================================================================
     *  大型异虫巢的自定义 StructureStart
     * ================================================================================== */

    /**
     * 只做一件事：把结构的<b>几何中心</b>钉到中心列的地表高度上。
     *
     * <p>这就是 HBM 小虫巢的口径——它按 {@code iY = y + j - 2}（j=0..4）摆方块，
     * 中心正好落在地表，于是地面上鼓起一个小土包、地下埋着巢室。</p>
     *
     * <p>为什么不直接用 HBM 自带的 {@code averageHeight + heightOffset}：
     * 那边取的是整个包围盒（31×31）的地表<b>平均值</b>，跨山谷/悬崖时会被拉平，
     * 虫巢一半悬空一半埋死。小虫巢用的是单点高度，这里保持一致。</p>
     */
    public static class HiveStart extends NBTStructure.Start {

        /** 是否已经定过高。只用于避免重复 offset（读档后为 false，但 Component 自己的标记会兜底）。 */
        private boolean heightPinned;

        /** 反序列化用（{@code MapGenStructureIO} 会反射 new 无参构造）。 */
        public HiveStart() {
        }

        public HiveStart(World world, Random rand, SpawnCondition spawn, int chunkX, int chunkZ) {
            super(world, rand, spawn, chunkX, chunkZ);
        }

        @Override
        public void generateStructure(World world, Random rand, StructureBoundingBox box) {

            // 必须在 super 之前：HBM 的高度判定发生在 Component.addComponentParts 里，
            // 而我们调过 offsetYHeight 之后组件会被标记成「高度已确定」，那边就不再动它。
            if(!heightPinned) {
                heightPinned = true;
                pinHeightToSurface(world);
            }

            super.generateStructure(world, rand, box);

            processAfterBuild(world, rand, box);
        }

        /**
         * 生成后处理：内腔挖空 + 箱子战利品随机化。
         *
         * <p>HBM 的 build 对 NBT 里<b>没记录</b>的格子是直接跳过的
         * （{@code if(state == null) continue}），所以只记录实体方块的虫巢 NBT
         * 落地后，内部巢室的空腔会被天然地形原样填死。这里补两件事：</p>
         *
         * <ol>
         *   <li><b>内腔挖空</b>：从结构包围盒边界做泛洪填充，「与外界完全隔离」的空腔
         *       清成空气；壳体与地形之间的缝隙属于「外部」，维持地形不动（配置允许）。</li>
         *   <li><b>箱子随机化</b>：和地心实验室同一套模板掷骰（{@link #randomizeContainer}），
         *       保留率、散布等参数共用 {@code structureLoot*} 配置。</li>
         * </ol>
         *
         * <p>和 {@link RuinStart} 一样按「box ∩ 结构包围盒」裁剪：区块中心的 box
         * （±8）首尾相接铺满整个平面，每个格子恰好被处理一次，跨区块不会漏也不会重。</p>
         */
        private void processAfterBuild(World world, Random rand, StructureBoundingBox box) {

            List<?> comps = getComponents();
            if(comps.isEmpty() || !(comps.get(0) instanceof StructureComponent)) return;
            StructureComponent component = (StructureComponent) comps.get(0);

            StructureBoundingBox bb = component.getBoundingBox();
            if(bb == null) return;

            NBTStructure structure = resolveHiveStructure(component);
            if(structure == null) return;
            int mode = resolveHiveCoordMode(component);

            int carved = 0;
            int looted = 0;

            // ── 1) 内腔挖空 ──────────────────────────────────────────────────────
            if(ModConfig.structureAlienHiveCarveInterior) {

                // 必须走和注册侧同一个 normalize：配置留空/带反斜杠时裸传会静默失效
                boolean[][][] interior = getHiveInterior(normalizeNbtPath(ModConfig.structureAlienHiveNbtFile, HIVE_NBT));
                if(interior != null) {
                    for(int bx = 0; bx < interior.length; bx++) {
                        for(int by = 0; by < interior[bx].length; by++) {
                            for(int bz = 0; bz < interior[bx][by].length; bz++) {
                                if(!interior[bx][by][bz]) continue;

                                // 与 NBTStructure.build 完全相同的局部→世界映射
                                int rx = structure.rotateX(bx, bz, mode) + bb.minX;
                                int rz = structure.rotateZ(bx, bz, mode) + bb.minZ;
                                int ry = by + bb.minY;

                                if(rx < box.minX || rx > box.maxX || rz < box.minZ || rz > box.maxZ) continue;
                                if(ry < 1 || ry > 255) continue;

                                world.setBlock(rx, ry, rz, Blocks.air, 0, 2);
                                carved++;
                            }
                        }
                    }
                }
            }

            // ── 2) 箱子战利品随机化（和地心实验室同一套） ────────────────────────
            if(ModConfig.structureAlienHiveLootRandomize && ModConfig.structureLootRandomize) {

                int minX = Math.max(box.minX, bb.minX);
                int maxX = Math.min(box.maxX, bb.maxX);
                int minZ = Math.max(box.minZ, bb.minZ);
                int maxZ = Math.min(box.maxZ, bb.maxZ);

                if(minX <= maxX && minZ <= maxZ) {
                    for(int x = minX; x <= maxX; x++) {
                        for(int z = minZ; z <= maxZ; z++) {
                            for(int y = bb.minY; y <= bb.maxY; y++) {
                                if(!isLootContainer(world.getBlock(x, y, z))) continue;

                                TileEntity te = world.getTileEntity(x, y, z);
                                if(te instanceof IInventory) {
                                    randomizeContainer((IInventory) te, rand);
                                    looted++;
                                }
                            }
                        }
                    }
                }
            }

            if(carved > 0 || looted > 0) {
                hiveDebug("虫巢后处理 @ 区块(" + (box.minX >> 4) + "," + (box.minZ >> 4) + ")"
                        + " → 空腔挖空 " + carved + " 格，箱子随机化 " + looted + " 个");
            }
        }

        /** 从组件里掏出 {@link JigsawPiece}（包私有字段，反射读，结果缓存）。 */
        private static NBTStructure resolveHiveStructure(StructureComponent component) {
            try {
                if(fieldComponentPiece == null) {
                    fieldComponentPiece = NBTStructure.Component.class.getDeclaredField("piece");
                    fieldComponentPiece.setAccessible(true);
                }
                JigsawPiece piece = (JigsawPiece) fieldComponentPiece.get(component);
                return piece != null ? piece.structure : null;
            } catch(Exception e) {
                LOG.warn("[structure-hive] 无法读取组件的 piece 字段，内腔挖空/箱子随机化跳过: " + e);
                return null;
            }
        }

        /** 读组件的旋转朝向（HBM 补丁版 {@code StructureComponent.coordBaseMode}，protected int）。 */
        private static int resolveHiveCoordMode(StructureComponent component) {
            try {
                if(fieldComponentMode == null) {
                    fieldComponentMode = StructureComponent.class.getDeclaredField("coordBaseMode");
                    fieldComponentMode.setAccessible(true);
                }
                return fieldComponentMode.getInt(component);
            } catch(Exception e) {
                LOG.warn("[structure-hive] 无法读取组件的朝向字段，按未旋转处理: " + e);
                return 0;
            }
        }

        private void pinHeightToSurface(World world) {

            List<?> components = getComponents();
            if(components.isEmpty()) return;

            StructureBoundingBox bb = ((StructureComponent) components.get(0)).getBoundingBox();
            if(bb == null) return;

            int centerX = (bb.minX + bb.maxX) / 2;
            int centerZ = (bb.minZ + bb.maxZ) / 2;
            int surface = world.getTopSolidOrLiquidBlock(centerX, centerZ);
            int sizeY = bb.maxY - bb.minY + 1;

            // 中心对齐地表，再叠配置偏移（默认 0 = 和小虫巢一样半露出地面）
            int targetY = surface - (sizeY - 1) / 2 + ModConfig.structureAlienHiveHeightOffset;
            targetY = MathHelper.clamp_int(targetY,
                    ModConfig.structureAlienHiveMinHeight, ModConfig.structureAlienHiveMaxHeight);

            // offsetYHeight 是增量。组件初始 minY=0，所以 delta 就是目标底座高度。
            offsetYHeight(targetY - bb.minY);

            hiveDebug("虫巢已生成: 中心 x=" + centerX + ", z=" + centerZ
                    + "，地表 y=" + surface + "，底座 y=" + targetY
                    + "（高 " + sizeY + " 格）");
        }
    }

    /* ==================================================================================
     *  日落六号的自定义 StructureStart
     * ================================================================================== */

    /**
     * 日落六号的生成 Start。复刻 {@link RuinStart} 的模式，但有三点不同：
     *
     * <ol>
     *   <li><b>钉到岩浆层</b>：底座高度取 {@link #findSunsetBaseY} 算出的「能埋进岩浆且上方不暴露」
     *       的那一层，而不是地表平均高度；</li>
     *   <li><b>固定朝向 0</b>：反射把组件 {@code coordBaseMode} 钉成 0，结构什么样子就什么样子，
     *       不随机旋转（前两个遗迹是 {@code rand.nextInt(4)} 随机朝向）；</li>
     *   <li><b>原样不替换</b>：不接方块劣化。父类 {@code build(wipeExisting=false)} 只写 NBT
     *       记录的方块；NBT 未记录的格子（原建筑的空气/内腔）生成后清成空气，还原空腔，
     *       不被岩浆填死——结构什么样子就生成什么样子。</li>
     * </ol>
     *
     * <p>生成成功后：把箱子按前两个遗迹同一套稀有度掷骰（{@link #randomizeContainer}），
     * 再放入一件不参与随机化的中微子护目镜，并把「已生成」写进 {@link SunsetSixSavedData}（每世界仅一个）。</p>
     */
    public static class SunsetStart extends NBTStructure.Start {

        /** 是否已钉过层位（结构跨区块会被 generateStructure 调多次，只钉一次）。 */
        private boolean heightPinned;

        private boolean gogglesPlaced;
        private int gogglesX;
        private int gogglesY;
        private int gogglesZ;

        @Override
        public void func_143017_b(NBTTagCompound nbt) {
            super.func_143017_b(nbt);
            gogglesPlaced = nbt.getBoolean("GogglesPlaced");
            gogglesX = nbt.getInteger("GogglesX");
            gogglesY = nbt.getInteger("GogglesY");
            gogglesZ = nbt.getInteger("GogglesZ");
        }

        @Override
        public void func_143022_a(NBTTagCompound nbt) {
            super.func_143022_a(nbt);
            if(gogglesPlaced) {
                nbt.setBoolean("GogglesPlaced", true);
                nbt.setInteger("GogglesX", gogglesX);
                nbt.setInteger("GogglesY", gogglesY);
                nbt.setInteger("GogglesZ", gogglesZ);
            }
        }

        /** 反序列化用（{@code MapGenStructureIO} 会反射 new 无参构造）。 */
        public SunsetStart() {
        }

        public SunsetStart(World world, Random rand, SpawnCondition spawn, int chunkX, int chunkZ) {
            super(world, rand, spawn, chunkX, chunkZ);
        }

        @Override
        public void generateStructure(World world, Random rand, StructureBoundingBox box) {

            // A replay must not restore the template inventory over an already-awarded stack.
            if(gogglesPlaced && box.isVecInside(gogglesX, gogglesY, gogglesZ)) return;

            // 必须先钉层位与朝向：HBM 的高度判定发生在 Component.addComponentParts 里（super 内部）
            if(!heightPinned) {
                heightPinned = true;
                pinToLava(world, box);
                pinOrientation();
            }

            // super 只写 NBT 记录的方块、跳过空格（wipeExisting=false）→ 原样、不替换
            super.generateStructure(world, rand, box);

            // 后处理必须每个区块 box 都跑：结构 9×10 跨多个区块写入盒，
            // 第一个 box 只盖到结构边缘一列，若只跑一次会漏掉大部分箱子和内腔。
            // 各 box 首尾相接、互不重叠，按 box∩包围盒 裁剪后每格恰好处理一次。
            processAfterBuild(world, rand, box);
        }

        /**
         * 把整座结构钉到「能埋进岩浆且上方不暴露」的那一层。
         *
         * <p>底座高度由 {@link #findSunsetBaseY} 在锚点列上算出；找不到（理论上不会，
         * checkCoordinates 已经先验过）就退回配置下限。{@code offsetYHeight} 同时把组件
         * 标记成「高度已确定」，HBM 后面那条平均高度分支就不会再动它。</p>
         */
        private void pinToLava(World world, StructureBoundingBox box) {

            List<?> components = getComponents();
            if(components.isEmpty()) return;

            StructureBoundingBox bb = ((StructureComponent) components.get(0)).getBoundingBox();
            if(bb == null) return;

            int centerX = (bb.minX + bb.maxX) / 2;
            int centerZ = (bb.minZ + bb.maxZ) / 2;

            int baseY;
            if(ModConfig.structureSunsetSixTestMode) {
                // 测试模式：底座固定钉在 y=2（最初指定的高度），不做岩浆扫描，保证原点必刷
                baseY = ModConfig.structureSunsetSixTestBaseY;
            } else {
                baseY = findSunsetBaseY(world, centerX, centerZ);
                if(baseY < 0) baseY = Math.max(1, ModConfig.structureSunsetSixMinHeight);
            }

            // delta == 0 也要走一遍：这一步同时负责把 heightUpdated 置 true
            offsetYHeight(baseY - bb.minY);
        }

        /**
         * 反射把所有组件的 {@code coordBaseMode} 钉成 0（不旋转），并同步修正包围盒尺寸。
         *
         * <p>父类构造时是 {@code rand.nextInt(4)} 随机朝向；日落六号要求「结构什么样子就生成什么样子」，
         * 所以这里强制 0。{@code coordBaseMode} 是 protected，复用 {@link #fieldComponentMode} 反射缓存。</p>
         *
         * <p><b>必须同时修正包围盒</b>：组件构造时若随机到 mode 1/3，包围盒的 X/Z 是交换过的
         * （10×9 而非 9×10）。只钉朝向不改包围盒，{@code NBTStructure.build} 会按原始尺寸校验，
         * 触发 "bounds mismatch" 直接跳过整座结构。这里把每个组件包围盒的 X/Z 跨度重设为
         * 结构原始尺寸（mode 0 不交换），保持 minX/minZ 锚点不变。</p>
         */
        private void pinOrientation() {
            try {
                if(fieldComponentMode == null) {
                    fieldComponentMode = StructureComponent.class.getDeclaredField("coordBaseMode");
                    fieldComponentMode.setAccessible(true);
                }
                if(fieldComponentPiece == null) {
                    fieldComponentPiece = NBTStructure.Component.class.getDeclaredField("piece");
                    fieldComponentPiece.setAccessible(true);
                }
                for(Object o : getComponents()) {
                    fieldComponentMode.setInt(o, 0);

                    // 修正包围盒 X/Z 跨度为结构原始尺寸（mode 0 不交换 X/Z）
                    JigsawPiece piece = (JigsawPiece) fieldComponentPiece.get(o);
                    if(piece == null || piece.structure == null) continue;
                    StructureBoundingBox bb = ((StructureComponent) o).getBoundingBox();
                    if(bb == null) continue;
                    bb.maxX = bb.minX + piece.structure.getSizeX() - 1;
                    bb.maxZ = bb.minZ + piece.structure.getSizeZ() - 1;
                }
            } catch(Exception e) {
                LOG.warn("[structure-sunset] 无法固定朝向，按随机朝向生成: " + e);
            }
        }

        /**
         * 生成后处理：内腔清空 + 箱子战利品随机化 + 每世界唯一标记。
         *
         * <p>和 {@link RuinStart} 一样按「box ∩ 结构包围盒」裁剪：区块中心的 box（±8）
         * 首尾相接铺满平面，每个格子恰好被处理一次，跨区块不会漏也不会重。
         * 本方法每个区块 box 都会被调一次（结构跨多个写入盒），不能只跑一次。</p>
         */
        private void processAfterBuild(World world, Random rand, StructureBoundingBox box) {

            List<?> comps = getComponents();
            if(comps.isEmpty() || !(comps.get(0) instanceof StructureComponent)) return;
            StructureComponent component = (StructureComponent) comps.get(0);

            StructureBoundingBox bb = component.getBoundingBox();
            if(bb == null) return;

            int minX = Math.max(box.minX, bb.minX);
            int maxX = Math.min(box.maxX, bb.maxX);
            int minZ = Math.max(box.minZ, bb.minZ);
            int maxZ = Math.min(box.maxZ, bb.maxZ);
            if(minX > maxX || minZ > maxZ) return;

            // ── 1) 内腔清空：NBT 没记录的格子（原建筑里的空气）清成空气 ──────────────
            // 结构 NBT 只记录实体方块，空气格不记录；落地时这些格子被跳过、由地形（岩浆）填死。
            // 这里把包围盒内「NBT 未记录」的格子主动清成空气，恢复原建筑的空腔。
            carveInterior(world, bb, minX, maxX, minZ, maxZ);

            // ── 2) 箱子战利品随机化（和前两个遗迹同一套） ────────────────────────────
            if(ModConfig.structureSunsetSixLootRandomize && ModConfig.structureLootRandomize) {
                for(int x = minX; x <= maxX; x++) {
                    for(int z = minZ; z <= maxZ; z++) {
                        for(int y = bb.minY; y <= bb.maxY; y++) {
                            if(!isLootContainer(world.getBlock(x, y, z))) continue;
                            TileEntity te = world.getTileEntity(x, y, z);
                            if(te instanceof IInventory) {
                                randomizeContainer((IInventory) te, rand);
                            }
                        }
                    }
                }
            }

            if(!gogglesPlaced) {
                placeGuaranteedGoggles(world, box, bb, minX, maxX, minZ, maxZ);
            }

            // ── 3) 每世界仅一个：写进存档标记（幂等，重复写同一坐标无副作用） ──────────
            int centerX = (bb.minX + bb.maxX) / 2;
            int centerZ = (bb.minZ + bb.maxZ) / 2;
            SunsetSixSavedData.get(world).markGenerated(centerX, centerZ, bb.minY);
        }

        private void placeGuaranteedGoggles(World world, StructureBoundingBox box, StructureBoundingBox bb,
                                           int minX, int maxX, int minZ, int maxZ) {
            if(SUNSET_NBT.equals(normalizeNbtPath(ModConfig.structureSunsetSixNbtFile, SUNSET_NBT))) {
                // The shipped template has one book in this unlocked, 15-slot safe.
                int x = bb.minX + 2;
                int y = bb.minY + 22;
                int z = bb.minZ + 6;
                if(box.isVecInside(x, y, z) && world.getBlock(x, y, z) == ModBlocks.safe) {
                    putGogglesInEmptySlot(world.getTileEntity(x, y, z), x, y, z);
                }
                return;
            }

            // Custom templates need not put a safe at the shipped template's coordinates.
            for(int x = minX; x <= maxX; x++) {
                for(int z = minZ; z <= maxZ; z++) {
                    for(int y = Math.max(bb.minY, box.minY); y <= Math.min(bb.maxY, box.maxY); y++) {
                        Block block = world.getBlock(x, y, z);
                        if((block == ModBlocks.safe || isLootContainer(block))
                                && putGogglesInEmptySlot(world.getTileEntity(x, y, z), x, y, z)) return;
                    }
                }
            }
        }

        private boolean putGogglesInEmptySlot(TileEntity te, int x, int y, int z) {
            if(!(te instanceof IInventory)) return false;
            IInventory inventory = (IInventory) te;
            for(int slot = 0; slot < inventory.getSizeInventory(); slot++) {
                if(inventory.getStackInSlot(slot) != null) continue;
                inventory.setInventorySlotContents(slot,
                        new ItemStack(com.makentmgreatagain.init.ModItems.neutrino_goggles, 1, 0));
                gogglesPlaced = true;
                gogglesX = x;
                gogglesY = y;
                gogglesZ = z;
                return true;
            }
            return false;
        }

        /**
         * 把结构包围盒内「NBT 未记录」的格子清成空气，恢复原建筑的空腔。
         *
         * <p>解析 NBT 的占据网格（{@link #getSunsetOccupied}），局部坐标里没被记录的格子
         * 就是原建筑的空气。按 HBM build 同款的局部→世界映射（固定朝向 0，不旋转）落到世界，
         * 只处理本次 box 范围内的格子，清成空气。</p>
         *
         * <p>已记录的实体方块不动（super 已经写好），所以「结构什么样子就什么样子」，
         * 只是把原本该是空气、却被岩浆填死的内腔还原。</p>
         */
        private void carveInterior(World world, StructureBoundingBox bb,
                                   int minX, int maxX, int minZ, int maxZ) {

            boolean[][][] occupied = getSunsetOccupied(
                    normalizeNbtPath(ModConfig.structureSunsetSixNbtFile, SUNSET_NBT));
            if(occupied == null) return;

            for(int bx = 0; bx < occupied.length; bx++) {
                for(int by = 0; by < occupied[bx].length; by++) {
                    for(int bz = 0; bz < occupied[bx][by].length; bz++) {
                        if(occupied[bx][by][bz]) continue;   // 实体方块：不动

                        // 固定朝向 0：局部→世界不旋转，直接加包围盒原点
                        int wx = bx + bb.minX;
                        int wz = bz + bb.minZ;
                        int wy = by + bb.minY;

                        if(wx < minX || wx > maxX || wz < minZ || wz > maxZ) continue;
                        if(wy < 1 || wy > 255) continue;

                        world.setBlock(wx, wy, wz, Blocks.air, 0, 2);
                    }
                }
            }
        }
    }

    /** 日落六号 NBT 的占据网格（true = 该格有实体方块）。缓存与 NBT 路径绑定。 */
    private static boolean[][][] sunsetOccupied;
    private static String sunsetOccupiedPath;

    /**
     * 解析日落六号 NBT，算出哪些格子记录了实体方块。
     *
     * <p>与 {@link #getHiveInterior} 同一套解析（尺寸 + 调色板 + blocks 列表），
     * 但只返回「占据网格」，不做泛洪——日落六号的空腔就是「NBT 未记录的格子」，
     * 不需要区分内外（整个包围盒内未记录的都是原建筑的空气）。</p>
     *
     * @return 占据网格 {@code [x][y][z]}；NBT 读不到或解析失败返回 null（跳过清空）。
     */
    private static boolean[][][] getSunsetOccupied(String nbtPath) {

        String path = nbtPath == null ? "" : nbtPath.trim().replace('\\', '/');
        while(path.startsWith("/")) path = path.substring(1);

        if(sunsetOccupied != null && path.equals(sunsetOccupiedPath)) return sunsetOccupied;

        boolean[][][] result = null;

        InputStream in = AddonStructures.class.getResourceAsStream(
                "/assets/" + MakeNTMGreatAgain.MODID + "/" + path);
        if(in == null) {
            LOG.warn("[structure-sunset] 找不到结构 NBT（assets/" + MakeNTMGreatAgain.MODID + "/" + path
                    + "），内腔清空跳过");
            return null;
        }

        try {
            NBTTagCompound data = CompressedStreamTools.readCompressed(in);

            NBTTagList sizeList = data.getTagList("size", NBT.TAG_INT);
            int sx = ((NBTTagInt) sizeList.tagList.get(0)).func_150287_d();
            int sy = ((NBTTagInt) sizeList.tagList.get(1)).func_150287_d();
            int sz = ((NBTTagInt) sizeList.tagList.get(2)).func_150287_d();

            NBTTagList paletteList = data.getTagList("palette", NBT.TAG_COMPOUND);
            Block[] palette = new Block[paletteList.tagCount()];
            for(int i = 0; i < paletteList.tagCount(); i++) {
                String name = paletteList.getCompoundTagAt(i).getString("Name");
                Block block = Block.getBlockFromName(name);
                palette[i] = block != null ? block : Blocks.stone;
            }

            result = new boolean[sx][sy][sz];
            NBTTagList blocksList = data.getTagList("blocks", NBT.TAG_COMPOUND);
            for(int i = 0; i < blocksList.tagCount(); i++) {
                NBTTagCompound block = blocksList.getCompoundTagAt(i);
                int state = block.getInteger("state");
                if(state < 0 || state >= palette.length) continue;
                if(palette[state] == Blocks.air) continue;   // 显式空气也算空腔

                NBTTagList pos = block.getTagList("pos", NBT.TAG_INT);
                int x = ((NBTTagInt) pos.tagList.get(0)).func_150287_d();
                int y = ((NBTTagInt) pos.tagList.get(1)).func_150287_d();
                int z = ((NBTTagInt) pos.tagList.get(2)).func_150287_d();
                if(x < 0 || x >= sx || y < 0 || y >= sy || z < 0 || z >= sz) continue;
                result[x][y][z] = true;
            }

        } catch(Exception e) {
            LOG.warn("[structure-sunset] 解析日落六号 NBT 失败，内腔清空跳过: " + e);
            result = null;
        } finally {
            try { in.close(); } catch(Exception ignored) { }
        }

        if(result != null) {
            sunsetOccupied = result;
            sunsetOccupiedPath = path;
        }
        return result;
    }

    /* ==================================================================================
     *  地心实验室的自定义 StructureStart
     * ================================================================================== */

    /**
     * 两件事：先把层位钉死（{@link #pinHeight}），再在 HBM 的组件生成完之后补一轮战利品填充。
     *
     * <p>父类 {@code NBTStructure.Start} 处理了 piece 展开、旋转、方块写入等全部逻辑，
     * 这里只覆盖「高度」和「生成之后」这两个点。</p>
     */
    public static class RuinStart extends NBTStructure.Start {

        /** 只用来去重聊天框/日志，不参与序列化。 */
        private boolean heightLogged;

        /** 是否已钉过层位：结构跨 3×3 区块会被 generateStructure 调 9 次，不挡的话每次都白算 256 次地表高度。 */
        private boolean heightPinned;

        /** 反序列化用（{@code MapGenStructureIO} 会反射 new 无参构造）。 */
        public RuinStart() {
        }

        public RuinStart(World world, Random rand, SpawnCondition spawn, int chunkX, int chunkZ) {
            super(world, rand, spawn, chunkX, chunkZ);
        }

        @Override
        public void generateStructure(World world, Random rand, StructureBoundingBox box) {

            // 必须先钉层位：HBM 的高度判定发生在 Component.addComponentParts 里，
            // 也就是 super 这一句内部，晚了就来不及了。
            pinHeight(world, box);

            super.generateStructure(world, rand, box);

            // ── 1) 先求所有组件包围盒的并集 ────────────────────────────────────────
            // 这一步的初值必须用「反向哨兵」：求最小值的起点是最大值，求最大值的起点是最小值。
            // （这里曾经写反过：minX 从 box.maxX 起、maxX 从 box.minX 起，于是下面的交集
            //   恒为 minX >= maxX，直接 return —— 战利品随机化和方块劣化全部静默失效。）
            int sMinX = Integer.MAX_VALUE, sMaxX = Integer.MIN_VALUE;
            int sMinZ = Integer.MAX_VALUE, sMaxZ = Integer.MIN_VALUE;
            int sMinY = 255, sMaxY = 1;
            boolean found = false;

            for(Object o : this.getComponents()) {
                StructureBoundingBox bb = ((StructureComponent) o).getBoundingBox();
                if(bb == null) continue;
                if(bb.minX < sMinX) sMinX = bb.minX;
                if(bb.maxX > sMaxX) sMaxX = bb.maxX;
                if(bb.minZ < sMinZ) sMinZ = bb.minZ;
                if(bb.maxZ > sMaxZ) sMaxZ = bb.maxZ;
                if(bb.minY < sMinY) sMinY = bb.minY;
                if(bb.maxY > sMaxY) sMaxY = bb.maxY;
                found = true;
            }

            if(!found) return;

            // ── 2) 再和本次的 box 求交集 ──────────────────────────────────────────
            // 原版传进来的是 new StructureBoundingBox(chunkX<<4 + 8, chunkZ<<4 + 8, +15, +15)，
            // 也就是「区块中心 ±8」，不是区块本身，Y 还是 1~512 的兜底值。
            // 相邻区块的 box 首尾相接（[8,23]、[24,39]…），既不会漏也不会重复处理，
            // 所以直接取交集即可；Y 用组件自己的（super 之后已按地形 clamp 完成）。
            int minX = Math.max(box.minX, sMinX);
            int maxX = Math.min(box.maxX, sMaxX);
            int minZ = Math.max(box.minZ, sMinZ);
            int maxZ = Math.min(box.maxZ, sMaxZ);

            // 这个 box 完全落在结构外面（结构被旋转/裁掉时会发生），跳过。
            if(minX > maxX || minZ > maxZ) return;

            int[] result = afterGenerate(world, rand, minX, maxX, minZ, maxZ, sMinY, sMaxY);

            // 日志由 structureDebugLog 控制（默认关）。注意即使两个数字都是 0 也照打：
            // 结构跨 3×3 区块时会被调 9 次，边角区块本来就可能什么都不含，
            // 「全是 0」和「范围算错」要靠范围那一段来区分，不是靠有没有这行。
            debug("生成后处理 @ 区块(" + (minX >> 4) + "," + (minZ >> 4) + ")"
                    + " 范围 x=" + minX + "~" + maxX + ", z=" + minZ + "~" + maxZ
                    + ", y=" + sMinY + "~" + sMaxY
                    + " → 方块劣化 " + result[0] + " 个，战利品箱随机化 " + result[1] + " 个");
        }

        /**
         * 把整座结构钉到 {@code structureMinHeight} ~ {@code structureMaxHeight} 这一层。
         *
         * <p><b>为什么不能只靠 SpawnCondition 的 minHeight/maxHeight</b>：HBM 的高度逻辑是</p>
         * <pre>
         * int y = isFlatWorld ? averageHeight : clamp(averageHeight, minHeight, maxHeight);
         * </pre>
         * <p>「超平坦世界」那一支<b>直接跳过 clamp、拿地表高度当地板</b>——
         * 而地狱的地表高度是<b>天花板</b>（y≈127），于是这座实验室在整个 y=123~135 生成，
         * 埋进基岩顶里，地表和地下两头都看不到。这里自己算一遍 clamp 再把组件整体挪过去。</p>
         *
         * <p>{@code offsetYHeight} 不仅挪位置，还会把组件标记成「高度已确定」（heightUpdated），
         * 所以 HBM 后面那条分支就不会再动它。{@code delta == 0} 时也要调一次，不然标记不上。</p>
         */
        private void pinHeight(World world, StructureBoundingBox box) {

            // 钉一次就够（与 HiveStart 的 heightPinned 同口径）：后续区块再进来
            // 要么重复挪组件、要么拿刚生成了一半的地形算出不同的目标层
            if(heightPinned) return;
            heightPinned = true;

            List<?> components = getComponents();
            if(components.isEmpty()) return;

            StructureBoundingBox bb = ((StructureComponent) components.get(0)).getBoundingBox();
            if(bb == null) return;

            // 和 HBM 同一口径：先加 piece 的 heightOffset，再 clamp 到配置层位。
            // 漏掉 heightOffset 的话这一项配置就静默失效（曾出现过）。
            int targetY = MathHelper.clamp_int(averageHeight(world, box) + ModConfig.structureHeightOffset,
                    ModConfig.structureMinHeight, ModConfig.structureMaxHeight);

            // delta == 0 也要走一遍：这一步同时负责把 heightUpdated 置 true
            offsetYHeight(targetY - bb.minY);

            if(!heightLogged) {
                heightLogged = true;
                debug("地心实验室已生成: 底座 y=" + targetY
                        + "，平面范围 x=" + bb.minX + "~" + bb.maxX
                        + ", z=" + bb.minZ + "~" + bb.maxZ
                        + "（区块 " + (bb.minX >> 4) + "," + (bb.minZ >> 4) + " 附近）");
            }
        }

        /** 和 HBM 一样的取样口径：当前区块 16×16 的「地表最高实心方块」平均值。 */
        private static int averageHeight(World world, StructureBoundingBox box) {

            int total = 0;
            int count = 0;

            for(int z = box.minZ; z <= box.maxZ; z++) {
                for(int x = box.minX; x <= box.maxX; x++) {
                    total += world.getTopSolidOrLiquidBlock(x, z);
                    count++;
                }
            }

            return count == 0 ? 64 : total / count;
        }
    }

    /* ==================================================================================
     *  劣化规则
     * ================================================================================== */

    /** 一条来源规则：{@code from} 命中时，依次掷 {@code outcomes}。 */
    private static final class Decay {

        final Block from;
        final Outcome[] outcomes;

        Decay(Block from, Outcome... outcomes) {
            this.from = from;
            this.outcomes = outcomes;
        }
    }

    /** 一个结果：按 {@code chance}（百分比）把来源方块换成 {@code to}；{@code to == null} 表示空气。 */
    private static final class Outcome {

        final Block to;
        final int meta;
        final float chance;

        Outcome(Block to, int meta, float chance) {
            this.to = to;
            this.meta = meta;
            this.chance = chance;
        }
    }

    /* ==================================================================================
     *  稀有度
     * ================================================================================== */

    /**
     * 稀有度 → 保留概率。
     *
     * <p><b>基础值不在这里，而在配置文件里</b>（{@code config/MNGA/worldgen.cfg} 的
     * {@code structureLootKeepCommon / Uncommon / Rare / Epic / Legendary}，默认
     * 90 / 78 / 62 / 45 / 30 %）。本枚举只负责两件事：</p>
     * <ul>
     *   <li>把物品归类（见类顶部的 {@code rate(...)} 调用）；</li>
     *   <li>决定「堆得越多越容易留下」的加成斜率 {@link #perExtra}。</li>
     * </ul>
     *
     * <p>默认值是按「遗迹很稀有」调高的：一个 121 区块才出现一次的地心实验室，
     * 箱子里空荡荡的就没意义了。想整体调稀/调密直接改 cfg，不用重编译。</p>
     */
    private enum Rarity {
        /** 工业耗材、建材、普通食物、核废料 */
        COMMON(1F),
        /** 加工品、防护服、医疗、弹药 */
        UNCOMMON(1F),
        /** 核材料、机器、军械、机器升级 */
        RARE(1F),
        /** 动力装甲、蒸汽服 */
        EPIC(1F),
        /** 附魔金苹果这类独一无二的东西 */
        LEGENDARY(0F);

        /** 每一格多 1 个物品，保留率额外 +1 个百分点（封顶 +15）。0 = 不享受这条加成。 */
        final float perExtra;

        Rarity(float perExtra) {
            this.perExtra = perExtra;
        }

        /** 配置里的基础保留率（%）。每次判定都重新读，改 cfg 之后新生成的区块立刻生效。 */
        float baseFromConfig() {
            switch(this) {
                case COMMON:    return ModConfig.lootKeepCommonPercent;
                case UNCOMMON:  return ModConfig.lootKeepUncommonPercent;
                case RARE:      return ModConfig.lootKeepRarePercent;
                case EPIC:      return ModConfig.lootKeepEpicPercent;
                case LEGENDARY: return ModConfig.lootKeepLegendaryPercent;
                default:        return 50F;
            }
        }

        /** 这一格留下内容的概率（百分比）。 */
        float keepChance(int count) {
            float bonus = Math.min(15F, perExtra * Math.max(0, count - 1));
            return Math.min(100F, this.baseFromConfig() + bonus);
        }
    }
}
