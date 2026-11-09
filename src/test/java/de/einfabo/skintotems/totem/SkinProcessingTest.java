package de.einfabo.skintotems.totem;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Random;
import org.junit.jupiter.api.Test;

class SkinProcessingTest {
	@Test
	void legacySkinGetsMirroredRightArm() {
		Random random = new Random(42);
		int[] legacy = new int[64 * 32];
		for (int i = 0; i < legacy.length; i++) {
			legacy[i] = 0xFF000000 | random.nextInt(0x1000000);
		}
		int[] img = SkinProcessing.process(legacy, 64, 32);
		// Vanilla: copyRect(44, 20, -8, 32, 4, 12, mirrorX) -> rechter Arm vorne bei (36, 52)
		for (int y = 0; y < 12; y++) {
			for (int x = 0; x < 4; x++) {
				assertEquals(legacy[(20 + y) * 64 + 44 + x], img[(52 + y) * 64 + 36 + (3 - x)]);
			}
		}
	}

	@Test
	void modernSkinOnlyForcesOpaqueBaseLayers() {
		int[] skin = new int[64 * 64];
		skin[8 * 64 + 8] = 0x00123456;   // Kopf (Basis) -> wird deckend
		skin[8 * 64 + 40] = 0x00123456;  // Hut-Overlay -> bleibt transparent
		int[] img = SkinProcessing.process(skin, 64, 64);
		assertEquals(0xFF123456, img[8 * 64 + 8]);
		assertEquals(0x00123456, img[8 * 64 + 40]);
	}

	@Test
	void legacyAndModernGiveSameTotem() {
		Random random = new Random(7);
		int[] legacy = new int[64 * 32];
		for (int i = 0; i < legacy.length; i++) {
			legacy[i] = 0xFF000000 | random.nextInt(0x1000000);
		}
		int[] converted = SkinProcessing.process(legacy, 64, 32);
		int[] totem = TotemImageGenerator.generate(converted, 64, 64, null, 0, 0, true);
		assertArrayEquals(totem, TotemImageGenerator.generate(SkinProcessing.process(converted, 64, 64), 64, 64, null, 0, 0, true));
	}

	@Test
	void wrongSizeIsRejected() {
		assertNull(SkinProcessing.process(new int[32 * 32], 32, 32));
	}
}
