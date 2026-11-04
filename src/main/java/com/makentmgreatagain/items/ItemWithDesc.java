package com.makentmgreatagain.items;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.EnumRarity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;

/**
 * 带说明文字与名称颜色的通用物品基类。
 *
 * <p>本附属里原本有三处各抄了一遍 addInformation（{@code ItemAdvancedAlloy}、
 * {@code ItemFluidDestroyer}、{@code ItemCigaretteDrx}），三份逻辑几乎一致却都不完整：
 * 都只能整段灰字、都要在语言文件里手写 {@code $} 换行、都不能给名字上色。
 * 这个类把它们合并掉，并把「描述」和「名称颜色」都做成一行配置。</p>
 *
 * <h3>最短用法</h3>
 * <pre>
 * // ModItems 里，原来是 new Item()
 * powder_zinc_isp = new ItemWithDesc();
 * powder_zinc_isp.setUnlocalizedName("powder_zinc_isp");
 *
 * // 语言文件里加一行就有了说明，$ 是换行符
 * item.powder_zinc_isp.desc=第一行$第二行
 * </pre>
 *
 * <h3>改名称颜色</h3>
 * <pre>
 * // 方式一：任意 16 色，链式写法
 * new ItemWithDesc().setNameColor(EnumChatFormatting.GOLD)
 *
 * // 方式二：只改四档稀有度，不污染显示名（见下方「注意」）
 * new ItemWithDesc().setRarity(EnumRarity.rare)
 * </pre>
 *
 * <p><b>注意（名称颜色的代价）</b>：{@code setNameColor} 是往显示名前面拼一个
 * {@code §x} 颜色代码，而 NEI 搜索、按首字母快速匹配、成就与统计都读这份显示名。
 * 所以它会：</p>
 * <ul>
 *   <li>不影响包含式搜索（名字原文还在，拼音搜索也照样能命中）</li>
 *   <li>影响「按首字符」匹配——首字符从原本的字母变成了 {@code §}</li>
 *   <li>服务端侧不会带色（下方用 effectiveSide 挡掉了），成就消息保持干净</li>
 * </ul>
 * <p>如果你在意搜索，用 {@code setRarity}；只在意外观，就用 {@code setNameColor}。</p>
 *
 * <h3>自动换行</h3>
 * <p>语言文件里不想手写一堆 {@code $} 就调 {@code setDescWidth(30)}：</p>
 * <pre>
 * new ItemWithDesc().setDescWidth(30)   // 一行约 15 个汉字
 * </pre>
 * <p>宽度以半角字符为 1、汉字与全角符号为 2 计算，对应物品提示框的实际像素宽度。
 * 手写的 {@code $} 优先级更高，会先按它分段，再对每段自动折行。</p>
 *
 * <h3>多个物品共用一个类但参数不同</h3>
 * <pre>
 * // 销毁罐三种型号，描述里都有 %s 占位，想各自填自己的速率
 * new ItemFluidDestroyer(25, true, false, false)     // 构造函数里自己 setDescArgs(rate)
 * </pre>
 *
 * <h3>不覆盖行为</h3>
 * <p>语言文件里没有 {@code .desc} 条目时，addInformation 直接返回，物品表现与
 * 普通 {@code new Item()} 完全一致；不调 setNameColor 时显示名也与原版一致。
 * 所以可以无脑把所有简单物品换成这个类。</p>
 */
public class ItemWithDesc extends Item {

	/** 说明文字的键后缀，完整键名是 item.&lt;unlocalizedName&gt;.desc */
	public static final String DESC_SUFFIX = ".desc";

	/** HBM 语言文件约定的手动换行符 */
	public static final char LINE_BREAK = '$';

	/** 稀有度，null 表示沿用父类（普通白名） */
	protected EnumRarity rarity;
	/** 名称颜色，null 表示不动 */
	protected EnumChatFormatting nameColor;
	/** 说明文字颜色，null 表示不加前缀（完全由语言文件里的 § 代码决定） */
	protected EnumChatFormatting descColor = EnumChatFormatting.GRAY;
	/** 自动换行宽度，0 表示不自动换行（只认手写的 $） */
	protected int descWidth = 0;
	/** 说明文字里 %s 的实参，null 表示走不格式化的分支 */
	protected Object[] descArgs;
	/** 是否显示附魔光效 */
	protected boolean glow;

	/* ------------------------------------------------------------------ 链式配置 */

	/** 稀有度：决定名字颜色（白/黄/青/紫）。不污染显示名，搜索友好。 */
	public ItemWithDesc setRarity(EnumRarity rarity) {
		this.rarity = rarity;
		return this;
	}

	/** 名称颜色：任意 16 色。注意会往显示名前拼 § 代码，见类注释。 */
	public ItemWithDesc setNameColor(EnumChatFormatting color) {
		this.nameColor = color;
		return this;
	}

	/** 说明文字颜色。传 null 表示不加前缀，让语言文件里的 § 代码自己说话。 */
	public ItemWithDesc setDescColor(EnumChatFormatting color) {
		this.descColor = color;
		return this;
	}

	/** 自动换行宽度（半角单位）。0 = 关闭，只认语言文件里的 $。 */
	public ItemWithDesc setDescWidth(int width) {
		this.descWidth = width < 0 ? 0 : width;
		return this;
	}

	/** 说明文字里 %s / %d 的实参。 */
	public ItemWithDesc setDescArgs(Object... args) {
		this.descArgs = args;
		return this;
	}

	/** 加附魔光效（不影响任何属性，纯视觉）。 */
	public ItemWithDesc setEffect() {
		this.glow = true;
		return this;
	}

	/* ------------------------------------------------------------------ 覆盖 */

