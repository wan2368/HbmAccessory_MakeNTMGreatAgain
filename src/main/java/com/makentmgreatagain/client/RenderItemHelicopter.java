package com.makentmgreatagain.client;

import org.lwjgl.opengl.GL11;

import com.makentmgreatagain.entity.EntityItemHelicopter;

import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.entity.Entity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IIcon;
import net.minecraft.util.MathHelper;
import net.minecraft.util.ResourceLocation;

/**
 * {@link EntityItemHelicopter} 的渲染器：把物品图标画成一片竖立的薄片，
 * 上升段绕竖直轴自旋，下坠段固定正面朝镜头。
 *
 * <h2>为什么不直接借原版 {@code RenderItem}</h2>
 * <p>原版渲染掉落物时会对物品做一次 billboard
 * （{@code glRotatef(180 - playerViewY, 0, 1, 0)}），让物品永远正对镜头 ——
 * 也就是说，在它之前施加的任何绕 Y 轴旋转都会被抵消，玩家永远看不出物品在转。
 * 所以这里自己画 quad：位置、朝向完全由我们决定。顺带还能接管那个写死的
 * ±0.1 格上下浮动（起飞后它和上升轨迹会叠成抖动）。
 * </p>
 *
 * <h2>画法</h2>
 * <ul>
 * <li>一片 1×1 的平面，正反各一条 quad（背面补齐，否则从反面看会消失）；</li>
 * <li>缩放 0.5，整体约半格大小，与原版掉落物观感接近；</li>
 * <li><b>旋转直接算进顶点，不用 {@code glRotatef}</b>（见 {@link #drawDoubleSidedQuad}）——
 * 薄片本来就全在 {@code z=0} 平面上，绕 Y 轴转角只是把 x 分量按
 * {@code (cosθ, -sinθ)} 投到 (x, z)，不受任何上游矩阵状态影响；</li>
 * <li>角度由 {@link EntityItemHelicopter#spinAngleAt(double)} 给出：<b>出生即开始转</b>
 * （蓄势 2.75 秒里把转速从 10 rpm 拉到 120 rpm）、上升段保持 120 rpm、悬停段收回整圈、下坠段定格；
 * 每帧用 {@code partialTicks} 插值，转动是连续的而不是一 tick 一跳；</li>
 * <li>上下浮动的幅度由 {@link EntityItemHelicopter#bobScaleAt(double)} 给出：
 * 静止时是原版的 ±0.1 格，起飞前 20 tick 收干净。固定抬升 0.1 格不参与收窄，
 * 否则会陷进地面。</li>
 * </ul>
 *
 * <p>浮动和旋转都用同一个时间基准 {@code ticksExisted + partialTicks}，
 * 不要一个用 {@code age} 一个用 {@code ticksExisted} —— 两个计数器虽然通常同步，
 * 但在实体刚生成 / 读档的瞬间可能差一拍，视觉上就是「一边动一边不动」。</p>
 *
 * <p>角度完全由 {@code ticksExisted} 推导，不需要网络同步。</p>
 */
public class RenderItemHelicopter extends Render {

	/**
	 * 原版掉落物渲染器，给「关掉演出」的实体用（护甲四件）。
	 *
	 * <p>必须 {@code setRenderManager}：原版 {@code RenderItem} 内部要读
	 * {@code renderManager.playerViewY} 做 billboard，不绑定会直接 NPE。
	 * 这里在构造时就绑好 —— {@code RenderManager.instance} 在客户端注册
	 * 渲染器时已经初始化完成。</p>
	 */
	private static final net.minecraft.client.renderer.entity.RenderItem VANILLA = createVanilla();

	private static net.minecraft.client.renderer.entity.RenderItem createVanilla() {
		net.minecraft.client.renderer.entity.RenderItem render =
				new net.minecraft.client.renderer.entity.RenderItem();
		render.setRenderManager(net.minecraft.client.renderer.entity.RenderManager.instance);
		return render;
	}

	public RenderItemHelicopter() {
		// 只影响脚下那圈影子；不设的话完全没影子，看着像浮空
		this.shadowSize = 0.15F;
	}

