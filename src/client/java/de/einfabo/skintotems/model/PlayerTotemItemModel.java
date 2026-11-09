package de.einfabo.skintotems.model;

import com.mojang.math.Transformation;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import de.einfabo.skintotems.config.SkinTotemsConfig;
import de.einfabo.skintotems.render.PlayerTotemRenderer;
import de.einfabo.skintotems.render.PlushieRenderer;
import de.einfabo.skintotems.totem.SkinTotemManager;
import de.einfabo.skintotems.totem.SkinTotemManager.TotemTexture;
import java.util.Optional;
import java.util.function.Supplier;
import com.google.common.base.Suppliers;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.ClientAvatarEntity;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemModels;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.item.ModelRenderProperties;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ResolvableModel;
import net.minecraft.client.resources.model.ResolvedModel;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4fc;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

/**
 * Item-Modell-Typ {@code skintotems:player_totem}.
 *
 * <pre>{@code
 * { "model": {
 *     "type": "skintotems:player_totem",
 *     "base": "minecraft:item/totem_of_undying",
 *     "plushie_base": "skintotems:item/plushie",
 *     "fallback": { "type": "minecraft:model", "model": "minecraft:item/totem_of_undying" } } }
 * }</pre>
 *
 * Hält ein Spieler (oder Mannequin) das Item, wird sein Skin-Totem gezeichnet – mit den
 * Display-Transforms von {@code base}, also genau so groß und so gehalten wie das normale Totem
 * inklusive aller Änderungen durch Texture Packs. Ohne Spieler als Halter (gedroppt, Item Frame,
 * Rüstungsständer …) oder solange der Skin noch lädt, greift {@code fallback}.
 *
 * <p>Steht die Config auf Plushie und ist {@code plushie_base} gesetzt, wird statt des flachen
 * Totems das 3D-Plushie gezeichnet – mit den Display-Transforms von {@code plushie_base}.
 */
public final class PlayerTotemItemModel implements ItemModel {
	private final ItemModel fallback;
	private final ModelRenderProperties properties;
	private final Matrix4fc transformation;
	private final Supplier<Vector3fc[]> extents;
	private final @Nullable ModelRenderProperties plushieProperties;
	private final Supplier<Vector3fc[]> plushieExtents;

	private PlayerTotemItemModel(
		ItemModel fallback, ModelRenderProperties properties, @Nullable ModelRenderProperties plushieProperties, Matrix4fc transformation
	) {
		this.fallback = fallback;
		this.properties = properties;
		this.plushieProperties = plushieProperties;
		this.transformation = transformation;
		this.extents = Suppliers.memoize(() -> {
			Set<Vector3fc> results = new HashSet<>();
			PlayerTotemRenderer.INSTANCE.getExtents(results::add);
			return results.toArray(new Vector3fc[0]);
		});
		this.plushieExtents = Suppliers.memoize(() -> {
			Set<Vector3fc> results = new HashSet<>();
			PlushieRenderer.INSTANCE.getExtents(results::add);
			return results.toArray(new Vector3fc[0]);
		});
	}

