package dev.ezblocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.StructureBlockEntity;
import net.minecraft.world.level.block.state.properties.StructureMode;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.List;

/**
 * Server side implementation of every Structure Block operation EZ Blocks exposes.
 *
 * <p>All methods are safe to call with an arbitrary position; they simply do nothing when the
 * target is not a Structure Block.
 */
public final class StructureActions {
	public static final String ACTION_SAVE = "save";
	public static final String ACTION_SAVE_PLACE = "save_place";
	public static final String ACTION_DETECT_SIZE = "detect_size";
	public static final String ACTION_TOGGLE = "toggle";

	/** Axis selectors used by the size stepper actions. */
	public static final int AXIS_X = 0;
	public static final int AXIS_Y = 1;
	public static final int AXIS_Z = 2;

	/** Reach in blocks, matching the vanilla Structure Block. */
	private static final double MAX_REACH = 4.5;

	private StructureActions() {
	}

	/** Every structure the world knows about, sorted by id for a stable browser listing. */
	public static List<Identifier> listStructures(ServerLevel level) {
		var registry = level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.STRUCTURE);

		return registry.keySet().stream()
				.sorted(Comparator.comparing(Identifier::toString))
				.toList();
	}

	private static void reply(ServerPlayer player, String message) {
		player.sendSystemMessage(Component.literal("[EZ] " + message));
	}

	/**
	 * Validate that a player may operate on the Structure Block at {@code pos} and return it.
	 *
	 * <p>Returns null after telling the player why the action was refused. Every entry point
	 * goes through here, so a modified client cannot drive a block it does not own, cannot
	 * reach, or is not allowed to save. Distance and build checks apply to all actions;
	 * {@code needsCreative} mirrors vanilla, which only lets creative players write a template.
	 */
	private static StructureBlockEntity target(ServerPlayer player, BlockPos pos, boolean needsCreative) {
		ServerLevel level = player.level();

		if (!(level.getBlockEntity(pos) instanceof StructureBlockEntity be)) {
			return null;
		}

		if (player.distanceToSqr(Vec3.atCenterOf(pos)) > MAX_REACH * MAX_REACH) {
			reply(player, "Too far away.");

			return null;
		}

		if (!player.mayBuild()) {
			reply(player, "You cannot edit blocks in this game mode.");

			return null;
		}

		if (needsCreative && !player.isCreative()) {
			reply(player, "Saving a structure needs creative mode.");

			return null;
		}

		return be;
	}

	/** Flip a plain Structure Block between save and load without opening any screen. */
	public static void toggleMode(ServerPlayer player, BlockPos pos) {
		StructureBlockEntity be = target(player, pos, false);

		if (be == null) {
			return;
		}

		StructureMode mode = be.getMode();

		if (mode == StructureMode.CORNER || mode == StructureMode.DATA) {
			reply(player, "Corner and Data blocks cannot be toggled.");

			return;
		}

		StructureMode next = mode == StructureMode.SAVE ? StructureMode.LOAD : StructureMode.SAVE;
		be.setMode(next);
		reply(player, "Mode is now " + next.getSerializedName() + ".");
	}

	/** Point the block at a structure and pull its bounding box, rotation and mirror from it. */
	public static boolean select(ServerPlayer player, BlockPos pos, Identifier structure) {
		ServerLevel level = player.level();
		StructureBlockEntity be = target(player, pos, false);

		if (be == null) {
			return false;
		}

		if (!level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.STRUCTURE)
				.containsKey(structure)) {
			reply(player, "Unknown structure " + structure + ".");

			return false;
		}

		be.setStructureName(structure);
		be.setMode(StructureMode.LOAD);

		if (!be.loadStructureInfo(level)) {
			reply(player, "Could not read " + structure + ".");

			return false;
		}

		reply(player, "Loaded " + structure + ".");

		return true;
	}

	/** Grow or shrink the save bounding box on one axis. */
	public static void resize(ServerPlayer player, BlockPos pos, int axis, int delta) {
		StructureBlockEntity be = target(player, pos, false);

		if (be == null) {
			return;
		}

		Vec3i size = be.getStructureSize();
		int x = clampAxis(axis == AXIS_X ? size.getX() + delta : size.getX());
		int y = clampAxis(axis == AXIS_Y ? size.getY() + delta : size.getY());
		int z = clampAxis(axis == AXIS_Z ? size.getZ() + delta : size.getZ());
		be.setStructureSize(new Vec3i(x, y, z));
	}

	private static int clampAxis(int value) {
		return Math.max(1, Math.min(StructureBlockEntity.MAX_SIZE_PER_AXIS, value));
	}

	/** Scan the world for paired Structure Blocks and adopt the resulting bounding box. */
	public static void detectSize(ServerPlayer player, BlockPos pos) {
		ServerLevel level = player.level();
		StructureBlockEntity be = target(player, pos, false);

		if (be == null) {
			return;
		}

		if (be.detectSize()) {
			Vec3i size = be.getStructureSize();
			reply(player, "Detected size " + size.getX() + " x " + size.getY() + " x " + size.getZ() + ".");
		} else {
			reply(player, "Could not detect a size. Place a second Structure Block to mark the area.");
		}
	}

	/** Save the marked region as a template, keeping the block in save mode. */
	public static boolean save(ServerPlayer player, BlockPos pos) {
		StructureBlockEntity be = target(player, pos, true);

		if (be == null) {
			return false;
		}

		if (!be.saveStructure(true)) {
			reply(player, "Save failed. The region is empty or larger than 48 blocks per axis.");

			return false;
		}

		reply(player, "Saved " + be.getStructureName() + ".");

		return true;
	}

	/** Save the region and immediately stamp it back into the world. */
	public static boolean saveAndPlace(ServerPlayer player, BlockPos pos) {
		ServerLevel level = player.level();
		StructureBlockEntity be = target(player, pos, true);

		if (be == null) {
			return false;
		}

		be.setMode(StructureMode.SAVE);

		if (be.getStructureSize().getX() <= 0 || be.getStructureSize().getY() <= 0 || be.getStructureSize().getZ() <= 0) {
			be.detectSize();
		}

		if (!be.saveStructure(true)) {
			reply(player, "Save failed. The region is empty or larger than 48 blocks per axis.");

			return false;
		}

		be.setMode(StructureMode.LOAD);
		be.placeStructure(level);
		reply(player, "Saved and placed " + be.getStructureName() + ".");

		return true;
	}
}
