package de.einfabo.skintotems.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import de.einfabo.skintotems.config.SkinTotemsConfig;
import de.einfabo.skintotems.plushie.PlushieGeometry;
import java.util.function.Consumer;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

/**
 * Zeichnet das Plushie mit dem Skin des Halters. Die Display-Transforms (Größe, Haltung) setzt
 * {@link de.einfabo.skintotems.model.PlayerTotemItemModel} aus {@code skintotems:item/plushie}.
 */
public final class PlushieRenderer implements SpecialModelRenderer<PlushieRenderer.Args> {
	public static final PlushieRenderer INSTANCE = new PlushieRenderer();

	/** @param skin registrierte Skin-Textur, @param slim dünne Arme (Alex-Modell) */
	public record Args(Identifier skin, boolean slim) {
	}

	private PlushieRenderer() {
	}

	@Override
	public void submit(
		final @Nullable Args args,
		final PoseStack poseStack,
		final SubmitNodeCollector collector,
		final int lightCoords,
		final int overlayCoords,
		final boolean hasFoil,
		final int outlineColor
	) {
		if (args == null) {
			return;
		}
		ModelPart model = PlushieGeometry.get(args.slim(), SkinTotemsConfig.plushieOuterLayer());
		if (model == null) {
			return;
		}
		poseStack.pushPose();
		applySize(poseStack);
		toItemSpace(poseStack);
		collector.order(0).submitModelPart(
			model, poseStack, RenderTypes.entityTranslucent(args.skin()), lightCoords, OverlayTexture.NO_OVERLAY, null, -1, null, outlineColor);
		if (hasFoil) {
			collector.order(1).submitModelPart(model, poseStack, RenderTypes.entityGlint(), lightCoords, OverlayTexture.NO_OVERLAY, null, -1, null, 0);
		}
		poseStack.popPose();
	}

	/** Größe aus den Einstellungen, skaliert um die Mitte des Plushies. */
	private static void applySize(PoseStack poseStack) {
		float size = SkinTotemsConfig.plushieSize() / 100.0F;
		if (size != 1.0F) {
			poseStack.translate(0.5F, 0.45F, 0.5F);
			poseStack.scale(size, size, size);
			poseStack.translate(-0.5F, -0.45F, -0.5F);
		}
	}

	/** Entity-Koordinaten (Y nach unten, Mitte bei 0) → Item-Raum (0..1, Boden unten, Gesicht nach +Z). */
	private static void toItemSpace(PoseStack poseStack) {
		poseStack.translate(0.5F, 0.0F, 0.5F);
		poseStack.mulPose(Axis.XP.rotationDegrees(180.0F));
	}

	@Override
	public void getExtents(final Consumer<Vector3fc> output) {
		ModelPart model = PlushieGeometry.get(false, true);
		if (model == null) {
			return;
		}
		PoseStack poseStack = new PoseStack();
		toItemSpace(poseStack);
		model.getExtentsForGui(poseStack, output);
	}

	@Override
	public @Nullable Args extractArgument(final ItemStack stack) {
		return null; // hängt vom Halter ab, wird im ItemModel gesetzt
	}
}
