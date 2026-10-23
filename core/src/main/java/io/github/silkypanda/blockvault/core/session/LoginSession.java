package io.github.silkypanda.blockvault.core.session;

import io.github.silkypanda.blockvault.core.detect.PatternPack;
import io.github.silkypanda.blockvault.core.detect.PromptKind;
import io.github.silkypanda.blockvault.core.detect.PromptMatcher;
import io.github.silkypanda.blockvault.core.detect.ResultKind;
import io.github.silkypanda.blockvault.core.detect.ResultMatcher;
import io.github.silkypanda.blockvault.core.gen.PasswordGenerator;
import io.github.silkypanda.blockvault.core.vault.Account;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Pure-Java login prompt state machine. Callbacks from vault access should return on the session thread. */
public final class LoginSession {
    private static final Pattern COMMAND = Pattern.compile("(?iu)^\\s*/?(login|l|log|register|reg|changepassword|changepass|unregister)\\s+(.+)$");
    private final SessionEnv env;
    private final Scheduler scheduler;
    private final PromptMatcher prompts;
    private final ResultMatcher results;
    private PasswordGenerator.Policy policy;
    private SessionState state = SessionState.IDLE;
    private String host;
    private String username;
    private CommandTemplate template = CommandTemplate.builtins().get("authme");
    private char[] pending;
    private long lastPromptAt = Long.MIN_VALUE;
    private boolean attempted;
    private boolean registerRetry;
    private int generation;

    /** Creates a connection session with immutable matchers and password policy. */
    public LoginSession(SessionEnv env, Scheduler scheduler, PatternPack pack, PasswordGenerator.Policy policy) {
        this.env = env;
        this.scheduler = scheduler;
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
        Set<String> lower = rootCommandNames.stream().map(s -> s.toLowerCase(Locale.ROOT)).collect(java.util.stream.Collectors.toSet());
        if (lower.contains("login") || lower.contains("register") || lower.contains("reg")) arm(lower.contains("register") || lower.contains("reg") ? PromptKind.REGISTER : PromptKind.LOGIN);
    }

    /** Processes a plain system message only; callers must exclude signed player chat. */
    public void onSystemMessage(String plain) {
        if (host == null || state == SessionState.CONFIRMED) return;
        prompts.matchPrompt(plain).ifPresent(this::arm);
        ResultKind result = results.classify(plain);
        if (result != ResultKind.NONE) onResult(result, plain);
    }

    /** Captures a sensitive command in memory for possible post-success saving. */
    public void onOutgoingCommand(String commandWithoutSlash) {
        if (host == null || commandWithoutSlash == null) return;
        Matcher matcher = COMMAND.matcher(commandWithoutSlash);
        if (!matcher.matches()) return;
        String[] words = matcher.group(2).trim().split("\\s+");
        if (words.length == 0) return;
        wipePending();
        pending = words[words.length - 1].toCharArray();
    }

    /** Confirms the visible registration card; pending data is persisted before command transmission. */
    public void confirmRegistration(char[] password) {
        if (host == null || state != SessionState.PROMPTED) return;
        wipePending();
        pending = password.clone();
        int token = generation;
        char[] stagedPassword = pending.clone();
        env.vault().savePending(host, username, stagedPassword, saved -> {
            java.util.Arrays.fill(stagedPassword, '\0');
            if (token != generation || state != SessionState.PROMPTED) return;
            if (!saved) { wipePending(); state = SessionState.UNKNOWN; return; }
            env.sendCommand(template.render(true, pending, null, username, null));
            attempted = true;
            state = SessionState.SENT;
            scheduleTimeout(token);
        });
    }

    /** Saves a manually captured password after the owner confirms its card. */
    public void confirmCapturedSave(char[] password) {
        if (host != null && state == SessionState.CONFIRMED) env.vault().saveVerified(host, username, password.clone());
    }

    /** Returns the current connection state. */
    public SessionState state() { return state; }
    /** Returns whether an autofill or registration command has already been attempted. */
    public boolean attempted() { return attempted; }

