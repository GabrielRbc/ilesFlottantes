package fr.Gabilou.ilesFlottantes.listeners;

import fr.Gabilou.ilesFlottantes.modeles.results.FinalResults;
import fr.Gabilou.ilesFlottantes.modeles.game.GameSession;
import fr.Gabilou.ilesFlottantes.modeles.teams.TeamManager;
import fr.Gabilou.ilesFlottantes.utils.MessageManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.entity.EntityDeathEvent;

/** Attribue le premier boss à l’équipe du joueur crédité par Minecraft, uniquement pendant le jeu actif. */
public final class BossPointsListener implements Listener {
    private final GameSession game;
    private final TeamManager teams;
    private final FinalResults results;
    private final MessageManager messages;

    public BossPointsListener(GameSession game, TeamManager teams, FinalResults results, MessageManager messages) {
        this.game = game; this.teams = teams; this.results = results; this.messages = messages;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDeath(EntityDeathEvent event) {
        game.finishIfExpired();
        if (game.getState() != GameSession.State.RUNNING) return;
        FinalResults.Category category = switch (event.getEntityType()) {
            case ENDER_DRAGON -> FinalResults.Category.DRAGON;
            case WARDEN -> FinalResults.Category.WARDEN;
            case WITHER -> FinalResults.Category.WITHER;
            default -> null;
        };
        Player killer = event.getEntity().getKiller();
        if (category == null || killer == null) return;
        var team = teams.getPlayerTeam(killer);
        if (team != null && team.isPlayable() && results.claimBoss(category, team.getName())) {
            Bukkit.broadcastMessage(messages.get("results.boss-earned", team.getName(), messages.text("results.categories." + category.name())));
        }
    }
}
