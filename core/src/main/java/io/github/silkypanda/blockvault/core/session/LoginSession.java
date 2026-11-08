package io.github.silkypanda.blockvault.core.session;

import io.github.silkypanda.blockvault.core.detect.PatternPack;
import io.github.silkypanda.blockvault.core.detect.PromptKind;
import io.github.silkypanda.blockvault.core.detect.PromptMatcher;
import io.github.silkypanda.blockvault.core.detect.ResultKind;
import io.github.silkypanda.blockvault.core.detect.ResultMatcher;
import io.github.silkypanda.blockvault.core.gen.PasswordGenerator;
import io.github.silkypanda.blockvault.core.vault.Account;
import io.github.silkypanda.blockvault.core.validation.InputRules;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Pure-Java login prompt state machine. Vault callbacks must return on the session thread. */
public final class LoginSession {
    private static final long DEBOUNCE_MILLIS = 1500;
    private static final long RESULT_TIMEOUT_MILLIS = 5_000;
    private static final long PROMPT_LISTENING_MILLIS = 30_000;
    /** Server text is untrusted and unbounded; only this many characters are ever matched. */
    private static final int MAX_MESSAGE_CHARS = 2048;
    /** Used when a server rejects a password but its message gives no usable numbers. */
    private static final PasswordGenerator.Policy FALLBACK_POLICY =
            new PasswordGenerator.Policy(12, true, true, true, false, "", false);
    private static final Pattern COMMAND = Pattern.compile("(?i)^\\s*/?((?:[A-Za-z0-9_]+:)?[A-Za-z0-9_]+)(?:\\s+(.*))?$");

    private enum Action { NONE, AUTOFILL, PASSWORDLESS, REGISTER, MANUAL, UNREGISTER }

    private final SessionEnv env;
    private final Scheduler scheduler;
    private final PromptMatcher prompts;
    private final ResultMatcher results;
    private final PatternPack pack;
    private PasswordGenerator.Policy policy;
    // Fail closed while settings are still loading, so an early prompt cannot ignore the owner's opt-out.
    private boolean globalAutofill;
    private boolean passwordlessLogin = true;
    private long autofillDelayMillis = 400;
    private SessionState state = SessionState.IDLE;
    private String host;
    private String username;
    private CommandTemplate template = CommandTemplate.builtins().get("authme");
    private char[] pending;
    private char[] autofillStaged;
    private Action action = Action.NONE;
    private long lastPromptAt = Long.MIN_VALUE;
    private PromptKind lastPromptKind;
    private final EnumSet<PromptKind> dismissedPromptKinds = EnumSet.noneOf(PromptKind.class);
    private final java.util.Set<String> promptedShortAliases = new java.util.HashSet<>();
    private final java.util.Set<String> consumedShortAliases = new java.util.HashSet<>();
    private long shortAliasPromptAt = Long.MIN_VALUE;
    private long promptListeningUntil = Long.MIN_VALUE;
    private boolean attempted;
    private boolean registerRetry;
    private int generation;
    private int promptRevision;
    private Scheduler.Handle timer;

    /** Creates a connection session with immutable matchers and password policy. */
    public LoginSession(SessionEnv env, Scheduler scheduler, PatternPack pack, PasswordGenerator.Policy policy) {
        this.env = env;
        this.scheduler = scheduler;
        this.pack = pack;
        this.prompts = new PromptMatcher(pack);
        this.results = new ResultMatcher(pack);
        this.policy = policy;
    }

    /** Begins tracking a multiplayer connection; a null host disables all account actions. */
    public void onJoin(String hostKey, String mcUsername) {
        onDisconnect();
        host = hostKey;
        username = mcUsername;
        promptListeningUntil = scheduler.nowMillis() + PROMPT_LISTENING_MILLIS;
    }

