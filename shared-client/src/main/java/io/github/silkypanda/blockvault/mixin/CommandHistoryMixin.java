package io.github.silkypanda.blockvault.mixin;

import io.github.silkypanda.blockvault.Hooks;
import io.github.silkypanda.blockvault.SensitiveCommands;
import net.minecraft.client.CommandHistory;
import net.minecraft.client.gui.components.ChatComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Prevents login credentials from entering memory or command_history.txt. */
@Mixin({CommandHistory.class, ChatComponent.class})
public abstract class CommandHistoryMixin {
    @Inject(method = "addCommand", at = @At("HEAD"), cancellable = true, require = 0)
    private void blockvault$omitSensitiveCommand(String command, CallbackInfo callback) {
        if (SensitiveCommands.isSensitive(command)) {
            Hooks.historyScrubbed();
            callback.cancel();
        }
    }
    @Inject(method = "addRecentChat", at = @At("HEAD"), cancellable = true, require = 0)
    private void blockvault$omitSensitiveRecall(String message, CallbackInfo callback) {
        if (SensitiveCommands.isSensitive(message)) callback.cancel();
    }
}
