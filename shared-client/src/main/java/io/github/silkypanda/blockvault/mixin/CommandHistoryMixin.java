package io.github.silkypanda.blockvault.mixin;

import io.github.silkypanda.blockvault.MixinHealth;
import io.github.silkypanda.blockvault.SensitiveCommands;
import net.minecraft.client.CommandHistory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Prevents login credentials from entering memory or command_history.txt. */
@Mixin(CommandHistory.class)
public abstract class CommandHistoryMixin {
    @Inject(method = "addCommand", at = @At("HEAD"), cancellable = true, require = 0)
    private void blockvault$omitSensitiveCommand(String command, CallbackInfo callback) {
        MixinHealth.historyRan();
        if (SensitiveCommands.isSensitive(command)) callback.cancel();
    }
}
