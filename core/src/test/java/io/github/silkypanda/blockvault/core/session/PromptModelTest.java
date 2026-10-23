package io.github.silkypanda.blockvault.core.session;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import org.junit.jupiter.api.Test;

final class PromptModelTest {
    @Test
    void masksPasswordInStringRepresentation() {
        PromptModel model = new PromptModel(CardVariant.SAVE, "example:25565", "Alex", "secret".toCharArray());
        assertFalse(model.toString().contains("secret"));
    }

    @Test
    void clearsRetainedPasswordAfterPresentation() {
        PromptModel model = new PromptModel(CardVariant.SAVE, "example:25565", "Alex", "secret".toCharArray());
        model.clearPassword();
        assertArrayEquals(new char[6], model.password());
    }
}
