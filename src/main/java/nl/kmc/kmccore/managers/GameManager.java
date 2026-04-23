package nl.kmc.kmccore.managers;

import nl.kmc.kmccore.KMCCore;
import nl.kmc.kmccore.models.KMCGame;
import nl.kmc.kmccore.util.AnnouncementUtil;
import nl.kmc.kmccore.util.ClickableVoteMessage;
import nl.kmc.kmccore.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Manages the game pool, random selection, voting, and active game state.
 *
 * <p>Features:
 * <ul>
 *   <li>Loads all games from config</li>
 *   <li>Tracks recently played games to prevent repeats</li>
 *   <li>Optional vote for next game</li>
 *   <li>Start / stop / skip game lifecycle</li>
 * </ul>
 */
public class GameManager {

    private final KMCCore plugin;

    /** All registered games, keyed by ID. */
    private final Map<String, KMCGame> games = new LinkedHashMap<>();

    /** Currently running game (null = no active game). */
    private KMCGame activeGame;

    /** Game scheduled to run next (may be overridden by vote). */
    private KMCGame nextGame;

    /** Ring-buffer of recently played game IDs for cooldown check. */
    private final Deque<String> recentGames = new ArrayDeque<>();

    // Vote state
    private boolean          votingActive;
    private List<KMCGame>    voteOptions;
    private Map<UUID, String> votes;   // player UUID → voted game ID
    private BukkitTask        voteTask;

    private final Random random = new Random();

    // ----------------------------------------------------------------
    // Init
    // ----------------------------------------------------------------

    public GameManager(KMCCore plugin) {
        this.plugin = plugin;
        loadGamesFromConfig();
        loadState();
    }

    // ----------------------------------------------------------------
    // Config loading
    // ----------------------------------------------------------------

    private void loadGamesFromConfig() {
        ConfigurationSection gameSection = plugin.getConfig().getConfigurationSection("games.list");
        if (gameSection == null) {
            plugin.getLogger().warning("No games found in config.yml under 'games.list'!");
            return;
        }

        for (String key : gameSection.getKeys(false)) {
            ConfigurationSection gc = gameSection.getConfigurationSection(key);
            if (gc == null) continue;

            String  name       = gc.getString("display-name", key);
            String  iconName   = gc.getString("icon", "PAPER");
            int     minPlayers = gc.getInt("min-players", 2);

            Material icon;
            try {
                icon = Material.valueOf(iconName.toUpperCase());
            } catch (IllegalArgumentException e) {
                icon = Material.PAPER;
            }

            games.put(key, new KMCGame(key, name, icon, minPlayers));
        }

        plugin.getLogger().info("Loaded " + games.size() + " games.");
    }

    // ----------------------------------------------------------------
    // State persistence (lightweight – uses tournament_state table)
    // ----------------------------------------------------------------

    private void loadState() {
        String activeId = plugin.getDatabaseManager()
                .getTournamentValue("active_game", null);
        if (activeId != null) activeGame = games.get(activeId);
    }

    public void save() {
        plugin.getDatabaseManager().setTournamentValue(
                "active_game", activeGame != null ? activeGame.getId() : "");
    }

    // ----------------------------------------------------------------
    // Game lifecycle
    // ----------------------------------------------------------------

