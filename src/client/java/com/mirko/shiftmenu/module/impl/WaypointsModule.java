package com.mirko.shiftmenu.module.impl;

import com.mirko.shiftmenu.module.TextModule;
import com.mirko.shiftmenu.module.setting.BoolSetting;
import com.mirko.shiftmenu.module.setting.KeySetting;
import com.mirko.shiftmenu.module.setting.NumberSetting;
import com.mirko.shiftmenu.util.Game;
import com.mirko.shiftmenu.util.Keys;
import com.mirko.shiftmenu.util.Waypoints;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

import java.util.ArrayList;
import java.util.List;

/**
 * Lists your nearest waypoints with distance and a direction arrow.
 * Manage waypoints in the WAYPOINTS tab of the mod menu.
 */
public class WaypointsModule extends TextModule {
	private static final String[] ARROWS = {"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"};

	public final NumberSetting maxShown = add(new NumberSetting("Max Shown", 3, 1, 10, 1).in("Waypoints"));
	public final BoolSetting showDistance = add(new BoolSetting("Show Distance", true).in("Waypoints"));
	public final BoolSetting showArrow = add(new BoolSetting("Show Direction Arrow", true).in("Waypoints"));
	public final BoolSetting showCoords = add(new BoolSetting("Show Coordinates", false).in("Waypoints"));
	public final BoolSetting useColors = add(new BoolSetting("Use Waypoint Colors", true).in("Waypoints"));
	public final KeySetting addKey = add(new KeySetting("Add Waypoint Key", -1).in("Waypoints"));
	public final BoolSetting deathWaypoints = add(new BoolSetting("Auto Death Waypoint", true).in("Waypoints"));
	public final BoolSetting replaceOldDeath = add(new BoolSetting("Replace Old Death Waypoint", true).in("Waypoints").showIf(this.deathWaypoints::on));

	private List<Waypoints.Waypoint> shown = new ArrayList<>();

	public WaypointsModule() {
		super("waypoints", "Waypoints", Category.INFO, false, 0.99f, 0.5f, true, false);
	}

	@Override
	protected String defaultAlign() {
		return "Left";
	}

	@Override
	public void tick(Minecraft mc) {
		if (addKey.pressed(mc.screen == null && mc.player != null && Keys.isKeyDown(mc, addKey.get()))) {
			Waypoints.addHere("Waypoint " + (Waypoints.LIST.size() + 1));
		}
	}

	private String arrow(float rel) {
		int i = Math.round(((rel % 360f) + 360f) % 360f / 45f) % 8;
		return ARROWS[i];
	}

	@Override
	protected List<String> lines(boolean preview) {
		LocalPlayer p = Game.player();
		List<String> out = new ArrayList<>();
		shown = new ArrayList<>();
		if (p != null) {
			List<Waypoints.Waypoint> list = Waypoints.visibleHere();
			int max = maxShown.get().intValue();
			for (int i = 0; i < list.size() && i < max; i++) {
				Waypoints.Waypoint w = list.get(i);
				StringBuilder b = new StringBuilder();
				if (showArrow.on()) b.append(arrow(w.relativeAngle(p))).append(' ');
				b.append(w.name);
				if (showDistance.on()) b.append(" §7").append(Math.round(w.distanceTo(p))).append("m");
				if (showCoords.on()) b.append(" §8(").append(w.x).append(", ").append(w.y).append(", ").append(w.z).append(")");
				out.add(b.toString());
				shown.add(w);
			}
		}
		if (out.isEmpty() && preview) {
			out.add("↗ Home §7120m");
			out.add("← Base §7340m");
		}
		return out;
	}

	@Override
	protected int lineColor(int index, int base) {
		if (!useColors.on() || index >= shown.size()) return base;
		return shown.get(index).color;
	}
}
