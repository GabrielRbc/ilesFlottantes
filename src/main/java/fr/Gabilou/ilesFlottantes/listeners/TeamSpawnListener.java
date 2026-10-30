package fr.Gabilou.ilesFlottantes.listeners;

import fr.Gabilou.ilesFlottantes.modeles.game.GameSession;
import fr.Gabilou.ilesFlottantes.modeles.teams.TeamManager;
import fr.Gabilou.ilesFlottantes.modeles.territories.*;
import fr.Gabilou.ilesFlottantes.utils.MessageManager;
import org.bukkit.Bukkit;
import org.bukkit.event.*;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.java.JavaPlugin;

/** Rejoint le spawn au premier retour après le départ et utilise le spawn d’équipe à la réapparition. */
public final class TeamSpawnListener implements Listener {
    private final JavaPlugin plugin;
    private final GameSession game;
    private final TeamManager teams;
    private final TerritoryManager territories;
    private final SpawnService spawns;
    private final MessageManager messages;

    public TeamSpawnListener(JavaPlugin plugin, GameSession game, TeamManager teams, TerritoryManager territories, SpawnService spawns, MessageManager messages) {
        this.plugin = plugin; this.game = game; this.teams = teams; this.territories = territories; this.spawns = spawns; this.messages = messages;
    }

    private boolean active() { return game.getState() == GameSession.State.RUNNING || game.getState() == GameSession.State.PAUSED; }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (event.getPlayer().isOnline() && active() && !spawns.join(event.getPlayer())) {
                event.getPlayer().sendMessage(messages.get("territories.join-spawn-failed"));
            }
        });
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onRespawn(PlayerRespawnEvent event) {
        if (!active()) return;
        var team = teams.getPlayerTeam(event.getPlayer());
        if (team != null && spawns.valid(team.getName(), territories.spawn(team.getName()))) {
            event.setRespawnLocation(spawns.location(territories.spawn(team.getName())));
        }
    }
}
