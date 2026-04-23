package nl.kmc.kmccore.database;

import nl.kmc.kmccore.KMCCore;
import nl.kmc.kmccore.models.KMCTeam;
import nl.kmc.kmccore.models.PlayerData;
import org.bukkit.ChatColor;

import java.io.File;
import java.sql.*;
import java.util.*;
import java.util.logging.Level;

/**
 * Manages all persistence for KMC Core.
 *
 * <p>Currently supports SQLite (default) and can be extended to
 * support MySQL/MariaDB by swapping the JDBC URL and driver.
 *
 * <p>Schema:
 * <pre>
 *   players  (uuid, name, team_id, coins, points, kills, wins)
 *   teams    (id, display_name, color, tag_color, points, wins)
 *   tournament (key, value)   – flat key/value store for misc state
 * </pre>
 */
public class DatabaseManager {

    private final KMCCore plugin;
    private Connection connection;

    // ----------------------------------------------------------------
    // Init
    // ----------------------------------------------------------------

    public DatabaseManager(KMCCore plugin) {
        this.plugin = plugin;
    }

    /** Opens the SQLite connection and creates tables if needed. */
    public void connect() {
        String type = plugin.getConfig().getString("database.type", "sqlite");

        try {
            if (type.equalsIgnoreCase("sqlite")) {
                connectSQLite();
            } else {
                plugin.getLogger().warning("Unknown database type '" + type + "', defaulting to SQLite.");
                connectSQLite();
            }
            createTables();
            plugin.getLogger().info("Database connected successfully.");
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to connect to database!", e);
        }
    }

    private void connectSQLite() throws SQLException {
        File dbFile = new File(plugin.getDataFolder(),
                plugin.getConfig().getString("database.file", "kmccore.db"));
        plugin.getDataFolder().mkdirs();

        String url = "jdbc:sqlite:" + dbFile.getAbsolutePath();
        connection = DriverManager.getConnection(url);
        // Enable WAL mode for better concurrency
        try (Statement st = connection.createStatement()) {
            st.execute("PRAGMA journal_mode=WAL;");
            st.execute("PRAGMA foreign_keys=ON;");
        }
    }

    /** Closes the connection gracefully. */
    public void disconnect() {
        if (connection != null) {
            try {
                connection.close();
                plugin.getLogger().info("Database connection closed.");
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Error closing database connection.", e);
            }
        }
    }

    // ----------------------------------------------------------------
    // Schema
    // ----------------------------------------------------------------

    private void createTables() throws SQLException {
        String players = """
            CREATE TABLE IF NOT EXISTS players (
                uuid                  TEXT PRIMARY KEY,
                name                  TEXT NOT NULL,
                team_id               TEXT,
                coins                 INTEGER DEFAULT 0,
                points                INTEGER DEFAULT 0,
                kills                 INTEGER DEFAULT 0,
                wins                  INTEGER DEFAULT 0,
                total_coins_earned    INTEGER DEFAULT 0,
                games_played          INTEGER DEFAULT 0,
                play_time_minutes     INTEGER DEFAULT 0,
                win_streak            INTEGER DEFAULT 0,
                best_win_streak       INTEGER DEFAULT 0,
                wins_per_game         TEXT DEFAULT ''
            );
            """;
        // Add new columns to existing tables (safe to run on existing DB)
        String alterPlayers1 = "ALTER TABLE players ADD COLUMN IF NOT EXISTS total_coins_earned INTEGER DEFAULT 0;";
        String alterPlayers2 = "ALTER TABLE players ADD COLUMN IF NOT EXISTS games_played INTEGER DEFAULT 0;";
        String alterPlayers3 = "ALTER TABLE players ADD COLUMN IF NOT EXISTS play_time_minutes INTEGER DEFAULT 0;";
        String alterPlayers4 = "ALTER TABLE players ADD COLUMN IF NOT EXISTS win_streak INTEGER DEFAULT 0;";
        String alterPlayers5 = "ALTER TABLE players ADD COLUMN IF NOT EXISTS best_win_streak INTEGER DEFAULT 0;";
        String alterPlayers6 = "ALTER TABLE players ADD COLUMN IF NOT EXISTS wins_per_game TEXT DEFAULT '';";

        String teams = """
            CREATE TABLE IF NOT EXISTS teams (
                id           TEXT PRIMARY KEY,
                display_name TEXT NOT NULL,
                color        TEXT NOT NULL,
                tag_color    TEXT NOT NULL,
                points       INTEGER DEFAULT 0,
                wins         INTEGER DEFAULT 0
            );
            """;

        String tournament = """
            CREATE TABLE IF NOT EXISTS tournament_state (
                key   TEXT PRIMARY KEY,
                value TEXT
            );
            """;

        try (Statement st = connection.createStatement()) {
            st.execute(players);
            st.execute(teams);
            st.execute(tournament);
            // Safely add new columns — SQLite ignores duplicate column errors
            try { st.execute(alterPlayers1); } catch (SQLException ignored) {}
            try { st.execute(alterPlayers2); } catch (SQLException ignored) {}
            try { st.execute(alterPlayers3); } catch (SQLException ignored) {}
            try { st.execute(alterPlayers4); } catch (SQLException ignored) {}
            try { st.execute(alterPlayers5); } catch (SQLException ignored) {}
            try { st.execute(alterPlayers6); } catch (SQLException ignored) {}
        }
    }

