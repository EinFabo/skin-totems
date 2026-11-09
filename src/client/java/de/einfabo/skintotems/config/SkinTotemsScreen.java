package de.einfabo.skintotems.config;

import com.mojang.serialization.Codec;
import de.einfabo.skintotems.config.SkinTotemsConfig.Style;
import de.einfabo.skintotems.totem.SkinTotemManager;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Einstellungsmenü (Mod Menu oder Tastenkürzel). Links eine Live-Vorschau: eine eigene, still
 * stehende Figur mit dem eigenen Skin und Totems in beiden Händen (mit der Maus drehbar) und das
 * Inventar-Icon; rechts die Optionen.
 */
public final class SkinTotemsScreen extends OptionsSubScreen {
	private static final int PREVIEW_WIDTH = 130;
	private static final int MIN_WIDTH_FOR_PREVIEW = 310 + PREVIEW_WIDTH + 20;
	private static final ItemStack TOTEM = new ItemStack(Items.TOTEM_OF_UNDYING);
	private static final Codec<Style> STYLE_CODEC = Codec.STRING.xmap(s -> Style.valueOf(s.toUpperCase(Locale.ROOT)), s -> s.name().toLowerCase(Locale.ROOT));

	/** Drehung der Vorschau-Figur in Grad (0 = schaut nach vorne) und Schwung nach dem Loslassen. */
	private float previewYaw = -25.0F;
	private float spinVelocity;
	private boolean draggingPreview;
	private long lastFrameMillis = Util.getMillis();
	private int previewTop;
	private int previewBottom;

	public SkinTotemsScreen(Screen lastScreen) {
		super(lastScreen, Minecraft.getInstance().options, Component.translatable("skintotems.options.title"));
	}

	@Override
	protected void addOptions() {
		this.list.addBig(new OptionInstance<>(
			"skintotems.options.style",
			OptionInstance.cachedConstantTooltip(Component.translatable("skintotems.options.style.tooltip")),
			(caption, value) -> Component.translatable("skintotems.options.style." + value.name().toLowerCase(Locale.ROOT)), // „Stil:“ setzt der Knopf selbst davor
			new OptionInstance.Enum<>(List.of(Style.values()), STYLE_CODEC),
			SkinTotemsConfig.style(),
			SkinTotemsConfig::setStyle
		));

		this.list.addHeader(Component.translatable("skintotems.options.header.where"));
		this.list.addSmall(
			bool("show_others", SkinTotemsConfig.showOthers(), SkinTotemsConfig::setShowOthers),
			bool("show_own_hand", SkinTotemsConfig.showOwnHand(), SkinTotemsConfig::setShowOwnHand),
			bool("show_inventory", SkinTotemsConfig.showInventory(), SkinTotemsConfig::setShowInventory),
			bool("show_activation", SkinTotemsConfig.showActivation(), SkinTotemsConfig::setShowActivation)
		);

		this.list.addHeader(Component.translatable("skintotems.options.header.totem"));
		this.list.addSmall(
			bool("totem_hat", SkinTotemsConfig.totemHat(), v -> {
				SkinTotemsConfig.setTotemHat(v);
				SkinTotemManager.regenerate();
			}),
			bool("totem_outline", SkinTotemsConfig.totemOutline(), v -> {
				SkinTotemsConfig.setTotemOutline(v);
				SkinTotemManager.regenerate();
			})
		);

		this.list.addHeader(Component.translatable("skintotems.options.header.plushie"));
		this.list.addSmall(
			bool("plushie_outer_layer", SkinTotemsConfig.plushieOuterLayer(), SkinTotemsConfig::setPlushieOuterLayer),
			new OptionInstance<>(
				"skintotems.options.plushie_size",
				OptionInstance.cachedConstantTooltip(Component.translatable("skintotems.options.plushie_size.tooltip")),
				(caption, value) -> Options.genericValueLabel(caption, Component.literal(value + " %")),
				new OptionInstance.IntRange(SkinTotemsConfig.MIN_PLUSHIE_SIZE, SkinTotemsConfig.MAX_PLUSHIE_SIZE),
				SkinTotemsConfig.plushieSize(),
				SkinTotemsConfig::setPlushieSize
			)
		);
	}

