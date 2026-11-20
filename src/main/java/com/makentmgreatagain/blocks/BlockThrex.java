package com.makentmgreatagain.blocks;

import com.makentmgreatagain.compat.HBMVersionCompat;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;

/**
 * Threx 块（9 锭 = 1 块）。
 *
 * <h2>数值来源</h2>
 * <ul>
 *   <li>{@code setResistance(450000F)} —— 抗爆。1.7.10 里这个数就是
 *       {@code Block#getExplosionResistance}，HBM 的 {@code ExplosionNT} 逐方块
 *       比较它和自己的破坏阈值，450000 足以在任何常规核爆下原地不动。</li>
 *   <li>{@code setHardness(100F)} —— 黑曜石是 50，取两倍即挖掘时间翻倍。
 *       注意挖掘时间同时受 {@code setHarvestLevel("pickaxe", 3)} 影响，
 *       所以登记时一并给了钻石镐等级（见 {@code ModBlocks}）。</li>
 *   <li>不带 TileEntity，纯实体方块。物品形态用 {@code ItemBlockThrex}，
 *       放在背包任意一格就给缓慢 III（锭与粉是 II）。</li>
 * </ul>
 *
 * <h2>辐射隔绝靠 PRISM，本类不写任何辐射逻辑</h2>
 * HBM 默认的 {@code ChunkRadiationHandlerSimple} 按<b>区块</b>存一个浮点数，没有方向、
 * 没有空间结构 —— 辐射不存在「从某格穿到某格」的过程，也就没有可以拦下的判定点。
 * 在那种模型下唯一能做的是让区块的值变小（吸收），做不到隔绝。
 *
 * <p>真正的隔绝由 PRISM 3D 系统提供（切换点在 {@code CommonProxy#postInit}）：
 * 它把世界切成 16×16×16 子区块，按方块的爆炸抗性给三个轴各算一组阻力，扩散时按
 * {@code e^(-阻力/10000)} 衰减。这就是「拿方块围起来、辐射穿不过去」的实现，
 * 本块的 450000 抗爆直接顶到单方块阻力上限。</p>
 *
 * <p><b>PRISM 的阻力默认有上限</b>：源码里写的是
 * {@code Math.min(block.getExplosionResistance(...), 100)}，直说的话 450000 会被截到 100，
 * 和黑曜石、加固 CMB 钢砖块完全同级 —— 那等于这块造价远超 CMB 的金属在抗辐射上毫无优势。
 * 所以附属模组补了一刀：{@code core/PrismThrexResistanceTransformer} 把那个
 * {@code Math.min} 换成 {@link com.makentmgreatagain.hook.ThrexRadResistance#apply}，
 * Threx 走独立值 {@code THREX_RESISTANCE}（默认 1000，即一块抵十块加固 CMB），
 * <b>其余方块的算法一个字节都没改</b>。</p>
 *
 * <h2>与 CMB 钢瓦块的对比</h2>
 * <table border="1">
 *   <tr><th></th><th>CMB钢瓦块</th><th>加固CMB钢砖块</th><th>Threx 块</th></tr>
 *   <tr><td>抗爆</td><td>5000</td><td>50000</td><td>450000</td></tr>
 *   <tr><td>硬度（挖掘时间）</td><td>25</td><td>25</td><td>100</td></tr>
 *   <tr><td>挖掘等级</td><td>0</td><td>0</td><td>3（钻石镐）</td></tr>
 *   <tr><td>PRISM 辐射阻力/格</td><td>100</td><td>100</td><td>1000</td></tr>
 * </table>
 */
public class BlockThrex extends Block {

	public BlockThrex() {
		super(Material.iron);
		setStepSound(HBMVersionCompat.plateMetalSound());
		setHardness(100.0F);
		setResistance(450000.0F);
	}
}
