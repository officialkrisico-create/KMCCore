package nl.kmc.kmccore.listeners;

import nl.kmc.kmccore.KMCCore;
import nl.kmc.kmccore.models.PlayerData;
import org.bukkit.GameMode;
import org.bukkit.event.*;
import org.bukkit.event.player.*;

/**
 * On join:
 *   - Load PlayerData
 *   - Refresh team nametag on main scoreboard (colour in chat + above head + TAB)
 *   - Teleport to lobby in adventure mode
 *   - Update tab list header/footer
 *
 * Also refreshes nametags for ALL players so existing ones see the new arrival
 * with the correct prefix.
 */
public class PlayerJoinQuitListener implements Listener {

    private final KMCCore plugin;

    public PlayerJoinQuitListener(KMCCore plugin) { this.plugin = plugin; }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onJoin(PlayerJoinEvent event) {
        var player = event.getPlayer();

        PlayerData pd = plugin.getPlayerDataManager()
                .getOrCreate(player.getUniqueId(), player.getName());

        // Critical: refresh ALL nametags (not just the new player)
        // otherwise existing players see the newcomer without a prefix.
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            plugin.getTabListManager().refreshAll();
        }, 5L);

        plugin.getScoreboardManager().onPlayerJoin(player);
        plugin.getAutomationManager().addPlayerToBossBar(player);
        plugin.getTabListManager().updateTabList(player);

        // Teleport to lobby in adventure mode (if lobby set and no active game)
        if (plugin.getArenaManager().getLobby() != null
                && !plugin.getGameManager().isGameActive()) {
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                player.teleport(plugin.getArenaManager().getLobby());
                player.setGameMode(GameMode.ADVENTURE);
            }, 10L);
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onQuit(PlayerQuitEvent event) {
        var player = event.getPlayer();
        plugin.getPlayerDataManager().unload(player.getUniqueId());
        plugin.getScoreboardManager().onPlayerQuit(player);
    }
}
