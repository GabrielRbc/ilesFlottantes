package fr.Gabilou.ilesFlottantes.modeles.game;

import fr.Gabilou.ilesFlottantes.modeles.economy.EconomyManager;
import fr.Gabilou.ilesFlottantes.modeles.teams.TeamManager;
import java.io.IOException;
import fr.Gabilou.ilesFlottantes.modeles.territories.SpawnService;
import fr.Gabilou.ilesFlottantes.modeles.territories.TerritoryManager;

/**
 * Coordonne la partie, les équipes et leurs caisses sur le thread serveur.
 * Le reset sauvegarde les caisses vides avant de vider les équipes et les statistiques :
 * un échec de persistance laisse la partie intacte. Les inventaires et mondes sont conservés.
 */
public final class GameManager {
    private final GameSession session;
    private final TeamManager teams;
    private final EconomyManager economy;
    private final SpawnService spawns;
    private final TerritoryManager territories;
    private final Runnable resetResults;
    /** Corps d’une remise à zéro pouvant échouer lors de la sauvegarde des caisses. */
    @FunctionalInterface public interface ResetAction { void run() throws IOException; }
    /** Frontière de transaction fournie par la persistance de session. */
    @FunctionalInterface public interface ResetTransaction { void run(ResetAction action) throws IOException; }
    private ResetTransaction resetTransaction = ResetAction::run;
    public void resetTransaction(ResetTransaction transaction) { resetTransaction = transaction; }
    private Runnable saveSession = () -> { };
    private java.util.function.BooleanSupplier storageHealthy = () -> true;
    public void persistence(Runnable save, java.util.function.BooleanSupplier healthy) { saveSession = save; storageHealthy = healthy; }
    public boolean save() { saveSession.run(); return storageHealthy.getAsBoolean(); }
    public boolean storageHealthy() { return storageHealthy.getAsBoolean(); }

    public GameManager(GameSession session, TeamManager teams, EconomyManager economy, SpawnService spawns, TerritoryManager territories, Runnable resetResults) {
        this.session = session;
        this.teams = teams;
        this.economy = economy;
        this.spawns = spawns;
        this.territories = territories;
        this.resetResults = resetResults;
    }

    public GameSession getSession() { return session; }

    /** Démarre ou reprend ; les équipements ne sont donnés qu’au premier départ. */
    public boolean start() {
        boolean firstStart = session.getState() == GameSession.State.READY;
        if (firstStart) spawns.teleportAtStart();
        if (!session.start()) return false;
        if (firstStart) teams.giveStartEquipment();
        return true;
    }

    /**
     * Réinitialise les données de partie, y compris les joueurs déconnectés.
     * @throws IOException si les caisses ne peuvent pas être sauvegardées
     */
    public void reset() throws IOException {
        resetTransaction.run(this::resetState);
    }

    private void resetState() throws IOException {
        economy.reset();
        resetResults.run();
        teams.reset();
        session.reset();
        spawns.reset();
        territories.lockAll();
    }
}
