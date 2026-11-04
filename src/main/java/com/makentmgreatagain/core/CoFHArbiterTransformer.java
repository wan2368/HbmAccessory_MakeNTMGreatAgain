package com.makentmgreatagain.core;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * 把 CoFH Core 的 OreDictionaryArbiter.initialize() 改成 synchronized。
 *
 * 原因：该方法每次被调用都会重建 4 张静态表（oreIDs / oreStacks / stackIDs / stackNames），
 * 但没有任何同步。整合服务端登录那一下，FMLModIdMappingEvent 会同时在两侧触发，
 * 两个线程一起重建，先 put 的那张表被后一个线程整张换掉，另一条线程再 get 就是 null。
 * 结果就是 NPE 抛在 registerOreDictionaryEntry，被 FML 的 catch(Throwable) 吞掉之后
 * 又在 rejectHandshake 上再 NPE 一次，崩在 FMLProxyPacket，跟真正的原因隔了两层。
 *
 * 只在 jar 里存在 CoFH Core 时生效，找不到类就原样返回。
 */
public class CoFHArbiterTransformer implements IClassTransformer {

	private static final String TARGET = "cofh.core.util.oredict.OreDictionaryArbiter";

	@Override
	public byte[] transform(String name, String transformedName, byte[] basicClass) {
		if(basicClass == null || transformedName == null) return basicClass;
		if(!transformedName.equals(TARGET)) return basicClass;

		try {
			ClassNode node = new ClassNode();
			new ClassReader(basicClass).accept(node, 0);

			boolean changed = false;
			for(MethodNode method : node.methods) {
				if(!method.name.equals("initialize") || !method.desc.equals("()V")) continue;
				if((method.access & Opcodes.ACC_STATIC) == 0) continue;
				if((method.access & Opcodes.ACC_SYNCHRONIZED) != 0) return basicClass;
				method.access |= Opcodes.ACC_SYNCHRONIZED;
				changed = true;
			}

			if(!changed) return basicClass;

			ClassWriter writer = new ClassWriter(0);
			node.accept(writer);
			System.out.println("[MakeNTMGreatAgain] CoFH OreDictionaryArbiter.initialize() 已加同步锁");
			return writer.toByteArray();

		} catch(Throwable t) {
			t.printStackTrace();
			return basicClass;
		}
	}
}
