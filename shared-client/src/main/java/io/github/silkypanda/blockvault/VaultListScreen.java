package io.github.silkypanda.blockvault;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Searchable list of saved server accounts with per-server controls. */
final class VaultListScreen extends Screen {
    private static final int PAGE_SIZE = 6;
    private List<VaultRow> rows;
    private final List<Button> rowButtons = new ArrayList<>();
    private static final int ROW_ACTIONS = 6;
    private EditBox search;
    private Button previousButton;
    private Button nextButton;
    private int page;
    private int pageSize;
    private int contentLeft;
    private int contentWidth;
    private int rowLabelWidth;
    private int rowButtonWidth;
    private int footerButtonWidth;
    private String searchQuery = "";

    VaultListScreen(List<VaultRow> rows) {
        super(Component.translatable("blockvault.vault.title"));
        this.rows = List.copyOf(rows);
    }

    @Override
    protected void init() {
        rowButtons.clear();
        contentWidth = Math.max(1, Math.min(554, width - 24));
        contentLeft = (width - contentWidth) / 2;
        pageSize = pageSizeForCurrentScreen();
        page = Math.min(page, totalPages() - 1);
        rowButtonWidth = Math.max(1, Math.min(54, (contentWidth - 94) / ROW_ACTIONS));
        rowLabelWidth = Math.max(0, contentWidth - 14 - ROW_ACTIONS * rowButtonWidth);
        footerButtonWidth = Math.max(1, Math.min(88, (contentWidth - 30) / 6));
        int searchWidth = Math.max(1, Math.min(320, width - 24));
        search = new EditBox(font, (width - searchWidth) / 2, 32, searchWidth, 20,
                Component.translatable("blockvault.vault.search"));
        search.setValue(searchQuery);
        search.setHint(Component.translatable("blockvault.vault.search"));
        search.setMaxLength(128);
        search.setResponder(value -> { VaultRuntime.activity(); searchQuery = value; page = 0; refresh(); });
        addRenderableWidget(search);

        List<VaultRow> pageRows = pageRows();
        for (int index = 0; index < pageRows.size(); index++) {
            VaultRow row = pageRows.get(index);
            int y = 68 + index * 34;
            int actionsX = contentLeft + rowLabelWidth + 4;
            Button reveal = addRowButton(actionsX, y, rowButtonWidth, "blockvault.vault.reveal", button ->
                    MinecraftHolder.open(new VaultCredentialScreen(row)));
            Button copy = addRowButton(actionsX + rowButtonWidth + 2, y, rowButtonWidth, "blockvault.vault.copy", button -> VaultRuntime.copyPassword(row));
            reveal.active = !row.serverOnly();
            copy.active = !row.serverOnly();
            addRowButton(actionsX + (rowButtonWidth + 2) * 2, y, rowButtonWidth,
                    row.autofill() ? "blockvault.vault.autofill.on" : "blockvault.vault.autofill.off",
                    button -> VaultRuntime.updateServerPolicy(row, !row.autofill(), row.neverSave()));
            addRowButton(actionsX + (rowButtonWidth + 2) * 3, y, rowButtonWidth,
                    row.neverSave() ? "blockvault.vault.save.on" : "blockvault.vault.save.off",
                    button -> VaultRuntime.updateServerPolicy(row, row.autofill(), !row.neverSave()));
            addRowButton(actionsX + (rowButtonWidth + 2) * 4, y, rowButtonWidth,
                    "blockvault.vault.template.short", button ->
                    MinecraftHolder.open(new VaultTemplateScreen(row, this)));
            addRowButton(actionsX + (rowButtonWidth + 2) * 5, y, rowButtonWidth,
                    "blockvault.vault.delete.short", button ->
                    MinecraftHolder.open(new VaultDeleteScreen(row, this)));
        }
        previousButton = Button.builder(Component.translatable("blockvault.vault.previous"), button -> {
            VaultRuntime.activity();
            page = Math.max(0, page - 1);
            refresh();
        }).bounds(contentLeft, height - 34, footerButtonWidth, 20).build();
        nextButton = Button.builder(Component.translatable("blockvault.vault.next"), button -> {
            VaultRuntime.activity();
            page = Math.min(totalPages() - 1, page + 1);
            refresh();
        }).bounds(contentLeft + footerButtonWidth + 6, height - 34, footerButtonWidth, 20).build();
        previousButton.active = page > 0;
        nextButton.active = page < totalPages() - 1;
        addRenderableWidget(previousButton);
        addRenderableWidget(nextButton);
        addRenderableWidget(Button.builder(Component.translatable("blockvault.transfer.import.short"),
                        button -> { VaultRuntime.activity(); MinecraftHolder.open(new VaultTransferScreen(this, true)); })
                .bounds(contentLeft + (footerButtonWidth + 6) * 2, height - 34, footerButtonWidth, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("blockvault.transfer.export.short"),
                        button -> { VaultRuntime.activity(); MinecraftHolder.open(new VaultTransferScreen(this, false)); })
                .bounds(contentLeft + (footerButtonWidth + 6) * 3, height - 34, footerButtonWidth, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("blockvault.vault.settings.short"),
                        button -> { VaultRuntime.activity(); VaultRuntime.openSettingsScreen(this); })
                .bounds(contentLeft + (footerButtonWidth + 6) * 4, height - 34, footerButtonWidth, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("blockvault.vault.lock.short"),
                        button -> VaultRuntime.lock())
                .bounds(contentLeft + (footerButtonWidth + 6) * 5, height - 34, footerButtonWidth, 20).build());
        updateVisibleRows();
    }

