package fr.Gabilou.ilesFlottantes.interfaces;

import fr.Gabilou.ilesFlottantes.modeles.results.FinalResults;
import fr.Gabilou.ilesFlottantes.modeles.game.GameSession;
import fr.Gabilou.ilesFlottantes.modeles.economy.EconomyManager;
import fr.Gabilou.ilesFlottantes.modeles.teams.*;
import fr.Gabilou.ilesFlottantes.utils.MessageManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.title.Title;
import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import java.time.Duration;
import java.util.*;

/**
 * Anime la cérémonie via la tâche synchrone existante, sans tâches différées orphelines.
 * Les catégories défilent, puis un scrutin de trente minutes s’ouvre avant le décompte
 * final. Un reset interrompt immédiatement les titres, le vote et la visite en spectateur.
 */
public final class ResultsController {
    private final FinalResults results;
    private final GameSession game;
    private final TeamManager teams;
    private final EconomyManager economy;
    private final MessageManager messages;
    private final SpectatorTour tour;
    private final Set<UUID> barViewers = new HashSet<>();
    private BossBar voteBar;
    private long nextFrame;
    private int frame;
    private int countdown;
    private boolean suspended;
    private Runnable changed = () -> { };
    public void onChange(Runnable changed) { this.changed = changed; }
    public boolean suspended() { return suspended; }

    /** Reconstruit l’animation à partir des catégories déjà attribuées, sans redonner de point. */
    public void restore() {
        frame = results.revealed() * 2; countdown = 3; nextFrame = 0;
        suspended = results.phase() != FinalResults.Phase.IDLE && results.phase() != FinalResults.Phase.FINISHED;
        results.pauseVoting();
    }

    public boolean pause() {
        if (suspended || results.phase() == FinalResults.Phase.IDLE || results.phase() == FinalResults.Phase.FINISHED) return false;
        suspended = true; results.pauseVoting(); changed.run(); return true;
    }

    public boolean resume() {
        if (!suspended) return false;
        suspended = false; results.resumeVoting(); nextFrame = System.nanoTime();
        if (results.phase() == FinalResults.Phase.VOTING || results.phase() == FinalResults.Phase.REVEAL) tour.start();
        changed.run(); return true;
    }

    public ResultsController(FinalResults results, GameSession game, TeamManager teams, EconomyManager economy, MessageManager messages, SpectatorTour tour) {
        this.results = results; this.game = game; this.teams = teams; this.economy = economy; this.messages = messages; this.tour = tour;
    }

    /** Une seule prise d’instantané, déclenchée aussi bien par /game stop que par expiration. */
    public void tick() {
        if (suspended) return;
        if (game.getState() == GameSession.State.STOPPED && results.phase() == FinalResults.Phase.IDLE) begin();
        long now = System.nanoTime();
        if (results.phase() == FinalResults.Phase.AWARDS && now >= nextFrame) {
            int index = frame / 2;
            if (index < results.awards().size()) {
                var award = results.awards().get(index);
                if (frame % 2 == 0) title(messages.component("results.categories." + award.category().name()), messages.component("results.suspense"), Sound.BLOCK_NOTE_BLOCK_CHIME);
                else showAward(results.revealNext());
                frame++; nextFrame = now + Duration.ofSeconds(4).toNanos();
            } else {
                results.openVoting(Duration.ofMinutes(30)); tour.start();
                Bukkit.broadcastMessage(messages.get(results.eligibleVoters() == 0 ? "results.vote-unavailable" : "results.vote-open"));
                title(messages.component("results.vote-title"), messages.component("results.vote-instructions"), Sound.ENTITY_PLAYER_LEVELUP);
            }
        }
        if (results.phase() == FinalResults.Phase.VOTING) {
            if (results.votingComplete()) closeVoting();
            else updateVoteBar();
        }
        if (results.phase() == FinalResults.Phase.REVEAL && now >= nextFrame) {
            if (countdown > 0) {
                title(messages.component("results.countdown", countdown--), messages.component("results.winner-suspense"), Sound.BLOCK_NOTE_BLOCK_PLING);
                nextFrame = now + Duration.ofSeconds(1).toNanos();
            } else {
                results.finish();
                var winners = results.winners();
                Component names = names(winners);
                int score = winners.isEmpty() ? 0 : results.points().get(winners.getFirst());
                title(messages.component(winners.isEmpty() ? "results.empty-title" : winners.size() > 1 ? "results.tie-title" : "results.winner-title"),
                        names.append(messages.component("results.winner-score", score)), Sound.UI_TOAST_CHALLENGE_COMPLETE);
                Bukkit.getOnlinePlayers().forEach(this::showRanking);
            }
        }
    }