	@Override
	public void doRender(Entity entity, double x, double y, double z, float yaw, float partialTicks) {
		if (!(entity instanceof EntityItemHelicopter)) {
			return;
		}

		EntityItemHelicopter item = (EntityItemHelicopter) entity;

		// 演出被关掉的（护甲四件）：完全退回原版掉落物渲染。
		// 本渲染器是为「自旋升空」写的：缩到 0.5 倍、竖成薄片、角度按 spinAngleAt 走。
		// 护甲不飞不砸，套这套画法就会看到「一片小薄片原地打转」，而且比正常掉落物小一半。
		// 交给原版 RenderItem，外观 / 大小 / billboard 朝向都与普通掉落物一致。
		if (!item.isLiftEnabled()) {
			VANILLA.doRender(entity, x, y, z, yaw, partialTicks);
			return;
		}

		// 已经砸碎：立刻不画。服务端会多活 SPLASH_DELAY tick 才真正删掉这个实体
		// （为了等客户端的落地判定跑完、把粒子和音效发出去），这几 tick 里如果照常
		// 渲染，玩家看到的就是「碎掉的东西还贴在地上」—— 也就是那 0.25~0.5 秒的残留。
		if (item.isLanded()) {
			return;
		}

		ItemStack stack = item.getEntityItem();
		if (stack == null || stack.getItem() == null) {
			return;
		}

		Item theItem = stack.getItem();
		IIcon icon = theItem.getIconIndex(stack);
		if (icon == null) {
			return;
		}

		// 方块物品（ItemBlock）的图标在 blocks 图集，普通物品在 items 图集
		bindTexture(theItem.getSpriteNumber() == 0
				? TextureMap.locationBlocksTexture
				: TextureMap.locationItemsTexture);

		// 与原版掉落物同节奏的上下浮动，起飞前 20 tick 内淡出到 0
		// （bobScaleAt 是 smoothstep，两端斜率为 0，收尾不留折角）。
		// 只淡「浮动」那部分，固定的 0.1 格抬升要留着，否则物品会陷进地面。
		float ticksNow = (float) item.ticksExisted + partialTicks;
		float bobOsc = MathHelper.sin((ticksNow / 10.0F) + item.hoverStart) * 0.1F;
		float bob = bobOsc * (float) EntityItemHelicopter.bobScaleAt(ticksNow) + 0.1F;

		GL11.glPushMatrix();
		// 这一段会改 TEXTURE_2D / LIGHTING / BLEND / ALPHA_TEST / CULL_FACE，
		// 还会改混合公式（glBlendFunc，属于 GL_COLOR_BUFFER_BIT 组），
		// 用 attrib 栈整段还原，比手动逐个 enable/disable 安全 ——
		// 漏还原 LIGHTING 会让后面渲染的生物变成无光照的平涂，
		// 漏还原混合公式会让后面的 HUD/方块画成加法混合
		GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT);
		GL11.glTranslated(x, y + bob + 0.1D, z);

