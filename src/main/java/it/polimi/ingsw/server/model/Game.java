package it.polimi.ingsw.server.model;

import it.polimi.ingsw.Constants;
import it.polimi.ingsw.Utils;
import it.polimi.ingsw.common.exceptions.InvalidOperationException;
import it.polimi.ingsw.server.model.charactercards.*;
import it.polimi.ingsw.server.model.influencecalculators.DefaultInfluenceCalculator;

import java.io.Serializable;
import java.text.MessageFormat;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * This class represents a game. When a new game is created it is in the State.CREATED state.
 * While in this state, players can be added through the method addPlayer(...).
 * When the desired number of players (@see numberOfPlayers in the constructor) is reached the game can be started
 * with the method startGame(). At this point the game state is State.STARTED.
 */
public class Game implements Serializable {

    /**
     * Stores the players of this game
     */
    private final ArrayList<Player> players;

    /**
     * Desired number of players of this game, decided when game is created
     */
    private final int numberOfPlayers;

    /**
     * Stores all the islands of this game.
     * The last one is "connected" with the first one in a circular path.
     */
    private List<Island> islands;

    /**
     * The students bag used to pick random students throughout the game
     */
    private final RandomizedStudentsContainer studentsBag;

    /**
     * Stores the current mother nature position (index relative to the islands list)
     */
    private int motherNaturePosition = 0;

    /**
     * Stores the current round that is being played
     */
    private Round currentRound;

    /**
     * Stores the 3 character cards selected for this game and the number of times they've been used
     */
    private final Map<String, Integer> characterCards = new HashMap<>();

    /**
     * Stores for each student color which player has the professor, it is empty when the game is started
     */
    private final Map<Student, Player> professors;

    /**
     * Stores the current game state (@see Game.State)
     */
    private State gameState = State.CREATED;

    /**
     * InfluenceCalculator used by default when calculating influence of a player on an island
     */
    private final InfluenceCalculator defaultInfluenceCalculator = new DefaultInfluenceCalculator();

    /**
     * One-shot InfluenceCalculator used when calculating influence of a player on an island
     * under a character card effect. It has higher priority than defaultInfluenceCalculator
     */
    private InfluenceCalculator temporaryInfluenceCalculator = null;

    /**
     * Used to retrieve the winner of this game, it is null until the game comes in the FINISHED state
     */
    private Player winner = null;

    /**
     * Whether the game is played with experto mode on
     */
    private final boolean expertMode;

    /**
     * Stores the unique id of this game
     */
    private final UUID uuid = UUID.randomUUID();

    /**
     * Stores all the listeners observing this game
     */
    private final transient ArrayList<GameUpdateListener> listeners;

    private static final transient Logger logger = Utils.LOGGER;

    /**
     * Create a new game
     *
     * @param numberOfPlayers number of players chose at the creation of the game
     */
    public Game(int numberOfPlayers, boolean expertMode) {

        if(numberOfPlayers < 2 || numberOfPlayers > 3)
            throw new InvalidOperationException("NumberOfPlayers must be 2 or 3");

        logger.log(Level.INFO, "Creating game with {0} players and expert = {1}", new Object[]{numberOfPlayers, expertMode});
        this.numberOfPlayers = numberOfPlayers;
        this.studentsBag = new RandomizedStudentsContainer(Constants.STUDENTS_BAG_NUMBER_PER_COLOR);
        this.players = new ArrayList<>();
        this.professors = new EnumMap<>(Student.class);
        this.expertMode = expertMode;
        this.listeners = new ArrayList<>();

        CharacterCard.generateRandomDeck(Constants.NUMBER_OF_CHARACTER_CARD)
                .forEach(s -> this.characterCards.put(s, 0));
        logger.log(Level.FINER, "Character cards -> {0}", characterCards.keySet());

        initializeIslands();
    }

