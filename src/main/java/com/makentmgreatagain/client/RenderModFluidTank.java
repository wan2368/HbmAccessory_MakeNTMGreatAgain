package com.makentmgreatagain.client;

import com.hbm.inventory.fluid.FluidType;
import com.hbm.inventory.fluid.Fluids;
import com.hbm.inventory.fluid.trait.FT_Corrosive;
import com.hbm.lib.RefStrings;
import com.hbm.main.ResourceManager;
import com.hbm.render.util.DiamondPronter;
import com.hbm.tileentity.machine.storage.TileEntityMachineFluidTank;
import com.makentmgreatagain.init.ModFluids;

import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.opengl.GL11;

/**
 * 储罐（machine_fluidtank）的自定义渲染：支持“每个流体一张储罐贴图”。
 *
 * <p>HBM 原版渲染器是按“流体内部名”去 hbm 资源里找 textures/models/tank/tank_&lt;名字&gt;.png，
 * 附属的贴图它找不到；而 FluidType 里又只存 GUI 用的那张贴图，没有储罐贴图字段。
 * 所以这里做了两件事：</p>
 *
 * <ul>
 *   <li>ModFluids 里用 .tankTexture("tank_WATER") 给流体登记专属贴图；</li>
 *   <li>渲染时按 FluidType 查表，有就用自定义图，没有就完全复刻 HBM 原版逻辑
 *       （自带贴图 / 染色 / DANGER / 爆炸状态）。</li>
 * </ul>
 *
 * <p>注意：不能 new 一个 HBM 的 RenderFluidTank 当兜底再调它的 renderTileEntityAt，
 * 手动 new 出来的实例没有 dispatcher，bindTexture 会 NPE。</p>
 */
public class RenderModFluidTank extends TileEntitySpecialRenderer {

    /** 本模组流体的储罐贴图由 ModFluids.TANK_TEXTURES 提供（见 FluidDef.tankTexture） */

    /** HBM 自己那些 tank_<NAME>.png 都在 hbm 命名空间下 */
    private static final String HBM_TANK_PATH = "textures/models/tank/tank_";

    @Override
    public void renderTileEntityAt(TileEntity tile, double x, double y, double z, float partialTicks) {

        if (!(tile instanceof TileEntityMachineFluidTank)) return;

        TileEntityMachineFluidTank tank = (TileEntityMachineFluidTank) tile;
        FluidType type = tank.tank == null ? Fluids.NONE : tank.tank.getTankType();

        GL11.glPushMatrix();
        GL11.glTranslated(x + 0.5D, y, z + 0.5D);
        GL11.glEnable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_CULL_FACE);

        // 方块朝向：HBM 的机器方块 meta 从 10 开始
        switch (tank.getBlockMetadata() - 10) {
            case 2: GL11.glRotatef(180F, 0F, 1F, 0F); break;
            case 4: GL11.glRotatef(270F, 0F, 1F, 0F); break;
            case 3: GL11.glRotatef(0F, 0F, 1F, 0F); break;
            case 5: GL11.glRotatef(90F, 0F, 1F, 0F); break;
            default: break;
        }

        GL11.glShadeModel(GL11.GL_SMOOTH);
        GL11.glColor3d(1D, 1D, 1D);

        if (!tank.hasExploded) {
            // 正常：外框 + 液体层
            this.bindTexture(ResourceManager.tank_tex);
            ResourceManager.fluidtank.renderPart("Frame");

            this.bindTexture(this.getTankTexture(type));
            ResourceManager.fluidtank.renderPart("Tank");
        } else {
            // 炸过的罐子：外框 + 内胆 + 液体层
            this.bindTexture(ResourceManager.tank_tex);
            ResourceManager.fluidtank_exploded.renderPart("Frame");

            this.bindTexture(ResourceManager.tank_inner_tex);
            ResourceManager.fluidtank_exploded.renderPart("TankInner");

            this.bindTexture(this.getTankTexture(type));
            ResourceManager.fluidtank_exploded.renderPart("Tank");
        }

        GL11.glColor3d(1D, 1D, 1D);
        GL11.glShadeModel(GL11.GL_FLAT);

        // 罐体两侧的 NFPA 危险菱形
        if (type != null && type != Fluids.NONE) {
            RenderHelper.disableStandardItemLighting();

            GL11.glPushMatrix();
            GL11.glTranslated(-0.25, 0.5, -1.501);
            GL11.glRotated(90, 0, 1, 0);
            GL11.glScalef(1.0F, 0.375F, 0.375F);
            DiamondPronter.pront(type.poison, type.flammability, type.reactivity, type.symbol);
            GL11.glPopMatrix();

            GL11.glPushMatrix();
            GL11.glTranslated(0.25, 0.5, 1.501);
            GL11.glRotated(-90, 0, 1, 0);
            GL11.glScalef(1.0F, 0.375F, 0.375F);
            DiamondPronter.pront(type.poison, type.flammability, type.reactivity, type.symbol);
            GL11.glPopMatrix();
        }

        GL11.glEnable(GL11.GL_CULL_FACE);
        GL11.glPopMatrix();
        RenderHelper.enableStandardItemLighting();
    }

    /**
     * 决定“液体层”用哪张贴图，并设置染色。
     * 逻辑与 HBM 原版 RenderFluidTank.getTextureFromType 一致，只是把盐水的分支换成我们自己的贴图。
     */
    private ResourceLocation getTankTexture(FluidType type) {

        if (type == null || type == Fluids.NONE) {
            return new ResourceLocation(RefStrings.MODID, HBM_TANK_PATH + "NONE.png");
        }

        // 本模组自定义流体：查表拿专属贴图，不染色
        ResourceLocation custom = ModFluids.getTankTexture(type);
        if (custom != null) {
            GL11.glColor3d(1D, 1D, 1D);
            return custom;
        }

        // 自定义/外来流体：HBM 用通用 tank_NONE + tint 染色
        if (type.renderWithTint) {
            int color = type.getTint();
            GL11.glColor3d(
                    ((color & 0xff0000) >> 16) / 255D,
                    ((color & 0x00ff00) >> 8) / 255D,
                    (color & 0x0000ff) / 255D);
            return new ResourceLocation(RefStrings.MODID, HBM_TANK_PATH + "NONE.png");
        }

        // HBM 自带流体：按内部名找 tank_<NAME>.png；反物质/强腐蚀用 DANGER
        String name = type.getName();

        if (type.isAntimatter()
                || (type.hasTrait(FT_Corrosive.class) && type.getTrait(FT_Corrosive.class).isHighlyCorrosive())) {
            name = "DANGER";
        }

        return new ResourceLocation(RefStrings.MODID, HBM_TANK_PATH + name + ".png");
    }
}
