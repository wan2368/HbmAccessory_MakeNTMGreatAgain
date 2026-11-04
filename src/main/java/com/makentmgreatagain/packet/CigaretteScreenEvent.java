package com.makentmgreatagain.packet;

/**
 * 香烟屏幕效果的跨线程状态槽。
 *
 * <p>FML 简单网络包的处理器可能跑在 netty 线程上，直接在里面调用渲染 API 是不安全的；
 * 而 1.7.10 的 Minecraft 又没有稳定的主线程调度入口可跨版本使用。
 * 这里的做法是：网络线程只写一个 volatile 档位值，客户端渲染管理器
 * （ScreenEffectManager）在下一帧的主线程渲染回调里读取并清零，真正开始效果。</p>
 *
 * <p>本类不引用任何客户端类，专用服务端可以安全加载。</p>
 */
public class CigaretteScreenEvent {

	/** 待处理的档位（1~5），0 表示没有待处理。网络线程写，渲染线程（主线程）读并清零。 */
	public static volatile int pendingStage = 0;
}