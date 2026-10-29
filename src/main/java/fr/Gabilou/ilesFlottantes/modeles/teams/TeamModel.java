package fr.Gabilou.ilesFlottantes.modeles.teams;

import org.bukkit.Color;
import java.util.ArrayList;
import java.util.List;

public class TeamModel {

    private final String name;
    private final Color color;
    private final List<GamePlayer> players;

    public TeamModel(String name, Color color) {
        this.name = name;
        this.color = color;
        this.players = new ArrayList<>();
    }

    public String getName() { return name; }
    public Color getColor() { return color; }
    public List<GamePlayer> getPlayers() { return players; }

    public void addPlayer(GamePlayer player) {
        if (!players.contains(player)) {
            players.add(player);
        }
    }

    public void removePlayer(GamePlayer player) {
        players.remove(player);
    }
}