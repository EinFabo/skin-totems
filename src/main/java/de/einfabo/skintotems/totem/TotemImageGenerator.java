package de.einfabo.skintotems.totem;

/**
 * Port von {@code generate_totem_image} aus EinFabo/Custom-Totem-Generator (app.py).
 *
 * <p>Arbeitet bewusst nur auf ARGB-int-Arrays (0xAARRGGBB), damit die Logik ohne Minecraft
 * testbar ist. Die Alpha-Mischung bildet Pillow exakt nach ({@code Image.paste} mit Maske und
 * {@code Image.alpha_composite}), damit das Ergebnis pixelgleich zum Original ist.
 */
public final class TotemImageGenerator {
	public static final int SIZE = 32;
	public static final int SKIN_SIZE = 64;

	/** Pixel, die aus dem auf 16x16 vergrößerten Kopf entfernt werden (x1, y1, x2, y2 inklusive). */
	private static final int[][] HEAD_CROPS = {
		{0, 0, 15, 0},
		{0, 1, 0, 1}, {1, 1, 1, 1}, {14, 1, 14, 1}, {15, 1, 15, 1},
		{0, 2, 0, 2}, {15, 2, 15, 2},
		{0, 15, 15, 15}
	};

	private TotemImageGenerator() {
	}

	/**
	 * @param skin    Skin-Pixel (ARGB), {@code skinWidth x skinHeight}; nicht-64x64-Skins werden per
	 *                Nearest-Neighbor auf 64x64 gebracht
	 * @param outline Outline-Pixel (ARGB) {@code outlineWidth x outlineHeight} oder {@code null}
	 * @param overlay Hut-Overlay über den Kopf legen
	 * @return Totem 32x32 (ARGB, zeilenweise)
	 */
	public static int[] generate(int[] skin, int skinWidth, int skinHeight, int[] outline, int outlineWidth, int outlineHeight, boolean overlay) {
		if (skinWidth != SKIN_SIZE || skinHeight != SKIN_SIZE) {
			skin = resizeNearest(skin, skinWidth, skinHeight, SKIN_SIZE, SKIN_SIZE);
		}

		int[] totem = new int[SIZE * SIZE];

		// --- Kopf: 8x8 -> 16x16 (Nearest), Ecken weg ---
		int[] head = resizeNearest(crop(skin, SKIN_SIZE, 8, 8, 8, 8), 8, 8, 16, 16);
		applyCrops(head, 16, 16);
		if (overlay) {
			int[] hat = resizeNearest(crop(skin, SKIN_SIZE, 40, 8, 8, 8), 8, 8, 16, 16);
			applyCrops(hat, 16, 16);
			head = alphaComposite(head, hat);
		}
		pasteWithMask(totem, head, 16, 16, 8, 1);

		// --- Arme und Körper (ohne Skalierung) ---
		pasteWithMask(totem, crop(skin, SKIN_SIZE, 44, 20, 3, 9), 3, 9, 8, 17);   // linker Arm
		pasteWithMask(totem, crop(skin, SKIN_SIZE, 36, 52, 3, 9), 3, 9, 21, 17);  // rechter Arm
		pasteWithMask(totem, crop(skin, SKIN_SIZE, 20, 20, 8, 12), 8, 12, 12, 16); // Körper

		// --- Feinschliff ---
		set(totem, 10, 16, get(totem, 10, 17));
		for (int y = 16; y < 27; y++) {
			set(totem, 11, y, get(totem, 12, y));
		}
		for (int y = 16; y < 27; y++) {
			set(totem, 20, y, get(totem, 19, y));
		}
		set(totem, 21, 16, get(totem, 21, 17));

		// --- Outline ---
		if (outline != null) {
			if (outlineWidth != SIZE || outlineHeight != SIZE) {
				outline = resizeNearest(outline, outlineWidth, outlineHeight, SIZE, SIZE);
			}
			totem = alphaComposite(totem, outline);
		}
		return totem;
	}

	private static int get(int[] img, int x, int y) {
		return img[y * SIZE + x];
	}

	private static void set(int[] img, int x, int y, int argb) {
		img[y * SIZE + x] = argb;
	}

