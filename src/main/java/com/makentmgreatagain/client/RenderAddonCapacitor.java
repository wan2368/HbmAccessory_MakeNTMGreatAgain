package com.makentmgreatagain.client;

import org.lwjgl.opengl.GL11;

import com.hbm.render.loader.HFRWavefrontObject;
import com.hbm.render.util.ObjUtil;
import com.makentmgreatagain.blocks.BlockAddonCapacitor;

import cpw.mods.fml.client.registry.ISimpleBlockRenderingHandler;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.block.Block;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.world.IBlockAccess;

/**
 * 附属电容的方块渲染器 —— 用附属自己的 OBJ 模型与五面贴图，不碰原版 {@code RenderCapacitor}。
 *
 * <p>几何与朝向逻辑照原版电容的口径（meta 决定翻转/旋转），但模型取自
 * {@link AddonModels#capacitorModel()}（{@code assets/makentmgreatagain/models/blocks/capacitor.obj}），
 * 图标取自 {@link BlockAddonCapacitor} 自己的五个字段。这样资产与渲染都独立于 HBM。</p>
 *
 * <p>只复用 HBM 的<b>无状态绘制工具</b> {@link ObjUtil#renderPartWithIcon}（纯几何绘制，
 * 不读任何原版资源），以及模型加载器 {@link HFRWavefrontObject}（从附属命名空间读 obj）。
 * 这两个是代码工具，不是资产，符合「资产不依赖原版」的要求。</p>
 */
@SideOnly(Side.CLIENT)
public class RenderAddonCapacitor implements ISimpleBlockRenderingHandler {

    @Override
    public void renderInventoryBlock(Block block, int metadata, int modelId, RenderBlocks renderer) {
        GL11.glPushMatrix();
        Tessellator tess = Tessellator.instance;
        tess.setColorOpaque_F(1, 1, 1);

        BlockAddonCapacitor cap = (BlockAddonCapacitor) block;
        HFRWavefrontObject model = AddonModels.capacitorModel();

        tess.startDrawingQuads();
        ObjUtil.renderPartWithIcon(model, "Top", cap.iconTop, tess, 0, false);
        ObjUtil.renderPartWithIcon(model, "Side", cap.iconSide, tess, 0, false);
        ObjUtil.renderPartWithIcon(model, "Bottom", cap.iconBottom, tess, 0, false);
        ObjUtil.renderPartWithIcon(model, "InnerTop", cap.iconInnerTop, tess, 0, false);
        ObjUtil.renderPartWithIcon(model, "InnerSide", cap.iconInnerSide, tess, 0, false);
        tess.draw();

        GL11.glPopMatrix();
    }

    @Override
    public boolean renderWorldBlock(IBlockAccess world, int x, int y, int z, Block block, int modelId, RenderBlocks renderer) {
        Tessellator tess = Tessellator.instance;
        BlockAddonCapacitor cap = (BlockAddonCapacitor) block;
        HFRWavefrontObject model = AddonModels.capacitorModel();

        tess.setBrightness(block.getMixedBrightnessForBlock(world, x, y, z));
        tess.setColorOpaque_F(1, 1, 1);

        float flip = 0;
        float rotation = 0;
        int meta = world.getBlockMetadata(x, y, z);

        if (meta == 0) flip = (float) Math.PI;
        if (meta == 2) rotation = 90F / 180F * (float) Math.PI;
        if (meta == 3) rotation = 270F / 180F * (float) Math.PI;
        if (meta == 4) rotation = 180F / 180F * (float) Math.PI;
        if (rotation != 0F || meta == 5) flip = (float) Math.PI * 0.5F;

        tess.addTranslation(x + 0.5F, y + 0.5F, z + 0.5F);
        ObjUtil.renderPartWithIcon(model, "Top", cap.iconTop, tess, rotation, flip, true);
        ObjUtil.renderPartWithIcon(model, "Side", cap.iconSide, tess, rotation, flip, true);
        ObjUtil.renderPartWithIcon(model, "Bottom", cap.iconBottom, tess, rotation, flip, true);
        ObjUtil.renderPartWithIcon(model, "InnerTop", cap.iconInnerTop, tess, rotation, flip, true);
        ObjUtil.renderPartWithIcon(model, "InnerSide", cap.iconInnerSide, tess, rotation, flip, true);
        tess.addTranslation(-x - 0.5F, -y - 0.5F, -z - 0.5F);

        return true;
    }

    @Override
    public boolean shouldRender3DInInventory(int modelId) {
        return true;
    }

    @Override
    public int getRenderId() {
        return BlockAddonCapacitor.renderID;
    }
}