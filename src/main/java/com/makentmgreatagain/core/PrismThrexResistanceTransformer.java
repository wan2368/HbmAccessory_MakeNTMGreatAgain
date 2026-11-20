package com.makentmgreatagain.core;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * 让 Threx 在 PRISM 辐射系统里获得独立阻力值，绕开 HBM 写死的 100 上限。
 *
 * <h2>改哪一行</h2>
 * {@code ChunkRadiationHandlerPRISM$SubChunk#rebuild} 里：
 * <pre>
 * float resistance = Math.min(b.getExplosionResistance(null, world, ...), 100);
 * </pre>
 * 改写成：
 * <pre>
 * float resistance = ThrexRadResistance.apply(b, Math.min(...));
 * </pre>
 *
 * <h2>字节码操作</h2>
 * <ol>
 *   <li>定位 {@code INVOKEVIRTUAL Block.getExplosionResistance}；</li>
 *   <li>从调用点往前找第一条 {@code ACONST_NULL} —— 那是参数表里的 {@code Entity}，
 *       它前面那条 {@code ALOAD} 就是把 block 引用压上来的指令；</li>
 *   <li>在 {@code ACONST_NULL} 之前插 {@code DUP}，把 block 引用复制一份留在栈底；</li>
 *   <li>把紧随其后的 {@code Math.min(FF)F} 换成 {@code apply(Block;F)F}。</li>
 * </ol>
 *
 * <p>栈的净变化为 0（{@code [Block, float]} + 一个 float → 一个 float），
 * 没有新增跳转标签，所以只需要 {@code COMPUTE_MAXS}，不需要重算栈帧 ——
 * 和其它几个补丁不同，这里不必手补 {@code FrameNode}。</p>
 *
 * <p><b>易错点</b>：{@code apply} 必须插在 {@code Math.min} <b>之后</b>，不能替换
 * {@code Math.min}。替换的话调用发生时栈顶还压着 {@code LDC 100.0F}，
 * 验证器会报「Argument 1: expected Block, but found F」—— 这个坑已经踩过一次。</p>
 *
 * <p>先找 {@code Math.min} 再插 {@code DUP}，是为了让「找到了 getExplosionResistance
 * 但没有对应的 min」这种情况不留下半截补丁（漏插 DUP 只会少一个加权，
 * 但多插一个 DUP 会让栈失衡直接 VerifyError）。</p>
 *
 * <p>同一类的 {@code getResistanceFromArray} 还有一个边界问题：循环从 1 到 15，
 * 永远漏掉出射方向最外侧的第 0 或第 15 层切片。这里在返回值写回临时槽位后，
 * 追加 {@code ThrexRadResistance.includeBoundarySlice}；钩子只在边界层已经含有
 * Threx 级高阻力时补回 1/15，普通 HBM 方块保持原算法。</p>
 */
public class PrismThrexResistanceTransformer implements IClassTransformer {

	private static final String TARGET = "com.hbm.handler.radiation.ChunkRadiationHandlerPRISM$SubChunk";
	private static final String HOOK = "com/makentmgreatagain/hook/ThrexRadResistance";
	private static final String GET_RESIST = "getExplosionResistance";
	private static final String REBUILD = "rebuild";
	private static final String GET_RESISTANCE = "getResistanceFromArray";

	@Override
	public byte[] transform(String name, String transformedName, byte[] basicClass) {

		if(basicClass == null || transformedName == null) return basicClass;
		if(!transformedName.equals(TARGET)) return basicClass;

		try {
			return patch(basicClass, transformedName);
		} catch(Throwable t) {
			// HBM 换版本、字节码形态对不上时放弃补丁而不是崩启动
			System.out.println("[MakeNTMGreatAgain] PRISM 阻力补丁跳过（与本版本字节码不匹配）: " + transformedName);
			t.printStackTrace();
			return basicClass;
		}
	}

	private byte[] patch(byte[] bytes, String cls) {

		ClassReader reader = new ClassReader(bytes);
		ClassNode node = new ClassNode();
		reader.accept(node, 0);

		int count = 0;

		for(MethodNode m : node.methods) {

			if(!m.name.equals(REBUILD)) continue;

			// 1. 定位取爆炸抗性的那次虚调用
			AbstractInsnNode call = null;
			for(AbstractInsnNode ins = m.instructions.getFirst(); ins != null; ins = ins.getNext()) {
				if(ins.getOpcode() == Opcodes.INVOKEVIRTUAL && ins instanceof MethodInsnNode) {
					MethodInsnNode mi = (MethodInsnNode) ins;
					if(GET_RESIST.equals(mi.name)) { call = mi; break; }
				}
			}
			if(call == null) continue;

			// 2. 先确认后面真的跟着 Math.min，否则不动字节码
			AbstractInsnNode minCall = null;
			for(AbstractInsnNode ins = call.getNext(); ins != null; ins = ins.getNext()) {
				if(ins.getOpcode() == Opcodes.INVOKESTATIC && ins instanceof MethodInsnNode) {
					MethodInsnNode mi = (MethodInsnNode) ins;
					if("java/lang/Math".equals(mi.owner) && "min".equals(mi.name) && "(FF)F".equals(mi.desc)) {
						minCall = mi;
						break;
					}
					// 跨到别的静态调用就说明结构不是预期形态，放弃
					break;
				}
				if(ins.getOpcode() == Opcodes.FSTORE) break;
			}
			if(minCall == null) continue;

			// 3. 参数表里 Entity 传的是 null，第一条 ACONST_NULL 就是它，
			//    它前面那条 ALOAD 才是 block 的接收者
			AbstractInsnNode nullPush = call.getPrevious();
			while(nullPush != null && nullPush.getOpcode() != Opcodes.ACONST_NULL) {
				nullPush = nullPush.getPrevious();
			}
			if(nullPush == null) continue;

			m.instructions.insertBefore(nullPush, new InsnNode(Opcodes.DUP));
			// 注意是插在 Math.min 之后，不是替换它：min 执行完栈上才是
			// [Block, float]（Block 来自上面的 DUP），正好是 apply 的两个入参。
			// 若直接替换 min，此刻栈上还停着 LDC 100.0F，类型会对不上。
			m.instructions.insert(minCall, new MethodInsnNode(Opcodes.INVOKESTATIC, HOOK,
					"apply", "(Lnet/minecraft/block/Block;F)F", false));
			count++;
		}

		if(count == 0) {
			System.out.println("[MakeNTMGreatAgain] 警告: " + cls + " 未找到阻力计算点，Threx 的辐射加权不生效");
			return bytes;
		}

		boolean boundaryPatched = false;
		for(MethodNode m : node.methods) {
			if(!m.name.equals(GET_RESISTANCE) || !m.desc.equals("([FZ)F")) continue;
			for(AbstractInsnNode ins = m.instructions.getFirst(); ins != null; ins = ins.getNext()) {
				if(ins.getOpcode() != Opcodes.FRETURN) continue;
				// 保存原返回值到方法现有参数之后的临时槽位，再交给钩子补回边界层。
				InsnList call = new InsnList();
				call.add(new VarInsnNode(Opcodes.FSTORE, 3));
				call.add(new VarInsnNode(Opcodes.ALOAD, 1));
				call.add(new VarInsnNode(Opcodes.ILOAD, 2));
				call.add(new VarInsnNode(Opcodes.FLOAD, 3));
				call.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HOOK,
						"includeBoundarySlice", "([FZF)F", false));
				m.instructions.insertBefore(ins, call);
				boundaryPatched = true;
			}
		}

		if(!boundaryPatched) {
			System.out.println("[MakeNTMGreatAgain] 警告: " + cls + " 未找到方向阻力出口，边界切片补偿未生效");
			return bytes;
		}

		System.out.println("[MakeNTMGreatAgain] " + cls + " 已加装 Threx 辐射阻力加权（" + count + " 处）及方向边界补偿");

		ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);
		node.accept(writer);
		return writer.toByteArray();
	}
}
