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
    private final List<VaultRow> rows;
    private final List<Button> rowButtons = new ArrayList<>();
    private static final int ROW_ACTIONS = 6;
    private EditBox search;
    private int page;

    VaultListScreen(List<VaultRow> rows) {
        super(Component.translatable("blockvault.vault.title"));
        this.rows = List.copyOf(rows);
    }

    @Override
    protected void init() {
        rowButtons.clear();
        search = new EditBox(font, width / 2 - 160, 32, 320, 20, Component.translatable("blockvault.vault.search"));
        search.setHint(Component.translatable("blockvault.vault.search"));
        search.setMaxLength(128);
        search.setResponder(ignored -> { VaultRuntime.activity(); updateVisibleRows(); });
        addRenderableWidget(search);

        int left = width / 2 - 300;
        List<VaultRow> pageRows = pageRows();
        for (int index = 0; index < pageRows.size(); index++) {
            VaultRow row = pageRows.get(index);
            int y = 68 + index * 34;
            addRowButton(left + 220, y, 54, "blockvault.vault.reveal", button ->
                    MinecraftHolder.open(new VaultCredentialScreen(row)));
            addRowButton(left + 276, y, 54, "blockvault.vault.copy", button -> VaultRuntime.copyPassword(row));
            addRowButton(left + 332, y, 54, row.autofill() ? "blockvault.vault.autofill.on" : "blockvault.vault.autofill.off",
                    button -> VaultRuntime.updateServerPolicy(row, !row.autofill(), row.neverSave()));
            addRowButton(left + 388, y, 54, row.neverSave() ? "blockvault.vault.save.on" : "blockvault.vault.save.off",
                    button -> VaultRuntime.updateServerPolicy(row, row.autofill(), !row.neverSave()));
            addRowButton(left + 444, y, 54, "blockvault.vault.template.short", button ->
                    MinecraftHolder.open(new VaultTemplateScreen(row, this)));
            addRowButton(left + 500, y, 54, "blockvault.vault.delete.short", button ->
                    MinecraftHolder.open(new VaultDeleteScreen(row)));
        }
        addRenderableWidget(Button.builder(Component.translatable("blockvault.vault.previous"), button -> {
            VaultRuntime.activity();
            page = Math.max(0, page - 1);
            refresh();
        }).bounds(left, height - 34, 78, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("blockvault.vault.next"), button -> {
            VaultRuntime.activity();
            page++;
            refresh();
        }).bounds(left + 84, height - 34, 78, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("blockvault.vault.settings"),
                        button -> { VaultRuntime.activity(); VaultRuntime.openSettingsScreen(this); })
                .bounds(width / 2 - 100, height - 34, 120, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("blockvault.vault.lock"),
                        button -> VaultRuntime.lock())
                .bounds(width / 2 + 26, height - 34, 120, 20).build());
        updateVisibleRows();
    }

    private void addRowButton(int x, int y, int buttonWidth, String key, Button.OnPress onPress) {
        Button button = Button.builder(Component.translatable(key), pressed -> {
            VaultRuntime.activity();
            onPress.onPress(pressed);
        }).bounds(x, y, buttonWidth, 20).build();
        rowButtons.add(button);
        addRenderableWidget(button);
    }

    private List<VaultRow> pageRows() {
        int from = page * PAGE_SIZE;
        if (from >= rows.size()) return List.of();
        return rows.subList(from, Math.min(rows.size(), from + PAGE_SIZE));
    }

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

    private void refresh() {
        rebuildWidgets();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        graphics.centeredText(font, title, width / 2, 12, 0xffffffff);
        List<VaultRow> visible = pageRows();
        String query = search == null ? "" : search.getValue().toLowerCase(java.util.Locale.ROOT);
        int left = width / 2 - 300;
        for (int rowIndex = 0; rowIndex < visible.size(); rowIndex++) {
            VaultRow row = visible.get(rowIndex);
            if (!(row.serverName() + " " + row.host() + " " + row.username()).toLowerCase(java.util.Locale.ROOT).contains(query)) continue;
            int y = 72 + rowIndex * 34;
            graphics.text(font, Component.literal(row.serverName()), left, y, 0xffffffff);
            graphics.text(font, Component.literal(row.username() + " · " + row.status().name()), left, y + 12, 0xffb9b4c5);
        }
        if (rows.isEmpty()) graphics.centeredText(font, Component.translatable("blockvault.vault.empty"), width / 2, 80, 0xffcccccc);
        graphics.centeredText(font, Component.translatable("blockvault.vault.page", page + 1,
                Math.max(1, (rows.size() + PAGE_SIZE - 1) / PAGE_SIZE)), width / 2, height - 50, 0xffaaaaaa);
    }

    @Override public boolean isPauseScreen() { return false; }

    private static final class MinecraftHolder {
        private static void open(Screen screen) { net.minecraft.client.Minecraft.getInstance().setScreenAndShow(screen); }
    }
}
