package io.github.silkypanda.blockvault.core.session;

import io.github.silkypanda.blockvault.core.detect.PatternPack;
import io.github.silkypanda.blockvault.core.detect.PromptKind;
import io.github.silkypanda.blockvault.core.detect.PromptMatcher;
import io.github.silkypanda.blockvault.core.detect.ResultKind;
import io.github.silkypanda.blockvault.core.detect.ResultMatcher;
import io.github.silkypanda.blockvault.core.gen.PasswordGenerator;
import io.github.silkypanda.blockvault.core.vault.Account;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Pure-Java login prompt state machine. Vault callbacks must return on the session thread. */
public final class LoginSession {
    private static final long DEBOUNCE_MILLIS = 1500;
    private static final long RESULT_TIMEOUT_MILLIS = 5000;
    private static final Pattern COMMAND = Pattern.compile("(?iu)^\\s*/?([\\p{L}0-9_]+)(?:\\s+(.*))?$");

    private enum Action { NONE, AUTOFILL, REGISTER, MANUAL, UNREGISTER }

    private final SessionEnv env;
    private final Scheduler scheduler;
    private final PromptMatcher prompts;
    private final ResultMatcher results;
    private final PatternPack pack;
    private PasswordGenerator.Policy policy;
    private boolean globalAutofill = true;
    private long autofillDelayMillis = 400;
    private SessionState state = SessionState.IDLE;
    private String host;
    private String username;
    private CommandTemplate template = CommandTemplate.builtins().get("authme");
    private char[] pending;
    private Action action = Action.NONE;
    private long lastPromptAt = Long.MIN_VALUE;
    private PromptKind lastPromptKind;
    private PromptKind dismissedPromptKind;
    private boolean attempted;
    private boolean registerRetry;
    private boolean canRegister;
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
    }

    /** Arms from known root command names. */
    public void onCommandTree(Set<String> rootCommandNames) {
        if (host == null || rootCommandNames == null) return;
        Set<String> names = rootCommandNames.stream().map(s -> s.toLowerCase(Locale.ROOT))
                .collect(java.util.stream.Collectors.toSet());
        boolean login = !java.util.Collections.disjoint(names, pack.commandNames("login"));
        boolean register = !java.util.Collections.disjoint(names, pack.commandNames("register"));
        if (login || register) {
            canRegister = !login && register;
            arm(canRegister ? PromptKind.REGISTER : PromptKind.LOGIN, null);
        }
    }

    /** Processes a plain system message only; callers must exclude signed player chat. */
    public void onSystemMessage(String plain) {
        if (host == null || state == SessionState.CONFIRMED) return;
        prompts.matchPrompt(plain).ifPresent(kind -> {
            if (kind == dismissedPromptKind) return;
            dismissedPromptKind = null;
            canRegister = kind == PromptKind.REGISTER;
            arm(kind, plain);
        });
        ResultKind result = results.classify(plain);
        if (result != ResultKind.NONE) onResult(result, plain);
    }

    /** Captures a sensitive command in memory for possible post-success saving. */
    public void onOutgoingCommand(String commandWithoutSlash) {
        if (host == null || commandWithoutSlash == null) return;
        Matcher matcher = COMMAND.matcher(commandWithoutSlash);
        if (!matcher.matches()) return;
        String command = matcher.group(1).toLowerCase(Locale.ROOT);
        String[] words = matcher.group(2) == null ? new String[0] : matcher.group(2).trim().split("\\s+");
        if (words.length == 0 || words[0].isEmpty()) return;
        if (contains("login", command)) {
            replacePending(words[0]);
            action = Action.MANUAL;
        } else if (contains("register", command)) {
            replacePending(words[0]);
            action = Action.MANUAL;
        } else if (contains("changepassword", command)) {
            if (words.length < 2) return;
            replacePending(words[1]);
            action = Action.MANUAL;
        } else if (contains("unregister", command)) {
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

    /** Confirms the visible registration card; pending data is persisted before command transmission. */
    public void confirmRegistration(char[] password) { confirmRegistration(password, null); }

    /** Confirms registration, including an optional email requested by the detected prompt. */
    public void confirmRegistration(char[] password, String email) {
        if (host == null || state != SessionState.PROMPTED || !canRegister || action == Action.REGISTER) return;
        replacePending(password);
        action = Action.REGISTER;
        // Close the prompt race while the pending credential is persisted. Repeated
        // registration messages must not queue a second card before send() runs.
        state = SessionState.SENT;
        int token = generation;
        char[] staged = pending.clone();
        env.vault().savePending(host, username, staged, saved -> {
            Arrays.fill(staged, '\0');
            if (token != generation || state != SessionState.SENT || action != Action.REGISTER) return;
            if (!saved) { wipePending(); state = SessionState.UNKNOWN; return; }
            send(true, email, token);
        });
    }

    /** Saves a manually captured password after the owner confirms its card. */
    public void confirmCapturedSave(char[] password) {
        if (host == null || state != SessionState.CONFIRMED) return;
        char[] staged = password.clone();
        try { env.vault().saveVerified(host, username, staged); }
        finally { Arrays.fill(staged, '\0'); }
    }

    /** Updates the rejected server credential after an explicit user confirmation. */
    public void updateRejectedPassword(char[] password) {
        if (host == null || state != SessionState.REJECTED) return;
        char[] staged = password.clone();
        try { env.vault().saveVerified(host, username, staged); }
        finally { Arrays.fill(staged, '\0'); }
        state = SessionState.CONFIRMED;
    }

    /** Removes a rejected credential after an explicit user confirmation. */
    public void forgetRejectedCredential() {
        if (host == null || state != SessionState.REJECTED) return;
        env.vault().delete(host, username);
        state = SessionState.CONFIRMED;
    }

    /** Dismisses a rejected-password prompt without changing the vault. */
    public void dismissRejectedPrompt() {
        if (state == SessionState.REJECTED) {
            dismissedPromptKind = lastPromptKind;
            state = SessionState.UNKNOWN;
        }
    }

    /** Dismisses a pending save or registration card until the server shows a different prompt. */
    public void dismissPrompt() {
        if (state != SessionState.PROMPTED) return;
        dismissedPromptKind = lastPromptKind;
        state = SessionState.UNKNOWN;
        action = Action.NONE;
        promptRevision++;
        cancelTimer();
        wipePending();
    }

    /** Returns the current connection state. */
    public SessionState state() { return state; }
    /** Returns whether an autofill or registration command has already been attempted. */
    public boolean attempted() { return attempted; }

    /** Applies client preferences on the session thread before future prompt handling. */
    public void configure(boolean autofill, long delayMillis, PasswordGenerator.Policy nextPolicy) {
        if (delayMillis < 0 || delayMillis > 5000 || nextPolicy == null) {
            throw new IllegalArgumentException("Invalid session preferences");
        }
        globalAutofill = autofill;
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
        canRegister = false;
        lastPromptAt = Long.MIN_VALUE;
        lastPromptKind = null;
        dismissedPromptKind = null;
        promptRevision++;
        template = CommandTemplate.builtins().get("authme");
    }

    private boolean contains(String group, String command) { return pack.commandNames(group).contains(command); }

    private void arm(PromptKind kind, String plain) {
        if (kind == dismissedPromptKind) return;
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
        env.vault().find(host, username, saved -> {
            if (token != generation || revision != promptRevision || state != SessionState.PROMPTED) return;
            if (saved != null && saved.template() != null) template = saved.template();
            Account account = saved == null ? null : saved.account();
            if (kind == PromptKind.REGISTER) {
                if (saved != null && saved.neverSave()) return;
                if (account != null && account.status() == Account.Status.PENDING) {
                    char[] oldPassword = account.password();
                    try { env.showSavePrompt(new PromptModel(CardVariant.UPDATE, host, username, oldPassword)); }
                    finally { Arrays.fill(oldPassword, '\0'); }
                } else {
                    char[] generated = PasswordGenerator.generate(policy);
                    try { env.showSavePrompt(new PromptModel(account == null ? CardVariant.REGISTER : CardVariant.UPDATE,
                            host, username, generated, template.register().contains("{email}"))); }
                    finally { Arrays.fill(generated, '\0'); }
                }
                return;
            }
            if (!globalAutofill || saved == null || account == null || account.status() != Account.Status.VERIFIED
                    || !saved.autofill() || saved.neverSave()) return;
            char[] storedPassword = account.password();
            try { replacePending(storedPassword); }
            finally { Arrays.fill(storedPassword, '\0'); }
            action = Action.AUTOFILL;
            char[] forSend = pending.clone();
            timer = scheduler.schedule(autofillDelayMillis, () -> {
                try {
                    if (token != generation || revision != promptRevision || state != SessionState.PROMPTED || attempted) return;
                    replacePending(forSend);
                    send(false, null, token);
                } finally { Arrays.fill(forSend, '\0'); }
            });
        });
    }

    private void send(boolean registration, String email, int token) {
        if (token != generation || pending == null) return;
        cancelTimer();
        attempted = true;
        state = SessionState.SENT;
        char[] copy = pending.clone();
        try { env.sendCommand(template.render(registration, copy, email, username, null)); }
        finally { Arrays.fill(copy, '\0'); }
        timer = scheduler.schedule(RESULT_TIMEOUT_MILLIS, () -> {
            if (token == generation && state == SessionState.SENT) {
                state = SessionState.UNKNOWN;
                wipePending();
            }
        });
    }

    private void onResult(ResultKind result, String plain) {
        if (result == ResultKind.SUCCESS) {
            if (state == SessionState.SENT && action != Action.NONE) {
                cancelTimer();
                Action completed = action;
                if (completed == Action.UNREGISTER) env.vault().delete(host, username);
                else if (completed == Action.MANUAL) {
                    int token = generation;
                    char[] captured = pending.clone();
                    env.vault().find(host, username, saved -> {
                        try {
                            if (token != generation || host == null) return;
                            Account existing = saved == null ? null : saved.account();
                            char[] oldPassword = existing == null ? null : existing.password();
                            boolean same = oldPassword != null && Arrays.equals(oldPassword, captured);
                            if (oldPassword != null) Arrays.fill(oldPassword, '\0');
                            if (!same && (saved == null || !saved.neverSave())) env.showSavePrompt(new PromptModel(existing == null
                                    ? CardVariant.SAVE : CardVariant.UPDATE, host, username, captured));
                            state = SessionState.CONFIRMED;
                        } finally { Arrays.fill(captured, '\0'); }
                    });
                    wipePending();
                    action = Action.NONE;
                    return;
                } else {
                    env.vault().markVerified(host, username);
                    if (completed == Action.REGISTER) env.toast("blockvault.toast.saved");
                }
                state = SessionState.CONFIRMED;
                wipePending();
                action = Action.NONE;
            }
        } else if (result == ResultKind.FAILURE && state == SessionState.SENT) {
            cancelTimer();
            if (action == Action.REGISTER) env.vault().delete(host, username);
            if (action == Action.AUTOFILL) {
                state = SessionState.REJECTED;
                env.showSavePrompt(new PromptModel(CardVariant.REJECTED, host, username, pending));
            } else state = SessionState.REJECTED;
            wipePending();
            action = Action.NONE;
        } else if (result == ResultKind.PASSWORD_RULE && state == SessionState.SENT && action == Action.REGISTER) {
            cancelTimer();
            env.vault().delete(host, username);
            if (!registerRetry) {
                var hint = results.parseRuleHint(plain);
                if (hint.isPresent()) {
                    registerRetry = true;
                    policy = policy.adjustFor(hint.orElseThrow());
                    wipePending();
                    action = Action.NONE;
                    state = SessionState.PROMPTED;
                    char[] generated = PasswordGenerator.generate(policy);
                    try { env.showSavePrompt(new PromptModel(CardVariant.REGISTER, host, username, generated,
                            template.register().contains("{email}"))); }
                    finally { Arrays.fill(generated, '\0'); }
                    return;
                }
            }
            wipePending();
            state = SessionState.REJECTED;
            action = Action.NONE;
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
    }

    private void wipePending() {
        if (pending != null) Arrays.fill(pending, '\0');
        pending = null;
    }
}
