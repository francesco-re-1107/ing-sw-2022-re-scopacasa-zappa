package it.polimi.ingsw.model.charactercards;

import it.polimi.ingsw.model.InfluenceCalculator;
import it.polimi.ingsw.model.influencecalculators.NoTowersInfluenceCalculator;

/**
 * This class models the centaur card
 * EFFECT: Towers are not considered in the influence calculation process
 */
public class CentaurCharacterCard extends InfluenceCharacterCard {

    public CentaurCharacterCard() {
        super(3);
    }

    @Override
    public InfluenceCalculator getInfluenceCalculator() {
        return new NoTowersInfluenceCalculator();
    }
}
