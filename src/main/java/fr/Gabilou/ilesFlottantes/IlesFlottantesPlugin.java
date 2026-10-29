package fr.Gabilou.ilesFlottantes;

import fr.Gabilou.ilesFlottantes.commandes.GameCommand;
import fr.Gabilou.ilesFlottantes.commandes.TeamCommand;
import fr.Gabilou.ilesFlottantes.modeles.teams.TeamManager;
import org.bukkit.plugin.java.JavaPlugin;

public class IlesFlottantesPlugin extends JavaPlugin {

    private TeamManager teamManager;

    @Override
    public void onEnable() {
        this.teamManager = new TeamManager();

        // Enregistrement de la classe des commandes
        GameCommand gameCommand = new GameCommand(teamManager);
        getCommand("start").setExecutor(new GameCommand(teamManager));
        getCommand("jointeam").setExecutor(new TeamCommand(teamManager));

        getLogger().info("ilesFlottantes active avec succès !");
    }

    public TeamManager getTeamManager() {
        return teamManager;
    }
}