package nl.kmc.kmccore.managers;

import nl.kmc.kmccore.KMCCore;
import nl.kmc.kmccore.models.KMCGame;
import nl.kmc.kmccore.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

/**
 * AutomationManager drives the full tournament loop without admin input.
 *
 * <p>Flow per game cycle:
 * <pre>
 *  [GAME ACTIVE]
 *       ↓  /kmcgame stop  (or manual trigger)
 *  [INTERMISSION]  — countdown timer (bossbar + titles)
 *       ↓  countdown reaches 0
 *  [VOTING]        — players type 1/2/3 in chat
 *       ↓  vote duration expires → winner picked
 *  [PRE-START]     — short "game starts in X" countdown
 *       ↓  countdown reaches 0
 *  [GAME ACTIVE]   — loop repeats
 * </pre>
 *
 * <p>When all games in a round are done the round advances automatically.
 * When all rounds are done the tournament ends.
 *
 * <p>The automation can be paused/resumed by an admin at any time using
 * {@link #pause()} and {@link #resume()}.
 */
public class AutomationManager {

    // ----------------------------------------------------------------
    // States
    // ----------------------------------------------------------------
    public enum State {
        IDLE,           // automation not running
        GAME_ACTIVE,    // a game is currently being played
        INTERMISSION,   // counting down between games
        VOTING,         // vote is open
        PRE_START,      // counting down before game launch
        PAUSED          // admin paused automation
    }

    // ----------------------------------------------------------------
    // Fields
    // ----------------------------------------------------------------
    private final KMCCore plugin;

    private State       state           = State.IDLE;
    private State       stateBeforePause= State.IDLE;

    /** Games played in the current round (to know when to advance round). */
    private int         gamesThisRound  = 0;

    /** Current countdown value in seconds. */
    private int         countdownSeconds= 0;

    /** The ticking task (cancelled when state changes). */
    private BukkitTask  tickTask;

    /** BossBar used as a visual countdown bar. */
    private BossBar     bossBar;

    // ----------------------------------------------------------------
    // Constructor
    // ----------------------------------------------------------------
    public AutomationManager(KMCCore plugin) {
        this.plugin = plugin;
    }

    // ----------------------------------------------------------------
    // Public control
    // ----------------------------------------------------------------

    /**
     * Starts the automation engine.
     * Call this right after {@link TournamentManager#start()}.
     */
    public void start() {
        if (state != State.IDLE) return;
        gamesThisRound = 0;
        createBossBar();
        enterIntermission();
    }

    /**
     * Stops the automation engine completely (tournament ended).
     */
    public void stop() {
        cancelTick();
        hideBossBar();
        state = State.IDLE;
    }

    /**
     * Called by {@link GameManager} (or an admin command) when the
     * active game finishes. Triggers the intermission countdown.
     *
     * @param winnerName display name of winning team / player
     */
    public void onGameEnd(String winnerName) {
        if (state != State.GAME_ACTIVE) return;
        gamesThisRound++;

        int gamesPerRound = plugin.getConfig().getInt("automation.games-per-round", 3);
        boolean roundDone = gamesThisRound >= gamesPerRound;

        if (roundDone) {
            gamesThisRound = 0;
            boolean tournamentDone = !plugin.getTournamentManager().nextRound();
            if (tournamentDone) {
                endTournament(winnerName);
                return;
            }
        }

        enterIntermission();
    }

    /**
     * Pauses all automation ticking. The bossbar hides.
     */
    public void pause() {
        if (state == State.PAUSED || state == State.IDLE) return;
        stateBeforePause = state;
        state = State.PAUSED;
        cancelTick();
        hideBossBar();
        broadcast("&6[KMC] &eAutomatisering gepauzeerd door een admin.");
    }

    /**
     * Resumes from wherever we paused.
     */
    public void resume() {
        if (state != State.PAUSED) return;
        state = stateBeforePause;
        createBossBar();
        // Re-enter the state to restart its countdown
        switch (state) {
            case INTERMISSION -> enterIntermission();
            case VOTING       -> enterVoting();
            case PRE_START    -> enterPreStart(plugin.getGameManager().getNextGame());
            default           -> {}
        }
        broadcast("&6[KMC] &aAutomatisering hervat.");
    }

