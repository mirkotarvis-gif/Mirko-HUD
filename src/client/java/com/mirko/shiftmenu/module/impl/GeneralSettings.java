package com.mirko.shiftmenu.module.impl;

import com.mirko.shiftmenu.module.HudModule;
import com.mirko.shiftmenu.module.setting.BoolSetting;
import com.mirko.shiftmenu.module.setting.ColorSetting;
import com.mirko.shiftmenu.module.setting.ModeSetting;
import com.mirko.shiftmenu.module.setting.NumberSetting;
import net.minecraft.client.gui.GuiGraphics;

/** Not a HUD module: the global "Settings" page (menu theme, editor, HUD, controls). */
public class GeneralSettings extends HudModule {
	// Theme + rendering
	public static final String[] THEMES = {"Mirko Aqua", "Lunar Blue", "Midnight Purple", "Crimson", "Emerald",
			"Sunset", "Rose", "Mono", "Custom"};
	public final ModeSetting theme;
	public final BoolSetting hdRendering;
	public final BoolSetting antiAliasing;
	/** Theme whose colors were last applied (so a theme change re-colors the UI once). */
	public String appliedTheme = "";
	// Menu look
	public final ColorSetting accent;
	public final ColorSetting panelColor;
	public final ColorSetting cardColor;
	public final ColorSetting enabledColor;
	public final ColorSetting disabledColor;
	public final NumberSetting panelRadius;
	public final BoolSetting blur;
	public final BoolSetting animations;
	public final NumberSetting animationSpeed;
	public final ModeSetting cardStyle;
	public final ModeSetting modsView;
	public final ModeSetting sortMode;
	public final BoolSetting notifications;
	public final com.mirko.shiftmenu.module.setting.KeySetting menuKey;
	public final BoolSetting sounds;
	public final ModeSetting columns;
	// Editor
	public final BoolSetting snapCenter;
	public final BoolSetting snapEdges;
	public final BoolSetting snapModules;
	public final NumberSetting gridSnap;
	public final BoolSetting showGrid;
	public final NumberSetting editorDim;
	public final BoolSetting outlines;
	public final ColorSetting menuText;
	public final ColorSetting menuTextDim;
	public final NumberSetting menuWidth;
	public final NumberSetting menuHeight;
	public final NumberSetting cardHeight;
	public final ModeSetting menuFont;
	public final BoolSetting menuBold;
	// HUD
	public final ModeSetting hudFont;
	public final BoolSetting hudBold;
	public final BoolSetting hudItalic;
	public final NumberSetting globalScale;
	public final ModeSetting hudVisibility;
	// Controls
	public final ModeSetting openMode;
	public final NumberSetting tapTime;

	public GeneralSettings() {
		super("general", "Settings", Category.SYSTEM, true, 0, 0);
		settings.remove(scale); // no per-page scale / toggle key here
		settings.remove(toggleKey);

		theme = add(new ModeSetting("Theme", "Mirko Aqua", THEMES).in("Theme"));
		hdRendering = add(new BoolSetting("HD Rendering (Smooth UI)", true).in("Theme"));
		antiAliasing = add(new BoolSetting("Anti-Aliased Edges", true).in("Theme").showIf(this.hdRendering::on));
		accent = add(new ColorSetting("Accent Color", 0xFF1FC7D9).in("Theme"));
		panelColor = add(new ColorSetting("Panel Color", 0xF00C1416).in("Theme"));
		cardColor = add(new ColorSetting("Card Color", 0xFF162024).in("Theme"));
		enabledColor = add(new ColorSetting("Enabled Color", 0xFF20B486).in("Theme"));
		disabledColor = add(new ColorSetting("Disabled Color", 0xFFE0475B).in("Theme"));
		panelRadius = add(new NumberSetting("Corner Radius", 6, 0, 10, 1).in("Menu Look"));
		columns = add(new ModeSetting("Card Columns", "Auto", "Auto", "2", "3", "4", "5", "6").in("Menu Look"));
		menuText = add(new ColorSetting("Text Color", 0xFFFFFFFF).in("Theme"));
		menuTextDim = add(new ColorSetting("Secondary Text Color", 0xFF8FA7AD).in("Theme"));
		menuWidth = add(new NumberSetting("Menu Width", 460, 300, 800, 10).in("Menu Look"));
		menuHeight = add(new NumberSetting("Menu Height", 290, 200, 500, 10).in("Menu Look"));
		cardHeight = add(new NumberSetting("Card Height", 92, 70, 130, 2).in("Menu Look"));
		menuFont = add(new ModeSetting("Menu Font", "Inter", com.mirko.shiftmenu.util.Fonts.NAMES).in("Menu Look"));
		menuBold = add(new BoolSetting("Menu Bold Text", false).in("Menu Look"));
		blur = add(new BoolSetting("Blur Background", true).in("Menu Look"));
		animations = add(new BoolSetting("Animations", true).in("Menu Look"));
		animationSpeed = add(new NumberSetting("Animation Speed", 1.0, 0.3, 3.0, 0.1).in("Menu Look").showIf(this.animations::on));
		cardStyle = add(new ModeSetting("Card Style", "Icon", "Icon", "Live Preview").in("Menu Look"));
		modsView = add(new ModeSetting("Mods View", "Grid", "Grid", "List").in("Menu Look"));
		sortMode = add(new ModeSetting("Sort Mods", "Default", "Default", "A-Z", "Enabled First").in("Menu Look"));
		sounds = add(new BoolSetting("Click Sounds", true).in("Menu Look"));

		snapCenter = add(new BoolSetting("Snap To Center", true).in("HUD Editor"));
		snapEdges = add(new BoolSetting("Snap To Edges", true).in("HUD Editor"));
		snapModules = add(new BoolSetting("Snap To Other Modules", true).in("HUD Editor"));
		gridSnap = add(new NumberSetting("Grid Snap", 0, 0, 20, 1).in("HUD Editor"));
		showGrid = add(new BoolSetting("Show Grid", false).in("HUD Editor").showIf(() -> this.gridSnap.get() > 0));
		editorDim = add(new NumberSetting("Background Dim", 33, 0, 90, 1).in("HUD Editor"));
		outlines = add(new BoolSetting("Module Outlines", true).in("HUD Editor"));

		hudFont = add(new ModeSetting("Default HUD Font", "Minecraft", com.mirko.shiftmenu.util.Fonts.NAMES).in("HUD"));
		hudBold = add(new BoolSetting("Default Bold", false).in("HUD"));
		hudItalic = add(new BoolSetting("Default Italic", false).in("HUD"));
		globalScale = add(new NumberSetting("Global HUD Scale", 1.0, 0.5, 2.0, 0.05).in("HUD"));
		hudVisibility = add(new ModeSetting("Show HUD", "Always", "Always", "Hide In Menus", "Hide In Menus Except Chat").in("HUD"));

		menuKey = add(new com.mirko.shiftmenu.module.setting.KeySetting("Menu Key", org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_SHIFT).in("Controls"));
		openMode = add(new ModeSetting("Open Menu On", "Press", "Press", "Tap").in("Controls"));
		notifications = add(new BoolSetting("Toggle Notifications", true).in("Controls"));
		tapTime = add(new NumberSetting("Max Tap Time (ms)", 250, 100, 600, 10).in("Controls").showIf(() -> this.openMode.is("Tap")));
	}

