package io.github.silkypanda.blockvault.core.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class SavedCredentialTest {
    @Test
    void policyOnlyCredentialGetsDefaultTemplateAndSafeString() {
        SavedCredential saved = new SavedCredential(null, false, null, false);
        assertEquals("authme", saved.template().id());
        assertTrue(saved.toString().contains("account=null"));
        assertTrue(!saved.toString().contains("secret"));
    }
}