    // ----------------------------------------------------------------
    // Players CRUD
    // ----------------------------------------------------------------

    /** Loads a player by UUID, or returns {@code null} if not found. */
    public PlayerData loadPlayer(UUID uuid) {
        String sql = "SELECT * FROM players WHERE uuid = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                PlayerData pd = new PlayerData(uuid, rs.getString("name"));
                pd.setTeamId(rs.getString("team_id"));
                pd.setCoins(rs.getInt("coins"));
                pd.setPoints(rs.getInt("points"));
                pd.setKills(rs.getInt("kills"));
                pd.setWins(rs.getInt("wins"));
                pd.setTotalCoinsEarned(rs.getInt("total_coins_earned"));
                pd.setGamesPlayed(rs.getInt("games_played"));
                pd.setTotalPlayTimeMinutes(rs.getInt("play_time_minutes"));
                pd.setWinStreak(rs.getInt("win_streak"));
                pd.setBestWinStreak(rs.getInt("best_win_streak"));
                pd.setWinsPerGame(deserializeWinsPerGame(rs.getString("wins_per_game")));
                return pd;
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Error loading player " + uuid, e);
        }
        return null;
    }

    /** Saves (inserts or updates) a player record. */
    public void savePlayer(PlayerData pd) {
        String sql = """
            INSERT INTO players (uuid, name, team_id, coins, points, kills, wins,
                total_coins_earned, games_played, play_time_minutes, win_streak, best_win_streak, wins_per_game)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(uuid) DO UPDATE SET
                name               = excluded.name,
                team_id            = excluded.team_id,
                coins              = excluded.coins,
                points             = excluded.points,
                kills              = excluded.kills,
                wins               = excluded.wins,
                total_coins_earned = excluded.total_coins_earned,
                games_played       = excluded.games_played,
                play_time_minutes  = excluded.play_time_minutes,
                win_streak         = excluded.win_streak,
                best_win_streak    = excluded.best_win_streak,
                wins_per_game      = excluded.wins_per_game;
            """;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, pd.getUuid().toString());
            ps.setString(2, pd.getName());
            ps.setString(3, pd.getTeamId());
            ps.setInt(4,    pd.getCoins());
            ps.setInt(5,    pd.getPoints());
            ps.setInt(6,    pd.getKills());
            ps.setInt(7,    pd.getWins());
            ps.setInt(8,    pd.getTotalCoinsEarned());
            ps.setInt(9,    pd.getGamesPlayed());
            ps.setInt(10,   pd.getTotalPlayTimeMinutes());
            ps.setInt(11,   pd.getWinStreak());
            ps.setInt(12,   pd.getBestWinStreak());
            ps.setString(13, serializeWinsPerGame(pd.getWinsPerGame()));
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Error saving player " + pd.getUuid(), e);
        }
    }

    /** Loads all player records (used for leaderboards). */
    public List<PlayerData> loadAllPlayers() {
        List<PlayerData> list = new ArrayList<>();
        String sql = "SELECT * FROM players ORDER BY points DESC";
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                UUID uuid = UUID.fromString(rs.getString("uuid"));
                PlayerData pd = new PlayerData(uuid, rs.getString("name"));
                pd.setTeamId(rs.getString("team_id"));
                pd.setCoins(rs.getInt("coins"));
                pd.setPoints(rs.getInt("points"));
                pd.setKills(rs.getInt("kills"));
                pd.setWins(rs.getInt("wins"));
                list.add(pd);
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Error loading all players.", e);
        }
        return list;
    }

    // ----------------------------------------------------------------
    // Teams CRUD
    // ----------------------------------------------------------------

    /** Saves a team (insert or update). */
    public void saveTeam(KMCTeam team) {
        String sql = """
            INSERT INTO teams (id, display_name, color, tag_color, points, wins)
            VALUES (?, ?, ?, ?, ?, ?)
            ON CONFLICT(id) DO UPDATE SET
                display_name = excluded.display_name,
                color        = excluded.color,
                tag_color    = excluded.tag_color,
                points       = excluded.points,
                wins         = excluded.wins;
            """;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, team.getId());
            ps.setString(2, team.getDisplayName());
            ps.setString(3, team.getColor().name());
            ps.setString(4, team.getTagColor());
            ps.setInt(5,    team.getPoints());
            ps.setInt(6,    team.getWins());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Error saving team " + team.getId(), e);
        }
    }

    /** Loads all teams from the database. */
    public Map<String, KMCTeam> loadAllTeams() {
        Map<String, KMCTeam> map = new LinkedHashMap<>();
        String sql = "SELECT * FROM teams ORDER BY points DESC";
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                String id   = rs.getString("id");
                String name = rs.getString("display_name");
                ChatColor color = ChatColor.valueOf(rs.getString("color"));
                String tagColor = rs.getString("tag_color");

                KMCTeam team = new KMCTeam(id, name, color, tagColor);
                team.setPoints(rs.getInt("points"));
                team.setWins(rs.getInt("wins"));
                map.put(id, team);
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Error loading teams.", e);
        }
        return map;
    }

    // ----------------------------------------------------------------
    // Tournament state key/value store
    // ----------------------------------------------------------------

    public void setTournamentValue(String key, String value) {
        String sql = """
            INSERT INTO tournament_state (key, value) VALUES (?, ?)
            ON CONFLICT(key) DO UPDATE SET value = excluded.value;
            """;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, key);
            ps.setString(2, value);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Error setting tournament value: " + key, e);
        }
    }

    public String getTournamentValue(String key, String defaultValue) {
        String sql = "SELECT value FROM tournament_state WHERE key = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, key);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getString("value");
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Error getting tournament value: " + key, e);
        }
        return defaultValue;
    }

    // ----------------------------------------------------------------
    // Serialization helpers for wins_per_game map
    // Format: "gameId:count|gameId:count|..."
    // ----------------------------------------------------------------

    private String serializeWinsPerGame(java.util.Map<String, Integer> map) {
        if (map == null || map.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (java.util.Map.Entry<String, Integer> e : map.entrySet()) {
            if (sb.length() > 0) sb.append("|");
            sb.append(e.getKey()).append(":").append(e.getValue());
        }
        return sb.toString();
    }

    private java.util.Map<String, Integer> deserializeWinsPerGame(String raw) {
        java.util.Map<String, Integer> map = new java.util.HashMap<>();
        if (raw == null || raw.isBlank()) return map;
        for (String part : raw.split("\\|")) {
            String[] kv = part.split(":");
            if (kv.length == 2) {
                try { map.put(kv[0], Integer.parseInt(kv[1])); } catch (NumberFormatException ignored) {}
            }
        }
        return map;
    }

    /** Wipes all data – used for tournament reset. */
    public void resetAll() {
        try (Statement st = connection.createStatement()) {
            st.execute("DELETE FROM players");
            st.execute("DELETE FROM tournament_state");
            st.execute("UPDATE teams SET points = 0, wins = 0");
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Error resetting tournament data.", e);
        }
    }
}
