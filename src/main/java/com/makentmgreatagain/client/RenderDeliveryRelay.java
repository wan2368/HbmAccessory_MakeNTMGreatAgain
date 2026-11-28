package com.makentmgreatagain.client;

import com.makentmgreatagain.tileentity.TileEntityDeliveryRelay;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.client.IItemRenderer;
import net.minecraftforge.client.model.IModelCustom;
import net.minecraftforge.common.util.ForgeDirection;
import org.lwjgl.opengl.GL11;

/**
 * 投送中继节点 3D 渲染器（加载 models/machines/delivery_relay.obj + 32×32 16px/格贴图）。
 */
public class RenderDeliveryRelay extends TileEntitySpecialRenderer {

	@Override
	public void renderTileEntityAt(TileEntity te, double x, double y, double z, float partialTicks) {
		if(!(te instanceof TileEntityDeliveryRelay)) return;
		TileEntityDeliveryRelay relay = (TileEntityDeliveryRelay) te;

		GL11.glPushMatrix();
		GL11.glTranslated(x + 0.5D, y + 0.5D, z + 0.5D);
		applyFacingRotation(relay.getFacing());
		GL11.glTranslated(0.0D, -0.5D, 0.0D);

		renderRelayModel();
		GL11.glPopMatrix();
	}

	private void renderRelayModel() {
		bindTexture(AddonModels.delivery_relay_tex);
		IModelCustom model = AddonModels.deliveryRelayModel();
		GL11.glColor4f(1F, 1F, 1F, 1F);
		GL11.glDisable(GL11.GL_CULL_FACE);

		model.renderPart("Body");

		// 光学棱镜核心与顶部状态灯全亮发光
		GL11.glDisable(GL11.GL_LIGHTING);
		float prevBx = OpenGlHelper.lastBrightnessX;
		float prevBy = OpenGlHelper.lastBrightnessY;
		OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240F, 240F);

		model.renderPart("Core");
		model.renderPart("LED");

		OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, prevBx, prevBy);
		GL11.glEnable(GL11.GL_LIGHTING);
		GL11.glEnable(GL11.GL_CULL_FACE);
	}

	private static void applyFacingRotation(ForgeDirection facing) {
		switch(facing) {
			case UP:    break;
			case DOWN:  GL11.glRotatef(180F, 1F, 0F, 0F); break;
			case SOUTH: GL11.glRotatef(90F, 1F, 0F, 0F); break;
			case NORTH: GL11.glRotatef(180F, 0F, 1F, 0F); GL11.glRotatef(90F, 1F, 0F, 0F); break;
			case EAST:  GL11.glRotatef(90F, 0F, 1F, 0F);  GL11.glRotatef(90F, 1F, 0F, 0F); break;
			case WEST:  GL11.glRotatef(-90F, 0F, 1F, 0F); GL11.glRotatef(90F, 1F, 0F, 0F); break;
			default:    break;
		}
	}

	public IItemRenderer getItemRenderer() {
		return new IItemRenderer() {
			@Override public boolean handleRenderType(ItemStack item, ItemRenderType type) { return true; }
			@Override public boolean shouldUseRenderHelper(ItemRenderType type, ItemStack item, ItemRendererHelper helper) { return true; }
			@Override
			public void renderItem(ItemRenderType type, ItemStack item, Object... data) {
				GL11.glPushMatrix();
				if(type == ItemRenderType.INVENTORY) {
					GL11.glTranslated(0.0D, -0.45D, 0.0D);
					GL11.glScalef(1.15F, 1.15F, 1.15F);
				} else if(type == ItemRenderType.EQUIPPED || type == ItemRenderType.EQUIPPED_FIRST_PERSON) {
					GL11.glTranslated(0.5D, 0.15D, 0.5D);
				} else {
					GL11.glTranslated(0.0D, -0.35D, 0.0D);
				}
				renderRelayModel();
				GL11.glPopMatrix();
			}
		};
	}
}