    /** Processes a plain system message only; callers must exclude signed player chat. */
    public void onSystemMessage(String message) {
        if (host == null || state == SessionState.CONFIRMED || message == null) return;
        String plain = message.length() > MAX_MESSAGE_CHARS ? message.substring(0, MAX_MESSAGE_CHARS) : message;
        boolean listenForPrompts = scheduler.nowMillis() <= promptListeningUntil;
        java.util.Set<String> explicitShortAliases = listenForPrompts
                ? prompts.promptedShortCommands(plain) : java.util.Set.of();
        if (!explicitShortAliases.isEmpty()) {
            promptedShortAliases.clear();
            promptedShortAliases.addAll(explicitShortAliases);
            shortAliasPromptAt = scheduler.nowMillis();
        }
        java.util.Set<PromptKind> matches = listenForPrompts ? prompts.matchingPrompts(plain) : java.util.Set.of();
        if (matches.size() == 1) handlePrompt(matches.iterator().next(), plain);
        else if (matches.contains(PromptKind.LOGIN) && matches.contains(PromptKind.REGISTER)) {
            if (dismissedPromptKinds.contains(PromptKind.LOGIN)
                    && dismissedPromptKinds.contains(PromptKind.REGISTER)) return;
            long now = scheduler.nowMillis();
            if (state == SessionState.SENT || state == SessionState.CONFIRMED
                    || lastPromptAt != Long.MIN_VALUE && now - lastPromptAt < DEBOUNCE_MILLIS) return;
            // Enter the prompted state before the asynchronous lookup so a cancelled unlock can
            // dismiss both possible prompt kinds and repeated messages are debounced.
            state = SessionState.PROMPTED;
            action = Action.NONE;
            lastPromptAt = now;
            lastPromptKind = null;
            int token = generation;
            String expectedHost = host;
            String expectedUsername = username;
            env.vault().find(expectedHost, expectedUsername, saved -> safely(() -> {
                if (token != generation || host == null || !host.equals(expectedHost) || !username.equals(expectedUsername)) return;
                Account existing = saved == null ? null : saved.account();
                PromptKind selected = existing != null && existing.isValidForUse()
                        && existing.status() == Account.Status.VERIFIED
                        ? PromptKind.LOGIN : PromptKind.REGISTER;
                handlePrompt(selected, plain);
            }), () -> safely(() -> abandonPrompt(token)));
        }
        if (state == SessionState.SENT) {
            ResultKind result = results.classify(plain);
            if (result != ResultKind.NONE) {
                promptedShortAliases.clear();
                consumedShortAliases.clear();
                shortAliasPromptAt = Long.MIN_VALUE;
                onResult(result, plain);
            }
        }
    }

    private void handlePrompt(PromptKind kind, String plain) {
        if (dismissedPromptKinds.contains(kind)) return;
        arm(kind, plain);
    }