    /**
     * Create the islands and add them in the islands list (used internally)
     */
    private void initializeIslands() {
        RandomizedStudentsContainer bag =
                new RandomizedStudentsContainer(Constants.ISLANDS_STUDENTS_BAG_NUMBER_PER_COLOR);

        this.islands = new LinkedList<>(Arrays.asList(
                new Island(),
                new Island(bag.pickOneRandom()),
                new Island(bag.pickOneRandom()),
                new Island(bag.pickOneRandom()),
                new Island(bag.pickOneRandom()),
                new Island(bag.pickOneRandom()),
                new Island(),
                new Island(bag.pickOneRandom()),
                new Island(bag.pickOneRandom()),
                new Island(bag.pickOneRandom()),
                new Island(bag.pickOneRandom()),
                new Island(bag.pickOneRandom())
        ));

        logger.fine("initialized islands");
    }

    /**
     * Start this game
     * Only if the numberOfPlayers is reached and if the Game is in state CREATED.
     */
    public void startGame() {
        if (players.size() != numberOfPlayers)
            throw new InvalidOperationException("Number of players not reached yet");

        if (gameState != State.CREATED)
            throw new InvalidOperationException(
                    MessageFormat.format("Game already started (gameState is {0})", gameState)
            );

        logger.info("game starting...");
        newRound();

        this.gameState = State.STARTED;

        notifyUpdate();
    }

    /**
     * Create a new round
     * Generate automatically the clouds.
     */
    private void newRound() {
        logger.info("round started");
        //if the bag is empty the game is finished
        if (studentsBag.getSize() == 0)
            setGameFinished(calculateCurrentWinner());

        //check if players have any card left
        boolean playersWithZeroCardsLeft = players.stream().anyMatch(p -> p.getAssistantCardsLeftCount() == 0);
        if (playersWithZeroCardsLeft)
            setGameFinished(calculateCurrentWinner());

        //generate clouds
        List<StudentsContainer> clouds = new ArrayList<>();
        for (int i = 0; i < numberOfPlayers; i++)
            clouds.add(studentsBag.pickManyRandom(
                    numberOfPlayers == 2 ?
                            Constants.TwoPlayers.STUDENTS_PER_CLOUD :
                            Constants.ThreePlayers.STUDENTS_PER_CLOUD)
            );

        logger.log(Level.FINE, "generated clouds: {0}", clouds);
        List<Player> tmpPlayers;

        //if it's not the first round, use the previous players order for the new round
        if (currentRound != null)
            tmpPlayers = currentRound.getPlayers();
        else
            tmpPlayers = new ArrayList<>(players);

        currentRound = new Round(tmpPlayers, clouds);

        notifyUpdate();
    }

    /**
     * Select a cloud for a given player
     *
     * @param cloud  the cloud chose by the player
     * @param player the player
     */
    public void selectCloud(Player player, StudentsContainer cloud) {
        checkIfCurrentPlayer(player);

        if (!currentRound.getClouds().contains(cloud))
            throw new InvalidOperationException("Cannot find selected cloud");

        player.addCloudToEntrance(cloud);
        currentRound.removeCloud(cloud);
        currentRound.setAttackSubstage(Stage.Attack.SELECTED_CLOUD);

        //go to next player or new round if necessary
        if (currentRound.nextPlayer())
            newRound();

        notifyUpdate();
    }

    /**
     * Check if the given player is the current player
     *
     * @param player the player to check
     */
    private void checkIfCurrentPlayer(Player player) {
        checkIfValidPlayer(player);

        if (!currentRound.getCurrentPlayer().equals(player))
            throw new InvalidOperationException("This player cannot play at this time");
    }

