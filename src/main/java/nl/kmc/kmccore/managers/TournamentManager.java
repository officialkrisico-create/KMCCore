package nl.kmc.kmccore.managers;

import nl.kmc.kmccore.KMCCore;
import nl.kmc.kmccore.models.KMCTeam;
import nl.kmc.kmccore.models.PlayerData;
import nl.kmc.kmccore.util.AnnouncementUtil;
import nl.kmc.kmccore.util.MessageUtil;
import org.bukkit.Bukkit;

/**
 * Manages the overall KMC tournament lifecycle.
 *
 * <p>Key feature: {@link #endTournament()} resets ALL points and wins to zero
 * (for the next event), but preserves lifetime stats like bestWinStreak.
 * A full wipe is available via {@link #hardReset()}.
 */
public class TournamentManager {

    private final KMCCore plugin;
    private boolean active;
    private int     currentRound;
    private int     totalRounds;

    public TournamentManager(KMCCore plugin) {
        this.plugin      = plugin;
        this.totalRounds = plugin.getConfig().getInt("tournament.total-rounds", 5);
        load();
    }

    private void load() {
        active       = Boolean.parseBoolean(plugin.getDatabaseManager()
                .getTournamentValue("active",
                        String.valueOf(plugin.getConfig().getBoolean("tournament.active", false))));
        currentRound = Integer.parseInt(plugin.getDatabaseManager()
                .getTournamentValue("current_round",
                        String.valueOf(plugin.getConfig().getInt("tournament.current-round", 1))));
    }

    public void save() {
        plugin.getDatabaseManager().setTournamentValue("active",        String.valueOf(active));
        plugin.getDatabaseManager().setTournamentValue("current_round", String.valueOf(currentRound));
    }

    // ----------------------------------------------------------------
    // Start / Stop
    // ----------------------------------------------------------------

    public boolean start() {
        if (active) return false;
        active       = true;
        currentRound = 1;
        save();

        AnnouncementUtil.broadcastTitle(plugin, "announcements.tournament-start", null);
        Bukkit.broadcastMessage(MessageUtil.get("broadcast.tournament-start"));
        plugin.getScoreboardManager().refreshAll();
        plugin.getTabListManager().refreshAll();
        return true;
    }

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

    /**
     * Ends the tournament NICELY: announces the winner, then resets all
     * tournament points and team wins to zero so a new event can start fresh.
     * Lifetime stats (bestWinStreak, winsPerGame, playtime) are kept.
     */
    public void endTournament() {
        active = false;
        save();

        // Find winning team
        KMCTeam winner = plugin.getTeamManager().getTeamsSortedByPoints().stream()
                .findFirst().orElse(null);
        String winnerName = winner != null
                ? winner.getColor() + winner.getDisplayName()
                : "Onbekend";

        // Big announcement
        Bukkit.broadcastMessage(MessageUtil.color("&6&l═══════════════════════════"));
        Bukkit.broadcastMessage(MessageUtil.color("&6&l   TOERNOOI AFGELOPEN!"));
        Bukkit.broadcastMessage(MessageUtil.color("&eWinnaar: " + winnerName));
        Bukkit.broadcastMessage(MessageUtil.color("&6&l═══════════════════════════"));

        // Soft reset — zeros points/wins, keeps lifetime stats
        plugin.getDatabaseManager().resetAll(false);
        plugin.getTeamManager().resetScores();
        plugin.getPlayerDataManager().resetSeasonStats();
        currentRound = 1;
        save();

        plugin.getScoreboardManager().refreshAll();
        plugin.getTabListManager().refreshAll();

        Bukkit.broadcastMessage(MessageUtil.color("&aAlle punten zijn gereset. Klaar voor het volgende toernooi!"));
    }

    // ----------------------------------------------------------------
    // Rounds
    // ----------------------------------------------------------------

    public boolean nextRound() {
        if (currentRound >= totalRounds) return false;
        currentRound++;
        save();
        double mul = plugin.getPointsManager().getMultiplierForRound(currentRound);
        AnnouncementUtil.broadcastTitle(plugin, "announcements.round-start",
                new String[]{"{round}", "{multiplier}"},
                new String[]{String.valueOf(currentRound), String.valueOf(mul)});
        Bukkit.broadcastMessage(MessageUtil.get("broadcast.round-start")
                .replace("{round}", String.valueOf(currentRound))
                .replace("{multiplier}", String.valueOf(mul)));
        plugin.getScoreboardManager().refreshAll();
        return true;
    }

    public boolean setRound(int round) {
        if (round < 1 || round > totalRounds) return false;
        currentRound = round;
        save();
        plugin.getScoreboardManager().refreshAll();
        return true;
    }

    // ----------------------------------------------------------------
    // Resets
    // ----------------------------------------------------------------

    /** Soft reset — zero tournament data, keep lifetime stats. */
    public void reset() {
        active       = false;
        currentRound = 1;
        save();
        plugin.getDatabaseManager().resetAll(false);
        plugin.getTeamManager().resetScores();
        plugin.getPlayerDataManager().resetSeasonStats();
        plugin.getGameManager().resetPlayedGames();
        plugin.getScoreboardManager().refreshAll();
    }

    /** Hard reset — wipes EVERYTHING including lifetime stats. */
    public void hardReset() {
        active       = false;
        currentRound = 1;
        save();
        plugin.getDatabaseManager().resetAll(true);
        plugin.getTeamManager().resetScores();
        plugin.getPlayerDataManager().resetAll();
        plugin.getGameManager().resetPlayedGames();
        plugin.getScoreboardManager().refreshAll();
    }

    public boolean isActive()        { return active; }
    public int     getCurrentRound() { return currentRound; }
    public int     getTotalRounds()  { return totalRounds; }
    public double  getMultiplier()   {
        return plugin.getPointsManager().getMultiplierForRound(currentRound);
    }
}
