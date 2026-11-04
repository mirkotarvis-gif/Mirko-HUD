package com.mirko.shiftmenu;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mirko.shiftmenu.module.HudModule;
import com.mirko.shiftmenu.module.ModuleManager;
import com.mirko.shiftmenu.module.setting.InfoSetting;
import com.mirko.shiftmenu.module.setting.Setting;
import com.mirko.shiftmenu.util.Waypoints;
import net.fabricmc.loader.api.FabricLoader;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Saves everything to config/shiftmenu.json. Profiles (whole HUD setups) are stored as
 * separate files in config/shiftmenu/profiles/ and can be switched from the mod menu.
 */
public final class Config {
	private static final int VERSION = 4;
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	public static String activeProfile = "Default";

	private Config() {}

	private static Path mainFile() {
		return FabricLoader.getInstance().getConfigDir().resolve("shiftmenu.json");
	}

	private static Path profileDir() {
		return FabricLoader.getInstance().getConfigDir().resolve("shiftmenu").resolve("profiles");
	}

	private static Path profileFile(String name) {
		return profileDir().resolve(safe(name) + ".json");
	}

	private static String safe(String name) {
		return name.replaceAll("[^A-Za-z0-9 _\\-]", "_").trim();
	}

	// ------------------------------------------------------------------ module (de)serialization

	public static JsonObject modulesJson() {
		JsonObject mods = new JsonObject();
		for (HudModule m : ModuleManager.ALL) {
			JsonObject o = new JsonObject();
			o.addProperty("enabled", m.isEnabled());
			o.addProperty("x", m.rx);
			o.addProperty("y", m.ry);
			JsonObject s = new JsonObject();
			for (Setting<?> setting : m.settings) {
				if (setting instanceof InfoSetting) continue;
				s.add(setting.key(), setting.toJson());
			}
			o.add("settings", s);
			mods.add(m.id, o);
		}
		return mods;
	}

	public static void applyModules(JsonObject mods, boolean includeGeneral) {
		for (HudModule m : ModuleManager.ALL) {
			if (m == ModuleManager.SETTINGS && !includeGeneral) continue;
			if (!mods.has(m.id)) continue;
			JsonObject o = mods.getAsJsonObject(m.id);
			if (o.has("enabled")) m.setEnabled(o.get("enabled").getAsBoolean());
			if (o.has("x")) m.rx = o.get("x").getAsFloat();
			if (o.has("y")) m.ry = o.get("y").getAsFloat();
			if (o.has("settings")) {
				JsonObject s = o.getAsJsonObject("settings");
				for (Setting<?> setting : m.settings) {
					JsonElement e = s.get(setting.key());
					if (e == null) continue;
					try {
						setting.fromJson(e);
					} catch (Exception ignored) {
					}
				}
			}
		}
	}

	// ------------------------------------------------------------------ main file

	public static void load() {
		Path p = mainFile();
		if (!Files.exists(p)) {
			save();
			saveProfile(activeProfile);
			return;
		}
		try (Reader r = Files.newBufferedReader(p, StandardCharsets.UTF_8)) {
			JsonObject root = JsonParser.parseReader(r).getAsJsonObject();
			int version = root.has("version") ? root.get("version").getAsInt() : 1;
			if (root.has("profile")) activeProfile = root.get("profile").getAsString();
			if (root.has("modules")) applyModules(root.getAsJsonObject("modules"), true);
			if (root.has("waypoints")) Waypoints.fromJson(root.getAsJsonArray("waypoints"));
			if (version < 4) {
				// v1.4: new default look (HD rendering, Inter menu font, Mirko Aqua theme)
				ModuleManager.SETTINGS.menuFont.reset();
				ModuleManager.SETTINGS.theme.reset();
				ModuleManager.SETTINGS.appliedTheme = "";
				ModuleManager.SETTINGS.applyThemeIfChanged();
			}
			if (version < 3) {
				// v1.3 changed the default menu key to Right Shift and opening to instant press
				ModuleManager.SETTINGS.menuKey.reset();
				ModuleManager.SETTINGS.openMode.reset();
			}
			if (version < VERSION) save();
		} catch (Exception e) {
			System.err.println("[ShiftMenu] Failed to read config, using defaults: " + e);
		}
		if (!Files.exists(profileFile(activeProfile))) saveProfile(activeProfile);
	}

	public static void save() {
		JsonObject root = new JsonObject();
		root.addProperty("version", VERSION);
		root.addProperty("profile", activeProfile);
		root.add("modules", modulesJson());
		root.add("waypoints", Waypoints.toJson());
		write(mainFile(), root);
	}

	private static void write(Path path, JsonObject json) {
		try {
			Files.createDirectories(path.getParent());
			try (Writer w = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
				GSON.toJson(json, w);
			}
		} catch (Exception e) {
			System.err.println("[ShiftMenu] Failed to save " + path + ": " + e);
		}
	}

	// ------------------------------------------------------------------ profiles

	public static List<String> profiles() {
		List<String> out = new ArrayList<>();
		try {
			if (Files.isDirectory(profileDir())) {
				try (DirectoryStream<Path> ds = Files.newDirectoryStream(profileDir(), "*.json")) {
					for (Path f : ds) {
						String n = f.getFileName().toString();
						out.add(n.substring(0, n.length() - 5));
					}
				}
			}
		} catch (Exception ignored) {
		}
		if (!out.contains(activeProfile)) out.add(activeProfile);
		out.sort(String.CASE_INSENSITIVE_ORDER);
		return out;
	}

	/** Writes the current HUD setup (modules, not the global Settings page) into a profile file. */
	public static void saveProfile(String name) {
		JsonObject root = new JsonObject();
		root.addProperty("name", name);
		root.add("modules", modulesJson());
		write(profileFile(name), root);
	}

	public static void switchProfile(String name) {
		if (name.equals(activeProfile)) return;
		saveProfile(activeProfile);
		Path f = profileFile(name);
		if (Files.exists(f)) {
			try (Reader r = Files.newBufferedReader(f, StandardCharsets.UTF_8)) {
				JsonObject root = JsonParser.parseReader(r).getAsJsonObject();
				if (root.has("modules")) applyModules(root.getAsJsonObject("modules"), false);
			} catch (Exception e) {
				System.err.println("[ShiftMenu] Failed to load profile " + name + ": " + e);
			}
		}
		activeProfile = name;
		save();
	}

	public static boolean createProfile(String name) {
		name = safe(name);
		if (name.isEmpty() || Files.exists(profileFile(name))) return false;
		saveProfile(activeProfile);
		activeProfile = name;
		saveProfile(name);
		save();
		return true;
	}

	public static void deleteProfile(String name) {
		if (name.equals(activeProfile)) return;
		try {
			Files.deleteIfExists(profileFile(name));
		} catch (Exception ignored) {
		}
	}
}
