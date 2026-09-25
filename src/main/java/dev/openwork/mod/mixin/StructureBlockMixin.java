package dev.openwork.mod.mixin;

import dev.openwork.mod.ModNetworking;
import dev.openwork.mod.StructureActions;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.StructureBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Replaces the vanilla Structure Block screen.
 *
 * <p>Right click with an empty hand toggles save/load instantly.
 * Sneak and right click opens the OpenWork browser instead of the vanilla screen.
 *
 * <p>The return value is consumed on both sides so vanilla never opens its own screen.
 */
@Mixin(StructureBlock.class)
public class StructureBlockMixin {
	@Inject(method = "useWithoutItem", at = @At("HEAD"), cancellable = true)
	private void openwork$overrideInteraction(BlockState state, Level level, BlockPos pos, Player player,
			BlockHitResult hitResult, CallbackInfoReturnable<InteractionResult> cir) {
		if (level instanceof ServerLevel && player instanceof ServerPlayer serverPlayer) {
			if (player.isShiftKeyDown()) {
				ModNetworking.sendOpenBrowser(serverPlayer, pos);
			} else {
				StructureActions.toggleMode(serverPlayer, pos);
			}
		}

		cir.setReturnValue(InteractionResult.SUCCESS_SERVER);
	}
}
