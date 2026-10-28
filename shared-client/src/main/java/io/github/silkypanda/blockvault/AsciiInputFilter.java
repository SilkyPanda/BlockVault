package io.github.silkypanda.blockvault;

import io.github.silkypanda.blockvault.core.validation.AsciiInput;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.minecraft.client.gui.components.EditBox;

/** Applies core ASCII allowlists to the EditBox API available in this Minecraft version. */
final class AsciiInputFilter {
    private AsciiInputFilter() { }

    static void bind(EditBox box, Predicate<String> allowed, Consumer<String> responder) {
        box.setResponder(value -> {
            StringBuilder filtered = new StringBuilder(value.length());
            for (int i = 0; i < value.length(); i++) {
                String character = value.substring(i, i + 1);
                if (allowed.test(character)) filtered.append(character);
            }
            String accepted = filtered.toString();
            if (!accepted.equals(value)) {
                box.setValue(accepted);
            } else if (responder != null) {
                responder.accept(value);
            }
        });
        String value = box.getValue();
        StringBuilder filtered = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            String character = value.substring(i, i + 1);
            if (allowed.test(character)) filtered.append(character);
        }
        if (!filtered.toString().equals(value)) box.setValue(filtered.toString());
    }
}
