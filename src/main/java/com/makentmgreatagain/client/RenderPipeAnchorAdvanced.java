package com.makentmgreatagain.client;

import org.lwjgl.opengl.GL11;

import com.hbm.main.ResourceManager;
import com.hbm.render.tileentity.RenderPipeAnchor;
import com.hbm.tileentity.network.TileEntityFluidPipeAnchor;
import com.hbm.tileentity.network.TileEntityPipelineBase;
import com.hbm.util.ColorUtil;
import com.hbm.util.Compat;

import net.minecraft.item.Item;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.Vec3;

/**
 * 25m 高级管锚渲染器。
 *
 * <p>锚体、连接管、中段环全部复用本体的模型和贴图，所以外观和 10m 版完全一致。
 * 唯一的区别是：每个连接点的管子根部会额外渲染一圈加厚法兰和 4 个铆钉状突起。
 * 这样既不破坏原版管道纹理，又能一眼看出“这根管子接的是高级锚”。</p>
 */
public class RenderPipeAnchorAdvanced extends RenderPipeAnchor {

    private final ResourceLocation texture;
    private final Item item;

    public RenderPipeAnchorAdvanced(Item item, ResourceLocation texture) {
        // 按编译目标（新版）的两参构造编译。旧版 HBM 只有无参构造，
        // 由 coremod 的 PipeAnchorCtorTransformer 在类加载时把这条 invokespecial
        // 改写成无参调用（物品/贴图已由下方字段与覆写接管，行为不变）。
        super(item, texture);
        this.item = item;
        this.texture = texture;
    }

    @Override
    public Item getItemForRenderer() {
        return this.item;
    }

    @Override
    public net.minecraftforge.client.IItemRenderer getRenderer() {
        return new com.hbm.render.item.ItemRenderBase() {
            @Override
            public void renderInventory() {
                GL11.glTranslated(0, -3.5D, 0);
                GL11.glScaled(10D, 10D, 10D);
            }

            @Override
            public void renderCommon() {
                GL11.glShadeModel(GL11.GL_SMOOTH);
                bindTexture(texture);
                ResourceManager.pipe_anchor.renderPart("Anchor");
                GL11.glShadeModel(GL11.GL_FLAT);
            }
        };
    }

    @Override
    public void renderTileEntityAt(TileEntity te, double x, double y, double z, float interp) {
        // 先让本体渲染锚体 / 连接管 / 中段环
        super.renderTileEntityAt(te, x, y, z, interp);

        if(!(te instanceof TileEntityPipelineBase)) return;
        TileEntityPipelineBase anchor = (TileEntityPipelineBase) te;

        for(int[] pos : anchor.getConnected()) {
            TileEntity tile = Compat.getTileStandard(te.getWorldObj(), pos[0], pos[1], pos[2]);
            if(!(tile instanceof TileEntityPipelineBase)) continue;

            Vec3 anchorPoint = anchor.getConnectionPoint();
            Vec3 connectionPoint = ((TileEntityPipelineBase) tile).getConnectionPoint();

            double dX = connectionPoint.xCoord - anchorPoint.xCoord;
            double dY = connectionPoint.yCoord - anchorPoint.yCoord;
            double dZ = connectionPoint.zCoord - anchorPoint.zCoord;

            double hyp = Math.sqrt(dX * dX + dZ * dZ);
            double yaw = Math.toDegrees(Math.atan2(dX, dZ));
            double pitch = Math.toDegrees(Math.atan2(dY, hyp));

            // 突起和管子一样跟随流体颜色（本体 RenderPipeAnchor 用的是同一套 lighten 逻辑）
            float red = 1F, green = 1F, blue = 1F;
            if(anchor instanceof TileEntityFluidPipeAnchor) {
                int color = ((TileEntityFluidPipeAnchor) anchor).getType().getColor();
                color = ColorUtil.lightenColor(color, 0.25D);
                red = ColorUtil.fr(color);
                green = ColorUtil.fg(color);
                blue = ColorUtil.fb(color);
            }

            GL11.glPushMatrix();
            GL11.glTranslated(x + 0.5D, y + 0.5D, z + 0.5D);
            GL11.glRotated(yaw, 0, 1, 0);
            GL11.glRotated(90 - pitch, 1, 0, 0);

            // 加厚法兰：把本体 Ring 部件沿管轴拉厚，套在离开锚体一点点的位置
            GL11.glPushMatrix();
            GL11.glTranslated(0, 0.36D, 0);
            GL11.glScaled(1.10D, 1.8D, 1.10D);
            GL11.glTranslated(0, -1.5D, 0);
            bindTexture(texture);
            ResourceManager.pipe_anchor.renderPart("Ring");
            GL11.glPopMatrix();

            // 法兰外侧的 4 个纹理突起：2 格像素宽、向外凸 2 格像素，颜色跟随流体
            GL11.glPushMatrix();
            GL11.glTranslated(0, 0.36D, 0);
            drawLugs(red, green, blue);
            GL11.glPopMatrix();

            GL11.glPopMatrix();
        }
    }

