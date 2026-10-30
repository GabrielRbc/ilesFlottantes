package fr.Gabilou.ilesFlottantes.modeles.teams;

import org.bukkit.Color;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

/** Vérifie qu’une équipe de zones communes reste sans membres, même via le modèle. */
class TeamModelTest {
    @Test void commonTeamCannotHaveMembers() {
        TeamModel common = new TeamModel("Commun", Color.WHITE, false);
        assertFalse(common.isPlayable());
        assertFalse(common.addPlayer(new GamePlayer(UUID.randomUUID(), "Alice")));
        assertTrue(common.getPlayers().isEmpty());
    }

    @Test void playerTeamsKeepTheirMembershipBehavior() {
        TeamModel red = new TeamModel("Rouge", Color.RED);
        GamePlayer player = new GamePlayer(UUID.randomUUID(), "Alice");
        assertTrue(red.isPlayable());
        assertTrue(red.addPlayer(player));
        assertFalse(red.addPlayer(player));
        assertThrows(UnsupportedOperationException.class, () -> red.getPlayers().clear());
        assertTrue(red.removePlayer(player));
    }
}
