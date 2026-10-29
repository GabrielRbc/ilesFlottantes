package fr.Gabilou.ilesFlottantes;

import fr.Gabilou.ilesFlottantes.commandes.GameCommand;
import fr.Gabilou.ilesFlottantes.commandes.TeamCommand;
import fr.Gabilou.ilesFlottantes.modeles.teams.TeamManager;
import fr.Gabilou.ilesFlottantes.utils.MessageManager;
import org.bukkit.plugin.java.JavaPlugin;

public class IlesFlottantesPlugin extends JavaPlugin {

    private TeamManager teamManager;
    private MessageManager messageManager;

    @Override
    public void onEnable() {
        this.messageManager = new MessageManager(this);
        this.teamManager = new TeamManager();

        // Enregistrement des commandes
        getCommand("start").setExecutor(new GameCommand(teamManager, messageManager));
        getCommand("team").setExecutor(new TeamCommand(teamManager, messageManager));

        getLogger().info("ilesFlottantes activé avec succès !");
    }

    public TeamManager getTeamManager() {
        return teamManager;
    }

    public MessageManager getMessageManager() {
        return messageManager;
    }
}