package dev.openwork.mod.network;

import dev.openwork.mod.OpenWorkMod;

import io.netty.buffer.ByteBuf;

import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.List;

/**
 * All network payloads exchanged between the OpenWork client screen and the server.
 *
 * <p>Every structure mutation is server authoritative: the client only ever sends an
 * intent, and the server validates the target block before acting.
 */
public final class ModPayloads {
	private ModPayloads() {
	}

	/** S2C: open the structure browser for {@code pos} with the currently known structures. */
	public record OpenBrowser(BlockPos pos, List<Identifier> structures, String mode, String structure,
			int sizeX, int sizeY, int sizeZ) implements CustomPacketPayload {
		public static final CustomPacketPayload.Type<OpenBrowser> ID =
				new CustomPacketPayload.Type<>(OpenWorkMod.id("open_browser"));

		public static final StreamCodec<ByteBuf, OpenBrowser> CODEC = StreamCodec.composite(
				BlockPos.STREAM_CODEC, OpenBrowser::pos,
				Identifier.STREAM_CODEC.apply(ByteBufCodecs.list()), OpenBrowser::structures,
				ByteBufCodecs.stringUtf8(32), OpenBrowser::mode,
				ByteBufCodecs.stringUtf8(256), OpenBrowser::structure,
				ByteBufCodecs.VAR_INT, OpenBrowser::sizeX,
				ByteBufCodecs.VAR_INT, OpenBrowser::sizeY,
				ByteBufCodecs.VAR_INT, OpenBrowser::sizeZ,
				OpenBrowser::new);

		@Override
		public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
			return ID;
		}
	}

	/** S2C: refresh only the state readout of an already open browser. */
	public record SyncState(String mode, String structure, int sizeX, int sizeY, int sizeZ)
			implements CustomPacketPayload {
		public static final CustomPacketPayload.Type<SyncState> ID =
				new CustomPacketPayload.Type<>(OpenWorkMod.id("sync_state"));

		public static final StreamCodec<ByteBuf, SyncState> CODEC = StreamCodec.composite(
				ByteBufCodecs.stringUtf8(32), SyncState::mode,
				ByteBufCodecs.stringUtf8(256), SyncState::structure,
				ByteBufCodecs.VAR_INT, SyncState::sizeX,
				ByteBufCodecs.VAR_INT, SyncState::sizeY,
				ByteBufCodecs.VAR_INT, SyncState::sizeZ,
				SyncState::new);

		@Override
		public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
			return ID;
		}
	}

	/** C2S: assign a structure to the block at {@code pos}. */
	public record SelectStructure(BlockPos pos, Identifier structure) implements CustomPacketPayload {
		public static final CustomPacketPayload.Type<SelectStructure> ID =
				new CustomPacketPayload.Type<>(OpenWorkMod.id("select_structure"));

		public static final StreamCodec<ByteBuf, SelectStructure> CODEC = StreamCodec.composite(
				BlockPos.STREAM_CODEC, SelectStructure::pos,
				Identifier.STREAM_CODEC, SelectStructure::structure,
				SelectStructure::new);

		@Override
		public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
			return ID;
		}
	}

	/** C2S: run a named action against the block at {@code pos}. */
	public record RunAction(BlockPos pos, String action) implements CustomPacketPayload {
		public static final CustomPacketPayload.Type<RunAction> ID =
				new CustomPacketPayload.Type<>(OpenWorkMod.id("run_action"));

		public static final StreamCodec<ByteBuf, RunAction> CODEC = StreamCodec.composite(
				BlockPos.STREAM_CODEC, RunAction::pos,
				ByteBufCodecs.stringUtf8(32), RunAction::action,
				RunAction::new);

		@Override
		public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
			return ID;
		}
	}
}
