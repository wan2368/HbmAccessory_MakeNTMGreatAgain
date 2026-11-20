package com.makentmgreatagain.client;

import org.lwjgl.opengl.GL11;

import com.hbm.blocks.BlockDummyable;
import com.hbm.render.item.ItemRenderBase;
import com.hbm.render.tileentity.IItemRendererProvider;
import com.makentmgreatagain.init.ModBlocks;

import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.item.Item;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.client.IItemRenderer;

/**
 * 可控核爆炸发生器渲染器。
 *
 * <p>模型原点在核心格底面的中心（导出时已按 1 单位 = 1 格、Y 轴朝上处理）。
 * 模型的控制面板只在 +Y（导出后是 -Z）一侧，所以必须按核心朝向旋转，
 * 让控制面板朝向玩家。</p>
 *
 * <p>模型按材质拆成了 6 个 OBJ，这里逐个绑定各自的贴图再画，
 * 这样大面积的平铺 UV 才能保持（合成一张图集会和 REPEAT 平铺冲突）。</p>
 */
public class RenderNuclearExplosionGenerator extends TileEntitySpecialRenderer implements IItemRendererProvider {

	@Override
	public void renderTileEntityAt(TileEntity tile, double x, double y, double z, float interp) {

		GL11.glPushMatrix();
		GL11.glTranslated(x + 0.5D, y, z + 0.5D);
		GL11.glEnable(GL11.GL_LIGHTING);
		GL11.glRotatef(facingAngle(tile.getBlockMetadata()), 0F, 1F, 0F);

		renderCommon();

		GL11.glPopMatrix();
	}

	/** 核心 meta = 10 + 朝向序号（NORTH=2、SOUTH=3、WEST=4、EAST=5）；模型正面默认朝 -Z */
	private static float facingAngle(int meta) {
		switch(meta - BlockDummyable.offset) {
		case 2: return 0F;      // NORTH：-Z，模型本来就朝这边
		case 3: return 180F;    // SOUTH
		case 4: return 90F;     // WEST：-Z 绕 Y 转 +90° 得到 -X
		case 5: return -90F;    // EAST：得到 +X
		default: return 0F;
		}
	}

	private void renderCommon() {

		GL11.glDisable(GL11.GL_CULL_FACE);
		GL11.glShadeModel(GL11.GL_FLAT);

		for(int i = 0; i < AddonModels.neg_keys.length; i++) {
			bindTexture(AddonModels.neg_tex[i]);
			AddonModels.negModel(i).renderAll();
		}

		GL11.glEnable(GL11.GL_CULL_FACE);
	}

	@Override
	public Item getItemForRenderer() {
		return Item.getItemFromBlock(ModBlocks.machine_nuclear_explosion_generator);
	}

	@Override
	public IItemRenderer getRenderer() {
		return new ItemRenderBase() {
			public void renderInventory() {
				// 13×13×17 的巨型机器：17 格高 × 0.55 ≈ 9 px，正好塞进 16 px 的物品格
				GL11.glTranslated(0, -1.8, 0);
				GL11.glScaled(0.55, 0.55, 0.55);
			}
			public void renderNonInv() {
				// 基类已经缩到 0.25，整机 24 格宽直接糊满整个视野，这里再缩一次
				GL11.glTranslated(0, -0.45, 0);
				GL11.glScaled(0.18, 0.18, 0.18);
			}
			public void renderCommon() {
				RenderNuclearExplosionGenerator.this.renderCommon();
			}
		};
	}
}
