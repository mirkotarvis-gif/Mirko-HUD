package com.mirko.shiftmenu.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

public final class Draw {
	private Draw() {}

	/** Filled rectangle with rounded corners (pixel-accurate, no overlapping fills). */
	public static void rounded(GuiGraphics g, int x1, int y1, int x2, int y2, int r, int color) {
		int w = x2 - x1, h = y2 - y1;
		if (w <= 0 || h <= 0) return;
		r = Math.max(0, Math.min(r, Math.min(w, h) / 2));
		if (r == 0) {
			g.fill(x1, y1, x2, y2, color);
			return;
		}
		g.fill(x1 + r, y1, x2 - r, y2, color);       // center column
		g.fill(x1, y1 + r, x1 + r, y2 - r, color);   // left side
		g.fill(x2 - r, y1 + r, x2, y2 - r, color);   // right side
		for (int i = 0; i < r; i++) {
			double dy = r - i - 0.5;
			int inset = (int) Math.round(r - Math.sqrt(Math.max(0, r * r - dy * dy)));
			g.fill(x1 + inset, y1 + i, x1 + r, y1 + i + 1, color);
			g.fill(x2 - r, y1 + i, x2 - inset, y1 + i + 1, color);
			g.fill(x1 + inset, y2 - i - 1, x1 + r, y2 - i, color);
			g.fill(x2 - r, y2 - i - 1, x2 - inset, y2 - i, color);
		}
	}

	/** Rounded ring of thickness t (no overlapping fills, so translucent colors stay even). */
	public static void roundedBorder(GuiGraphics g, int x1, int y1, int x2, int y2, int r, int t, int color) {
		int w = x2 - x1, h = y2 - y1;
		if (w <= 0 || h <= 0 || t <= 0) return;
		t = Math.min(t, Math.min(w, h) / 2);
		r = Math.max(0, Math.min(r, Math.min(w, h) / 2));
		if (r <= t) {
			// square-ish corners
			g.fill(x1, y1, x2, y1 + t, color);
			g.fill(x1, y2 - t, x2, y2, color);
			g.fill(x1, y1 + t, x1 + t, y2 - t, color);
			g.fill(x2 - t, y1 + t, x2, y2 - t, color);
			return;
		}
		g.fill(x1 + r, y1, x2 - r, y1 + t, color);
		g.fill(x1 + r, y2 - t, x2 - r, y2, color);
		g.fill(x1, y1 + r, x1 + t, y2 - r, color);
		g.fill(x2 - t, y1 + r, x2, y2 - r, color);
		int ri = r - t;
		for (int i = 0; i < r; i++) {
			double dy = r - i - 0.5;
			int outer = (int) Math.round(r - Math.sqrt(Math.max(0, r * r - dy * dy)));
			int innerEnd = dy >= ri ? r : (int) Math.round(r - Math.sqrt(Math.max(0, ri * ri - dy * dy)));
			if (innerEnd <= outer) continue;
			g.fill(x1 + outer, y1 + i, x1 + innerEnd, y1 + i + 1, color);
			g.fill(x2 - innerEnd, y1 + i, x2 - outer, y1 + i + 1, color);
			g.fill(x1 + outer, y2 - i - 1, x1 + innerEnd, y2 - i, color);
			g.fill(x2 - innerEnd, y2 - i - 1, x2 - outer, y2 - i, color);
		}
	}

	/** Background + optional border, the shared "box" look every module uses. */
	public static void box(GuiGraphics g, int x1, int y1, int x2, int y2, int radius, boolean bg, int bgColor,
						   boolean border, int borderThickness, int borderColor) {
		int t = border ? borderThickness : 0;
		if (bg) {
			rounded(g, x1 + t, y1 + t, x2 - t, y2 - t, Math.max(0, radius - t), bgColor);
		}
		if (border) {
			roundedBorder(g, x1, y1, x2, y2, radius, t, borderColor);
		}
	}

	public static void outline(GuiGraphics g, int x1, int y1, int x2, int y2, int color) {
		g.fill(x1, y1, x2, y1 + 1, color);
		g.fill(x1, y2 - 1, x2, y2, color);
		g.fill(x1, y1 + 1, x1 + 1, y2 - 1, color);
		g.fill(x2 - 1, y1 + 1, x2, y2 - 1, color);
	}

	public static void dashed(GuiGraphics g, int x1, int y1, int x2, int y2, int color) {
		int dash = 3, gap = 2;
		for (int x = x1; x < x2; x += dash + gap) {
			int e = Math.min(x + dash, x2);
			g.fill(x, y1, e, y1 + 1, color);
			g.fill(x, y2 - 1, e, y2, color);
		}
		for (int y = y1 + 1; y < y2 - 1; y += dash + gap) {
			int e = Math.min(y + dash, y2 - 1);
			g.fill(x1, y, x1 + 1, e, color);
			g.fill(x2 - 1, y, x2, e, color);
		}
	}

	private static Fonts.TextStyle menu() {
		return com.mirko.shiftmenu.module.ModuleManager.SETTINGS.menuStyle();
	}

	/** Menu text (uses the menu font from the Settings page). */
	public static void text(GuiGraphics g, String s, int x, int y, int color) {
		Fonts.draw(g, s, x, y, color, false, menu());
	}

	public static void centered(GuiGraphics g, String s, int cx, int y, int color) {
		int w = width(s);
		Fonts.draw(g, s, cx - w / 2, y, color, false, menu());
	}

	public static int width(String s) {
		return Fonts.width(s, menu());
	}

	public static boolean inside(double mx, double my, int x, int y, int w, int h) {
		return mx >= x && my >= y && mx < x + w && my < y + h;
	}