    /** Captures a sensitive command in memory for possible post-success saving. */
    public void onOutgoingCommand(String commandWithoutSlash) {
        if (host == null || commandWithoutSlash == null) return;
        Matcher matcher = COMMAND.matcher(commandWithoutSlash);
        if (!matcher.matches()) return;
        String command = matcher.group(1).toLowerCase(Locale.ROOT);
        int namespace = command.lastIndexOf(':');
        if (namespace >= 0) command = command.substring(namespace + 1);
        if (isShortAuthenticationAlias(command) && !consumePromptedShortAlias(command)) return;
        String[] words = matcher.group(2) == null ? new String[0] : matcher.group(2).trim().split("\\s+");
        if (words.length == 0 || words[0].isEmpty()) {
            if (!contains("login", command) || !passwordlessLogin) return;
            String passThrough = matcher.group(1);
            // A bare login is an explicit request, independent of the automatic-login switches.
            if (state == SessionState.SENT) return;
            cancelTimer();
            wipePending();
            attempted = false;
            action = Action.PASSWORDLESS;
            state = SessionState.PROMPTED;
            int token = generation;
            int revision = ++promptRevision;
            String expectedHost = host;
            String expectedUsername = username;
            env.vault().find(expectedHost, expectedUsername, saved -> safely(() -> {
                if (token != generation || revision != promptRevision || state != SessionState.PROMPTED
                        || action != Action.PASSWORDLESS) return;
                Account account = saved == null ? null : saved.account();
                if (account == null || !account.isValidForUse() || account.status() != Account.Status.VERIFIED) {
                    action = Action.NONE;
                    state = SessionState.UNKNOWN;
                    env.sendCommand(passThrough);
                    return;
                }
                template = saved.template();
                char[] storedPassword = account.password();
                try { replacePending(storedPassword); }
                finally { Arrays.fill(storedPassword, '\0'); }
                send(false, null, token);
            }), () -> safely(() -> {
                if (token == generation && revision == promptRevision && action == Action.PASSWORDLESS) {
                    action = Action.NONE;
                    state = SessionState.UNKNOWN;
                }
            }));
            return;
        }
        if (contains("login", command)) {
            if (!InputRules.isPasswordArgument(words[0])) return;
            replacePending(words[0]);
            action = Action.MANUAL;
        } else if (contains("register", command)) {
            if (!InputRules.isPasswordArgument(words[0])) return;
            replacePending(words[0]);
            action = Action.MANUAL;
        } else if (contains("changepassword", command)) {
            if (words.length < 2) return;
            if (!InputRules.isPasswordArgument(words[1])) return;
            replacePending(words[1]);
            action = Action.MANUAL;
        } else if (contains("unregister", command)) {
            if (!InputRules.isPasswordArgument(words[0])) return;
            replacePending(words[0]);
            action = Action.UNREGISTER;
        } else return;
        state = SessionState.SENT;
        cancelTimer();
        int token = generation;
        timer = scheduler.schedule(RESULT_TIMEOUT_MILLIS, () -> {
            if (token == generation && (action == Action.MANUAL || action == Action.UNREGISTER)) {
                state = SessionState.UNKNOWN;
                wipePending();
                action = Action.NONE;
            }
        });
    }

    /** Whether a bare login command should be held while checking for a verified saved credential. */
    public boolean shouldInterceptBareLogin(String command) {
        return passwordlessLogin && command != null && contains("login", command.toLowerCase(Locale.ROOT));
    }

    /** Confirms the visible registration card; pending data is persisted before command transmission. */
    public void confirmRegistration(char[] password) { confirmRegistration(password, null); }

    /** Confirms registration, including an optional email requested by the detected prompt. */
    public void confirmRegistration(char[] password, String email) {
        if (host == null || state != SessionState.PROMPTED || lastPromptKind != PromptKind.REGISTER || action == Action.REGISTER) return;
        if (!CredentialPassword.isValid(password)) { env.toast("blockvault.toast.password_invalid"); return; }
        if (email != null && !email.isEmpty() && !InputRules.isEmail(email)) {
            env.toast("blockvault.toast.email_invalid");
            return;
        }
        replacePending(password);
        action = Action.REGISTER;
        // Close the prompt race while the pending credential is persisted. Repeated
        // registration messages must not queue a second card before send() runs.
        state = SessionState.SENT;
        env.cardResult(CardResult.SAVING);
        int token = generation;
        char[] staged = pending.clone();
        env.vault().savePending(host, username, staged, saved -> safely(() -> {
            Arrays.fill(staged, '\0');
            if (token != generation || state != SessionState.SENT || action != Action.REGISTER) return;
            if (!saved) {
                wipePending(); state = SessionState.UNKNOWN;
                env.cardResult(CardResult.FAILURE);
                return;
            }
            send(true, email, token);
        }));
    }

