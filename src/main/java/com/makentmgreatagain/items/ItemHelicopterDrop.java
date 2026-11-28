package com.makentmgreatagain.items;

import com.makentmgreatagain.entity.EntityItemHelicopter;

import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

/**
 * 耐摔合金（helicopter）系列物品的基类：掉落物会自旋 → 升空 → 砸落 → 碎裂，
 * 且<b>落地瞬间会处决落点 1 格内的玩家</b>。
 *
 * <p>行为本体在 {@link EntityItemHelicopter}，这里只负责两件事：</p>
 * <ol>
 * <li>{@link #hasCustomEntity(ItemStack)} 恒为 true —— 这是告诉 Forge
 * 「这物品掉出来时我要换成自己的实体」的开关；</li>
 * <li>{@link #createEntity(World, Entity, ItemStack)} 返回替换实体。</li>
 * </ol>
 *
 * <p><b>覆盖范围</b>：{@code HasCustomEntity} 的调用点在
 * {@code ForgeInternalHandler.onEntityJoinWorld}，世界每次 spawn 一个
 * <b>精确类为 {@code EntityItem}</b> 的实体都会走一遍。所以玩家丢弃、方块被破坏、
 * 箱子被打碎、{@code /give} 掉落 —— 只要落点是普通掉落物，都会换成我们的实体。</p>
 *
 * <p><b>方块不在覆盖范围内</b>：{@code block_helicopter} 等金属储存块用的是原版
 * {@code ItemBlock}，破坏后掉落的是普通 {@code EntityItem}，不会触发这套演出。</p>
 */
public class ItemHelicopterDrop extends ItemWithDesc {

    @Override
    public boolean hasCustomEntity(ItemStack stack) {
        return true;
    }

    @Override
    public Entity createEntity(World world, Entity location, ItemStack stack) {
        return EntityItemHelicopter.fromDropped(world, location, stack);
    }
}