    /**
     * Add a new player to this game lobby, only if the game is in CREATED state and there's space for another user
     *
     * @param nickname the nickname chose by the player
     * @return the newly created player
     */
    public Player addPlayer(String nickname) {
        if (players.size() >= numberOfPlayers)
            throw new InvalidOperationException("Players lobby is already full");

        int entranceSize = numberOfPlayers == 2 ?
                Constants.TwoPlayers.ENTRANCE_SIZE :
                Constants.ThreePlayers.ENTRANCE_SIZE;

        int towersCount = numberOfPlayers == 2 ?
                Constants.TwoPlayers.TOWERS_COUNT :
                Constants.ThreePlayers.TOWERS_COUNT;

        Player p = new Player(
                nickname,
                Tower.values()[players.size()], //0->BLACK, 1->WHITE, 2->GREY
                towersCount,
                studentsBag.pickManyRandom(entranceSize)
        );

        logger.log(Level.INFO, "added player {0}", p.getNickname());
        players.add(p);

        notifyUpdate();

        return p;
    }

    /**
     * Move mother nature on the islands
     *
     * @param steps number of steps that mother nature needs to be moved
     */
    public void moveMotherNature(Player player, int steps) {
        checkIfCurrentPlayer(player);

        if(steps < 1)
            throw new InvalidOperationException("Cannot move mother nature for less than 1 step");

        if ((!(currentRound.getStage() instanceof Stage.Attack)) ||
                Stage.IsEqOrPost(currentRound.getStage(), Stage.Attack.MOTHER_NATURE_MOVED))
            throw new InvalidOperationException("Not currently in ATTACK mode");

        //use get directly cause in attack stage every player has played its card
        AssistantCard card = currentRound.getCardPlayedBy(player).orElseThrow();

        if (steps > card.motherNatureMaxMoves() + currentRound.getAdditionalMotherNatureMoves())
            throw new InvalidOperationException("Cannot move mother nature that far");
        //reset additional moves after use
        currentRound.setAdditionalMotherNatureMoves(0);

        this.motherNaturePosition = calculateMotherNatureIndex(steps);

        // if there's a no entry on the island then remove it and don't calculate influence
        if (getCurrentIsland().isNoEntry()) {
            getCurrentIsland().setNoEntry(false);
        } else {
            //after moving mother nature calculate influence on the island she just reached
            this.calculateInfluenceOnCurrentIsland();

            //After influence calculation a player may have conquered a new island.
            //It's necessary to check if islands could be merged
            this.checkMergeableIslands();
        }
        currentRound.setAttackSubstage(Stage.Attack.MOTHER_NATURE_MOVED);

        notifyUpdate();
    }

    /**
     * Calculate which player has the most influence on the current island
     * and change the towers on that island respectively
     */
    private void calculateInfluenceOnCurrentIsland() {
        this.calculateInfluenceOnIsland(getCurrentIsland());
    }

    /**
     * Calculate which player has the most influence on the given island
     * and change the towers on that island respectively
     */
    private void calculateInfluenceOnIsland(Island island) {
        int max = -1;
        Optional<Player> maxP = Optional.empty();

        var calc =
                Objects.requireNonNullElse(temporaryInfluenceCalculator, defaultInfluenceCalculator);

        for (Player p : players) {
            int infl = calc.calculateInfluence(p, island, getProfessors());

            if (infl > max) {
                max = infl;
                maxP = Optional.of(p);
            } else if (infl == max) { // if there are two or more maximums
                maxP = Optional.empty();
            }
        }

        //only if there's a max without duplicate
        maxP.ifPresent(player -> {
                //only if island is not yet conquered by this player
                if (player.getTowerColor() != island.getTowerColor()) {
                    if(island.isConquered()) { //island already conquered, remove towers from the previous owner
                        players.stream()
                                .filter(p -> p.getTowerColor() == island.getTowerColor())
                                .findFirst()
                                .ifPresent(p -> p.incrementTowersCount(island.getTowersCount()));
                    }

                    island.setConquered(player.getTowerColor());
                    //in both cases remove towers from player
                    player.decrementTowersCount(island.getTowersCount());

                    if (player.getTowersCount() <= 0)
                        setGameFinished(player);
                }
            }
        );

        //remove temporary after use
        temporaryInfluenceCalculator = null;
    }

