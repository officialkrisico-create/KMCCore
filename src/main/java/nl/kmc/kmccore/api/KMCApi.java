package nl.kmc.kmccore.api;

import nl.kmc.kmccore.KMCCore;
import nl.kmc.kmccore.models.KMCTeam;
import nl.kmc.kmccore.models.PlayerData;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Public API surface for KMC Core.
 *
 * <p>Other plugins (or future Twitch/chat-bot integrations) can depend on
 * KMC Core and use this class to:
 * <ul>
 *   <li>Query team / player data</li>
 *   <li>Award coins or points programmatically</li>
 *   <li>Register callbacks for game-end / round-start events</li>
 * </ul>
 *
 * <p>Usage from another plugin:
 * <pre>{@code
 *   KMCCore core = (KMCCore) Bukkit.getPluginManager().getPlugin("KMCCore");
 *   KMCApi api = core.getApi();
 *   api.onGameEnd((gameName, winner) -> {
 *       // do something in your Twitch bot / chat overlay
 *   });
 * }</pre>
 */
public class KMCApi {

    private final KMCCore plugin;

    // ---- Hook lists ------------------------------------------------
    private final List<BiConsumer<String, String>> gameEndHooks    = new ArrayList<>();
    private final List<Consumer<Integer>>          roundStartHooks = new ArrayList<>();
    private final List<Runnable>                   tournamentStartHooks = new ArrayList<>();

    // ----------------------------------------------------------------

    public KMCApi(KMCCore plugin) {
        this.plugin = plugin;
    }

    // ----------------------------------------------------------------
    // Data queries
    // ----------------------------------------------------------------

    /** Returns the team a player belongs to, or {@code null}. */
    public KMCTeam getTeamByPlayer(UUID uuid) {
        return plugin.getTeamManager().getTeamByPlayer(uuid);
    }

    /** Returns a team by its ID (e.g. {@code "rode_ratten"}). */
    public KMCTeam getTeam(String teamId) {
        return plugin.getTeamManager().getTeam(teamId);
    }

    /** Returns all teams sorted by points. */
    public List<KMCTeam> getTeamLeaderboard() {
        return plugin.getTeamManager().getTeamsSortedByPoints();
    }

    /** Returns player data, or {@code null} if never seen. */
    public PlayerData getPlayerData(UUID uuid) {
        return plugin.getPlayerDataManager().get(uuid);
    }

    /** Returns the full player leaderboard sorted by points. */
    public List<PlayerData> getPlayerLeaderboard() {
        return plugin.getPlayerDataManager().getLeaderboard();
    }

    /** @return name of the currently active game, or {@code null}. */
    public String getActiveGameName() {
        return plugin.getGameManager().getActiveGame() != null
               ? plugin.getGameManager().getActiveGame().getDisplayName()
               : null;
    }

    /** @return current round multiplier. */
    public double getCurrentMultiplier() {
        return plugin.getTournamentManager().getMultiplier();
    }

    /** @return current round number. */
    public int getCurrentRound() {
        return plugin.getTournamentManager().getCurrentRound();
    }

    /** @return whether the tournament is active. */
    public boolean isTournamentActive() {
        return plugin.getTournamentManager().isActive();
    }

    // ----------------------------------------------------------------
    // Mutations
    // ----------------------------------------------------------------

    /** Awards coins to a player. */
    public void giveCoins(UUID uuid, int amount) {
        PlayerData pd = plugin.getPlayerDataManager().get(uuid);
        if (pd != null) {
            pd.addCoins(amount);
            plugin.getDatabaseManager().savePlayer(pd);
        }
    }

    /** Awards points to a player (not multiplied). */
    public void givePoints(UUID uuid, int amount) {
        plugin.getPointsManager().addPlayerPoints(uuid, amount);
    }

    /** Awards points to a team (not multiplied). */
    public void giveTeamPoints(String teamId, int amount) {
        plugin.getPointsManager().addTeamPoints(teamId, amount);
    }

    // ----------------------------------------------------------------
    // Event hooks
    // ----------------------------------------------------------------

    /**
     * Registers a callback invoked when a game ends.
     *
     * @param hook {@code BiConsumer<gameName, winnerName>}
     */
    public void onGameEnd(BiConsumer<String, String> hook) {
        gameEndHooks.add(hook);
    }

    /**
     * Registers a callback invoked when a new round starts.
     *
     * @param hook {@code Consumer<roundNumber>}
     */
    public void onRoundStart(Consumer<Integer> hook) {
        roundStartHooks.add(hook);
    }

    /** Registers a callback invoked when the tournament starts. */
    public void onTournamentStart(Runnable hook) {
        tournamentStartHooks.add(hook);
    }

    // ----------------------------------------------------------------
    // Internal fire methods (called by managers)
    // ----------------------------------------------------------------

    public void fireGameEnd(String gameName, String winner) {
        for (BiConsumer<String, String> h : gameEndHooks) {
            try { h.accept(gameName, winner); }
            catch (Exception e) { plugin.getLogger().warning("API gameEnd hook error: " + e.getMessage()); }
        }
    }

    public void fireRoundStart(int round) {
        for (Consumer<Integer> h : roundStartHooks) {
            try { h.accept(round); }
            catch (Exception e) { plugin.getLogger().warning("API roundStart hook error: " + e.getMessage()); }
        }
    }

    public void fireTournamentStart() {
        for (Runnable h : tournamentStartHooks) {
            try { h.run(); }
            catch (Exception e) { plugin.getLogger().warning("API tournamentStart hook error: " + e.getMessage()); }
        }
    }
}
