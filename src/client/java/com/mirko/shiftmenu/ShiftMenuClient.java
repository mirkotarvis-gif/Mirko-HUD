package com.mirko.shiftmenu;

import com.mirko.shiftmenu.gui.HudEditorScreen;
import com.mirko.shiftmenu.gui.ModMenuScreen;
import com.mirko.shiftmenu.module.HudModule;
import com.mirko.shiftmenu.module.ModuleManager;
import com.mirko.shiftmenu.util.CombatTracker;
import com.mirko.shiftmenu.util.Keys;
import com.mirko.shiftmenu.util.Placeholders;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.resources.Identifier;

public class ShiftMenuClient implements ClientModInitializer {
	public static final String MOD_ID = "shiftmenu";

	@Override
	public void onInitializeClient() {
		ModuleManager.init();
		Config.load();
		// colors were loaded from the config: mark the current theme as already applied
		ModuleManager.SETTINGS.appliedTheme = ModuleManager.SETTINGS.theme.get();
		CombatTracker.register();

		HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(MOD_ID, "hud"), ShiftMenuClient::renderHud);

		// Custom crosshair replaces the vanilla one while enabled.
		HudElementRegistry.replaceElement(VanillaHudElements.CROSSHAIR, vanilla -> (HudElement) (g, dt) -> {
			if (ModuleManager.CROSSHAIR.isEnabled()) {
				Minecraft mc = Minecraft.getInstance();
				if (ModuleManager.CROSSHAIR.shouldDraw(mc)) ModuleManager.CROSSHAIR.drawCentered(g);
			} else {
				vanilla.render(g, dt);
			}
		});

		// Potion Effects can hide the vanilla effect icons in the top right.
		HudElementRegistry.replaceElement(VanillaHudElements.STATUS_EFFECTS, vanilla -> (HudElement) (g, dt) -> {
			if (!(ModuleManager.POTIONS.isEnabled() && ModuleManager.POTIONS.hideVanilla.on())) {
				vanilla.render(g, dt);
			}
		});

		ModuleManager.VANILLA_HUD.install();

		ClientTickEvents.START_CLIENT_TICK.register(mc -> {
			Keys.poll(mc);
			ModuleManager.TOGGLE_SPRINT.applyKeys(mc);
		});

		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			Keys.poll(mc);
			Placeholders.tick(mc);
			CombatTracker.tick(mc);
			com.mirko.shiftmenu.util.Waypoints.tick(mc);
			// per-module toggle keys
			boolean inGame = mc.screen == null && mc.player != null;
			for (HudModule m : ModuleManager.MODULES) {
				if (!m.toggleKey.isBound()) continue;
				if (m.toggleKey.pressed(inGame && Keys.isKeyDown(mc, m.toggleKey.get()))) {
					m.toggle();
					com.mirko.shiftmenu.util.Toasts.show(m.name, m.isEnabled() ? "Enabled" : "Disabled",
							m.isEnabled() ? ModuleManager.SETTINGS.enabledColor.color() : ModuleManager.SETTINGS.disabledColor.color());
					Config.save();
				}
			}
			for (HudModule m : ModuleManager.MODULES) {
				m.tick(mc);
			}
			ModuleManager.TOGGLE_SPRINT.applyKeys(mc);

			if (Keys.consumeOpenRequest() && mc.screen == null && mc.player != null) {
				mc.setScreen(new HudEditorScreen());
			}
		});
	}

	private static void renderHud(GuiGraphics g, DeltaTracker deltaTracker) {
		Minecraft mc = Minecraft.getInstance();
		// Poll every frame too, so very quick taps / clicks aren't missed between ticks.
		Keys.poll(mc);

		if (mc.options.hideGui || mc.player == null) return;
		if (mc.screen instanceof HudEditorScreen || mc.screen instanceof ModMenuScreen) return;
		String vis = ModuleManager.SETTINGS.hudVisibility.get();
		if (mc.screen != null) {
			if (vis.equals("Hide In Menus")) return;
			if (vis.equals("Hide In Menus Except Chat") && !(mc.screen instanceof ChatScreen)) return;
		}

		for (HudModule m : ModuleManager.MODULES) {
			if (m.isEnabled() && m.isDraggable()) m.renderAt(g, false);
		}
		com.mirko.shiftmenu.util.Toasts.render(g);
	}
}
