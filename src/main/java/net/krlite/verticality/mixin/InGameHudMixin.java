package net.krlite.verticality.mixin;

import net.krlite.verticality.Verticality;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.JumpingMount;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Arm;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin(InGameHud.class)
public class InGameHudMixin {
	@Inject(method = "renderHotbar", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/util/math/MatrixStack;push()V", shift = At.Shift.AFTER))
	private void renderHotbar(float tickDelta, DrawContext context, CallbackInfo ci) {
		if (Verticality.enabled()) {
			context.getMatrices().translate(
					 Verticality.CENTER_DISTANCE_TO_BORDER
							 + (Verticality.height() - Verticality.CENTER_DISTANCE_TO_BORDER)
							 - Verticality.HOTBAR_HEIGHT * Verticality.hotbar(),
					(
							Verticality.height() - Verticality.width()
									+ Verticality.OFFHAND_WIDTH * Verticality.offset()
					) / 2.0, 0
			);
			context.getMatrices().multiply(RotationAxis.POSITIVE_Z.rotationDegrees(90));
		}
		else {
			context.getMatrices().translate(0, Verticality.HOTBAR_HEIGHT * Verticality.hotbar(), 0);
		}
	}

	@Redirect(
			method = "renderHotbar",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/client/gui/DrawContext;drawTexture(Lnet/minecraft/util/Identifier;IIIIII)V",
					ordinal = 1
			)
	)
	private void drawSelectedSlot(DrawContext context, Identifier identifier, int x, int y, int u, int v, int width, int height) {
		Verticality.drawSelectedSlot(context, identifier, x, y, u, v, width, height);
	}

	@Redirect(
			method = "renderHotbar",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/client/gui/DrawContext;drawTexture(Lnet/minecraft/util/Identifier;IIIIII)V"
			),
			slice = @Slice(
					from = @At(value = "INVOKE", target = "Lnet/minecraft/item/ItemStack;isEmpty()Z", ordinal = 0),
					to = @At(value = "INVOKE", target = "Lnet/minecraft/client/util/math/MatrixStack;pop()V")
			)
	)
	private void drawOffhandSlot(DrawContext context, Identifier identifier, int x, int y, int u, int v, int width, int height) {
		if (Verticality.enabled()) {
			if ((MinecraftClient.getInstance().options.getMainArm().getValue().getOpposite() == Arm.LEFT) == !Verticality.upsideDown()) {
				context.drawTexture(identifier, (int) (Verticality.width() / 2.0 - 91 - 29), Verticality.height() - 23, 24, 22, 29, 24); // Left
			}
			else context.drawTexture(identifier, (int) (Verticality.width() / 2.0 + 91), Verticality.height() - 23, 53, 22, 29, 24); // Right
		}
		else context.drawTexture(identifier, x, y, u, v, width, height);
	}
}

@Mixin(InGameHud.class)
abstract
class ItemAdjustor {
	@Shadow protected abstract void renderHotbarItem(DrawContext context, int x, int y, float tickDelta, PlayerEntity playerEntity, ItemStack itemStack, int seed);