    /** Clears connection state and in-memory pending credentials. */
    public void onDisconnect() {
        generation++;
        wipePending();
        host = null;
        username = null;
        state = SessionState.IDLE;
        attempted = false;
        registerRetry = false;
        lastPromptAt = Long.MIN_VALUE;
    }

    private void arm(PromptKind kind) {
        long now = scheduler.nowMillis();
        if ((lastPromptAt != Long.MIN_VALUE && now - lastPromptAt < 1500) || state == SessionState.SENT || attempted) return;
        lastPromptAt = now;
        state = SessionState.PROMPTED;
        int token = generation;
        if (kind == PromptKind.REGISTER) {
            registerRetry = false;
            env.vault().find(host, username, existing -> {
                if (token != generation || state != SessionState.PROMPTED) return;
                char[] generated = PasswordGenerator.generate(policy);
                env.showSavePrompt(new PromptModel(existing == null ? CardVariant.REGISTER : CardVariant.UPDATE, host, username, generated));
                java.util.Arrays.fill(generated, '\0');
            });
            return;
        }
        env.vault().find(host, username, account -> {
            if (token != generation || state != SessionState.PROMPTED) return;
            if (account == null || account.status() != Account.Status.VERIFIED) return;
            pending = account.password();
            char[] passwordCopy = pending.clone();
            scheduler.schedule(400, () -> {
                try {
                    if (token != generation || state != SessionState.PROMPTED || attempted) return;
                    attempted = true;
                    state = SessionState.SENT;
                    env.sendCommand(template.render(false, passwordCopy, null, username, null));
                    scheduleTimeout(token);
                } finally {
                    java.util.Arrays.fill(passwordCopy, '\0');
                }
            });
        });
    }

    private void onResult(ResultKind result, String plain) {
        if (state != SessionState.SENT && pending == null) return;
        if (result == ResultKind.SUCCESS) {
            if (state == SessionState.SENT && pending != null) {
                env.vault().markVerified(host, username);
                env.toast("blockvault.toast.saved");
                state = SessionState.CONFIRMED;
            } else if (pending != null) {
                int token = generation;
                char[] captured = pending.clone();
                env.vault().find(host, username, existing -> {
                    try {
                        if (token != generation || host == null) return;
                        CardVariant variant = existing == null ? CardVariant.SAVE
                                : java.util.Arrays.equals(existing.password(), captured) ? null : CardVariant.UPDATE;
                        if (variant != null) env.showSavePrompt(new PromptModel(variant, host, username, captured));
                        state = SessionState.CONFIRMED;
                    } finally {
                        java.util.Arrays.fill(captured, '\0');
                    }
                });
            }
            wipePending();
        } else if (result == ResultKind.FAILURE) {
            if (state == SessionState.SENT) {
                state = SessionState.REJECTED;
                env.showSavePrompt(new PromptModel(CardVariant.REJECTED, host, username, pending));
            }
            wipePending();
        } else if (result == ResultKind.PASSWORD_RULE && state == SessionState.SENT) {
            java.util.Optional<io.github.silkypanda.blockvault.core.detect.ServerRuleHint> hint = results.parseRuleHint(plain);
            if (!registerRetry && hint.isPresent()) {
                registerRetry = true;
                env.vault().delete(host, username);
                policy = policy.adjustFor(hint.orElseThrow());
                char[] regenerated = PasswordGenerator.generate(policy);
                state = SessionState.PROMPTED;
                env.showSavePrompt(new PromptModel(CardVariant.REGISTER, host, username, regenerated));
                java.util.Arrays.fill(regenerated, '\0');
            } else {
                env.vault().delete(host, username);
                state = SessionState.REJECTED;
            }
            wipePending();
        }
    }

    private void scheduleTimeout(int token) {
        scheduler.schedule(5000, () -> {
            if (token == generation && state == SessionState.SENT) {
                state = SessionState.UNKNOWN;
                wipePending();
            }
        });
    }

    private void wipePending() {
        if (pending != null) java.util.Arrays.fill(pending, '\0');
        pending = null;
    }
}
