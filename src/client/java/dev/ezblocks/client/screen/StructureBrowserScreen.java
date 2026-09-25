package dev.ezblocks.client.screen;

import dev.ezblocks.network.ModPayloads;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The EZ Blocks Structure Block browser.
 *
 * <p>Opened by sneak right clicking a Structure Block. Searching filters the full world
 * registry of structures, the grid pages through the matches, and the action row drives the
 * server side operations.
 */
public class StructureBrowserScreen extends Screen {
	private static final int PANEL_WIDTH = 428;
	private static final int PANEL_HEIGHT = 264;

	private static final int COLUMNS = 7;
	private static final int ROWS = 5;
	private static final int CELL_WIDTH = 56;
	private static final int CELL_HEIGHT = 20;
	private static final int STEP_X = CELL_WIDTH + 2;
	private static final int STEP_Y = CELL_HEIGHT + 2;
	private static final int GRID_X = 10;
	private static final int GRID_Y = 72;

	private static final int TEXT_COLOR = 0xE0E0E0;
	private static final int MUTED_COLOR = 0x9A9A9A;
	private static final int HIGHLIGHT_COLOR = 0x55FF55;
	private static final int PANEL_COLOR = 0xC0101010;

	private final BlockPos pos;
	private final List<Identifier> allStructures;

	private String query = "";
	private int page;
	private String mode = "load";
	private String currentStructure = "";
	private int sizeX;
	private int sizeY;
	private int sizeZ;

	private EditBox searchBox;

	/**
	 * The browser currently on screen, tracked here because 26.3 exposes no public
	 * accessor for the active screen on {@link net.minecraft.client.Minecraft}.
	 */
	private static StructureBrowserScreen active;

	/** The browser currently on screen, or null. */
	public static StructureBrowserScreen active() {
		return active;
	}

	public StructureBrowserScreen(BlockPos pos, List<Identifier> structures, String mode, String currentStructure,
			int sizeX, int sizeY, int sizeZ) {
		super(Component.literal("EZ Blocks - Structure Block"));
		this.pos = pos;
		this.allStructures = structures;
		this.mode = mode;
		this.currentStructure = currentStructure;
		this.sizeX = sizeX;
		this.sizeY = sizeY;
		this.sizeZ = sizeZ;
	}

	/** Apply a server side state refresh without reopening the browser. */
	public void applySyncState(String mode, String currentStructure, int sizeX, int sizeY, int sizeZ) {
		this.mode = mode;
		this.currentStructure = currentStructure;
		this.sizeX = sizeX;
		this.sizeY = sizeY;
		this.sizeZ = sizeZ;
		rebuildButtons();
	}

	@Override
	protected void init() {
		active = this;

		int centerX = (this.width - PANEL_WIDTH) / 2;
		int top = (this.height - PANEL_HEIGHT) / 2;

		if (this.searchBox == null) {
			this.searchBox = new EditBox(this.font, centerX + 10, top + 28, 408, 20, Component.literal("Search"));
			this.searchBox.setHint(Component.literal("Search structures..."));
			this.searchBox.setMaxLength(128);
			this.searchBox.setResponder(value -> {
				this.query = value;
				this.page = 0;
				rebuildButtons();
			});
		} else {
			this.searchBox.setPosition(centerX + 10, top + 28);
		}

		this.searchBox.setValue(this.query);
		this.addRenderableWidget(this.searchBox);
		this.setInitialFocus(this.searchBox);

		int gridTop = top + GRID_Y;
		List<Identifier> matches = matches();
		int maxPage = Math.max(0, (matches.size() - 1) / (COLUMNS * ROWS));
		this.page = Math.min(this.page, maxPage);
		int first = this.page * COLUMNS * ROWS;

		for (int slot = 0; slot < COLUMNS * ROWS; slot++) {
			int index = first + slot;

			if (index >= matches.size()) {
				break;
			}

			Identifier id = matches.get(index);
			int column = slot % COLUMNS;
			int row = slot / COLUMNS;
			Identifier captured = id;

			this.addRenderableWidget(Button.builder(Component.literal(shortName(id)), button -> select(captured))
					.bounds(centerX + GRID_X + column * STEP_X, gridTop + row * STEP_Y, CELL_WIDTH, CELL_HEIGHT)
					.build());
		}

		int navY = gridTop + ROWS * STEP_Y + 2;
		this.addRenderableWidget(Button.builder(Component.literal("<"), button -> {
			this.page = Math.max(0, this.page - 1);
			rebuildButtons();
		}).bounds(centerX + 10, navY, 20, 20).build());

		this.addRenderableWidget(Button.builder(Component.literal(">"), button -> {
			this.page = Math.min(maxPage, this.page + 1);
			rebuildButtons();
		}).bounds(centerX + 34, navY, 20, 20).build());

		int sizeY2 = navY + 24;
		this.addRenderableWidget(Button.builder(Component.literal("X-"), button -> adjustSize(0, -1))
				.bounds(centerX + 10, sizeY2, 30, 20).build());
		this.addRenderableWidget(Button.builder(Component.literal("X+"), button -> adjustSize(0, 1))
				.bounds(centerX + 44, sizeY2, 30, 20).build());
		this.addRenderableWidget(Button.builder(Component.literal("Y-"), button -> adjustSize(1, -1))
				.bounds(centerX + 92, sizeY2, 30, 20).build());
		this.addRenderableWidget(Button.builder(Component.literal("Y+"), button -> adjustSize(1, 1))
				.bounds(centerX + 126, sizeY2, 30, 20).build());
		this.addRenderableWidget(Button.builder(Component.literal("Z-"), button -> adjustSize(2, -1))
				.bounds(centerX + 174, sizeY2, 30, 20).build());
		this.addRenderableWidget(Button.builder(Component.literal("Z+"), button -> adjustSize(2, 1))
				.bounds(centerX + 208, sizeY2, 30, 20).build());

		int actionY = sizeY2 + 26;
		this.addRenderableWidget(Button.builder(Component.literal("Save"), button -> runAction("save"))
				.bounds(centerX + 10, actionY, 100, 20).build());
		this.addRenderableWidget(Button.builder(Component.literal("Save & Place"), button -> runAction("save_place"))
				.bounds(centerX + 116, actionY, 120, 20).build());
		this.addRenderableWidget(Button.builder(Component.literal("Auto-Size"), button -> runAction("detect_size"))
				.bounds(centerX + 242, actionY, 100, 20).build());
		this.addRenderableWidget(Button.builder(Component.literal("Done"), button -> onClose())
				.bounds(centerX + 348, actionY, 70, 20).build());
	}

