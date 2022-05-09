package it.polimi.ingsw.common.responses.replies;

import it.polimi.ingsw.common.reducedmodel.GameListItem;
import it.polimi.ingsw.common.responses.Reply;

import java.util.List;
import java.util.UUID;

public class GamesListReply extends Reply {

    private final List<GameListItem> gamesList;

    public GamesListReply(UUID requestId, List<GameListItem> games) {
        super(requestId, true);
        gamesList = games;
    }

    public List<GameListItem> getGamesList() {
        return gamesList;
    }
}
