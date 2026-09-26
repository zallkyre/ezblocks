package dev.ezblocks.client.screen;

import dev.ezblocks.network.ModPayloads;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
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
 *
 * <p>The panel is laid out from both edges so the browser still fits small windows. Every
 * refresh goes through {@link #rebuildButtons()}, which re-creates the widgets but keeps the
 * search box focused and never re-enters its own text responder.
 */
public class StructureBrowserScreen extends Screen {
	private static final int MAX_PANEL_WIDTH = 428;
	private static final int PANEL_PADDING = 8;

	private static final int GRID_X = 10;
	private static final int GRID_Y = 72;
	private static final int GRID_GAP = 2;

	private static final int MAX_COLUMNS_ROOMY = 7;
	private static final int MAX_COLUMNS_COMPACT = 8;
	private static final int MAX_ROWS = 5;

	private static final int CELL_WIDTH_ROOMY = 56;
	private static final int CELL_HEIGHT_ROOMY = 20;
	private static final int CELL_WIDTH_COMPACT = 44;
	private static final int CELL_HEIGHT_COMPACT = 16;

	/** Rows that sit under the grid: nav gap, nav, size gap, size, action gap, action, pad. */
	private static final int BELOW_GRID = 122;

	private static final int SEARCH_ROW_Y = 28;
	private static final int SEARCH_ROW_H = 20;
	private static final int CLEAR_W = 18;
	private static final int MODE_W = 58;

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

	/** Guards the text responder while the screen writes the query back into the box. */
	private boolean syncingSearch;

	/** Cell metrics and panel size, recomputed for the current window on every init. */
	private int columns = MAX_COLUMNS_ROOMY;
	private int rows = MAX_ROWS;
	private int cellWidth = CELL_WIDTH_ROOMY;
	private int cellHeight = CELL_HEIGHT_ROOMY;
	private int stepX = CELL_WIDTH_ROOMY + GRID_GAP;
	private int stepY = CELL_HEIGHT_ROOMY + GRID_GAP;
	private int panelWidth = MAX_PANEL_WIDTH;
	private int panelHeight = 304;
	private int searchWidth = 328;

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
		super(Component.literal("EZ Blocks"));
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

	/**
	 * Fit the panel to the window.
	 *
	 * <p>Minecraft picks a GUI scale automatically, so a small window can leave very little
	 * room. Both tiers are measured and the one showing more structures wins, which keeps the
	 * roomy grid on a large window and a compact grid on a small one.
	 */
	private void computeLayout() {
		int availW = Math.max(140, this.width - PANEL_PADDING);
		int availH = Math.max(160, this.height - PANEL_PADDING);

		this.panelWidth = Math.min(MAX_PANEL_WIDTH, availW);

		int gridAvailW = this.panelWidth - 2 * GRID_X;
		int gridAvailH = availH - GRID_Y - BELOW_GRID;
		int bestCells = -1;

		for (int compact = 0; compact < 2; compact++) {
			int width = compact == 0 ? CELL_WIDTH_ROOMY : CELL_WIDTH_COMPACT;
			int height = compact == 0 ? CELL_HEIGHT_ROOMY : CELL_HEIGHT_COMPACT;
			int maxColumns = compact == 0 ? MAX_COLUMNS_ROOMY : MAX_COLUMNS_COMPACT;
			int stepX = width + GRID_GAP;
			int stepY = height + GRID_GAP;
			int cols = Math.max(1, Math.min(maxColumns, (gridAvailW + GRID_GAP) / stepX));
			int rws = Math.max(1, Math.min(MAX_ROWS, (gridAvailH + GRID_GAP) / stepY));

			if (cols * rws > bestCells) {
				bestCells = cols * rws;
				this.cellWidth = width;
				this.cellHeight = height;
				this.columns = cols;
				this.rows = rws;
			}
		}

		this.stepX = this.cellWidth + GRID_GAP;
		this.stepY = this.cellHeight + GRID_GAP;
		this.panelHeight = Math.min(availH, GRID_Y + this.rows * this.stepY + BELOW_GRID);
		this.searchWidth = Math.max(60, this.panelWidth - 2 * GRID_X - CLEAR_W - MODE_W - 6);
	}

	@Override
	protected void init() {
		active = this;
		computeLayout();

		int centerX = (this.width - this.panelWidth) / 2;
		int top = (this.height - this.panelHeight) / 2;
		int gridTop = top + GRID_Y;

		if (this.searchBox == null) {
			this.searchBox = new EditBox(this.font, centerX + GRID_X, top + SEARCH_ROW_Y, this.searchWidth,
					SEARCH_ROW_H, Component.literal("Search"));
			this.searchBox.setHint(Component.literal("Search structures..."));
			this.searchBox.setMaxLength(128);
			this.searchBox.setResponder(this::onSearchChanged);
		} else {
			this.searchBox.setX(centerX + GRID_X);
			this.searchBox.setY(top + SEARCH_ROW_Y);
			this.searchBox.setWidth(this.searchWidth);
		}

		// Writing the text back has to be muted, otherwise the responder rebuilds the screen
		// and the rebuild writes the text back again until the stack overflows.
		syncSearchBox();
		this.addRenderableWidget(this.searchBox);
		this.setInitialFocus(this.searchBox);

		int clearX = centerX + GRID_X + this.searchWidth + 3;
		this.addRenderableWidget(Button.builder(Component.literal("X"), button -> clearSearch())
				.bounds(clearX, top + SEARCH_ROW_Y, CLEAR_W, SEARCH_ROW_H)
				.tooltip(Tooltip.create(Component.literal("Clear the search")))
				.build());

		int modeX = clearX + CLEAR_W + 3;
		String modeLabel = "Mode: " + this.mode.toUpperCase(Locale.ROOT);
		this.addRenderableWidget(Button.builder(Component.literal(modeLabel),
						button -> runAction("toggle"))
				.bounds(modeX, top + SEARCH_ROW_Y, MODE_W, SEARCH_ROW_H)
				.tooltip(Tooltip.create(Component.literal("Switch between save and load mode")))
				.build());

		int perPage = this.columns * this.rows;
		List<Identifier> matches = matches();
		int maxPage = Math.max(0, (matches.size() - 1) / perPage);
		this.page = Math.min(this.page, maxPage);
		int first = this.page * perPage;

		for (int slot = 0; slot < perPage; slot++) {
			int index = first + slot;

			if (index >= matches.size()) {
				break;
			}

			Identifier id = matches.get(index);
			int column = slot % this.columns;
			int row = slot / this.columns;
			Identifier captured = id;

			this.addRenderableWidget(Button
					.builder(Component.literal(cellLabel(id)), button -> select(captured))
					.bounds(centerX + GRID_X + column * this.stepX, gridTop + row * this.stepY, this.cellWidth,
							this.cellHeight)
					.tooltip(Tooltip.create(Component.literal(id.toString())))
					.build());
		}

		int navY = gridTop + this.rows * this.stepY + 2;
		this.addRenderableWidget(Button.builder(Component.literal("<"), button -> {
			this.page = Math.max(0, this.page - 1);
			rebuildButtons();
		}).bounds(centerX + GRID_X, navY, 20, 20).build());

		this.addRenderableWidget(Button.builder(Component.literal(">"), button -> {
			this.page = Math.min(maxPage, this.page + 1);
			rebuildButtons();
		}).bounds(centerX + GRID_X + 24, navY, 20, 20).build());

		int sizeY2 = navY + 44;
		int groupGap = Math.max(4, Math.min(18, (this.panelWidth - 2 * GRID_X - 196) / 2));
		int stepperX = centerX + GRID_X;

		for (int index = 0; index < 3; index++) {
			int axis = index;
			int base = stepperX + axis * (2 * 34 + groupGap);

			this.addRenderableWidget(Button.builder(Component.literal(label(axis, false)),
							button -> adjustSize(axis, -1))
					.bounds(base, sizeY2, 30, 20).build());
			this.addRenderableWidget(Button.builder(Component.literal(label(axis, true)),
							button -> adjustSize(axis, 1))
					.bounds(base + 34, sizeY2, 30, 20).build());
		}

		int actionY = sizeY2 + 46;
		int actionGap = 5;
		int actionW = Math.max(60, (this.panelWidth - 2 * GRID_X - 3 * actionGap) / 4);
		int actionX = centerX + GRID_X;

		this.addRenderableWidget(Button.builder(Component.literal("Save"), button -> runAction("save"))
				.bounds(actionX, actionY, actionW, 20).build());
		actionX += actionW + actionGap;
		this.addRenderableWidget(Button.builder(Component.literal("Save & Place"), button -> runAction("save_place"))
				.bounds(actionX, actionY, actionW, 20).build());
		actionX += actionW + actionGap;
		this.addRenderableWidget(Button.builder(Component.literal("Auto-Size"), button -> runAction("detect_size"))
				.bounds(actionX, actionY, actionW, 20).build());
		actionX += actionW + actionGap;
		this.addRenderableWidget(Button.builder(Component.literal("Done"), button -> onClose())
				.bounds(actionX, actionY, actionW, 20).build());
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

		int centerX = (this.width - this.panelWidth) / 2;
		int top = (this.height - this.panelHeight) / 2;
		int gridTop = top + GRID_Y;

		graphics.fill(centerX, top, centerX + this.panelWidth, top + this.panelHeight, PANEL_COLOR);
		graphics.fill(centerX, top, centerX + this.panelWidth, top + 1, 0x40FFFFFF);
		graphics.fill(centerX, top + this.panelHeight - 1, centerX + this.panelWidth, top + this.panelHeight,
				0x40000000);

		graphics.text(this.font, fit(this.title.getString(), this.panelWidth - 2 * GRID_X), centerX + GRID_X,
				top + 10, TEXT_COLOR);

		String structure = this.currentStructure.isEmpty() ? "none" : this.currentStructure;
		String info = "Structure: " + structure + "     Size: " + this.sizeX + " x " + this.sizeY + " x "
				+ this.sizeZ;
		graphics.text(this.font, fit(info, this.panelWidth - 2 * GRID_X), centerX + GRID_X, top + 54, MUTED_COLOR);

		List<Identifier> matches = matches();

		if (matches.isEmpty()) {
			String message = this.query.isEmpty() ? "No structures in this world"
					: "No match for \"" + this.query + "\"";
			graphics.centeredText(this.font, fit(message, this.panelWidth - 2 * GRID_X),
					centerX + this.panelWidth / 2, gridTop + 8, MUTED_COLOR);
		}

		int perPage = this.columns * this.rows;
		int maxPage = Math.max(0, (matches.size() - 1) / perPage);
		int first = this.page * perPage;

		for (int slot = 0; slot < perPage; slot++) {
			int index = first + slot;

			if (index >= matches.size()) {
				break;
			}

			Identifier id = matches.get(index);
			int column = slot % this.columns;
			int row = slot / this.columns;
			int cellX = centerX + GRID_X + column * this.stepX;
			int cellY = gridTop + row * this.stepY;

			boolean selected = id.toString().equals(this.currentStructure);
			int border = selected ? HIGHLIGHT_COLOR : 0x505050;
			graphics.fill(cellX - 1, cellY - 1, cellX + this.cellWidth + 1, cellY + this.cellHeight + 1, border);
		}

		int navY = gridTop + this.rows * this.stepY + 2;
		String pageText = matches.isEmpty() ? "No matches"
				: "Page " + (this.page + 1) + " / " + (maxPage + 1) + "   (" + matches.size() + " of "
						+ this.allStructures.size() + ")";
		graphics.centeredText(this.font, pageText, centerX + this.panelWidth / 2, navY + 6, MUTED_COLOR);
	}

	/**
	 * Copy the current query into the box with the responder muted.
	 *
	 * <p>{@code EditBox.setValue} always notifies its responder, so this is the only safe
	 * place to write text back into the field.
	 */
	private void syncSearchBox() {
		if (this.searchBox.getValue().equals(this.query)) {
			return;
		}

		this.syncingSearch = true;

		try {
			this.searchBox.setValue(this.query);
		} finally {
			this.syncingSearch = false;
		}
	}

	private void onSearchChanged(String value) {
		this.query = value;
		this.page = 0;

		if (!this.syncingSearch) {
			rebuildButtons();
		}
	}

	private void clearSearch() {
		if (this.query.isEmpty()) {
			return;
		}

		this.query = "";
		this.page = 0;
		syncSearchBox();
		rebuildButtons();
	}

	/**
	 * Re-create every widget from the current state.
	 *
	 * <p>Focus is restored afterwards so the search box keeps accepting keystrokes, and the
	 * text responder is muted while the box is rewritten.
	 */
	private void rebuildButtons() {
		if (this.searchBox == null || this.minecraft == null) {
			return;
		}

		boolean refocus = this.getFocused() == this.searchBox;
		this.clearWidgets();
		this.init();

		if (refocus) {
			this.setFocused(this.searchBox);
		}
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

	/**
	 * Cell text for a structure.
	 *
	 * <p>Cells are narrow, so the label keeps the tail of the path, where the part that tells
	 * near identical structures apart lives. The full id is in the tooltip.
	 */
	private String cellLabel(Identifier id) {
		String path = id.getPath();
		int maxChars = Math.max(4, this.cellWidth / 4);

		if (path.length() <= maxChars) {
			return path;
		}

		return "\u2026" + path.substring(path.length() - (maxChars - 1));
	}

	private static String label(int axis, boolean up) {
		String name = switch (axis) {
			case 0 -> "X";
			case 1 -> "Y";
			default -> "Z";
		};

		return name + (up ? "+" : "-");
	}

	/** Cut a string so it never paints past the given pixel width. */
	private String fit(String text, int width) {
		if (width <= 0) {
			return "";
		}

		return this.font.plainSubstrByWidth(text, width);
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
}
