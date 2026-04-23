package nl.kmc.kmccore.managers;

import nl.kmc.kmccore.KMCCore;
import nl.kmc.kmccore.models.KMCTeam;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.*;

/**
 * Manages the "arena" for each game:
 * <ul>
 *   <li>Loads/pastes the schematic at game start</li>
 *   <li>Teleports players to their team spawns</li>
 *   <li>Resets the arena at game end by re-pasting</li>
 *   <li>Holds the global lobby/waiting-room location</li>
 * </ul>
 *
 * <p>Each game has ONE arena. Config structure:
 * <pre>
 * games:
 *   list:
 *     lucky_block:
 *       display-name: "Lucky Block"
 *       schematic: lucky_block.schem
 *       arena-origin: &lt;location&gt;   # set with /kmcgame setorigin
 *       team-spawns:
 *         rode_ratten: &lt;location&gt;
 *         blauwe_bavianen: &lt;location&gt;
 *         ...
 *       solo-spawns:                 # used for FFA / solo games
 *         - &lt;location&gt;
 *         - &lt;location&gt;
 * </pre>
 */
public class ArenaManager {

    private final KMCCore plugin;

    /** Global lobby location — where players go between games. */
    private Location lobby;

    public ArenaManager(KMCCore plugin) {
        this.plugin = plugin;
        load();
    }

    // ----------------------------------------------------------------
    // Lobby
    // ----------------------------------------------------------------

    private void load() {
        lobby = plugin.getConfig().getLocation("arena.lobby");
        if (lobby != null) {
            plugin.getLogger().info("Lobby loaded at " + formatLoc(lobby));
        } else {
            plugin.getLogger().warning("No lobby set! Use /kmcarena setlobby");
        }
    }

    public Location getLobby() { return lobby; }

    public void setLobby(Location loc) {
        this.lobby = loc.clone();
        plugin.getConfig().set("arena.lobby", this.lobby);
        plugin.saveConfig();
    }

