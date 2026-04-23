package nl.kmc.kmccore.managers;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
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
 *   <li>The TAB player list (prefix = team colour)</li>
 *   <li>Chat messages (prefix = [TeamName] in team colour)</li>
 *   <li>Above-head nametags (via Scoreboard Team prefix)</li>
 * </ul>
 *
 * <p>Uses the Paper Adventure API for tab/chat and the legacy
 * Scoreboard Team API for above-head nametag colouring (still the
 * most reliable method in 1.21 without ProtocolLib).
 */
public class TabListManager {

    private final KMCCore plugin;

    /**
     * Maps ChatColor → NamedTextColor for Adventure conversion.
     * Only the colours actually used by KMC teams are listed.
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

    // ----------------------------------------------------------------

    public TabListManager(KMCCore plugin) {
        this.plugin = plugin;
    }

    // ----------------------------------------------------------------
    // Tab list header / footer
    // ----------------------------------------------------------------

    /**
     * Updates the TAB header and footer for a single player.
     * Called on join and whenever round/team state changes.
     */
    public void updateTabList(Player player) {
        KMCTeam team = plugin.getTeamManager().getTeamByPlayer(player.getUniqueId());

        String rawHeader = plugin.getConfig().getString("tablist.header", "\n&6&lKMC Tournament\n")
                .replace("{round}",      String.valueOf(plugin.getTournamentManager().getCurrentRound()))
                .replace("{multiplier}", String.valueOf(plugin.getTournamentManager().getMultiplier()));

        String rawFooter = plugin.getConfig().getString("tablist.footer", "\n&7Team: {team_color}{team_name}\n")
                .replace("{team_color}", team != null ? team.getColor().toString() : "")
                .replace("{team_name}",  team != null ? team.getDisplayName() : "Geen");

        player.sendPlayerListHeaderAndFooter(
                fromLegacy(rawHeader),
                fromLegacy(rawFooter));
    }

    /** Refreshes tab list header/footer for every online player. */
    public void refreshAll() {
        for (Player p : Bukkit.getOnlinePlayers()) updateTabList(p);
    }

    // ----------------------------------------------------------------
    // Nametag scoreboard (above head + tab name colour)
    // ----------------------------------------------------------------

    /**
     * Applies the team's colour to a player's nametag (above their head
     * and in the TAB list) using a Bukkit Scoreboard Team.
     *
     * <p>Call this whenever a player joins or changes team.
     */
    public void applyTeamNametag(Player player) {
        Scoreboard board = plugin.getTeamManager().getNametagScoreboard();
        KMCTeam team     = plugin.getTeamManager().getTeamByPlayer(player.getUniqueId());

        // Remove from all KMC scoreboard teams first
        for (Team bt : board.getTeams()) {
            if (bt.getName().startsWith("kmc_")) bt.removeEntry(player.getName());
        }

        if (team == null) return;

        Team bt = board.getTeam("kmc_" + team.getId());
        if (bt == null) return;

        // Set the Adventure prefix using the correct team colour
        NamedTextColor ntc = COLOR_MAP.getOrDefault(team.getColor(), NamedTextColor.WHITE);
        bt.prefix(Component.text("[" + team.getDisplayName() + "] ", ntc, TextDecoration.BOLD));
        bt.color(ntc);
        bt.addEntry(player.getName());

        player.setScoreboard(board);
    }

    // ----------------------------------------------------------------
    // Chat formatting
    // ----------------------------------------------------------------

    /**
     * Builds a chat Component for a player message with their team prefix.
     *
     * <p>Format:  {@code [TeamName] PlayerName: message}
     * If the player has no team, no prefix is prepended.
     *
     * @param player  the sending player
     * @param message the raw message string
     * @return a Component ready to broadcast
     */
    public Component buildChatMessage(Player player, String message) {
        KMCTeam team = plugin.getTeamManager().getTeamByPlayer(player.getUniqueId());
        NamedTextColor nameColor = NamedTextColor.WHITE;

        net.kyori.adventure.text.TextComponent.Builder builder =
                Component.text().color(NamedTextColor.WHITE);

        if (team != null) {
            NamedTextColor tc = COLOR_MAP.getOrDefault(team.getColor(), NamedTextColor.WHITE);
            nameColor = tc;

            // [TeamName] prefix
            builder.append(Component.text("[", NamedTextColor.GRAY))
                   .append(Component.text(team.getDisplayName(), tc, TextDecoration.BOLD))
                   .append(Component.text("] ", NamedTextColor.GRAY));
        }

        // PlayerName in team colour (or white if no team)
        builder.append(Component.text(player.getName(), nameColor, TextDecoration.BOLD));

        // Separator
        builder.append(Component.text(": ", NamedTextColor.GRAY));

        // Message
        builder.append(Component.text(message, NamedTextColor.WHITE));

        return builder.build();
    }



    /**
     * Builds a team-chat Component (only visible to teammates).
     * Prefixed with a "TC" indicator so it's visually distinct.
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

    /** Converts a legacy {@code &}-formatted string to an Adventure Component. */
    public static Component fromLegacy(String text) {
        // Paper's legacy serializer handles & codes
        return net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                .legacyAmpersand().deserialize(text);
    }

    /** Converts a ChatColor to its nearest NamedTextColor. */
    public static NamedTextColor toNamed(ChatColor cc) {
        return COLOR_MAP.getOrDefault(cc, NamedTextColor.WHITE);
    }


}
