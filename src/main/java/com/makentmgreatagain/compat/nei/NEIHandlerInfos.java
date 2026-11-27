package com.makentmgreatagain.compat.nei;

import com.makentmgreatagain.MakeNTMGreatAgain;

import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.event.FMLInterModComms;
import net.minecraft.nbt.NBTTagCompound;

/**
 * 给本模组的 NEI 配方页登记「页面信息」（modName / modId）。
 *
 * <h3>不登记会怎样</h3>
 * <p>不会崩，但页签的悬浮提示会很难看：NEI 找不到这个页面的 HandlerInfo，就退回内置的
 * {@code DEFAULT_HANDLER_INFO}，于是</p>
 * <ul>
 * <li>mod 名那一行显示 <b>Unknown</b>；</li>
 * <li>提示里还会多出 {@code HandlerName / HandlerID / HandlerOrder} 三行调试信息。</li>
 * </ul>
 *
 * <p>后者的判定在 {@code codechicken.nei.recipe.GuiRecipeTab#handleTooltip}：
 * {@code if(modName.equals("Unknown") || 按住 Shift) 追加调试行}。
 * 也就是说那三行本该只在按住 Shift 时才出现，mod 名未知把它变成了常驻。</p>
 *
 * <h3>为什么用 IMC 而不是 API</h3>
 * <p>GTNH 的 NEI 没有 {@code API.registerHandlerInfo}，只有 IMC 频道
 * {@code registerHandlerInfo}（HBM 本体在 {@code MainRegistry.init} 里也是这么发的，
 * 见 {@code IMCHandlerNHNEI}）。</p>
 *
 * <h3>三个字段的坑</h3>
 * <ul>
 * <li>{@code handler}：填<b>类的全限定名</b>。NEI 用
 * {@code IRecipeHandler.getHandlerId()} 作 key 查表，而该方法的默认实现就是
 * {@code getClass().getName()}；换成别的写法（比如 {@code KEY}）匹配不上，静默无效。</li>
 * <li>{@code handlerID}：填 handler 里的 {@code KEY} 常量，和 recipe/usage 两个注册项共用。</li>
 * <li>只发 modName/modId，<b>不发</b> handlerHeight/Width/yShift/maxRecipesPerPage：
 * 缺省值（166×65、yShift 0、每页 1 条）与当前显示完全一致，发了反而会挪动版面。</li>
 * </ul>
 */
public final class NEIHandlerInfos {

	private NEIHandlerInfos() {
	}

	/** 只在客户端调（NEI 是纯客户端模组），由 {@code ClientProxy#init} 触发。 */
	public static void send() {

		// NEI 没装时 IMC 发出去也没人收；这里提前挡掉，顺手避免加载下面那些依赖 NEI 的类
		if(!Loader.isModLoaded("NotEnoughItems")) return;

		send(LargeChemicalFactoryHandler.KEY, LargeChemicalFactoryHandler.class);
		send(LargeCentrifugeHandler.KEY, LargeCentrifugeHandler.class);
		send(HybridFractionTowerHandler.KEY, HybridFractionTowerHandler.class);
		send(EnvironmentPumpHandler.KEY, EnvironmentPumpHandler.class);
		send(RiserRigHandler.KEY, RiserRigHandler.class);
		send(NuclearExplosionHandler.KEY, NuclearExplosionHandler.class);
		send(StellaratorBreedingHandler.KEY, StellaratorBreedingHandler.class);
		send(StellaratorFusionHandler.KEY, StellaratorFusionHandler.class);
		send(PlasmaHeaterHandler.KEY, PlasmaHeaterHandler.class);
	}

	private static void send(String handlerID, Class<?> handlerClass) {

		NBTTagCompound nbt = new NBTTagCompound();
		nbt.setString("handler", handlerClass.getName());
		nbt.setString("handlerID", handlerID);
		nbt.setString("modName", MakeNTMGreatAgain.NAME);
		nbt.setString("modId", MakeNTMGreatAgain.MODID);
		nbt.setBoolean("modRequired", true);

		FMLInterModComms.sendMessage("NotEnoughItems", "registerHandlerInfo", nbt);
	}
}