	/** iOS-style switch, 18x10. */
	public static void toggleSwitch(GuiGraphics g, int x, int y, boolean on) {
		toggleSwitch(g, x, y, on ? 1f : 0f, 0xFF3BB273);
	}

	/** Animated switch: t = 0 (off) .. 1 (on). */
	public static void toggleSwitch(GuiGraphics g, int x, int y, float t, int onColor) {
		rounded(g, x, y, x + 18, y + 10, 5, Colors.lerp(0xFF4A4A52, onColor, t));
		int kx = x + 1 + Math.round(8 * t);
		rounded(g, kx, y + 1, kx + 8, y + 9, 4, 0xFFFFFFFF);
	}

	/** Item icon at any size. */
	public static void item(GuiGraphics g, net.minecraft.world.item.ItemStack stack, float x, float y, float size) {
		if (stack == null || stack.isEmpty()) return;
		g.pose().pushMatrix();
		g.pose().translate(x, y);
		g.pose().scale(size / 16f, size / 16f);
		g.renderItem(stack, 0, 0);
		g.pose().popMatrix();
	}

	// ---- tiny pixel icons (cx, cy = center) ----

	public static void iconGear(GuiGraphics g, int cx, int cy, int c) {
		roundedBorder(g, cx - 3, cy - 3, cx + 4, cy + 4, 3, 2, c);
		g.fill(cx, cy - 5, cx + 1, cy - 3, c);
		g.fill(cx, cy + 4, cx + 1, cy + 6, c);
		g.fill(cx - 5, cy, cx - 3, cy + 1, c);
		g.fill(cx + 4, cy, cx + 6, cy + 1, c);
		g.fill(cx - 4, cy - 4, cx - 2, cy - 2, c);
		g.fill(cx + 3, cy - 4, cx + 5, cy - 2, c);
		g.fill(cx - 4, cy + 3, cx - 2, cy + 5, c);
		g.fill(cx + 3, cy + 3, cx + 5, cy + 5, c);
	}

	public static void iconClose(GuiGraphics g, int cx, int cy, int c) {
		for (int i = -3; i <= 3; i++) {
			g.fill(cx + i, cy + i, cx + i + 1, cy + i + 1, c);
			g.fill(cx + i, cy - i, cx + i + 1, cy - i + 1, c);
			g.fill(cx + i + 1, cy + i, cx + i + 2, cy + i + 1, c);
			g.fill(cx + i + 1, cy - i, cx + i + 2, cy - i + 1, c);
		}
	}

	public static void iconGrid(GuiGraphics g, int cx, int cy, int c) {
		for (int i = -1; i <= 1; i++) for (int j = -1; j <= 1; j++) g.fill(cx + i * 3 - 1, cy + j * 3 - 1, cx + i * 3 + 1, cy + j * 3 + 1, c);
	}

	public static void iconList(GuiGraphics g, int cx, int cy, int c) {
		for (int j = -1; j <= 1; j++) {
			g.fill(cx - 4, cy + j * 3 - 1, cx - 2, cy + j * 3 + 1, c);
			g.fill(cx - 1, cy + j * 3 - 1, cx + 5, cy + j * 3 + 1, c);
		}
	}

	public static void iconSort(GuiGraphics g, int cx, int cy, int c) {
		g.fill(cx - 3, cy - 4, cx - 2, cy + 4, c);
		g.fill(cx - 5, cy - 2, cx, cy - 1, c);
		g.fill(cx - 4, cy - 3, cx - 1, cy - 2, c);
		g.fill(cx + 2, cy - 4, cx + 3, cy + 4, c);
		g.fill(cx, cy + 1, cx + 5, cy + 2, c);
		g.fill(cx + 1, cy + 2, cx + 4, cy + 3, c);
	}

	public static void iconPencil(GuiGraphics g, int cx, int cy, int c) {
		for (int i = -3; i <= 2; i++) g.fill(cx + i, cy - i, cx + i + 2, cy - i + 1, c);
		g.fill(cx - 4, cy + 3, cx - 2, cy + 4, c);
	}

	public static void iconTrash(GuiGraphics g, int cx, int cy, int c) {
		g.fill(cx - 4, cy - 3, cx + 5, cy - 2, c);
		g.fill(cx - 1, cy - 5, cx + 2, cy - 3, c);
		outline(g, cx - 3, cy - 2, cx + 4, cy + 5, c);
		g.fill(cx, cy - 1, cx + 1, cy + 4, c);
	}

	public static void iconEye(GuiGraphics g, int cx, int cy, int c, boolean open) {
		g.fill(cx - 4, cy - 1, cx + 5, cy, c);
		g.fill(cx - 2, cy - 2, cx + 3, cy - 1, c);
		g.fill(cx - 4, cy + 1, cx + 5, cy + 2, c);
		g.fill(cx - 2, cy + 2, cx + 3, cy + 3, c);
		if (open) g.fill(cx - 1, cy - 1, cx + 2, cy + 2, c);
		else for (int i = -4; i <= 4; i++) g.fill(cx + i, cy - i / 2, cx + i + 1, cy - i / 2 + 1, c);
	}

	public static void iconPlus(GuiGraphics g, int cx, int cy, int c) {
		g.fill(cx - 4, cy, cx + 5, cy + 1, c);
		g.fill(cx, cy - 4, cx + 1, cy + 5, c);
	}

	public static void iconBack(GuiGraphics g, int cx, int cy, int c) {
		for (int i = 0; i < 4; i++) {
			g.fill(cx - 2 + i, cy - i, cx - 1 + i, cy - i + 1, c);
			g.fill(cx - 2 + i, cy + i, cx - 1 + i, cy + i + 1, c);
		}
	}
}
