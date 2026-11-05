package io.github.silkypanda.blockvault;

import java.util.function.Consumer;
import java.util.function.Predicate;
import net.minecraft.client.gui.components.EditBox;

/**
 * Keeps a text field to the values BlockVault accepts, using the rules in {@code InputRules}.
 * A change containing any disallowed character is rejected in full.
 */
final class InputFilter {
    private InputFilter() { }

    static void bind(EditBox box, Predicate<String> allowed, Consumer<String> responder) {
        String[] accepted = { allowed.test(box.getValue()) ? box.getValue() : "" };
        boolean[] adjusting = { false };
        box.setResponder(value -> {
            if (adjusting[0]) return;
            if (!allowed.test(value)) {
                adjusting[0] = true;
                try { box.setValue(accepted[0]); }
                finally { adjusting[0] = false; }
                return;
            }
            accepted[0] = value;
            if (responder != null) responder.accept(value);
        });
    }
}
