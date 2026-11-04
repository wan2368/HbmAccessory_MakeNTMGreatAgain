package com.makentmgreatagain.init;

import net.minecraft.stats.Achievement;


public class ModAchievements {

	/** 逃离深渊：图标用香烟，置于成就页 (0,0) */
	public static Achievement escapeAbyss;

	public static void init() {
		escapeAbyss = new Achievement("escape_abyss", "escape_abyss", 0, 0, ModItems.cigarette_drx, null);
		escapeAbyss.registerStat();
	}
}
