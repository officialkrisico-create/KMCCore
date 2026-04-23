package nl.kmc.kmccore.scoreboard;

import nl.kmc.kmccore.KMCCore;
import nl.kmc.kmccore.models.KMCTeam;
import nl.kmc.kmccore.models.PlayerData;
import nl.kmc.kmccore.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Provides a live-updating sidebar scoreboard for each online player.
 *
 * <p>The scoreboard displays:
 * <ul>
 *   <li>Current round and multiplier</li>
 *   <li>Active game name</li>
 *   <li>Top 3 teams by points</li>
 *   <li>Player's personal coins/points</li>
 * </ul>
 *
 * <p>Updated every {@code scoreboard.update-interval} ticks (default 20 = 1s).
 */
public class ScoreboardManager {

    private final KMCCore plugin;

    /** Per-player scoreboard instances. */
    private final Map<UUID, Scoreboard> boards = new HashMap<>();

    /** Repeating task that ticks the update. */
    private BukkitTask updateTask;

    // ----------------------------------------------------------------
    // Init
    // ----------------------------------------------------------------

    public ScoreboardManager(KMCCore plugin) {
        this.plugin = plugin;
        if (plugin.getConfig().getBoolean("scoreboard.enabled", true)) {
            startUpdateTask();
        }
    }

    // ----------------------------------------------------------------
    // Update loop
    // ----------------------------------------------------------------

    private void startUpdateTask() {
        int interval = plugin.getConfig().getInt("scoreboard.update-interval", 20);
        updateTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickAll, interval, interval);
    }

    /** Updates scoreboards for all online players. */
    private void tickAll() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            updatePlayer(p);
        }
    }

    /** Forces immediate refresh for all online players. */
    public void refreshAll() {
        tickAll();
    }

    // ----------------------------------------------------------------
    // Per-player scoreboard
    // ----------------------------------------------------------------

    /**
     * Creates or updates the sidebar scoreboard for a single player.
     */
