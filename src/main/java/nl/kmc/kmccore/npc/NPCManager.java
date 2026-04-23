package nl.kmc.kmccore.npc;

import nl.kmc.kmccore.KMCCore;
import nl.kmc.kmccore.models.KMCTeam;
import nl.kmc.kmccore.models.PlayerData;
import nl.kmc.kmccore.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.logging.Level;

/**
 * Manages hologram/ArmorStand NPCs that display leaderboard info.
 *
 * <p>This implementation uses invisible ArmorStand entities with custom names
 * as holograms. If Citizens is detected a future expansion can swap in
 * proper NPC entities.
 *
 * <p>Supported display types:
 * <ul>
 *   <li>{@code top_teams}     – top 5 teams by points</li>
 *   <li>{@code top_players}   – top 5 players by points</li>
 *   <li>{@code current_game}  – name of the active game</li>
 *   <li>{@code multiplier}    – current round multiplier</li>
 * </ul>
 *
 * <p>NPCs are persisted in {@code plugins/KMCCore/npcs.yml} and
 * restored on startup.
 */
public class NPCManager {

    // ----------------------------------------------------------------
    // Types
    // ----------------------------------------------------------------

    public enum NpcType {
        TOP_TEAMS, TOP_PLAYERS, CURRENT_GAME, MULTIPLIER;

        public static NpcType fromString(String s) {
            return switch (s.toLowerCase()) {
                case "top_teams"   -> TOP_TEAMS;
                case "top_players" -> TOP_PLAYERS;
                case "current_game"-> CURRENT_GAME;
                case "multiplier"  -> MULTIPLIER;
                default            -> null;
            };
        }
    }

    /** A single leaderboard NPC entry. */
    public static class KmcNpc {
        public final String      id;
        public final NpcType     type;
        public final Location    location;
        public final List<UUID>  standUuids = new ArrayList<>();  // UUIDs of spawned ArmorStands

        KmcNpc(String id, NpcType type, Location location) {
            this.id       = id;
            this.type     = type;
            this.location = location;
        }
    }

    // ----------------------------------------------------------------
    // Fields
    // ----------------------------------------------------------------

    private final KMCCore plugin;
    private final Map<String, KmcNpc> npcs = new LinkedHashMap<>();
    private int nextId = 1;

    private final File npcFile;
    private FileConfiguration npcConfig;

    // ----------------------------------------------------------------
    // Init
    // ----------------------------------------------------------------

    public NPCManager(KMCCore plugin) {
        this.plugin  = plugin;
        this.npcFile = new File(plugin.getDataFolder(), "npcs.yml");
        loadFromDisk();

        // Schedule periodic refresh (every 5 seconds)
        Bukkit.getScheduler().runTaskTimer(plugin, this::refreshAll, 100L, 100L);
    }

    // ----------------------------------------------------------------
    // CRUD
    // ----------------------------------------------------------------

    /**
     * Creates a new NPC at the given location.
     *
     * @param type     display type
     * @param location spawn location
     * @return the created NPC
     */
    public KmcNpc createNpc(NpcType type, Location location) {
        String id = "npc_" + nextId++;
        KmcNpc npc = new KmcNpc(id, type, location);
        npcs.put(id, npc);
        spawnStands(npc);
        save();
        return npc;
    }

    /**
     * Removes an NPC and despawns its ArmorStands.
     *
     * @param id NPC id
     * @return {@code false} if not found
     */
    public boolean removeNpc(String id) {
        KmcNpc npc = npcs.remove(id);
        if (npc == null) return false;
        despawnStands(npc);
        save();
        return true;
    }

    public Collection<KmcNpc> getAllNpcs() {
        return Collections.unmodifiableCollection(npcs.values());
    }

    // ----------------------------------------------------------------
    // ArmorStand helpers
    // ----------------------------------------------------------------

    /** Spawns stacked ArmorStands at {@code npc.location} with placeholder names. */
    private void spawnStands(KmcNpc npc) {
        int lines = getLinesForType(npc.type);
        Location base = npc.location.clone();

        for (int i = 0; i < lines; i++) {
            base.setY(npc.location.getY() + (lines - i - 1) * 0.25);
            ArmorStand as = (ArmorStand) base.getWorld().spawnEntity(base, EntityType.ARMOR_STAND);
            as.setVisible(false);
            as.setCustomNameVisible(true);
            as.setGravity(false);
            as.setSmall(true);
            as.setInvulnerable(true);
            as.setCustomName("...");
            npc.standUuids.add(as.getUniqueId());
        }

        updateStandNames(npc);
    }

    /** Removes all ArmorStands belonging to an NPC. */
    private void despawnStands(KmcNpc npc) {
        for (UUID uuid : npc.standUuids) {
            Entity e = Bukkit.getEntity(uuid);
            if (e != null) e.remove();
        }
        npc.standUuids.clear();
    }

