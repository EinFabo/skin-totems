package de.einfabo.skintotems;

import com.mojang.blaze3d.platform.InputConstants;
import de.einfabo.skintotems.config.SkinTotemsConfig;
import de.einfabo.skintotems.config.SkinTotemsScreen;
import de.einfabo.skintotems.model.PlayerTotemItemModel;
import de.einfabo.skintotems.plushie.PlushieGeometry;
import de.einfabo.skintotems.totem.SkinTotemManager;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.KeyMapping;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.minecraft.client.renderer.item.ItemModels;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;

public final class SkinTotemsClient implements ClientModInitializer {
	public static final String MOD_ID = "skintotems";

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	@Override
	public void onInitializeClient() {
		SkinTotemsConfig.load();

		// Eigener Item-Modell-Typ, nutzbar in assets/<ns>/items/*.json als "skintotems:player_totem"
		ItemModels.ID_MAPPER.put(id("player_totem"), PlayerTotemItemModel.Unbaked.MAP_CODEC);

		// Neue Outline/Plushie-Form aus einem Texture Pack -> alles neu erzeugen
		ResourceLoader.get(PackType.CLIENT_RESOURCES).registerReloadListener(id("totem_cache"), new ResourceManagerReloadListener() {
			@Override
			public void onResourceManagerReload(ResourceManager resourceManager) {
				SkinTotemManager.onResourceReload();
				PlushieGeometry.reload(resourceManager);
			}
		});

		// Einstellungsmenü per Tastenkürzel (standardmäßig nicht belegt; sonst über Mod Menu)
		KeyMapping openSettings = KeyMappingHelper.registerKeyMapping(
			new KeyMapping("key.skintotems.open_settings", InputConstants.UNKNOWN.getValue(), KeyMapping.Category.MISC));
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (openSettings.consumeClick()) {
				client.gui.setScreen(new SkinTotemsScreen(client.gui.screen()));
			}
		});

		// Texturen fremder Spieler nicht ewig im Speicher halten
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(SkinTotemManager::clearAll));
	}
}
