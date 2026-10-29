package fr.Gabilou.ilesFlottantes.commandes;

import fr.Gabilou.ilesFlottantes.modeles.teams.TeamManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class TeamCommand implements CommandExecutor {

    private final TeamManager teamManager;

    public TeamCommand(TeamManager teamManager) {
        this.teamManager = teamManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        // Commande /jointeam <équipe>
        if (command.getName().equalsIgnoreCase("jointeam")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("Seul un joueur en jeu peut exécuter cette commande.");
                return true;
            }

            if (args.length < 1) {
                player.sendMessage("§cUsage : /jointeam <nom_equipe>");
                return true;
            }

            String teamName = args[0];
            boolean success = teamManager.assignPlayerToTeam(player, teamName);

            if (success) {
                player.sendMessage("§aVous avez rejoint l'équipe " + teamName + " !");
            } else {
                player.sendMessage("§cL'équipe " + teamName + " n'existe pas.");
            }
            return true;
        }

        return false;
    }
}