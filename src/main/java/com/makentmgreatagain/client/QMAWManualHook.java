package com.makentmgreatagain.client;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hbm.qmaw.QMAWLoader;
import com.makentmgreatagain.MakeNTMGreatAgain;

import net.minecraft.client.resources.IResourceManager;
import net.minecraft.client.resources.IResourceManagerReloadListener;

/**
 * QMAW（F1 手册）的第二条注入路径。
 *
 * 正常路径是 ClientProxy 把自己那份 jar 登记给 QMAWLoader，让它按 zip 扫一遍
 * assets/hbm/manual。这条路径依赖「拿得到自己的 jar 文件」，一旦拿不到（CodeSource
 * 为 null、或类被别的加载器装进来），手册页就整批消失，而且没有任何报错。
 *
 * 所以这里不再依赖 jar 文件本身：直接按类路径读自己 assets 里的 json，解析后交给
 * QMAWLoader.registerJson 登记。资源在 jar 里、在开发目录里都读得到。
 *
 * 注册顺序很关键：HBM 在 preInit 里把 QMAWLoader 挂上资源重载监听，这里挂得比它晚，
 * 于是每次资源重载都是它先清表重建、我们后补，不会互相覆盖。
 */
public class QMAWManualHook implements IResourceManagerReloadListener {

	/** 手册页的相对路径，新增页面时在这里补一行 */
	private static final String[] PAGES = {
			"environment_pump.json",
			"environment_pump_media.json",
			"environment_pump_altitude.json",
			"seawater.json"
	};

	private static final String ROOT = "/assets/hbm/manual/makentmgreatagain/";

	@Override
	public void onResourceManagerReload(IResourceManager resMan) {
		injectMissingPages();
	}

	/** 缺哪页补哪页；jar 那条路径已经登记过的直接跳过 */
	public void injectMissingPages() {

		int added = 0;

		for(String page : PAGES) {
			InputStream in = null;
			try {
				in = QMAWManualHook.class.getResourceAsStream(ROOT + page);
				if(in == null) continue;

				JsonObject json = (JsonObject) new JsonParser().parse(new InputStreamReader(in, StandardCharsets.UTF_8));
				if(!json.has("name")) continue;

				String name = json.get("name").getAsString();
				if(QMAWLoader.qmaw.containsKey(name)) continue;

				QMAWLoader.registerJson("makentmgreatagain/" + page, json);
				if(QMAWLoader.qmaw.containsKey(name)) added++;

			} catch(Exception ex) {
				MakeNTMGreatAgain.LOG.warn("[QMAW] 注入手册页失败: " + page, ex);
			} finally {
				try { if(in != null) in.close(); } catch(Exception ignored) { }
			}
		}

		if(added <= 0) return;

		// 不碰 QMAWLoader 的索引容器：索引页是直接遍历 qmaw.values() 的，
		// 而 sortedPages / COMPARATOR 只在 5860 以后的版本里才有，访问它们会在旧版直接 NoSuchFieldError。
		MakeNTMGreatAgain.LOG.info("[QMAW] 已补充 " + added + " 页附属手册（共 " + QMAWLoader.qmaw.size() + " 页）");
	}
}