    private void begin() {
        Map<String, FinalResults.TeamStats> stats = new HashMap<>();
        Map<UUID, String> players = new HashMap<>();
        for (TeamModel team : teams.getTeams()) {
            if (!team.isPlayable() || team.getPlayers().isEmpty()) continue;
            stats.put(team.getName(), new FinalResults.TeamStats(economy.getBalance(team.getName()), team.getPlayers().stream().mapToLong(GamePlayer::getDeaths).sum()));
            team.getPlayers().forEach(player -> players.put(player.getId(), team.getName()));
        }
        results.begin(stats, players); frame = 0; nextFrame = System.nanoTime();
    }

    private void showAward(FinalResults.Award award) {
        Component detail = award.teams().isEmpty() ? messages.component("results.no-award") : names(award.teams())
                .append(messages.component("results.values." + award.category().name(), award.value()));
        title(messages.component("results.categories." + award.category().name()), detail, Sound.ENTITY_PLAYER_LEVELUP);
        Bukkit.getOnlinePlayers().forEach(player -> {
            player.sendMessage(messages.component("results.categories." + award.category().name()));
            if (award.teams().isEmpty()) player.sendMessage(messages.component("results.no-award"));
            for (String team : award.teams()) player.sendMessage(names(List.of(team)).append(messages.component("results.award-score", award.value(), results.points().get(team))));
        });
    }

    private Component names(List<String> names) {
        if (names.isEmpty()) return messages.component("results.no-team");
        Component result = Component.empty();
        for (String name : names) {
            if (!result.equals(Component.empty())) result = result.append(messages.component("general.separator"));
            TeamModel team = teams.getTeamByName(name);
            Component label = Component.text(name);
            if (team != null) label = label.color(TextColor.color(team.getColor().asRGB()));
            result = result.append(label);
        }
        return result;
    }

    private void title(Component title, Component subtitle, Sound sound) {
        Bukkit.getOnlinePlayers().forEach(player -> {
            player.showTitle(Title.title(title, subtitle, Title.Times.times(Duration.ofMillis(200), Duration.ofSeconds(3), Duration.ofMillis(400))));
            player.playSound(player.getLocation(), sound, 0.6f, 1.0f);
        });
    }

    private void updateVoteBar() {
        long seconds = results.remainingSeconds();
        Component name = messages.component("results.vote-bar", seconds / 60, seconds % 60, results.votesCast(), results.eligibleVoters());
        if (voteBar == null) voteBar = BossBar.bossBar(name, 1, BossBar.Color.PURPLE, BossBar.Overlay.PROGRESS);
        voteBar.name(name).progress(Math.min(1, seconds / 1800f));
        Set<UUID> online = new HashSet<>();
        Bukkit.getOnlinePlayers().forEach(player -> {
            online.add(player.getUniqueId());
            if (barViewers.add(player.getUniqueId())) player.showBossBar(voteBar);
        });
        barViewers.retainAll(online);
    }

    /** Termine le scrutin immédiatement, sans attendre les joueurs absents. */
    public boolean closeVoting() {
        if (suspended) return false;
        var award = results.finishVoting();
        if (award == null) return false;
        hideVoteBar(); showAward(award);
        countdown = 3; nextFrame = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        return true;
    }

    /** Affiche le classement acquis jusqu’à l’étape courante ; le détail reste accessible après la cérémonie. */
    public void showRanking(CommandSender sender) {
        sender.sendMessage(messages.get("results.ranking-title"));
        results.points().entrySet().stream().sorted(Map.Entry.<String, Integer>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                .forEach(entry -> sender.sendMessage(names(List.of(entry.getKey())).append(messages.component("results.ranking-row", entry.getValue()))));
        if (results.points().isEmpty()) sender.sendMessage(messages.get("results.no-team"));
    }

    private void hideVoteBar() {
        if (voteBar != null) Bukkit.getOnlinePlayers().forEach(player -> player.hideBossBar(voteBar));
        voteBar = null; barViewers.clear();
    }

    public void reset() {
        suspended = false;
        hideVoteBar(); tour.stop(); results.reset(); frame = 0; countdown = 0; nextFrame = 0;
        Bukkit.getOnlinePlayers().forEach(player -> player.clearTitle());
    }

    /** Nettoie l’interface à l’arrêt sans effacer les votes ni les points persistants. */
    public void shutdown() {
        pause(); hideVoteBar(); tour.stop();
        Bukkit.getOnlinePlayers().forEach(player -> player.clearTitle());
    }
}