//    public void updatePlayer(Player player) {
//        Scoreboard board = boards.computeIfAbsent(player.getUniqueId(),
//                uuid -> Bukkit.getScoreboardManager().getNewScoreboard());
//
//        String objName = "kmc_sidebar";
//        Objective obj = board.getObjective(objName);
//
//        // Recreate objective to allow full title/line changes
//        if (obj != null) obj.unregister();
//        obj = board.registerNewObjective(objName, Criteria.DUMMY,
//                MessageUtil.color(plugin.getConfig().getString("scoreboard.title", "&6&lKMC")));
//        obj.setDisplaySlot(DisplaySlot.SIDEBAR);
//
//        // Gather data
//        boolean active       = plugin.getTournamentManager().isActive();
//        int     round        = plugin.getTournamentManager().getCurrentRound();
//        double  mul          = plugin.getTournamentManager().getMultiplier();
//        String  gameName     = plugin.getGameManager().getActiveGame() != null
//                               ? plugin.getGameManager().getActiveGame().getDisplayName()
//                               : "&8Geen";
//
//        PlayerData pd        = plugin.getPlayerDataManager().get(player.getUniqueId());
//        int coins  = pd != null ? pd.getCoins()  : 0;
//        int ppoints= pd != null ? pd.getPoints() : 0;
//
//        KMCTeam myTeam = plugin.getTeamManager().getTeamByPlayer(player.getUniqueId());
//
//        List<KMCTeam> top3 = plugin.getTeamManager().getTeamsSortedByPoints()
//                .stream().limit(3).toList();
//
//        // Build lines (score = display order, highest = top)
//        int line = 14;
//
//        // --- Header spacer
//        line = setLine(obj, board, line, "&r");
//
//        // --- Tournament status
//        if (!active) {
//            line = setLine(obj, board, line, "&7Status: &cInactief");
//        } else {
//            line = setLine(obj, board, line, "&7Ronde: &e" + round + " &8(&e" + mul + "x&8)");
//            line = setLine(obj, board, line, "&7Game: &b" + gameName);
//        }
//
//        // --- Spacer
//        line = setLine(obj, board, line, "&r ");
//
//        // --- Team leaderboard
//        line = setLine(obj, board, line, "&6&lTop Teams:");
//        for (int i = 0; i < top3.size(); i++) {
//            KMCTeam t = top3.get(i);
//            String medal = i == 0 ? "&6#1" : i == 1 ? "&7#2" : "&c#3";
//            line = setLine(obj, board, line,
//                    medal + " " + t.getColor() + t.getDisplayName() + " &8- &e" + t.getPoints());
//        }
//
//        // --- Spacer
//        line = setLine(obj, board, line, "&r  ");
//
//        // --- Player stats
//        if (myTeam != null) {
//            line = setLine(obj, board, line,
//                    "&7Team: " + myTeam.getColor() + myTeam.getDisplayName());
//        }
//        line = setLine(obj, board, line, "&7Punten: &e" + ppoints);
//        line = setLine(obj, board, line, "&7Munten: &b" + coins);
//
//        // --- Bottom spacer
//        line = setLine(obj, board, line, "&r   ");
//
//        player.setScoreboard(board);
//    }
    public void updatePlayer(Player player) {
        Scoreboard board = boards.computeIfAbsent(player.getUniqueId(),
                uuid -> Bukkit.getScoreboardManager().getNewScoreboard());


        PlayerData pd = plugin.getPlayerDataManager().get(player.getUniqueId());

        int ppoints = (pd != null) ? pd.getPoints() : 0;
        KMCTeam myTeam = plugin.getTeamManager().getTeamByPlayer(player.getUniqueId());


        if (myTeam != null) {
            // Gebruik de displaynaam of kleur-naam als ID voor het team
            String teamId = myTeam.getDisplayName().replace(" ", "_");
            Team boardTeam = board.getTeam(teamId);
            if (boardTeam == null) {
                boardTeam = board.registerNewTeam(teamId);
            }
            boardTeam.setColor(myTeam.getColor());
            if (!boardTeam.hasEntry(player.getName())) {
                boardTeam.addEntry(player.getName());
            }
        }


        String objName = "kmc_sidebar";
        Objective obj = board.getObjective(objName);
        if (obj != null) obj.unregister();

        obj = board.registerNewObjective(objName, Criteria.DUMMY,
                MessageUtil.color(plugin.getConfig().getString("scoreboard.title", "&6&lKMC")));
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);


        boolean active = plugin.getTournamentManager().isActive();
        int round = plugin.getTournamentManager().getCurrentRound();
        double mul = plugin.getTournamentManager().getMultiplier();
        String gameName = plugin.getGameManager().getActiveGame() != null
                ? plugin.getGameManager().getActiveGame().getDisplayName()
                : "&8Geen";

        List<KMCTeam> top3 = plugin.getTeamManager().getTeamsSortedByPoints()
                .stream().limit(3).toList();

        int line = 14;
        line = setLine(obj, board, line, "&r");

        if (!active) {
            line = setLine(obj, board, line, "&7Status: &cInactief");
        } else {
            line = setLine(obj, board, line, "&7Ronde: &e" + round + " &8(&e" + mul + "x&8)");
            line = setLine(obj, board, line, "&7Game: &b" + gameName);
        }

        line = setLine(obj, board, line, "&r ");
        line = setLine(obj, board, line, "&6&lTop Teams:");
        for (int i = 0; i < top3.size(); i++) {
            KMCTeam t = top3.get(i);
            String medal = i == 0 ? "&6#1" : i == 1 ? "&7#2" : "&c#3";
            line = setLine(obj, board, line,
                    medal + " " + t.getColor() + t.getDisplayName() + " &8- &e" + t.getPoints());
        }

        line = setLine(obj, board, line, "&r  ");


        if (myTeam != null) {
            line = setLine(obj, board, line, "&7Team: " + myTeam.getColor() + myTeam.getDisplayName());
        } else {
            line = setLine(obj, board, line, "&7Team: &8Geen");
        }

        line = setLine(obj, board, line, "&7Punten: &e" + ppoints);

        line = setLine(obj, board, line, "&r   ");

        player.setScoreboard(board);
    }

    /**
     * Helper to set a scoreboard line.
     * Uses unique invisible colours as entry identifiers so duplicate
     * text doesn't cause Bukkit to merge lines.
     *
     * @return {@code lineIndex - 1}
     */
    private int setLine(Objective obj, Scoreboard board, int lineIndex, String text) {
        // Use invisible colour codes to make each entry unique
        String unique = ChatColor.values()[lineIndex % ChatColor.values().length].toString()
                + ChatColor.RESET.toString();
        String entry  = unique + MessageUtil.color(text);

        // Remove stale entries at this score
        for (String e : board.getEntries()) {
            Integer s = board.getObjective(obj.getName()) != null
                        ? null : null;
            // Clean entries from previous cycle that start with our unique code
            if (e.startsWith(unique)) board.resetScores(e);
        }

        Score score = obj.getScore(entry);
        score.setScore(lineIndex);
        return lineIndex - 1;
    }

    // ----------------------------------------------------------------
    // Lifecycle
    // ----------------------------------------------------------------

    /** Called when a player joins – initialise their board. */
    public void onPlayerJoin(Player player) {
        updatePlayer(player);
    }

    /** Called when a player leaves – remove their board. */
    public void onPlayerQuit(Player player) {
        boards.remove(player.getUniqueId());
    }

    /** Cancels the update task and cleans up. */
    public void cleanup() {
        if (updateTask != null) updateTask.cancel();
        boards.clear();
    }

}
