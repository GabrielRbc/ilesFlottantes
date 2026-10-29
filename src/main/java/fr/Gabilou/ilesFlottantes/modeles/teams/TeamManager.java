package fr.Gabilou.ilesFlottantes.modeles.teams;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Gère les équipes disponibles, leurs membres et la distribution des casques colorés.
 * <p>Les affectations sont conservées en mémoire et synchronisées vers le scoreboard
 * principal. Elles ne sont pas restaurées dans ce modèle après un redémarrage.</p>
 * <p>Les opérations utilisant Bukkit doivent être appelées sur le thread du serveur.
 * La gestion financière est déléguée au gestionnaire d’économie.</p>
 */
public class TeamManager {

    private final Scoreboard mainScoreboard;
    private final List<TeamModel> teams = new ArrayList<>();
    private final Map<UUID, GamePlayer> playerMap = new HashMap<>();

    public TeamManager() {
        this.mainScoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
        initDefaultTeams();
    }

    private void initDefaultTeams() {
        Map<String, Color> defaultColors = new java.util.LinkedHashMap<>();
        defaultColors.put("Blanche", Color.WHITE);
        defaultColors.put("Gris clair", Color.SILVER);
        defaultColors.put("Grise", Color.GRAY);
        defaultColors.put("Noire", Color.BLACK);
        defaultColors.put("Marron", Color.MAROON);
        defaultColors.put("Rouge", Color.RED);
        defaultColors.put("Orange", Color.ORANGE);
        defaultColors.put("Jaune", Color.YELLOW);
        defaultColors.put("Bleu clair", Color.AQUA);
        defaultColors.put("Cyan", Color.NAVY);
        defaultColors.put("Bleu", Color.BLUE);
        defaultColors.put("Vert", Color.GREEN);
        defaultColors.put("Vert clair", Color.LIME);
        defaultColors.put("Rose", Color.fromRGB(0xF38BAA));
        defaultColors.put("Magenta", Color.FUCHSIA);
        defaultColors.put("Violet", Color.PURPLE);

        for (Map.Entry<String, Color> entry : defaultColors.entrySet()) {
            String teamName = entry.getKey();
            Color color = entry.getValue();

            TeamModel teamModel = new TeamModel(teamName, color);
            teams.add(teamModel);

            Team team = mainScoreboard.getTeam(teamName);
            if (team == null) {
                mainScoreboard.registerNewTeam(teamName);
            }
        }
    }

    public GamePlayer getOrCreateGamePlayer(Player player) {
        return playerMap.computeIfAbsent(player.getUniqueId(),
                uuid -> new GamePlayer(uuid, player.getName()));
    }

    public boolean assignPlayerToTeam(Player player, String teamName) {
        TeamModel targetTeam = getTeamByName(teamName);
        if (targetTeam == null) return false;

        GamePlayer gamePlayer = getOrCreateGamePlayer(player);

        // Retire le joueur de son ancienne équipe si nécessaire
        for (TeamModel team : teams) {
            team.removePlayer(gamePlayer);
        }

        targetTeam.addPlayer(gamePlayer);

        // Synchronisation avec le Scoreboard Minecraft
        Team sbTeam = mainScoreboard.getTeam(targetTeam.getName());
        if (sbTeam != null) {
            sbTeam.addEntry(player.getName());
        }
        return true;
    }

    public boolean removePlayerFromTeam(Player player) {
        GamePlayer gamePlayer = getOrCreateGamePlayer(player);
        boolean removed = false;

        for (TeamModel team : teams) {
            if (team.removePlayer(gamePlayer)) {
                removed = true;
            }
        }

        for (Team sbTeam : mainScoreboard.getTeams()) {
            sbTeam.removeEntry(player.getName());
        }

        return removed;
    }

    public TeamModel getTeamByName(String name) {
        for (TeamModel team : teams) {
            if (team.getName().equalsIgnoreCase(name)) {
                return team;
            }
        }
        return null;
    }

    public void giveStartEquipment() {
        for (TeamModel team : teams) {
            for (GamePlayer gamePlayer : team.getPlayers()) {
                Player player = Bukkit.getPlayer(gamePlayer.getId());
                if (player != null && player.isOnline()) {
                    giveTeamHelmet(player, team.getColor());
                }
            }
        }
    }

    private void giveTeamHelmet(Player player, Color color) {
        ItemStack helmet = new ItemStack(Material.LEATHER_HELMET);
        LeatherArmorMeta meta = (LeatherArmorMeta) helmet.getItemMeta();

        if (meta != null) {
            meta.setColor(color);
            meta.setUnbreakable(true);
            helmet.setItemMeta(meta);
        }

        player.getInventory().setHelmet(helmet);
    }

    public List<TeamModel> getTeams() {
        return List.copyOf(teams);
    }

    public TeamModel getPlayerTeam(Player player) {
        return teams.stream().filter(team -> team.getPlayers().stream()
                .anyMatch(member -> member.getId().equals(player.getUniqueId())))
                .findFirst().orElse(null);
    }

    /** Retire tous les membres et efface leurs statistiques, même hors ligne. */
    public void reset() {
        for (TeamModel model : teams) {
            model.getPlayers().forEach(model::removePlayer);
            Team scoreboardTeam = mainScoreboard.getTeam(model.getName());
            if (scoreboardTeam != null) {
                new java.util.HashSet<>(scoreboardTeam.getEntries()).forEach(scoreboardTeam::removeEntry);
            }
        }
        playerMap.clear();
    }
}
