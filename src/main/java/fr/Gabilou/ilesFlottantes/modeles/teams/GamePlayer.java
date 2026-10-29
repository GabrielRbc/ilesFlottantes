package fr.Gabilou.ilesFlottantes.modeles.teams;

import java.util.UUID;

/**
 * Représente l’identité et les statistiques de jeu d’un membre d’équipe.
 * <p>L’UUID identifie le joueur ; le nom est celui connu lors de sa création.
 * Ce modèle ne contient aucun solde : l’argent appartient exclusivement aux équipes.</p>
 */
public class GamePlayer {

    private final UUID id;
    private final String name;
    private int kills;
    private int deaths;

    public GamePlayer(UUID id, String name) {
        this.id = id;
        this.name = name;
        this.kills = 0;
        this.deaths = 0;
    }

    // Getters et Setters
    public UUID getId() { return id; }
    public String getName() { return name; }

    public int getKills() { return kills; }
    public void setKills(int kills) { this.kills = kills; }
    public void addKill() { this.kills++; }

    public int getDeaths() { return deaths; }
    public void setDeaths(int deaths) { this.deaths = deaths; }
    public void addDeath() { this.deaths++; }
}