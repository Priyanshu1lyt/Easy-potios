package com.easygamemode;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.PlayerSkinDrawer;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableTextContent;
import net.minecraft.world.GameMode;

/**
 * Easy Gamemode GUI. Everything is drawn by hand and every change is sent as a normal
 * /gamemode command, so the server's own permission checks always apply.
 */
public class GamemodeScreen extends Screen {
	private static final int ROW_H = 24;
	private static final int BTN = 20;
	private static final int GAP = 2;
	private static final int BUTTONS_W = 4 * BTN + 3 * GAP;

	private static final GameMode[] MODES = {GameMode.SURVIVAL, GameMode.CREATIVE, GameMode.ADVENTURE, GameMode.SPECTATOR};
	private static final String[] IDS = {"survival", "creative", "adventure", "spectator"};
	private static final String[] NAMES = {"Survival", "Creative", "Adventure", "Spectator"};
	private static final int[] COLORS = {0xFFE8554E, 0xFF55AAFF, 0xFF55D66B, 0xFFB46BFF};

	private static String query = "";
	private static long lastCommandMs = 0;

	// Survival = sword, Creative = blocks, Adventure = map, Spectator = eye (same icons as F3+F4)
	private final ItemStack[] icons = {
			new ItemStack(Items.IRON_SWORD), new ItemStack(Items.GRASS_BLOCK),
			new ItemStack(Items.MAP), new ItemStack(Items.ENDER_EYE)};
	private final ItemStack everyoneIcon = new ItemStack(Items.NETHER_STAR);

	private final List<PlayerListEntry> players = new ArrayList<>();
	private TextFieldWidget search;
	private int panelX, panelY, panelW, panelH;
	private int everyoneY, listTop, listBottom;
	private double scroll = 0;
	private int tickCounter = 0;

	private String status = "";
	private boolean statusError = false;
	private Text hoverTip = null;

	public GamemodeScreen() {
		super(Text.literal("Easy Gamemode"));
	}

	@Override
	protected void init() {
		panelW = Math.min(300, width - 16);
		panelH = Math.min(height - 16, 250);
		panelX = (width - panelW) / 2;
		panelY = (height - panelH) / 2;
		everyoneY = panelY + 44;
		listTop = everyoneY + ROW_H + 4;
		listBottom = panelY + panelH - 18;

		search = new TextFieldWidget(textRenderer, panelX + 8, panelY + 22, panelW - 16, 16, Text.literal("Search"));
		search.setPlaceholder(Text.literal("Search players..."));
		search.setText(query);
		search.setChangedListener(s -> {
			query = s;
			scroll = 0;
			rebuild();
		});
		addDrawableChild(search);
		rebuild();
	}

	private void rebuild() {
		players.clear();
		ClientPlayNetworkHandler handler = client == null ? null : client.getNetworkHandler();
		if (handler != null) {
			String q = query.toLowerCase(Locale.ROOT).trim();
			for (PlayerListEntry e : handler.getPlayerList()) {
				if (q.isEmpty() || e.getProfile().name().toLowerCase(Locale.ROOT).contains(q)) {
					players.add(e);
				}
			}
			players.sort(Comparator.comparing(e -> e.getProfile().name(), String.CASE_INSENSITIVE_ORDER));
		}
		clampScroll();
	}

	private void clampScroll() {
		double max = Math.max(0, players.size() * ROW_H - (listBottom - listTop));
		scroll = Math.max(0, Math.min(scroll, max));
	}

	@Override
	public void tick() {
		super.tick();
		if (client == null || client.player == null || client.getNetworkHandler() == null) {
			close();
			return;
		}
		// Refreshes joins/leaves and picks up gamemode updates the server has sent.
		if (++tickCounter % 10 == 0) {
			rebuild();
		}
	}

	@Override
	public boolean shouldPause() {
		return false;
	}

	// ---------------------------------------------------------------- rendering

