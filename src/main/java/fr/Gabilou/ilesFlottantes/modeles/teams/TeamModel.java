package fr.Gabilou.ilesFlottantes.modeles.teams;

import org.bukkit.Color;
import java.util.ArrayList;
import java.util.List;

public class TeamModel {

    private final String name;
    private final Color color;
    private double monnaie;
    private final List<GamePlayer> players;

    /**
     * Constructeur d'une équipe
     * @param name Nom de l'équipe
     * @param color Couleur de l'équipe
     */
    public TeamModel(String name, Color color) {
        this.name = name;
        this.color = color;
        this.monnaie = 0.0;
        this.players = new ArrayList<>();
    }

    // Getters et Setters
    public String getName() { return name; }
    public Color getColor() { return color; }
    public double getMonnaie() { return monnaie; }
    public List<GamePlayer> getPlayers() { return players; }

    /**
     * Permet d'ajouter des joueurs dans une équipe
     * @param player Un joueur
     * @return Confirmation de l'ajout
     */
    public boolean addPlayer(GamePlayer player) {
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

    /**
     * Permet de définir la monnaie d'une équipe
     * @param monnaie à définir
     */
    public void setMonnaie(double monnaie) {
        this.monnaie = Math.max(0.0, monnaie); // Empêche un solde négatif
    }

    /**
     * Permet d'ajouter de la monnaie à une équipe
     * @param monnaie à ajouter
     * @return Confirmation du virement
     */
    public boolean addMonnaie(double monnaie) {
        if (monnaie > 0) {
            this.monnaie += monnaie;
            return true;
        }
        return false;
    }

    /**
     * Permet de retirer de la monnaie à une équipe
     * @param monnaie à retirer
     * @return Confirmation du virement
     */
    public boolean removeMonnaie(double monnaie) {
        if (monnaie > 0 && this.monnaie >= monnaie) {
            this.monnaie -= monnaie;
            return true;
        }
        return false;
    }
}