package com.makentmgreatagain.client;

import java.util.Collections;
import java.util.List;

import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

import com.makentmgreatagain.client.GuiNeutrinoLocator.Target;
import com.makentmgreatagain.locator.LocatorNetwork;
import com.makentmgreatagain.locator.LocatorNetwork.Position;
import com.makentmgreatagain.locator.LocatorNetwork.Result;

import cpw.mods.fml.client.registry.ClientRegistry;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.common.MinecraftForge;

public final class NeutrinoLocatorClient {

	private static final String TEXT = "gui.makentmgreatagain.neutrino_locator.";
	private static final KeyBinding LOCATE = new KeyBinding("makentmgreatagain.key.neutrinoLocator", Keyboard.KEY_G,
			"makentmgreatagain.key.group");
	private static final String[] DIRECTIONS = {"ahead", "right", "behind", "left"};
	private static final int HOLD_TICKS = 8;
	private static final int TIMEOUT_TICKS = 2400;
	/** 双击判定窗口：两次短按间隔 ≤10tick(0.5s) 视为双击 G 开始寻找；单击 G 取消定位 */
	private static final int DOUBLE_TAP_TICKS = 10;

	private final Minecraft mc = Minecraft.getMinecraft();
	private Target target;
	private World world;
	private List<Position> results = Collections.emptyList();
	private List<Target> scanTargets = Collections.emptyList();
	private Position marker;
	private int sequence, pendingTicks, heldTicks, messageTicks, nearbyMatches;
	private long lastTapTick = -1000;
	private boolean waitForRelease, drewHotbar;
	private String message;

	public static void register() {
		ClientRegistry.registerKeyBinding(LOCATE);
		NeutrinoLocatorClient handler = new NeutrinoLocatorClient();
		FMLCommonHandler.instance().bus().register(handler);
		MinecraftForge.EVENT_BUS.register(handler);
	}

	@SubscribeEvent
	public void onTick(TickEvent.ClientTickEvent event) {
		if(event.phase != TickEvent.Phase.END) return;
		if(world != mc.theWorld) {
			world = mc.theWorld;
			target = null;
			resetScan();
		}
		boolean active = world != null && LocatorNetwork.canUse(mc.thePlayer);
		boolean pressed = false;
		while(LOCATE.isPressed()) pressed = true;
		boolean down = LOCATE.getIsKeyPressed();
		if(!active) resetScan();
		if(!active || mc.currentScreen != null || !mc.inGameHasFocus) {
			heldTicks = 0;
			waitForRelease = down;
		} else if(!down) {
			if(!waitForRelease && (heldTicks > 0 || pressed)) {
				long now = world.getTotalWorldTime();
				if(now - lastTapTick <= DOUBLE_TAP_TICKS) {
					// 双击 G：开始寻找（有样本或上次目标时扫描）
					lastTapTick = -1000;
					if(target != null || !GuiNeutrinoLocator.currentSamples().isEmpty()) scanAll();
				} else {
					// 单击 G：取消当前定位显示；无定位时单击无动作
					lastTapTick = now;
					if(marker != null || !results.isEmpty() || pendingTicks > 0) cancelLocate();
				}
			}
			heldTicks = 0;
			waitForRelease = false;
		} else if(!waitForRelease && (pressed || heldTicks > 0) && ++heldTicks >= HOLD_TICKS) {
			waitForRelease = true;
			heldTicks = 0;
			openSelector(LOCATE.getKeyCode());
		}

		Result reply;
		while((reply = LocatorNetwork.poll()) != null) {
			if(!active || pendingTicks <= 0 || reply.sequence != sequence || reply.dimension != mc.thePlayer.dimension) continue;
			pendingTicks = 0;
			results = reply.positions;
			switch(reply.status) {
			case LocatorNetwork.OK:
				selectMatchedTarget();
				messageTicks = 0;
				break;
			case LocatorNetwork.NO_MATCH:
				target = null;
				notify("no_match");
				break;
			case LocatorNetwork.COOLDOWN: notify("cooldown"); break;
			case LocatorNetwork.FORBIDDEN: notify("disabled"); break;
			case LocatorNetwork.INVALID_TARGET: notify("invalid"); break;
			case LocatorNetwork.BUSY: notify("busy"); break;
			default: notify("cancelled"); break;
			}
		}
		if(pendingTicks > 0 && --pendingTicks == 0) notify("timeout");
		if(messageTicks > 0) --messageTicks;
		if(active && !results.isEmpty()) updateMarker();
	}

