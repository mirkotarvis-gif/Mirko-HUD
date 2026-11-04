package com.mirko.shiftmenu.util;

import com.mirko.shiftmenu.module.ModuleManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * All UI drawing. With "HD Rendering" on (default), shapes are drawn at your monitor's real
 * pixel resolution instead of Minecraft's big GUI pixels: anti-aliased rounded corners,
 * hairline borders and smooth vector icons - the "high-res texture pack" look.
 */
public final class Draw {
	private Draw() {}

	// ================================================================== HD core

	/** Real screen pixels per GUI pixel (1 when HD rendering is off). */
	public static int hd(GuiGraphics g) {
		if (!ModuleManager.SETTINGS.hdRendering.on()) return 1;
		int gw = g.guiWidth();
		if (gw <= 0) return 1;
		return Math.max(1, Math.round(Minecraft.getInstance().getWindow().getWidth() / (float) gw));
	}

	/** Thickness (in real pixels) of a crisp hairline. */
	private static int hair(int k) {
		return Math.max(1, Math.round(k / 2f));
	}

	/**
	 * Cached anti-aliasing coverage for one rounded corner of radius r (real px) and ring thickness t
	 * (0 = filled). rows[i] = runs {startX, endX, alpha 0..255} measured from the outer edge.
	 */
	private static final Map<Long, int[][][]> RUNS = new HashMap<>();

	private static int[][][] runs(int r, int t) {
		long key = ((long) r << 20) | t;
		int[][][] cached = RUNS.get(key);
		if (cached != null) return cached;
		boolean aa = ModuleManager.SETTINGS.antiAliasing.on();
		int[][][] rows = new int[r][][];
		double ri = t > 0 ? r - t : -1;
		for (int i = 0; i < r; i++) {
			double dy = r - i - 0.5;
			List<int[]> list = new ArrayList<>();
			int runStart = -1;
			for (int j = 0; j < r; j++) {
				double dx = r - j - 0.5;
				double d = Math.sqrt(dx * dx + dy * dy);
				double cov = clamp01(r - d + 0.5);
				if (t > 0 && ri > 0) cov -= clamp01(ri - d + 0.5);
				if (!aa) cov = cov >= 0.5 ? 1 : 0;
				int a = (int) Math.round(cov * 255);
				if (a >= 250) {
					if (runStart < 0) runStart = j;
				} else {
					if (runStart >= 0) {
						list.add(new int[]{runStart, j, 255});
						runStart = -1;
					}
					if (a > 4) list.add(new int[]{j, j + 1, a});
				}
			}
			if (runStart >= 0) list.add(new int[]{runStart, r, 255});
			rows[i] = list.toArray(new int[0][]);
		}
		RUNS.put(key, rows);
		return rows;
	}

	/** Call when the anti-aliasing setting changes. */
	public static void clearCache() {
		RUNS.clear();
	}

	private static double clamp01(double v) {
		return v < 0 ? 0 : (v > 1 ? 1 : v);
	}

	private static int mulAlpha(int color, int a255) {
		int a = ((color >>> 24) & 0xFF) * a255 / 255;
		return (a << 24) | (color & 0xFFFFFF);
	}

	private static void corners(GuiGraphics g, int x1, int y1, int x2, int y2, int r, int t, int color) {
		int[][][] rows = runs(r, t);
		for (int i = 0; i < r; i++) {
			int top = y1 + i, bot = y2 - 1 - i;
			for (int[] run : rows[i]) {
				int c = run[2] == 255 ? color : mulAlpha(color, run[2]);
				g.fill(x1 + run[0], top, x1 + run[1], top + 1, c);
				g.fill(x2 - run[1], top, x2 - run[0], top + 1, c);
				g.fill(x1 + run[0], bot, x1 + run[1], bot + 1, c);
				g.fill(x2 - run[1], bot, x2 - run[0], bot + 1, c);
			}
		}
	}

	private static void begin(GuiGraphics g, int k) {
		g.pose().pushMatrix();
		g.pose().scale(1f / k, 1f / k);
	}

	private static void end(GuiGraphics g) {
		g.pose().popMatrix();
	}

	// ================================================================== shapes

