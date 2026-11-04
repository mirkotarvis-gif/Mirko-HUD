package com.mirko.shiftmenu.gui;

import com.mirko.shiftmenu.Config;
import com.mirko.shiftmenu.module.HudModule;
import com.mirko.shiftmenu.module.ModuleManager;
import com.mirko.shiftmenu.module.impl.GeneralSettings;
import com.mirko.shiftmenu.util.Anim;
import com.mirko.shiftmenu.util.Colors;
import com.mirko.shiftmenu.util.Draw;
import com.mirko.shiftmenu.util.Keys;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

import java.util.ArrayList;
import java.util.List;

/**
 * The HUD layout editor (Right Shift): drag modules (snaps to center, edges, other modules and an
 * optional grid), scroll to resize, middle-click to reset size, right-click for settings,
 * MODS opens the mod menu. Fades in and out smoothly.
 */
public class HudEditorScreen extends Screen {
	private static final int BTN_W = 96;
	private static final int BTN_H = 26;
	private static final int SNAP = 4;

	private final Anim anim = new Anim();
	private final HudModule focus;
	private final long openedAt = System.currentTimeMillis();
	private HudModule dragging;
	private int dragOffX, dragOffY;
	private int guideX = -1, guideY = -1;
	private boolean closing;
	private Screen next;
	private float openT;

	public HudEditorScreen() {
		this(null);
	}

	/** @param focus module to highlight (opened from its gear button) */
	public HudEditorScreen(HudModule focus) {
		super(Component.literal("Edit HUD Layout"));
		this.focus = focus;
	}

	private static GeneralSettings cfg() {
		return ModuleManager.SETTINGS;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		int a = (int) (cfg().editorDim.get() / 100.0 * 255 * openT);
		g.fill(0, 0, this.width, this.height, a << 24);
		int grid = cfg().gridSnap.get().intValue();
		if (grid > 0 && cfg().showGrid.on()) {
			int ga = Math.round(0x18 * openT);
			for (int x = 0; x < this.width; x += grid) g.fill(x, 0, x + 1, this.height, (ga << 24) | 0xFFFFFF);
			for (int y = 0; y < this.height; y += grid) g.fill(0, y, this.width, y + 1, (ga << 24) | 0xFFFFFF);
		}
	}

	private void closeTo(Screen target) {
		if (closing) return;
		Config.save();
		closing = true;
		next = target;
		dragging = null;
	}

	@Override
	public void tick() {
		if (closing && openT <= 0.04f) this.minecraft.setScreen(next);
	}

	private int btnX() {
		return this.width / 2 - BTN_W / 2;
	}

	private int btnY() {
		return this.height / 2 - BTN_H / 2;
	}

	private boolean overButton(double mx, double my) {
		return Draw.inside(mx, my, btnX(), btnY(), BTN_W, BTN_H);
	}