    /** Saves a manually captured password after the owner confirms its card. */
    public void confirmCapturedSave(char[] password) {
        if (host == null || state != SessionState.CONFIRMED) return;
        if (!CredentialPassword.isValid(password)) { env.toast("blockvault.toast.password_invalid"); return; }
        char[] staged = password.clone();
        env.vault().saveVerified(host, username, staged, saved -> safely(() -> {
            Arrays.fill(staged, '\0');
            env.cardResult(saved ? CardResult.SUCCESS : CardResult.FAILURE);
        }));
    }

    /** Sends a saved pending password only after the owner confirms the login card. */
    public void confirmPendingLogin(char[] password) {
        if (host == null || state != SessionState.PROMPTED || lastPromptKind != PromptKind.LOGIN
                || action != Action.NONE || !CredentialPassword.isValid(password)) return;
        replacePending(password);
        action = Action.AUTOFILL;
        state = SessionState.SENT;
        env.cardResult(CardResult.SAVING);
        int token = generation;
        char[] staged = pending.clone();
        env.vault().savePending(host, username, staged, saved -> safely(() -> {
            Arrays.fill(staged, '\0');
            if (token != generation || state != SessionState.SENT || action != Action.AUTOFILL) return;
            if (!saved) {
                wipePending(); action = Action.NONE; state = SessionState.UNKNOWN;
                env.cardResult(CardResult.FAILURE);
                return;
            }
            send(false, null, token);
        }));
    }

    /** Updates the rejected server credential after an explicit user confirmation. */
    public void updateRejectedPassword(char[] password) {
        if (host == null || state != SessionState.REJECTED) return;
        if (!CredentialPassword.isValid(password)) { env.toast("blockvault.toast.password_invalid"); return; }
        char[] staged = password.clone();
        env.vault().saveVerified(host, username, staged, saved -> safely(() -> {
            Arrays.fill(staged, '\0');
            if (saved) state = SessionState.CONFIRMED;
            env.cardResult(saved ? CardResult.SUCCESS : CardResult.FAILURE);
        }));
    }

    /** Removes a rejected credential after an explicit user confirmation. */
    public void forgetRejectedCredential() {
        forgetRejectedCredential(ignored -> { });
    }

    public void forgetRejectedCredential(java.util.function.Consumer<Boolean> result) {
        if (host == null || state != SessionState.REJECTED) return;
        env.vault().delete(host, username, deleted -> safely(() -> {
            if (deleted) state = SessionState.CONFIRMED;
            result.accept(deleted);
        }));
    }

    /** Dismisses a rejected-password prompt without changing the vault. */
    public void dismissRejectedPrompt() {
        if (state == SessionState.REJECTED) {
            if (lastPromptKind != null) dismissedPromptKinds.add(lastPromptKind);
            state = SessionState.UNKNOWN;
        }
    }

    /** Dismisses the current prompt until this connection ends. */
    public void dismissPrompt() {
        if (state != SessionState.PROMPTED) return;
        if (lastPromptKind == null) {
            dismissedPromptKinds.add(PromptKind.LOGIN);
            dismissedPromptKinds.add(PromptKind.REGISTER);
        } else dismissedPromptKinds.add(lastPromptKind);
        state = SessionState.UNKNOWN;
        action = Action.NONE;
        promptRevision++;
        cancelTimer();
        wipePending();
    }

    /** Returns the current connection state. */
    public SessionState state() { return state; }
    /** Whether a server message matches one of the loaded login or registration prompts. */
    public boolean isPromptMessage(String message) { return !prompts.matchingPrompts(message).isEmpty(); }
    /** Returns whether an autofill or registration command has already been attempted. */
    public boolean attempted() { return attempted; }

    /** Applies client preferences on the session thread before future prompt handling. */
    public void configure(boolean autofill, long delayMillis, PasswordGenerator.Policy nextPolicy) {
        configure(autofill, true, delayMillis, nextPolicy);
    }