	@Override
	public EnumRarity getRarity(ItemStack stack) {
		return this.rarity != null ? this.rarity : super.getRarity(stack);
	}

	@Override
	@SideOnly(Side.CLIENT)
	public boolean hasEffect(ItemStack stack) {
		// 保留父类的判断，否则本来就带附魔的物品会失去那层闪光
		return this.glow || super.hasEffect(stack);
	}

	/**
	 * 名称染色。
	 *
	 * <p>只在客户端逻辑端拼接颜色代码：服务端拿到的是干净名字，
	 * 成就广播、死亡消息、统计排行里不会混进 {@code §x} 这种字符。
	 * 集成服务端里客户端线程与服务器线程同 JVM，{@code getEffectiveSide()}
	 * 会按当前线程区分，所以单人游戏也能正确工作。</p>
	 */
	@Override
	public String getItemStackDisplayName(ItemStack stack) {
		String name = super.getItemStackDisplayName(stack);
		if(this.nameColor == null) return name;
		if(FMLCommonHandler.instance().getEffectiveSide() != Side.CLIENT) return name;
		return this.nameColor + name;
	}

	/**
	 * 说明文字。键名 {@code item.<unlocalizedName>.desc}。
	 *
	 * <p>处理顺序：查键 → 缺键直接返回 → 有实参就格式化 → 按 {@code $} 分段
	 * → 需要时对每段自动折行 → 上色（行内已有 {@code §} 就不覆盖）。</p>
	 */
	@Override
	@SideOnly(Side.CLIENT)
	@SuppressWarnings("unchecked")
	public void addInformation(ItemStack stack, EntityPlayer player, List list, boolean advanced) {
		String key = this.getUnlocalizedName() + DESC_SUFFIX;

		// translateToLocal 在键不存在时原样返回键本身，据此判断有没有配说明
		String raw = StatCollector.translateToLocal(key);
		if(raw == null || raw.isEmpty() || raw.equals(key)) return;

		if(this.descArgs != null && this.descArgs.length > 0) {
			raw = StatCollector.translateToLocalFormatted(key, this.descArgs);
		}

		for(String para : raw.split("\\$")) {
			// 连续两个 $ 会产生空段，正好用来手动留空行
			if(para.isEmpty()) {
				list.add("");
				continue;
			}

			List<String> lines = this.descWidth > 0 ? wrap(para, this.descWidth) : Collections.singletonList(para);
			for(String line : lines) {
				// 语言文件里已经写了颜色代码的行不再套默认灰，否则会把 §c 这类覆盖掉
				if(this.descColor == null || line.indexOf('\u00a7') >= 0) {
					list.add(line);
				} else {
					list.add(this.descColor + line);
				}
			}
		}
	}

	/* ------------------------------------------------------------------ 自动折行 */

	/**
	 * 按显示宽度折行。
	 *
	 * <p>优先在空格处断（英文描述不会把单词劈开），没有空格可用就按宽度硬断
	 * （中文本来就该这么断）。{@code §x} 这类颜色代码整对跳过、不占宽度。</p>
	 *
	 * @param text     单段文本（已按 $ 切好）
	 * @param maxWidth 一行允许的最大半角宽度
	 */
	protected static List<String> wrap(String text, int maxWidth) {
		List<String> lines = new ArrayList<String>();
		StringBuilder line = new StringBuilder();
		int width = 0;
		int lastSpace = -1;

		for(int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);

			// 颜色代码是两字符一组，成对搬运，不参与宽度计算
			if(c == '\u00a7' && i + 1 < text.length()) {
				line.append(c).append(text.charAt(++i));
				continue;
			}

			int w = charWidth(c);

			if(width + w > maxWidth && line.length() > 0) {
				if(lastSpace > 0) {
					// 回退到最近的空格：前半行入表，后半行接着排
					String head = line.substring(0, lastSpace);
					String tail = line.substring(lastSpace + 1);
					lines.add(head);
					line = new StringBuilder(tail);
					width = measure(tail);
					lastSpace = tail.lastIndexOf(' ');
				} else {
					lines.add(line.toString());
					line = new StringBuilder();
					width = 0;
				}
			}

			if(c == ' ') lastSpace = line.length();
			line.append(c);
			width += w;
		}

		if(line.length() > 0) lines.add(line.toString());
		return lines;
	}

	/** 一段文本的显示宽度，颜色代码不计。 */
	protected static int measure(String text) {
		int width = 0;
		for(int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			if(c == '\u00a7' && i + 1 < text.length()) {
				i++;
				continue;
			}
			width += charWidth(c);
		}
		return width;
	}

	/**
	 * 单字符显示宽度：ASCII 记 1，CJK 与全角符号记 2。
	 *
	 * <p>MC 的物品提示框里 ASCII 约 6px、汉字约 9px，按 2 倍算虽然略微高估，
	 * 但折行偏保守比溢出到框外好。</p>
	 */
	protected static int charWidth(char c) {
		if(c < 0x80) return 1;
		if(c == 0x2329 || c == 0x232A) return 2;
		if(c >= 0x1100 && c <= 0x115F) return 2;   // 谚文字母
		if(c >= 0x2E80 && c <= 0xA4CF) return 2;   // CJK 部首、假名、汉字
		if(c >= 0xAC00 && c <= 0xD7A3) return 2;   // 谚文音节
		if(c >= 0xF900 && c <= 0xFAFF) return 2;   // CJK 兼容汉字
		if(c >= 0xFE30 && c <= 0xFE6F) return 2;   // CJK 兼容形式
		if(c >= 0xFF00 && c <= 0xFF60) return 2;   // 全角 ASCII
		if(c >= 0xFFE0 && c <= 0xFFE6) return 2;   // 全角符号
		return 1;
	}
}
