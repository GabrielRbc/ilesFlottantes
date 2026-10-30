package fr.Gabilou.ilesFlottantes.modeles.teams;

import org.bukkit.Color;
import java.util.ArrayList;
import java.util.List;

/**
 * Décrit une équipe par son nom canonique, sa couleur et ses membres.
 * <p>La liste exposée est une copie non modifiable. La caisse partagée est gérée
 * séparément par le gestionnaire d’économie, sous le nom canonique de l’équipe,
 * afin de conserver une source unique pour les soldes.</p>
 */
public class TeamModel {

    private final String name;
    private final Color color;
    private final List<GamePlayer> players;
    private final boolean playable;

    /**
     * Constructeur d'une équipe
     * @param name Nom de l'équipe
     * @param color Couleur de l'équipe
     */
    public TeamModel(String name, Color color) {
        this(name, color, true);
    }

    /** Crée une équipe jouable ou une équipe de protection sans membres. */
    public TeamModel(String name, Color color, boolean playable) {
        this.name = name;
        this.color = color;
        this.players = new ArrayList<>();
        this.playable = playable;
    }

    // Getters et Setters
    public String getName() { return name; }
    public Color getColor() { return color; }
    public List<GamePlayer> getPlayers() { return List.copyOf(players); }
    public boolean isPlayable() { return playable; }

    /**
     * Permet d'ajouter des joueurs dans une équipe
     * @param player Un joueur
     * @return Confirmation de l'ajout
     */
    public boolean addPlayer(GamePlayer player) {
        if (!playable) return false;
        if (!players.contains(player)) {
            return players.add(player);
        }
        return false;
    }

    /**
     * Permet de retirer un joueur d'une équipe
     * @param player Un joueur
     * @return Confirmation de la suppression
     */
    public boolean removePlayer(GamePlayer player) {
        return players.remove(player);
    }

}
