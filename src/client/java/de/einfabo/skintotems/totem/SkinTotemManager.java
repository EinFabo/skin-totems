package de.einfabo.skintotems.totem;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;
import de.einfabo.skintotems.SkinTotemsClient;
import de.einfabo.skintotems.config.SkinTotemsConfig;
import de.einfabo.skintotems.mixin.TextureManagerAccessor;
import de.einfabo.skintotems.render.ExtrudedMesh;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.HttpURLConnection;
import java.net.Proxy;
import java.net.URI;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import net.minecraft.util.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.ClientAvatarEntity;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

/**
 * Erzeugt und cached pro Skin-Textur ein Totem (Textur + Mesh).
 *
 * <p>Skin-Quelle: Heruntergeladene Skins hält Vanilla als {@link DynamicTexture} samt Pixeln im
 * Speicher – die lesen wir direkt aus. Default-Skins (Steve, Alex …) kommen aus dem
 * ResourceManager. Alles läuft auf dem Render-Thread; die Arbeit ist winzig (64x64 lesen,
 * 32x32 erzeugen) und passiert pro Skin nur einmal.
 */
public final class SkinTotemManager {
	private static final Logger LOGGER = LogUtils.getLogger();
	public static final Identifier OUTLINE = Identifier.fromNamespaceAndPath(SkinTotemsClient.MOD_ID, "textures/totem/outline.png");
	private static final TotemTexture FAILED = new TotemTexture(null, null, false, null);

	private static final long DOWNLOAD_FALLBACK_DELAY_MS = 3000;
	/** Nur zum Testen: Skins immer selbst herunterladen (simuliert Clients mit eigenem Skin-System). */
	private static final boolean FORCE_DOWNLOAD = Boolean.getBoolean("skintotems.debug.forceDownload");

	private static final Map<Identifier, TotemTexture> CACHE = new HashMap<>();
	// Fallback-Download (nur Render-Thread)
	private static final Map<Identifier, Long> FIRST_MISSING = new HashMap<>();
	private static final Set<Identifier> DOWNLOADING = new HashSet<>();
	private static final Map<Identifier, int[]> DOWNLOADED = new HashMap<>();
	private static int generation;
	private static int @Nullable [] outline;
	private static int outlineWidth;
	private static int outlineHeight;
	private static boolean outlineLoaded;

	/**
	 * @param textureId erzeugte Totem-Textur
	 * @param skinTexture Kopie des Skins (fürs Plushie) – unabhängig davon, wie der Client Skins lädt
	 * Alle Felder sind nur beim internen FAILED-Marker null.
	 */
	public record TotemTexture(Identifier textureId, ExtrudedMesh mesh, boolean translucent, Identifier skinTexture) {
	}

	private SkinTotemManager() {
	}

	/** @return das Totem für diesen Spieler oder {@code null}, solange es (noch) keins gibt */
	public static @Nullable TotemTexture get(ClientAvatarEntity avatar) {
		ClientAsset.Texture body = avatar.getSkin().body();
		Identifier key = body.texturePath();
		TotemTexture cached = CACHE.get(key);
		if (cached != null) {
			return cached == FAILED ? null : cached;
		}

		int[] skin;
		try {
			skin = readSkin(body);
		} catch (Exception e) {
			LOGGER.warn("[Skin Totems] Konnte Skin {} nicht lesen", key, e);
			CACHE.put(key, FAILED);
			return null;
		}
		if (skin == null) {
			return null; // Skin wird noch heruntergeladen – nächstes Frame nochmal
		}

		TotemTexture totem = createTotem(key, skin);
		CACHE.put(key, totem == null ? FAILED : totem);
		return totem;
	}