	private static OptionInstance<Boolean> bool(String key, boolean value, OptionInstance.ValueUpdateListener<Boolean> onChange) {
		return OptionInstance.createBoolean(
			"skintotems.options." + key,
			OptionInstance.cachedConstantTooltip(Component.translatable("skintotems.options." + key + ".tooltip")),
			value,
			onChange
		);
	}

	private boolean showPreview() {
		return this.width >= MIN_WIDTH_FOR_PREVIEW;
	}

	@Override
	protected void repositionElements() {
		super.repositionElements();
		if (this.list != null && showPreview()) {
			this.list.updateSizeAndPosition(this.width - PREVIEW_WIDTH, this.layout.getContentHeight(), PREVIEW_WIDTH, this.layout.getHeaderHeight());
		}
	}

	@Override
	public void extractRenderState(final GuiGraphicsExtractor graphics, final int mouseX, final int mouseY, final float a) {
		super.extractRenderState(graphics, mouseX, mouseY, a);
		updateSpin();
		if (!showPreview()) {
			return;
		}
		int top = this.layout.getHeaderHeight() + 6;
		int bottom = this.height - this.layout.getFooterHeight() - 6;
		int centerX = PREVIEW_WIDTH / 2;
		graphics.centeredText(this.font, Component.translatable("skintotems.options.preview").getString(), centerX, top, 0xFFFFFFFF);

		LocalPlayer player = this.minecraft.player;
		if (player == null) {
			graphics.centeredText(this.font, Component.translatable("skintotems.options.preview.no_world").getString(), centerX, top + 40, 0xFFA0A0A0);
			return;
		}

		// Inventar-Icon in doppelter Größe
		int iconY = bottom - 34;
		graphics.pose().pushMatrix();
		graphics.pose().translate(centerX - 16, iconY);
		graphics.pose().scale(2.0F, 2.0F);
		graphics.item(TOTEM, 0, 0);
		graphics.pose().popMatrix();

		int hintY = iconY - 12;
		graphics.centeredText(this.font, Component.translatable("skintotems.options.preview.drag").getString(), centerX, hintY, 0xFFA0A0A0);

		this.previewTop = top + 12;
		this.previewBottom = hintY - 4;
		extractPlayerPreview(graphics, player, 12, this.previewTop, PREVIEW_WIDTH - 12, this.previewBottom);
	}

	/** Schwung nach dem Loslassen, unabhängig von der Bildrate. */
	private void updateSpin() {
		long now = Util.getMillis();
		float ticks = Math.min((now - this.lastFrameMillis) / 50.0F, 5.0F);
		this.lastFrameMillis = now;
		if (!this.draggingPreview && Math.abs(this.spinVelocity) > 0.01F) {
			this.previewYaw -= this.spinVelocity * ticks;
			this.spinVelocity *= (float) Math.pow(0.85, ticks);
		}
	}

	private boolean overPreview(double x, double y) {
		return showPreview() && this.minecraft.player != null && x < PREVIEW_WIDTH && y >= this.previewTop && y <= this.previewBottom;
	}