    /**
     * @param player
     * @param card
     */
    public void playAssistantCard(Player player, AssistantCard card) {
        checkIfCurrentPlayer(player);

        if (!player.canPlayAssistantCard(card))
            throw new InvalidOperationException();

        logger.log(Level.INFO, MessageFormat.format("{0} played assistant card with priority {1} and max moves {2} ",
                player.getNickname(), card.turnPriority(), card.motherNatureMaxMoves()));
        currentRound.playAssistantCard(player, card);

        notifyUpdate();
    }

    /**
     * Play character card for the given player
     * @param player the player that plays the card
     * @param card the card to paly
     */
    public void playCharacterCard(Player player, CharacterCard card) {
        if (!expertMode)
            throw new InvalidOperationException("Cannot play character cards in simple mode");

        checkIfCurrentPlayer(player);

        if ((!(currentRound.getStage() instanceof Stage.Attack)) ||
                Stage.IsEqOrPost(currentRound.getStage(), Stage.Attack.CARD_PLAYED))
            throw new InvalidOperationException();

        if (!characterCards.containsKey(card.getName()))
            throw new InvalidOperationException();

        int cost = card.getCost(characterCards.get(card.getName()));

        if (player.getCoins() < cost)
            throw new InvalidOperationException("Player cannot buy the card");

        logger.log(Level.INFO,  MessageFormat.format("playing character card {0} at {1}c", card.getName(), cost));

        player.useCoins(cost);

        if (card instanceof InfluenceCharacterCard influenceCard) {
            temporaryInfluenceCalculator = influenceCard.getInfluenceCalculator(player);
        } else if (card instanceof HeraldCharacterCard heraldCard) {
            calculateInfluenceOnIsland(heraldCard.getIsland());
        } else if (card instanceof PostmanCharacterCard postmanCard) {
            currentRound.setAdditionalMotherNatureMoves(postmanCard.getAdditionalMotherNatureMoves());
        } else if (card instanceof GrandmaCharacterCard grandmaCard) {
            if (!islands.contains(grandmaCard.getIsland()))
                throw new InvalidOperationException("Island not present in this game");
            grandmaCard.getIsland().setNoEntry(true);
        } else if (card instanceof MinstrelCharacterCard minstrelCard) {
            if (minstrelCard.getStudentsToRemove().getSize() > 2 ||
                    minstrelCard.getStudentsToAdd().getSize() > 2)
                throw new InvalidOperationException("Too much students to swap");

            player.swapStudents(minstrelCard.getStudentsToRemove(), minstrelCard.getStudentsToAdd());
        }

        currentRound.setAttackSubstage(Stage.Attack.CARD_PLAYED);

        notifyUpdate();
    }

    /**
     * Move students of a player from entrance to school or islands
     *
     * @param player
     * @param inSchool students to add to school
     * @param inIsland students to add to the relative island
     */
    public void placeStudents(Player player, StudentsContainer inSchool, Map<Island, StudentsContainer> inIsland) {
        checkIfCurrentPlayer(player);

        if (currentRound.getStage() != Stage.Attack.STARTED)
            throw new InvalidOperationException();

        int studentsMoved = inSchool.getSize() + inIsland.values().stream().mapToInt(StudentsContainer::getSize).sum();

        int studentsToMove = numberOfPlayers == 3 ?
                Constants.ThreePlayers.STUDENTS_TO_MOVE :
                Constants.TwoPlayers.STUDENTS_TO_MOVE;

        if (studentsMoved != studentsToMove)
            throw new InvalidOperationException();

        player.addStudentsToSchool(inSchool);
        inIsland.forEach(player::addStudentsToIsland);

        updateProfessors();

        if(expertMode)
            currentRound.setAttackSubstage(Stage.Attack.STUDENTS_PLACED);
        else
            currentRound.setAttackSubstage(Stage.Attack.CARD_PLAYED);

        notifyUpdate();
    }

