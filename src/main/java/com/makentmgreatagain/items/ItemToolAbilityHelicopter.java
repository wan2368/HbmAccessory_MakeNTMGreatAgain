package com.makentmgreatagain.items;

import com.hbm.items.tool.ItemToolAbility;
import com.makentmgreatagain.entity.EntityItemHelicopter;

import net.minecraft.entity.Entity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

/**
 * 掉在地上会自旋、转速到了会升空的 HBM 能力工具（镐/斧/…）。
 *
 * <p>「掉落物换成 {@link EntityItemHelicopter}」只能由物品自己声明：Forge 的
 * {@code ForgeInternalHandler.onEntityJoinWorld} 对每个普通 {@code EntityItem} 问一次
 * {@code item.hasCustomEntity(stack)}。{@code ItemToolAbility} 属于 HBM 本体，没有修改权，
 * 也没有别的钩子（cancel 后重 spawn 会漏 tick、可能复制物品），Java 又不能多继承，
 * 所以只能反过来继承能力工具、把这两行抄进来；工具能力本身原样可用。</p>
 *
 * <p>渲染不用管：渲染器按<b>实体类</b>注册（{@code RenderingRegistry.registerEntityRenderingHandler}），
 * 任何物品掉成这个实体都会自动用 {@code RenderItemHelicopter}。另外不要给工具挂
 * {@code ItemRenderTransformer}，那套参数是给大剑的。</p>
 *
 * <p>工具掉落同样走完整演出、同样会砸死落点玩家，不给工具做豁免；要退回原版掉落物，
 * 用 {@code EntityItemHelicopter#setLiftEnabled(false)} 关掉整套演出。</p>
 */
public class ItemToolAbilityHelicopter extends ItemToolAbility {

    /**
     * 参数与 {@link ItemToolAbility} 完全一致，不做任何包装。
     *
     * @param damage   攻击伤害（能力工具里这个值是「原版伤害 + 附加值」，不是最终伤害）
     * @param movement 蓄力/使用时的移速倍率，0 表示站着不动
     * @param material 工具材质，见 {@code MNGAMaterials.TOOL_HELICOPTER}
     * @param type     工具类型，决定能采什么（{@code MINER} = 镐+锹合一）
     */
    public ItemToolAbilityHelicopter(float damage, double movement, Item.ToolMaterial material, EnumToolType type) {
        super(damage, movement, material, type);
    }

    @Override
    public boolean hasCustomEntity(ItemStack stack) {
        return true;
    }

    @Override
    public Entity createEntity(World world, Entity location, ItemStack stack) {
        return EntityItemHelicopter.fromDropped(world, location, stack);
    }
}