	/** Filled rounded rectangle (GUI coordinates), anti-aliased in HD. */
	public static void rounded(GuiGraphics g, float x1, float y1, float x2, float y2, float r, int color) {
		if (x2 <= x1 || y2 <= y1 || (color >>> 24) == 0) return;
		int k = hd(g);
		int X1 = Math.round(x1 * k), Y1 = Math.round(y1 * k), X2 = Math.round(x2 * k), Y2 = Math.round(y2 * k);
		int R = Math.max(0, Math.min(Math.round(r * k), Math.min(X2 - X1, Y2 - Y1) / 2));
		if (k > 1) begin(g, k);
		if (R == 0) {
			g.fill(X1, Y1, X2, Y2, color);
		} else {
			g.fill(X1 + R, Y1, X2 - R, Y2, color);
			g.fill(X1, Y1 + R, X1 + R, Y2 - R, color);
			g.fill(X2 - R, Y1 + R, X2, Y2 - R, color);
			corners(g, X1, Y1, X2, Y2, R, 0, color);
		}
		if (k > 1) end(g);
	}

	public static void rounded(GuiGraphics g, int x1, int y1, int x2, int y2, int r, int color) {
		rounded(g, (float) x1, (float) y1, (float) x2, (float) y2, (float) r, color);
	}

	/** Rounded ring. Thickness t is in GUI pixels; t <= 0 means a crisp hairline. */
	public static void roundedBorder(GuiGraphics g, float x1, float y1, float x2, float y2, float r, float t, int color) {
		if (x2 <= x1 || y2 <= y1 || (color >>> 24) == 0) return;
		int k = hd(g);
		int X1 = Math.round(x1 * k), Y1 = Math.round(y1 * k), X2 = Math.round(x2 * k), Y2 = Math.round(y2 * k);
		int T = t <= 0 ? hair(k) : Math.max(1, Math.round(t * k));
		T = Math.min(T, Math.min(X2 - X1, Y2 - Y1) / 2);
		int R = Math.max(0, Math.min(Math.round(r * k), Math.min(X2 - X1, Y2 - Y1) / 2));
		if (k > 1) begin(g, k);
		if (R <= T) {
			g.fill(X1, Y1, X2, Y1 + T, color);
			g.fill(X1, Y2 - T, X2, Y2, color);
			g.fill(X1, Y1 + T, X1 + T, Y2 - T, color);
			g.fill(X2 - T, Y1 + T, X2, Y2 - T, color);
		} else {
			g.fill(X1 + R, Y1, X2 - R, Y1 + T, color);
			g.fill(X1 + R, Y2 - T, X2 - R, Y2, color);
			g.fill(X1, Y1 + R, X1 + T, Y2 - R, color);
			g.fill(X2 - T, Y1 + R, X2, Y2 - R, color);
			corners(g, X1, Y1, X2, Y2, R, T, color);
		}
		if (k > 1) end(g);
	}

	public static void roundedBorder(GuiGraphics g, int x1, int y1, int x2, int y2, int r, int t, int color) {
		roundedBorder(g, (float) x1, (float) y1, (float) x2, (float) y2, (float) r, (float) t, color);
	}

	/** Thin crisp border (hairline in HD). */
	public static void hairline(GuiGraphics g, float x1, float y1, float x2, float y2, float r, int color) {
		roundedBorder(g, x1, y1, x2, y2, r, 0f, color);
	}

	/** Circle / filled dot. */
	public static void circle(GuiGraphics g, float cx, float cy, float radius, int color) {
		rounded(g, cx - radius, cy - radius, cx + radius, cy + radius, radius, color);
	}

	public static void ring(GuiGraphics g, float cx, float cy, float radius, float thickness, int color) {
		roundedBorder(g, cx - radius, cy - radius, cx + radius, cy + radius, radius, thickness, color);
	}

	/** A straight line of any angle with rounded ends. */
	public static void line(GuiGraphics g, float x1, float y1, float x2, float y2, float thick, int color) {
		float dx = x2 - x1, dy = y2 - y1;
		float len = (float) Math.sqrt(dx * dx + dy * dy);
		if (len <= 0.01f) return;
		int k = hd(g);
		g.pose().pushMatrix();
		g.pose().translate(x1, y1);
		g.pose().rotate((float) Math.atan2(dy, dx));
		g.pose().scale(1f / k, 1f / k);
		int half = Math.max(1, Math.round(thick * k / 2f));
		g.fill(0, -half, Math.round(len * k), half, color);
		g.pose().popMatrix();
		if (thick * k >= 3) {
			circle(g, x1, y1, thick / 2f, color);
			circle(g, x2, y2, thick / 2f, color);
		}
	}