	private HudModule moduleAt(double mx, double my) {
		for (int i = ModuleManager.MODULES.size() - 1; i >= 0; i--) {
			HudModule m = ModuleManager.MODULES.get(i);
			if (!m.isEnabled() || !m.isDraggable()) continue;
			int x = m.getX(this.width, true), y = m.getY(this.height, true);
			int w = Math.max(4, m.scaledWidth(true)), h = Math.max(4, m.scaledHeight(true));
			if (Draw.inside(mx, my, x - 1, y - 1, w + 2, h + 2)) return m;
		}
		return null;
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		anim.frame();
		openT = anim.get("open", closing ? 0f : 1f, 14f, 0f);
		int accent = cfg().accent.color();
		int alpha = Math.round(255 * openT);

		float gx = anim.get("guideX", dragging != null && guideX >= 0 ? 1f : 0f, 20f);
		float gy = anim.get("guideY", dragging != null && guideY >= 0 ? 1f : 0f, 20f);
		if (guideX >= 0 && gx > 0.01f) g.fill(guideX, 0, guideX + 1, this.height, Colors.withAlpha(accent, Math.round(0xCC * gx)));
		if (guideY >= 0 && gy > 0.01f) g.fill(0, guideY, this.width, guideY + 1, Colors.withAlpha(accent, Math.round(0xCC * gy)));

		if (ModuleManager.CROSSHAIR.isEnabled()) ModuleManager.CROSSHAIR.drawCentered(g);

		HudModule hovered = dragging != null ? dragging : moduleAt(mouseX, mouseY);
		boolean pulseFocus = focus != null && System.currentTimeMillis() - openedAt < 2000;
		for (HudModule m : ModuleManager.MODULES) {
			if (!m.isEnabled() || !m.isDraggable()) continue;
			m.renderAt(g, true);
			int x = m.getX(this.width, true), y = m.getY(this.height, true);
			int w = m.scaledWidth(true), h = m.scaledHeight(true);
			float hv = anim.get("hov:" + m.id, m == hovered ? 1f : 0f, 18f);
			if (hv > 0.01f) {
				g.fill(x, y, x + w, y + h, Colors.withAlpha(0xFFFFFF, Math.round(0x22 * hv)));
				Draw.outline(g, x - 1, y - 1, x + w + 1, y + h + 1, Colors.withAlpha(0xFFFFFF, Math.round(0xFF * hv * openT)));
			}
			if (hv < 0.99f && cfg().outlines.on()) {
				Draw.dashed(g, x - 1, y - 1, x + w + 1, y + h + 1, Colors.withAlpha(0xFFFFFF, Math.round(0x88 * (1 - hv) * openT)));
			}
			if (pulseFocus && m == focus) {
				float p = 0.5f + 0.5f * (float) Math.sin((System.currentTimeMillis() - openedAt) / 120.0);
				Draw.outline(g, x - 2, y - 2, x + w + 2, y + h + 2, Colors.withAlpha(accent, Math.round(255 * p)));
			}
			if (hv > 0.01f) {
				String label = m.name + "  " + Math.round(m.scale.get() * 100) + "%";
				int ly = y > 12 ? y - 11 : y + h + 3;
				int lw = Draw.width(label) + 6;
				Draw.rounded(g, x, ly - 1, x + lw, ly + 9, 2, Colors.withAlpha(0x000000, Math.round(0xCC * hv)));
				Draw.text(g, label, x + 3, ly, Colors.withAlpha(0xFFFFFF, Math.max(5, Math.round(255 * hv))));
			}
		}

		// MODS button (slides up + fades in, grows a little on hover)
		boolean hb = dragging == null && overButton(mouseX, mouseY);
		float hbT = anim.get("btn", hb ? 1f : 0f, 16f);
		float rise = (1f - Anim.easeOut(openT)) * 12f;
		int grow = Math.round(hbT * 3);
		int bx = btnX() - grow, by = btnY() - grow + Math.round(rise);
		int bw = BTN_W + grow * 2, bh = BTN_H + grow * 2;
		Draw.rounded(g, bx, by, bx + bw, by + bh, 6, Colors.withAlpha(Colors.lerp(0x141416, 0x26262B, hbT), Math.round(0xE6 * openT)));
		Draw.hairline(g, bx, by, bx + bw, by + bh, 6, Colors.withAlpha(Colors.lerp(0xFFFFFF, accent, hbT), Math.round((0x30 + 0xA0 * hbT) * openT)));
		Draw.centered(g, "§lMODS", bx + bw / 2, by + (bh - 8) / 2, Colors.withAlpha(0xFFFFFF, Math.max(5, alpha)));

		int hintA = Math.max(5, Math.round(0xAA * openT));
		String hint = "Drag to move  •  Scroll to resize  •  Right-click for settings";
		Draw.centered(g, hint, this.width / 2, btnY() + BTN_H + 10 + Math.round(rise), Colors.withAlpha(0xFFFFFF, hintA));
		String keyHint = "Press " + Keys.keyName(cfg().menuKey.get()) + " or ESC to close";
		Draw.centered(g, keyHint, this.width / 2, btnY() + BTN_H + 22 + Math.round(rise), Colors.withAlpha(0xFFFFFF, Math.max(5, hintA * 2 / 3)));

		com.mirko.shiftmenu.util.Toasts.render(g);
		super.render(g, mouseX, mouseY, partialTick);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (closing) return true;
		double mx = event.x(), my = event.y();
		int button = event.button();

		if (button == 0 && overButton(mx, my)) {
			click();
			Config.save();
			this.minecraft.setScreen(new ModMenuScreen(null));
			return true;
		}

		HudModule m = moduleAt(mx, my);
		if (m != null) {
			if (button == 0) {
				dragging = m;
				dragOffX = (int) mx - m.getX(this.width, true);
				dragOffY = (int) my - m.getY(this.height, true);
			} else if (button == 1) {
				click();
				Config.save();
				this.minecraft.setScreen(new ModMenuScreen(m));
			} else if (button == 2) {
				m.scale.reset();
			}
			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		if (dragging == null) return super.mouseDragged(event, dx, dy);
		int w = dragging.scaledWidth(true), h = dragging.scaledHeight(true);
		int nx = (int) event.x() - dragOffX;
		int ny = (int) event.y() - dragOffY;

		int grid = cfg().gridSnap.get().intValue();
		if (grid > 0) {
			nx = Math.round(nx / (float) grid) * grid;
			ny = Math.round(ny / (float) grid) * grid;
		}

		List<Integer> xs = new ArrayList<>();
		List<Integer> ys = new ArrayList<>();
		if (cfg().snapCenter.on()) {
			xs.add(this.width / 2);
			ys.add(this.height / 2);
		}
		if (cfg().snapEdges.on()) {
			xs.add(0);
			xs.add(this.width);
			ys.add(0);
			ys.add(this.height);
		}
		if (cfg().snapModules.on()) {
			for (HudModule o : ModuleManager.MODULES) {
				if (o == dragging || !o.isEnabled() || !o.isDraggable()) continue;
				int ox = o.getX(this.width, true), oy = o.getY(this.height, true);
				int ow = o.scaledWidth(true), oh = o.scaledHeight(true);
				xs.add(ox);
				xs.add(ox + ow);
				xs.add(ox + ow / 2);
				ys.add(oy);
				ys.add(oy + oh);
				ys.add(oy + oh / 2);
			}
		}

		int newGuideX = -1, bestX = SNAP + 1;
		int[] myX = {nx, nx + w, nx + w / 2};
		for (int line : xs) {
			for (int k = 0; k < 3; k++) {
				int d = line - myX[k];
				if (Math.abs(d) < Math.abs(bestX)) {
					bestX = d;
					newGuideX = line;
				}
			}
		}
		if (newGuideX >= 0) nx += bestX;
		if (newGuideX >= 0) guideX = newGuideX;
		else guideX = -1;

		int newGuideY = -1, bestY = SNAP + 1;
		int[] myY = {ny, ny + h, ny + h / 2};
		for (int line : ys) {
			for (int k = 0; k < 3; k++) {
				int d = line - myY[k];
				if (Math.abs(d) < Math.abs(bestY)) {
					bestY = d;
					newGuideY = line;
				}
			}
		}
		if (newGuideY >= 0) ny += bestY;
		guideY = newGuideY;

		nx = Math.max(0, Math.min(nx, this.width - w));
		ny = Math.max(0, Math.min(ny, this.height - h));
		dragging.setPosition(nx, ny, this.width, this.height);
		return true;
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (dragging != null) {
			dragging = null;
			Config.save();
			return true;
		}
		return super.mouseReleased(event);
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
		HudModule m = moduleAt(mx, my);
		if (m != null && scrollY != 0) {
			m.scale.set(m.scale.get() + (scrollY > 0 ? 0.05 : -0.05));
			Config.save();
			return true;
		}
		return super.mouseScrolled(mx, my, scrollX, scrollY);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (event.key() == cfg().menuKey.get()) {
			Keys.suppressMenuKey();
			closeTo(null);
			return true;
		}
		if (event.key() == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
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

	static void click() {
		if (!ModuleManager.SETTINGS.sounds.on()) return;
		Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
	}
}
