package com.mceteams.xii;

import com.mceteams.xii.enums.TeamColor;
import com.mceteams.xii.manager.TeamManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests unitaires du TeamManager (spec §37).
 * Le manager est construit SANS plugin : la logique métier pure
 * (création, ajout, retrait, tailles) doit fonctionner sans serveur.
 */
class TeamManagerTest {

    private TeamManager teamManager;
    private final UUID alice = UUID.randomUUID();
    private final UUID bob = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        // Plugin null => mode test ; taille par défaut = 2 pour tester "plein".
        teamManager = new TeamManager(null, 2);
    }

    @Test
    void createTeamThenDuplicateFails() {
        assertTrue(teamManager.createTeam(TeamColor.BLUE));
        assertFalse(teamManager.createTeam(TeamColor.BLUE), "doublon refusé");
        assertEquals(1, teamManager.all().size());
    }

    @Test
    void addPlayerToTeam() {
        teamManager.createTeam(TeamColor.RED);
        assertEquals(TeamManager.AddResult.OK,
                teamManager.addPlayer(alice, TeamColor.RED));
        assertEquals(1, teamManager.getTeam(TeamColor.RED).getPlayerCount());
        assertEquals(TeamColor.RED,
                teamManager.getTeamOf(alice).getColor());
    }

    @Test
    void addPlayerTwiceSameTeamIsRejected() {
        teamManager.createTeam(TeamColor.GREEN);
        teamManager.addPlayer(alice, TeamColor.GREEN);
        assertEquals(TeamManager.AddResult.ALREADY_IN_TEAM,
                teamManager.addPlayer(alice, TeamColor.GREEN));
        assertEquals(1, teamManager.getTeam(TeamColor.GREEN).getPlayerCount());
    }

    @Test
    void switchingTeamsMovesThePlayer() {
        teamManager.createTeam(TeamColor.BLUE);
        teamManager.createTeam(TeamColor.YELLOW);
        teamManager.addPlayer(alice, TeamColor.BLUE);
        assertEquals(TeamManager.AddResult.OK,
                teamManager.addPlayer(alice, TeamColor.YELLOW));
        // L'ancienne équipe existe toujours mais est vidée de ses membres.
        assertTrue(teamManager.getTeam(TeamColor.BLUE).getPlayers().isEmpty(),
                "ancienne équipe vidée");
        assertEquals(alice, onlyMember(teamManager.getTeam(TeamColor.YELLOW)));
    }

    @Test
    void fullTeamRejectsNewMembers() {
        teamManager.createTeam(TeamColor.RED);
        teamManager.addPlayer(alice, TeamColor.RED);
        teamManager.addPlayer(bob, TeamColor.RED);
        assertEquals(TeamManager.AddResult.FULL,
                teamManager.addPlayer(UUID.randomUUID(), TeamColor.RED));
    }

    @Test
    void removePlayerLeavesTeamEmpty() {
        teamManager.createTeam(TeamColor.BLUE);
        teamManager.addPlayer(alice, TeamColor.BLUE);
        assertTrue(teamManager.removePlayer(alice));
        assertFalse(teamManager.removePlayer(alice), "second retrait échoue");
        assertEquals(0, teamManager.getTeam(TeamColor.BLUE).getPlayerCount());
    }

    @Test
    void removeTeamDetachesEverything() {
        teamManager.createTeam(TeamColor.GREEN);
        teamManager.addPlayer(alice, TeamColor.GREEN);
        assertTrue(teamManager.removeTeam(TeamColor.GREEN));
        assertNull(teamManager.getTeam(TeamColor.GREEN));
        assertNull(teamManager.getTeamOf(alice));
    }

    @Test
    void setMaxPlayersValidatesAgainstCurrentCount() {
        teamManager.createTeam(TeamColor.YELLOW);
        teamManager.addPlayer(alice, TeamColor.YELLOW);
        teamManager.addPlayer(bob, TeamColor.YELLOW);

        assertFalse(teamManager.setMaxPlayers(TeamColor.YELLOW, 1),
                "taille < effectif actuel refusée");
        assertTrue(teamManager.setMaxPlayers(TeamColor.YELLOW, 5));
        assertEquals(5, teamManager.getTeam(TeamColor.YELLOW).getMaxPlayers());
    }

    /** Helper : unique membre d'une équipe. */
    private UUID onlyMember(com.mceteams.xii.model.GameTeam team) {
        return team.getPlayers().iterator().next();
    }
}