    public void configure(boolean autofill, boolean passwordless, long delayMillis, PasswordGenerator.Policy nextPolicy) {
        if (delayMillis < 200 || delayMillis > 5000 || nextPolicy == null) {
            throw new IllegalArgumentException("Invalid session preferences");
        }
        globalAutofill = autofill;
        passwordlessLogin = passwordless;
        autofillDelayMillis = delayMillis;
        policy = nextPolicy;
    }

    /** Clears connection state and in-memory pending credentials. */
    public void onDisconnect() {
        generation++;
        cancelTimer();
        wipePending();
        host = null;
        username = null;
        state = SessionState.IDLE;
        action = Action.NONE;
        attempted = false;
        registerRetry = false;
        lastPromptAt = Long.MIN_VALUE;
        lastPromptKind = null;
        dismissedPromptKinds.clear();
        promptedShortAliases.clear();
        consumedShortAliases.clear();
        shortAliasPromptAt = Long.MIN_VALUE;
        promptListeningUntil = Long.MIN_VALUE;
        promptRevision++;
        template = CommandTemplate.builtins().get("authme");
    }

    private boolean contains(String group, String command) { return pack.commandNames(group).contains(command); }

    private boolean isShortAuthenticationAlias(String command) {
        if (command.length() > 3) return false;
        return contains("login", command) || contains("register", command)
                || contains("changepassword", command) || contains("unregister", command);
    }

    private boolean consumePromptedShortAlias(String command) {
        return shortAliasPromptAt != Long.MIN_VALUE && scheduler.nowMillis() - shortAliasPromptAt <= 30_000
                && promptedShortAliases.contains(command) && consumedShortAliases.add(command);
    }

    /** Used by command history so an unrelated short command is not suppressed. */
    public boolean wasShortAliasPrompted(String command) {
        return shortAliasPromptAt != Long.MIN_VALUE && scheduler.nowMillis() - shortAliasPromptAt <= 30_000
                && promptedShortAliases.contains(command.toLowerCase(Locale.ROOT));
    }

    private void safely(Runnable callback) {
        try { callback.run(); }
        catch (RuntimeException failure) {
            cancelTimer();
            action = Action.NONE;
            state = SessionState.UNKNOWN;
            attempted = false;
            wipePending();
            try { env.toast("blockvault.toast.send_failed"); }
            catch (RuntimeException ignored) { /* A failed UI callback must not escape onto the game thread. */ }
        }
    }

    private void arm(PromptKind kind, String plain) {
        if (dismissedPromptKinds.contains(kind)) return;
        long now = scheduler.nowMillis();
        if ((lastPromptKind == kind && lastPromptAt != Long.MIN_VALUE && now - lastPromptAt < DEBOUNCE_MILLIS)
                || state == SessionState.SENT || action == Action.REGISTER || attempted
                || state == SessionState.CONFIRMED) return;
        cancelTimer();
        wipePending();
        action = Action.NONE;
        lastPromptAt = now;
        lastPromptKind = kind;
        int revision = ++promptRevision;
        state = SessionState.PROMPTED;
        if (plain != null && kind == PromptKind.REGISTER) {
            template = CommandTemplate.fromRegistrationPrompt(plain, pack.commandNames("register"));
        }
        int token = generation;
        env.vault().find(host, username, saved -> safely(() -> {
            if (token != generation || revision != promptRevision || state != SessionState.PROMPTED) return;
            if (saved != null && saved.templateExplicit() && saved.template() != null) {
                template = saved.template();
            }
            Account account = saved == null ? null : saved.account();
            if (kind == PromptKind.REGISTER) {
                if (account != null && account.isValidForUse() && account.status() == Account.Status.PENDING) {
                    char[] oldPassword = account.password();
                    try { env.showSavePrompt(new PromptModel(CardVariant.UPDATE, host, username, oldPassword)); }
                    finally { Arrays.fill(oldPassword, '\0'); }
                } else {
                    char[] generated = PasswordGenerator.generate(policy);
                    try { env.showSavePrompt(new PromptModel(account == null ? CardVariant.REGISTER : CardVariant.UPDATE,
                            host, username, generated, template.register().contains("{email}"), policy)); }
                    finally { Arrays.fill(generated, '\0'); }
                }
                return;
            }
            if (account != null && account.isValidForUse() && account.status() == Account.Status.PENDING) {
                char[] pendingPassword = account.password();
                try { env.showSavePrompt(new PromptModel(CardVariant.PENDING_LOGIN, host, username, pendingPassword)); }
                finally { Arrays.fill(pendingPassword, '\0'); }
                return;
            }
            if (!globalAutofill || saved == null || account == null || !account.isValidForUse()
                    || account.status() != Account.Status.VERIFIED
                    || !saved.autofill()) return;
            char[] storedPassword = account.password();
            try { replacePending(storedPassword); }
            finally { Arrays.fill(storedPassword, '\0'); }
            action = Action.AUTOFILL;
            wipeStaged();
            autofillStaged = pending.clone();
            timer = scheduler.schedule(autofillDelayMillis, () -> {
                char[] forSend = autofillStaged;
                autofillStaged = null;
                try {
                    if (forSend == null || token != generation || revision != promptRevision
                            || state != SessionState.PROMPTED || attempted) return;
                    replacePending(forSend);
                    send(false, null, token);
                } finally { if (forSend != null) Arrays.fill(forSend, '\0'); }
            });
        }), () -> safely(() -> { if (revision == promptRevision) abandonPrompt(token); }));
    }

