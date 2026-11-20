package com.makentmgreatagain.blocks;

import com.makentmgreatagain.tileentity.TileEntityDeepSeaNodule;

import net.minecraft.block.BlockContainer;
import net.minecraft.block.material.Material;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

/**
 * 深海结核混合物。
 *
 * <p>海床表层矿：不是埋在地下的矿脉，而是直接铺在深海海床表面的一层多金属结核。
 * 海面立管钻机向下钻到它时会停下，并每 20 秒从这里采一次矿，共 30 次。</p>
 *
 * <p>剩余次数记在方块实体 {@link TileEntityDeepSeaNodule} 里（跟着方块存档），
 * 采集机拆了重放、区块卸载重载都不会刷新进度。采空后方块会被移除，
 * 钻机继续向下钻探。</p>
 *
 * <p>用 {@link Material#rock}、砂砾音效和较低硬度，挖起来像“从海床上铲起来的一坨”，
 * 而不是普通矿石。硬度/抗性/音效都在这里固定，注册时只负责名字、贴图和创造栏。</p>
 */
public class BlockDeepSeaNodule extends BlockContainer {

    public BlockDeepSeaNodule() {
        super(Material.rock);
        this.setStepSound(soundTypeGravel);
        this.setHardness(3.0F);
        this.setResistance(5.0F);
    }

    @Override
    public TileEntity createNewTileEntity(World world, int meta) {
        return new TileEntityDeepSeaNodule();
    }

    @Override
    public int getRenderType() {
        return 0;
    }

    @Override
    public boolean isOpaqueCube() {
        return true;
    }
}
