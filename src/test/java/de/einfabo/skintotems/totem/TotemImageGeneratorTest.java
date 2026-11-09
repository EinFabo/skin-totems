package de.einfabo.skintotems.totem;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import javax.imageio.ImageIO;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.api.Test;

/**
 * Vergleicht den Java-Port pixelgenau mit Referenzbildern, die das ORIGINAL
 * (app.py: generate_totem_image) erzeugt hat – siehe tools/make_reference_totems.py.
 */
class TotemImageGeneratorTest {
	private static final Path OUTLINE = Path.of("src/client/resources/assets/skintotems/textures/totem/outline.png");

	static Stream<Arguments> cases() {
		return Stream.of("alex", "einfabo", "kai", "steve")
			.flatMap(skin -> Stream.of(Arguments.of(skin, true), Arguments.of(skin, false)));
	}

	@ParameterizedTest(name = "{0} overlay={1}")
	@MethodSource("cases")
	void matchesOriginalPythonGenerator(String skin, boolean overlay) throws IOException {
		BufferedImage skinImg = load("/skins/" + skin + ".png");
		BufferedImage outline = ImageIO.read(OUTLINE.toFile());
		BufferedImage expected = load("/reference/" + skin + "_" + (overlay ? "overlay" : "nooverlay") + ".png");

		int[] result = TotemImageGenerator.generate(
			argb(skinImg), skinImg.getWidth(), skinImg.getHeight(),
			argb(outline), outline.getWidth(), outline.getHeight(), overlay);

		int[] exp = argb(expected);
		for (int i = 0; i < exp.length; i++) {
			// Voll transparente Pixel vergleichen wir nur über Alpha (Farbe ist dort egal).
			int e = exp[i] >>> 24 == 0 ? 0 : exp[i];
			int r = result[i] >>> 24 == 0 ? 0 : result[i];
			assertEquals(Integer.toHexString(e), Integer.toHexString(r), "Pixel " + (i % 32) + "," + (i / 32));
		}
	}

	@Test
	void hdSkinGivesSameResultAsDownscaled() throws IOException {
		BufferedImage skin = load("/skins/einfabo.png");
		int[] px = argb(skin);
		int[] hd = TotemImageGenerator.resizeNearest(px, 64, 64, 256, 256);
		int[] normal = TotemImageGenerator.generate(px, 64, 64, null, 0, 0, true);
		int[] fromHd = TotemImageGenerator.generate(hd, 256, 256, null, 0, 0, true);
		for (int i = 0; i < normal.length; i++) {
			assertEquals(normal[i], fromHd[i]);
		}
	}

	private static BufferedImage load(String resource) throws IOException {
		try (InputStream in = TotemImageGeneratorTest.class.getResourceAsStream(resource)) {
			assertNotNull(in, resource);
			return ImageIO.read(in);
		}
	}

	private static int[] argb(BufferedImage img) {
		return img.getRGB(0, 0, img.getWidth(), img.getHeight(), null, 0, img.getWidth());
	}
}