	@Redirect(
			method = "renderHotbar",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/client/gui/hud/InGameHud;renderHotbarItem(Lnet/minecraft/client/gui/DrawContext;IIFLnet/minecraft/entity/player/PlayerEntity;Lnet/minecraft/item/ItemStack;I)V"
			)
	)
	private void renderHotbarItem(InGameHud inGameHud, DrawContext context, int x, int y, float tickDelta, PlayerEntity player, ItemStack stack, int seed) {
		if (Verticality.enabled()) {
			double xRelative = (x + 8) - Verticality.width() / 2.0, yRelative = (y + 8) - (Verticality.height() - Verticality.CENTER_DISTANCE_TO_BORDER);
			renderHotbarItem(
					context,
					(int) Math.round(Verticality.CENTER_DISTANCE_TO_BORDER - yRelative) - 8,
					(int) Math.round(Verticality.height() / 2.0 + xRelative) - 8,
					tickDelta, player, stack, seed
			);
		}
		else renderHotbarItem(context, x, y, tickDelta, player, stack, seed);
	}

	@ModifyArgs(
			method = "renderHotbar",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/client/gui/hud/InGameHud;renderHotbarItem(Lnet/minecraft/client/gui/DrawContext;IIFLnet/minecraft/entity/player/PlayerEntity;Lnet/minecraft/item/ItemStack;I)V"
			),
			slice = @Slice(
					from = @At(value = "INVOKE", target = "Lnet/minecraft/item/ItemStack;isEmpty()Z", ordinal = 1),
					to = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;enableBlend()V")
			)
	)
	private void fixOffhandItem(Args args) {
		int x = args.get(2);
		if (MinecraftClient.getInstance().player != null && MinecraftClient.getInstance().player.getMainArm() == Arm.LEFT)
			// Don't use 'MinecraftClient.getInstance().options.getMainArm()' as vanilla doesn't use it neither, otherwise causing the offhand item out-of-phase
			args.set(2, (int) Math.round(x - 2 * ((x + 8) - Verticality.width() / 2.0))); // Revert the x-coordinate of the item
		else args.set(2, x);
	}

	@Inject(
			method = "renderHotbarItem",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/item/ItemStack;getBobbingAnimationTime()I"
			)
	)
	private void drawItemPre(DrawContext context, int x, int y, float f, PlayerEntity player, ItemStack stack, int seed, CallbackInfo ci) {
		context.getMatrices().push();
		Verticality.translateIcon(context, y, false);
	}

	@Inject(
			method = "renderHotbarItem",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/client/gui/DrawContext;drawItemInSlot(Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/item/ItemStack;II)V"
			)
	)
	private void drawItemPost(DrawContext context, int x, int y, float f, PlayerEntity player, ItemStack stack, int seed, CallbackInfo ci) {
		context.getMatrices().pop();
	}

	@Inject(
			method = "renderHotbarItem",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/client/gui/DrawContext;drawItemInSlot(Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/item/ItemStack;II)V"
			)
	)
	private void drawItemInSlotPre(DrawContext context, int x, int y, float f, PlayerEntity player, ItemStack stack, int seed, CallbackInfo ci) {
		context.getMatrices().push();
		Verticality.translateIcon(context, y, false);
	}

	@Inject(
			method = "renderHotbarItem",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/client/gui/DrawContext;drawItemInSlot(Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/item/ItemStack;II)V",
					shift = At.Shift.AFTER
			)
	)
	private void drawItemInSlotPost(DrawContext context, int x, int y, float f, PlayerEntity player, ItemStack stack, int seed, CallbackInfo ci) {
		context.getMatrices().pop();
	}
}

