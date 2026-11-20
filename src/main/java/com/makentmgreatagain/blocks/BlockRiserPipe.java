package com.makentmgreatagain.blocks;

import com.makentmgreatagain.tileentity.TileEntityRiserPipe;

import net.minecraft.block.BlockContainer;
import net.minecraft.block.material.Material;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

/**
 * 海面立管钻机泵送时在世界里逐格落下的输送管道。
 *
 * <p>模型由 {@code RenderRiserPipe} 用管段 OBJ 绘制（八棱管身 + 法兰），
 * 所以世界渲染交给 TESR；方块图标用单独裁出的 16×16 侧壁贴图。</p>
 */
public class BlockRiserPipe extends BlockContainer {

	public BlockRiserPipe() {
		super(Material.iron);

		this.setBlockName("riser_pipe");
		this.setBlockTextureName("makentmgreatagain:riser_pipe");
		this.setStepSound(soundTypeMetal);
		this.setHardness(1.5F);
		this.setResistance(10.0F);
		this.setHarvestLevel("pickaxe", 1);
	}

	@Override
	public boolean isOpaqueCube() {
		return false;
	}

	@Override
	public boolean renderAsNormalBlock() {
		return false;
	}

	@Override
	public int getRenderType() {
		return -1;
	}

	@Override
	public TileEntity createNewTileEntity(World world, int meta) {
		return new TileEntityRiserPipe();
	}
}