	private void openSelector(int openingKey) {
		mc.displayGuiScreen(new GuiNeutrinoLocator(this::scan, openingKey));
	}

	/** Keep the GUI callback simple; the selected sample is read when scanning. */
	private boolean scan(Target selection) {
		if(pendingTicks > 0) {
			notify("busy");
			return true;
		}
		return scanAll();
	}

	/** Scan the selected sample with one shared five-second cooldown; fall back to the last target if empty. */
	private boolean scanAll() {
		if(world != mc.theWorld || !LocatorNetwork.canUse(mc.thePlayer)) return false;
		if(pendingTicks > 0) {
			notify("busy");
			return false;
		}
		List<Target> samples = GuiNeutrinoLocator.currentSamples();
		if(samples.isEmpty()) {
			if(target == null) return false;
			samples = Collections.singletonList(target);
		}
		scanTargets = new java.util.ArrayList<>(samples);
		target = samples.get(0);
		results = Collections.emptyList();
		marker = null;
		messageTicks = 0;
		pendingTicks = TIMEOUT_TICKS;
		List<LocatorNetwork.TargetSpec> specs = new java.util.ArrayList<>(samples.size());
		for(Target t : samples) specs.add(new LocatorNetwork.TargetSpec(t.blockId, t.meta, t.oreKey == null ? "" : t.oreKey));
		LocatorNetwork.request(++sequence, specs);
		return true;
	}

	private void selectMatchedTarget() {
		if(results.isEmpty()) {
			target = null;
			return;
		}
		int targetIndex = results.get(0).targetIndex;
		target = targetIndex >= 0 && targetIndex < scanTargets.size() ? scanTargets.get(targetIndex) : null;
	}

	private void resetScan() {
		scanTargets = Collections.emptyList();
		pendingTicks = messageTicks = nearbyMatches = 0;
		results = Collections.emptyList();
		marker = null;
	}

	/** 单击 G：取消当前定位显示（保留样本目标，双击 G 可重新扫描） */
	private void cancelLocate() {
		resetScan();
		notify("cancelled");
	}

	private void notify(String key) {
		message = key;
		messageTicks = 55;
	}

	private void updateMarker() {
		Position previous = marker;
		marker = null;
		nearbyMatches = 0;
		double nearest = Double.POSITIVE_INFINITY;
		for(Position pos : results) {
			double distance = mc.thePlayer.getDistanceSq(pos.x + 0.5, pos.y + 0.5, pos.z + 0.5);
			if(!world.blockExists(pos.x, pos.y, pos.z)) continue;
			if(Block.getIdFromBlock(world.getBlock(pos.x, pos.y, pos.z)) != pos.blockId
					|| world.getBlockMetadata(pos.x, pos.y, pos.z) != pos.meta) continue;
			++nearbyMatches;
			if(distance < nearest || marker == null) {
				nearest = distance;
				marker = pos;
			}
		}
		if(previous != null && marker == null) notify("lost");
	}

	@SubscribeEvent
	public void onHudStart(RenderGameOverlayEvent.Pre event) {
		if(event.type == RenderGameOverlayEvent.ElementType.ALL) {
			drewHotbar = false;
		}
	}

