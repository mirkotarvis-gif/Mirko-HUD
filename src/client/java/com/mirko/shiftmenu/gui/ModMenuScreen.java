package com.mirko.shiftmenu.gui;

import com.mirko.shiftmenu.Config;
import com.mirko.shiftmenu.module.HudModule;
import com.mirko.shiftmenu.module.ModuleManager;
import com.mirko.shiftmenu.module.impl.GeneralSettings;
import com.mirko.shiftmenu.module.setting.BoolSetting;
import com.mirko.shiftmenu.module.setting.ColorSetting;
import com.mirko.shiftmenu.module.setting.InfoSetting;
import com.mirko.shiftmenu.module.setting.KeySetting;
import com.mirko.shiftmenu.module.setting.ModeSetting;
import com.mirko.shiftmenu.module.setting.NumberSetting;
import com.mirko.shiftmenu.module.setting.Setting;
import com.mirko.shiftmenu.module.setting.TextSetting;
import com.mirko.shiftmenu.util.Anim;
import com.mirko.shiftmenu.util.Colors;
import com.mirko.shiftmenu.util.Draw;
import com.mirko.shiftmenu.util.Game;
import com.mirko.shiftmenu.util.Icons;
import com.mirko.shiftmenu.util.Keys;
import com.mirko.shiftmenu.util.Waypoints;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.DoubleConsumer;
import java.util.function.IntConsumer;

/**
 * The mod menu, laid out like a modern PvP client: top bar (MODS / SETTINGS / WAYPOINTS + close),
 * a profiles sidebar, category pills, search, grid or list of mod cards (icon, name, OPTIONS,
 * gear, ENABLED / DISABLED bar), and per-mod settings pages. Everything animates smoothly.
 */
public class ModMenuScreen extends Screen {
	private enum Page { MODS, SETTINGS, WAYPOINTS }

	private static final int TB = 28;   // top bar height
	private static final int SW = 104;  // sidebar width
	private static final int PREVIEW_BG = 0xFF141418;
	private static final int TRACK = 0xFF3A3A42;
	private static final String[] TABS = {"ALL", "NEW", "ON", "HUD", "COMBAT", "INFO", "MECHANIC"};
	private static final int[] PRESETS = {0xFFFFFFFF, 0xFFAAAAAA, 0xFF000000, 0xFFFF5555, 0xFFFFAA00, 0xFFFFFF55,
			0xFF55FF55, 0xFF55FFFF, 0xFF3B82F6, 0xFFAA55FF, 0xFFFF77C8};

	private int TEXT = 0xFFFFFFFF;
	private int TEXT_DIM = 0xFF9A9AA5;

	private Page page = Page.MODS;
	private HudModule settingsFor;
	private String tab = "ALL";
	private EditBox search;
	private EditBox profileInput;
	private boolean namingProfile;
	private List<String> profiles = new ArrayList<>();

	private float scrollTarget;
	private float scroll;
	private int maxScroll;
	private KeySetting listeningSetting;

	private final Anim anim = new Anim();
	private boolean closing;
	private Screen next;
	private float openT;
	private float scale = 1f;

	private record Drag(int x, int w, DoubleConsumer set) {}

	private Drag drag;

	private record Hit(int x, int y, int w, int h, IntConsumer action) {}

	private final List<Hit> hits = new ArrayList<>();
	private int clipX1, clipY1, clipX2, clipY2;
	private boolean clipping;

	private final Map<Object, EditBox> boxes = new HashMap<>();
	private final Set<ColorSetting> expanded = new HashSet<>();

	private int px, py, pw, ph;
	private boolean lastAA = true;

	public ModMenuScreen(HudModule settingsFor) {
		super(Component.literal("Mods"));
		this.settingsFor = settingsFor == ModuleManager.SETTINGS ? null : settingsFor;
		if (settingsFor == ModuleManager.SETTINGS) page = Page.SETTINGS;
	}

	private static GeneralSettings cfg() {
		return ModuleManager.SETTINGS;
	}

	private static int accent() {
		return cfg().accent.color();
	}

	private static int card() {
		return cfg().cardColor.color();
	}

