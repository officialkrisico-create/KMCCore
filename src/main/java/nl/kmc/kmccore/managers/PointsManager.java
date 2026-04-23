package nl.kmc.kmccore.managers;

import nl.kmc.kmccore.KMCCore;
import nl.kmc.kmccore.models.KMCTeam;
import nl.kmc.kmccore.models.PlayerData;

import java.util.UUID;

/**
 * Handles awarding and calculating points.
 *
 * <p>Points are awarded after each game based on:
 * <ol>
 *   <li>Placement (1st → most points, etc.)</li>
 *   <li>Kill bonuses</li>
 *   <li>Current round multiplier</li>
 * </ol>
 *
 * <p>The multiplier is read from the active tournament round.
 */
public class PointsManager {

    private final KMCCore plugin;

    public PointsManager(KMCCore plugin) {
        this.plugin = plugin;
    }

    // ----------------------------------------------------------------
    // Multiplier
    // ----------------------------------------------------------------

    /**
     * Returns the multiplier for the given round number.
     * Falls back to {@code 1.0} if no override is configured.
     */
    public double getMultiplierForRound(int round) {
        return plugin.getConfig().getDouble("tournament.multipliers." + round, 1.0);
    }

    /** Convenience: returns multiplier for the current tournament round. */
    public double getCurrentMultiplier() {
        int round = plugin.getTournamentManager().getCurrentRound();
        return getMultiplierForRound(round);
    }

    // ----------------------------------------------------------------
    // Placement reward
    // ----------------------------------------------------------------

    /**
     * Returns the base points awarded for a given placement (1-indexed).
     * Reads from {@code points.placement} in config.
     */
    public int getBasePointsForPlacement(int placement) {
        return plugin.getConfig().getInt("points.placement." + placement, 0);
    }

    /**
     * Awards placement points to a team, applying the current round multiplier.
     *
     * @param teamId    target team ID
     * @param placement finish position (1 = first place)
     * @return actual points awarded (after multiplier, rounded)
     */
    public int awardTeamPlacement(String teamId, int placement) {
        KMCTeam team = plugin.getTeamManager().getTeam(teamId);
        if (team == null) return 0;

        int base   = getBasePointsForPlacement(placement);
        double mul = getCurrentMultiplier();
        int award  = (int) Math.round(base * mul);

        team.addPoints(award);
        plugin.getDatabaseManager().saveTeam(team);
        return award;
    }

    /**
     * Awards placement points to an individual player.
     *
     * @param uuid      target player UUID
     * @param placement finish position
     * @return actual points awarded
     */
    public int awardPlayerPlacement(UUID uuid, int placement) {
        PlayerData pd = plugin.getPlayerDataManager().get(uuid);
        if (pd == null) return 0;

        int base   = getBasePointsForPlacement(placement);
        double mul = getCurrentMultiplier();
        int award  = (int) Math.round(base * mul);

        pd.addPoints(award);
        plugin.getDatabaseManager().savePlayer(pd);
        return award;
    }

    // ----------------------------------------------------------------
    // Kill reward
    // ----------------------------------------------------------------

    /**
     * Awards kill bonus points to a player and their team.
     *
     * @param killerUuid UUID of the killer
     */
    public void awardKill(UUID killerUuid) {
        PlayerData pd = plugin.getPlayerDataManager().get(killerUuid);
        if (pd == null) return;

        int baseKillReward = plugin.getConfig().getInt("points.kill-reward", 10);
        double mul         = getCurrentMultiplier();
        int award          = (int) Math.round(baseKillReward * mul);

        pd.addKill();
        pd.addPoints(award);
        plugin.getDatabaseManager().savePlayer(pd);

        // Also award to team
        KMCTeam team = plugin.getTeamManager().getTeamByPlayer(killerUuid);
        if (team != null) {
            team.addPoints(award);
            plugin.getDatabaseManager().saveTeam(team);
        }
    }

    // ----------------------------------------------------------------
    // Manual adjustments (used by admin commands)
    // ----------------------------------------------------------------

    public void setTeamPoints(String teamId, int amount) {
        KMCTeam t = plugin.getTeamManager().getTeam(teamId);
        if (t == null) return;
        t.setPoints(amount);
        plugin.getDatabaseManager().saveTeam(t);
    }

    public void addTeamPoints(String teamId, int amount) {
        KMCTeam t = plugin.getTeamManager().getTeam(teamId);
        if (t == null) return;
        t.addPoints(amount);
        plugin.getDatabaseManager().saveTeam(t);
    }

    public void removeTeamPoints(String teamId, int amount) {
        KMCTeam t = plugin.getTeamManager().getTeam(teamId);
        if (t == null) return;
        t.removePoints(amount);
        plugin.getDatabaseManager().saveTeam(t);
    }

    public void setPlayerPoints(UUID uuid, int amount) {
        PlayerData pd = plugin.getPlayerDataManager().get(uuid);
        if (pd == null) return;
        pd.setPoints(amount);
        plugin.getDatabaseManager().savePlayer(pd);
    }

    public void addPlayerPoints(UUID uuid, int amount) {
        PlayerData pd = plugin.getPlayerDataManager().get(uuid);
        if (pd == null) return;
        pd.addPoints(amount);
        plugin.getDatabaseManager().savePlayer(pd);
    }

    public void removePlayerPoints(UUID uuid, int amount) {
        PlayerData pd = plugin.getPlayerDataManager().get(uuid);
        if (pd == null) return;
        pd.removePoints(amount);
        plugin.getDatabaseManager().savePlayer(pd);
    }
}