	@Override
	public boolean mouseClicked(final MouseButtonEvent event, final boolean doubleClick) {
		if (event.button() == 0 && overPreview(event.x(), event.y())) {
			this.draggingPreview = true;
			this.spinVelocity = 0.0F;
			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseDragged(final MouseButtonEvent event, final double dx, final double dy) {
		if (this.draggingPreview) {
			this.previewYaw -= (float) dx * 2.0F;
			this.spinVelocity = (float) dx * 2.0F;
			return true;
		}
		return super.mouseDragged(event, dx, dy);
	}

	@Override
	public boolean mouseReleased(final MouseButtonEvent event) {
		if (this.draggingPreview) {
			this.draggingPreview = false;
			return true;
		}
		return super.mouseReleased(event);
	}

	/**
	 * Eigene Figur: Render-State des Spielers (für Skin und Modell), aber in Grundhaltung – keine
	 * Lauf-, Schleich-, Schwimm- oder Schlaganimation, Kopf gerade, Drehung nur per Maus.
	 */
	private void extractPlayerPreview(GuiGraphicsExtractor graphics, LocalPlayer player, int x0, int y0, int x1, int y1) {
		if (y1 - y0 < 40) {
			return;
		}
		EntityRenderer<? super LocalPlayer, ?> renderer = this.minecraft.getEntityRenderDispatcher().getRenderer(player);
		EntityRenderState state = renderer.createRenderState(player, 1.0F);
		state.shadowPieces.clear();
		state.outlineColor = 0;
		state.nameTag = null;
		if (state instanceof LivingEntityRenderState living) {
			living.pose = Pose.STANDING;
			living.bodyRot = 180.0F + this.previewYaw;
			living.yRot = 0.0F;
			living.xRot = 0.0F;
			living.walkAnimationPos = 0.0F;
			living.walkAnimationSpeed = 0.0F;
			living.deathTime = 0.0F;
			living.isUpsideDown = false;
			living.isFullyFrozen = false;
			living.isInWater = false;
			living.isAutoSpinAttack = false;
			living.hasRedOverlay = false;
			living.boundingBoxWidth = living.boundingBoxWidth / living.scale;
			living.boundingBoxHeight = living.boundingBoxHeight / living.scale;
			living.scale = 1.0F;
		}
		if (state instanceof HumanoidRenderState humanoid) {
			humanoid.isCrouching = false;
			humanoid.isFallFlying = false;
			humanoid.isVisuallySwimming = false;
			humanoid.swimAmount = 0.0F;
			humanoid.isPassenger = false;
			humanoid.isUsingItem = false;
			humanoid.ticksUsingItem = 0.0F;
			humanoid.speedValue = 1.0F;
			humanoid.elytraRotX = (float) (Math.PI / 12.0);
			humanoid.elytraRotY = 0.0F;
			humanoid.elytraRotZ = (float) (-Math.PI / 12.0);
		}
		if (state instanceof AvatarRenderState avatar) {
			avatar.fallFlyingTimeInTicks = 0.0F;
			avatar.shouldApplyFlyingYRot = false;
			avatar.flyingYRot = 0.0F;
			avatar.parrotOnLeftShoulder = null;
			avatar.parrotOnRightShoulder = null;
		}
		if (state instanceof ArmedEntityRenderState armed) {
			ItemModelResolver resolver = this.minecraft.getItemModelResolver();
			resolver.updateForLiving(armed.rightHandItemState, TOTEM, ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, player);
			resolver.updateForLiving(armed.leftHandItemState, TOTEM, ItemDisplayContext.THIRD_PERSON_LEFT_HAND, player);
			armed.rightHandItemStack = TOTEM;
			armed.leftHandItemStack = TOTEM;
			armed.rightArmPose = HumanoidModel.ArmPose.ITEM;
			armed.leftArmPose = HumanoidModel.ArmPose.ITEM;
			armed.attackTime = 0.0F;
		}

		Quaternionf rotation = new Quaternionf().rotateZ((float) Math.PI);
		float size = Math.min((x1 - x0) / 1.05F, (y1 - y0) / 2.1F);
		Vector3f translation = new Vector3f(0.0F, state.boundingBoxHeight / 2.0F + 0.0625F, 0.0F);
		graphics.entity(state, size, translation, rotation, new Quaternionf(), x0, y0, x1, y1);
	}
}