	private static int lighten(int c, float t) {
		return Colors.lerp(c, 0xFFFFFFFF, t);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		if (cfg().blur.on()) super.renderBackground(g, mouseX, mouseY, partialTick);
		else g.fill(0, 0, this.width, this.height, Math.round(0x66 * openT) << 24);
	}

	private void layout() {
		pw = Math.min(cfg().menuWidth.get().intValue(), this.width - 16);
		ph = Math.min(cfg().menuHeight.get().intValue(), this.height - 16);
		px = (this.width - pw) / 2;
		py = (this.height - ph) / 2;
	}

	@Override
	protected void init() {
		layout();
		String prev = search != null ? search.getValue() : "";
		boxes.clear();
		search = newBox("search", 32, s -> scrollTarget = 0);
		search.setHint(Component.literal("Search...").withStyle(ChatFormatting.DARK_GRAY));
		search.setValue(prev);
		profileInput = newBox("profile", 24, s -> {});
		profileInput.setHint(Component.literal("Profile name").withStyle(ChatFormatting.DARK_GRAY));
		profiles = Config.profiles();
	}

	private EditBox newBox(Object key, int maxLen, java.util.function.Consumer<String> onChange) {
		EditBox b = new EditBox(this.font, 0, 0, 100, 10, Component.literal("input"));
		b.setBordered(false);
		b.setMaxLength(maxLen);
		b.setTextColor(0xFFFFFFFF);
		b.setResponder(onChange);
		boxes.put(key, b);
		this.addWidget(b);
		return b;
	}

	private EditBox boxFor(Object key, int maxLen, java.util.function.Consumer<String> onChange) {
		EditBox b = boxes.get(key);
		return b != null ? b : newBox(key, maxLen, onChange);
	}

	private void clearPageBoxes() {
		boxes.entrySet().removeIf(e -> {
			if (e.getKey().equals("search") || e.getKey().equals("profile")) return false;
			this.removeWidget(e.getValue());
			return true;
		});
	}

	private void switchTo(Page p, HudModule module) {
		clearPageBoxes();
		expanded.clear();
		page = p;
		settingsFor = module;
		scrollTarget = 0;
		anim.set("scroll", 0);
		anim.set("content", 0);
		drag = null;
		listeningSetting = null;
		unfocusAll();
		Config.save();
	}

	private void closeTo(Screen target) {
		if (closing) return;
		Config.save();
		closing = true;
		next = target;
		unfocusAll();
	}

	@Override
	public void tick() {
		if (closing && openT <= 0.04f) {
			this.minecraft.setScreen(next);
		}
	}

	// ================================================================== rendering

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		anim.frame();
		cfg().applyThemeIfChanged();
		if (cfg().antiAliasing.on() != lastAA) {
			lastAA = cfg().antiAliasing.on();
			Draw.clearCache();
		}
		layout();
		hits.clear();
		TEXT = cfg().menuText.color();
		TEXT_DIM = cfg().menuTextDim.color();

		openT = anim.get("open", closing ? 0f : 1f, 14f, 0f);
		scale = 0.94f + 0.06f * Anim.easeOut(openT);
		int cxs = px + pw / 2, cys = py + ph / 2;
		int mx = (int) Math.round(cxs + (mouseX - cxs) / scale);
		int my = (int) Math.round(cys + (mouseY - cys) / scale);

		for (EditBox b : boxes.values()) b.visible = false;

		g.pose().pushMatrix();
		g.pose().translate(cxs, cys);
		g.pose().scale(scale, scale);
		g.pose().translate(-cxs, -cys);

		int panel = cfg().panelColor.color();
		Draw.rounded(g, px, py, px + pw, py + ph, cfg().panelRadius.get().intValue(),
				Colors.withAlpha(panel, Math.round(((panel >>> 24) & 0xFF) * openT)));

		renderTopBar(g, mx, my);
		float content = anim.get("content", 1f, 9f, 0f);
		int slide = Math.round((1f - Anim.easeOut(content)) * 10f);

		switch (page) {
			case MODS -> {
				renderSidebar(g, mx, my);
				if (settingsFor == null) renderMods(g, mx, my, slide, partialTick);
				else renderSettingsPage(g, settingsFor, px + SW + 6, py + TB + 6, pw - SW - 14, ph - TB - 14, mx, my, slide, partialTick);
			}
			case SETTINGS -> renderSettingsPage(g,
