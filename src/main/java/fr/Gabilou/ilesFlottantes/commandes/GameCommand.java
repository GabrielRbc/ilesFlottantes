package fr.Gabilou.ilesFlottantes.commandes;

import fr.Gabilou.ilesFlottantes.modeles.teams.TeamManager;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class GameCommand implements CommandExecutor {

    private final TeamManager teamManager;

    public GameCommand(TeamManager teamManager) {
        this.teamManager = teamManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        // Commande /start
        if (command.getName().equalsIgnoreCase("start")) {
            teamManager.giveStartEquipment();
            Bukkit.broadcastMessage("§a[Événement] La partie commence ! Les équipements ont été distribués.");
            return true;
        }

        return false;
    }
}