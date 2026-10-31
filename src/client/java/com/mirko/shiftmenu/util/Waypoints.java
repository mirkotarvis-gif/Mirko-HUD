package com.mirko.shiftmenu.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mirko.shiftmenu.module.ModuleManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

import java.util.ArrayList;
import java.util.List;

/** Saved waypoints (global, not per profile). Includes automatic death waypoints. */
public final class Waypoints {
	public static final int[] COLORS = {0xFF55FFFF, 0xFFFF5555, 0xFF55FF55, 0xFFFFFF55, 0xFFFFAA00, 0xFFAA55FF, 0xFFFF77C8, 0xFFFFFFFF};

	public static final class Waypoint {
		public String name;
		public int x, y, z;
		public int color;
		public boolean visible = true;
		public String dimension;

		public Waypoint(String name, int x, int y, int z, int color, String dimension) {
			this.name = name;
			this.x = x;
			this.y = y;
			this.z = z;
			this.color = color;
			this.dimension = dimension;
		}

		public double distanceTo(LocalPlayer p) {
			double dx = x + 0.5 - p.getX(), dy = y - p.getY(), dz = z + 0.5 - p.getZ();
			return Math.sqrt(dx * dx + dy * dy + dz * dz);
		}

		/** Angle (degrees, -180..180) of this waypoint relative to where the player looks. 0 = straight ahead. */
		public float relativeAngle(LocalPlayer p) {
			double dx = x + 0.5 - p.getX(), dz = z + 0.5 - p.getZ();
			float target = (float) Math.toDegrees(Math.atan2(-dx, dz));
			float rel = target - p.getYRot();
			rel %= 360f;
			if (rel > 180f) rel -= 360f;
			if (rel < -180f) rel += 360f;
			return rel;
		}

		/** Absolute compass heading (0 = north) towards the waypoint. */
		public float heading(LocalPlayer p) {
			double dx = x + 0.5 - p.getX(), dz = z + 0.5 - p.getZ();
			float h = (float) Math.toDegrees(Math.atan2(dx, -dz));
			return (h % 360f + 360f) % 360f;
		}

		public JsonObject toJson() {
			JsonObject o = new JsonObject();
			o.addProperty("name", name);
			o.addProperty("x", x);
			o.addProperty("y", y);
			o.addProperty("z", z);
			o.addProperty("color", color);
			o.addProperty("visible", visible);
			o.addProperty("dimension", dimension);
			return o;
		}

		public static Waypoint fromJson(JsonObject o) {
			Waypoint w = new Waypoint(o.get("name").getAsString(), o.get("x").getAsInt(), o.get("y").getAsInt(),
					o.get("z").getAsInt(), o.get("color").getAsInt(), o.has("dimension") ? o.get("dimension").getAsString() : "");
			if (o.has("visible")) w.visible = o.get("visible").getAsBoolean();
			return w;
		}
	}

	public static final List<Waypoint> LIST = new ArrayList<>();
	private static boolean wasDead;

	private Waypoints() {}

	public static String currentDimension() {
		Minecraft mc = Minecraft.getInstance();
		return mc.level == null ? "" : mc.level.dimension().toString();
	}

	public static String prettyDimension(String dim) {
		if (dim.contains("the_nether")) return "Nether";
		if (dim.contains("the_end")) return "End";
		if (dim.contains("overworld")) return "Overworld";
		return "Other";
	}

	public static Waypoint addHere(String name) {
		LocalPlayer p = Minecraft.getInstance().player;
		if (p == null) return null;
		Waypoint w = new Waypoint(name, (int) Math.floor(p.getX()), (int) Math.floor(p.getY()), (int) Math.floor(p.getZ()),
				COLORS[LIST.size() % COLORS.length], currentDimension());
		LIST.add(w);
		com.mirko.shiftmenu.Config.save();
		Toasts.show("Waypoint added", w.name + " (" + w.x + ", " + w.y + ", " + w.z + ")", w.color);
		return w;
	}

	/** Visible waypoints in the current dimension, nearest first. */
	public static List<Waypoint> visibleHere() {
		LocalPlayer p = Minecraft.getInstance().player;
		String dim = currentDimension();
		List<Waypoint> out = new ArrayList<>();
		for (Waypoint w : LIST) if (w.visible && w.dimension.equals(dim)) out.add(w);
		if (p != null) out.sort((a, b) -> Double.compare(a.distanceTo(p), b.distanceTo(p)));
		return out;
	}

	public static void tick(Minecraft mc) {
		LocalPlayer p = mc.player;
		if (p == null) {
			wasDead = false;
			return;
		}
		boolean dead = p.isDeadOrDying();
		if (dead && !wasDead && ModuleManager.WAYPOINTS.deathWaypoints.on()) {
			LIST.removeIf(w -> w.name.equals("Death") && ModuleManager.WAYPOINTS.replaceOldDeath.on());
			Waypoint w = new Waypoint("Death", (int) Math.floor(p.getX()), (int) Math.floor(p.getY()), (int) Math.floor(p.getZ()),
					0xFFFF5555, currentDimension());
			LIST.add(w);
			com.mirko.shiftmenu.Config.save();
		}
		wasDead = dead;
	}

	public static JsonArray toJson() {
		JsonArray a = new JsonArray();
		for (Waypoint w : LIST) a.add(w.toJson());
		return a;
	}

	public static void fromJson(JsonArray a) {
		LIST.clear();
		for (JsonElement e : a) {
			try {
				LIST.add(Waypoint.fromJson(e.getAsJsonObject()));
			} catch (Exception ignored) {
			}
		}
	}
}