    /** The owner cancelled the master-password unlock: stop prompting for this kind until disconnect. */
    private void abandonPrompt(int token) {
        if (token != generation || state != SessionState.PROMPTED) return;
        dismissPrompt();
    }

    private void send(boolean registration, String email, int token) {
        if (token != generation || pending == null) return;
        char[] copy = pending.clone();
        String outgoing;
        try {
            outgoing = template.render(registration, copy, email, username);
        } catch (RuntimeException invalidTemplate) {
            state = SessionState.UNKNOWN;
            action = Action.NONE;
            wipePending();
            env.cardResult(CardResult.FAILURE);
            env.toast("blockvault.toast.send_failed");
            return;
        } finally {
            Arrays.fill(copy, '\0');
        }
        cancelTimer();
        attempted = true;
        state = SessionState.SENT;
        try { env.sendCommand(outgoing); }
        catch (RuntimeException sendFailure) {
            attempted = false;
            state = SessionState.UNKNOWN;
            action = Action.NONE;
            wipePending();
            env.cardResult(CardResult.FAILURE);
            env.toast("blockvault.toast.send_failed");
            return;
        }
        env.cardResult(CardResult.SENDING);
        timer = scheduler.schedule(RESULT_TIMEOUT_MILLIS, () -> {
            if (token == generation && state == SessionState.SENT) {
                state = SessionState.UNKNOWN;
                wipePending();
                action = Action.NONE;
                env.cardResult(CardResult.TIMEOUT);
                env.toast("blockvault.toast.result_timeout");
            }
        });
    }

