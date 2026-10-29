package fr.Gabilou.ilesFlottantes.commandes;

import fr.Gabilou.ilesFlottantes.modeles.teams.TeamManager;
import fr.Gabilou.ilesFlottantes.utils.MessageManager;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class GameCommand implements CommandExecutor {

    private final TeamManager teamManager;
    private final MessageManager messageManager;

    public GameCommand(TeamManager teamManager, MessageManager messageManager) {
        this.teamManager = teamManager;
        this.messageManager = messageManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (command.getName().equalsIgnoreCase("start")) {
            teamManager.giveStartEquipment();
            Bukkit.broadcastMessage(messageManager.get("game.start-broadcast"));
            return true;
        }

        return false;
    }
}