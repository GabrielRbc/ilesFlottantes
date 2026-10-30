package fr.Gabilou.ilesFlottantes.persistence;

import fr.Gabilou.ilesFlottantes.interfaces.*;
import fr.Gabilou.ilesFlottantes.modeles.game.*;
import fr.Gabilou.ilesFlottantes.modeles.results.FinalResults;
import fr.Gabilou.ilesFlottantes.modeles.teams.*;
import fr.Gabilou.ilesFlottantes.modeles.territories.SpawnService;
import fr.Gabilou.ilesFlottantes.utils.MessageManager;
import org.bukkit.Bukkit;
import java.io.IOException;
import java.util.*;
import java.util.logging.*;

/**
 * Coordonne les points de reprise synchrones : immédiatement après mutation et chaque
 * seconde pour les compteurs. En cas d’erreur disque, suspend le jeu et la cérémonie.
 * Les réinitialisations multi-fichiers utilisent un marqueur durable rejoué au démarrage.
 */
public final class SessionPersistence {
    private final FileSessionRepository repository;
    private final GameSession game;
    private final TeamManager teams;
    private final FinalResults results;
    private final ResultsController controller;
    private final SpectatorTour tour;
    private final SpawnService spawns;
    private final MessageManager messages;
    private final Logger logger;
    private boolean suppressed = true;
    private boolean saving;
    private boolean failed;
    private boolean incompleteReset;

    public SessionPersistence(FileSessionRepository repository, GameSession game, TeamManager teams, FinalResults results,
                              ResultsController controller, SpectatorTour tour, SpawnService spawns, MessageManager messages, Logger logger) {
        this.repository = repository; this.game = game; this.teams = teams; this.results = results;
        this.controller = controller; this.tour = tour; this.spawns = spawns; this.messages = messages; this.logger = logger;
    }

    /** Charge avant d’abonner les mutations ; un fichier invalide ne sera jamais remplacé par une partie vide. */
    public boolean load(GameManager manager) throws IOException {
        var saved = repository.load();
        try {
            if (saved.isPresent()) {
                SessionSnapshot data = saved.get();
                Set<String> names = new HashSet<>();
                teams.getTeams().stream().filter(TeamModel::isPlayable).forEach(team -> names.add(team.getName()));
                if (!names.containsAll(data.results().points().keySet()) || !names.containsAll(data.results().bosses().values())
                        || !names.containsAll(data.results().electorate().values())) throw new IllegalArgumentException();
                teams.restore(data.players()); game.restore(data.game()); results.restore(data.results());
                boolean visiting = data.tourActive() || data.results().phase() == FinalResults.Phase.VOTING
                        || data.results().phase() == FinalResults.Phase.REVEAL || data.results().phase() == FinalResults.Phase.FINISHED;
                tour.restore(data.visitors(), visiting); spawns.restorePending(data.pendingSpawns()); controller.restore();
            }
            if (repository.resetting()) {
                manager.reset();
                repository.save(snapshot()); repository.finishReset();
            }
        } catch (RuntimeException e) { throw new IOException(e); }
        game.onChange(this::checkpoint); teams.onChange(this::checkpoint); results.onChange(this::checkpoint);
        controller.onChange(this::checkpoint); tour.onChange(this::checkpoint); spawns.onChange(this::checkpoint);
        manager.resetTransaction(this::reset);
        manager.persistence(this::checkpoint, this::healthy);
        suppressed = false;
        // Établit une première sauvegarde avant d’accepter les commandes.
        repository.save(snapshot());
        return saved.isPresent();
    }

    private SessionSnapshot snapshot() {
        return new SessionSnapshot(game.snapshot(), results.snapshot(), teams.snapshot(), spawns.pending(), tour.snapshot(), tour.active());
    }

    /** Reste synchrone pour ne jamais capturer des objets Bukkit sur un thread de sauvegarde. */
    public void checkpoint() {
        if (suppressed || saving || incompleteReset) return;
        saving = true;
        try {
            repository.save(snapshot());
            if (repository.resetting()) repository.finishReset();
            if (failed) logger.info(messages.text("session.save-recovered"));
            failed = false;
        } catch (IOException | RuntimeException e) {
            game.pause(); controller.pause();
            if (!failed) {
                logger.log(Level.SEVERE, messages.text("session.save-failed"), e);
                Bukkit.getOnlinePlayers().stream().filter(player -> player.isOp()).forEach(player -> player.sendMessage(messages.get("session.save-failed")));
            }
            failed = true;
        } finally { saving = false; }
    }

    /** Écrit l’intention avant le premier changement, puis publie un instantané cohérent après le reset. */
    private void reset(GameManager.ResetAction action) throws IOException {
        repository.beginReset(); suppressed = true; incompleteReset = true;
        try {
            try { action.run(); }
            catch (IOException e) { repository.finishReset(); incompleteReset = false; throw e; }
            incompleteReset = false;
            repository.save(snapshot()); repository.finishReset(); failed = false;
        } finally { suppressed = false; }
    }

    /** Sauvegarde finale après suspension des compteurs, sans remise à zéro. */
    public void shutdown() {
        if (suppressed) return;
        suppressed = true;
        game.pause(); controller.shutdown();
        suppressed = false; checkpoint(); suppressed = true;
    }

    public boolean healthy() { return !failed && !incompleteReset; }
}