    /**
     * Check if island could be merged.
     * It is called every time mother nature is moved so only the current island is checked
     */
    private void checkMergeableIslands() {
        Island curr = getCurrentIsland();
        Island prev = islands.get(calculateMotherNatureIndex(-1));
        Island next = islands.get(calculateMotherNatureIndex(1));

        if (curr.isMergeCompatible(prev)) {
            logger.info("island merged with previous one");
            curr.merge(prev);
            islands.remove(prev);
        }

        if (curr.isMergeCompatible(next)) {
            logger.info("island merged with next one");
            curr.merge(next);
            islands.remove(next);
        }

        if (islands.size() <= 3)
            setGameFinished(calculateCurrentWinner());

        //adjust motherNatureIndex
        this.motherNaturePosition = islands.indexOf(curr);
    }

    /**
     * Calculate player that is winning right now, based on the number of towers left.
     * If there's a draw, the player with most professors wins
     * If there's a draw, a random player is returned
     *
     * @return the winning player
     */
    private Player calculateCurrentWinner() {
        //order players by placed towers
        var orderedPlayers = players.stream()
                .sorted(Comparator.comparingInt(Player::getTowersCount))
                .toList();

        Player firstPlayer =
                orderedPlayers.get(0);

        Player secondPlayer =
                orderedPlayers.get(1);

        if (firstPlayer.getTowersCount() < secondPlayer.getTowersCount()) //there's a winner
            return firstPlayer;

        //otherwise, look at the professors
        //players with same towers count are ordered by the number of professors
        orderedPlayers = players.stream()
                .filter(p -> p.getTowersCount() == firstPlayer.getTowersCount())
                .sorted(Comparator.comparingInt(p -> getProfessorsForPlayer((Player)p).size()).reversed())
                .toList();

        return orderedPlayers.get(0);
    }

    /**
     * @return the island on which mother nature is located
     */
    public Island getCurrentIsland() {
        return islands.get(this.motherNaturePosition);
    }

    /**
     * Calculate the index of mother nature on the islands in a circular path
     *
     * @param steps number of steps to move mother nature (positive or negative)
     * @return the calculated index
     */
    private int calculateMotherNatureIndex(int steps) {
        //using floorMod because the % operator gives problems with negative numbers
        return Math.floorMod(this.motherNaturePosition + steps, islands.size());
    }

    /**
     * Add a game update listener for this game
     *
     * @param listener
     */
    public void addGameUpdateListener(GameUpdateListener listener) {
        listeners.add(listener);

        //when a new listener is added, notify to this new listener only
        listener.onGameUpdate(this);
    }

    /**
     * Remove a previously added listener
     *
     * @param listener
     */
    public void removeGameUpdateListener(GameUpdateListener listener) {
        listeners.remove(listener);
    }

    /**
     * This method is called whenever there's a change to be notified to the listeners
     */
    private void notifyUpdate() {
        //create a copy so observers can remove the listener
        new ArrayList<>(listeners)
                .forEach(l -> l.onGameUpdate(this));
    }

    /**
     * Calculate current professors
     * If two players have the same number of students nothing changes for that student
     */
    private void updateProfessors() {
        Arrays.stream(Student.values()).forEach(s -> {
            List<Player> sortedPlayers = players.stream()
                    .sorted(
                            Comparator.comparingInt(p -> ((Player) p).getSchool().getCountForStudent(s))
                                    .reversed()
                    )
                    .toList();

            Player firstPlayer = sortedPlayers.get(0);
            Player secondPlayer = sortedPlayers.get(1);

            if (firstPlayer.getSchool().getCountForStudent(s) >
                    secondPlayer.getSchool().getCountForStudent(s))
                professors.put(s, firstPlayer);
        });
    }

    /**
     * Utility method to retrieve the professors of a given player
     *
     * @param player
     * @return a list of student for which the player has the professor
     */
    public List<Student> getProfessorsForPlayer(Player player) {
        return professors.entrySet()
                .stream()
                .filter(e -> e.getValue().equals(player))
                .map(Map.Entry::getKey)
                .toList();
    }

    /**
     * End this game and set a winner
     *
     * @param winner the winner of the game
     */
    private void setGameFinished(Player winner) {
        logger.log(Level.INFO, "game finished! {0} won", winner.getNickname());
        gameState = State.FINISHED;

        this.winner = winner;

        notifyUpdate();
    }

