package de.einfabo.skintotems.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.Mth;
import org.slf4j.Logger;

/** Einstellungen in {@code config/skintotems.json}, änderbar im Einstellungsmenü. */
public final class SkinTotemsConfig {
	private static final Logger LOGGER = LogUtils.getLogger();
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("skintotems.json");

	public enum Style {
		/** Flaches Totem im Stil des Custom-Totem-Generators */
		TOTEM,
		/** 3D-Plushie */
		PLUSHIE
	}

	public static final int MIN_PLUSHIE_SIZE = 50;
	public static final int MAX_PLUSHIE_SIZE = 150;

	private static Style style = Style.TOTEM;
	// Wo das Skin-Totem gezeigt wird
	private static boolean showOthers = true;
	private static boolean showOwnHand = true;
	private static boolean showInventory = true;
	private static boolean showActivation = true;
	// Totem
	private static boolean totemHat = true;
	private static boolean totemOutline = true;
	// Plushie
	private static boolean plushieOuterLayer = true;
	private static int plushieSize = 100;
	/** Zählt jede Änderung hoch, damit zwischengespeicherte Inventar-Icons neu gezeichnet werden. */
	private static int revision;

	private SkinTotemsConfig() {
	}

	public static Style style() { return style; }
	public static boolean showOthers() { return showOthers; }
	public static boolean showOwnHand() { return showOwnHand; }
	public static boolean showInventory() { return showInventory; }
	public static boolean showActivation() { return showActivation; }
	public static boolean totemHat() { return totemHat; }
	public static boolean totemOutline() { return totemOutline; }
	public static boolean plushieOuterLayer() { return plushieOuterLayer; }
	public static int plushieSize() { return plushieSize; }
	public static int revision() { return revision; }

	public static void setStyle(Style value) { style = value; save(); }
	public static void setShowOthers(boolean value) { showOthers = value; save(); }
	public static void setShowOwnHand(boolean value) { showOwnHand = value; save(); }
	public static void setShowInventory(boolean value) { showInventory = value; save(); }
	public static void setShowActivation(boolean value) { showActivation = value; save(); }
	public static void setTotemHat(boolean value) { totemHat = value; save(); }
	public static void setTotemOutline(boolean value) { totemOutline = value; save(); }
	public static void setPlushieOuterLayer(boolean value) { plushieOuterLayer = value; save(); }
	public static void setPlushieSize(int value) { plushieSize = Mth.clamp(value, MIN_PLUSHIE_SIZE, MAX_PLUSHIE_SIZE); save(); }

	public static void load() {
		if (!Files.exists(FILE)) {
			save();
			return;
		}
		try (Reader reader = Files.newBufferedReader(FILE)) {
			JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
			if (json.has("style")) {
				style = Style.valueOf(json.get("style").getAsString().toUpperCase(Locale.ROOT));
			}
			showOthers = bool(json, "show_others", showOthers);
			showOwnHand = bool(json, "show_own_hand", showOwnHand);
			showInventory = bool(json, "show_inventory", showInventory);
			showActivation = bool(json, "show_activation", showActivation);
			totemHat = bool(json, "totem_hat", totemHat);
			totemOutline = bool(json, "totem_outline", totemOutline);
			plushieOuterLayer = bool(json, "plushie_outer_layer", plushieOuterLayer);
			if (json.has("plushie_size")) {
				plushieSize = Mth.clamp(json.get("plushie_size").getAsInt(), MIN_PLUSHIE_SIZE, MAX_PLUSHIE_SIZE);
			}
		} catch (IOException | RuntimeException e) {
			LOGGER.warn("[Skin Totems] Konnte {} nicht lesen, nutze Standardwerte", FILE, e);
		}
	}

	private static boolean bool(JsonObject json, String key, boolean fallback) {
		return json.has(key) ? json.get(key).getAsBoolean() : fallback;
	}

	private static void save() {
		revision++;
		JsonObject json = new JsonObject();
		json.addProperty("style", style.name().toLowerCase(Locale.ROOT));
		json.addProperty("show_others", showOthers);
		json.addProperty("show_own_hand", showOwnHand);
		json.addProperty("show_inventory", showInventory);
		json.addProperty("show_activation", showActivation);
		json.addProperty("totem_hat", totemHat);
		json.addProperty("totem_outline", totemOutline);
		json.addProperty("plushie_outer_layer", plushieOuterLayer);
		json.addProperty("plushie_size", plushieSize);
		try {
			Files.createDirectories(FILE.getParent());
			try (Writer writer = Files.newBufferedWriter(FILE)) {
				GSON.toJson(json, writer);
			}
		} catch (IOException e) {
			LOGGER.warn("[Skin Totems] Konnte {} nicht speichern", FILE, e);
		}
	}
}
