package com.makentmgreatagain.client;

import org.lwjgl.opengl.GL11;

import com.hbm.blocks.BlockDummyable;
import com.hbm.render.item.ItemRenderBase;
import com.hbm.render.tileentity.IItemRendererProvider;
import com.makentmgreatagain.init.ModBlocks;
import com.makentmgreatagain.tileentity.TileEntityFluidCooler;

import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.item.Item;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.client.IItemRenderer;

/**
 * 流体冷却机渲染器。
 *
 * <p>模型与贴图都取自本体 1.0.27_X5778 的<b>紧凑型压缩机</b>（{@code RenderCompressorCompact}）：
 * 几何是 {@code models/machines/condenser.obj}，三个部件 {@code Condenser} 静止机体、
 * {@code Fan1} / {@code Fan2} 两个对转风扇；涂装是 {@code textures/models/machines/compressor_compact.png}。</p>
 *
 * <p><b>注意别搞混</b>：5778 的紧凑型压缩机与蒸汽冷凝器共用同一个 {@code condenser.obj} 几何，
 * 区别只在贴图——冷凝器用深绿色的 {@code condenser.png}，紧凑型压缩机用带黑黄警示条纹的
 * {@code compressor_compact.png}。所以本类必须绑 {@code fluid_cooler.png}
 * （内容 = {@code compressor_compact.png}），一旦误用 {@code condenser.png}，外观立刻变成蒸汽冷凝器。</p>
 *
 * <p>风扇角度由 {@link TileEntityFluidCooler#fanSpin} 提供，只在机器运转（{@code isOn}）时
 * 每 tick 转 45°，与本体 {@code RenderCompressorCompact} 完全一致。</p>
 *
 * <p>旋转表、光照、剔除开关也照抄本体：NORTH=90°、WEST=180°、SOUTH=270°、EAST=0°；
 * 渲染期间关闭背面剔除（模型有朝向内侧的面），退出时恢复。</p>
 */
public class RenderFluidCooler extends TileEntitySpecialRenderer implements IItemRendererProvider {

	@Override
	public void renderTileEntityAt(TileEntity tile, double x, double y, double z, float interp) {

		GL11.glPushMatrix();
		GL11.glTranslated(x + 0.5D, y, z + 0.5D);
		GL11.glEnable(GL11.GL_LIGHTING);
		GL11.glDisable(GL11.GL_CULL_FACE);

		switch(tile.getBlockMetadata() - BlockDummyable.offset) {
		case 2: GL11.glRotatef(90, 0F, 1F, 0F); break;
		case 4: GL11.glRotatef(180, 0F, 1F, 0F); break;
		case 3: GL11.glRotatef(270, 0F, 1F, 0F); break;
		case 5: GL11.glRotatef(0, 0F, 1F, 0F); break;
		}

		// 风扇角度做插值，否则 20 tick/秒的 45° 跳变会看出来
		float fanAngle = 0F;
		if(tile instanceof TileEntityFluidCooler) {
			TileEntityFluidCooler cooler = (TileEntityFluidCooler) tile;
			fanAngle = cooler.prevFanSpin + (cooler.fanSpin - cooler.prevFanSpin) * interp;
		}

		this.renderMachine(fanAngle);

		GL11.glEnable(GL11.GL_CULL_FACE);
		GL11.glPopMatrix();
	}

	private void renderMachine(float fanAngle) {

		GL11.glShadeModel(GL11.GL_SMOOTH);
		bindTexture(AddonModels.fluid_cooler_tex);

		AddonModels.fluidCoolerModel().renderPart("Condenser");

		// 两个风扇绕 y = 1.5 的轴对转
		GL11.glPushMatrix();
		GL11.glTranslated(0D, 1.5D, 0D);
		GL11.glRotatef(fanAngle, 1F, 0F, 0F);
		GL11.glTranslated(0D, -1.5D, 0D);
		AddonModels.fluidCoolerModel().renderPart("Fan1");
		GL11.glPopMatrix();

		GL11.glPushMatrix();
		GL11.glTranslated(0D, 1.5D, 0D);
		GL11.glRotatef(-fanAngle, 1F, 0F, 0F);
		GL11.glTranslated(0D, -1.5D, 0D);
		AddonModels.fluidCoolerModel().renderPart("Fan2");
		GL11.glPopMatrix();

		GL11.glShadeModel(GL11.GL_FLAT);
	}

	@Override
	public Item getItemForRenderer() {
		return Item.getItemFromBlock(ModBlocks.machine_fluid_cooler);
	}

	@Override
	public IItemRenderer getRenderer() {
		return new ItemRenderBase() {
			@Override
			public void renderInventory() {
				GL11.glTranslated(-1, -1, 0);
				GL11.glScaled(2.75, 2.75, 2.75);
			}

			@Override
			public void renderCommon() {
				GL11.glScaled(0.75, 0.75, 0.75);
				GL11.glTranslated(0.5, 0, 0);
				GL11.glDisable(GL11.GL_CULL_FACE);
				RenderFluidCooler.this.renderMachine(0F);
				GL11.glEnable(GL11.GL_CULL_FACE);
			}
		};
	}
}
