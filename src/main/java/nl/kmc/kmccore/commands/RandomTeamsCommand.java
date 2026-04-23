package nl.kmc.kmccore.commands;

import nl.kmc.kmccore.KMCCore;
import nl.kmc.kmccore.managers.TeamManager;
import nl.kmc.kmccore.models.KMCTeam;
import nl.kmc.kmccore.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.*;

/**
 * /kmcrandomteams [all|new] [confirm]
 *
 * Distributes online players evenly across teams.
 *
 * <p><b>FIX:</b> previous version checked {@code kmc.team.exempt}
 * permission which OPs get by default (they have all permissions).
 * That check is removed — every online player is now eligible.
 *
 * <p>Modes:
 * <ul>
 *   <li>{@code new} (default) — only places players not yet in a team</li>
 *   <li>{@code all confirm}   — wipes all teams and redistributes everyone</li>
 * </ul>
 */
public class RandomTeamsCommand implements CommandExecutor {

    private final KMCCore plugin;
    private final Random  random = new Random();

    public RandomTeamsCommand(KMCCore plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("kmc.team.admin")) {
            sender.sendMessage(MessageUtil.get("no-permission"));
            return true;
        }

        String mode = args.length >= 1 ? args[0].toLowerCase() : "new";
        boolean confirmed = args.length >= 2 && args[1].equalsIgnoreCase("confirm");

        if (mode.equals("all") && !confirmed) {
            sender.sendMessage(MessageUtil.color("&c⚠ Dit wist ALLE team toewijzingen."));
            sender.sendMessage(MessageUtil.color("&7Typ &e/kmcrandomteams all confirm &7om door te gaan."));
            return true;
        }

        // "all" mode: wipe teams first
        if (mode.equals("all")) {
            for (KMCTeam t : plugin.getTeamManager().getAllTeams()) {
                List<UUID> members = new ArrayList<>(t.getMembers());
                for (UUID uuid : members) plugin.getTeamManager().removePlayerFromTeam(uuid);
            }
            sender.sendMessage(MessageUtil.color("&7Alle teams gewist."));
        }

        // Collect eligible online players (NO permission check anymore)
        List<Player> toAssign = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (plugin.getTeamManager().getTeamByPlayer(p.getUniqueId()) == null) {
                plugin.getPlayerDataManager().getOrCreate(p.getUniqueId(), p.getName());
                toAssign.add(p);
            }
        }

        if (toAssign.isEmpty()) {
            sender.sendMessage(MessageUtil.color("&eGeen spelers om toe te wijzen (iedereen zit al in een team)."));
            return true;
        }

        Collections.shuffle(toAssign, random);

        int maxPerTeam    = plugin.getTeamManager().getMaxPlayersPerTeam();
        int assignedCount = 0;
        int skippedFull   = 0;

        for (Player p : toAssign) {
            KMCTeam target = findSmallestTeamWithRoom(maxPerTeam);
            if (target == null) { skippedFull++; continue; }

            TeamManager.AddResult r = plugin.getTeamManager().addPlayerToTeam(p.getUniqueId(), target.getId());
            if (r == TeamManager.AddResult.OK) {
                assignedCount++;
                p.sendMessage(MessageUtil.get("team.join-message").replace("{team}", target.getDisplayName()));
            }
        }

        sender.sendMessage(MessageUtil.color("&a✔ " + assignedCount + " spelers verdeeld over teams."));
        if (skippedFull > 0)
            sender.sendMessage(MessageUtil.color("&c" + skippedFull + " spelers konden niet geplaatst worden (alle teams vol)."));

        plugin.getTabListManager().refreshAllNametags();
        plugin.getTabListManager().refreshAll();
        plugin.getScoreboardManager().refreshAll();
        return true;
    }

    private KMCTeam findSmallestTeamWithRoom(int maxPerTeam) {
        KMCTeam smallest = null;
        int smallestSize = Integer.MAX_VALUE;
        for (KMCTeam t : plugin.getTeamManager().getAllTeams()) {
            int size = t.getMemberCount();
            if (size < maxPerTeam && size < smallestSize) {
                smallest = t;
                smallestSize = size;
            }
        }
        return smallest;
    }
}