    private Button addRowButton(int x, int y, int buttonWidth, String key, Button.OnPress onPress) {
        Button button = Button.builder(Component.translatable(key), pressed -> {
            VaultRuntime.activity();
            onPress.onPress(pressed);
        }).bounds(x, y, buttonWidth, 20).build();
        rowButtons.add(button);
        addRenderableWidget(button);
        return button;
    }

    private List<VaultRow> pageRows() {
        List<VaultRow> filtered = filteredRows();
        int from = page * pageSize;
        if (from >= filtered.size()) return List.of();
        return filtered.subList(from, Math.min(filtered.size(), from + pageSize));
    }

    private int totalPages() { return Math.max(1, (filteredRows().size() + pageSize - 1) / pageSize); }

    private int pageSizeForCurrentScreen() { return Math.max(1, Math.min(PAGE_SIZE, (height - 122) / 34)); }

    private void updateVisibleRows() {
        String query = search == null ? "" : search.getValue().toLowerCase(java.util.Locale.ROOT);
        List<VaultRow> visible = pageRows();
        for (int rowIndex = 0; rowIndex < visible.size(); rowIndex++) {
            VaultRow row = visible.get(rowIndex);
            boolean matches = (row.serverName() + " " + row.host() + " " + row.username())
                    .toLowerCase(java.util.Locale.ROOT).contains(query);
            for (int column = 0; column < ROW_ACTIONS; column++) rowButtons.get(rowIndex * ROW_ACTIONS + column).visible = matches;
        }
    }

    private List<VaultRow> filteredRows() {
        String query = search == null ? "" : search.getValue().toLowerCase(java.util.Locale.ROOT);
        return rows.stream().filter(row -> searchableText(row).contains(query)).toList();
    }

    private String searchableText(VaultRow row) {
        String value = BlockVaultConfig.current().streamerMode()
                ? row.username() : row.serverName() + " " + row.host() + " " + row.username();
        return value.toLowerCase(java.util.Locale.ROOT);
    }

    private void refresh() {
        rebuildWidgets();
    }

    void updateRows(List<VaultRow> updated) {
        rows = List.copyOf(updated);
        page = Math.min(page, totalPages() - 1);
        refresh();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        graphics.centeredText(font, title, width / 2, 12, 0xffffffff);
        List<VaultRow> visible = pageRows();
        String query = search == null ? "" : search.getValue().toLowerCase(java.util.Locale.ROOT);
        for (int rowIndex = 0; rowIndex < visible.size(); rowIndex++) {
            VaultRow row = visible.get(rowIndex);
            if (!searchableText(row).contains(query)) continue;
            int y = 72 + rowIndex * 34;
            String serverName = BlockVaultConfig.current().streamerMode()
                    ? Component.translatable("blockvault.settings.streamer.hidden").getString() : row.serverName();
            graphics.text(font, Component.literal(clip(serverName, rowLabelWidth)), contentLeft, y, 0xffffffff);
            String accountLine = row.serverOnly() ? Component.translatable("blockvault.vault.server.settings").getString()
                    : row.username() + " · " + row.status().name();
            graphics.text(font, Component.literal(clip(accountLine, rowLabelWidth)),
                    contentLeft, y + 12, 0xffb9b4c5);
        }
        if (rows.isEmpty()) graphics.centeredText(font, Component.translatable("blockvault.vault.empty"), width / 2, 80, 0xffcccccc);
        graphics.centeredText(font, Component.translatable("blockvault.vault.page",
                Math.min(page + 1, totalPages()), totalPages()), width / 2, height - 50, 0xffaaaaaa);
    }

    private static String clip(String value, int maxPixels) {
        int maxChars = Math.max(0, maxPixels / 6);
        return value.length() <= maxChars ? value : maxChars < 2 ? "…" : value.substring(0, maxChars - 1) + "…";
    }

    @Override public boolean isPauseScreen() { return false; }

    private static final class MinecraftHolder {
        private static void open(Screen screen) { net.minecraft.client.Minecraft.getInstance().setScreenAndShow(screen); }
    }
}
