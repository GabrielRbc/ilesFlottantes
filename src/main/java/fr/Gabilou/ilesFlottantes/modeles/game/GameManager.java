package fr.Gabilou.ilesFlottantes.modeles.game;

import fr.Gabilou.ilesFlottantes.modeles.economy.EconomyManager;
import fr.Gabilou.ilesFlottantes.modeles.teams.TeamManager;
import java.io.IOException;

/**
 * Coordonne la partie, les équipes et leurs caisses sur le thread serveur.
 * Le reset sauvegarde les caisses vides avant de vider les équipes et les statistiques :
 * un échec de persistance laisse la partie intacte. Les inventaires et mondes sont conservés.
 */
public final class GameManager {
    private final GameSession session;
    private final TeamManager teams;
    private final EconomyManager economy;

    public GameManager(GameSession session, TeamManager teams, EconomyManager economy) {
        this.session = session;
        this.teams = teams;
        this.economy = economy;
    }

    public GameSession getSession() { return session; }

    /** Démarre ou reprend ; les équipements ne sont donnés qu’au premier départ. */
    public boolean start() {
        boolean firstStart = session.getState() == GameSession.State.READY;
        if (!session.start()) return false;
        if (firstStart) teams.giveStartEquipment();
        return true;
    }

    /**
     * Réinitialise les données de partie, y compris les joueurs déconnectés.
     * @throws IOException si les caisses ne peuvent pas être sauvegardées
     */
    public void reset() throws IOException {
        economy.reset();
        teams.reset();
        session.reset();
    }
}
