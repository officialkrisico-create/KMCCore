package nl.kmc.kmccore.models;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Stores all persistent statistics for a single player.
 *
 * Basic stats:      coins, points, kills, wins
 * Extended stats:   totalCoinsEarned, gamesPlayed, totalPlayTimeMinutes,
 *                   winStreak, bestWinStreak, winsPerGame
 */
public class PlayerData {

    private final UUID uuid;
    private String     name;
    private String     teamId;

    // ---- Basic stats -----------------------------------------------
    private int  coins;
    private int  points;
    private int  kills;
    private int  wins;

    // ---- Extended / lifetime stats ---------------------------------
    private int  totalCoinsEarned;
    private int  gamesPlayed;
    private int  totalPlayTimeMinutes;
    private int  winStreak;
    private int  bestWinStreak;

    /** Per-game win tally. Key = game ID, value = wins. */
    private Map<String, Integer> winsPerGame = new HashMap<>();

    /** Server-time millis when player last entered an active game (not persisted). */
    private transient long gameSessionStart = -1;

    private boolean teamChatEnabled;

    // ----------------------------------------------------------------
    public PlayerData(UUID uuid, String name) {
        this.uuid = uuid;
        this.name = name;
    }

    // ---- Coins -----------------------------------------------------
    public int  getCoins()         { return coins; }
    public void setCoins(int v)    { this.coins = Math.max(0, v); }
    public void addCoins(int v)    { if (v > 0) totalCoinsEarned += v; this.coins = Math.max(0, this.coins + v); }
    public void removeCoins(int v) { this.coins = Math.max(0, this.coins - v); }

    // ---- Points ----------------------------------------------------
    public int  getPoints()         { return points; }
    public void setPoints(int v)    { this.points = Math.max(0, v); }
    public void addPoints(int v)    { this.points = Math.max(0, this.points + v); }
    public void removePoints(int v) { this.points = Math.max(0, this.points - v); }

    // ---- Kills -----------------------------------------------------
    public int  getKills()      { return kills; }
    public void setKills(int v) { this.kills = Math.max(0, v); }
    public void addKill()       { this.kills++; }

    // ---- Wins ------------------------------------------------------
    public int  getWins()       { return wins; }
    public void setWins(int v)  { this.wins = Math.max(0, v); }

    /** Records a win, updates streak, and increments the per-game counter. */
    public void addWin(String gameId) {
        this.wins++;
        this.winStreak++;
        if (this.winStreak > this.bestWinStreak) this.bestWinStreak = this.winStreak;
        if (gameId != null && !gameId.isBlank()) winsPerGame.merge(gameId, 1, Integer::sum);
    }
    public void addWin() { addWin(null); }

    // ---- Streaks ---------------------------------------------------
    public void resetStreak()           { this.winStreak = 0; }
    public int  getWinStreak()          { return winStreak; }
    public void setWinStreak(int v)     { this.winStreak = Math.max(0, v); }
    public int  getBestWinStreak()      { return bestWinStreak; }
    public void setBestWinStreak(int v) { this.bestWinStreak = Math.max(0, v); }

    // ---- Games played / time ---------------------------------------
    public int  getGamesPlayed()               { return gamesPlayed; }
    public void setGamesPlayed(int v)          { this.gamesPlayed = Math.max(0, v); }
    public void incrementGamesPlayed()         { this.gamesPlayed++; }
    public int  getTotalPlayTimeMinutes()      { return totalPlayTimeMinutes; }
    public void setTotalPlayTimeMinutes(int v) { this.totalPlayTimeMinutes = Math.max(0, v); }
    public void addPlayTimeMinutes(int v)      { this.totalPlayTimeMinutes += Math.max(0, v); }

    /** Call when player enters a game. */
    public void startGameSession() { this.gameSessionStart = System.currentTimeMillis(); }
    /** Call when player exits a game — auto-adds elapsed minutes. */
    public void endGameSession() {
        if (gameSessionStart < 0) return;
        addPlayTimeMinutes((int)((System.currentTimeMillis() - gameSessionStart) / 60_000));
        gameSessionStart = -1;
    }

    // ---- Per-game wins ---------------------------------------------
    public Map<String, Integer> getWinsPerGame()                    { return winsPerGame; }
    public void setWinsPerGame(Map<String, Integer> m)              { this.winsPerGame = m != null ? m : new HashMap<>(); }
    public String getFavouriteGame() {
        return winsPerGame.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey).orElse(null);
    }

    // ---- Total coins earned ----------------------------------------
    public int  getTotalCoinsEarned()      { return totalCoinsEarned; }
    public void setTotalCoinsEarned(int v) { this.totalCoinsEarned = Math.max(0, v); }

    // ---- Team / chat -----------------------------------------------
    public String  getTeamId()              { return teamId; }
    public void    setTeamId(String id)     { this.teamId = id; }
    public boolean hasTeam()                { return teamId != null && !teamId.isEmpty(); }
    public boolean isTeamChatEnabled()           { return teamChatEnabled; }
    public void    setTeamChatEnabled(boolean v) { this.teamChatEnabled = v; }
    public void    toggleTeamChat()              { this.teamChatEnabled = !this.teamChatEnabled; }

    // ---- Identity --------------------------------------------------
    public UUID   getUuid() { return uuid; }
    public String getName() { return name; }
    public void   setName(String n) { this.name = n; }

    @Override
    public String toString() {
        return "PlayerData{name=" + name + ", points=" + points + ", wins=" + wins + ", streak=" + winStreak + "}";
    }
}
