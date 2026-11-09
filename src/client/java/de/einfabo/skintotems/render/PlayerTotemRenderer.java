package de.einfabo.skintotems.render;

import com.mojang.blaze3d.vertex.PoseStack;
import de.einfabo.skintotems.totem.SkinTotemManager.TotemTexture;
import java.util.function.Consumer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.world.item.ItemStack;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

/**
 * Zeichnet ein Skin-Totem als extrudiertes Item (wie {@code item/generated}), aber mit der
 * pro Spieler erzeugten Textur. Die Transforms (Größe, Haltung in der Hand, GUI …) setzt
 * {@link de.einfabo.skintotems.model.PlayerTotemItemModel} aus dem Basis-Modell.
 */
public final class PlayerTotemRenderer implements SpecialModelRenderer<TotemTexture> {
	public static final PlayerTotemRenderer INSTANCE = new PlayerTotemRenderer();
	private static final int WHITE = 0xFFFFFFFF;

	private PlayerTotemRenderer() {
	}

	@Override
	public void submit(
		final @Nullable TotemTexture totem,
		final PoseStack poseStack,
		final SubmitNodeCollector collector,
		final int lightCoords,
		final int overlayCoords,
		final boolean hasFoil,
		final int outlineColor
	) {
		if (totem == null) {
			return;
		}
		RenderType renderType = totem.translucent() ? RenderTypes.itemTranslucent(totem.textureId()) : RenderTypes.itemCutout(totem.textureId());
		ExtrudedMesh mesh = totem.mesh();

		if (outlineColor != 0) {
			// Wie ItemFeatureRenderer: Outline-Durchgang (leuchtende Spieler) statt Hauptdurchgang.
			renderType.outline().ifPresent(outline -> collector.submitCustomGeometry(
				poseStack, outline, (pose, buffer) -> mesh.emit(pose, buffer, outlineColor, lightCoords, overlayCoords)));
		} else {
			collector.submitCustomGeometry(poseStack, renderType, (pose, buffer) -> mesh.emit(pose, buffer, WHITE, lightCoords, overlayCoords));
		}

		if (hasFoil) {
			RenderType glint = totem.translucent() ? RenderTypes.glintTranslucent() : RenderTypes.glint();
			collector.submitCustomGeometry(poseStack, glint, (pose, buffer) -> mesh.emit(pose, buffer, WHITE, lightCoords, overlayCoords));
		}
	}

	@Override
	public void getExtents(final Consumer<Vector3fc> output) {
		float minZ = 7.5F / 16.0F;
		float maxZ = 8.5F / 16.0F;
		for (int i = 0; i < 8; i++) {
			output.accept(new Vector3f((i & 1), (i >> 1 & 1), (i & 4) == 0 ? minZ : maxZ));
		}
	}

	@Override
	public @Nullable TotemTexture extractArgument(final ItemStack stack) {
		// Das Argument hängt vom Halter ab, nicht vom Stack – es wird im ItemModel gesetzt.
		return null;
	}
}
