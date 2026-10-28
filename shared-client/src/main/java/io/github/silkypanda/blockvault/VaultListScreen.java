package io.github.silkypanda.blockvault;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Searchable list of saved server accounts with per-server controls. */
final class VaultListScreen extends Screen {
    private static final int PAGE_SIZE = 6;
    private List<VaultRow> rows;
    private final List<Button> rowButtons = new ArrayList<>();
    private static final int ROW_ACTIONS = 5;
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
        rowButtonWidth = Math.max(1, Math.min(80, (contentWidth - 86) / ROW_ACTIONS));
        rowLabelWidth = Math.max(0, contentWidth - 14 - ROW_ACTIONS * rowButtonWidth);
        footerButtonWidth = Math.max(1, Math.min(88, (contentWidth - 42) / 8));
        int searchWidth = Math.max(1, Math.min(320, width - 24));
        search = new EditBox(font, (width - searchWidth) / 2, 32, searchWidth, 20,
                Component.translatable("blockvault.vault.search"));
        search.setValue(searchQuery);
        search.setHint(Component.translatable("blockvault.vault.search"));
        search.setMaxLength(128);
        search.setResponder(value -> { searchQuery = value; page = 0; updateVisibleRows(); });
        addRenderableWidget(search);

        for (int index = 0; index < pageSize; index++) {
            int y = 68 + index * 34;
            int actionsX = contentLeft + rowLabelWidth + 4;
            addRowButton(index, actionsX, y, "blockvault.vault.reveal", null, row ->
                    MinecraftHolder.open(new VaultCredentialScreen(row)));
            addRowButton(index, actionsX + rowButtonWidth + 2, y, "blockvault.vault.copy", null, VaultRuntime::copyPassword);
            addRowButton(index, actionsX + (rowButtonWidth + 2) * 2, y, "blockvault.vault.autofill.off",
                    "blockvault.vault.tooltip.autofill", row ->
                    VaultRuntime.updateServerAutofill(row, !row.autofill()));
            addRowButton(index, actionsX + (rowButtonWidth + 2) * 3, y, "blockvault.vault.template.short",
                    "blockvault.vault.tooltip.template", row ->
                    MinecraftHolder.open(new VaultTemplateScreen(row, this)));
            addRowButton(index, actionsX + (rowButtonWidth + 2) * 4, y, "blockvault.vault.delete.short",
                    "blockvault.vault.tooltip.trash", row ->
                    MinecraftHolder.open(new VaultDeleteScreen(row, this)));
        }
        previousButton = Button.builder(Component.translatable("blockvault.vault.previous"), button -> {
            page = Math.max(0, page - 1);
            updateVisibleRows();
        }).bounds(contentLeft, height - 34, footerButtonWidth, 20).build();
        nextButton = Button.builder(Component.translatable("blockvault.vault.next"), button -> {
            page = Math.min(totalPages() - 1, page + 1);
            updateVisibleRows();
        }).bounds(contentLeft + footerButtonWidth + 6, height - 34, footerButtonWidth, 20).build();
        previousButton.active = page > 0;
        nextButton.active = page < totalPages() - 1;
        addRenderableWidget(previousButton);
        addRenderableWidget(nextButton);
        addRenderableWidget(Button.builder(Component.translatable("blockvault.transfer.import.short"),
                        button -> { MinecraftHolder.open(new VaultTransferScreen(this, true)); })
                .bounds(contentLeft + (footerButtonWidth + 6) * 2, height - 34, footerButtonWidth, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("blockvault.transfer.export.short"),
                        button -> { MinecraftHolder.open(new VaultTransferScreen(this, false)); })
                .bounds(contentLeft + (footerButtonWidth + 6) * 3, height - 34, footerButtonWidth, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("blockvault.vault.add.short"),
                        button -> MinecraftHolder.open(new VaultAddScreen(this)))
                .bounds(contentLeft + (footerButtonWidth + 6) * 4, height - 34, footerButtonWidth, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("blockvault.vault.lock.short"),
                        button -> VaultRuntime.lock())
                .bounds(contentLeft + (footerButtonWidth + 6) * 6, height - 34, footerButtonWidth, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("blockvault.vault.trash.short"),
                        button -> VaultRuntime.openTrashScreen(this))
                .bounds(contentLeft + (footerButtonWidth + 6) * 5, height - 34, footerButtonWidth, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("blockvault.vault.back"),
                        button -> onClose())
                .bounds(contentLeft + (footerButtonWidth + 6) * 7, height - 34, footerButtonWidth, 20).build());
        updateVisibleRows();
    }

    private Button addRowButton(int rowIndex, int x, int y, String key, String tooltipKey,
                                java.util.function.Consumer<VaultRow> action) {
        Button button = Button.builder(Component.translatable(key), pressed -> {
            List<VaultRow> currentRows = pageRows();
            if (rowIndex < currentRows.size()) action.accept(currentRows.get(rowIndex));
        }).bounds(x, y, rowButtonWidth, 20).tooltip(tooltipKey == null ? null
                : Tooltip.create(Component.translatable(tooltipKey))).build();
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
        List<VaultRow> visible = pageRows();
        for (int rowIndex = 0; rowIndex < pageSize; rowIndex++) {
            boolean hasRow = rowIndex < visible.size();
            VaultRow row = hasRow ? visible.get(rowIndex) : null;
            for (int column = 0; column < ROW_ACTIONS; column++) {
                Button button = rowButtons.get(rowIndex * ROW_ACTIONS + column);
                button.visible = hasRow;
                button.active = hasRow && (!row.serverOnly() || column >= 2 && column <= 3);
                if (hasRow && column == 2) {
                    button.setMessage(Component.translatable(row.autofill()
                            ? "blockvault.vault.autofill.on" : "blockvault.vault.autofill.off"));
                    button.setTooltip(Tooltip.create(Component.translatable(row.autofill()
                            ? "blockvault.vault.tooltip.autofill.on" : "blockvault.vault.tooltip.autofill.off")));
                }
            }
        }
        if (previousButton != null) previousButton.active = page > 0;
        if (nextButton != null) nextButton.active = page < totalPages() - 1;
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

    void updateRows(List<VaultRow> updated) {
        rows = List.copyOf(updated);
        page = Math.min(page, totalPages() - 1);
        updateVisibleRows();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        List<VaultRow> visible = pageRows();
        for (int rowIndex = 0; rowIndex < visible.size(); rowIndex++) {
            int top = 66 + rowIndex * 34;
            graphics.fill(contentLeft, top, contentLeft + contentWidth, top + 30, 0x66201d2b);
            graphics.fill(contentLeft, top, contentLeft + 2, top + 30, 0xff6e58a5);
        }
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        graphics.centeredText(font, title, width / 2, 12, 0xffffffff);
        graphics.text(font, Component.translatable("blockvault.vault.description"), contentLeft, 56, 0xffb9b4c5);
        String query = search == null ? "" : search.getValue().toLowerCase(java.util.Locale.ROOT);
        for (int rowIndex = 0; rowIndex < visible.size(); rowIndex++) {
            VaultRow row = visible.get(rowIndex);
            if (!searchableText(row).contains(query)) continue;
            int y = 72 + rowIndex * 34;
            String serverName = BlockVaultConfig.current().streamerMode()
                    ? Component.translatable("blockvault.settings.streamer.hidden").getString() : row.serverName();
            graphics.text(font, Component.literal(clip(serverName, rowLabelWidth)), contentLeft, y, 0xffffffff);
            String accountLine;
            if (row.serverOnly()) {
                accountLine = Component.translatable("blockvault.vault.server.settings").getString();
            } else if (row.status() == null) {
                accountLine = Component.translatable("blockvault.vault.status.unknown").getString();
            } else {
                String status = Component.translatable("blockvault.vault.status." +
                        row.status().name().toLowerCase(java.util.Locale.ROOT)).getString();
                accountLine = row.username() + " · " + status;
            }
            graphics.text(font, Component.literal(clip(accountLine, rowLabelWidth)),
                    contentLeft, y + 12, 0xffb9b4c5);
        }
        if (rows.isEmpty()) graphics.centeredText(font, Component.translatable("blockvault.vault.empty"), width / 2, 80, 0xffcccccc);
        else if (visible.isEmpty()) graphics.centeredText(font, Component.translatable("blockvault.vault.no_matches"),
                width / 2, 80, 0xffcccccc);
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
