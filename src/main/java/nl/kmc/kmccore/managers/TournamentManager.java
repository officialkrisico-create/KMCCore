package nl.kmc.kmccore.managers;

import nl.kmc.kmccore.KMCCore;
import nl.kmc.kmccore.util.AnnouncementUtil;
import nl.kmc.kmccore.util.MessageUtil;
import org.bukkit.Bukkit;

/**
 * Manages the overall KMC tournament lifecycle.
 *
 * <p>State tracked:
 * <ul>
 *   <li>Whether the tournament is currently active</li>
 *   <li>Current round number</li>
 *   <li>Total rounds configured</li>
 * </ul>
 *
 * <p>State is persisted to SQLite via the {@link DatabaseManager}
 * tournament_state table so it survives server restarts.
 */
public class TournamentManager {

    private final KMCCore plugin;

    private boolean active;
    private int     currentRound;
    private int     totalRounds;

    // ----------------------------------------------------------------
    // Init
    // ----------------------------------------------------------------

    public TournamentManager(KMCCore plugin) {
        this.plugin      = plugin;
        this.totalRounds = plugin.getConfig().getInt("tournament.total-rounds", 5);
        load();
    }

    // ----------------------------------------------------------------
    // Persistence
    // ----------------------------------------------------------------

    /** Loads tournament state from the DB (falls back to config defaults). */
    private void load() {
        String activeStr = plugin.getDatabaseManager()
                .getTournamentValue("active", String.valueOf(
                        plugin.getConfig().getBoolean("tournament.active", false)));
        this.active = Boolean.parseBoolean(activeStr);

        String roundStr = plugin.getDatabaseManager()
                .getTournamentValue("current_round", String.valueOf(
                        plugin.getConfig().getInt("tournament.current-round", 1)));
        this.currentRound = Integer.parseInt(roundStr);
    }

    /** Persists current state to the DB. */
    public void save() {
        plugin.getDatabaseManager().setTournamentValue("active",        String.valueOf(active));
        plugin.getDatabaseManager().setTournamentValue("current_round", String.valueOf(currentRound));
    }

    // ----------------------------------------------------------------
    // Tournament control
    // ----------------------------------------------------------------

    /**
     * Starts the tournament from round 1.
     *
     * @return {@code false} if already active
     */
    public boolean start() {
        if (active) return false;
        active       = true;
        currentRound = 1;
        save();

        AnnouncementUtil.broadcastTitle(plugin, "announcements.tournament-start", null);
        AnnouncementUtil.broadcastMessage(plugin, "broadcast.tournament-start",
                "{winner}", "");   // placeholder, winner unknown at start

        plugin.getScoreboardManager().refreshAll();
        return true;
    }

    /**
     * Stops the tournament.
     *
     * @return {@code false} if not active
     */
    public boolean stop() {
        if (!active) return false;
        active = false;
        save();

        Bukkit.broadcastMessage(MessageUtil.color(
                plugin.getConfig().getString("settings.prefix", "&6[KMC] ") +
                "&cHet toernooi is gestopt."));
        plugin.getScoreboardManager().refreshAll();
        return true;
    }

    // ----------------------------------------------------------------
    // Round management
    // ----------------------------------------------------------------

    /**
     * Advances to the next round.
     *
     * @return {@code false} if already at max rounds
     */
    public boolean nextRound() {
        if (currentRound >= totalRounds) return false;
        currentRound++;
        save();

        double mul = plugin.getPointsManager().getMultiplierForRound(currentRound);
        AnnouncementUtil.broadcastTitle(plugin, "announcements.round-start",
                new String[]{"{round}", "{multiplier}"},
                new String[]{String.valueOf(currentRound), String.valueOf(mul)});

        String msg = MessageUtil.get("broadcast.round-start")
                .replace("{round}", String.valueOf(currentRound))
                .replace("{multiplier}", String.valueOf(mul));
        Bukkit.broadcastMessage(msg);

        plugin.getScoreboardManager().refreshAll();
        return true;
    }

    /**
     * Sets the current round to a specific value.
     *
     * @param round must be between 1 and totalRounds (inclusive)
     * @return {@code false} if out of range
     */
    public boolean setRound(int round) {
        if (round < 1 || round > totalRounds) return false;
        currentRound = round;
        save();
        plugin.getScoreboardManager().refreshAll();
        return true;
    }

    // ----------------------------------------------------------------
    // Reset
    // ----------------------------------------------------------------

    /**
     * Resets the entire tournament: scores, round, active state.
     * Also resets team and player data.
     */
    public void reset() {
        active       = false;
        currentRound = 1;
        save();

        plugin.getTeamManager().resetScores();
        plugin.getPlayerDataManager().resetAll();
        plugin.getScoreboardManager().refreshAll();
    }

    // ----------------------------------------------------------------
    // Getters
    // ----------------------------------------------------------------

    public boolean isActive()       { return active; }
    public int     getCurrentRound(){ return currentRound; }
    public int     getTotalRounds() { return totalRounds; }
    public double  getMultiplier()  {
        return plugin.getPointsManager().getMultiplierForRound(currentRound);
    }
}