	@Override
	public void removed() {
		if (active == this) {
			active = null;
		}

		super.removed();
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);

		int centerX = (this.width - PANEL_WIDTH) / 2;
		int top = (this.height - PANEL_HEIGHT) / 2;

		graphics.fill(centerX, top, centerX + PANEL_WIDTH, top + PANEL_HEIGHT, PANEL_COLOR);

		graphics.text(this.font, this.title, centerX + 10, top + 10, TEXT_COLOR);

		String structure = this.currentStructure.isEmpty() ? "none" : this.currentStructure;
		String info = "Mode: " + this.mode.toUpperCase(Locale.ROOT) + "    Structure: " + structure
				+ "    Size: " + this.sizeX + " x " + this.sizeY + " x " + this.sizeZ;
		graphics.text(this.font, info, centerX + 10, top + 54, MUTED_COLOR);

		int gridTop = top + GRID_Y;
		List<Identifier> matches = matches();
		int maxPage = Math.max(0, (matches.size() - 1) / (COLUMNS * ROWS));
		int first = this.page * COLUMNS * ROWS;

		for (int slot = 0; slot < COLUMNS * ROWS; slot++) {
			int index = first + slot;

			if (index >= matches.size()) {
				break;
			}

			Identifier id = matches.get(index);
			int column = slot % COLUMNS;
			int row = slot / COLUMNS;
			int cellX = centerX + GRID_X + column * STEP_X;
			int cellY = gridTop + row * STEP_Y;

			boolean selected = id.toString().equals(this.currentStructure);
			int border = selected ? HIGHLIGHT_COLOR : 0x505050;
			graphics.fill(cellX - 1, cellY - 1, cellX + CELL_WIDTH + 1, cellY + CELL_HEIGHT + 1, border);
		}

		int navY = gridTop + ROWS * STEP_Y + 2;
		String pageText = matches.isEmpty() ? "No matches" : "Page " + (this.page + 1) + " / " + (maxPage + 1)
				+ "   (" + matches.size() + " of " + this.allStructures.size() + ")";
		graphics.centeredText(this.font, pageText, centerX + PANEL_WIDTH / 2, navY + 6, MUTED_COLOR);
	}

	private List<Identifier> matches() {
		if (this.query.isBlank()) {
			return this.allStructures;
		}

		String needle = this.query.toLowerCase(Locale.ROOT);
		List<Identifier> result = new ArrayList<>();

		for (Identifier id : this.allStructures) {
			if (id.toString().toLowerCase(Locale.ROOT).contains(needle)) {
				result.add(id);
			}
		}

		return result;
	}

	private static String shortName(Identifier id) {
		String path = id.getPath();

		if (path.length() > 9) {
			return path.substring(0, 8) + "\u2026";
		}

		return path;
	}

	private void select(Identifier id) {
		ClientPlayNetworking.send(new ModPayloads.SelectStructure(this.pos, id));
		this.currentStructure = id.toString();
		this.mode = "load";
	}

	private void adjustSize(int axis, int delta) {
		ClientPlayNetworking.send(new ModPayloads.RunAction(this.pos, "size:" + axis + ":" + delta));
	}

	private void runAction(String action) {
		ClientPlayNetworking.send(new ModPayloads.RunAction(this.pos, action));
	}

	private void rebuildButtons() {
		if (this.searchBox != null) {
			clearWidgets();
			init();
		}
	}
}