    /**
     * @return a copy of the players list of this game
     */
    public List<Player> getPlayers() {
        //return a copy
        return new ArrayList<>(players);
    }

    /**
     * If game is started, this will return the same as getCurrentNumberOfPlayers()
     *
     * @return the desired number of players
     */
    public int getNumberOfPlayers() {
        return numberOfPlayers;
    }

    /**
     * If game is started, this will return the same as getNumberOfPlayers()
     *
     * @return the current number of players
     */
    public int getCurrentNumberOfPlayers() {
        return players.size();
    }

    /**
     * @return a copy of the islands list
     */
    public List<Island> getIslands() {
        //return a copy
        return new ArrayList<>(islands);
    }

    /**
     * @return current mother nature position (index relative to the islands list)
     */
    public int getMotherNaturePosition() {
        return motherNaturePosition;
    }

    /**
     * @return the current round
     */
    public Round getCurrentRound() {
        return currentRound;
    }

    /**
     * @return a copy of the character cards selected for this game
     */
    public Map<String, Integer> getCharacterCards() {
        //return a copy
        return new HashMap<>(characterCards);
    }

    /**
     * @return a copy of the current professors
     */
    public Map<Student, Player> getProfessors() {
        //return a copy
        return new EnumMap<>(professors);
    }

    /**
     * @return current game state (@see Game.State)
     */
    public State getGameState() {
        return gameState;
    }


    /**
     * When a player disconnects the game is paused until it reconnects back
     * @param player the player disconnected
     */
    public void setPlayerDisconnected(Player player){
        checkIfValidPlayer(player);

        player.setConnected(false);

        //pause game
        this.gameState = State.PAUSED;

        notifyUpdate();
    }

    /**
     * This method is called when a player (previously disconnected) reconnects
     * @param player
     */
    public void setPlayerReconnected(Player player){
        checkIfValidPlayer(player);

        player.setConnected(true);

        //resume game if all players are connected
        boolean allConnected = players.stream().allMatch(Player::isConnected);
        if(allConnected)
            this.gameState = State.STARTED;

        notifyUpdate();
    }

    /**
     * This method is called when a player explicitly left the game.
     * The game is terminated for all and there is no winner.
     * @param player
     */
    public void leaveGame(Player player) {
        checkIfValidPlayer(player);

        if(gameState == State.CREATED) {
            //just remove the player
            players.remove(player);
        } else if(gameState == State.FINISHED || gameState == State.TERMINATED){
            //game already in finished or terminated state
            throw new InvalidOperationException("Game is finished or terminated, cannot leave");
        } else {
            //if the player leaves the game sets it as disconnected
            player.setConnected(false);

            this.gameState = State.TERMINATED;
        }

        notifyUpdate();
    }

    /**
     * Check if the given player is a valid player for this game
     * @param player
     */
    private void checkIfValidPlayer(Player player) {
        if(!players.contains(player))
            throw new InvalidOperationException("Not a valid player");
    }

    /**
     * Return
     *
     * @return the winner of the game, if the game was ended
     */
    public Player getWinner() {
        return winner;
    }

    /**
     * @return whether the game is played with expert rules or not
     */
    public boolean isExpertMode() {
        return expertMode;
    }

    /**
     * @return uuid of this game
     */
    public UUID getUUID() {
        return uuid;
    }

    /**
     * @return students bag copy
     */
    public RandomizedStudentsContainer getStudentsBag() {
        return new RandomizedStudentsContainer(studentsBag);
    }

    /**
     * this enum represents all the possible states of a game
     */
    public enum State {
        CREATED, //game was created but never started
        STARTED, //game is currently played
        PAUSED, //when a client disconnects, the game is paused
        FINISHED, //game finished, there's a winner
        TERMINATED //game terminated before finish (e.g. a player left the game)
    }

    public interface GameUpdateListener {
        void onGameUpdate(Game game);
    }
}
