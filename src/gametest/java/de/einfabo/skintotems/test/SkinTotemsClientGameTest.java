package de.einfabo.skintotems.test;

import de.einfabo.skintotems.config.SkinTotemsConfig;
import de.einfabo.skintotems.config.SkinTotemsScreen;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Startet einen echten Client und fotografiert die wichtigen Fälle. Screenshots landen in
 * build/run/clientGameTest/screenshots.
 */
public class SkinTotemsClientGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		// Stumm, falls options.txt zurückgesetzt wurde
		context.runOnClient(mc -> mc.options.getSoundSourceOptionInstance(SoundSource.MASTER).set(0.0));
		if (Boolean.getBoolean("skintotems.test.showcase")) {
			runShowcase(context);
			return;
		}
		installTestPacks(context);

		try (TestSingleplayerContext sp = context.worldBuilder().create()) {
			sp.getConnection().waitForChunksRender();
			TestServerContext server = sp.getServer();
			server.runCommand("time set noon");
			server.runCommand("gamerule advance_time false");
			server.runCommand("gamerule advance_weather false");
			server.runCommand("weather clear");
			server.runCommand("tp @p 0.5 -60 0.5 0 0");

			// Eigener Spieler (Default-Skin im Dev-Client): Totem in beiden Händen
			server.runCommand("item replace entity @p weapon.mainhand with totem_of_undying");
			server.runCommand("item replace entity @p weapon.offhand with totem_of_undying");
			// Mannequin mit EinFabos echtem Skin und eins mit Notch, beide schauen zum Spieler
			server.runCommand("summon mannequin 0.5 -60 3.5 {profile:{name:\"EinFabo\"},Rotation:[180f,0f],immovable:1b}");
			server.runCommand("summon mannequin 2.5 -60 4.5 {profile:{name:\"Notch\"},Rotation:[200f,0f],immovable:1b}");
			// Dünne Arme (Alex-Modell)
			server.runCommand("summon mannequin -1.5 -60 5.5 {profile:{texture:\"minecraft:entity/player/slim/alex\",model:\"slim\"},Rotation:[160f,0f],immovable:1b}");
			server.runCommand("item replace entity @e[type=mannequin] weapon.mainhand with totem_of_undying");
			server.runCommand("item replace entity @e[type=mannequin] weapon.offhand with totem_of_undying");
			// Gegenprobe: gedropptes Totem und Rüstungsständer zeigen das normale Totem
			server.runCommand("summon item -0.3 -59.3 1.8 {Item:{id:\"totem_of_undying\",count:1},NoGravity:1b,PickupDelay:32767,Age:-32768}");
			server.runCommand("summon armor_stand -1.5 -60 4 {Rotation:[160f,0f],ShowArms:1b,NoGravity:1b,equipment:{mainhand:{id:\"totem_of_undying\",count:1}}}");
			context.waitTicks(100); // Mannequin-Profile werden übers Netz aufgelöst

			// Plushie-Stil
			context.runOnClient(mc -> SkinTotemsConfig.setStyle(SkinTotemsConfig.Style.PLUSHIE));
			shootAll(context, server, sp, "p_plushie");
			context.setScreen(() -> new InventoryScreen(sp.getConnection().getClientPlayer()));
			context.waitTicks(5);
			context.takeScreenshot("p_plushie_inventory");
			context.setScreen(() -> null);
			context.runOnClient(mc -> mc.gameRenderer.displayItemActivation(new ItemStack(Items.TOTEM_OF_UNDYING)));
			context.waitTicks(12);
			context.takeScreenshot("p_plushie_activation");
			context.waitTicks(40);
			// Achievement-Toast mit Totem-Icon (GUI ohne Halter) -> eigenes Plushie
			server.runCommand("advancement grant @p only minecraft:adventure/totem_of_undying");
			context.waitTicks(15);
			context.takeScreenshot("p_advancement_toast");
			context.waitTicks(120);

			// Einstellungen: andere Spieler aus -> Mannequins halten normale Totems
			context.runOnClient(mc -> SkinTotemsConfig.setShowOthers(false));
			server.runCommand("tp @p 0.5 -60 0.5 0 0");
			context.waitTicks(5);
			context.takeScreenshot("p_others_off");
			context.runOnClient(mc -> SkinTotemsConfig.setShowOthers(true));

			// Einstellungsmenü (GUI-Skalierung 1, damit die Vorschau Platz hat)
			context.runOnClient(mc -> { mc.options.guiScale().set(1); mc.resizeGui(); });
			context.setScreen(() -> new SkinTotemsScreen(null));
			context.waitTicks(10);
			context.takeScreenshot("p_settings_plushie");
			context.runOnClient(mc -> { SkinTotemsConfig.setPlushieSize(150); SkinTotemsConfig.setPlushieOuterLayer(false); });
			context.setScreen(() -> new SkinTotemsScreen(null));
			context.waitTicks(10);
			context.takeScreenshot("p_settings_big_no_layer");
			context.runOnClient(mc -> {
				SkinTotemsConfig.setPlushieSize(100);
				SkinTotemsConfig.setPlushieOuterLayer(true);
				SkinTotemsConfig.setStyle(SkinTotemsConfig.Style.TOTEM);
				SkinTotemsConfig.setTotemOutline(false);
				de.einfabo.skintotems.totem.SkinTotemManager.regenerate();
			});
			context.setScreen(() -> new SkinTotemsScreen(null));
			context.waitTicks(10);
			context.takeScreenshot("p_settings_totem_no_outline");
			context.runOnClient(mc -> {
				SkinTotemsConfig.setTotemOutline(true);
				de.einfabo.skintotems.totem.SkinTotemManager.regenerate();
			});
			context.setScreen(() -> null);
			context.runOnClient(mc -> { mc.options.guiScale().set(0); mc.resizeGui(); });
			context.waitTicks(5);

			context.runOnClient(mc -> SkinTotemsConfig.setStyle(SkinTotemsConfig.Style.TOTEM));
			if (Boolean.getBoolean("skintotems.test.plushieOnly")) {
				return;
			}

			shootAll(context, server, sp, "a_standard");

			context.setScreen(() -> new InventoryScreen(sp.getConnection().getClientPlayer()));
			context.waitTicks(5);
			context.takeScreenshot("a_standard_inventory");
			context.setScreen(() -> null);

			context.runOnClient(mc -> mc.gameRenderer.displayItemActivation(new ItemStack(Items.TOTEM_OF_UNDYING)));
			context.waitTicks(12);
			context.takeScreenshot("a_standard_activation");
			context.waitTicks(40);

			// Pack verkleinert das Totem -> Skin-Totem muss mitschrumpfen
			selectPacks(context, "file/small_totem");
			shootAll(context, server, sp, "b_small_pack");

			// Pack ersetzt die Item-Definition -> überall wieder Vanilla
			selectPacks(context, "file/vanilla_totem");
			shootAll(context, server, sp, "c_vanilla_items_pack");

			// Fabis Weihnachts-Overlay (ersetzt nur die Textur) -> Skin-Totem gewinnt, Rüstungsständer zeigt Weihnachten
			// Fabis Pack liegt nicht im öffentlichen Repo -> nur testen, wenn vorhanden
			if (SkinTotemsClientGameTest.class.getResource("/testpacks/weihnachten.zip") != null) {
				selectPacks(context, "file/weihnachten.zip");
				shootAll(context, server, sp, "d_weihnachten_pack");
			}

			selectPacks(context);
		}
	}

	// ---------------------------------------------------------------------------------------------
	// Galerie-Bilder für Modrinth (1920x1080): ./gradlew runClientGameTest -Pshowcase
	// ---------------------------------------------------------------------------------------------
	private static final int W = 1920;
	private static final int H = 1080;

	private static void shot(ClientGameTestContext context, String name) {
		context.runOnClient(mc -> mc.gui.hud.getChat().clearMessages(false));
		context.waitTicks(2);
		context.takeScreenshot(net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions.of(name).disableCounterPrefix());
	}

	private static void runShowcase(ClientGameTestContext context) {
		context.getInput().resizeWindow(W, H);
		try (TestSingleplayerContext sp = context.worldBuilder().create()) {
			sp.getConnection().waitForChunksRender();
			TestServerContext server = sp.getServer();
			server.runCommand("time set 1500");
			server.runCommand("gamerule advance_time false");
			server.runCommand("gamerule advance_weather false");
			server.runCommand("weather clear");
			server.runCommand("gamerule send_command_feedback false");

			// Bühne: Hecke mit Blumen
			server.runCommand("fill -8 -60 8 9 -59 8 minecraft:oak_leaves[persistent=true]");
			server.runCommand("fill -8 -58 8 9 -58 8 minecraft:azalea_leaves[persistent=true]");
			String[][] flowers = {{"-7", "7", "poppy"}, {"-5", "7", "dandelion"}, {"-2", "7", "cornflower"}, {"3", "7", "oxeye_daisy"},
				{"6", "7", "allium"}, {"8", "7", "poppy"}, {"-1", "7", "azure_bluet"}, {"1", "7", "lily_of_the_valley"}, {"4", "7", "pink_tulip"},
				{"-3", "7", "orange_tulip"}, {"-6", "7", "cornflower"}, {"7", "7", "dandelion"}};
			for (String[] f : flowers) {
				server.runCommand("setblock " + f[0] + " -60 " + f[1] + " minecraft:" + f[2]);
			}

			// Fünf Mannequins mit verschiedenen Skins (alle lokal, keine Netz-Abfrage)
			String[][] lineup = {
				{"-3.5", "minecraft:entity/player/wide/steve", "wide", "175"},
				{"-1.5", "minecraft:entity/player/slim/noor", "slim", "178"},
				{"0.5", "skintotems_test:entity/einfabo", "slim", "180"},
				{"2.5", "minecraft:entity/player/wide/efe", "wide", "182"},
				{"4.5", "minecraft:entity/player/slim/alex", "slim", "185"}};
			for (String[] m : lineup) {
				server.runCommand("summon mannequin " + m[0] + " -60 5.5 {profile:{texture:\"" + m[1] + "\",model:\"" + m[2]
					+ "\"},Rotation:[" + m[3] + "f,0f],immovable:1b,hide_description:1b}");
			}
			server.runCommand("item replace entity @e[type=mannequin] weapon.mainhand with totem_of_undying");
			server.runCommand("item replace entity @e[type=mannequin] weapon.offhand with totem_of_undying");
			server.runCommand("item replace entity @p weapon.mainhand with totem_of_undying");
			server.runCommand("item replace entity @p weapon.offhand with totem_of_undying");
			context.waitTicks(40);

			for (SkinTotemsConfig.Style style : SkinTotemsConfig.Style.values()) {
				String tag = style.name().toLowerCase(java.util.Locale.ROOT);
				context.runOnClient(mc -> SkinTotemsConfig.setStyle(style));

				// Gruppenbild (ohne HUD, ohne eigene Hände)
				server.runCommand("gamemode spectator @p");
				server.runCommand("tp @p 0.5 -60 1.2 0 12");
				context.runOnClient(mc -> { if (!mc.gui.hud.isHidden()) mc.gui.hud.toggle(); mc.options.fov().set(60); });
				context.waitTicks(20);
				shot(context, tag + "_group");

				// Erste Person mit HUD
				server.runCommand("gamemode creative @p");
				server.runCommand("tp @p 0.5 -60 2.0 0 8");
				context.runOnClient(mc -> { if (mc.gui.hud.isHidden()) mc.gui.hud.toggle(); mc.options.fov().set(70); });
				context.waitTicks(20);
				shot(context, tag + "_first_person");

				context.runOnClient(mc -> mc.gameRenderer.displayItemActivation(new ItemStack(Items.TOTEM_OF_UNDYING)));
				context.waitTicks(12);
				shot(context, tag + "_activation");
				context.waitTicks(40);
			}

			context.runOnClient(mc -> SkinTotemsConfig.setStyle(SkinTotemsConfig.Style.PLUSHIE));
			server.runCommand("gamemode survival @p");
			server.runCommand("tp @p 0.5 -60 2.0 0 8");
			server.runCommand("give @p totem_of_undying 3");
			context.waitTicks(5);
			context.setScreen(() -> new InventoryScreen(sp.getConnection().getClientPlayer()));
			context.waitTicks(10);
			shot(context, "plushie_inventory");
			context.setScreen(() -> null);

			server.runCommand("advancement grant @p only minecraft:adventure/totem_of_undying");
			context.waitTicks(15);
			shot(context, "plushie_advancement");
			context.waitTicks(120);

			context.runOnClient(mc -> { mc.options.guiScale().set(3); mc.resizeGui(); });
			context.setScreen(() -> new SkinTotemsScreen(null));
			context.waitTicks(10);
			shot(context, "settings");
			context.setScreen(() -> null);
			context.runOnClient(mc -> { mc.options.guiScale().set(0); mc.resizeGui(); });
		}
	}

	private static void shootAll(ClientGameTestContext context, TestServerContext server, TestSingleplayerContext sp, String name) {
		server.runCommand("tp @p 0.5 -60 0.5 0 0");
		context.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
		context.waitTicks(10);
		context.takeScreenshot(name + "_1_front");

		// Seitlich neben die Mannequins: Hand-Items zeigen ihre Fläche zur Seite
		server.runCommand("tp @p 2.3 -60 3.6 90 10");
		context.waitTicks(10);
		context.takeScreenshot(name + "_2_side");

		// Nahaufnahme: Hände von EinFabos Mannequin, gezoomt
		server.runCommand("tp @p 2.2 -60 3.3 90 25");
		context.runOnClient(mc -> mc.options.fov().set(30));
		context.waitTicks(10);
		context.takeScreenshot(name + "_4_closeup");
		context.runOnClient(mc -> mc.options.fov().set(70));

		// Frontal-Nahaufnahme von EinFabos Mannequin
		server.runCommand("tp @p 0.5 -60 1.6 0 15");
		context.runOnClient(mc -> mc.options.fov().set(30));
		context.waitTicks(10);
		context.takeScreenshot(name + "_5_front_closeup");
		context.runOnClient(mc -> mc.options.fov().set(70));

		server.runCommand("tp @p 0.5 -60 0.5 0 0");
		context.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
		context.waitTicks(10);
		context.takeScreenshot(name + "_3_third_person");
		context.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
	}

	private static void selectPacks(ClientGameTestContext context, String... packIds) {
		java.util.concurrent.CompletableFuture<Void> reload = context.computeOnClient(mc -> {
			PackRepository repo = mc.getResourcePackRepository();
			repo.reload();
			List<String> selected = new ArrayList<>();
			for (String id : repo.getSelectedIds()) {
				if (!id.startsWith("file/")) selected.add(id);
			}
			selected.addAll(List.of(packIds));
			repo.setSelected(selected);
			return mc.reloadResourcePacks();
		});
		context.waitFor(mc -> reload.isDone(), 1200);
		context.waitTicks(60); // Ladebildschirm blendet noch aus
	}

	/** Kopiert src/gametest/resources/testpacks/* nach resourcepacks/ im Test-Spielordner. */
	private static void installTestPacks(ClientGameTestContext context) {
		context.runOnClient(mc -> {
			Path target = mc.getResourcePackDirectory();
			try {
				Path source = Path.of(SkinTotemsClientGameTest.class.getResource("/testpacks").toURI());
				try (Stream<Path> files = Files.walk(source)) {
					for (Path file : (Iterable<Path>) files::iterator) {
						Path dest = target.resolve(source.relativize(file).toString());
						if (Files.isDirectory(file)) {
							Files.createDirectories(dest);
						} else {
							try (InputStream in = Files.newInputStream(file)) {
								Files.copy(in, dest, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
							}
						}
					}
				}
			} catch (IOException e) {
				throw new UncheckedIOException(e);
			} catch (URISyntaxException e) {
				throw new IllegalStateException(e);
			}
		});
	}
}