	@Override
	public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
		ctx.fill(0, 0, width, height, 0x90000000);
		ctx.fill(panelX - 1, panelY - 1, panelX + panelW + 1, panelY + panelH + 1, 0xFF3A3A3A);
		ctx.fill(panelX, panelY, panelX + panelW, panelY + panelH, 0xF0151515);
	}

	@Override
	public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
		super.render(ctx, mouseX, mouseY, delta); // background + search box
		hoverTip = null;

		ctx.drawText(textRenderer, Text.literal("Easy Gamemode"), panelX + 8, panelY + 8, 0xFFFFFFFF, true);

		// close button
		int cx = panelX + panelW - 20, cy = panelY + 5;
		boolean closeHover = mouseX >= cx && mouseX < cx + 14 && mouseY >= cy && mouseY < cy + 14;
		ctx.fill(cx, cy, cx + 14, cy + 14, closeHover ? 0xFFAA3333 : 0xFF333333);
		ctx.drawText(textRenderer, Text.literal("x"), cx + 5, cy + 3, 0xFFFFFFFF, false);

		// EVERYONE row (pinned above the scrolling list)
		drawRow(ctx, null, everyoneY, mouseX, mouseY, true);

		// player list
		ctx.enableScissor(panelX + 4, listTop, panelX + panelW - 4, listBottom);
		for (int i = 0; i < players.size(); i++) {
			int y = listTop + i * ROW_H - (int) scroll;
			if (y + ROW_H < listTop || y > listBottom) {
				continue;
			}
			drawRow(ctx, players.get(i), y, mouseX, mouseY, false);
		}
		ctx.disableScissor();

		if (players.isEmpty()) {
			ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("No players found"), panelX + panelW / 2, listTop + 8, 0xFF888888);
		}

		// status line
		if (!status.isEmpty()) {
			String shown = textRenderer.trimToWidth(status, panelW - 16);
			ctx.drawText(textRenderer, Text.literal(shown), panelX + 8, panelY + panelH - 13, statusError ? 0xFFFF6666 : 0xFFAAAAAA, false);
		}

		if (hoverTip != null) {
			ctx.drawTooltip(textRenderer, hoverTip, mouseX, mouseY);
		}
	}

	private void drawRow(DrawContext ctx, PlayerListEntry entry, int y, int mouseX, int mouseY, boolean everyone) {
		int x = panelX + 8;
		int w = panelW - 16;
		ctx.fill(x, y, x + w, y + ROW_H - 2, everyone ? 0xFF3B3114 : 0xFF222222);
		if (everyone) {
			ctx.drawBorder(x, y, w, ROW_H - 2, 0xFFFFAA00);
		}

		String name;
		if (entry == null) {
			ctx.drawItem(everyoneIcon, x + 3, y + 3);
			name = "EVERYONE";
		} else {
			PlayerSkinDrawer.draw(ctx, entry.getSkinTextures(), x + 4, y + 3, 16);
			name = entry.getProfile().name();
		}
		int nameMax = w - 24 - BUTTONS_W - 8;
		ctx.drawText(textRenderer, Text.literal(textRenderer.trimToWidth(name, nameMax)), x + 24, y + 7,
				everyone ? 0xFFFFD27F : 0xFFFFFFFF, true);

		GameMode current = entry == null ? null : entry.getGameMode();
		boolean rowVisible = everyone || (mouseY >= listTop && mouseY < listBottom);
		int hovered = rowVisible ? buttonAt(y, mouseX, mouseY) : -1;

		for (int i = 0; i < 4; i++) {
			int bx = buttonsX() + i * (BTN + GAP);
			int by = y + 1;
			boolean active = current != null && current == MODES[i];
			ctx.fill(bx, by, bx + BTN, by + BTN, hovered == i ? 0xFF555555 : 0xFF333333);
			ctx.drawItem(icons[i], bx + 2, by + 2);
			if (active) {
				ctx.fill(bx, by, bx + BTN, by + BTN, (COLORS[i] & 0x00FFFFFF) | 0x40000000);
				ctx.drawBorder(bx, by, BTN, BTN, COLORS[i]);
			} else {
				ctx.fill(bx, by, bx + BTN, by + BTN, 0x55000000);
				ctx.drawBorder(bx, by, BTN, BTN, hovered == i ? 0xFFFFFFFF : 0xFF111111);
			}
			if (hovered == i) {
				hoverTip = Text.literal(entry == null ? "Everyone: " + NAMES[i] : NAMES[i]);
			}
		}
	}

	private int buttonsX() {
		return panelX + panelW - 8 - 4 - BUTTONS_W;
	}

	private int buttonAt(int rowY, double mx, double my) {
		if (my < rowY + 1 || my >= rowY + 1 + BTN) {
			return -1;
		}
		for (int i = 0; i < 4; i++) {
			int bx = buttonsX() + i * (BTN + GAP);
			if (mx >= bx && mx < bx + BTN) {
				return i;
			}
		}
		return -1;
	}

	// -------------------------------------------------------------------- input

	@Override
	public boolean mouseClicked(Click click, boolean doubled) {
		if (super.mouseClicked(click, doubled)) {
			return true; // search box
		}
		if (click.button() != 0) {
			return false;
		}
		double mx = click.x();
		double my = click.y();

		int cx = panelX + panelW - 20, cy = panelY + 5;
		if (mx >= cx && mx < cx + 14 && my >= cy && my < cy + 14) {
			close();
			return true;
		}

		int idx = buttonAt(everyoneY, mx, my);
		if (idx >= 0) {
			send(null, idx);
			return true;
		}

		if (my >= listTop && my < listBottom) {
			for (int i = 0; i < players.size(); i++) {
				int y = listTop + i * ROW_H - (int) scroll;
				idx = buttonAt(y, mx, my);
				if (idx >= 0) {
					send(players.get(i), idx);
					return true;
				}
			}
		}
		return false;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		scroll -= verticalAmount * ROW_H;
		clampScroll();
		return true;
	}

	@Override
	public boolean keyPressed(KeyInput input) {
		// Same key again closes the GUI (unless the user is typing in the search box).
		if (!search.isFocused() && EasyGamemodeClient.getOpenKey() != null
				&& input.key() == KeyBindingHelper.getBoundKeyOf(EasyGamemodeClient.getOpenKey()).getCode()) {
			close();
			return true;
		}
		return super.keyPressed(input);
	}

	// ----------------------------------------------------------------- commands

	private void send(PlayerListEntry target, int modeIndex) {
		ClientPlayNetworkHandler handler = client == null ? null : client.getNetworkHandler();
		if (handler == null) {
			return;
		}
		if (target != null && target.getGameMode() == MODES[modeIndex]) {
			setStatus(target.getProfile().name() + " is already " + NAMES[modeIndex], false);
			return; // no pointless command
		}
		String who = target == null ? "@a" : target.getProfile().name();
		handler.sendChatCommand("gamemode " + IDS[modeIndex] + " " + who);
		lastCommandMs = System.currentTimeMillis();
		// Not assumed successful: the highlight only changes once the server updates the player list.
		setStatus("Requested " + NAMES[modeIndex] + " for " + (target == null ? "everyone" : who) + "...", false);
	}

	private void setStatus(String message, boolean error) {
		this.status = message;
		this.statusError = error;
	}

	/** Called for every system/game chat message; flags rejected /gamemode commands. */
	public static void onServerMessage(Text message) {
		if (System.currentTimeMillis() - lastCommandMs > 4000) {
			return;
		}
		if (!(MinecraftClient.getInstance().currentScreen instanceof GamemodeScreen screen)) {
			return;
		}
		if (message.getContent() instanceof TranslatableTextContent content) {
			String key = content.getKey();
			if (key.startsWith("command.unknown")) {
				screen.setStatus("Rejected: you probably need OP permission on this server.", true);
			} else if (key.startsWith("arguments.entity.notfound")) {
				screen.setStatus("Player not found.", true);
			} else if (key.startsWith("command.exception") || key.startsWith("command.failed")) {
				screen.setStatus("The server rejected the command.", true);
			}
		}
	}
}