    /**
     * 4 个纹理突起；局部 Y 轴是管子方向。
     *
     * <p>尺寸：切向 2 格像素宽，沿管轴高度是原来的一半，径向从管壁向外凸出 2 格像素。
     * 贴图使用管子那一行，颜色乘上流体色，所以看起来像管子本身鼓出来的四块。</p>
     */
    private void drawLugs(float red, float green, float blue) {
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_CURRENT_BIT);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_CULL_FACE);
        bindTexture(texture);
        GL11.glColor3f(red, green, blue);

        GL11.glBegin(GL11.GL_QUADS);
        drawLugBox(0.20D, -0.0625D, 0.3125D, 0.0625D);    // +X
        drawLugBox(-0.3125D, -0.0625D, -0.20D, 0.0625D);  // -X
        drawLugBox(-0.0625D, 0.20D, 0.0625D, 0.3125D);    // +Z
        drawLugBox(-0.0625D, -0.3125D, 0.0625D, -0.20D);  // -Z
        GL11.glEnd();

        GL11.glPopAttrib();
    }

    private void drawLugBox(double x0, double z0, double x1, double z1) {
        double y0 = -0.035D;
        double y1 = 0.035D;

        // 管子那一行贴图：UV 0.625..0.8125 × 0..0.0625
        float u0 = 0.625F, u1 = 0.8125F;
        float v0 = 0.0F, v1 = 0.0625F;

        // -Y
        GL11.glNormal3f(0F, -1F, 0F);
        uvVertex(u0, v0, x0, y0, z0);
        uvVertex(u1, v0, x1, y0, z0);
        uvVertex(u1, v1, x1, y0, z1);
        uvVertex(u0, v1, x0, y0, z1);
        // +Y
        GL11.glNormal3f(0F, 1F, 0F);
        uvVertex(u0, v0, x0, y1, z0);
        uvVertex(u0, v1, x0, y1, z1);
        uvVertex(u1, v1, x1, y1, z1);
        uvVertex(u1, v0, x1, y1, z0);
        // -Z
        GL11.glNormal3f(0F, 0F, -1F);
        uvVertex(u0, v0, x0, y0, z0);
        uvVertex(u0, v1, x0, y1, z0);
        uvVertex(u1, v1, x1, y1, z0);
        uvVertex(u1, v0, x1, y0, z0);
        // +Z
        GL11.glNormal3f(0F, 0F, 1F);
        uvVertex(u0, v0, x0, y0, z1);
        uvVertex(u1, v0, x1, y0, z1);
        uvVertex(u1, v1, x1, y1, z1);
        uvVertex(u0, v1, x0, y1, z1);
        // -X
        GL11.glNormal3f(-1F, 0F, 0F);
        uvVertex(u0, v0, x0, y0, z0);
        uvVertex(u0, v1, x0, y0, z1);
        uvVertex(u1, v1, x0, y1, z1);
        uvVertex(u1, v0, x0, y1, z0);
        // +X
        GL11.glNormal3f(1F, 0F, 0F);
        uvVertex(u0, v0, x1, y0, z0);
        uvVertex(u0, v1, x1, y1, z0);
        uvVertex(u1, v1, x1, y1, z1);
        uvVertex(u1, v0, x1, y0, z1);
    }

    private void uvVertex(float u, float v, double x, double y, double z) {
        GL11.glTexCoord2f(u, v);
        GL11.glVertex3d(x, y, z);
    }
}
