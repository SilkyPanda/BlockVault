package io.github.silkypanda.blockvault;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Deleted entries can be restored, deleted for good, or expire after 60 days. */
final class VaultTrashScreen extends Screen {
    private static final int ROWS_PER_PAGE = 5;
    private final Screen parent;
    private List<TrashRow> rows;
    private final List<Button> restoreButtons = new ArrayList<>();
    private final List<Button> deleteButtons = new ArrayList<>();
    private Button previousButton;
    private Button nextButton;
    private Button emptyButton;
    private boolean confirmEmpty;
    private String confirmDeleteId;
    private int page;
    private int contentLeft;
    private int contentWidth;

    VaultTrashScreen(Screen parent, List<TrashRow> rows) {
        super(Component.translatable("blockvault.trash.title"));
        this.parent = parent;
        this.rows = List.copyOf(rows);
    }

    @Override
    protected void init() {
        restoreButtons.clear();
        deleteButtons.clear();
        contentWidth = Math.max(1, Math.min(554, width - 24));
        contentLeft = (width - contentWidth) / 2;
        int buttonWidth = Math.max(1, Math.min(70, (contentWidth - 60) / 4));
        int actionsX = contentLeft + contentWidth - (buttonWidth * 2 + 6);
        for (int index = 0; index < ROWS_PER_PAGE; index++) {
            int y = 40 + index * 34;
            int rowIndex = index;
            Button restore = Button.builder(Component.translatable("blockvault.trash.restore"),
                    button -> withRow(rowIndex, row -> VaultRuntime.restoreFromTrash(row.id(), this::update)))
                    .bounds(actionsX, y, buttonWidth, 20).build();
            Button delete = Button.builder(Component.translatable("blockvault.trash.delete"),
                    button -> withRow(rowIndex, row -> deleteRow(row)))
                    .bounds(actionsX + buttonWidth + 6, y, buttonWidth, 20).build();
            restoreButtons.add(restore);
            deleteButtons.add(delete);
            addRenderableWidget(restore);
            addRenderableWidget(delete);
        }
        int footer = Math.max(1, Math.min(88, (contentWidth - 18) / 4));
        int footerTotal = footer * 4 + 6 * 3;
        int footerLeft = contentLeft + (contentWidth - footerTotal) / 2;
        previousButton = Button.builder(Component.translatable("blockvault.vault.previous"), button -> {
            page = Math.max(0, page - 1);
            confirmDeleteId = null;
            refreshButtons();
        }).bounds(footerLeft, height - 34, footer, 20).build();
        nextButton = Button.builder(Component.translatable("blockvault.vault.next"), button -> {
            page = Math.min(totalPages() - 1, page + 1);
            confirmDeleteId = null;
            refreshButtons();
        }).bounds(footerLeft + footer + 6, height - 34, footer, 20).build();
        emptyButton = Button.builder(Component.translatable("blockvault.trash.empty_all"), button -> {
            if (!confirmEmpty) {
                confirmEmpty = true;
                refreshButtons();
                return;
            }
            confirmEmpty = false;
            VaultRuntime.emptyTrash(this::update);
        }).bounds(footerLeft + (footer + 6) * 2, height - 34, footer, 20).build();
        addRenderableWidget(previousButton);
        addRenderableWidget(nextButton);
        addRenderableWidget(emptyButton);
        addRenderableWidget(Button.builder(Component.translatable("blockvault.vault.back"),
                button -> onClose()).bounds(footerLeft + (footer + 6) * 3, height - 34, footer, 20).build());
        refreshButtons();
    }

    private void deleteRow(TrashRow row) {
        if (!row.id().equals(confirmDeleteId)) {
            confirmDeleteId = row.id();
            refreshButtons();
            return;
        }
        confirmDeleteId = null;
        VaultRuntime.deleteFromTrash(row.id(), this::update);
    }

    private void withRow(int rowIndex, java.util.function.Consumer<TrashRow> action) {
        int index = page * ROWS_PER_PAGE + rowIndex;
        if (index < rows.size()) action.accept(rows.get(index));
    }

    private void update(List<TrashRow> updated) {
        rows = List.copyOf(updated);
        confirmDeleteId = null;
        page = Math.min(page, totalPages() - 1);
        refreshButtons();
    }

    private int totalPages() { return Math.max(1, (rows.size() + ROWS_PER_PAGE - 1) / ROWS_PER_PAGE); }

    private void refreshButtons() {
        for (int index = 0; index < ROWS_PER_PAGE; index++) {
            boolean visible = page * ROWS_PER_PAGE + index < rows.size();
            restoreButtons.get(index).visible = visible;
            deleteButtons.get(index).visible = visible;
            deleteButtons.get(index).setMessage(Component.translatable(visible
                    && rows.get(page * ROWS_PER_PAGE + index).id().equals(confirmDeleteId)
                    ? "blockvault.trash.delete.confirm" : "blockvault.trash.delete"));
        }
        previousButton.active = page > 0;
        nextButton.active = page < totalPages() - 1;
        emptyButton.active = !rows.isEmpty();
        emptyButton.setMessage(Component.translatable(confirmEmpty
                ? "blockvault.trash.empty_all.confirm" : "blockvault.trash.empty_all"));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        graphics.centeredText(font, title, width / 2, 12, 0xffffffff);
        graphics.centeredText(font, Component.translatable("blockvault.trash.note"), width / 2, 26, 0xffaaaaaa);
        boolean streamer = BlockVaultConfig.current().streamerMode();
        long now = System.currentTimeMillis();
        int labelWidth = Math.max(0, contentWidth - 160);
        for (int index = 0; index < ROWS_PER_PAGE; index++) {
            int rowIndex = page * ROWS_PER_PAGE + index;
            if (rowIndex >= rows.size()) break;
            TrashRow row = rows.get(rowIndex);
            int y = 40 + index * 34;
            String server = streamer ? Component.translatable("blockvault.settings.streamer.hidden").getString()
                    : row.serverName();
            String detail = Component.translatable("blockvault.trash.row.account",
                    row.usernames().isEmpty() ? "" : row.usernames().getFirst(), row.daysLeft(now)).getString();
            graphics.text(font, Component.literal(clip(server, labelWidth)), contentLeft, y, 0xffffffff);
            graphics.text(font, Component.literal(clip(detail, labelWidth)), contentLeft, y + 12, 0xffb9b4c5);
        }
        if (rows.isEmpty()) {
            graphics.centeredText(font, Component.translatable("blockvault.trash.empty"), width / 2, 80, 0xffcccccc);
        }
        graphics.centeredText(font, Component.translatable("blockvault.vault.page",
                Math.min(page + 1, totalPages()), totalPages()), width / 2, height - 72, 0xffaaaaaa);
    }

    private static String clip(String value, int maxPixels) {
        int maxChars = Math.max(0, maxPixels / 6);
        return value.length() <= maxChars ? value : maxChars < 2 ? "…" : value.substring(0, maxChars - 1) + "…";
    }

    @Override
    public void onClose() {
        // Back goes to the vault list with fresh rows.
        if (parent instanceof VaultListScreen) VaultRuntime.openVaultScreen();
        else Minecraft.getInstance().setScreenAndShow(parent);
    }

    @Override public boolean isPauseScreen() { return false; }
}
