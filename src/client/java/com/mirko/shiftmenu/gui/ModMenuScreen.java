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
			case SETTINGS -> renderSettingsPage(g, ModuleManager.SETTINGS, px + 10, py + TB + 6, pw - 20, ph - TB - 14, mx, my, slide, partialTick);
			case WAYPOINTS -> renderWaypoints(g, mx, my, slide, partialTick);
		}

		g.pose().popMatrix();
	}

	private void renderTopBar(GuiGraphics g, int mx, int my) {
		int y = py + 6;
		// logo
		int lx = px + 10;
		Draw.rounded(g, lx, y, lx + 16, y + 16, 4, accent());
		Draw.centered(g, "§lM", lx + 8, y + 4, 0xFFFFFFFF);
		Draw.text(g, "§lMIRKO'S HUD", lx + 22, y + 4, TEXT);

		// tabs
		int tx = lx + 22 + Draw.width("§lMIRKO'S HUD") + 18;
		String[] names = {"MODS", "SETTINGS", "WAYPOINTS"};
		Page[] pages = {Page.MODS, Page.SETTINGS, Page.WAYPOINTS};
		for (int i = 0; i < names.length; i++) {
			int w = Draw.width(names[i]) + 18;
			boolean sel = page == pages[i];
			boolean hov = Draw.inside(mx, my, tx, y, w, 16);
			float h = anim.get("tab:" + i, hov || sel ? 1f : 0f, 16f);
			Draw.rounded(g, tx, y, tx + w, y + 16, 3, Colors.lerp(0x18FFFFFF, sel ? 0x40FFFFFF : 0x2CFFFFFF, h));
			Draw.hairline(g, tx, y, tx + w, y + 16, 3, sel ? 0x66FFFFFF : 0x22FFFFFF);
			Draw.centered(g, names[i], tx + w / 2, y + 4, sel ? TEXT : Colors.lerp(TEXT_DIM, TEXT, h));
			final Page p = pages[i];
			hit(tx, y, w, 16, b -> {
				if (page != p || settingsFor != null) switchTo(p, null);
			});
			tx += w + 5;
		}

		// theme button: click = next theme, right-click = previous
		int thx = px + pw - 46;
		boolean ht = Draw.inside(mx, my, thx, y, 16, 16);
		float htT = anim.get("theme", ht ? 1f : 0f, 18f);
		Draw.rounded(g, thx, y, thx + 16, y + 16, 3, Colors.lerp(0x2CFFFFFF, 0x50FFFFFF, htT));
		String cur = cfg().theme.get();
		int ti = java.util.Arrays.asList(GeneralSettings.THEMES).indexOf(cur);
		int n = GeneralSettings.THEMES.length - 1; // presets only (no Custom)
		Draw.iconPalette(g, thx + 8, y + 8, accent(),
				GeneralSettings.themeAccent(GeneralSettings.THEMES[(Math.max(0, ti) + 1) % n]),
				GeneralSettings.themeAccent(GeneralSettings.THEMES[(Math.max(0, ti) + 2) % n]));
		if (ht) {
			String tip = "Theme: " + cur;
			int tw = Draw.width(tip) + 10;
			Draw.rounded(g, thx + 8 - tw / 2, y + 19, thx + 8 + tw / 2, y + 31, 3, 0xEE000000);
			Draw.centered(g, tip, thx + 8, y + 21, 0xFFFFFFFF);
		}
		hit(thx, y, 16, 16, b -> {
			int idx = Math.max(0, java.util.Arrays.asList(GeneralSettings.THEMES).indexOf(cfg().theme.get()));
			if (idx >= n) idx = 0;
			idx = b == 1 ? (idx - 1 + n) % n : (idx + 1) % n;
			cfg().theme.set(GeneralSettings.THEMES[idx]);
			cfg().applyThemeIfChanged();
			com.mirko.shiftmenu.util.Toasts.show("Theme", GeneralSettings.THEMES[idx], cfg().accent.color());
			Config.save();
		});

		// close button
		int cx = px + pw - 26;
		boolean hc = Draw.inside(mx, my, cx, y, 16, 16);
		float hcT = anim.get("close", hc ? 1f : 0f, 18f);
		Draw.rounded(g, cx, y, cx + 16, y + 16, 3, Colors.lerp(0x2CFFFFFF, 0xCCD9434B, hcT));
		Draw.iconClose(g, cx + 8, y + 8, TEXT);
		hit(cx, y, 16, 16, b -> closeTo(null));

		g.fill(px + 8, py + TB, px + pw - 8, py + TB + 1, 0x1FFFFFFF);
	}

	// ------------------------------------------------------------------ sidebar (profiles)

	private void renderSidebar(GuiGraphics g, int mx, int my) {
		int x = px + 8, y = py + TB + 6, w = SW - 10, bottom = py + ph - 8;
		Draw.rounded(g, x, y, x + w, bottom, 4, 0x22000000);
		int ry = y + 4;
		Draw.text(g, "PROFILES", x + 5, ry, TEXT_DIM);
		ry += 12;
		for (String name : profiles) {
			if (ry > bottom - 60) break;
			boolean active = name.equals(Config.activeProfile);
			boolean hov = Draw.inside(mx, my, x + 3, ry, w - 6, 14);
			float h = anim.get("prof:" + name, hov || active ? 1f : 0f, 16f);
			Draw.rounded(g, x + 3, ry, x + w - 3, ry + 14, 2, Colors.lerp(0x14FFFFFF, active ? 0x3AFFFFFF : 0x28FFFFFF, h));
			if (active) g.fill(x + 3, ry + 2, x + 5, ry + 12, accent());
			String label = name;
			while (Draw.width(label) > w - 26 && label.length() > 2) label = label.substring(0, label.length() - 1);
			Draw.text(g, label, x + 9, ry + 3, active ? TEXT : TEXT_DIM);
			if (!active && hov) {
				boolean hd = Draw.inside(mx, my, x + w - 15, ry, 12, 14);
				Draw.iconTrash(g, x + w - 10, ry + 7, hd ? 0xFFFF6666 : TEXT_DIM);
				final String n = name;
				hit(x + 3, ry, w - 6, 14, b -> {
					Config.switchProfile(n);
					profiles = Config.profiles();
					com.mirko.shiftmenu.util.Toasts.show("Profile", "Loaded " + n, accent());
				});
				hit(x + w - 16, ry, 13, 14, b -> {
					Config.deleteProfile(n);
					profiles = Config.profiles();
				});
			} else if (active) {
				Draw.iconPencil(g, x + w - 10, ry + 7, TEXT_DIM);
			} else {
				final String n = name;
				hit(x + 3, ry, w - 6, 14, b -> {
					Config.switchProfile(n);
					profiles = Config.profiles();
				});
			}
			ry += 16;
		}
		if (namingProfile) {
			Draw.rounded(g, x + 3, ry, x + w - 3, ry + 14, 2, 0x40000000);
			Draw.hairline(g, x + 3, ry, x + w - 3, ry + 14, 2, accent());
			renderBox(g, profileInput, x + 3, ry, w - 6, 14, mx, my, 0, false);
		}

		// bottom buttons
		int by = bottom - 38;
		boolean hs = Draw.inside(mx, my, x + 3, by, w - 6, 16);
		float hsT = anim.get("saveprof", hs ? 1f : 0f, 16f);
		Draw.rounded(g, x + 3, by, x + w - 3, by + 16, 3, Colors.lerp(0x10FFFFFF, 0x2AFFFFFF, hsT));
		Draw.hairline(g, x + 3, by, x + w - 3, by + 16, 3, 0x30FFFFFF);
		Draw.centered(g, namingProfile ? "SAVE PROFILE" : "SAVE AS NEW PROFILE", x + w / 2, by + 4, Colors.lerp(TEXT_DIM, TEXT, hsT));
		hit(x + 3, by, w - 6, 16, b -> {
			if (namingProfile) commitProfile();
			else {
				namingProfile = true;
				profileInput.setValue("");
				unfocusAll();
				this.setFocused(profileInput);
				profileInput.setFocused(true);
			}
		});

		int ey = bottom - 19;
		boolean he = Draw.inside(mx, my, x + 3, ey, w - 6, 16);
		float heT = anim.get("edithud", he ? 1f : 0f, 16f);
		Draw.rounded(g, x + 3, ey, x + w - 3, ey + 16, 3, lighten(accent(), heT * 0.15f));
		Draw.centered(g, "EDIT HUD LAYOUT", x + w / 2, ey + 4, 0xFFFFFFFF);
		hit(x + 3, ey, w - 6, 16, b -> closeTo(new HudEditorScreen()));
	}

	private void commitProfile() {
		String n = profileInput.getValue().trim();
		if (!n.isEmpty() && Config.createProfile(n)) {
			profiles = Config.profiles();
			com.mirko.shiftmenu.util.Toasts.show("Profile", "Saved " + n, accent());
		}
		namingProfile = false;
		unfocusAll();
	}

	// ------------------------------------------------------------------ mods grid / list

	private List<HudModule> filtered() {
		String q = search.getValue().trim().toLowerCase(Locale.ROOT);
		List<HudModule> out = new ArrayList<>();
		for (HudModule m : ModuleManager.MODULES) {
			boolean ok = switch (tab) {
				case "ALL" -> true;
				case "NEW" -> ModuleManager.NEW_IDS.contains(m.id);
				case "ON" -> m.isEnabled();
				default -> m.category.name().equals(tab);
			};
			if (!ok) continue;
			if (!q.isEmpty() && !m.name.toLowerCase(Locale.ROOT).contains(q)) continue;
			out.add(m);
		}
		if (cfg().sortMode.is("A-Z")) out.sort(Comparator.comparing(m -> m.name.toLowerCase(Locale.ROOT)));
		else if (cfg().sortMode.is("Enabled First")) out.sort(Comparator.comparing(m -> !m.isEnabled()));
		return out;
	}

	private static int tabColor(String t) {
		return switch (t) {
			case "NEW" -> 0xFF2FA866;
			case "ON" -> 0xFF8A8A95;
			case "HUD" -> 0xFF3B6FD6;
			case "COMBAT" -> 0xFFD9434B;
			case "INFO" -> 0xFF8B5CF6;
			case "MECHANIC" -> 0xFF14B8A6;
			default -> accent();
		};
	}

	private void renderMods(GuiGraphics g, int mx, int my, int slide, float pt) {
		int cx = px + SW + 6, cy = py + TB + 6, cw = pw - SW - 14, ch = ph - TB - 14;

		// filter row
		int fx = cx, fy = cy;
		for (String t : TABS) {
			int w = Draw.width(t) + 10;
			boolean sel = t.equals(tab);
			boolean hov = Draw.inside(mx, my, fx, fy, w, 14);
			float h = anim.get("pill:" + t, sel ? 1f : (hov ? 0.45f : 0f), 16f);
			int col = tabColor(t);
			Draw.rounded(g, fx, fy, fx + w, fy + 14, 3, Colors.lerp(Colors.withAlpha(col, 0x38), col, h));
			Draw.centered(g, t, fx + w / 2, fy + 3, Colors.lerp(TEXT_DIM, TEXT, Math.max(h, 0.4f)));
			final String tn = t;
			hit(fx, fy, w, 14, b -> {
				if (!tab.equals(tn)) {
					tab = tn;
					scrollTarget = 0;
					anim.set("content", 0);
				}
			});
			fx += w + 3;
		}
		// view + sort buttons
		fx += 4;
		boolean grid = cfg().modsView.is("Grid");
		fx = iconButton(g, mx, my, fx, fy, "view-grid", grid, () -> cfg().modsView.set("Grid"), 0);
		fx = iconButton(g, mx, my, fx, fy, "view-list", !grid, () -> cfg().modsView.set("List"), 1);
		fx = iconButton(g, mx, my, fx, fy, "sort", !cfg().sortMode.is("Default"), () -> cfg().sortMode.next(), 2);

		int sw = Math.min(100, cx + cw - fx - 6);
		if (sw >= 50) {
			int sx = cx + cw - sw;
			Draw.rounded(g, sx, fy, sx + sw, fy + 14, 3, search.isFocused() ? 0x40000000 : 0x24FFFFFF);
			if (search.isFocused()) Draw.hairline(g, sx, fy, sx + sw, fy + 14, 3, accent());
			renderBox(g, search, sx + 2, fy, sw - 4, 14, mx, my, pt, false);
		}

		int gy = fy + 20 + slide, gh = ch - 20;
		List<HudModule> list = filtered();
		if (list.isEmpty()) {
			Draw.centered(g, "No mods found", cx + cw / 2, gy + gh / 2 - 4, TEXT_DIM);
			return;
		}

		scroll = anim.get("scroll", scrollTarget, 16f);
		beginClip(g, cx, fy + 19, cx + cw, cy + ch);
		boolean inView = Draw.inside(mx, my, cx, fy + 19, cw, ch - 19);
		int totalH = grid ? renderGrid(g, list, cx, gy, cw - 5, mx, my, inView) : renderList(g, list, cx, gy, cw - 5, mx, my, inView);
		endClip(g);
		maxScroll = Math.max(0, totalH - gh);
		scrollTarget = Math.max(0, Math.min(scrollTarget, maxScroll));
		drawScrollbar(g, cx + cw - 2, fy + 20, gh, totalH);
	}

	private int iconButton(GuiGraphics g, int mx, int my, int x, int y, String key, boolean active, Runnable action, int icon) {
		boolean hov = Draw.inside(mx, my, x, y, 14, 14);
		float h = anim.get("ib:" + key, active ? 1f : (hov ? 0.5f : 0f), 16f);
		Draw.rounded(g, x, y, x + 14, y + 14, 3, Colors.lerp(0x18FFFFFF, 0x44FFFFFF, h));
		int c = Colors.lerp(TEXT_DIM, TEXT, h);
		switch (icon) {
			case 0 -> Draw.iconGrid(g, x + 7, y + 7, c);
			case 1 -> Draw.iconList(g, x + 7, y + 7, c);
			default -> Draw.iconSort(g, x + 7, y + 7, c);
		}
		hit(x, y, 14, 14, b -> {
			action.run();
			scrollTarget = 0;
			anim.set("content", 0);
		});
		return x + 17;
	}

	private int renderGrid(GuiGraphics g, List<HudModule> list, int x0, int y0, int w, int mx, int my, boolean inView) {
		int gap = 6;
		int cols = cfg().columns.is("Auto") ? Math.max(2, (w + gap) / (96 + gap)) : Integer.parseInt(cfg().columns.get());
		int cardW = (w - (cols - 1) * gap) / cols;
		int cardH = cfg().cardHeight.get().intValue();
		int rows = (list.size() + cols - 1) / cols;
		int s = Math.round(scroll);
		boolean preview = cfg().cardStyle.is("Live Preview");
		int on = cfg().enabledColor.color(), off = cfg().disabledColor.color();

		for (int i = 0; i < list.size(); i++) {
			HudModule m = list.get(i);
			int x = x0 + (i % cols) * (cardW + gap);
			int y = y0 + (i / cols) * (cardH + gap) - s;
			if (y + cardH < clipY1 || y > clipY2) continue;

			boolean hov = inView && Draw.inside(mx, my, x, y, cardW, cardH);
			float h = anim.get("card:" + m.id, hov ? 1f : 0f, 14f);
			Draw.rounded(g, x, y, x + cardW, y + cardH, 5, Colors.lerp(card(), lighten(card(), 0.07f), h));
			Draw.hairline(g, x, y, x + cardW, y + cardH, 5, Colors.lerp(0x18FFFFFF, 0x40FFFFFF, h));
			hit(x, y, cardW, cardH, b -> {
				if (b == 1) switchTo(Page.MODS, m);
				else m.toggle();
			});
			if (ModuleManager.NEW_IDS.contains(m.id)) {
				Draw.rounded(g, x + cardW - 26, y + 4, x + cardW - 4, y + 12, 2, 0xFF2FA866);
				Draw.centered(g, "NEW", x + cardW - 15, y + 4, 0xFFFFFFFF);
			}

			// icon / preview area
			int nameY;
			if (preview) {
				int ax = x + 5, ay = y + 5, aw = cardW - 10, ah = cardH - 50;
				Draw.rounded(g, ax, ay, ax + aw, ay + ah, 4, PREVIEW_BG);
				drawPreview(g, m, ax, ay, aw, ah);
				nameY = ay + ah + 3;
			} else {
				float iconSize = 24 + h * 2;
				Draw.item(g, Icons.forModule(m.id), x + cardW / 2f - iconSize / 2f, y + 9 - h, iconSize);
				nameY = y + 37;
			}
			String name = m.name;
			while (Draw.width(name) > cardW - 8 && name.length() > 3) name = name.substring(0, name.length() - 1);
			Draw.centered(g, name, x + cardW / 2, nameY, Colors.lerp(TEXT_DIM, TEXT, 0.6f + h * 0.4f));

			// OPTIONS + gear
			int oy = y + cardH - 33, oh = 13;
			int gw = 15;
			int ow = cardW - 8 - gw - 2;
			boolean ho = inView && Draw.inside(mx, my, x + 4, oy, ow, oh);
			float hoT = anim.get("opt:" + m.id, ho ? 1f : 0f, 18f);
			Draw.rounded(g, x + 4, oy, x + 4 + ow, oy + oh, 2, Colors.lerp(0x22FFFFFF, 0x44FFFFFF, hoT));
			Draw.centered(g, "OPTIONS", x + 4 + ow / 2, oy + 3, Colors.lerp(TEXT_DIM, TEXT, hoT));
			hit(x + 4, oy, ow, oh, b -> switchTo(Page.MODS, m));
			int gx = x + 4 + ow + 2;
			boolean hg = inView && Draw.inside(mx, my, gx, oy, gw, oh);
			float hgT = anim.get("gear:" + m.id, hg ? 1f : 0f, 18f);
			Draw.rounded(g, gx, oy, gx + gw, oy + oh, 2, Colors.lerp(0x22FFFFFF, 0x44FFFFFF, hgT));
			Draw.iconGear(g, gx + gw / 2, oy + oh / 2, Colors.lerp(TEXT_DIM, TEXT, hgT));
			hit(gx, oy, gw, oh, b -> {
				if (m.isDraggable()) closeTo(new HudEditorScreen(m));
				else switchTo(Page.MODS, m);
			});

			// ENABLED / DISABLED bar
			int ty = y + cardH - 18, th = 14;
			boolean ht = inView && Draw.inside(mx, my, x + 4, ty, cardW - 8, th);
			float tT = anim.get("tog:" + m.id, m.isEnabled() ? 1f : 0f, 12f);
			float htT = anim.get("togh:" + m.id, ht ? 1f : 0f, 18f);
			int col = lighten(Colors.lerp(off, on, tT), htT * 0.12f);
			Draw.rounded(g, x + 4, ty, x + cardW - 4, ty + th, 2, col);
			Draw.centered(g, tT >= 0.5f ? "§lENABLED" : "§lDISABLED", x + cardW / 2, ty + 3, 0xFFFFFFFF);
			hit(x + 4, ty, cardW - 8, th, b -> m.toggle());
		}
		return rows * (cardH + gap) - gap;
	}

	private int renderList(GuiGraphics g, List<HudModule> list, int x0, int y0, int w, int mx, int my, boolean inView) {
		int rowH = 24, gap = 3;
		int s = Math.round(scroll);
		int on = cfg().enabledColor.color(), off = cfg().disabledColor.color();
		for (int i = 0; i < list.size(); i++) {
			HudModule m = list.get(i);
			int y = y0 + i * (rowH + gap) - s;
			if (y + rowH < clipY1 || y > clipY2) continue;
			boolean hov = inView && Draw.inside(mx, my, x0, y, w, rowH);
			float h = anim.get("row:" + m.id, hov ? 1f : 0f, 14f);
			Draw.rounded(g, x0, y, x0 + w, y + rowH, 4, Colors.lerp(card(), lighten(card(), 0.07f), h));
			hit(x0, y, w, rowH, b -> {
				if (b == 1) switchTo(Page.MODS, m);
				else m.toggle();
			});
			Draw.item(g, Icons.forModule(m.id), x0 + 5, y + 4, 16);
			Draw.text(g, m.name, x0 + 27, y + 8, TEXT);
			if (ModuleManager.NEW_IDS.contains(m.id)) {
				int nx = x0 + 31 + Draw.width(m.name);
				Draw.rounded(g, nx, y + 7, nx + 22, y + 16, 2, 0xFF2FA866);
				Draw.centered(g, "NEW", nx + 11, y + 8, 0xFFFFFFFF);
			}

			int tw = 64, tx = x0 + w - 6 - tw;
			float tT = anim.get("tog:" + m.id, m.isEnabled() ? 1f : 0f, 12f);
			boolean ht = inView && Draw.inside(mx, my, tx, y + 5, tw, 14);
			Draw.rounded(g, tx, y + 5, tx + tw, y + 19, 2, lighten(Colors.lerp(off, on, tT), ht ? 0.12f : 0f));
			Draw.centered(g, tT >= 0.5f ? "§lENABLED" : "§lDISABLED", tx + tw / 2, y + 8, 0xFFFFFFFF);
			hit(tx, y + 5, tw, 14, b -> m.toggle());

			int gx = tx - 18;
			boolean hg = inView && Draw.inside(mx, my, gx, y + 5, 15, 14);
			Draw.rounded(g, gx, y + 5, gx + 15, y + 19, 2, hg ? 0x44FFFFFF : 0x22FFFFFF);
			Draw.iconGear(g, gx + 7, y + 12, hg ? TEXT : TEXT_DIM);
			hit(gx, y + 5, 15, 14, b -> {
				if (m.isDraggable()) closeTo(new HudEditorScreen(m));
				else switchTo(Page.MODS, m);
			});

			int ow = Draw.width("OPTIONS") + 14, ox = gx - 3 - ow;
			boolean ho = inView && Draw.inside(mx, my, ox, y + 5, ow, 14);
			Draw.rounded(g, ox, y + 5, ox + ow, y + 19, 2, ho ? 0x44FFFFFF : 0x22FFFFFF);
			Draw.centered(g, "OPTIONS", ox + ow / 2, y + 8, ho ? TEXT : TEXT_DIM);
			hit(ox, y + 5, ow, 14, b -> switchTo(Page.MODS, m));
		}
		return list.size() * (rowH + gap) - gap;
	}

	private void drawPreview(GuiGraphics g, HudModule m, int ax, int ay, int aw, int ah) {
		int w = Math.max(1, m.width(true)), h = Math.max(1, m.height(true));
		float s = Math.min(1.5f, Math.min((aw - 10) / (float) w, (ah - 10) / (float) h));
		g.pose().pushMatrix();
		g.pose().translate(ax + aw / 2f - w * s / 2f, ay + ah / 2f - h * s / 2f);
		g.pose().scale(s, s);
		m.render(g, true);
		g.pose().popMatrix();
	}

	// ================================================================== settings page

	private static final int ROW = 20, GAP = 3, SUB = 14;

	private void renderSettingsPage(GuiGraphics g, HudModule m, int cx, int cy0, int cw, int ch, int mx, int my, int slide, float pt) {
		// header
		int hy = cy0;
		int titleX = cx;
		if (m != ModuleManager.SETTINGS) {
			int bw = Draw.width("BACK") + 20;
			boolean hb = Draw.inside(mx, my, cx, hy, bw, 16);
			float hbT = anim.get("back", hb ? 1f : 0f, 18f);
			Draw.rounded(g, cx, hy, cx + bw, hy + 16, 3, Colors.lerp(0x22FFFFFF, 0x44FFFFFF, hbT));
			Draw.iconBack(g, cx + 8, hy + 8, TEXT);
			Draw.text(g, "BACK", cx + 14, hy + 4, TEXT);
			hit(cx, hy, bw, 16, b -> switchTo(Page.MODS, null));
			titleX = cx + bw + 8;
			Draw.item(g, Icons.forModule(m.id), titleX, hy, 16);
			titleX += 20;
		}
		Draw.text(g, "§l" + m.name.toUpperCase(Locale.ROOT), titleX, hy + 4, TEXT);
		if (m == ModuleManager.SETTINGS) {
			String hint = "Global options for the menu, HUD editor and controls";
			Draw.text(g, hint, titleX + Draw.width("§lSETTINGS") + 10, hy + 4, TEXT_DIM);
		}

		int top = cy0 + 22;
		int ch2 = ch - 22;
		boolean hasPreview = m.hasToggle() && m.width(true) > 0;
		int prevW = hasPreview ? Math.min(130, cw / 3) : 0;
		int listW = hasPreview ? cw - prevW - 10 : cw - 6;
		if (hasPreview) {
			int pvx = cx + listW + 10;
			Draw.rounded(g, pvx, top, pvx + prevW, top + 80, 4, PREVIEW_BG);
			Draw.hairline(g, pvx, top, pvx + prevW, top + 80, 4, 0x18FFFFFF);
			drawPreview(g, m, pvx, top, prevW, 80);
			Draw.centered(g, "Live Preview", pvx + prevW / 2, top + 84, TEXT_DIM);
		}

		LinkedHashMap<String, List<Setting<?>>> sections = new LinkedHashMap<>();
		for (Setting<?> s : m.settings) {
			if (!s.isVisible()) continue;
			sections.computeIfAbsent(s.section(), k -> new ArrayList<>()).add(s);
		}

		scroll = anim.get("scroll", scrollTarget, 16f);
		beginClip(g, cx, top, cx + listW, top + ch2);
		boolean inView = Draw.inside(mx, my, cx, top, listW, ch2);
		int y = top - Math.round(scroll) + slide;

		if (m.hasToggle()) {
			rowBg(g, cx, y, listW, ROW, false);
			Draw.text(g, "§lEnabled", cx + 8, y + 6, TEXT);
			Draw.toggleSwitch(g, cx + listW - 8 - 18, y + 5, anim.get("en:" + m.id, m.isEnabled() ? 1f : 0f, 14f), cfg().enabledColor.color());
			hit(cx, y, listW, ROW, b -> m.toggle());
			y += ROW + GAP;
		}

		for (Map.Entry<String, List<Setting<?>>> e : sections.entrySet()) {
			y += 4;
			Draw.text(g, e.getKey().toUpperCase(Locale.ROOT), cx + 2, y, accent());
			y += 12;
			for (Setting<?> s : e.getValue()) y = renderSetting(g, s, cx, y, listW, mx, my, inView, pt);
		}

		// reset row
		y += 4;
		int half = (listW - GAP) / 2;
		boolean h1 = inView && Draw.inside(mx, my, cx, y, half, ROW);
		boolean h2 = inView && Draw.inside(mx, my, cx + half + GAP, y, listW - half - GAP, ROW);
		int danger = cfg().disabledColor.color();
		if (m.isDraggable()) {
			Draw.rounded(g, cx, y, cx + half, y + ROW, 3, h1 ? 0x44FFFFFF : 0x22FFFFFF);
			Draw.centered(g, "Reset Position", cx + half / 2, y + 6, TEXT);
			hit(cx, y, half, ROW, b -> m.resetPosition());
		}
		int rx2 = m.isDraggable() ? cx + half + GAP : cx;
		int rw2 = m.isDraggable() ? listW - half - GAP : listW;
		boolean hr = m.isDraggable() ? h2 : inView && Draw.inside(mx, my, rx2, y, rw2, ROW);
		Draw.rounded(g, rx2, y, rx2 + rw2, y + ROW, 3, hr ? danger : Colors.lerp(danger, 0xFF000000, 0.55f));
		Draw.centered(g, "Reset Settings", rx2 + rw2 / 2, y + 6, TEXT);
		hit(rx2, y, rw2, ROW, b -> {
			m.resetSettings();
			expanded.clear();
		});
		y += ROW;

		int totalH = y + Math.round(scroll) - slide - top;
		maxScroll = Math.max(0, totalH - ch2);
		scrollTarget = Math.max(0, Math.min(scrollTarget, maxScroll));
		endClip(g);
		drawScrollbar(g, cx + listW + 3, top, ch2, totalH);
	}

	private void rowBg(GuiGraphics g, int x, int y, int w, int h, boolean hov) {
		Draw.rounded(g, x, y, x + w, y + h, 3, hov ? lighten(card(), 0.05f) : card());
	}

	private int renderSetting(GuiGraphics g, Setting<?> s, int cx, int y, int listW, int mx, int my, boolean inView, float pt) {
		int right = cx + listW - 8;
		if (s instanceof InfoSetting info) {
			List<String> lines = wrap(info.get(), listW - 16);
			int h = lines.size() * 10 + 8;
			rowBg(g, cx, y, listW, h, false);
			for (int i = 0; i < lines.size(); i++) Draw.text(g, lines.get(i), cx + 8, y + 5 + i * 10, TEXT_DIM);
			return y + h + GAP;
		}

		boolean rowHov = inView && Draw.inside(mx, my, cx, y, listW, ROW);
		rowBg(g, cx, y, listW, ROW, rowHov);
		Draw.text(g, s.name, cx + 8, y + 6, TEXT);
		String key = "s:" + System.identityHashCode(s);

		if (s instanceof BoolSetting bs) {
			Draw.toggleSwitch(g, right - 18, y + 5, anim.get(key, bs.on() ? 1f : 0f, 14f), accent());
			hit(cx, y, listW, ROW, b -> bs.toggle());
		} else if (s instanceof NumberSetting ns) {
			int valW = 34;
			int slW = Math.max(40, Math.min(120, listW - 16 - Draw.width(s.name) - 14 - valW));
			int slX = right - valW - slW;
			float p = anim.get(key, (float) ns.progress(), 22f);
			Draw.rounded(g, (float) slX, y + 9f, (float) (slX + slW), y + 11f, 1f, TRACK);
			Draw.rounded(g, (float) slX, y + 9f, slX + p * slW, y + 11f, 1f, accent());
			boolean hov = inView && Draw.inside(mx, my, slX - 3, y, slW + 6, ROW);
			float kr = anim.get(key + "k", hov ? 4f : 3.2f, 18f);
			Draw.circle(g, slX + p * slW, y + 10f, kr, 0xFFFFFFFF);
			String v = ns.display();
			Draw.text(g, v, right - Draw.width(v), y + 6, TEXT_DIM);
			hit(slX - 4, y, slW + 8, ROW, b -> drag = new Drag(slX, slW, ns::setProgress));
		} else if (s instanceof ModeSetting ms) {
			int bw = Math.max(90, Draw.width(ms.get()) + 26);
			int bx = right - bw;
			boolean hov = inView && Draw.inside(mx, my, bx, y + 3, bw, ROW - 6);
			float h = anim.get(key, hov ? 1f : 0f, 18f);
			Draw.rounded(g, bx, y + 3, bx + bw, y + ROW - 3, 3, Colors.lerp(0x22FFFFFF, 0x44FFFFFF, h));
			Draw.text(g, "<", bx + 5, y + 6, TEXT_DIM);
			Draw.text(g, ">", bx + bw - 5 - Draw.width(">"), y + 6, TEXT_DIM);
			Draw.centered(g, ms.get(), bx + bw / 2, y + 6, TEXT);
			hit(bx, y + 3, bw, ROW - 6, b -> {
				if (b == 1) ms.previous();
				else ms.next();
			});
		} else if (s instanceof TextSetting ts) {
			int bw = Math.min(180, listW - 24 - Draw.width(s.name));
			int bx = right - bw;
			EditBox box = boxFor(ts, ts.maxLength, ts::set);
			if (!box.isFocused() && !box.getValue().equals(ts.get())) box.setValue(ts.get());
			renderBox(g, box, bx, y + 3, bw, ROW - 6, mx, my, pt, true);
		} else if (s instanceof KeySetting ks) {
			boolean listening = listeningSetting == ks;
			String label = listening ? "PRESS A KEY..." : (ks.isBound() ? Keys.keyName(ks.get()).toUpperCase(Locale.ROOT) : "NONE");
			int bw = Math.max(80, Draw.width(label) + 16);
			int bx = right - bw;
			boolean hov = inView && Draw.inside(mx, my, bx, y + 3, bw, ROW - 6);
			float pulse = listening ? 0.5f + 0.5f * (float) Math.sin(System.currentTimeMillis() / 150.0) : 0f;
			Draw.rounded(g, bx, y + 3, bx + bw, y + ROW - 3, 3, listening ? Colors.lerp(Colors.lerp(accent(), 0xFF000000, 0.3f), accent(), pulse) : (hov ? 0x44FFFFFF : 0x22FFFFFF));
			Draw.centered(g, label, bx + bw / 2, y + 6, TEXT);
			hit(bx, y + 3, bw, ROW - 6, b -> {
				if (b == 1) {
					ks.set(-1);
					listeningSetting = null;
				} else {
					listeningSetting = listening ? null : ks;
				}
			});
		} else if (s instanceof ColorSetting cs) {
			return renderColor(g, cs, cx, y, listW, mx, my, inView, pt);
		}
		return y + ROW + GAP;
	}

	private int renderColor(GuiGraphics g, ColorSetting cs, int cx, int y, int listW, int mx, int my, boolean inView, float pt) {
		int right = cx + listW - 8;
		boolean open = expanded.contains(cs);
		int sx = right - 24, sy = y + 4;
		checker(g, sx, sy, 24, 12);
		g.fill(sx, sy, sx + 24, sy + 12, cs.color());
		Draw.outline(g, sx - 1, sy - 1, sx + 25, sy + 13, 0x55FFFFFF);
		String label = cs.chroma ? "Chroma" : cs.hex();
		Draw.text(g, label, sx - 6 - Draw.width(label), y + 6, TEXT_DIM);
		Draw.text(g, open ? "-" : "+", sx - 6 - Draw.width(label) - 12, y + 6, TEXT_DIM);
		hit(cx, y, listW, ROW, b -> {
			if (!expanded.remove(cs)) expanded.add(cs);
		});
		y += ROW;
		float t = anim.get("cx:" + System.identityHashCode(cs), open ? 1f : 0f, 16f);
		if (t <= 0.01f) return y + GAP;

		int bodyH = SUB * 6 + 8;
		int shown = Math.round(bodyH * Anim.easeOut(t));
		// clip the picker so it can slide open
		g.enableScissor(cx, Math.max(clipY1, y - 2), cx + listW, Math.min(clipY2, y - 2 + shown));
		int prevC1 = clipY1, prevC2 = clipY2;
		clipY1 = Math.max(clipY1, y - 2);
		clipY2 = Math.min(clipY2, y - 2 + shown);

		int ex = cx + 8, ew = listW - 16;
		Draw.rounded(g, cx, y - 2, cx + listW, y - 2 + bodyH, 3, Colors.lerp(card(), 0xFF000000, 0.25f));
		int labelW = 64;
		int barX = ex + labelW, barW = ew - labelW - 4;
		int yy = y + 2;
		Draw.text(g, "Hue", ex, yy + 3, TEXT_DIM);
		for (int i = 0; i < barW; i += 2) g.fill(barX + i, yy + 2, barX + Math.min(i + 2, barW), yy + 10, Colors.hsv(i / (float) barW, 1f, 1f));
		knob(g, barX + Math.round(cs.hue * barW), yy + 1, 10);
		hit(barX - 3, yy, barW + 6, SUB, b -> drag = new Drag(barX, barW, p -> cs.setHsba((float) p, cs.sat, cs.bri, cs.alpha)));
		yy += SUB;
		Draw.text(g, "Saturation", ex, yy + 3, TEXT_DIM);
		for (int i = 0; i < barW; i += 2) g.fill(barX + i, yy + 2, barX + Math.min(i + 2, barW), yy + 10, Colors.hsv(cs.hue, i / (float) barW, Math.max(0.2f, cs.bri)));
		knob(g, barX + Math.round(cs.sat * barW), yy + 1, 10);
		hit(barX - 3, yy, barW + 6, SUB, b -> drag = new Drag(barX, barW, p -> cs.setHsba(cs.hue, (float) p, cs.bri, cs.alpha)));
		yy += SUB;
		Draw.text(g, "Brightness", ex, yy + 3, TEXT_DIM);
		for (int i = 0; i < barW; i += 2) g.fill(barX + i, yy + 2, barX + Math.min(i + 2, barW), yy + 10, Colors.hsv(cs.hue, cs.sat, i / (float) barW));
		knob(g, barX + Math.round(cs.bri * barW), yy + 1, 10);
		hit(barX - 3, yy, barW + 6, SUB, b -> drag = new Drag(barX, barW, p -> cs.setHsba(cs.hue, cs.sat, (float) p, cs.alpha)));
		yy += SUB;
		Draw.text(g, "Opacity", ex, yy + 3, TEXT_DIM);
		checker(g, barX, yy + 2, barW, 8);
		int opaque = cs.color() | 0xFF000000;
		for (int i = 0; i < barW; i += 2) g.fill(barX + i, yy + 2, barX + Math.min(i + 2, barW), yy + 10, Colors.withAlpha(opaque, Math.round(i / (float) barW * 255)));
		knob(g, barX + Math.round(cs.alpha * barW), yy + 1, 10);
		hit(barX - 3, yy, barW + 6, SUB, b -> drag = new Drag(barX, barW, p -> cs.setHsba(cs.hue, cs.sat, cs.bri, (float) p)));
		yy += SUB;
		Draw.text(g, "Chroma", ex, yy + 3, TEXT_DIM);
		Draw.toggleSwitch(g, barX, yy + 2, anim.get("ch:" + System.identityHashCode(cs), cs.chroma ? 1f : 0f, 14f), accent());
		hit(barX, yy, 22, SUB, b -> cs.chroma = !cs.chroma);
		int px0 = barX + 28;
		for (int i = 0; i < PRESETS.length; i++) {
			int sx2 = px0 + i * 12;
			if (sx2 + 10 > barX + barW) break;
			g.fill(sx2, yy + 2, sx2 + 10, yy + 12, PRESETS[i]);
			Draw.outline(g, sx2 - 1, yy + 1, sx2 + 11, yy + 13, 0x55FFFFFF);
			final int preset = PRESETS[i];
			hit(sx2, yy + 1, 10, 12, b -> {
				cs.chroma = false;
				cs.setArgb((Math.round(cs.alpha * 255) << 24) | (preset & 0xFFFFFF));
			});
		}
		yy += SUB;
		Draw.text(g, "Hex", ex, yy + 3, TEXT_DIM);
		EditBox box = boxFor(cs, 9, v -> cs.setHex(v));
		if (!box.isFocused() && !box.getValue().equalsIgnoreCase(cs.hex())) box.setValue(cs.hex());
		renderBox(g, box, barX, yy, Math.min(90, barW), 13, mx, my, pt, true);

		g.disableScissor(); // pops back to the outer list clip
		clipY1 = prevC1;
		clipY2 = prevC2;
		return y + shown + GAP;
	}

	// ================================================================== waypoints page

	private void renderWaypoints(GuiGraphics g, int mx, int my, int slide, float pt) {
		int cx = px + 10, cy = py + TB + 6, cw = pw - 20, ch = ph - TB - 14;
		Draw.text(g, "§lWAYPOINTS", cx, cy + 4, TEXT);
		String dim = Waypoints.prettyDimension(Waypoints.currentDimension());
		Draw.text(g, Waypoints.LIST.size() + " saved • you are in the " + dim, cx + Draw.width("§lWAYPOINTS") + 10, cy + 4, TEXT_DIM);

		String add = "+  ADD AT MY POSITION";
		int aw = Draw.width(add) + 16, ax = cx + cw - aw;
		boolean ha = Draw.inside(mx, my, ax, cy, aw, 16);
		float haT = anim.get("wpadd", ha ? 1f : 0f, 18f);
		Draw.rounded(g, ax, cy, ax + aw, cy + 16, 3, lighten(accent(), haT * 0.15f));
		Draw.centered(g, add, ax + aw / 2, cy + 4, 0xFFFFFFFF);
		hit(ax, cy, aw, 16, b -> Waypoints.addHere("Waypoint " + (Waypoints.LIST.size() + 1)));

		int top = cy + 22, lh = ch - 22;
		if (Waypoints.LIST.isEmpty()) {
			Draw.centered(g, "No waypoints yet. Click \"Add at my position\" to save where you are.", cx + cw / 2, top + lh / 2 - 10, TEXT_DIM);
			Draw.centered(g, "Turn on the Waypoints module to see them on your HUD and compass.", cx + cw / 2, top + lh / 2 + 2, TEXT_DIM);
			return;
		}

		scroll = anim.get("scroll", scrollTarget, 16f);
		beginClip(g, cx, top, cx + cw, top + lh);
		boolean inView = Draw.inside(mx, my, cx, top, cw, lh);
		int rowH = 24, gap = 3;
		int y = top - Math.round(scroll) + slide;
		var p = Game.player();
		String here = Waypoints.currentDimension();
		Waypoints.Waypoint remove = null;
		for (Waypoints.Waypoint w : Waypoints.LIST) {
			if (y + rowH >= clipY1 && y <= clipY2) {
				boolean hov = inView && Draw.inside(mx, my, cx, y, cw - 6, rowH);
				float h = anim.get("wp:" + System.identityHashCode(w), hov ? 1f : 0f, 14f);
				Draw.rounded(g, cx, y, cx + cw - 6, y + rowH, 4, Colors.lerp(card(), lighten(card(), 0.06f), h));
				// color swatch: click to cycle
				Draw.rounded(g, cx + 6, y + 6, cx + 18, y + 18, 3, w.color);
				hit(cx + 6, y + 6, 12, 12, b -> {
					int idx = 0;
					for (int i = 0; i < Waypoints.COLORS.length; i++) if (Waypoints.COLORS[i] == w.color) idx = i;
					w.color = Waypoints.COLORS[(idx + (b == 1 ? Waypoints.COLORS.length - 1 : 1)) % Waypoints.COLORS.length];
				});
				// name
				EditBox nb = boxFor(w, 32, v -> w.name = v);
				if (!nb.isFocused() && !nb.getValue().equals(w.name)) nb.setValue(w.name);
				renderBox(g, nb, cx + 24, y + 5, 110, 14, mx, my, pt, true);
				// coords + distance
				String info = w.x + ", " + w.y + ", " + w.z + "  §8" + Waypoints.prettyDimension(w.dimension);
				Draw.text(g, info, cx + 142, y + 8, TEXT_DIM);
				if (p != null && w.dimension.equals(here)) {
					String d = Math.round(w.distanceTo(p)) + "m";
					Draw.text(g, d, cx + cw - 6 - 52 - Draw.width(d), y + 8, TEXT);
				}
				// visible toggle
				int ex = cx + cw - 6 - 44;
				boolean he = inView && Draw.inside(mx, my, ex, y + 5, 16, 14);
				Draw.rounded(g, ex, y + 5, ex + 16, y + 19, 2, he ? 0x44FFFFFF : 0x22FFFFFF);
				Draw.iconEye(g, ex + 8, y + 12, w.visible ? TEXT : TEXT_DIM, w.visible);
				hit(ex, y + 5, 16, 14, b -> w.visible = !w.visible);
				// delete
				int dx = ex + 20;
				boolean hd = inView && Draw.inside(mx, my, dx, y + 5, 16, 14);
				Draw.rounded(g, dx, y + 5, dx + 16, y + 19, 2, hd ? 0xCCD9434B : 0x22FFFFFF);
				Draw.iconTrash(g, dx + 8, y + 12, TEXT);
				final Waypoints.Waypoint target = w;
				hit(dx, y + 5, 16, 14, b -> {
					Waypoints.LIST.remove(target);
					EditBox old = boxes.remove(target);
					if (old != null) this.removeWidget(old);
					Config.save();
				});
			}
			y += rowH + gap;
		}
		endClip(g);
		int totalH = Waypoints.LIST.size() * (rowH + gap) - gap;
		maxScroll = Math.max(0, totalH - lh);
		scrollTarget = Math.max(0, Math.min(scrollTarget, maxScroll));
		drawScrollbar(g, cx + cw - 2, top, lh, totalH);
	}

	// ================================================================== small widgets

	private void renderBox(GuiGraphics g, EditBox box, int x, int y, int w, int h, int mx, int my, float pt, boolean withBg) {
		boolean f = box.isFocused();
		if (withBg) {
			Draw.rounded(g, x, y, x + w, y + h, 3, f ? 0x50000000 : 0x22FFFFFF);
			if (f) Draw.hairline(g, x, y, x + w, y + h, 3, accent());
		}
		box.setX(x + 4);
		box.setY(y + (h - 8) / 2);
		box.setWidth(Math.max(10, w - 8));
		box.visible = !clipping || (y + h > clipY1 && y < clipY2);
		if (box.visible) box.render(g, mx, my, pt);
	}

	private static void knob(GuiGraphics g, int x, int y, int h) {
		g.fill(x - 2, y - 1, x + 2, y + h + 1, 0xFF000000);
		g.fill(x - 1, y, x + 1, y + h, 0xFFFFFFFF);
	}

	private static void checker(GuiGraphics g, int x, int y, int w, int h) {
		for (int i = 0; i < w; i += 4) {
			for (int j = 0; j < h; j += 4) {
				boolean dark = ((i / 4) + (j / 4)) % 2 == 0;
				g.fill(x + i, y + j, x + Math.min(i + 4, w), y + Math.min(j + 4, h), dark ? 0xFF8A8A8A : 0xFFCFCFCF);
			}
		}
	}

	private List<String> wrap(String text, int width) {
		List<String> out = new ArrayList<>();
		StringBuilder line = new StringBuilder();
		for (String word : text.split(" ")) {
			String test = line.isEmpty() ? word : line + " " + word;
			if (Draw.width(test) > width && !line.isEmpty()) {
				out.add(line.toString());
				line = new StringBuilder(word);
			} else {
				line = new StringBuilder(test);
			}
		}
		if (!line.isEmpty()) out.add(line.toString());
		return out;
	}

	private void drawScrollbar(GuiGraphics g, int x, int y, int h, int totalH) {
		if (maxScroll <= 0) return;
		int barH = Math.max(16, (int) (h * (h / (double) totalH)));
		int barY = y + (int) ((h - barH) * (scroll / maxScroll));
		g.fill(x, y, x + 2, y + h, 0x18FFFFFF);
		Draw.rounded(g, x, barY, x + 2, barY + barH, 1, 0x88FFFFFF);
	}

	private void beginClip(GuiGraphics g, int x1, int y1, int x2, int y2) {
		g.enableScissor(x1, y1, x2, y2);
		clipping = true;
		clipX1 = x1;
		clipY1 = y1;
		clipX2 = x2;
		clipY2 = y2;
	}

	private void endClip(GuiGraphics g) {
		g.disableScissor();
		clipping = false;
	}

	private void hit(int x, int y, int w, int h, IntConsumer action) {
		if (clipping) {
			int x1 = Math.max(x, clipX1), y1 = Math.max(y, clipY1);
			int x2 = Math.min(x + w, clipX2), y2 = Math.min(y + h, clipY2);
			if (x2 <= x1 || y2 <= y1) return;
			hits.add(new Hit(x1, y1, x2 - x1, y2 - y1, action));
		} else {
			hits.add(new Hit(x, y, w, h, action));
		}
	}

	// ================================================================== input

	private double tx(double mx) {
		int c = px + pw / 2;
		return c + (mx - c) / scale;
	}

	private double ty(double my) {
		int c = py + ph / 2;
		return c + (my - c) / scale;
	}

	private MouseButtonEvent transformed(MouseButtonEvent e) {
		if (scale == 1f) return e;
		return new MouseButtonEvent(tx(e.x()), ty(e.y()), e.buttonInfo());
	}

	private EditBox focusedBox() {
		for (EditBox b : boxes.values()) if (b.isFocused()) return b;
		return null;
	}

	private void unfocusAll() {
		for (EditBox b : boxes.values()) b.setFocused(false);
		this.setFocused(null);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (closing) return true;
		MouseButtonEvent ev = transformed(event);
		double mx = ev.x(), my = ev.y();

		for (EditBox b : boxes.values()) {
			if (b.visible && Draw.inside(mx, my, b.getX() - 4, b.getY() - 4, b.getWidth() + 8, 16)) {
				unfocusAll();
				this.setFocused(b);
				b.setFocused(true);
				b.mouseClicked(ev, doubleClick);
				return true;
			}
		}
		if (namingProfile && focusedBox() == profileInput) commitProfile();
		unfocusAll();

		KeySetting wasListening = listeningSetting;
		for (int i = hits.size() - 1; i >= 0; i--) {
			Hit h = hits.get(i);
			if (Draw.inside(mx, my, h.x, h.y, h.w, h.h)) {
				HudEditorScreen.click();
				h.action.accept(ev.button());
				if (wasListening != null && listeningSetting == wasListening) listeningSetting = null;
				if (drag != null) applyDrag(mx);
				return true;
			}
		}
		listeningSetting = null;
		// click outside the panel closes the menu
		if (!Draw.inside(mx, my, px, py, pw, ph)) {
			closeTo(null);
			return true;
		}
		return super.mouseClicked(ev, doubleClick);
	}

	private void applyDrag(double mx) {
		if (drag == null || drag.w <= 0) return;
		drag.set.accept(Math.max(0, Math.min(1, (mx - drag.x) / drag.w)));
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		if (drag != null) {
			applyDrag(tx(event.x()));
			return true;
		}
		return super.mouseDragged(transformed(event), dx, dy);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (drag != null) {
			drag = null;
			Config.save();
			return true;
		}
		return super.mouseReleased(transformed(event));
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
		scrollTarget = (float) Math.max(0, Math.min(maxScroll, scrollTarget - scrollY * 28));
		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		int key = event.key();
		if (listeningSetting != null) {
			if (key != GLFW.GLFW_KEY_ESCAPE) {
				listeningSetting.set(key);
				if (listeningSetting == cfg().menuKey) Keys.suppressMenuKey();
			}
			listeningSetting = null;
			Config.save();
			return true;
		}
		EditBox focused = focusedBox();
		if (focused != null) {
			if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
				if (focused == profileInput) commitProfile();
				unfocusAll();
				Config.save();
				return true;
			}
			if (key == GLFW.GLFW_KEY_ESCAPE) {
				if (focused == profileInput) namingProfile = false;
				unfocusAll();
				return true;
			}
			return super.keyPressed(event);
		}
		if (key == GLFW.GLFW_KEY_ESCAPE) {
			if (page == Page.MODS && settingsFor != null) switchTo(Page.MODS, null);
			else closeTo(null);
			return true;
		}
		if (key == cfg().menuKey.get()) {
			Keys.suppressMenuKey();
			closeTo(null);
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public void onClose() {
		closeTo(null);
	}

	@Override
	public void removed() {
		Config.save();
		super.removed();
	}
}
