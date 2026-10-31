package com.mirko.shiftmenu.util;

import com.mirko.shiftmenu.module.ModuleManager;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Small animated notifications in the bottom-right corner ("Zoom enabled", "Waypoint added"...). */
public final class Toasts {
	private static final long LIFE_MS = 2200;
	private static final List<Toast> TOASTS = new ArrayList<>();

	private record Toast(String title, String text, int color, long born) {}

	private Toasts() {}

	public static void show(String title, String text, int color) {
		if (!ModuleManager.SETTINGS.notifications.on()) return;
		// replace an existing toast with the same title instead of stacking duplicates
		TOASTS.removeIf(t -> t.title.equals(title));
		TOASTS.add(new Toast(title, text, color, System.currentTimeMillis()));
		while (TOASTS.size() > 5) TOASTS.remove(0);
	}

	public static void render(GuiGraphics g) {
		if (TOASTS.isEmpty()) return;
		long now = System.currentTimeMillis();
		int sw = g.guiWidth(), sh = g.guiHeight();
		int y = sh - 8;
		Iterator<Toast> it = TOASTS.iterator();
		List<Toast> alive = new ArrayList<>();
		while (it.hasNext()) {
			Toast t = it.next();
			if (now - t.born > LIFE_MS) it.remove();
			else alive.add(t);
		}
		for (int i = alive.size() - 1; i >= 0; i--) {
			Toast t = alive.get(i);
			long age = now - t.born;
			float in = Anim.easeOut(age / 220f);
			float out = age > LIFE_MS - 250 ? Anim.easeOut((LIFE_MS - age) / 250f) : 1f;
			float vis = Math.min(in, out);
			int w = Math.max(Draw.width(t.title), Draw.width(t.text)) + 22;
			int h = 26;
			int x = sw - 8 - Math.round(w * vis) + (int) ((1 - vis) * 8);
			y -= h;
			int bgA = Math.round(0xE6 * vis);
			Draw.rounded(g, x, y, x + w, y + h, 4, (bgA << 24) | 0x141418);
			Draw.rounded(g, x, y, x + 3, y + h, 1, Colors.withAlpha(t.color, Math.round(255 * vis)));
			int textA = Math.max(4, Math.round(255 * vis));
			Draw.text(g, t.title, x + 9, y + 4, Colors.withAlpha(0xFFFFFF, textA));
			Draw.text(g, t.text, x + 9, y + 14, Colors.withAlpha(t.color, textA));
			// life bar
			float life = 1f - age / (float) LIFE_MS;
			g.fill(x + 3, y + h - 1, x + 3 + Math.round((w - 3) * life), y + h, Colors.withAlpha(t.color, Math.round(0x88 * vis)));
			y -= 4;
		}
	}
}
