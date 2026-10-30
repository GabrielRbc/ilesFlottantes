package fr.Gabilou.ilesFlottantes.interfaces;

import fr.Gabilou.ilesFlottantes.modeles.economy.EconomyManager;
import fr.Gabilou.ilesFlottantes.modeles.game.GameSession;
import fr.Gabilou.ilesFlottantes.modeles.teams.GamePlayer;
import fr.Gabilou.ilesFlottantes.modeles.teams.TeamManager;
import fr.Gabilou.ilesFlottantes.modeles.teams.TeamModel;
import fr.Gabilou.ilesFlottantes.utils.MessageManager;
import io.papermc.paper.scoreboard.numbers.NumberFormat;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.*;
import java.util.*;
import fr.Gabilou.ilesFlottantes.modeles.results.FinalResults;

/**
 * Présente un scoreboard personnel et le TAB, actualisés chaque seconde sur le thread serveur.
 * Les équipes sont répliquées sur chaque scoreboard pour colorer les pseudos au-dessus
 * des joueurs. Les objectifs restent stables pour éviter les clignotements du panneau.
 */
public final class GameDisplay {
    private final TeamManager teams;
    private final EconomyManager economy;
    private final GameSession game;
    private final MessageManager messages;
    private final FinalResults results;
    private final Map<UUID, View> views = new HashMap<>();
    private BukkitTask task;

    /** État d’affichage détenu par le plugin et valeurs à restaurer à sa désactivation. */
    private record View(Scoreboard board, Objective objective, Scoreboard previousBoard,
                        Component previousName, Component previousHeader, Component previousFooter) { }

    public GameDisplay(TeamManager teams, EconomyManager economy, GameSession game, MessageManager messages, FinalResults results) {
        this.teams = teams;
        this.economy = economy;
        this.game = game;
        this.messages = messages;
        this.results = results;
    }

    /** Lance une seule tâche synchrone et initialise les joueurs déjà connectés. */
    public void start(JavaPlugin plugin, Runnable updateGame) {
        refresh();
        task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            updateGame.run();
            refresh();
        }, 20L, 20L);
    }

    /** Met à jour les données affichées sans reconstruire les objectifs. */
    public void refresh() {
        long nanos = game.displayedTime().toNanos();
        long seconds = game.isCountdown() ? (nanos + 999_999_999L) / 1_000_000_000L : nanos / 1_000_000_000L;
        String time = messages.text("ui.time-format", seconds / 3600, seconds / 60 % 60, seconds % 60);
        String state = messages.text("game.states." + game.getState().name());
        if (results.phase() != FinalResults.Phase.IDLE) state = messages.text("results.phases." + results.phase().name());
        if (results.suspended()) state = messages.text("session.paused-phase", state);
        for (Player player : Bukkit.getOnlinePlayers()) {
            View view = views.computeIfAbsent(player.getUniqueId(), id -> createView(player));
            syncTeams(view.board());
            TeamModel team = teams.getPlayerTeam(player);
            GamePlayer stats = teams.getOrCreateGamePlayer(player);
            String teamName = team == null ? messages.text("ui.no-team") : team.getName();
            String balance = team == null ? messages.text("ui.no-balance") : economy.getBalance(team.getName()).toPlainString();
            TextColor color = team == null ? NamedTextColor.GRAY : TextColor.color(team.getColor().asRGB());

            line(view, 6, messages.component("ui.sidebar.state", state));
            if (results.phase() == FinalResults.Phase.VOTING) {
                long remaining = results.remainingSeconds();
                line(view, 5, messages.component("results.sidebar-vote", remaining / 60, remaining % 60));
            } else line(view, 5, messages.component(game.isCountdown() ? "ui.sidebar.remaining" : "ui.sidebar.time", time));
            line(view, 7, messages.component("results.sidebar-points", results.points().getOrDefault(teamName, 0)));
            line(view, 4, messages.component("ui.sidebar.team", teamName).color(color));
            line(view, 3, messages.component("ui.sidebar.kills", stats.getKills()));
            line(view, 2, messages.component("ui.sidebar.deaths", stats.getDeaths()));
            line(view, 1, messages.component("ui.sidebar.balance", balance));

            player.playerListName(messages.component("ui.tab.player", player.getName(), teamName,
                    stats.getKills(), stats.getDeaths(), balance).color(color));
            player.sendPlayerListHeaderAndFooter(messages.component("ui.tab.header", state, time),
                    messages.component("ui.tab.footer", teamName, balance));
        }
    }

    private View createView(Player player) {
        Scoreboard board = Bukkit.getScoreboardManager().getNewScoreboard();
        Objective objective = board.registerNewObjective("if_sidebar", Criteria.DUMMY, messages.component("ui.title"));
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        View view = new View(board, objective, player.getScoreboard(), player.playerListName(),
                player.playerListHeader(), player.playerListFooter());
        player.setScoreboard(board);
        return view;
    }

    private void line(View view, int position, Component text) {
        Score score = view.objective().getScore("if_line_" + position);
        score.setScore(position);
        score.customName(text);
        score.numberFormat(NumberFormat.blank());
    }

    private void syncTeams(Scoreboard board) {
        int index = 0;
        for (TeamModel model : teams.getTeams()) {
            String id = "if_team_" + index++;
            Team team = board.getTeam(id);
            if (team == null) team = board.registerNewTeam(id);
            team.color(NamedTextColor.nearestTo(TextColor.color(model.getColor().asRGB())));
            team.prefix(messages.component("ui.nametag-prefix", model.getName()).color(TextColor.color(model.getColor().asRGB())));
            Set<String> expected = new HashSet<>();
            model.getPlayers().forEach(member -> expected.add(member.getName()));
            for (String old : new HashSet<>(team.getEntries())) {
                if (!expected.contains(old)) team.removeEntry(old);
            }
            for (String name : expected) {
                if (!team.hasEntry(name)) team.addEntry(name);
            }
        }
    }

    /** Libère l’affichage d’un joueur et restaure les valeurs présentes avant le plugin. */
    public void remove(Player player) {
        View view = views.remove(player.getUniqueId());
        if (view == null) return;
        if (player.getScoreboard() == view.board()) player.setScoreboard(view.previousBoard());
        player.playerListName(view.previousName());
        player.sendPlayerListHeaderAndFooter(view.previousHeader() == null ? Component.empty() : view.previousHeader(),
                view.previousFooter() == null ? Component.empty() : view.previousFooter());
    }

    /** Annule la tâche et restitue les affichages lors de la désactivation. */
    public void stop() {
        if (task != null) task.cancel();
        Bukkit.getOnlinePlayers().forEach(this::remove);
        views.clear();
    }
}
