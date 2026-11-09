package de.einfabo.skintotems.totem;

/**
 * Nachbau von Vanilla {@code SkinTextureDownloader.processLegacySkin} auf ARGB-Arrays – nur für den
 * Fallback, in dem die Mod einen Skin selbst herunterlädt. Normale Skins kommen bereits
 * fertig verarbeitet aus Minecraft.
 */
public final class SkinProcessing {
	private SkinProcessing() {
	}

	/**
	 * @return 64x64-ARGB wie Vanilla ihn rendert, oder {@code null} bei falscher Größe
	 */
	public static int[] process(int[] argb, int width, int height) {
		if (width != 64 || (height != 32 && height != 64)) {
			return null;
		}
		boolean legacy = height == 32;
		int[] img = new int[64 * 64];
		System.arraycopy(argb, 0, img, 0, 64 * height);
		if (legacy) {
			copyRect(img, 4, 16, 16, 32, 4, 4, true);
			copyRect(img, 8, 16, 16, 32, 4, 4, true);
			copyRect(img, 0, 20, 24, 32, 4, 12, true);
			copyRect(img, 4, 20, 16, 32, 4, 12, true);
			copyRect(img, 8, 20, 8, 32, 4, 12, true);
			copyRect(img, 12, 20, 16, 32, 4, 12, true);
			copyRect(img, 44, 16, -8, 32, 4, 4, true);
			copyRect(img, 48, 16, -8, 32, 4, 4, true);
			copyRect(img, 40, 20, 0, 32, 4, 12, true);
			copyRect(img, 44, 20, -8, 32, 4, 12, true);
			copyRect(img, 48, 20, -16, 32, 4, 12, true);
			copyRect(img, 52, 20, -8, 32, 4, 12, true);
		}
		setNoAlpha(img, 0, 0, 32, 16);
		if (legacy) {
			notchTransparencyHack(img, 32, 0, 64, 32);
		}
		setNoAlpha(img, 0, 16, 64, 32);
		setNoAlpha(img, 16, 48, 48, 64);
		return img;
	}

	/** Wie {@code NativeImage.copyRect(x, y, offX, offY, w, h, mirrorX, false)} innerhalb eines Bildes. */
	private static void copyRect(int[] img, int x, int y, int offX, int offY, int w, int h, boolean mirrorX) {
		for (int dy = 0; dy < h; dy++) {
			for (int dx = 0; dx < w; dx++) {
				int targetX = mirrorX ? w - 1 - dx : dx;
				int pixel = img[(y + dy) * 64 + x + dx];
				img[(y + offY + dy) * 64 + x + offX + targetX] = pixel;
			}
		}
	}

	private static void setNoAlpha(int[] img, int x0, int y0, int x1, int y1) {
		for (int x = x0; x < x1; x++) {
			for (int y = y0; y < y1; y++) {
				img[y * 64 + x] |= 0xFF000000;
			}
		}
	}

	private static void notchTransparencyHack(int[] img, int x0, int y0, int x1, int y1) {
		for (int x = x0; x < x1; x++) {
			for (int y = y0; y < y1; y++) {
				if ((img[y * 64 + x] >>> 24) < 128) {
					return;
				}
			}
		}
		for (int x = x0; x < x1; x++) {
			for (int y = y0; y < y1; y++) {
				img[y * 64 + x] &= 0x00FFFFFF;
			}
		}
	}
}
