package com.makentmgreatagain.render;

import net.minecraft.util.IIcon;

/**
 * 子图标：把一张大图（如 32×32）按 2×2 切成四块，本实例只代表其中一块，
 * 并且每一块可以独立水平/垂直镜像（1.7.10 的 IIcon 接口 U/V 独立映射，
 * 只能表达镜像与 180°（双镜像），做不了 90° 转置——要 90° 请改 PNG 本身）。
 *
 * UV 在 getter 里实时从父图标计算——注册阶段父图标的 minU/maxU 还没被
 * 图集合成器填充，构造时算会全是 0。
 */
public class IconSub implements IIcon {

	private final IIcon parent;
	private final int col;      // 0=左半 1=右半
	private final int row;      // 0=上半 1=下半
	private final boolean flipU;// 水平镜像
	private final boolean flipV;// 垂直镜像

	public IconSub(IIcon parent, int col, int row, boolean flipU, boolean flipV) {
		this.parent = parent;
		this.col = col;
		this.row = row;
		this.flipU = flipU;
		this.flipV = flipV;
	}

	private float w() { return (parent.getMaxU() - parent.getMinU()) / 2F; }
	private float h() { return (parent.getMaxV() - parent.getMinV()) / 2F; }

	// 镜像 = 交换 min/max，渲染器按 min→max 走 UV 时贴图自然翻转
	@Override public float getMinU() { return flipU ? parent.getMinU() + (col + 1) * w() : parent.getMinU() + col * w(); }
	@Override public float getMaxU() { return flipU ? parent.getMinU() + col * w() : parent.getMinU() + (col + 1) * w(); }
	@Override public float getMinV() { return flipV ? parent.getMinV() + (row + 1) * h() : parent.getMinV() + row * h(); }
	@Override public float getMaxV() { return flipV ? parent.getMinV() + row * h() : parent.getMinV() + (row + 1) * h(); }

	@Override public int getIconWidth() { return parent.getIconWidth() / 2; }
	@Override public int getIconHeight() { return parent.getIconHeight() / 2; }
	@Override public float getInterpolatedU(double p) { return getMinU() + (getMaxU() - getMinU()) * (float) p / 16F; }
	@Override public float getInterpolatedV(double p) { return getMinV() + (getMaxV() - getMinV()) * (float) p / 16F; }
	@Override public String getIconName() { return parent.getIconName() + "_sub" + row + col; }
}