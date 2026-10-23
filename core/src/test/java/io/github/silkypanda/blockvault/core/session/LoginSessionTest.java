package io.github.silkypanda.blockvault.core.session;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.silkypanda.blockvault.core.gen.PasswordGenerator;
import io.github.silkypanda.blockvault.core.vault.Account;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

final class LoginSessionTest {
    @Test
    void persistsPendingBeforeSendingRegistration() throws Exception {
        FakeEnv env = new FakeEnv();
        LoginSession session = new LoginSession(env, new FakeScheduler(), PatternPackFixture.load(), PasswordGenerator.Policy.defaults());
        session.onJoin("example.org:25565", "Alex");
        session.onSystemMessage("Please register using /register <password>");
        assertEquals(CardVariant.REGISTER, env.card.variant());
        session.confirmRegistration("unique-pass".toCharArray());
        assertEquals(List.of("pending", "send"), env.order);
        session.onSystemMessage("Successfully registered!");
        assertEquals(SessionState.CONFIRMED, session.state());
        assertEquals(1, env.verified);
    }

    @Test
    void manualCaptureIsOnlyOfferedAfterSuccess() throws Exception {
        FakeEnv env = new FakeEnv();
        LoginSession session = new LoginSession(env, new FakeScheduler(), PatternPackFixture.load(), PasswordGenerator.Policy.defaults());
        session.onJoin("example.org:25565", "Alex");
        session.onOutgoingCommand("/login manuallyTyped");
        assertEquals(null, env.card);
        session.onSystemMessage("Wrong password!");
        assertEquals(null, env.card);
        session.onOutgoingCommand("/login correctValue");
        session.onSystemMessage("Successful login!");
        assertEquals(CardVariant.SAVE, env.card.variant());
    }

    private static final class FakeScheduler implements Scheduler {
        private long now;
        private final List<Runnable> callbacks = new ArrayList<>();
        @Override public void schedule(long delayMillis, Runnable callback) { callbacks.add(callback); }
        @Override public long nowMillis() { return now; }
    }

    private static final class FakeEnv implements SessionEnv, VaultAccess {
        private final List<String> order = new ArrayList<>();
        private PromptModel card;
        private int verified;
        @Override public void sendCommand(String commandWithoutSlash) { order.add("send"); }
        @Override public void showSavePrompt(PromptModel model) { card = model; }
        @Override public void toast(String translationKey) { }
        @Override public VaultAccess vault() { return this; }
        @Override public void find(String host, String username, Consumer<Account> result) { result.accept(null); }
        @Override public void savePending(String host, String username, char[] password, Consumer<Boolean> saved) {
            order.add("pending"); saved.accept(true);
        }
        @Override public void markVerified(String host, String username) { verified++; }
        @Override public void delete(String host, String username) { }
        @Override public void saveVerified(String host, String username, char[] password) { }
    }

    private static final class PatternPackFixture {
        static io.github.silkypanda.blockvault.core.detect.PatternPack load() throws Exception {
            InputStream resource = LoginSessionTest.class.getResourceAsStream("/assets/blockvault/patterns/default.json");
            return io.github.silkypanda.blockvault.core.detect.PatternPack.load(resource, null);
        }
    }
}