	@Override
	public void update(
		final ItemStackRenderState output,
		final ItemStack item,
		final ItemModelResolver resolver,
		final ItemDisplayContext displayContext,
		final @Nullable ClientLevel level,
		final @Nullable ItemOwner owner,
		final int seed
	) {
		// GUIs ohne Halter (Achievements, Toasts, Rezeptbuch …) zeigen das eigene Totem
		ItemOwner effectiveOwner = owner == null && displayContext == ItemDisplayContext.GUI ? Minecraft.getInstance().player : owner;
		ClientAvatarEntity avatar = effectiveOwner != null && effectiveOwner.asLivingEntity() instanceof ClientAvatarEntity a ? a : null;
		TotemTexture totem = avatar != null && isEnabled(avatar, displayContext) ? SkinTotemManager.get(avatar) : null;
		if (totem == null) {
			this.fallback.update(output, item, resolver, displayContext, level, owner, seed);
			return;
		}

		output.appendModelIdentityElement(this);
		output.appendModelIdentityElement(SkinTotemsConfig.revision());
		ItemStackRenderState.LayerRenderState layer = output.newLayer();
		if (item.hasFoil()) {
			ItemStackRenderState.FoilType foilType = ItemStackRenderState.FoilType.STANDARD;
			layer.setFoilType(foilType);
			output.setAnimated();
			output.appendModelIdentityElement(foilType);
		}
		layer.setLocalTransform(this.transformation);

		if (this.plushieProperties != null && SkinTotemsConfig.style() == SkinTotemsConfig.Style.PLUSHIE) {
			PlushieRenderer.Args args = new PlushieRenderer.Args(totem.skinTexture(), avatar.getSkin().model() == PlayerModelType.SLIM);
			output.appendModelIdentityElement(args);
			layer.setExtents(this.plushieExtents);
			layer.setupSpecialModel(PlushieRenderer.INSTANCE, args);
			this.plushieProperties.applyToLayer(layer, displayContext);
		} else {
			output.appendModelIdentityElement(totem.textureId());
			layer.setExtents(this.extents);
			layer.setupSpecialModel(PlayerTotemRenderer.INSTANCE, totem);
			this.properties.applyToLayer(layer, displayContext);
		}
	}

	/** Einstellungen: wo das Skin-Totem gezeigt wird. */
	private static boolean isEnabled(ClientAvatarEntity avatar, ItemDisplayContext displayContext) {
		if (avatar != Minecraft.getInstance().player) {
			return SkinTotemsConfig.showOthers();
		}
		return switch (displayContext) {
			case GUI -> SkinTotemsConfig.showInventory();
			case FIXED -> SkinTotemsConfig.showActivation(); // Totem-Animation (Item Frames haben keinen Spieler als Halter)
			default -> SkinTotemsConfig.showOwnHand();
		};
	}

	public record Unbaked(
		Identifier base, Optional<Identifier> plushieBase, ItemModel.Unbaked fallback, Optional<Transformation> transformation
	) implements ItemModel.Unbaked {
		public static final MapCodec<Unbaked> MAP_CODEC = RecordCodecBuilder.mapCodec(
			i -> i.group(
					Identifier.CODEC.fieldOf("base").forGetter(Unbaked::base),
					Identifier.CODEC.optionalFieldOf("plushie_base").forGetter(Unbaked::plushieBase),
					ItemModels.CODEC.fieldOf("fallback").forGetter(Unbaked::fallback),
					Transformation.EXTENDED_CODEC.optionalFieldOf("transformation").forGetter(Unbaked::transformation)
				)
				.apply(i, Unbaked::new)
		);

		@Override
		public void resolveDependencies(final ResolvableModel.Resolver resolver) {
			resolver.markDependency(this.base);
			this.plushieBase.ifPresent(resolver::markDependency);
			this.fallback.resolveDependencies(resolver);
		}

		@Override
		public ItemModel bake(final ItemModel.BakingContext context, final Matrix4fc transformation) {
			Matrix4fc modelTransform = Transformation.compose(transformation, this.transformation);
			ItemModel bakedFallback = this.fallback.bake(context, transformation);
			ModelBaker baker = context.blockModelBaker();
			ModelRenderProperties properties = properties(baker, this.base);
			ModelRenderProperties plushieProperties = this.plushieBase.map(id -> properties(baker, id)).orElse(null);
			return new PlayerTotemItemModel(bakedFallback, properties, plushieProperties, modelTransform);
		}

		private static ModelRenderProperties properties(ModelBaker baker, Identifier id) {
			ResolvedModel model = baker.getModel(id);
			return ModelRenderProperties.fromResolvedModel(baker, model, model.getTopTextureSlots());
		}

		@Override
		public MapCodec<Unbaked> type() {
			return MAP_CODEC;
		}
	}
}
