package com.makentmgreatagain.init;

import java.util.List;

import com.google.gson.JsonObject;
import com.google.gson.stream.JsonWriter;
import com.hbm.inventory.fluid.trait.FluidTrait;

import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;

/**
 * 流体背景描述：往流体 tooltip 里加几行说明文字。
 *
 * 只影响显示，不参与任何逻辑判定，所以序列化只存 lang 键本身——
 * HBM 读写 hbmFluidTraits.json 时会先 traits.clear() 再按类名 newInstance() 重建，
 * 键存住了，重载后描述才不会丢。
 *
 * 键不存在或没翻译时整行不显示，不会露出 "hbmfluid.hcl.desc" 这种原始键名。
 * lang 值里用 $ 分行（和 HBM 自己的多行描述写法一致）。
 */
public class FT_FluidLore extends FluidTrait {

	public static final String TRAIT_NAME = "lore";

	private String key;

	/** HBM 的 readTraits 会用 newInstance() 反射创建，必须留 public 无参构造 */
	public FT_FluidLore() { }

	public FT_FluidLore(String langKey) {
		this.key = langKey;
	}

	@Override
	public void addInfo(List<String> info) {
		if (key == null || key.isEmpty()) return;

		String raw = StatCollector.translateToLocal(key).trim();
		// 没有对应翻译时 StatCollector 会原样返回键名，这时干脆不显示
		if (raw.isEmpty() || raw.equals(key)) return;

		String[] lines = raw.split("\\$");
		for (String line : lines) {
			String s = line.trim();
			if (!s.isEmpty()) info.add(EnumChatFormatting.ITALIC + "" + EnumChatFormatting.GRAY + s);
		}
	}

	@Override
	public void serializeJSON(JsonWriter writer) throws java.io.IOException {
		writer.name("key").value(key == null ? "" : key);
	}

	@Override
	public void deserializeJSON(JsonObject obj) {
		if (obj.has("key")) this.key = obj.get("key").getAsString();
	}
}