    private void onResult(ResultKind result, String plain) {
        if (result == ResultKind.ALREADY_LOGGED_IN) {
            cancelTimer();
            wipePending();
            action = Action.NONE;
            attempted = false;
            state = SessionState.UNKNOWN;
            env.cardResult(CardResult.ALREADY_LOGGED_IN);
        } else if (result == ResultKind.SUCCESS) {
            if (state == SessionState.SENT && action != Action.NONE) {
                cancelTimer();
                Action completed = action;
                if (completed == Action.UNREGISTER) env.vault().delete(host, username);
                else if (completed == Action.MANUAL) {
                    int token = generation;
                    char[] captured = pending.clone();
                    env.vault().find(host, username, saved -> safely(() -> {
                        try {
                            if (token != generation || host == null) return;
                            Account existing = saved == null ? null : saved.account();
                            char[] oldPassword = existing == null ? null : existing.password();
                            boolean same = oldPassword != null && Arrays.equals(oldPassword, captured);
                            boolean pendingAccount = existing != null && existing.status() == Account.Status.PENDING;
                            if (oldPassword != null) Arrays.fill(oldPassword, '\0');
                            if (same && pendingAccount) env.vault().markVerified(host, username);
                            else if (!same) env.showSavePrompt(new PromptModel(existing == null
                                    ? CardVariant.SAVE : CardVariant.UPDATE, host, username, captured));
                            state = SessionState.CONFIRMED;
                        } finally { Arrays.fill(captured, '\0'); }
                    }));
                    wipePending();
                    action = Action.NONE;
                    return;
                } else {
                    env.vault().markVerified(host, username);
                    if (completed == Action.AUTOFILL) env.chatMessage("blockvault.chat.autologin_success");
                    else if (completed == Action.PASSWORDLESS) env.chatMessage("blockvault.chat.passwordless_login_success");
                }
                if (completed == Action.REGISTER || completed == Action.AUTOFILL
                        || completed == Action.PASSWORDLESS) env.cardResult(CardResult.SUCCESS);
                state = SessionState.CONFIRMED;
                wipePending();
                action = Action.NONE;
            }
        } else if (result == ResultKind.FAILURE && state == SessionState.SENT) {
            cancelTimer();
            env.cardResult(CardResult.FAILURE);
            if (action == Action.REGISTER) {
                // The pending credential is kept: the server may have accepted it and a misread message must
                // never cost the owner the only copy of a generated password.
                attempted = false;
                registerRetry = false;
                dismissedPromptKinds.add(PromptKind.REGISTER);
            }
            if (action == Action.AUTOFILL || action == Action.PASSWORDLESS) {
                state = SessionState.REJECTED;
                env.showSavePrompt(new PromptModel(CardVariant.REJECTED, host, username, null));
            } else state = SessionState.REJECTED;
            wipePending();
            action = Action.NONE;
        } else if (result == ResultKind.PASSWORD_RULE && state == SessionState.SENT && action == Action.REGISTER) {
            cancelTimer();
            env.cardResult(CardResult.FAILURE);
            // The server explicitly rejected this password. Do not leave it as a PENDING
            // account where the next visit could offer the rejected value again.
            env.vault().delete(host, username);
            if (!registerRetry) {
                registerRetry = true;
                var hint = results.parseRuleHint(plain);
                try { policy = hint.isPresent() ? policy.adjustFor(hint.orElseThrow()) : FALLBACK_POLICY; }
                catch (IllegalArgumentException unsupportedRule) {
                    // The configured preset cannot satisfy this rule (for example memorable words vs. alphanumeric).
                    policy = FALLBACK_POLICY;
                }
                wipePending();
                action = Action.NONE;
                state = SessionState.PROMPTED;
                char[] generated = PasswordGenerator.generate(policy);
                try { env.showSavePrompt(new PromptModel(CardVariant.REGISTER, host, username, generated,
                        template.register().contains("{email}"), policy)); }
                finally { Arrays.fill(generated, '\0'); }
                return;
            }
            wipePending();
            attempted = false;
            registerRetry = false;
            state = SessionState.REJECTED;
            action = Action.NONE;
            env.toast("blockvault.toast.rule_retry_failed");
        }
    }

    private void replacePending(char[] chars) {
        wipePending();
        pending = chars == null ? null : chars.clone();
    }

    private void replacePending(String secret) {
        replacePending(secret == null ? null : secret.toCharArray());
    }

    private void cancelTimer() {
        if (timer != null) timer.cancel();
        timer = null;
        wipeStaged();
    }

    private void wipeStaged() {
        if (autofillStaged != null) Arrays.fill(autofillStaged, '\0');
        autofillStaged = null;
    }

    private void wipePending() {
        if (pending != null) Arrays.fill(pending, '\0');
        pending = null;
    }

}
