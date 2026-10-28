package io.github.silkypanda.blockvault.core.vault;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.silkypanda.blockvault.core.crypto.AesGcm;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

final class VaultFileTest {
    @Test
    void encodesAndParsesEncryptedPayload() throws Exception {
        byte[] key = new byte[32];
        Arrays.fill(key, (byte) 9);
        VaultFile.Header header = new VaultFile.Header(KeyMode.MASTER_PASSWORD, 1, 0, 210_000, new byte[16]);
        byte[] json = "{\"v\":1,\"servers\":[]}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] file = VaultFile.encode(header, new byte[60], key, json);
        VaultFile.Parsed parsed = VaultFile.parse(file);
        assertArrayEquals(json, AesGcm.decrypt(key, parsed.dataNonce(), parsed.headerAad(), parsed.ciphertext()));
    }

    @Test
    void rejectsTruncationAndUnknownVersion() {
        assertThrows(IllegalArgumentException.class, () -> VaultFile.parse(new byte[61]));
        byte[] future = new byte[62];
        future[0] = 'B'; future[1] = 'V'; future[2] = 'L'; future[3] = 'T'; future[4] = 2;
        assertThrows(UnsupportedVaultVersionException.class, () -> VaultFile.parse(future));
    }

    @Test
    void encoderRejectsFilesThatParserWouldConsiderOversized() {
        VaultFile.Header header = new VaultFile.Header(KeyMode.MASTER_PASSWORD, 1, 0, 210_000, new byte[16]);
        int overhead = 28 + 2 + 60 + 12 + 4 + 16;
        assertThrows(IllegalArgumentException.class, () -> VaultFile.encode(header, new byte[60], new byte[32],
                new byte[VaultFile.MAX_BYTES - overhead + 1]));
    }

    @Test
    void everySingleByteMutationIsRejectedOrFailsAuthentication() throws Exception {
        byte[] key = new byte[32];
        byte[] source = VaultFile.encode(new VaultFile.Header(KeyMode.MASTER_PASSWORD, 1, 0, 210_000, new byte[16]),
                new byte[60], key, "{\"v\":1,\"servers\":[]}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        for (int i = 0; i < source.length; i++) {
            byte[] changed = source.clone();
            changed[i] ^= 1;
            try {
                VaultFile.Parsed parsed = VaultFile.parse(changed);
                assertThrows(io.github.silkypanda.blockvault.core.crypto.VaultAuthException.class,
                        () -> AesGcm.decrypt(key, parsed.dataNonce(), parsed.headerAad(), parsed.ciphertext()));
            } catch (IllegalArgumentException | UnsupportedVaultVersionException expected) {
                // Invalid framing is rejected before cryptographic authentication.
            }
        }
    }

    @Test
    void boundedFuzzMutationsFailOnlyWithControlledExceptions() throws Exception {
        byte[] valid = VaultFile.encode(new VaultFile.Header(KeyMode.MASTER_PASSWORD, 1, 0, 210_000, new byte[16]),
                new byte[60], new byte[32], "{\"v\":1,\"servers\":[]}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        java.util.Random random = new java.util.Random(17);
        for (int n = 0; n < 5000; n++) {
            byte[] mutation = valid.clone();
            int index = random.nextInt(mutation.length);
            mutation[index] ^= (byte) (1 + random.nextInt(255));
            try { VaultFile.parse(mutation); }
            catch (IllegalArgumentException | UnsupportedVaultVersionException expected) { }
        }
    }
}
