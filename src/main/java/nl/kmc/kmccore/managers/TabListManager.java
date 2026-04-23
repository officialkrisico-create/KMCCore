package nl.kmc.kmccore.managers;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import nl.kmc.kmccore.KMCCore;
import nl.kmc.kmccore.models.KMCTeam;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.HashMap;
import java.util.Map;

/**
 * Manages coloured player names in:
 * <ul>
 *   <li>The TAB player list</li>
 *   <li>Chat messages</li>
 *   <li>Above-head nametags</li>
 * </ul>
 */
public class TabListManager {

    private final KMCCore plugin;

    /**
     * Maps ChatColor → NamedTextColor for Adventure conversion.
     */
    private static final Map<ChatColor, NamedTextColor> COLOR_MAP = new HashMap<>();
    static {
        COLOR_MAP.put(ChatColor.RED,          NamedTextColor.RED);
        COLOR_MAP.put(ChatColor.GOLD,         NamedTextColor.GOLD);
        COLOR_MAP.put(ChatColor.YELLOW,       NamedTextColor.YELLOW);
        COLOR_MAP.put(ChatColor.GREEN,        NamedTextColor.GREEN);
        COLOR_MAP.put(ChatColor.BLUE,         NamedTextColor.BLUE);
        COLOR_MAP.put(ChatColor.DARK_PURPLE,  NamedTextColor.DARK_PURPLE);
        COLOR_MAP.put(ChatColor.LIGHT_PURPLE, NamedTextColor.LIGHT_PURPLE);
        COLOR_MAP.put(ChatColor.WHITE,        NamedTextColor.WHITE);
        COLOR_MAP.put(ChatColor.DARK_GREEN,   NamedTextColor.DARK_GREEN);
        COLOR_MAP.put(ChatColor.AQUA,         NamedTextColor.AQUA);
        COLOR_MAP.put(ChatColor.DARK_AQUA,    NamedTextColor.DARK_AQUA);
    }

    public TabListManager(KMCCore plugin) {
        this.plugin = plugin;
    }

    // ----------------------------------------------------------------
    // Tab list header / footer
    // ----------------------------------------------------------------

    /**
     * Updates the TAB header and footer for a single player.
     */
    public void updateTabList(Player player) {
        KMCTeam team = plugin.getTeamManager().getTeamByPlayer(player.getUniqueId());

        String rawHeader = plugin.getConfig().getString("tablist.header", "\n&6&lKMC Tournament\n")
                .replace("{round}", String.valueOf(plugin.getTournamentManager().getCurrentRound()))
                .replace("{multiplier}", String.valueOf(plugin.getTournamentManager().getMultiplier()));

        String rawFooter = plugin.getConfig().getString("tablist.footer", "\n&7Team: {team_color}{team_name}\n")
                .replace("{team_color}", team != null ? team.getColor().toString() : "")
                .replace("{team_name}", team != null ? team.getDisplayName() : "Geen");

        player.sendPlayerListHeaderAndFooter(
                fromLegacy(rawHeader),
                fromLegacy(rawFooter)
        );
    }

    /**
     * Refreshes tab list header/footer for every online player.
     */
    public void refreshAll() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            updateTabList(p);
        }
    }

    // ----------------------------------------------------------------
    // Nametag scoreboard (above head + tab name colour)
    // ----------------------------------------------------------------

    /**
     * Applies the team's colour to a player's nametag and TAB list.
     */
    public void applyTeamNametag(Player player) {
        Scoreboard board = plugin.getTeamManager().getNametagScoreboard();
        KMCTeam team = plugin.getTeamManager().getTeamByPlayer(player.getUniqueId());

        // Remove player from all KMC scoreboard teams first
        for (Team bt : board.getTeams()) {
            if (bt.getName().startsWith("kmc_")) {
                bt.removeEntry(player.getName());
            }
        }

        if (team == null) {
            player.playerListName(Component.text(player.getName(), NamedTextColor.WHITE));
            return;
        }

        Team bt = board.getTeam("kmc_" + team.getId());
        if (bt == null) return;

        NamedTextColor ntc = COLOR_MAP.getOrDefault(team.getColor(), NamedTextColor.WHITE);

        // Set nametag / scoreboard formatting
        bt.prefix(Component.text("[" + team.getDisplayName() + "] ", ntc, TextDecoration.BOLD));
        bt.color(ntc);
        bt.addEntry(player.getName());

        // Set TAB list formatting explicitly
        player.playerListName(
                Component.text("[", NamedTextColor.GRAY)
                        .append(Component.text(team.getDisplayName(), ntc, TextDecoration.BOLD))
                        .append(Component.text("] ", NamedTextColor.GRAY))
                        .append(Component.text(player.getName(), ntc))
        );

        // Apply scoreboard to all players so everyone sees updates
        for (Player online : Bukkit.getOnlinePlayers()) {
            online.setScoreboard(board);
        }
    }

    // ----------------------------------------------------------------
    // Chat formatting
    // ----------------------------------------------------------------

    /**
     * Builds a chat Component for a player message with their team prefix.
     */
    public Component buildChatMessage(Player player, String message) {
        KMCTeam team = plugin.getTeamManager().getTeamByPlayer(player.getUniqueId());
        NamedTextColor nameColor = NamedTextColor.WHITE;

        net.kyori.adventure.text.TextComponent.Builder builder =
                Component.text().color(NamedTextColor.WHITE);

        if (team != null) {
            NamedTextColor tc = COLOR_MAP.getOrDefault(team.getColor(), NamedTextColor.WHITE);
            nameColor = tc;

            builder.append(Component.text("[", NamedTextColor.GRAY))
                    .append(Component.text(team.getDisplayName(), tc, TextDecoration.BOLD))
                    .append(Component.text("] ", NamedTextColor.GRAY));
        }

        builder.append(Component.text(player.getName(), nameColor, TextDecoration.BOLD));
        builder.append(Component.text(": ", NamedTextColor.GRAY));
        builder.append(Component.text(message, NamedTextColor.WHITE));

        return builder.build();
    }

    /**
     * Builds a team-chat Component.
     */
    public Component buildTeamChatMessage(Player player, KMCTeam team, String message) {
        NamedTextColor tc = COLOR_MAP.getOrDefault(team.getColor(), NamedTextColor.WHITE);

        return Component.text()
                .append(Component.text("[TC] ", NamedTextColor.GRAY))
                .append(Component.text("[" + team.getDisplayName() + "] ", tc, TextDecoration.BOLD))
                .append(Component.text(player.getName(), tc))
                .append(Component.text(": ", NamedTextColor.GRAY))
                .append(Component.text(message, NamedTextColor.WHITE))
                .build();
    }

    // ----------------------------------------------------------------
    // Helper
    // ----------------------------------------------------------------

    /**
     * Converts a legacy &-formatted string to an Adventure Component.
     */
    public static Component fromLegacy(String text) {
        return net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                .legacyAmpersand()
                .deserialize(text);
    }

    /**
     * Converts a ChatColor to its nearest NamedTextColor.
     */
    public static NamedTextColor toNamed(ChatColor cc) {
        return COLOR_MAP.getOrDefault(cc, NamedTextColor.WHITE);
    }
}