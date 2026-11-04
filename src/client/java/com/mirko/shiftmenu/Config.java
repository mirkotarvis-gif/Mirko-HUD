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
