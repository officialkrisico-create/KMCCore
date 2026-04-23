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
 * Handles team-coloured player names in:
 *   - Chat messages (prefix = [TeamName])
 *   - TAB player list (player name in team colour)
 *   - Above-head nametags (via a SHARED scoreboard)
 *
 * <p>IMPORTANT FIX FROM PREVIOUS VERSION:
 * All players must be on the SAME Bukkit scoreboard for team-prefixes
 * to show to everyone else. Previously the sidebar ScoreboardManager
 * was overwriting each player's scoreboard with a personal one,
 * which broke the team nametag team registration.
 *
 * <p>Now everything uses the shared {@code mainScoreboard} and the
 * sidebar is registered as an objective on THIS scoreboard instead.
 */
public class TabListManager {

    private final KMCCore plugin;

    /** Shared scoreboard used by ALL online players for team-nametags. */
    private final Scoreboard mainScoreboard;

    /** Map Bukkit ChatColor → Adventure NamedTextColor. */
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
        COLOR_MAP.put(ChatColor.DARK_RED,     NamedTextColor.DARK_RED);
        COLOR_MAP.put(ChatColor.DARK_BLUE,    NamedTextColor.DARK_BLUE);
        COLOR_MAP.put(ChatColor.GRAY,         NamedTextColor.GRAY);
        COLOR_MAP.put(ChatColor.DARK_GRAY,    NamedTextColor.DARK_GRAY);
    }

    public TabListManager(KMCCore plugin) {
        this.plugin = plugin;
        this.mainScoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
        // Create team entries for each KMC team
        setupTeams();
    }

    // ----------------------------------------------------------------

    /** Creates a Bukkit Team per KMC team on the main scoreboard. */
    private void setupTeams() {
        for (KMCTeam t : plugin.getTeamManager().getAllTeams()) {
            String teamName = "kmc_" + t.getId();
            Team bt = mainScoreboard.getTeam(teamName);
            if (bt == null) bt = mainScoreboard.registerNewTeam(teamName);

            NamedTextColor ntc = COLOR_MAP.getOrDefault(t.getColor(), NamedTextColor.WHITE);
            bt.prefix(Component.text("[" + t.getDisplayName() + "] ", ntc, TextDecoration.BOLD));
            bt.color(ntc);
            bt.setAllowFriendlyFire(false);
            bt.setCanSeeFriendlyInvisibles(true);
        }
    }

    // ----------------------------------------------------------------
    // Public API
    // ----------------------------------------------------------------

    /**
     * Adds a player to their team's entry on the shared scoreboard.
     * This is what actually shows the coloured [TeamName] prefix to others.
     */
    public void applyTeamNametag(Player player) {
        // Ensure teams are registered (new teams added at runtime?)
        setupTeams();

        // Remove player from all KMC teams first
        for (Team t : mainScoreboard.getTeams()) {
            if (t.getName().startsWith("kmc_") && t.hasEntry(player.getName())) {
                t.removeEntry(player.getName());
            }
        }

        KMCTeam kmcTeam = plugin.getTeamManager().getTeamByPlayer(player.getUniqueId());
        if (kmcTeam != null) {
            Team bt = mainScoreboard.getTeam("kmc_" + kmcTeam.getId());
            if (bt != null) bt.addEntry(player.getName());
        }

        // All players use the main scoreboard — do NOT switch them to a personal one.
        // The sidebar will register its objective on this same main scoreboard.
        player.setScoreboard(mainScoreboard);
    }

    /**
     * Refreshes nametags for every online player.
     * Call after team assignments change.
     */
    public void refreshAllNametags() {
        for (Player p : Bukkit.getOnlinePlayers()) applyTeamNametag(p);
    }

    // ----------------------------------------------------------------
    // Tab list header/footer
    // ----------------------------------------------------------------

    public void updateTabList(Player player) {
        KMCTeam team = plugin.getTeamManager().getTeamByPlayer(player.getUniqueId());

        String rawHeader = plugin.getConfig().getString("tablist.header", "\n&6&lKMC Tournament\n")
                .replace("{round}",      String.valueOf(plugin.getTournamentManager().getCurrentRound()))
                .replace("{multiplier}", String.valueOf(plugin.getTournamentManager().getMultiplier()));

        String rawFooter = plugin.getConfig().getString("tablist.footer", "\n&7Team: {team_color}{team_name}\n")
                .replace("{team_color}", team != null ? team.getColor().toString() : "&8")
                .replace("{team_name}",  team != null ? team.getDisplayName() : "Geen");

        player.sendPlayerListHeaderAndFooter(fromLegacy(rawHeader), fromLegacy(rawFooter));
    }

    public void refreshAll() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            applyTeamNametag(p);
            updateTabList(p);
        }
    }

    // ----------------------------------------------------------------
    // Chat message formatting
    // ----------------------------------------------------------------

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

        builder.append(Component.text(player.getName(), nameColor, TextDecoration.BOLD))
               .append(Component.text(": ", NamedTextColor.GRAY))
               .append(Component.text(message, NamedTextColor.WHITE));
        return builder.build();
    }

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
    // Helpers
    // ----------------------------------------------------------------

    public Scoreboard getMainScoreboard() { return mainScoreboard; }

    public static Component fromLegacy(String text) {
        return net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                .legacyAmpersand().deserialize(text);
    }

    public static NamedTextColor toNamed(ChatColor cc) {
        return COLOR_MAP.getOrDefault(cc, NamedTextColor.WHITE);
    }
}