@Mixin(InGameHud.class)
class BarAdjustor {
	@Inject(
			method = "renderStatusBars",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/client/gui/DrawContext;drawTexture(Lnet/minecraft/util/Identifier;IIIIII)V"
			)
	)
	private void renderStatusBarsPre(DrawContext context, CallbackInfo ci) {
		context.getMatrices().push();
		context.getMatrices().translate(0, Verticality.HOTBAR_HEIGHT * Verticality.later(), 0);
	}

	@Inject(
			method = "renderStatusBars",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/client/gui/DrawContext;drawTexture(Lnet/minecraft/util/Identifier;IIIIII)V",
					shift = At.Shift.AFTER
			)
	)
	private void renderStatusBarsPost(DrawContext context, CallbackInfo ci) {
		context.getMatrices().pop();
	}

	@Inject(
			method = "renderStatusBars",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/client/gui/hud/InGameHud;renderHealthBar(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/entity/player/PlayerEntity;IIIIFIIIZ)V"
			)
	)
	private void renderHealthBarPre(DrawContext context, CallbackInfo ci) {
		context.getMatrices().push();
		context.getMatrices().translate(0, Verticality.HOTBAR_HEIGHT * Verticality.later(), 0);
	}

	@Inject(
			method = "renderStatusBars",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/client/gui/hud/InGameHud;renderHealthBar(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/entity/player/PlayerEntity;IIIIFIIIZ)V",
					shift = At.Shift.AFTER
			)
	)
	private void renderHealthBarPost(DrawContext context, CallbackInfo ci) {
		context.getMatrices().pop();
	}

	@Inject(
			method = "renderMountHealth",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/client/gui/DrawContext;drawTexture(Lnet/minecraft/util/Identifier;IIIIII)V"
			)
	)
	private void renderMountHealthPre(DrawContext context, CallbackInfo ci) {
		context.getMatrices().push();
		context.getMatrices().translate(0, Verticality.HOTBAR_HEIGHT * Verticality.later(), 0);
	}

	@Inject(
			method = "renderMountHealth",
			at = @At(
					value = "INVOKE", target = "Lnet/minecraft/client/gui/DrawContext;drawTexture(Lnet/minecraft/util/Identifier;IIIIII)V",
					shift = At.Shift.AFTER
			)
	)
	private void renderMountHealthPost(DrawContext context, CallbackInfo ci) {
		context.getMatrices().pop();
	}

	@Inject(
			method = "renderMountJumpBar",
			at = @At(
					value = "INVOKE", target = "Lnet/minecraft/client/gui/DrawContext;drawTexture(Lnet/minecraft/util/Identifier;IIIIII)V"
			)
	)
	private void renderMountJumpBarPre(JumpingMount mount, DrawContext context, int x, CallbackInfo ci) {
		context.getMatrices().push();
		context.getMatrices().translate(0, Verticality.HOTBAR_HEIGHT * Verticality.later(), 0);
	}

	@Inject(
			method = "renderMountJumpBar",
			at = @At(
					value = "INVOKE", target = "Lnet/minecraft/client/gui/DrawContext;drawTexture(Lnet/minecraft/util/Identifier;IIIIII)V",
					shift = At.Shift.AFTER
			)
	)
	private void renderMountJumpBarPost(JumpingMount mount, DrawContext context, int x, CallbackInfo ci) {
		context.getMatrices().pop();
	}

	@Inject(
			method = "renderExperienceBar",
			at = @At(
					value = "INVOKE", target = "Lnet/minecraft/client/gui/DrawContext;drawTexture(Lnet/minecraft/util/Identifier;IIIIII)V"
			)
	)
	private void renderExperienceBarPre(DrawContext context, int x, CallbackInfo ci) {
		context.getMatrices().push();
		context.getMatrices().translate(0, Verticality.HOTBAR_HEIGHT * Verticality.later(), 0);
	}

	@Inject(
			method = "renderExperienceBar",
			at = @At(
					value = "INVOKE", target = "Lnet/minecraft/client/gui/DrawContext;drawTexture(Lnet/minecraft/util/Identifier;IIIIII)V",
					shift = At.Shift.AFTER
			)
	)
	private void renderExperienceBarPost(DrawContext context, int x, CallbackInfo ci) {
		context.getMatrices().pop();
	}

	@Inject(
			method = "renderHeldItemTooltip",
			at = @At(
					value = "INVOKE", target = "Lnet/minecraft/client/gui/DrawContext;drawTextWithShadow(Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/text/Text;III)I"
			)
	)
	private void renderHeldItemTooltipPre(DrawContext context, CallbackInfo ci) {
		context.getMatrices().push();
		context.getMatrices().translate(0, Verticality.HOTBAR_HEIGHT * Verticality.later(), 0);
	}

	@Inject(
			method = "renderHeldItemTooltip",
			at = @At(
					value = "INVOKE", target = "Lnet/minecraft/client/gui/DrawContext;drawTextWithShadow(Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/text/Text;III)I",
					shift = At.Shift.AFTER
			)
	)
	private void renderHeldItemTooltipPost(DrawContext context, CallbackInfo ci) {
		context.getMatrices().pop();
	}

	@Inject(method = "renderHeldItemTooltip", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/DrawContext;fill(IIIII)V"))
	private void fixHelpItemTooltipPre(DrawContext context, CallbackInfo ci) {
		if (MinecraftClient.getInstance().interactionManager != null && !MinecraftClient.getInstance().interactionManager.hasStatusBars()) {
			context.getMatrices().push();
			context.getMatrices().translate(0, Verticality.TOOLTIP_OFFSET * Verticality.later(), 0);
		}
	}

	@Inject(method = "renderHeldItemTooltip", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/DrawContext;fill(IIIII)V", shift = At.Shift.AFTER))
	private void fixHelpItemTooltipPost(DrawContext context, CallbackInfo ci) {
		if (MinecraftClient.getInstance().interactionManager != null && !MinecraftClient.getInstance().interactionManager.hasStatusBars()) {
			context.getMatrices().pop();
		}
	}
}