	private static void applyCrops(int[] img, int width, int height) {
		for (int[] c : HEAD_CROPS) {
			for (int x = c[0]; x <= c[2]; x++) {
				for (int y = c[1]; y <= c[3]; y++) {
					if (x >= 0 && x < width && y >= 0 && y < height) {
						img[y * width + x] = 0;
					}
				}
			}
		}
	}

	/** Wie PIL {@code crop}: Bereiche außerhalb des Bildes werden transparent. */
	static int[] crop(int[] src, int srcWidth, int x0, int y0, int w, int h) {
		int srcHeight = src.length / srcWidth;
		int[] out = new int[w * h];
		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				int sx = x0 + x;
				int sy = y0 + y;
				if (sx >= 0 && sy >= 0 && sx < srcWidth && sy < srcHeight) {
					out[y * w + x] = src[sy * srcWidth + sx];
				}
			}
		}
		return out;
	}

	/** Wie PIL {@code resize(..., Image.NEAREST)}: Abtastung in der Pixelmitte. */
	public static int[] resizeNearest(int[] src, int srcWidth, int srcHeight, int dstWidth, int dstHeight) {
		int[] out = new int[dstWidth * dstHeight];
		for (int y = 0; y < dstHeight; y++) {
			int sy = Math.min(srcHeight - 1, (int) ((y + 0.5) * srcHeight / dstHeight));
			for (int x = 0; x < dstWidth; x++) {
				int sx = Math.min(srcWidth - 1, (int) ((x + 0.5) * srcWidth / dstWidth));
				out[y * dstWidth + x] = src[sy * srcWidth + sx];
			}
		}
		return out;
	}

	/** PIL {@code Image.paste(src, (dx, dy), src)} auf ein 32x32-Ziel (Paste.c, paste_mask_RGBA). */
	static void pasteWithMask(int[] dst, int[] src, int srcWidth, int srcHeight, int dx, int dy) {
		for (int y = 0; y < srcHeight; y++) {
			int ty = dy + y;
			if (ty < 0 || ty >= SIZE) continue;
			for (int x = 0; x < srcWidth; x++) {
				int tx = dx + x;
				if (tx < 0 || tx >= SIZE) continue;
				int s = src[y * srcWidth + x];
				int mask = s >>> 24;
				int d = dst[ty * SIZE + tx];
				int out = 0;
				for (int shift = 0; shift <= 24; shift += 8) {
					int in1 = (d >>> shift) & 0xFF;
					int in2 = (s >>> shift) & 0xFF;
					out |= div255(in1 * (255 - mask) + in2 * mask) << shift;
				}
				dst[ty * SIZE + tx] = out;
			}
		}
	}

	private static int div255(int a) {
		int tmp = a + 128;
		return ((tmp >> 8) + tmp) >> 8;
	}

	/** PIL {@code Image.alpha_composite(dst, src)} (AlphaComposite.c). Beide gleich groß. */
	static int[] alphaComposite(int[] dst, int[] src) {
		final int precisionBits = 7;
		int[] out = new int[dst.length];
		for (int i = 0; i < dst.length; i++) {
			int s = src[i];
			int d = dst[i];
			int sa = s >>> 24;
			if (sa == 0) {
				out[i] = d;
				continue;
			}
			long da = d >>> 24;
			long blend = da * (255 - sa);
			long outa255 = (long) sa * 255 + blend;
			long coef1 = (long) sa * 255 * 255 * (1L << precisionBits) / outa255;
			long coef2 = 255L * (1L << precisionBits) - coef1;
			int r = channel(s >>> 16 & 0xFF, d >>> 16 & 0xFF, coef1, coef2, precisionBits);
			int g = channel(s >>> 8 & 0xFF, d >>> 8 & 0xFF, coef1, coef2, precisionBits);
			int b = channel(s & 0xFF, d & 0xFF, coef1, coef2, precisionBits);
			int a = (int) shiftForDiv255(outa255 + 0x80);
			out[i] = a << 24 | r << 16 | g << 8 | b;
		}
		return out;
	}

	private static int channel(int s, int d, long coef1, long coef2, int precisionBits) {
		long tmp = s * coef1 + d * coef2;
		return (int) (shiftForDiv255(tmp + (0x80L << precisionBits)) >> precisionBits);
	}

	private static long shiftForDiv255(long a) {
		return ((a >> 8) + a) >> 8;
	}
}