	/** Background + optional border, the shared "box" look every module uses. */
	public static void box(GuiGraphics g, int x1, int y1, int x2, int y2, int radius, boolean bg, int bgColor,
						   boolean border, int borderThickness, int borderColor) {
		int t = border ? borderThickness : 0;
		if (bg) rounded(g, (float) (x1 + t), (float) (y1 + t), (float) (x2 - t), (float) (y2 - t), (float) Math.max(0, radius - t), bgColor);
		if (border) roundedBorder(g, x1, y1, x2, y2, radius, t, borderColor);
	}

	/** Square outline; hairline-thin in HD. */
	public static void outline(GuiGraphics g, int x1, int y1, int x2, int y2, int color) {
		if (hd(g) > 1) {
			hairline(g, x1, y1, x2, y2, 0, color);
			return;
		}
		g.fill(x1, y1, x2, y1 + 1, color);
		g.fill(x1, y2 - 1, x2, y2, color);
		g.fill(x1, y1 + 1, x1 + 1, y2 - 1, color);
		g.fill(x2 - 1, y1 + 1, x2, y2 - 1, color);
	}

	/** Dashed outline (editor). */
	public static void dashed(GuiGraphics g, int x1, int y1, int x2, int y2, int color) {
		int k = hd(g);
		int X1 = x1 * k, Y1 = y1 * k, X2 = x2 * k, Y2 = y2 * k, T = hair(k);
		int dash = 3 * k, gap = 2 * k;
		if (k > 1) begin(g, k);
		for (int x = X1; x < X2; x += dash + gap) {
			int e = Math.min(x + dash, X2);
			g.fill(x, Y1, e, Y1 + T, color);
			g.fill(x, Y2 - T, e, Y2, color);
		}
		for (int y = Y1 + T; y < Y2 - T; y += dash + gap) {
			int e = Math.min(y + dash, Y2 - T);
			g.fill(X1, y, X1 + T, e, color);
			g.fill(X2 - T, y, X2, e, color);
		}
		if (k > 1) end(g);
	}

	// ================================================================== text