	@SubscribeEvent(priority = EventPriority.LOWEST)
	public void onHud(RenderGameOverlayEvent.Post event) {
		if(event.type == RenderGameOverlayEvent.ElementType.HOTBAR) drewHotbar = true;
		if(event.type != RenderGameOverlayEvent.ElementType.ALL || !drewHotbar || mc.currentScreen != null
				|| mc.gameSettings.hideGUI || mc.gameSettings.showDebugInfo || target == null
				|| !LocatorNetwork.canUse(mc.thePlayer)) return;
		int width = event.resolution.getScaledWidth();
		int screenHeight = event.resolution.getScaledHeight();
		int y = screenHeight - 58 - mc.fontRenderer.FONT_HEIGHT;
		int maxWidth = Math.min(width - 12, 300);
		if(y < 4 || maxWidth < 40) return;
		String text;
		String extra = null;
		if(pendingTicks > 0) text = I18n.format(TEXT + "scanning", target.label);
		else if(messageTicks > 0) text = I18n.format(TEXT + message);
		else if(marker != null) {
			double dx = marker.x + 0.5 - mc.thePlayer.posX;
			double dz = marker.z + 0.5 - mc.thePlayer.posZ;
			int dy = marker.y - MathHelper.floor_double(mc.thePlayer.posY);
			String height = dy == 0 ? I18n.format(TEXT + "level") : I18n.format(TEXT + (dy > 0 ? "above" : "below"), Math.abs(dy));
			double yaw = Math.toDegrees(Math.atan2(-dx, dz)) - mc.thePlayer.rotationYaw;
			int direction = MathHelper.floor_double(yaw / 90D + 0.5D) & 3;
			int distance = (int) Math.round(Math.sqrt(mc.thePlayer.getDistanceSq(marker.x + 0.5, marker.y + 0.5, marker.z + 0.5)));
			String directionName = I18n.format(TEXT + DIRECTIONS[direction]);
			String suffix = I18n.format(TEXT + "reading", "", directionName, distance, height);
			String label = mc.fontRenderer.trimStringToWidth(target.label, Math.max(0, maxWidth - mc.fontRenderer.getStringWidth(suffix)));
			text = I18n.format(TEXT + "reading", label, directionName, distance, height);
			if(nearbyMatches > 1) {
				extra = I18n.format(TEXT + (results.size() == LocatorNetwork.MAX_RESULTS ? "multiple_min" : "multiple"), nearbyMatches);
			}
		} else return;
		if(extra != null) {
			String combined = text + "  |  " + extra;
			if(mc.fontRenderer.getStringWidth(combined) <= maxWidth) text = combined;
			else {
				int upperY = y - mc.fontRenderer.FONT_HEIGHT - 2;
				if(upperY >= 4) drawHudLine(extra, width, maxWidth, upperY);
			}
		}
		drawHudLine(text, width, maxWidth, y);
	}

	private void drawHudLine(String text, int width, int maxWidth, int y) {
		text = mc.fontRenderer.trimStringToWidth(text, maxWidth);
		mc.fontRenderer.drawStringWithShadow(text, (width - mc.fontRenderer.getStringWidth(text)) / 2, y, 0x8CE5FF);
	}

	@SubscribeEvent
	public void onWorldRender(RenderWorldLastEvent event) {
		if(marker == null || mc.currentScreen != null || mc.gameSettings.hideGUI || !LocatorNetwork.canUse(mc.thePlayer)) return;
		EntityLivingBase view = mc.renderViewEntity;
		if(view == null) return;
		float partial = event.partialTicks;
		double x = marker.x - (view.lastTickPosX + (view.posX - view.lastTickPosX) * partial);
		double y = marker.y - (view.lastTickPosY + (view.posY - view.lastTickPosY) * partial);
		double z = marker.z - (view.lastTickPosZ + (view.posZ - view.lastTickPosZ) * partial);
		GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT | GL11.GL_LINE_BIT | GL11.GL_CURRENT_BIT);
		try {
			GL11.glDisable(GL11.GL_TEXTURE_2D);
			GL11.glDisable(GL11.GL_DEPTH_TEST);
			GL11.glDisable(GL11.GL_LIGHTING);
			GL11.glEnable(GL11.GL_BLEND);
			GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
			GL11.glDepthMask(false);
			GL11.glLineWidth(1.5F);
			RenderGlobal.drawOutlinedBoundingBox(AxisAlignedBB.getBoundingBox(x, y, z, x + 1, y + 1, z + 1).expand(0.008, 0.008, 0.008), 0x72DFFF);
		} finally {
			GL11.glPopAttrib();
		}
	}
}
