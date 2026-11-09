package de.einfabo.skintotems.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import net.minecraft.world.entity.ItemOwner;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Die große Totem-Animation beim Auslösen übergibt in Vanilla keinen Halter. Wir setzen den
 * eigenen Spieler ein, damit dort das eigene Skin-Totem erscheint.
 */
@Mixin(ScreenEffectRenderer.class)
public abstract class ScreenEffectRendererMixin {
	@ModifyArg(
		method = "renderItemActivationAnimation",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/client/renderer/item/ItemModelResolver;updateForTopItem(Lnet/minecraft/client/renderer/item/ItemStackRenderState;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/ItemOwner;I)V"
		),
		index = 4
	)
	private @Nullable ItemOwner skintotems$useLocalPlayer(@Nullable ItemOwner owner) {
		return owner != null ? owner : Minecraft.getInstance().player;
	}
}