	/** @return ARGB 64x64 (oder andere Größe, Breite = Höhe), {@code null} = noch nicht verfügbar */
	private static int @Nullable [] readSkin(ClientAsset.Texture body) throws IOException {
		Minecraft mc = Minecraft.getInstance();
		Identifier path = body.texturePath();
		AbstractTexture texture = ((TextureManagerAccessor) mc.getTextureManager()).skintotems$getByPath().get(path);
		if (texture instanceof DynamicTexture dynamic && !(FORCE_DOWNLOAD && body instanceof ClientAsset.DownloadedTexture)) {
			NativeImage pixels = dynamic.getPixels();
			if (pixels != null) {
				FIRST_MISSING.remove(path);
				return readPixels(pixels);
			}
		}
		if (body instanceof ClientAsset.DownloadedTexture downloaded) {
			int[] fetched = DOWNLOADED.get(path); // bleibt bis zum Disconnect, damit ein Reload nicht neu lädt
			if (fetched != null) {
				FIRST_MISSING.remove(path);
				return fetched;
			}
			// Fallback: Lädt ein Client (z. B. mit eigenem Skin-System) den Skin nicht als lesbare
			// DynamicTexture, holen wir ihn nach kurzer Wartezeit selbst von der Skin-URL.
			long now = Util.getMillis();
			long firstMissing = FIRST_MISSING.computeIfAbsent(path, k -> now);
			boolean unreadable = texture != null || FORCE_DOWNLOAD;
			if ((unreadable || now - firstMissing >= DOWNLOAD_FALLBACK_DELAY_MS) && DOWNLOADING.add(path)) {
				startDownload(path, downloaded.url());
			}
			return null;
		}
		Optional<Resource> resource = mc.getResourceManager().getResource(path);
		if (resource.isEmpty()) {
			throw new IOException("Skin-Ressource fehlt: " + path);
		}
		try (InputStream in = resource.get().open(); NativeImage image = NativeImage.read(NativeImage.Format.RGBA, in)) {
			return readPixels(image);
		}
	}

	private static void startDownload(Identifier path, String url) {
		Minecraft mc = Minecraft.getInstance();
		int startedGeneration = generation;
		CompletableFuture.supplyAsync(() -> fetchSkin(url, mc.getProxy()), Util.nonCriticalIoPool())
			.whenComplete((pixels, error) -> mc.execute(() -> {
				if (startedGeneration != generation) {
					return; // inzwischen geleert (Reload/Disconnect)
				}
				DOWNLOADING.remove(path);
				if (error != null || pixels == null) {
					LOGGER.warn("[Skin Totems] Skin {} konnte nicht geladen werden", url, error);
					CACHE.put(path, FAILED);
				} else {
					LOGGER.info("[Skin Totems] Skin selbst geladen: {}", url);
					DOWNLOADED.put(path, pixels);
				}
			}));
	}

	private static int @Nullable [] fetchSkin(String url, Proxy proxy) {
		URI uri = URI.create(url);
		String scheme = uri.getScheme();
		if (!"https".equalsIgnoreCase(scheme) && !"http".equalsIgnoreCase(scheme)) {
			return null;
		}
		HttpURLConnection connection = null;
		try {
			connection = (HttpURLConnection) uri.toURL().openConnection(proxy);
			connection.setConnectTimeout(10_000);
			connection.setReadTimeout(10_000);
			connection.connect();
			if (connection.getResponseCode() / 100 != 2) {
				return null;
			}
			byte[] bytes;
			try (InputStream in = connection.getInputStream()) {
				bytes = in.readAllBytes();
			}
			try (NativeImage image = NativeImage.read(NativeImage.Format.RGBA, new ByteArrayInputStream(bytes))) {
				return SkinProcessing.process(readPixels(image), image.getWidth(), image.getHeight());
			}
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		} finally {
			if (connection != null) {
				connection.disconnect();
			}
		}
	}

