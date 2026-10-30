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
 * principal. Elles sont restaurées depuis l’instantané de session au démarrage.</p>
 * <p>Les opérations utilisant Bukkit doivent être appelées sur le thread du serveur.
 * La gestion financière est déléguée au gestionnaire d’économie.</p>
 */
public class TeamManager {

    private final Scoreboard mainScoreboard;
    private final List<TeamModel> teams = new ArrayList<>();
    private final Map<UUID, GamePlayer> playerMap = new HashMap<>();
    private Runnable changed = () -> { };

    public void onChange(Runnable changed) {
        this.changed = changed; playerMap.values().forEach(player -> player.onChange(changed));
    }

    /** Capture tous les joueurs connus, y compris sans équipe ou déconnectés. */
    public List<fr.Gabilou.ilesFlottantes.persistence.SessionSnapshot.SavedPlayer> snapshot() {
        return playerMap.values().stream().map(player -> {
            String team = teams.stream().filter(t -> t.getPlayers().contains(player)).map(TeamModel::getName).findFirst().orElse(null);
            return new fr.Gabilou.ilesFlottantes.persistence.SessionSnapshot.SavedPlayer(player.getId(), player.getName(), team, player.getKills(), player.getDeaths());
        }).toList();
    }

    /** Restaure les identités par UUID et reconstruit uniquement les équipes gérées par le plugin. */
    public void restore(List<fr.Gabilou.ilesFlottantes.persistence.SessionSnapshot.SavedPlayer> saved) {
        for (var player : saved) {
            if (player.team() != null && (getTeamByName(player.team()) == null || !getTeamByName(player.team()).isPlayable())) throw new IllegalArgumentException();
        }
        reset();
        for (var savedPlayer : saved) {
            GamePlayer player = new GamePlayer(savedPlayer.id(), savedPlayer.name());
            player.setKills(savedPlayer.kills()); player.setDeaths(savedPlayer.deaths());
            player.onChange(changed); playerMap.put(player.getId(), player);
            if (savedPlayer.team() != null) {
                getTeamByName(savedPlayer.team()).addPlayer(player);
                mainScoreboard.getTeam(savedPlayer.team()).addEntry(player.getName());
            }
        }
    }

    public TeamManager(String commonTeamName) {
        this.mainScoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
        initDefaultTeams();
        if (getTeamByName(commonTeamName) != null) throw new IllegalArgumentException();
        teams.add(new TeamModel(commonTeamName, Color.WHITE, false));
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
        GamePlayer model = playerMap.computeIfAbsent(player.getUniqueId(), uuid -> {
            GamePlayer created = new GamePlayer(uuid, player.getName()); created.onChange(changed); return created;
        });
        if (!model.getName().equals(player.getName())) {
            for (TeamModel team : teams) {
                Team scoreboardTeam = mainScoreboard.getTeam(team.getName());
                if (scoreboardTeam != null) {
                    scoreboardTeam.removeEntry(model.getName());
                    if (team.getPlayers().contains(model)) scoreboardTeam.addEntry(player.getName());
                }
            }
            model.setName(player.getName());
        }
        return model;
    }

    public boolean assignPlayerToTeam(Player player, String teamName) {
        TeamModel targetTeam = getTeamByName(teamName);
        if (targetTeam == null || !targetTeam.isPlayable()) return false;

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
        changed.run();
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

        if (removed) changed.run();
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
        changed.run();
    }
}
