package dev.openwork.mod;

import dev.openwork.mod.network.ModPayloads;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.StructureBlockEntity;

/**
 * Payload registration and the server side handlers behind every OpenWork operation.
 */
public final class ModNetworking {
	private ModNetworking() {
	}

	/** Payload codecs. Safe to call from both the common and the client entrypoint. */
	public static void registerPayloads() {
		PayloadTypeRegistry.clientboundPlay().register(ModPayloads.OpenBrowser.ID, ModPayloads.OpenBrowser.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(ModPayloads.SyncState.ID, ModPayloads.SyncState.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(ModPayloads.SelectStructure.ID, ModPayloads.SelectStructure.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(ModPayloads.RunAction.ID, ModPayloads.RunAction.CODEC);
	}

	/** Server handlers. Must only run on a dedicated or integrated server. */
	public static void registerServer() {
		ServerPlayNetworking.registerGlobalReceiver(ModPayloads.SelectStructure.ID, (payload, context) ->
				context.server().execute(() -> {
					ServerPlayer player = context.player();
					StructureActions.select(player, payload.pos(), payload.structure());
					sendSyncState(player, payload.pos());
				}));

		ServerPlayNetworking.registerGlobalReceiver(ModPayloads.RunAction.ID, (payload, context) ->
				context.server().execute(() -> {
					ServerPlayer player = context.player();
					runAction(player, payload.pos(), payload.action());
					sendSyncState(player, payload.pos());
				}));
	}

	private static void runAction(ServerPlayer player, BlockPos pos, String action) {
		if (action.startsWith("size:")) {
			String[] parts = action.split(":");

			if (parts.length == 3) {
				try {
					StructureActions.resize(player, pos, Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));

					return;
				} catch (NumberFormatException ignored) {
					return;
				}
			}

			return;
		}

		switch (action) {
			case StructureActions.ACTION_SAVE -> StructureActions.save(player, pos);
			case StructureActions.ACTION_SAVE_PLACE -> StructureActions.saveAndPlace(player, pos);
			case StructureActions.ACTION_DETECT_SIZE -> StructureActions.detectSize(player, pos);
			case StructureActions.ACTION_TOGGLE -> StructureActions.toggleMode(player, pos);
			default -> {
			}
		}
	}

	/** Ask a player to open the browser on a specific Structure Block. */
	public static void sendOpenBrowser(ServerPlayer player, BlockPos pos) {
		ServerLevel level = player.level();
		StructureBlockEntity be = readStructureBlock(level, pos);

		if (be == null) {
			return;
		}

		ServerPlayNetworking.send(player, new ModPayloads.OpenBrowser(
				pos,
				StructureActions.listStructures(level),
				be.getMode().getSerializedName(),
				be.hasStructureName() ? be.getStructureName() : "",
				be.getStructureSize().getX(),
				be.getStructureSize().getY(),
				be.getStructureSize().getZ()));
	}

	/** Push the current mode, structure and size of a block back to an open browser. */
	public static void sendSyncState(ServerPlayer player, BlockPos pos) {
		StructureBlockEntity be = readStructureBlock(player.level(), pos);

		if (be == null) {
			return;
		}

		ServerPlayNetworking.send(player, new ModPayloads.SyncState(
				be.getMode().getSerializedName(),
				be.hasStructureName() ? be.getStructureName() : "",
				be.getStructureSize().getX(),
				be.getStructureSize().getY(),
				be.getStructureSize().getZ()));
	}

	private static StructureBlockEntity readStructureBlock(ServerLevel level, BlockPos pos) {
		return level.getBlockEntity(pos) instanceof StructureBlockEntity be ? be : null;
	}
}
