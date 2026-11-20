package com.makentmgreatagain.items;

import com.makentmgreatagain.entity.EntityItemHelicopter;

import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

/**
 * {@code block_helicopter}（耐摔合金块）的 ItemBlock：让方块被破坏后的掉落物
 * 也变成会自旋、会升空、会砸下来处决落点附近玩家的 {@link EntityItemHelicopter}。
 *
 * <p>方块掉落走的是 {@code World.spawnEntityInWorld(new EntityItem(...))}，
 * 不经过 {@code Item.dropItem}，但 Forge 的替换钩子在
 * {@code EntityJoinWorldEvent} 上，照样会问到这里 —— 所以只要覆盖
 * {@code hasCustomEntity} / {@code createEntity} 就够了，不需要改
 * {@code Block.dropBlockAsItem}。</p>
 *
 * <p>注册方式：{@code GameRegistry.registerBlock(block, ItemBlockHelicopter.class, name)}，
 * 由 Forge 反射调用 {@code ItemBlockHelicopter(Block)} 构造。</p>
 */
public class ItemBlockHelicopter extends ItemBlock {

    public ItemBlockHelicopter(Block block) {
        super(block);
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