    // ----------------------------------------------------------------
    // State transitions
    // ----------------------------------------------------------------

    /** Starts the between-game lobby countdown. */
    private void enterIntermission() {
        state            = State.INTERMISSION;
        countdownSeconds = plugin.getConfig().getInt("automation.intermission-seconds", 30);

        int votingEnabled = plugin.getConfig().getBoolean("games.voting-enabled", true) ? 1 : 0;
        String label = votingEnabled == 1 ? "Volgende game wordt gekozen over" : "Volgende game start over";

        setBossBar(label, BarColor.YELLOW, 1.0);
        broadcast("&6[KMC] &eTussenpauze! Volgende game start over &6" + countdownSeconds + " &eseconden.");

        startTick(() -> {
            countdownSeconds--;
            double progress = (double) countdownSeconds /
                    plugin.getConfig().getInt("automation.intermission-seconds", 30);
            setBossBarProgress(progress);

            // Play tick sounds at 10, 5, 4, 3, 2, 1
            playTickSound(countdownSeconds);

            // Update bossbar title every second
            bossBar.setTitle(MessageUtil.color("&eTussenpauze: &6" + countdownSeconds + "s"));

            if (countdownSeconds <= 0) {
                cancelTick();
                if (plugin.getConfig().getBoolean("games.voting-enabled", true)) {
                    enterVoting();
                } else {
                    KMCGame next = plugin.getGameManager().randomNextGame();
                    enterPreStart(next);
                }
            }
        });
    }

    /** Opens the vote, waits for it to complete, then enters pre-start. */
    private void enterVoting() {
        state            = State.VOTING;
        countdownSeconds = plugin.getConfig().getInt("games.voting-duration", 30);

        setBossBar("Stem voor de volgende game!", BarColor.BLUE, 1.0);
        plugin.getGameManager().startVote();

        startTick(() -> {
            countdownSeconds--;
            double progress = (double) countdownSeconds /
                    plugin.getConfig().getInt("games.voting-duration", 30);
            setBossBarProgress(progress);
            bossBar.setTitle(MessageUtil.color("&bStemmen sluiten over: &e" + countdownSeconds + "s"));

            playTickSound(countdownSeconds);

            if (countdownSeconds <= 0) {
                cancelTick();
                plugin.getGameManager().endVote();
                KMCGame next = plugin.getGameManager().getNextGame();
                if (next == null) next = plugin.getGameManager().randomNextGame();
                enterPreStart(next);
            }
        });
    }