    /** Updates name tags on all stands for an NPC. */
    private void updateStandNames(KmcNpc npc) {
        List<String> lines = buildLines(npc.type);
        for (int i = 0; i < npc.standUuids.size() && i < lines.size(); i++) {
            Entity e = Bukkit.getEntity(npc.standUuids.get(i));
            if (e instanceof ArmorStand as) {
                as.setCustomName(MessageUtil.color(lines.get(i)));
            }
        }
    }

    // ----------------------------------------------------------------
    // Line builders
    // ----------------------------------------------------------------

    private List<String> buildLines(NpcType type) {
        return switch (type) {
            case TOP_TEAMS   -> buildTopTeams();
            case TOP_PLAYERS -> buildTopPlayers();
            case CURRENT_GAME-> buildCurrentGame();
            case MULTIPLIER  -> buildMultiplier();
        };
    }

    private List<String> buildTopTeams() {
        List<String> lines = new ArrayList<>();
        lines.add("&6&lTop Teams");
        List<KMCTeam> teams = plugin.getTeamManager().getTeamsSortedByPoints();
        int rank = 1;
        for (KMCTeam t : teams) {
            if (rank > 5) break;
            String medal = rank == 1 ? "&6" : rank == 2 ? "&7" : "&c";
            lines.add(medal + "#" + rank + " " + t.getColor() + t.getDisplayName()
                      + " &8- &e" + t.getPoints());
            rank++;
        }
        return lines;
    }

    private List<String> buildTopPlayers() {
        List<String> lines = new ArrayList<>();
        lines.add("&e&lTop Spelers");
        List<PlayerData> players = plugin.getPlayerDataManager().getLeaderboard();
        int rank = 1;
        for (PlayerData pd : players) {
            if (rank > 5) break;
            String medal = rank == 1 ? "&6" : rank == 2 ? "&7" : "&c";
            lines.add(medal + "#" + rank + " &f" + pd.getName() + " &8- &e" + pd.getPoints());
            rank++;
        }
        return lines;
    }

    private List<String> buildCurrentGame() {
        List<String> lines = new ArrayList<>();
        lines.add("&b&lHuidige Game");
        String gameName = plugin.getGameManager().getActiveGame() != null
                          ? plugin.getGameManager().getActiveGame().getDisplayName()
                          : "&8Geen";
        lines.add("&f" + gameName);
        return lines;
    }

    private List<String> buildMultiplier() {
        List<String> lines = new ArrayList<>();
        lines.add("&d&lMultiplier");
        lines.add("&ex" + plugin.getTournamentManager().getMultiplier());
        lines.add("&7Ronde " + plugin.getTournamentManager().getCurrentRound());
        return lines;
    }

    private int getLinesForType(NpcType type) {
        return switch (type) {
            case TOP_TEAMS, TOP_PLAYERS -> 6;
            case CURRENT_GAME           -> 2;
            case MULTIPLIER             -> 3;
        };
    }

    // ----------------------------------------------------------------
    // Refresh
    // ----------------------------------------------------------------

    /** Refreshes name tags on all NPC stands. */
    public void refreshAll() {
        for (KmcNpc npc : npcs.values()) {
            updateStandNames(npc);
        }
    }

    // ----------------------------------------------------------------
    // Persistence (npcs.yml)
    // ----------------------------------------------------------------

    private void loadFromDisk() {
        if (!npcFile.exists()) return;
        npcConfig = YamlConfiguration.loadConfiguration(npcFile);

        ConfigurationSection sec = npcConfig.getConfigurationSection("npcs");
        if (sec == null) return;

        for (String id : sec.getKeys(false)) {
            ConfigurationSection nc = sec.getConfigurationSection(id);
            if (nc == null) continue;

            String typeStr = nc.getString("type", "top_teams");
            NpcType type   = NpcType.fromString(typeStr);
            if (type == null) continue;

            Location loc = (Location) nc.get("location");
            if (loc == null) continue;

            KmcNpc npc = new KmcNpc(id, type, loc);
            npcs.put(id, npc);
            spawnStands(npc);

            // Figure out next available ID
            try {
                int num = Integer.parseInt(id.replace("npc_", ""));
                if (num >= nextId) nextId = num + 1;
            } catch (NumberFormatException ignored) {}
        }

        plugin.getLogger().info("Loaded " + npcs.size() + " NPCs.");
    }

    public void save() {
        npcConfig = new YamlConfiguration();

        for (KmcNpc npc : npcs.values()) {
            String path = "npcs." + npc.id;
            npcConfig.set(path + ".type",     npc.type.name().toLowerCase());
            npcConfig.set(path + ".location", npc.location);
        }

        try {
            npcConfig.save(npcFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to save npcs.yml", e);
        }
    }
}