		// 跟随所在位置的方块亮度，避免在暗处自发光
		int light = entity.getBrightnessForRender(partialTicks);
		OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit,
				(float) light % 65536.0F, (float) light / 65536.0F);

		GL11.glEnable(GL11.GL_TEXTURE_2D);
		GL11.glDisable(GL11.GL_LIGHTING);
		GL11.glEnable(GL11.GL_BLEND);
		GL11.glEnable(GL11.GL_ALPHA_TEST);
		OpenGlHelper.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
		GL11.glDisable(GL11.GL_CULL_FACE);
		GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);

		// 上升段绕竖直轴自旋，悬停段平滑收到整圈，下坠段定格不再转（见 spinAngleAt）。
		// 角度不交给 GL 矩阵，而是直接算进顶点（见 drawDoubleSidedQuad）——
		// 之前用 glRotatef 没看出效果，而这条链上任何一处矩阵状态被上游改掉都会静默失效，
		// 顶点方案没有这个不确定性。
		float spin = (float) EntityItemHelicopter.spinAngleAt(ticksNow);
		GL11.glScalef(0.5F, 0.5F, 0.5F);

		drawDoubleSidedQuad(icon, spin);

		GL11.glPopAttrib();
		GL11.glPopMatrix();
	}

	/**
	 * 阴影也得一起关。{@code doRender} 返回只是不画物品本体，脚下那圈影子是
	 * {@code RenderManager} 单独调这个方法画的 —— 落地后还留个影子，看着像「隐形的东西立在那」。
	 */
	@Override
	public void doRenderShadowAndFire(Entity entity, double x, double y, double z,
									  float yaw, float partialTicks) {
		if (entity instanceof EntityItemHelicopter) {
			EntityItemHelicopter item = (EntityItemHelicopter) entity;
			if (item.isLanded()) {
				return;
			}
			// 关掉演出的（护甲）：影子也交给原版，尺寸与位置才和物品本体对得上
			if (!item.isLiftEnabled()) {
				VANILLA.doRenderShadowAndFire(entity, x, y, z, yaw, partialTicks);
				return;
			}
		}
		super.doRenderShadowAndFire(entity, x, y, z, yaw, partialTicks);
	}

	/**
	 * 抽象方法要求，实际用不到：图标由 {@link #doRender} 按物品自己选图集
	 * （方块物品在 blocks 图集、普通物品在 items 图集），这里返回 items 图集只是占位。
	 */
	@Override
	protected ResourceLocation getEntityTexture(Entity entity) {
		return TextureMap.locationItemsTexture;
	}

	/**
	 * 以原点为中心画一片 1×1 的双面 quad，UV 取物品图标的整张贴图。
	 *
	 * <p><b>旋转在这里算，不用 {@code glRotatef}</b>：顶点本来就都在 {@code z = 0} 平面上，
	 * 绕 Y 轴转 θ 只需要把 x 分量投到 (x, z) 两轴上：</p>
	 * <pre>
	 * x' =  x·cosθ + z·sinθ  =  x·cosθ      （z 恒为 0）
	 * z' = -x·sinθ + z·cosθ  = -x·sinθ
	 * </pre>
	 * <p>法线 {@code (0,0,1)} 同理变成 {@code (sinθ, 0, cosθ)}。这样即使上游把模型视图矩阵
	 * 改坏了（或者有人在别处 push/pop 不对称），旋转也照样生效 —— 它只是顶点坐标的一部分。</p>
	 *
	 * <p>Minecraft 的 V 轴朝下，所以左下角对 {@code (minU, maxV)}、右上角对
	 * {@code (maxU, minV)}。背面那条 quad 顶点顺序反过来，否则会被背面剔除吃掉
	 * （虽然这里已经 {@code glDisable(GL_CULL_FACE)}，但顺序对了对光照也正确）。</p>
	 *
	 * @param angleDeg 绕竖直轴的旋转角度（度），0 表示正面朝 +Z
	 */
	private static void drawDoubleSidedQuad(IIcon icon, float angleDeg) {
		float u0 = icon.getMinU();
		float u1 = icon.getMaxU();
		float v0 = icon.getMinV();
		float v1 = icon.getMaxV();

		double rad = Math.toRadians(angleDeg);
		float cos = (float) Math.cos(rad);
		float sin = (float) Math.sin(rad);

		// 左顶点 x=-0.5、右顶点 x=+0.5 旋转后的 (x, z)
		float xl = -0.5F * cos;
		float zl = 0.5F * sin;
		float xr = 0.5F * cos;
		float zr = -0.5F * sin;

		Tessellator tess = Tessellator.instance;

		tess.startDrawingQuads();
		tess.setNormal(sin, 0.0F, cos);
		tess.addVertexWithUV(xl, -0.5D, zl, u0, v1);
		tess.addVertexWithUV(xr, -0.5D, zr, u1, v1);
		tess.addVertexWithUV(xr, 0.5D, zr, u1, v0);
		tess.addVertexWithUV(xl, 0.5D, zl, u0, v0);
		tess.draw();

		tess.startDrawingQuads();
		tess.setNormal(-sin, 0.0F, -cos);
		tess.addVertexWithUV(xr, -0.5D, zr, u1, v1);
		tess.addVertexWithUV(xl, -0.5D, zl, u0, v1);
		tess.addVertexWithUV(xl, 0.5D, zl, u0, v0);
		tess.addVertexWithUV(xr, 0.5D, zr, u1, v0);
		tess.draw();
	}
}