	private static int[] readPixels(NativeImage image) {
		int w = image.getWidth();
		int h = image.getHeight();
		int[] out = new int[w * h];
		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				out[y * w + x] = image.getPixel(x, y);
			}
		}
		return out;
	}

	private static @Nullable TotemTexture createTotem(Identifier key, int[] skin) {
		int size = (int) Math.round(Math.sqrt(skin.length));
		if (size * size != skin.length) {
			LOGGER.warn("[Skin Totems] Unerwartete Skin-Größe für {} ({} Pixel)", key, skin.length);
			return null;
		}
		loadOutline();
		int[] totemOutline = SkinTotemsConfig.totemOutline() ? outline : null;
		int[] totem = TotemImageGenerator.generate(skin, size, size, totemOutline, outlineWidth, outlineHeight, SkinTotemsConfig.totemHat());

		NativeImage image = new NativeImage(TotemImageGenerator.SIZE, TotemImageGenerator.SIZE, true);
		boolean translucent = false;
		for (int y = 0; y < TotemImageGenerator.SIZE; y++) {
			for (int x = 0; x < TotemImageGenerator.SIZE; x++) {
				int argb = totem[y * TotemImageGenerator.SIZE + x];
				int alpha = argb >>> 24;
				translucent |= alpha != 0 && alpha != 255;
				image.setPixel(x, y, argb);
			}
		}

		Identifier textureId = Identifier.fromNamespaceAndPath(SkinTotemsClient.MOD_ID, "totem/" + sanitize(key));
		Minecraft.getInstance().getTextureManager().register(textureId, new DynamicTexture(textureId::toString, image));

		NativeImage skinImage = new NativeImage(size, size, true);
		for (int y = 0; y < size; y++) {
			for (int x = 0; x < size; x++) {
				skinImage.setPixel(x, y, skin[y * size + x]);
			}
		}
		Identifier skinTexture = Identifier.fromNamespaceAndPath(SkinTotemsClient.MOD_ID, "skin/" + sanitize(key));
		Minecraft.getInstance().getTextureManager().register(skinTexture, new DynamicTexture(skinTexture::toString, skinImage));

		return new TotemTexture(textureId, ExtrudedMesh.build(totem, TotemImageGenerator.SIZE, TotemImageGenerator.SIZE), translucent, skinTexture);
	}

	private static String sanitize(Identifier key) {
		String raw = (key.getNamespace() + "/" + key.getPath()).toLowerCase(Locale.ROOT);
		StringBuilder sb = new StringBuilder(raw.length());
		for (char c : raw.toCharArray()) {
			sb.append(Identifier.validPathChar(c) ? c : '_');
		}
		return sb.toString();
	}

	private static void loadOutline() {
		if (outlineLoaded) {
			return;
		}
		outlineLoaded = true;
		outline = null;
		ResourceManager resources = Minecraft.getInstance().getResourceManager();
		Optional<Resource> resource = resources.getResource(OUTLINE);
		if (resource.isEmpty()) {
			LOGGER.warn("[Skin Totems] Keine Outline unter {} gefunden – Totems ohne Rand", OUTLINE);
			return;
		}
		try (InputStream in = resource.get().open(); NativeImage image = NativeImage.read(NativeImage.Format.RGBA, in)) {
			outline = readPixels(image);
			outlineWidth = image.getWidth();
			outlineHeight = image.getHeight();
		} catch (IOException e) {
			LOGGER.warn("[Skin Totems] Outline {} konnte nicht geladen werden", OUTLINE, e);
		}
	}

	/** Nach Resource-Reload: Totems neu erzeugen (andere Outline), geladene Skins behalten. */
	public static void onResourceReload() {
		releaseTotems();
		outlineLoaded = false;
		outline = null;
	}

	/** Nach geänderten Totem-Einstellungen (Hut, Rahmen): Totems neu erzeugen. */
	public static void regenerate() {
		releaseTotems();
	}

	/** Beim Verlassen eines Servers: alles freigeben. */
	public static void clearAll() {
		releaseTotems();
		FIRST_MISSING.clear();
		DOWNLOADING.clear();
		DOWNLOADED.clear();
		generation++;
	}

	private static void releaseTotems() {
		Minecraft mc = Minecraft.getInstance();
		for (TotemTexture totem : CACHE.values()) {
			if (totem != FAILED) {
				mc.getTextureManager().release(totem.textureId());
				mc.getTextureManager().release(totem.skinTexture());
			}
		}
		CACHE.clear();
	}
}
