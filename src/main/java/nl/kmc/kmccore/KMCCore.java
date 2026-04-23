package nl.kmc.kmccore;

import nl.kmc.kmccore.api.KMCApi;
import nl.kmc.kmccore.commands.*;
import nl.kmc.kmccore.database.DatabaseManager;
import nl.kmc.kmccore.listeners.*;
import nl.kmc.kmccore.managers.*;
import nl.kmc.kmccore.npc.NPCManager;
import nl.kmc.kmccore.scoreboard.ScoreboardManager;
import nl.kmc.kmccore.util.MessageUtil;
import org.bukkit.plugin.java.JavaPlugin;
import nl.kmc.kmccore.managers.AutomationManager;
import nl.kmc.kmccore.managers.TabListManager;

/**
 * KMCCore - Main plugin class for the Krizi Minecraft Championship.
 *
 * <p>This plugin manages:
 * <ul>
 *   <li>Teams and team chat</li>
 *   <li>Individual player statistics (coins, points, kills, wins)</li>
 *   <li>Game randomizer and voting</li>
 *   <li>Points/multiplier system per round</li>
 *   <li>Tournament lifecycle management</li>
 *   <li>Live scoreboards and leaderboard NPCs</li>
 *   <li>SQLite / YAML persistence</li>
 *   <li>API hooks for external integrations (Twitch, chat bots, etc.)</li>
 * </ul>
 *
 * <p>Architecture: each feature area has its own Manager class.
 * The plugin acts as a dependency-injection root; managers reference
 * each other only via this class so they stay loosely coupled.
 */
public final class KMCCore extends JavaPlugin {

    // ----------------------------------------------------------------
    // Singleton instance – use KMCCore.getInstance() from other classes
    // ----------------------------------------------------------------
    private static KMCCore instance;

    // ----------------------------------------------------------------
    // Managers (one per feature area)
    // ----------------------------------------------------------------
    private DatabaseManager    databaseManager;
    private TeamManager        teamManager;
    private PlayerDataManager  playerDataManager;
    private TournamentManager  tournamentManager;
    private GameManager        gameManager;
    private PointsManager      pointsManager;
    private ScoreboardManager  scoreboardManager;
    private NPCManager         npcManager;
    private AutomationManager  automationManager;
    private TabListManager     tabListManager;
    private ArenaManager       arenaManager;
    private SchematicManager   schematicManager;

    // ----------------------------------------------------------------
    // Public API surface (for external plugins / integrations)
    // ----------------------------------------------------------------
    private KMCApi api;

    // ----------------------------------------------------------------
    // Lifecycle
    // ----------------------------------------------------------------

    @Override
    public void onLoad() {
        instance = this;
    }

    @Override
    public void onEnable() {
        // 1. Save default configs
        saveDefaultConfig();
        saveResource("messages.yml", false);

        // 2. Utility setup (needs config loaded first)
        MessageUtil.init(this);

        // 3. Database (SQLite or YAML)
        databaseManager = new DatabaseManager(this);
        databaseManager.connect();

        // 4. Core data managers
        teamManager       = new TeamManager(this);
        tabListManager    = new TabListManager(this);
        schematicManager  = new SchematicManager(this);
        arenaManager      = new ArenaManager(this);
        playerDataManager = new PlayerDataManager(this);
        pointsManager     = new PointsManager(this);
        tournamentManager = new TournamentManager(this);
        gameManager       = new GameManager(this);

        // 5. UI managers
        scoreboardManager = new ScoreboardManager(this);
        npcManager        = new NPCManager(this);
        automationManager = new AutomationManager(this);

        // 6. Register commands
        registerCommands();

        // 7. Register listeners
        registerListeners();

        // 8. Public API
        api = new KMCApi(this);

        getLogger().info("KMCCore v" + getDescription().getVersion() + " enabled!");
        getLogger().info("Tournament: " + getConfig().getString("tournament.name"));
    }

    @Override
    public void onDisable() {
        // Save all in-memory data before shutting down
        if (playerDataManager != null) playerDataManager.saveAll();
        if (teamManager       != null) teamManager.saveAll();
        if (tournamentManager != null) tournamentManager.save();
        if (gameManager       != null) gameManager.save();
        if (automationManager != null) automationManager.stop();
        if (scoreboardManager != null) scoreboardManager.cleanup();
        if (npcManager        != null) npcManager.save();
        if (databaseManager   != null) databaseManager.disconnect();

        getLogger().info("KMCCore disabled. All data saved.");
    }

    // ----------------------------------------------------------------
    // Private helpers
    // ----------------------------------------------------------------

    /** Register all command executors. */
    private void registerCommands() {
        getCommand("kmcteam").setExecutor(new TeamCommand(this));
        getCommand("kmcteam").setTabCompleter(new TeamCommand(this));

        getCommand("kmcstats").setExecutor(new StatsCommand(this));

        getCommand("kmccoins").setExecutor(new CoinsCommand(this));
        getCommand("kmccoins").setTabCompleter(new CoinsCommand(this));

        getCommand("kmctournament").setExecutor(new TournamentCommand(this));

        getCommand("kmcgame").setExecutor(new GameCommand(this));
        getCommand("kmcgame").setTabCompleter(new GameCommand(this));

        getCommand("kmclb").setExecutor(new LeaderboardCommand(this));

        getCommand("kmcnpc").setExecutor(new NPCCommand(this));

        getCommand("kmcround").setExecutor(new RoundCommand(this));

        getCommand("kmcpoints").setExecutor(new PointsCommand(this));
        getCommand("kmcpoints").setTabCompleter(new PointsCommand(this));

        getCommand("tc").setExecutor(new TeamChatCommand(this));

        getCommand("kmcvote").setExecutor(new VoteCommand(this));

        getCommand("kmcauto").setExecutor(new AutomationCommand(this));
        getCommand("kmcauto").setTabCompleter(new AutomationCommand(this));

        getCommand("kmcarena").setExecutor(new ArenaCommand(this));
        getCommand("kmcarena").setTabCompleter(new ArenaCommand(this));
    }

    /** Register all Bukkit event listeners. */
    private void registerListeners() {
        getServer().getPluginManager().registerEvents(new PlayerJoinQuitListener(this), this);
        getServer().getPluginManager().registerEvents(new ChatListener(this),            this);
        getServer().getPluginManager().registerEvents(new PlayerKillListener(this),      this);
        getServer().getPluginManager().registerEvents(new VoteListener(this),            this);
    }

    // ----------------------------------------------------------------
    // Accessors
    // ----------------------------------------------------------------

    public static KMCCore getInstance()             { return instance; }

    public DatabaseManager    getDatabaseManager()   { return databaseManager; }
    public TeamManager        getTeamManager()       { return teamManager; }
    public PlayerDataManager  getPlayerDataManager() { return playerDataManager; }
    public TournamentManager  getTournamentManager() { return tournamentManager; }
    public GameManager        getGameManager()       { return gameManager; }
    public PointsManager      getPointsManager()     { return pointsManager; }
    public ScoreboardManager  getScoreboardManager() { return scoreboardManager; }
    public NPCManager         getNpcManager()        { return npcManager; }
    public AutomationManager  getAutomationManager() { return automationManager; }
    public TabListManager     getTabListManager()     { return tabListManager; }
    public ArenaManager       getArenaManager()       { return arenaManager; }
    public SchematicManager   getSchematicManager()   { return schematicManager; }
    public KMCApi             getApi()               { return api; }
}
