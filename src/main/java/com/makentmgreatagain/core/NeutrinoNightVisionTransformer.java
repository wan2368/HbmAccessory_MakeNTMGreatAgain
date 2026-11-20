package com.makentmgreatagain.core;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/** Reuses the vanilla lightmap/fog night-vision branches without applying a potion. */
public final class NeutrinoNightVisionTransformer implements IClassTransformer {

	private static final String RENDERER = "net.minecraft.client.renderer.EntityRenderer";
	private static final String HOOK = "com/makentmgreatagain/client/NeutrinoNightVision";

	@Override
	public byte[] transform(String name, String transformedName, byte[] basicClass) {
		if(basicClass == null || !(RENDERER.equals(transformedName) || RENDERER.equals(name) || "blt".equals(name))) {
			return basicClass;
		}

		try {
			ClassNode node = new ClassNode();
			new ClassReader(basicClass).accept(node, 0);
			// The default coremod order can see Notch bytecode before FML's runtime remapper.
			boolean notch = "blt".equals(node.name);
			String potion = notch ? "Lrv;" : "Lnet/minecraft/potion/Potion;";
			String player = notch ? "Lyz;" : "Lnet/minecraft/entity/player/EntityPlayer;";
			int lightmap = 0, fog = 0, brightness = 0, camera = 0, world = 0;
			for(MethodNode method : node.methods) {
				if(matches(method, "(F)V", "updateLightmap", "func_78472_g", notch, "i")) {
					lightmap += patchNightVisionCheck(method, potion, "lightmapNightVision", notch);
				} else if(matches(method, "(F)V", "updateFogColor", "func_78466_h", notch, "j")) {
					fog += patchNightVisionCheck(method, potion, "fogNightVision", notch);
				} else if(matches(method, "(" + player + "F)F", "getNightVisionBrightness", "func_82830_a", notch, "a")) {
					brightness += patchBrightness(method, potion, notch);
				} else if(matches(method, "(F)V", "updateCameraAndRender", "func_78480_b", notch, "b")) {
					camera += patchLightmapRefresh(node.name, method, notch);
				} else if(matches(method, "(FJ)V", "renderWorld", "func_78471_a", notch, "a")) {
					world += patchLightmapRefresh(node.name, method, notch);
				}
			}

			// A partial patch could take the night-vision branch with no effect to read.
			if(lightmap != 1 || fog != 1 || brightness != 1 || camera != 1 || world != 1) {
				System.err.printf("[MakeNTMGreatAgain] Neutrino night vision patch skipped: lightmap=%d fog=%d brightness=%d camera=%d world=%d%n",
						lightmap, fog, brightness, camera, world);
				return basicClass;
			}
			ClassWriter writer = new ClassWriter(0); // No changes to control flow, max stack, or frame types.
			node.accept(writer);
			System.out.println("[MakeNTMGreatAgain] Neutrino night vision renderer hooks installed");
			return writer.toByteArray();
		} catch(RuntimeException e) {
			System.err.println("[MakeNTMGreatAgain] Failed to patch Neutrino night vision renderer");
			e.printStackTrace();
			return basicClass;
		}
	}

	private static boolean matches(MethodNode method, String desc, String mcp, String srg, boolean notch, String obf) {
		return method.desc.equals(desc) && named(method.name, mcp, srg, notch, obf);
	}

	private static boolean named(String actual, String mcp, String srg, boolean notch, String obf) {
		return notch ? actual.equals(obf) : actual.equals(mcp) || actual.equals(srg);
	}

	private static int patchNightVisionCheck(MethodNode method, String potion, String hook, boolean notch) {
		int count = 0;
		for(AbstractInsnNode insn : method.instructions.toArray()) {
			if(!(insn instanceof FieldInsnNode) || insn.getOpcode() != Opcodes.GETSTATIC) continue;
			FieldInsnNode field = (FieldInsnNode) insn;
			if(!isNightVisionField(field, potion, notch)) continue;
			AbstractInsnNode next = field.getNext();
			if(!(next instanceof MethodInsnNode) || next.getOpcode() != Opcodes.INVOKEVIRTUAL) continue;
			MethodInsnNode check = (MethodInsnNode) next;
			if(!check.desc.equals("(" + potion + ")Z") || !named(check.name, "isPotionActive", "func_70644_a", notch, "a")) continue;
			method.instructions.insert(check, call(hook, "(Z)Z"));
			count++;
		}
		return count;
	}

	private static boolean isNightVisionField(FieldInsnNode field, String potion, boolean notch) {
		return field.owner.equals(notch ? "rv" : "net/minecraft/potion/Potion") && field.desc.equals(potion)
				&& named(field.name, "nightVision", "field_76439_r", notch, "r");
	}

	private static int patchBrightness(MethodNode method, String potion, boolean notch) {
		String effect = notch ? "Lrw;" : "Lnet/minecraft/potion/PotionEffect;";
		String living = notch ? "Lsv;" : "Lnet/minecraft/entity/EntityLivingBase;";
		int count = 0;
		for(AbstractInsnNode insn : method.instructions.toArray()) {
			if(!(insn instanceof FieldInsnNode) || insn.getOpcode() != Opcodes.GETSTATIC
					|| !isNightVisionField((FieldInsnNode) insn, potion, notch)) continue;
			AbstractInsnNode next = insn.getNext();
			if(!(next instanceof MethodInsnNode) || next.getOpcode() != Opcodes.INVOKEVIRTUAL) continue;
			MethodInsnNode getEffect = (MethodInsnNode) next;
			if(!getEffect.desc.equals("(" + potion + ")" + effect)
					|| !named(getEffect.name, "getActivePotionEffect", "func_70660_b", notch, "b")) continue;
			next = getEffect.getNext();
			if(!(next instanceof MethodInsnNode) || next.getOpcode() != Opcodes.INVOKEVIRTUAL) continue;
			MethodInsnNode duration = (MethodInsnNode) next;
			if(!duration.desc.equals("()I") || !named(duration.name, "getDuration", "func_76459_b", notch, "b")) continue;
			method.instructions.set(getEffect, call("nightVisionDuration", "(" + living + potion + ")I"));
			method.instructions.remove(duration);
			count++;
		}
		return count;
	}

	private static int patchLightmapRefresh(String renderer, MethodNode method, boolean notch) {
		int count = 0;
		for(AbstractInsnNode insn : method.instructions.toArray()) {
			if(!(insn instanceof FieldInsnNode) || insn.getOpcode() != Opcodes.GETFIELD) continue;
			FieldInsnNode field = (FieldInsnNode) insn;
			if(!field.owner.equals(renderer) || !field.desc.equals("Z")
					|| !named(field.name, "lightmapUpdateNeeded", "field_78536_aa", notch, "ak")) continue;
			method.instructions.insert(field, call("refreshLightmap", "(Z)Z"));
			count++;
		}
		return count;
	}

	private static MethodInsnNode call(String name, String desc) {
		return new MethodInsnNode(Opcodes.INVOKESTATIC, HOOK, name, desc, false);
	}
}