    /**
     * Starts the specified game.
     *
     * @param gameId game to start
     * @return {@code false} if game not found or already running
     */
    public boolean startGame(String gameId) {
        KMCGame game = games.get(gameId);
        if (game == null) return false;

        activeGame = game;
        nextGame   = null;
        recentGames.addLast(gameId);

        int cooldown = plugin.getConfig().getInt("games.repeat-cooldown", 2);
        while (recentGames.size() > cooldown + 1) recentGames.pollFirst();

        save();

        // Paste arena schematic and teleport players to their spawns
        // Runs ~2 ticks later so the "game starting" title has time to show
        final String gId = gameId;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            plugin.getArenaManager().loadArenaForGame(gId);
        }, 10L);

        AnnouncementUtil.broadcastTitle(plugin, "announcements.game-start",
                new String[]{"{game_name}", "{round}", "{multiplier}"},
                new String[]{game.getDisplayName(),
                        String.valueOf(plugin.getTournamentManager().getCurrentRound()),
                        String.valueOf(plugin.getTournamentManager().getMultiplier())});

        String msg = MessageUtil.get("broadcast.game-start")
                .replace("{game_name}",  game.getDisplayName())
                .replace("{round}",      String.valueOf(plugin.getTournamentManager().getCurrentRound()))
                .replace("{multiplier}", String.valueOf(plugin.getTournamentManager().getMultiplier()));
        Bukkit.broadcastMessage(msg);

        plugin.getScoreboardManager().refreshAll();
        return true;
    }

    /**
     * Stops the current active game.
     *
     * @param winnerName display name of the winner (team or player name)
     * @return {@code false} if no game is active
     */
    public boolean stopGame(String winnerName) {
        if (activeGame == null) return false;

        String gameName = activeGame.getDisplayName();
        activeGame = null;
        save();

        AnnouncementUtil.broadcastTitle(plugin, "announcements.game-end",
                new String[]{"{winner}"},
                new String[]{winnerName != null ? winnerName : "?"});

        String msg = MessageUtil.get("broadcast.game-end")
                .replace("{winner}", winnerName != null ? winnerName : "?");
        Bukkit.broadcastMessage(msg);

        plugin.getScoreboardManager().refreshAll();

        // Fire API hook
        plugin.getApi().fireGameEnd(gameName, winnerName);

        // Reset arena schematic + teleport survivors to lobby
        // Delayed 3 seconds so players see the win title first
        final String finishedGameId = gameName;  // just for logging
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            // Find the game ID from the game we just stopped (stored in a local variable)
            String gid = null;
            for (KMCGame g : games.values()) {
                if (g.getDisplayName().equals(finishedGameId) || g.getId().equals(finishedGameId)) {
                    gid = g.getId(); break;
                }
            }
            if (gid != null) plugin.getArenaManager().resetArenaForGame(gid);
            plugin.getArenaManager().teleportAllToLobby();
        }, 60L);

        // Notify automation engine so it can start the intermission countdown
        if (plugin.getAutomationManager().isRunning()) {
            plugin.getAutomationManager().onGameEnd(winnerName);
        }
        return true;
    }

    /**
     * Skips the current game without awarding points.
     */
    public boolean skipGame() {
        if (activeGame == null) return false;
        activeGame = null;
        save();
        plugin.getScoreboardManager().refreshAll();
        return true;
    }

    // ----------------------------------------------------------------
    // Randomizer
    // ----------------------------------------------------------------

    /**
     * Selects the next game randomly, respecting the cooldown list.
     *
     * @return the selected game, or {@code null} if no games available
     */
    public KMCGame randomNextGame() {
        List<KMCGame> eligible = games.values().stream()
                .filter(g -> !recentGames.contains(g.getId()))
                .collect(Collectors.toList());

        if (eligible.isEmpty()) eligible = new ArrayList<>(games.values());
        if (eligible.isEmpty()) return null;

        KMCGame chosen = eligible.get(random.nextInt(eligible.size()));
        nextGame = chosen;
        return chosen;
    }

    /**
     * Forces the next game to a specific game ID.
     *
     * @return {@code false} if not found
     */
    public boolean forceNextGame(String gameId) {
        KMCGame game = games.get(gameId);
        if (game == null) return false;
        nextGame = game;
        return true;
    }

    // ----------------------------------------------------------------
    // Voting
    // ----------------------------------------------------------------

    /**
     * Starts a vote for the next game.
     * Picks {@code voting-options} random eligible games and broadcasts them.
     */
    /**
     * Opens a vote for the next game.
     * NOTE: The countdown timer is owned by AutomationManager — this method
     * only sets up state and sends the clickable UI message.
     */
    public void startVote() {
        if (votingActive) return;

        int optionCount  = plugin.getConfig().getInt("games.voting-options", 3);
        int durationSecs = plugin.getConfig().getInt("games.voting-duration", 30);

        // Pick random options, excluding recently played games
        List<KMCGame> pool = games.values().stream()
                .filter(g -> !recentGames.contains(g.getId()))
                .collect(Collectors.toList());
        if (pool.isEmpty()) pool = new ArrayList<>(games.values());
        Collections.shuffle(pool, random);
        voteOptions  = pool.stream().limit(optionCount).collect(Collectors.toList());
        votes        = new HashMap<>();
        votingActive = true;

        // Send clickable vote UI (Adventure API — no plain text fallback needed on Paper 1.21)
        ClickableVoteMessage.send(voteOptions, durationSecs);
    }

    /** Processes a vote from a player. */
    public boolean castVote(Player player, int option) {
        if (!votingActive) return false;
        if (option < 1 || option > voteOptions.size()) return false;
        if (votes.containsKey(player.getUniqueId())) {
            player.sendMessage(MessageUtil.get("vote.already-voted"));
            return false;
        }

        String gameId = voteOptions.get(option - 1).getId();
        votes.put(player.getUniqueId(), gameId);

        player.sendMessage(MessageUtil.get("vote.submit")
                .replace("{game}", voteOptions.get(option - 1).getDisplayName()));
        return true;
    }

    /** Ends the vote and selects the winner. */
    public void endVote() {
        if (!votingActive) return;
        if (voteTask != null) { voteTask.cancel(); voteTask = null; }
        votingActive = false;

        // Count votes
        Map<String, Integer> tally = new HashMap<>();
        for (String gameId : votes.values()) {
            tally.merge(gameId, 1, Integer::sum);
        }

        String winnerId = tally.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);

        // Fallback to random if no votes
        if (winnerId == null) {
            KMCGame rng = randomNextGame();
            if (rng != null) winnerId = rng.getId();
        }

        if (winnerId != null) {
            KMCGame winner = games.get(winnerId);
            nextGame = winner;
            ClickableVoteMessage.sendResult(winner, tally.getOrDefault(winnerId, 0));
        }
    }

    // ----------------------------------------------------------------
    // Getters
    // ----------------------------------------------------------------

    public KMCGame        getActiveGame()  { return activeGame; }
    public KMCGame        getNextGame()    { return nextGame; }
    public boolean        isVotingActive() { return votingActive; }
    public List<KMCGame>  getVoteOptions() { return voteOptions; }
    public Collection<KMCGame> getAllGames(){ return Collections.unmodifiableCollection(games.values()); }
    public KMCGame        getGame(String id){ return games.get(id); }
    public boolean        isGameActive()   { return activeGame != null; }
}
