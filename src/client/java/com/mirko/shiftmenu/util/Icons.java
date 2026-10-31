package com.mirko.shiftmenu.util;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.HashMap;
import java.util.Map;

/** The item shown as each module's icon on its card. */
public final class Icons {
	private static final Map<String, ItemStack> CACHE = new HashMap<>();

	private Icons() {}

	public static ItemStack forModule(String id) {
		return CACHE.computeIfAbsent(id, k -> new ItemStack(item(k)));
	}

	private static Item item(String id) {
		if (id.startsWith("customtext")) return Items.OAK_SIGN;
		return switch (id) {
			case "fps" -> Items.REDSTONE;
			case "cps" -> Items.STONE_BUTTON;
			case "keystrokes" -> Items.NOTE_BLOCK;
			case "togglesprint" -> Items.FEATHER;
			case "crosshair" -> Items.TARGET;
			case "armor" -> Items.DIAMOND_CHESTPLATE;
			case "potions" -> Items.POTION;
			case "reach" -> Items.TRIDENT;
			case "combo" -> Items.BLAZE_POWDER;
			case "coordinates" -> Items.MAP;
			case "ping" -> Items.ENDER_EYE;
			case "direction" -> Items.RECOVERY_COMPASS;
			case "clock", "stopwatch" -> Items.CLOCK;
			case "memory" -> Items.KNOWLEDGE_BOOK;
			case "speed" -> Items.SUGAR;
			case "server" -> Items.NAME_TAG;
			case "session" -> Items.HOPPER;
			case "players" -> Items.PLAYER_HEAD;
			case "watermark" -> Items.PAINTING;
			case "targetinfo" -> Items.ZOMBIE_HEAD;
			case "fpsgraph" -> Items.COMPARATOR;
			case "compass" -> Items.COMPASS;
			case "zoom" -> Items.SPYGLASS;
			case "fullbright" -> Items.GLOWSTONE_DUST;
			case "vanillahud" -> Items.ITEM_FRAME;
			case "health" -> Items.POPPY;
			case "food" -> Items.COOKED_BEEF;
			case "saturation" -> Items.GOLDEN_CARROT;
			case "armorpoints" -> Items.IRON_CHESTPLATE;
			case "xp" -> Items.EXPERIENCE_BOTTLE;
			case "air" -> Items.TURTLE_HELMET;
			case "helditem" -> Items.DIAMOND_SWORD;
			case "helddurability" -> Items.ANVIL;
			case "itemcounter" -> Items.CHEST;
			case "arrows" -> Items.ARROW;
			case "totems" -> Items.TOTEM_OF_UNDYING;
			case "pearls" -> Items.ENDER_PEARL;
			case "gapples" -> Items.GOLDEN_APPLE;
			case "blocks" -> Items.COBBLESTONE;
			case "potioncount" -> Items.SPLASH_POTION;
			case "rotation" -> Items.ARMOR_STAND;
			case "nethercoords" -> Items.NETHERRACK;
			case "chunk" -> Items.GRASS_BLOCK;
			case "velocity" -> Items.SLIME_BALL;
			case "falldistance" -> Items.HAY_BLOCK;
			case "gamemode" -> Items.COMMAND_BLOCK;
			case "weather" -> Items.WATER_BUCKET;
			case "date" -> Items.PAPER;
			case "targetdistance" -> Items.BOW;
			case "hits" -> Items.IRON_SWORD;
			case "maxcombo" -> Items.NETHERITE_SWORD;
			case "accuracy" -> Items.CROSSBOW;
			case "system" -> Items.REDSTONE_BLOCK;
			case "entities" -> Items.EGG;
			case "nearbyplayers" -> Items.SKELETON_SKULL;
			case "waypoints" -> Items.BEACON;
			case "general" -> Items.COMPARATOR;
			default -> Items.BOOK;
		};
	}
}