	/** {accent, panel, card, enabled, disabled, text, dimText} for each preset theme. */
	public static int[] themeColors(String name) {
		return switch (name) {
			case "Lunar Blue" -> new int[]{0xFF3B82F6, 0xEE0F0F12, 0xFF1C1C21, 0xFF2FA866, 0xFFD9434B, 0xFFFFFFFF, 0xFF9A9AA5};
			case "Midnight Purple" -> new int[]{0xFF8B5CF6, 0xF0100C18, 0xFF1C1626, 0xFF22C55E, 0xFFEF4444, 0xFFFFFFFF, 0xFFA39AB5};
			case "Crimson" -> new int[]{0xFFE11D48, 0xF0140C0E, 0xFF241418, 0xFF16A34A, 0xFF9F1239, 0xFFFFFFFF, 0xFFB59AA0};
			case "Emerald" -> new int[]{0xFF10B981, 0xF00B1410, 0xFF14221B, 0xFF10B981, 0xFFDC2626, 0xFFFFFFFF, 0xFF9AB5A7};
			case "Sunset" -> new int[]{0xFFF97316, 0xF0150F0A, 0xFF241A12, 0xFF22C55E, 0xFFE11D48, 0xFFFFFFFF, 0xFFB5A79A};
			case "Rose" -> new int[]{0xFFEC4899, 0xF0150C12, 0xFF24141E, 0xFF22C55E, 0xFFE11D48, 0xFFFFFFFF, 0xFFB59AAB};
			case "Mono" -> new int[]{0xFFE5E5E5, 0xF0101010, 0xFF1E1E1E, 0xFF3F7F5F, 0xFF7F3F3F, 0xFFFFFFFF, 0xFF9A9A9A};
			default -> new int[]{0xFF1FC7D9, 0xF00C1416, 0xFF162024, 0xFF20B486, 0xFFE0475B, 0xFFFFFFFF, 0xFF8FA7AD}; // Mirko Aqua
		};
	}

	/** Re-color the UI if the Theme setting changed (Custom keeps your own colors). */
	public void applyThemeIfChanged() {
		String t = theme.get();
		if (t.equals(appliedTheme)) return;
		appliedTheme = t;
		if (t.equals("Custom")) return;
		int[] c = themeColors(t);
		ColorSetting[] targets = {accent, panelColor, cardColor, enabledColor, disabledColor, menuText, menuTextDim};
		for (int i = 0; i < targets.length; i++) {
			targets[i].chroma = false;
			targets[i].setArgb(c[i]);
		}
	}

	/** Colors of a theme for the little palette preview. */
	public static int themeAccent(String name) {
		return themeColors(name)[0];
	}

	public com.mirko.shiftmenu.util.Fonts.TextStyle menuStyle() {
		return new com.mirko.shiftmenu.util.Fonts.TextStyle(menuFont.get(), menuBold.on(), false, false, false);
	}

	@Override
	public float effectiveScale() {
		return 1f;
	}

	@Override
	public boolean isDraggable() {
		return false;
	}

	@Override
	public boolean hasToggle() {
		return false;
	}

	@Override
	public int width(boolean preview) {
		return 0;
	}

	@Override
	public int height(boolean preview) {
		return 0;
	}

	@Override
	public void render(GuiGraphics g, boolean preview) {
	}
}