    /** Teleports every online player to the lobby. */
    public void teleportAllToLobby() {
        if (lobby == null) return;
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.teleport(lobby);
            p.setGameMode(org.bukkit.GameMode.ADVENTURE);
            p.setHealth(20);
            p.setFoodLevel(20);
            p.getInventory().clear();
        }
    }

    // ----------------------------------------------------------------
    // Team spawns
    // ----------------------------------------------------------------

    public void setTeamSpawn(String gameId, String teamId, Location loc) {
        plugin.getConfig().set("games.list." + gameId + ".team-spawns." + teamId, loc);
        plugin.saveConfig();
    }

    public Location getTeamSpawn(String gameId, String teamId) {
        return plugin.getConfig().getLocation("games.list." + gameId + ".team-spawns." + teamId);
    }

    public Map<String, Location> getAllTeamSpawns(String gameId) {
        Map<String, Location> result = new LinkedHashMap<>();
        ConfigurationSection sec = plugin.getConfig()
                .getConfigurationSection("games.list." + gameId + ".team-spawns");
        if (sec == null) return result;
        for (String teamId : sec.getKeys(false)) {
            Location l = sec.getLocation(teamId);
            if (l != null) result.put(teamId, l);
        }
        return result;
    }

    // ----------------------------------------------------------------
    // Solo spawns (FFA games)
    // ----------------------------------------------------------------

    public void addSoloSpawn(String gameId, Location loc) {
        List<Location> list = getSoloSpawns(gameId);
        list.add(loc.clone());
        plugin.getConfig().set("games.list." + gameId + ".solo-spawns", list);
        plugin.saveConfig();
    }

    @SuppressWarnings("unchecked")
    public List<Location> getSoloSpawns(String gameId) {
        List<Location> result = new ArrayList<>();
        Object raw = plugin.getConfig().get("games.list." + gameId + ".solo-spawns");
        if (raw instanceof List<?> list) {
            for (Object o : list) if (o instanceof Location l) result.add(l);
        }
        return result;
    }

    public void clearSoloSpawns(String gameId) {
        plugin.getConfig().set("games.list." + gameId + ".solo-spawns", new ArrayList<>());
        plugin.saveConfig();
    }

    // ----------------------------------------------------------------
    // Arena lifecycle
    // ----------------------------------------------------------------

    /**
     * Called at the start of a game:
     * 1. Pastes the schematic
     * 2. Teleports players to their team / solo spawns
     *
     * @param gameId the game being started
     * @return {@code true} on success
     */
    public boolean loadArenaForGame(String gameId) {
        String schematic = plugin.getSchematicManager().getSchematicForGame(gameId);
        Location origin  = plugin.getSchematicManager().getOriginForGame(gameId);

        if (schematic == null || origin == null) {
            plugin.getLogger().warning("Arena not configured for " + gameId
                    + " — skipping schematic paste. Set origin with /kmcgame setorigin " + gameId);
            // Still teleport players if spawns exist
            teleportPlayersForGame(gameId);
            return false;
        }

        boolean ok = plugin.getSchematicManager().pasteSchematic(schematic, origin);
        if (ok) teleportPlayersForGame(gameId);
        return ok;
    }

    /**
     * Called at the end of a game: resets the arena by re-pasting the schematic.
     */
    public boolean resetArenaForGame(String gameId) {
        String schematic = plugin.getSchematicManager().getSchematicForGame(gameId);
        Location origin  = plugin.getSchematicManager().getOriginForGame(gameId);
        if (schematic == null || origin == null) return false;
        return plugin.getSchematicManager().resetArena(schematic, origin);
    }

    /**
     * Teleports all online players to their team or solo spawn for this game.
     */
    public void teleportPlayersForGame(String gameId) {
        Map<String, Location> teamSpawns = getAllTeamSpawns(gameId);
        List<Location>        soloSpawns = getSoloSpawns(gameId);

        List<Player> soloQueue = new ArrayList<>();
        int soloIndex = 0;

        for (Player p : Bukkit.getOnlinePlayers()) {
            KMCTeam team = plugin.getTeamManager().getTeamByPlayer(p.getUniqueId());

            Location dest = null;

            // Prefer team spawn if available
            if (team != null && teamSpawns.containsKey(team.getId())) {
                dest = teamSpawns.get(team.getId());
            }
            // Else use solo spawn
            else if (!soloSpawns.isEmpty()) {
                dest = soloSpawns.get(soloIndex % soloSpawns.size());
                soloIndex++;
            }

            if (dest != null) {
                p.teleport(dest);
                p.setGameMode(org.bukkit.GameMode.SURVIVAL);
                p.setHealth(20);
                p.setFoodLevel(20);
                p.getInventory().clear();
            } else {
                plugin.getLogger().warning("No spawn available for " + p.getName() + " in game " + gameId);
            }
        }
    }

    // ----------------------------------------------------------------
    // Status helpers
    // ----------------------------------------------------------------

    public String getStatusReport(String gameId) {
        String schematic = plugin.getSchematicManager().getSchematicForGame(gameId);
        Location origin  = plugin.getSchematicManager().getOriginForGame(gameId);
        int teamSpawns   = getAllTeamSpawns(gameId).size();
        int soloSpawns   = getSoloSpawns(gameId).size();

        StringBuilder sb = new StringBuilder();
        sb.append("Schematic:    ").append(schematic != null ? "✔ " + schematic : "✘ niet ingesteld").append("\n");
        sb.append("Arena origin: ").append(origin != null ? "✔ " + formatLoc(origin) : "✘ niet ingesteld").append("\n");
        sb.append("Team spawns:  ").append(teamSpawns).append(" / 8 teams\n");
        sb.append("Solo spawns:  ").append(soloSpawns);
        return sb.toString();
    }

    private String formatLoc(Location l) {
        return l.getWorld().getName() + " " + l.getBlockX() + "," + l.getBlockY() + "," + l.getBlockZ();
    }
}