	private static Fonts.TextStyle menu() {
		return ModuleManager.SETTINGS.menuStyle();
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

	// ================================================================== widgets

	/** iOS-style switch, 18x10. */
	public static void toggleSwitch(GuiGraphics g, int x, int y, boolean on) {
		toggleSwitch(g, x, y, on ? 1f : 0f, ModuleManager.SETTINGS.accent.color());
	}

	/** Animated switch: t = 0 (off) .. 1 (on). */
	public static void toggleSwitch(GuiGraphics g, int x, int y, float t, int onColor) {
		rounded(g, (float) x, (float) y, x + 18f, y + 10f, 5f, Colors.lerp(0xFF3E3E46, onColor, t));
		float kx = x + 5f + 8f * t;
		circle(g, kx, y + 5f, 3.6f, 0xFFFFFFFF);
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

	// ================================================================== vector icons (cx, cy = center)

	public static void iconGear(GuiGraphics g, int cx, int cy, int c) {
		ring(g, cx + 0.5f, cy + 0.5f, 2.9f, 1.3f, c);
		for (int i = 0; i < 8; i++) {
			double a = i * Math.PI / 4;
			float ox = cx + 0.5f, oy = cy + 0.5f;
			line(g, ox + (float) Math.cos(a) * 3.4f, oy + (float) Math.sin(a) * 3.4f,
					ox + (float) Math.cos(a) * 5f, oy + (float) Math.sin(a) * 5f, 1.6f, c);
		}
	}

	public static void iconClose(GuiGraphics g, int cx, int cy, int c) {
		float x = cx + 0.5f, y = cy + 0.5f;
		line(g, x - 3f, y - 3f, x + 3f, y + 3f, 1.3f, c);
		line(g, x - 3f, y + 3f, x + 3f, y - 3f, 1.3f, c);
	}

	public static void iconGrid(GuiGraphics g, int cx, int cy, int c) {
		float x = cx + 0.5f, y = cy + 0.5f;
		for (int i = -1; i <= 1; i += 2) {
			for (int j = -1; j <= 1; j += 2) {
				rounded(g, x + i * 2.2f - 1.6f, y + j * 2.2f - 1.6f, x + i * 2.2f + 1.6f, y + j * 2.2f + 1.6f, 0.7f, c);
			}
		}
	}

	public static void iconList(GuiGraphics g, int cx, int cy, int c) {
		float x = cx + 0.5f, y = cy + 0.5f;
		for (int j = -1; j <= 1; j++) {
			circle(g, x - 3.6f, y + j * 3f, 0.9f, c);
			line(g, x - 1.5f, y + j * 3f, x + 4.2f, y + j * 3f, 1.3f, c);
		}
	}

	public static void iconSort(GuiGraphics g, int cx, int cy, int c) {
		float x = cx + 0.5f, y = cy + 0.5f;
		line(g, x - 2.2f, y - 4f, x - 2.2f, y + 4f, 1.2f, c);
		line(g, x - 2.2f, y - 4f, x - 4.2f, y - 1.8f, 1.2f, c);
		line(g, x - 2.2f, y - 4f, x - 0.2f, y - 1.8f, 1.2f, c);
		line(g, x + 2.2f, y - 4f, x + 2.2f, y + 4f, 1.2f, c);
		line(g, x + 2.2f, y + 4f, x + 0.2f, y + 1.8f, 1.2f, c);
		line(g, x + 2.2f, y + 4f, x + 4.2f, y + 1.8f, 1.2f, c);
	}

	public static void iconPencil(GuiGraphics g, int cx, int cy, int c) {
		float x = cx + 0.5f, y = cy + 0.5f;
		line(g, x - 2.6f, y + 2.6f, x + 3f, y - 3f, 1.8f, c);
		circle(g, x - 3.4f, y + 3.4f, 0.8f, c);
	}

	public static void iconTrash(GuiGraphics g, int cx, int cy, int c) {
		float x = cx + 0.5f, y = cy + 0.5f;
		line(g, x - 4f, y - 3f, x + 4f, y - 3f, 1.2f, c);
		line(g, x - 1.2f, y - 4.4f, x + 1.2f, y - 4.4f, 1.2f, c);
		roundedBorder(g, x - 3f, y - 2.2f, x + 3f, y + 4.6f, 1f, 1.1f, c);
		line(g, x, y - 0.8f, x, y + 3f, 1f, c);
	}

	public static void iconEye(GuiGraphics g, int cx, int cy, int c, boolean open) {
		float x = cx + 0.5f, y = cy + 0.5f;
		roundedBorder(g, x - 4.6f, y - 2.6f, x + 4.6f, y + 2.6f, 2.6f, 1.1f, c);
		if (open) circle(g, x, y, 1.4f, c);
		else line(g, x - 4.4f, y + 3.4f, x + 4.4f, y - 3.4f, 1.2f, c);
	}

	public static void iconPlus(GuiGraphics g, int cx, int cy, int c) {
		float x = cx + 0.5f, y = cy + 0.5f;
		line(g, x - 3.6f, y, x + 3.6f, y, 1.3f, c);
		line(g, x, y - 3.6f, x, y + 3.6f, 1.3f, c);
	}

	public static void iconBack(GuiGraphics g, int cx, int cy, int c) {
		float x = cx + 0.5f, y = cy + 0.5f;
		line(g, x + 1.2f, y - 3.4f, x - 2f, y, 1.4f, c);
		line(g, x - 2f, y, x + 1.2f, y + 3.4f, 1.4f, c);
	}

	/** Palette: three overlapping colored dots (theme button). */
	public static void iconPalette(GuiGraphics g, int cx, int cy, int c1, int c2, int c3) {
		float x = cx + 0.5f, y = cy + 0.5f;
		circle(g, x - 2.2f, y + 1.4f, 2.4f, c1);
		circle(g, x + 2.2f, y + 1.4f, 2.4f, c2);
		circle(g, x, y - 2.2f, 2.4f, c3);
	}
}
