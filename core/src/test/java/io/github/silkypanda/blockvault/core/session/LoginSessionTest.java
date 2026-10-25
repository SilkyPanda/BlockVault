package io.github.silkypanda.blockvault.core.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import io.github.silkypanda.blockvault.core.gen.PasswordGenerator;
import io.github.silkypanda.blockvault.core.vault.Account;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

final class LoginSessionTest {
    @Test
    void persistsPendingBeforeSendingAndConfirmsRegistration() throws Exception {
        FakeEnv env = new FakeEnv();
        FakeScheduler clock = new FakeScheduler();
        LoginSession session = session(env, clock);
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
    void verifiedCredentialAutofillsOnceAndFailureRejectsWithoutRetry() throws Exception {
        FakeEnv env = new FakeEnv();
        env.credential("secret");
        FakeScheduler clock = new FakeScheduler();
        LoginSession session = session(env, clock);
        session.onJoin("example.org:25565", "Alex");
        session.onSystemMessage("Please login with /login <password>");
        assertTrue(env.commands.isEmpty());
        clock.advance(399);
        assertTrue(env.commands.isEmpty());
        clock.advance(1);
        assertEquals(List.of("login secret"), env.commands);
        session.onSystemMessage("Wrong password!");
        assertEquals(SessionState.REJECTED, session.state());
        assertEquals(CardVariant.REJECTED, env.card.variant());
        session.onSystemMessage("Please login with /login <password>");
        clock.advance(1000);
        assertEquals(1, env.commands.size());
    }

    @Test
    void serverPolicyCanDisableAutofillAndRegistrationSaving() throws Exception {
        FakeEnv env = new FakeEnv();
        env.credential("secret");
        env.credential = new SavedCredential(env.credential.account(), false, false, null);
        FakeScheduler clock = new FakeScheduler();
        LoginSession session = session(env, clock);
        session.onJoin("example.org:25565", "Alex");
        session.onSystemMessage("Please login with /login <password>");
        clock.advance(1000);
        assertTrue(env.commands.isEmpty());

        env.credential = new SavedCredential(null, true, true, null);
        session.onDisconnect();
        session.onJoin("example.org:25565", "Alex");
        session.onSystemMessage("Please register using /register <password>");
        assertEquals(null, env.card);
    }

    @Test
    void commandTreeWithBothCommandsWaitsForActualRegistrationPrompt() throws Exception {
        FakeEnv env = new FakeEnv();
        LoginSession session = session(env, new FakeScheduler());
        session.onJoin("example.org:25565", "Alex");
        assertEquals(null, env.card);
        session.onSystemMessage("Please register with /register <password>");
        assertEquals(CardVariant.REGISTER, env.card.variant());
    }

    @Test
    void promptMentioningLoginAndRegisterUsesVerifiedVaultState() throws Exception {
        FakeEnv env = new FakeEnv();
        env.credential("saved-secret");
        FakeScheduler clock = new FakeScheduler();
        LoginSession session = session(env, clock);
        session.onJoin("example.org:25565", "Alex");
        session.onSystemMessage("Use /login <password> or /register <password>");
        clock.advance(400);
        assertEquals(List.of("login saved-secret"), env.commands);

        FakeEnv newAccountEnv = new FakeEnv();
        LoginSession newAccountSession = session(newAccountEnv, new FakeScheduler());
        newAccountSession.onJoin("example.org:25565", "Alex");
        newAccountSession.onSystemMessage("Use /login <password> or /register <password>");
        assertEquals(CardVariant.REGISTER, newAccountEnv.card.variant());
    }

    @Test
    void timeoutAndDebouncePreventRepeatedAutofill() throws Exception {
        FakeEnv env = new FakeEnv();
        env.credential("secret");
        FakeScheduler clock = new FakeScheduler();
        LoginSession session = session(env, clock);
        session.onJoin("example.org:25565", "Alex");
        session.onSystemMessage("Please login with /login <password>");
        session.onSystemMessage("Please login with /login <password>");
        clock.advance(400);
        assertEquals(1, env.commands.size());
        clock.advance(5000);
        assertEquals(SessionState.UNKNOWN, session.state());
        session.onSystemMessage("Please login with /login <password>");
        clock.advance(2000);
        assertEquals(1, env.commands.size());
    }

    @Test
    void manualCaptureIsOnlyOfferedAfterSuccessfulResult() throws Exception {
        FakeEnv env = new FakeEnv();
        LoginSession session = session(env, new FakeScheduler());
        session.onJoin("example.org:25565", "Alex");
        session.onOutgoingCommand("/login wrongValue");
        session.onSystemMessage("Wrong password!");
        assertEquals(null, env.card);
        session.onOutgoingCommand("/login correctValue");
        session.onSystemMessage("Successful login!");
        assertEquals(CardVariant.SAVE, env.card.variant());
        char[] accepted = env.card.password();
        session.confirmCapturedSave(accepted);
        assertArrayEquals(new char[accepted.length], env.savedPasswordArgument);
        java.util.Arrays.fill(accepted, '\0');
    }

    @Test
    void successfulManualLoginVerifiesMatchingPendingCredentialForLaterAutofill() throws Exception {
        FakeEnv env = new FakeEnv();
        env.pendingCredential("saved-pass");
        LoginSession session = session(env, new FakeScheduler());
        session.onJoin("example.org:25565", "Alex");
        session.onOutgoingCommand("/login saved-pass");
        session.onSystemMessage("Successful login!");
        assertEquals(1, env.verified);
        assertEquals(null, env.card);
    }

    @Test
    void successfulAutofillSendsLocalizedChatConfirmation() throws Exception {
        FakeEnv env = new FakeEnv();
        env.credential("secret");
        FakeScheduler clock = new FakeScheduler();
        LoginSession session = session(env, clock);
        session.onJoin("example.org:25565", "Alex");
        session.onSystemMessage("Please login with /login <password>");
        clock.advance(400);
        session.onSystemMessage("Successfully logged in!");
        assertEquals(List.of("blockvault.chat.autologin_success"), env.chatMessages);
    }

    @Test
    void rejectedCredentialCanBeUpdatedOrForgottenByExplicitAction() throws Exception {
        FakeEnv env = new FakeEnv();
        env.credential("old-secret");
        FakeScheduler clock = new FakeScheduler();
        LoginSession session = session(env, clock);
        session.onJoin("example.org:25565", "Alex");
        session.onSystemMessage("Please login with /login <password>");
        clock.advance(400);
        session.onSystemMessage("Wrong password!");
        session.updateRejectedPassword("replacement".toCharArray());
        assertEquals(1, env.savedVerified);
        assertEquals(SessionState.CONFIRMED, session.state());

        session.onDisconnect();
        session.onJoin("example.org:25565", "Alex");
        session.onSystemMessage("Please login with /login <password>");
        clock.advance(400);
        session.onSystemMessage("Wrong password!");
        session.forgetRejectedCredential();
        assertEquals(1, env.deleted);
        assertEquals(SessionState.CONFIRMED, session.state());
    }

    @Test
    void ruleComplaintOffersOneAdjustedRetryAndNeverDeletesThePendingCredential() throws Exception {
        FakeEnv env = new FakeEnv();
        LoginSession session = session(env, new FakeScheduler());
        session.onJoin("example.org:25565", "Alex");
        session.onSystemMessage("Please register using /register <password> <confirmPassword>");
        session.confirmRegistration("first-secret".toCharArray());
        session.onSystemMessage("Password too short, at least 24 characters");
        assertEquals(0, env.deleted);
        assertEquals(CardVariant.REGISTER, env.card.variant());
        assertEquals(24, env.card.policy().length());
        session.confirmRegistration(env.card.password());
        session.onSystemMessage("Password too short, at least 24 characters");
        assertEquals(SessionState.REJECTED, session.state());
        assertEquals(0, env.deleted);
    }

    @Test
    void registerFailureKeepsThePendingCredentialInCaseTheServerAcceptedIt() throws Exception {
        FakeEnv env = new FakeEnv();
        LoginSession session = session(env, new FakeScheduler());
        session.onJoin("example.org:25565", "Alex");
        session.onSystemMessage("Please register using /register <password>");
        session.confirmRegistration("not-accepted".toCharArray());
        session.onSystemMessage("Registration failed!");
        assertEquals(0, env.deleted);
        assertEquals(SessionState.REJECTED, session.state());
    }

    @Test
    void ruleComplaintWithoutNumbersFallsBackToAConservativePasswordInsteadOfGivingUp() throws Exception {
        FakeEnv env = new FakeEnv();
        LoginSession session = session(env, new FakeScheduler());
        session.onJoin("example.org:25565", "Alex");
        session.onSystemMessage("Please register using /register <password> <confirmPassword>");
        session.confirmRegistration("first-secret".toCharArray());
        session.onSystemMessage("Your password is too short or too long!");
        assertEquals(CardVariant.REGISTER, env.card.variant());
        assertEquals(12, env.card.password().length);
        assertEquals(false, env.card.policy().symbols());
    }

    @Test
    void cancellingTheMasterPasswordPromptStopsFurtherPromptsForThisConnection() throws Exception {
        FakeEnv env = new FakeEnv();
        env.cancelUnlock = true;
        FakeScheduler clock = new FakeScheduler();
        LoginSession session = session(env, clock);
        session.onJoin("example.org:25565", "Alex");
        session.onSystemMessage("Please login with /login <password>");
        assertEquals(SessionState.UNKNOWN, session.state());
        int cancelsAfterFirst = env.cancels;
        clock.advance(10_000);
        session.onSystemMessage("Please login with /login <password>");
        assertEquals(cancelsAfterFirst, env.cancels, "the owner already declined; do not ask again");
    }

    @Test
    void pendingAccountAfterRestartShowsUpdateInsteadOfGenerating() throws Exception {
        FakeEnv env = new FakeEnv();
        env.pendingCredential("recover-me");
        LoginSession session = session(env, new FakeScheduler());
        session.onJoin("example.org:25565", "Alex");
        session.onSystemMessage("Please register using /register <password>");
        assertEquals(CardVariant.UPDATE, env.card.variant());
        assertEquals("recover-me", new String(env.card.password()));
    }

    @Test
    void disconnectCancelsAutofillTimerAndWipesSession() throws Exception {
        FakeEnv env = new FakeEnv();
        env.credential("secret");
        FakeScheduler clock = new FakeScheduler();
        LoginSession session = session(env, clock);
        session.onJoin("example.org:25565", "Alex");
        session.onSystemMessage("Please login with /login <password>");
        session.onDisconnect();
        clock.advance(1000);
        assertEquals(SessionState.IDLE, session.state());
        assertTrue(env.commands.isEmpty());
        assertFalse(session.attempted());
    }

    private static LoginSession session(FakeEnv env, FakeScheduler scheduler) throws Exception {
        return new LoginSession(env, scheduler, PatternPackFixture.load(), PasswordGenerator.Policy.defaults());
    }

    private static final class FakeScheduler implements Scheduler {
        private long now;
        private final List<Entry> entries = new ArrayList<>();
        @Override public Handle schedule(long delayMillis, Runnable callback) {
            Entry entry = new Entry(now + delayMillis, callback);
            entries.add(entry);
            return () -> entry.cancelled = true;
        }
        @Override public long nowMillis() { return now; }
        void advance(long amount) {
            now += amount;
            boolean ran;
            do {
                ran = false;
                for (Entry entry : List.copyOf(entries)) {
                    if (!entry.cancelled && !entry.ran && entry.at <= now) {
                        entry.ran = true;
                        entry.callback.run();
                        ran = true;
                    }
                }
            } while (ran);
        }
        private static final class Entry {
            final long at;
            final Runnable callback;
            boolean cancelled;
            boolean ran;
            Entry(long at, Runnable callback) { this.at = at; this.callback = callback; }
        }
    }

    private static final class FakeEnv implements SessionEnv, VaultAccess {
        private final List<String> order = new ArrayList<>();
        private final List<String> commands = new ArrayList<>();
        private final List<String> chatMessages = new ArrayList<>();
        private PromptModel card;
        private SavedCredential credential;
        private int verified;
        private int deleted;
        private int savedVerified;
        private char[] savedPasswordArgument;
        @Override public void sendCommand(String commandWithoutSlash) { order.add("send"); commands.add(commandWithoutSlash); }
        @Override public void showSavePrompt(PromptModel model) { card = model; }
        @Override public void toast(String translationKey) { }
        @Override public void chatMessage(String translationKey) { chatMessages.add(translationKey); }
        @Override public VaultAccess vault() { return this; }
        private Runnable cancelAction;
        private boolean cancelUnlock;
        private int cancels;
        @Override public void find(String host, String username, Consumer<SavedCredential> result) { result.accept(credential); }
        @Override public void find(String host, String username, Consumer<SavedCredential> result, Runnable cancelled) {
            if (cancelUnlock) { cancels++; cancelAction = cancelled; cancelled.run(); } else result.accept(credential);
        }
        @Override public void savePending(String host, String username, char[] password, Consumer<Boolean> saved) {
            order.add("pending"); saved.accept(true);
        }
        @Override public void markVerified(String host, String username) { verified++; }
        @Override public void delete(String host, String username) { deleted++; credential = null; }
        @Override public void saveVerified(String host, String username, char[] password) {
            savedPasswordArgument = password;
            savedVerified++;
        }
        void credential(String secret) { credential = new SavedCredential(account(secret, Account.Status.VERIFIED), true, false, null); }
        void pendingCredential(String secret) { credential = new SavedCredential(account(secret, Account.Status.PENDING), true, false, null); }
        private static Account account(String secret, Account.Status status) {
            return new Account("Alex", secret.toCharArray(), status, 0, 0, 0, null);
        }
    }

    private static final class PatternPackFixture {
        static io.github.silkypanda.blockvault.core.detect.PatternPack load() throws Exception {
            InputStream resource = LoginSessionTest.class.getResourceAsStream("/assets/blockvault/patterns/default.json");
            return io.github.silkypanda.blockvault.core.detect.PatternPack.load(resource, null);
        }
    }
}