    /** Short countdown before the game actually launches. */
    private void enterPreStart(KMCGame game) {
        if (game == null) {
            broadcast("&c[KMC] Geen game beschikbaar! Automatisering gestopt.");
            stop();
            return;
        }

        state            = State.PRE_START;
        countdownSeconds = plugin.getConfig().getInt("automation.prestart-seconds", 10);

        setBossBar("&a" + game.getDisplayName() + " start over " + countdownSeconds + "s", BarColor.GREEN, 1.0);
        broadcast("&6[KMC] &a" + game.getDisplayName() + " &estart over &6" + countdownSeconds + " &eseconden!");

        startTick(() -> {
            countdownSeconds--;
            double progress = (double) countdownSeconds /
                    plugin.getConfig().getInt("automation.prestart-seconds", 10);
            setBossBarProgress(progress);
            bossBar.setTitle(MessageUtil.color(
                    "&a" + game.getDisplayName() + " &estart over &6" + countdownSeconds + "s"));

            // Title countdown at 5, 4, 3, 2, 1
            if (countdownSeconds <= 5 && countdownSeconds > 0) {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    p.sendTitle(
                            MessageUtil.color("&a" + game.getDisplayName()),
                            MessageUtil.color("&eStart over &6" + countdownSeconds + "s"),
                            0, 25, 5);
                    p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f,
                            0.5f + (5 - countdownSeconds) * 0.1f);
                }
            }

            if (countdownSeconds <= 0) {
                cancelTick();
                launchGame(game);
            }
        });
    }

    /** Actually starts the game and marks state as GAME_ACTIVE. */
    private void launchGame(KMCGame game) {
        state = State.GAME_ACTIVE;
        hideBossBar();
        plugin.getGameManager().startGame(game.getId());

        // Bossbar during game shows active status
        createBossBar();
        setBossBar("&2▶ &a" + game.getDisplayName() + " &2◀  Ronde " +
                plugin.getTournamentManager().getCurrentRound() +
                "  ×" + plugin.getTournamentManager().getMultiplier(), BarColor.GREEN, 1.0);
        setBossBarProgress(1.0);
    }

    /** Called when all rounds are complete. */
    private void endTournament(String lastWinner) {
        stop();
        plugin.getTournamentManager().stop();

        // Find top team
        String topTeam = plugin.getTeamManager().getTeamsSortedByPoints().stream()
                .findFirst()
                .map(t -> t.getColor() + t.getDisplayName())
                .orElse("Onbekend");

        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendTitle(
                    MessageUtil.color("&6&lToernooi Afgelopen!"),
                    MessageUtil.color("&eWinnaar: " + topTeam),
                    10, 100, 30);
            p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
        }

        broadcast("&6&l[KMC] &eHet toernooi is afgelopen! Winnaar: " + topTeam);
        plugin.getApi().fireTournamentStart(); // fires tournament-end hooks
    }

    // ----------------------------------------------------------------
    // Tick helper
    // ----------------------------------------------------------------

    /**
     * Schedules a repeating 1-second tick task.
     * Always cancels the previous task first.
     */
    private void startTick(Runnable onTick) {
        cancelTick();
        tickTask = Bukkit.getScheduler().runTaskTimer(plugin, onTick, 20L, 20L);
    }

    private void cancelTick() {
        if (tickTask != null) {
            tickTask.cancel();
            tickTask = null;
        }
    }

    // ----------------------------------------------------------------
    // BossBar helpers
    // ----------------------------------------------------------------

    private void createBossBar() {
        if (bossBar != null) hideBossBar();
        bossBar = Bukkit.createBossBar("KMC", BarColor.YELLOW, BarStyle.SOLID);
        for (Player p : Bukkit.getOnlinePlayers()) bossBar.addPlayer(p);
        bossBar.setVisible(true);
    }

    private void hideBossBar() {
        if (bossBar == null) return;
        bossBar.setVisible(false);
        bossBar.removeAll();
        bossBar = null;
    }

    private void setBossBar(String title, BarColor color, double progress) {
        if (bossBar == null) return;
        bossBar.setTitle(MessageUtil.color(title));
        bossBar.setColor(color);
        bossBar.setProgress(Math.max(0.0, Math.min(1.0, progress)));
    }

    private void setBossBarProgress(double progress) {
        if (bossBar == null) return;
        bossBar.setProgress(Math.max(0.0, Math.min(1.0, progress)));
    }

    /** Adds a newly joined player to the active bossbar. */
    public void addPlayerToBossBar(Player player) {
        if (bossBar != null) bossBar.addPlayer(player);
    }

    // ----------------------------------------------------------------
    // Sound helper
    // ----------------------------------------------------------------

    private void playTickSound(int secondsLeft) {
        if (secondsLeft != 10 && secondsLeft != 5 && secondsLeft > 3) return;
        if (secondsLeft <= 0) return;
        Sound s = secondsLeft <= 3 ? Sound.BLOCK_NOTE_BLOCK_BASS : Sound.BLOCK_NOTE_BLOCK_HAT;
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.playSound(p.getLocation(), s, 0.8f, 1f);
        }
    }

    // ----------------------------------------------------------------
    // Utility
    // ----------------------------------------------------------------

    private void broadcast(String msg) {
        Bukkit.broadcastMessage(MessageUtil.color(msg));
    }

    // ----------------------------------------------------------------
    // Getters
    // ----------------------------------------------------------------

    public State  getState()            { return state; }
    public boolean isRunning()          { return state != State.IDLE && state != State.PAUSED; }
    public int    getCountdownSeconds() { return countdownSeconds; }
}
