package it.polimi.ingsw.common.reducedmodel;

import it.polimi.ingsw.server.model.Round;
import it.polimi.ingsw.server.model.Stage;
import it.polimi.ingsw.server.model.StudentsContainer;

import java.io.Serializable;
import java.util.List;

/**
 * This record represents a Round for the client.
 * This is a reduced version of the Round class, so it used in the communication with the client
 */
public record ReducedRound(
        Stage stage,
        ReducedPlayer currentPlayer,
        List<StudentsContainer> clouds,
        int additionalMotherNatureMoves
        //Map<ReducedPlayer, AssistantCard> playedAssistantCards
) implements Serializable {

    /**
     * Create a ReducedRound starting from a Round
     * @param r the round to translate
     * @return the ReducedRound just created
     */
    public static ReducedRound fromRound(Round r) {
        return new ReducedRound(
                r.getStage(),
                ReducedPlayer.fromPlayer(r.getCurrentPlayer()),
                r.getClouds(),
                r.getAdditionalMotherNatureMoves()
                /*r.getPlayers()
                        .stream()
                        .collect(Collectors.toMap(
                                ReducedPlayer::fromPlayer,
                                p -> r.getCardPlayedBy(p).orElse(null))
                        )*/
        );
    }
}
