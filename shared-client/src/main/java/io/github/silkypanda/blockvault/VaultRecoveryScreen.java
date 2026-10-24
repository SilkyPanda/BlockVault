package io.github.silkypanda.blockvault;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Lets the player inspect and explicitly restore one of the three encrypted recovery points. */
final class VaultRecoveryScreen extends Screen {
    private final Screen parent;
    private boolean busy;
    private boolean failed;
    private int confirmSlot;
    private final Button[] restoreButtons = new Button[3];

    VaultRecoveryScreen(Screen parent) {
        super(Component.translatable("blockvault.backup.title"));
        this.parent = parent;
    }

    @Override protected void init() {
        int center = width / 2;
        int top = Math.max(38, height / 2 - 50);
        for (int slot = 1; slot <= 3; slot++) {
            final int number = slot;
            restoreButtons[slot - 1] = Button.builder(Component.translatable("blockvault.backup.restore", slot),
                    button -> choose(number, button)).bounds(center - 120, top + (slot - 1) * 26, 240, 20).build();
            addRenderableWidget(restoreButtons[slot - 1]);
        }
        addRenderableWidget(Button.builder(Component.translatable("blockvault.vault.back"),
                button -> net.minecraft.client.Minecraft.getInstance().setScreenAndShow(parent))
                .bounds(center - 100, top + 86, 200, 20).build());
        refreshAvailability();
    }

    private void refreshAvailability() {
        VaultRuntime.backupAvailability(available -> {
            if (net.minecraft.client.Minecraft.getInstance().gui.screen() != this) return;
            for (int slot = 1; slot <= 3; slot++) restoreButtons[slot - 1].active = available[slot - 1] && !busy;
        });
    }

    private void choose(int slot, Button button) {
        if (busy) return;
        if (confirmSlot != slot) {
            confirmSlot = slot;
            for (int index = 0; index < restoreButtons.length; index++) {
                restoreButtons[index].setMessage(Component.translatable("blockvault.backup.restore", index + 1));
            }
            button.setMessage(Component.translatable("blockvault.backup.confirm", slot));
            return;
        }
        busy = true;
        failed = false;
        VaultRuntime.restoreBackup(slot, success -> {
            busy = false;
            failed = !success;
            confirmSlot = 0;
            if (success) button.setMessage(Component.translatable("blockvault.backup.restored", slot));
            else button.setMessage(Component.translatable("blockvault.backup.restore", slot));
            refreshAvailability();
        });
    }

    @Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        graphics.centeredText(font, Component.translatable("blockvault.backup.help"), width / 2,
                Math.max(18, height / 2 - 72), 0xffc5c0cf);
        if (busy || failed) graphics.centeredText(font, Component.translatable(busy
                ? "blockvault.backup.working" : "blockvault.backup.failed"), width / 2,
                Math.max(134, height / 2 + 48), 0xffff7777);
    }

    @Override public boolean isPauseScreen() { return false; }
}
