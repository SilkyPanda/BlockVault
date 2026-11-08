package io.github.silkypanda.blockvault;

import io.github.silkypanda.blockvault.core.detect.PatternPack;
import io.github.silkypanda.blockvault.core.gen.PasswordGenerator;
import io.github.silkypanda.blockvault.core.host.HostKey;
import io.github.silkypanda.blockvault.core.session.LoginSession;
import io.github.silkypanda.blockvault.core.session.CommandPolicy;
import io.github.silkypanda.blockvault.core.session.SessionPreferences;
import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Bridges verified loader callbacks and Minecraft mixins to the pure-Java session. */
public final class Hooks {
    private static final Logger LOGGER = LoggerFactory.getLogger("BlockVault");
    private static final ThreadLocal<Boolean> SELF_SENT = ThreadLocal.withInitial(() -> false);
    private static final ThreadLocal<Boolean> HISTORY_SCRUBBED = ThreadLocal.withInitial(() -> false);
    private static final BackgroundExecutor SCHEDULER = BackgroundExecutor.scheduler();
    private static final int MAX_QUEUED_JOIN_MESSAGES = 32;
    private static final SessionState STATE = new SessionState();

    private Hooks() { }

    /** Starts a multiplayer-only session when Minecraft has a current remote server. */
    public static void onJoin() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.getCurrentServer() == null || minecraft.getUser() == null) {
            onDisconnect();
            return;
        }
        String address = minecraft.getCurrentServer().ip;
        String host = HostKey.normalize(address).orElse(null);
        if (host == null) {
            onDisconnect();
            return;
        }
        onDisconnect();
        VaultRuntime.deliverPendingTrustNotice();
        long revision = STATE.connectionRevision;
        String mcUsername = minecraft.getUser().getName();
        STATE.patternsLoading = true;
        BackgroundExecutor.submit(() -> {
            PatternPack loaded;
            try { loaded = loadPatterns(); }
            catch (Exception failure) {
                BackgroundExecutor.onGameThread(() -> {
                    if (STATE.connectionRevision != revision) return;
                    LOGGER.warn("BlockVault could not initialize login detection");
                    onDisconnect();
                });
                return null;
            }
            BackgroundExecutor.onGameThread(() -> {
                Minecraft current = Minecraft.getInstance();
                if (STATE.connectionRevision != revision || current.getCurrentServer() == null
                        || !host.equals(HostKey.normalize(current.getCurrentServer().ip).orElse(null))
                        || current.getUser() == null || !mcUsername.equals(current.getUser().getName())) return;
                STATE.patternsLoading = false;
                STATE.patterns = loaded;
                STATE.sensitiveAliases = java.util.stream.Stream.of("login", "register", "changepassword", "unregister")
                        .flatMap(kind -> STATE.patterns.commandNames(kind).stream()).collect(Collectors.toUnmodifiableSet());
                STATE.environment = new McSessionEnv();
                LoginSession active = new LoginSession(STATE.environment, SCHEDULER, STATE.patterns, PasswordGenerator.Policy.pickyServer());
                STATE.session = active;
                STATE.sessionHost = host;
                STATE.sessionUser = mcUsername;
                STATE.sessionErrors = 0;
                STATE.environment.attach(active);
                active.onJoin(host, mcUsername);
                List<String> earlyMessages = new ArrayList<>(STATE.queuedJoinMessages);
                STATE.queuedJoinMessages.clear();
                BlockVaultConfig.load(settings -> {
                    if (STATE.session != active) return;
                    configure(active, settings);
                    STATE.settingsLoaded = true;
                    for (String early : earlyMessages) {
                        if (active.isPromptMessage(early)) guard(active, () -> active.onSystemMessage(early));
                    }
                    String queued = STATE.queuedPrompt;
                    STATE.queuedPrompt = null;
                    if (queued != null) guard(active, () -> active.onSystemMessage(queued));
                });
            });
            return null;
        });
    }

    private static PatternPack loadPatterns() throws java.io.IOException {
        InputStream defaults = Hooks.class.getResourceAsStream("/assets/blockvault/patterns/default.json");
        if (defaults == null) throw new java.io.IOException("Built-in pattern resource is missing");
        return PatternPack.load(defaults, io.github.silkypanda.blockvault.core.vault.DataDirs.resolve()
                .resolve("patterns.json"), warning -> LOGGER.warn("BlockVault skipped an invalid pattern"));
    }

    static boolean isSensitiveAlias(String command) {
        String normalized = command.toLowerCase(java.util.Locale.ROOT);
        if (!STATE.sensitiveAliases.contains(normalized)) return false;
        if (normalized.length() > 3) return true;
        LoginSession active = STATE.session;
        return CommandPolicy.isSensitiveAlias(normalized, STATE.sensitiveAliases,
                active != null && active.wasShortAliasPrompted(normalized));
    }

    /** Ends the active session and wipes its in-memory credentials. */
    public static void onDisconnect() {
        STATE.connectionRevision++;
        STATE.patternsLoading = false;
        STATE.queuedJoinMessages.clear();
        if (STATE.session != null) STATE.session.onDisconnect();
        if (STATE.environment != null) STATE.environment.disconnect();
        VaultRuntime.clearWaiting();
        STATE.session = null;
        STATE.environment = null;
        STATE.settingsLoaded = false;
        STATE.queuedPrompt = null;
    }

    static void applySettings(BlockVaultConfig.Settings settings) {
        LoginSession active = STATE.session;
        if (active != null) configure(active, settings);
    }

    private static void configure(LoginSession active, BlockVaultConfig.Settings settings) {
        SessionPreferences.apply(active, settings.autofill(), settings.passwordlessLogin(),
                settings.autofillDelayMillis(), settings.generator());
    }

    /** Delivers plain system chat to the login detector. */
    public static void onSystemMessage(Component message) {
        LoginSession active = STATE.session;
        if (message == null) return;
        String plain = message.getString();
        if (active == null) {
            if (STATE.patternsLoading && plain.length() <= 2048) {
                if (STATE.queuedJoinMessages.size() >= MAX_QUEUED_JOIN_MESSAGES) STATE.queuedJoinMessages.removeFirst();
                STATE.queuedJoinMessages.addLast(plain);
            }
            return;
        }
        if (!STATE.settingsLoaded) {
            if (plain.length() <= 2048 && active.isPromptMessage(plain)) STATE.queuedPrompt = plain;
            return;
        }
        guard(active, () -> active.onSystemMessage(plain));
    }

    /** Captures an outgoing command without logging or retaining it outside the session. */
    public static boolean onOutgoingCommand(String command) {
        STATE.commandHookSeen = true;
        if (Boolean.TRUE.equals(SELF_SENT.get())) return false;
        checkHistoryScrubbing(command);
        LoginSession active = STATE.session;
        if (active == null) return false;
        String name = CommandPolicy.bareCommandName(command);
        if (name != null && STATE.patterns != null && STATE.patterns.commandNames("login").contains(name)
                && active.shouldInterceptBareLogin(name)
                && CommandPolicy.shouldHoldBareLogin(command, STATE.patterns.commandNames("login"), true,
                name.equals("login") || active.wasShortAliasPrompted(name))) {
            guard(active, () -> active.onOutgoingCommand(command));
            return true;
        }
        guard(active, () -> active.onOutgoingCommand(command));
        return false;
    }

    /** Called by the command-history mixin each time it keeps a sensitive command out of the history. */
    public static void historyScrubbed() { HISTORY_SCRUBBED.set(true); }

    /**
     * When the player types a sensitive command, the history mixin should scrub it within moments. If it did not,
     * the mixin is not applied (for example after a Minecraft update), so the owner is warned once.
     */
    private static void checkHistoryScrubbing(String command) {
        if (STATE.warnedHistoryHook || !SensitiveCommands.isSensitive(command)) return;
        boolean scrubbed = HISTORY_SCRUBBED.get();
        HISTORY_SCRUBBED.remove();
        if (!scrubbed) warnOnce(false);
    }

    private static void warnOnce(boolean commandHook) {
        if (commandHook ? STATE.warnedCommandHook : STATE.warnedHistoryHook) return;
        if (commandHook) STATE.warnedCommandHook = true; else STATE.warnedHistoryHook = true;
        LOGGER.warn("BlockVault's {} hook is not active in this Minecraft version", commandHook ? "command" : "history");
        VaultRuntime.showToast(commandHook ? "blockvault.toast.hooks_inactive.commands"
                : "blockvault.toast.hooks_inactive.history");
    }

    private static void guard(LoginSession active, Runnable operation) {
        try { operation.run(); }
        catch (RuntimeException failure) {
            LOGGER.warn("BlockVault hit an input error ({})", failure.getClass().getSimpleName());
            if (STATE.session != active) return;
            active.onDisconnect();
            // One bad message must not end login handling: start the session over, and only give up after repeats.
            if (++STATE.sessionErrors >= 3) {
                LOGGER.warn("BlockVault stopped login handling for this connection after repeated errors");
                STATE.session = null;
            } else {
                active.onJoin(STATE.sessionHost, STATE.sessionUser);
            }
        }
    }

    /** Sends a command generated by BlockVault while suppressing the manual-capture hook. */
    public static void sendOwnCommand(String commandWithoutSlash) {
        Minecraft minecraft = Minecraft.getInstance();
        Runnable send = () -> {
            ClientPacketListener connection = minecraft.getConnection();
            if (connection == null) return;
            SELF_SENT.set(true);
            STATE.commandHookSeen = false;
            try { connection.sendCommand(commandWithoutSlash); }
            finally { SELF_SENT.remove(); }
            // The command mixin runs at the start of sendCommand, so it must have run by now.
            if (!STATE.commandHookSeen) warnOnce(true);
        };
        if (minecraft.isSameThread()) send.run();
        else minecraft.execute(send);
    }

    /** Mutable state shared by loader callbacks and the game-thread login hooks. */
    private static final class SessionState {
        final ArrayDeque<String> queuedJoinMessages = new ArrayDeque<>();
        volatile PatternPack patterns;
        volatile Set<String> sensitiveAliases = Set.of("login", "register", "reg", "changepassword", "changepass", "unregister");
        LoginSession session;
        McSessionEnv environment;
        /** Set by the outgoing-command mixin; used to detect that the mixin is really applied. */
        volatile boolean commandHookSeen;
        boolean warnedCommandHook;
        String sessionHost;
        String sessionUser;
        int sessionErrors;
        boolean warnedHistoryHook;
        boolean settingsLoaded;
        String queuedPrompt;
        long connectionRevision;
        boolean patternsLoading;
    }

}
