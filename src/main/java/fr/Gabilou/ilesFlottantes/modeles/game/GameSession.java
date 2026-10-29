package fr.Gabilou.ilesFlottantes.modeles.game;

import java.time.Duration;
import java.util.Objects;
import java.util.function.LongSupplier;

/**
 * Cycle de vie d’une partie et chronomètre monotone, indépendants de Bukkit.
 * Une reprise conserve le temps accumulé ; seul un reset autorise un nouveau
 * départ après l’arrêt définitif. Les appels sont effectués sur le thread serveur.
 */
public final class GameSession {
    /** États possibles de la partie ; STOPPED est terminal jusqu’au reset. */
    public enum State { READY, RUNNING, PAUSED, STOPPED }

    private final LongSupplier nanoTime;
    private State state = State.READY;
    private long elapsedNanos;
    private long startedAt;
    private Duration timeLimit;

    /** Crée une partie en attente utilisant l’horloge monotone du système. */
    public GameSession() { this(System::nanoTime); }

    /** @param nanoTime horloge monotone en nanosecondes, injectable pour les tests */
    public GameSession(LongSupplier nanoTime) { this.nanoTime = Objects.requireNonNull(nanoTime); }

    /** @return l’état courant de la partie */
    public State getState() { return state; }

    /** @return durée active, sans inclure les pauses */
    public Duration elapsed() {
        Duration elapsed = Duration.ofNanos(elapsedNanos + (state == State.RUNNING ? nanoTime.getAsLong() - startedAt : 0));
        return timeLimit != null && elapsed.compareTo(timeLimit) > 0 ? timeLimit : elapsed;
    }

    /** @return vrai si une durée limite a été configurée */
    public boolean isCountdown() { return timeLimit != null; }

    /** @return temps écoulé en mode libre, ou temps restant sans valeur négative */
    public Duration displayedTime() { return timeLimit == null ? elapsed() : timeLimit.minus(elapsed()); }

    /**
     * Configure le mode avant le premier départ ; zéro retire la limite.
     * @param minutes durée en minutes, de zéro à 525600 (un an)
     * @return faux si la partie a déjà démarré ou est terminée
     * @throws IllegalArgumentException pour une durée hors limites
     */
    public boolean setTimeLimit(int minutes) {
        if (minutes < 0 || minutes > 525600) throw new IllegalArgumentException();
        if (state != State.READY) return false;
        timeLimit = minutes == 0 ? null : Duration.ofMinutes(minutes);
        return true;
    }

    /**
     * Termine automatiquement une partie ayant atteint sa limite.
     * @return vrai uniquement lors de la transition vers l’état terminé
     */
    public boolean finishIfExpired() {
        if (state != State.RUNNING || timeLimit == null || elapsed().compareTo(timeLimit) < 0) return false;
        elapsedNanos = timeLimit.toNanos();
        state = State.STOPPED;
        return true;
    }

    /** @return vrai si la partie démarre ou reprend, faux si la transition est interdite */
    public boolean start() {
        if (state != State.READY && state != State.PAUSED) return false;
        startedAt = nanoTime.getAsLong();
        state = State.RUNNING;
        return true;
    }

    /** @return vrai si une partie en cours a été suspendue */
    public boolean pause() {
        if (finishIfExpired()) return false;
        if (state != State.RUNNING) return false;
        elapsedNanos = elapsed().toNanos();
        state = State.PAUSED;
        return true;
    }

    /** @return vrai si une partie active ou suspendue a été terminée */
    public boolean stop() {
        if (state != State.RUNNING && state != State.PAUSED) return false;
        elapsedNanos = elapsed().toNanos();
        state = State.STOPPED;
        return true;
    }

    /** Efface le chronomètre et remet la partie en attente. */
    public void reset() {
        elapsedNanos = 0;
        startedAt = 0;
        state = State.READY;
        timeLimit = null;
    }
}
