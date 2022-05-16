package it.polimi.ingsw.client.gui.controllers;

import it.polimi.ingsw.Constants;
import it.polimi.ingsw.Utils;
import it.polimi.ingsw.client.Client;
import it.polimi.ingsw.client.ScreenController;
import it.polimi.ingsw.client.gui.InfoStrings;
import it.polimi.ingsw.client.gui.customviews.*;
import it.polimi.ingsw.common.reducedmodel.ReducedGame;
import it.polimi.ingsw.common.reducedmodel.ReducedIsland;
import it.polimi.ingsw.common.reducedmodel.ReducedPlayer;
import it.polimi.ingsw.common.requests.MoveMotherNatureRequest;
import it.polimi.ingsw.common.requests.PlaceStudentsRequest;
import it.polimi.ingsw.common.requests.PlayAssistantCardRequest;
import it.polimi.ingsw.server.model.*;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.util.*;
import java.util.stream.Collectors;

public class GameController implements ScreenController, Client.GameUpdateListener {
    @FXML
    private IslandCircularPane islandsPane;
    @FXML
    private CloudsPane cloudsPane;
    @FXML
    private VBox characterCardsView;
    @FXML
    private HBox characterCards;
    @FXML
    private CharacterCardView characterCard1;
    @FXML
    private CharacterCardView characterCard2;
    @FXML
    private CharacterCardView characterCard3;
    @FXML
    private AssistantCardDeckView assistantCardsDeck;
    @FXML
    private VBox assistantCardsLayer;
    @FXML
    private Button hideAssistantCardsDeck;
    @FXML
    private GameTitlePopupView gameTitlePopup;
    @FXML
    public PlayerBoardView myPlayerBoardView;
    @FXML
    private PlayerBoardView player2BoardView;
    @FXML
    private PlayerBoardView player3BoardView;
    @FXML
    private Button leaveButton;
    @FXML
    private Label myTowerLabel;
    @FXML
    private HBox myCoin;
    @FXML
    private TowerView myTower;
    @FXML
    private Label infoLabel;
    @FXML
    private GridPane myStudentsBoard;
    @FXML
    private Label myCoinLabel;
    private ReducedGame currentGame;

    private StudentsContainer studentsPlacedInSchool;

    private Map<Integer, StudentsContainer> studentsPlacedInIslands;
    private String myNickname;

    private StudentSelectContextMenu studentSelectContextMenu;

    private void setAssistantDeckVisibility(boolean visible) {
        assistantCardsLayer.setVisible(visible);
        assistantCardsLayer.setManaged(visible);
    }

    private void setAssistantCardsDeck(Map<AssistantCard, Boolean> deck) {
        assistantCardsDeck.setDeck(deck);
        assistantCardsDeck.setOnCardSelected(card -> Client.getInstance().forwardGameRequest(
                new PlayAssistantCardRequest(card),
                () -> setAssistantDeckVisibility(false),
                err -> assistantCardsDeck.showError("Non puoi giocare questa carta")
        ));
    }

    private void setInfoString(String info, Object... optionalArgs) {
        if (InfoStrings.EMPTY.equals(info)) {
            infoLabel.setVisible(false);
            infoLabel.setManaged(false);
        } else {
            infoLabel.setVisible(true);
            infoLabel.setManaged(true);
        }
        infoLabel.setText(String.format(info, optionalArgs));
    }

    private void setMyTowers(Tower towerColor, int numberOfTowers) {
        myTower.setTowerColor(towerColor);
        myTowerLabel.setText(numberOfTowers + "");
    }

    @FXML
    private void onLeavePressed() {
        Client.getInstance().leaveGame();
    }

    public void setMotherNaturePosition(int index) {
        //reset
        for (var iv : islandsPane.getChildren())
            ((IslandView) iv).getMotherNatureView().setState(MotherNatureView.State.INVISIBLE);

        var iv = (IslandView) islandsPane.getChildren().get(index);
        iv.getMotherNatureView().setState(MotherNatureView.State.ENABLED);
    }

    public void setMotherNaturePossibleSteps(int index, int steps) {
        //reset previous possible steps
        for (var n : islandsPane.getChildren()) {
            var iv = (IslandView) n;
            var mn = iv.getMotherNatureView();
            iv.setDisable(true);

            if (mn.getState() != MotherNatureView.State.ENABLED)
                mn.setState(MotherNatureView.State.INVISIBLE);
        }

        var currIndex = (index + 1) % islandsPane.getChildren().size();

        var currStep = 1;

        while (currStep <= steps) {
            //TODO: improve
            final var currStepFinal = currStep;

            var iv = (IslandView) islandsPane.getChildren().get(currIndex);
            if (currIndex != index) {
                iv.getMotherNatureView().setState(MotherNatureView.State.DISABLED);
                iv.setOnMouseClicked(e -> {
                    Client.getInstance().forwardGameRequest(
                            new MoveMotherNatureRequest(currStepFinal),
                            () -> {},
                            err -> Utils.LOGGER.info("Error moving mother nature: " + err.getMessage())
                    );
                });
            }
            iv.setDisable(false);
            currIndex = (currIndex + 1) % islandsPane.getChildren().size();
            currStep++;
        }
    }

    public void setVisibilityForExpertMode(boolean expertMode) {
        player2BoardView.setVisibilityForExpertMode(expertMode);
        player3BoardView.setVisibilityForExpertMode(expertMode);

        myCoin.setVisible(expertMode);
        myCoin.setManaged(expertMode);

        characterCardsView.setVisible(expertMode);
        characterCardsView.setManaged(expertMode);
    }

    public void setVisibilityForNumberOfPlayers(int numberOfPlayers) {
        player3BoardView.setVisible(numberOfPlayers == 3);
        player3BoardView.setManaged(numberOfPlayers == 3);
    }

    public void setMyStudentsBoard(ReducedPlayer myPlayer, Map<Student, String> professors) {
        myStudentsBoard.getChildren().clear();

        var l = new Label("Entrata");
        l.setId("my_students_board_small_label");
        myStudentsBoard.add(l, 2, 1);

        l = new Label("Sala");
        l.setId("my_students_board_small_label");
        myStudentsBoard.add(l, 2, 3);

        for (Student s : Student.values()) {
            var sv = new StudentView(s, myNickname.equals(professors.get(s)));
            sv.setFitWidth(40);
            sv.setFitHeight(40);
            sv.setId("selectable_student_view");
            sv.setOnMouseClicked(e -> placeStudentInSchool(s));
            myStudentsBoard.add(sv, s.ordinal(), 0);

            var entranceLabel = new Label(myPlayer.entrance().getCountForStudent(s) + "");
            entranceLabel.setId("my_students_board_label");
            myStudentsBoard.add(entranceLabel, s.ordinal(), 2);

            var schoolLabel = new Label(myPlayer.school().getCountForStudent(s) + "");
            schoolLabel.setId("my_students_board_label");
            myStudentsBoard.add(schoolLabel, s.ordinal(), 4);
        }
    }

    private boolean checkIfAllStudentsPlaced(){
        var count = 0;
        count += studentsPlacedInSchool.getSize();
        for(StudentsContainer sc : studentsPlacedInIslands.values())
            count += sc.getSize();

        var studentsToMove = currentGame.numberOfPlayers() == 2 ?
                Constants.TwoPlayers.STUDENTS_TO_MOVE : Constants.ThreePlayers.STUDENTS_TO_MOVE;

        if(count == studentsToMove) {
            Client.getInstance()
                    .forwardGameRequest(
                            new PlaceStudentsRequest(
                                    studentsPlacedInSchool,
                                    studentsPlacedInIslands
                            ),
                            () -> {
                            },
                            err -> Utils.LOGGER.info("Error placing students: " + err)
                    );
            return true;
        } else {
            setInfoString(InfoStrings.MY_TURN_PLACE_STUDENTS, studentsToMove-count);
            setMyStudentsBoard(findMyPlayer(currentGame), currentGame.currentProfessors());
        }
        return false;
    }

    private void placeStudentInSchool(Student s) {
        var myPlayer = findMyPlayer(currentGame);

        if(myPlayer.entrance().getCountForStudent(s) <= 0) return;

        myPlayer.entrance().removeStudent(s);
        myPlayer.school().addStudent(s);

        studentsPlacedInSchool.addStudent(s);

        checkIfAllStudentsPlaced();
    }

    private void placeStudentInIsland(Student s, IslandView islandView) {
        var myPlayer = findMyPlayer(currentGame);

        if(myPlayer.entrance().getCountForStudent(s) <= 0) return;

        myPlayer.entrance().removeStudent(s);

        //store student in island
        var index = islandView.getIndex();
        var oldContainer = studentsPlacedInIslands.get(index);
        if(oldContainer != null)
            oldContainer.addStudent(s);
        else
            studentsPlacedInIslands.put(index, new StudentsContainer().addStudent(s));

        //update island view
        islandView.addStudent(s);

        checkIfAllStudentsPlaced();
    }

    public void setIslands(List<ReducedIsland> islands) {
        islandsPane.getChildren().clear();
        islands.forEach(i -> islandsPane.addIsland(i));
    }

    public void setMyCoin(int coin) {
        myCoinLabel.setText(coin + "");
    }

    @Override
    public void onShow() {
        Client.getInstance().addGameUpdateListener(this);
        myNickname = Client.getInstance().getNickname();
        leaveButton.setText("ABBANDONA");
        gameTitlePopup.hide();
    }

    @Override
    public void onHide() {
        Client.getInstance().removeGameUpdateListener(this);
    }

    @Override
    public void onGameUpdate(ReducedGame game) {
        Platform.runLater(() -> gameUpdate(game));
    }

    private void gameUpdate(ReducedGame game) {
        currentGame = game;
        var myPlayer = findMyPlayer(game);
        var otherPlayers = new ArrayList<>(game.players());
        otherPlayers.remove(myPlayer);
        otherPlayers.sort(Comparator.comparing(ReducedPlayer::nickname));

        characterCards.setDisable(true);
        cloudsPane.setDisable(true);
        myStudentsBoard.setDisable(true);
        studentsPlacedInSchool = new StudentsContainer();
        studentsPlacedInIslands = new HashMap<>();

        setVisibilityForNumberOfPlayers(game.numberOfPlayers());
        setVisibilityForExpertMode(game.expertMode());
        setIslands(game.islands());
        setMotherNaturePosition(game.motherNaturePosition());
        cloudsPane.setClouds(game.currentRound().clouds());
        setMyBoard(myPlayer, game.currentProfessors());

        myPlayerBoardView.setPlayer(myPlayer);

        player2BoardView.setPlayer(otherPlayers.get(0));
        player2BoardView.setProfessors(game.currentProfessors());

        //TODO: improve
        //set card played by me
        var cardPlayedByMe = game.currentRound()
                .playedAssistantCards()
                .get(myPlayer.nickname());
        myPlayerBoardView.setPlayedCard(cardPlayedByMe);

        //set card played by player 2
        var cardPlayedByPlayer2 = game.currentRound()
                .playedAssistantCards()
                .get(otherPlayers.get(0).nickname());
        player2BoardView.setPlayedCard(cardPlayedByPlayer2);

        //set player if present
        if (game.numberOfPlayers() > 2) {
            player3BoardView.setPlayer(otherPlayers.get(1));
            player3BoardView.setProfessors(game.currentProfessors());

            //set card played by player 3
            var cardPlayedByPlayer3 = game.currentRound()
                    .playedAssistantCards()
                    .get(otherPlayers.get(1).nickname());
            player3BoardView.setPlayedCard(cardPlayedByPlayer3);
        }

        switch(game.currentState()) {
            case CREATED, STARTED -> gameTitlePopup.hide();
            case PAUSED -> {
                var offlinePlayers = "Giocatori offline: " +
                        game.players()
                                .stream()
                                .filter(p -> !p.isConnected())
                                .map(ReducedPlayer::nickname)
                                .collect(Collectors.joining(", "));

                gameTitlePopup.setState(GameTitlePopupView.State.PAUSED, offlinePlayers);
                gameTitlePopup.show();
            }
            case FINISHED -> {
                if(game.winner() == null) { //tie
                    gameTitlePopup.setState(GameTitlePopupView.State.TIE, "");
                } else {
                    if(game.winner().equals(myNickname)) {
                        gameTitlePopup.setState(GameTitlePopupView.State.WIN, "");
                    } else {
                        gameTitlePopup.setState(GameTitlePopupView.State.LOSE, game.winner()+ " ha vinto");
                    }
                }
                leaveButton.setText("VAI AL MENU PRINCIPALE");
                gameTitlePopup.show();
            }
            case TERMINATED -> {
                var leftPlayers = "Giocatori che hanno abbandonato: " +
                        game.players()
                                .stream()
                                .filter(p -> !p.isConnected())
                                .map(ReducedPlayer::nickname)
                                .collect(Collectors.joining(", "));

                gameTitlePopup.setState(GameTitlePopupView.State.TERMINATED, leftPlayers);
                leaveButton.setText("VAI AL MENU PRINCIPALE");
                gameTitlePopup.show();
            }
        }

        var currentPlayer = game.currentRound().currentPlayer();

        //my turn
        if (currentPlayer.equals(myPlayer.nickname())) {
            if (game.currentRound().stage() instanceof Stage.Attack s) { //attack
                var maxMotherNatureSteps =
                        cardPlayedByMe.motherNatureMaxMoves() +
                                game.currentRound().additionalMotherNatureMoves();

                switch (s) {
                    case STARTED -> {
                        //place students...
                        var studentsToMove =
                                game.numberOfPlayers() == 2 ?
                                        Constants.TwoPlayers.STUDENTS_TO_MOVE :
                                        Constants.ThreePlayers.STUDENTS_TO_MOVE;

                        setInfoString(InfoStrings.MY_TURN_PLACE_STUDENTS, studentsToMove);
                        myStudentsBoard.setDisable(false);
                        setIslandsForPlacingStudents();
                    }
                    case STUDENTS_PLACED -> {
                        //play character card or move mother nature
                        setInfoString(InfoStrings.MY_TURN_PLAY_CHARACTER_CARD);
                        characterCards.setDisable(false);

                        setMotherNaturePossibleSteps(game.motherNaturePosition(), maxMotherNatureSteps);
                    }
                    case CARD_PLAYED -> {
                        //move mother nature
                        setInfoString(InfoStrings.MY_TURN_MOVE_MOTHER_NATURE, maxMotherNatureSteps);
                        setMotherNaturePossibleSteps(game.motherNaturePosition(), maxMotherNatureSteps);
                    }
                    case MOTHER_NATURE_MOVED -> {
                        //select cloud
                        setInfoString(InfoStrings.MY_TURN_SELECT_CLOUD);
                        cloudsPane.setDisable(false);
                    }
                    case SELECTED_CLOUD -> {}//do nothing
                }
            } else { //plan
                setInfoString(InfoStrings.MY_TURN_PLAY_ASSISTANT_CARD);
                setAssistantDeckVisibility(true);
            }
        } else {
            setInfoString(InfoStrings.OTHER_PLAYER_WAIT_FOR_HIS_TURN, currentPlayer);
        }
    }

    private void setIslandsForPlacingStudents() {
        for (var n : islandsPane.getChildren()) {
            var iv = (IslandView) n;
            iv.setDisable(false);

            iv.setOnMouseClicked(e -> {
                if(studentSelectContextMenu != null)
                    studentSelectContextMenu.hide();

                studentSelectContextMenu = new StudentSelectContextMenu(
                        findMyPlayer(currentGame).entrance(),
                        s -> placeStudentInIsland(s, iv)
                );

                studentSelectContextMenu.show(iv, e.getScreenX(), e.getScreenY());
            });
        }
    }

    private void setMyBoard(ReducedPlayer myPlayer, Map<Student, String> professors) {
        setMyStudentsBoard(myPlayer, professors);
        setMyCoin(myPlayer.coins());
        setMyTowers(myPlayer.towerColor(), myPlayer.towersCount());
        setAssistantCardsDeck(myPlayer.deck());
    }

    private ReducedPlayer findMyPlayer(ReducedGame game) {
        return game.players()
                .stream()
                .filter(p -> p.nickname().equals(myNickname))
                .findFirst()
                .orElse(null);
    }

    /**
     * This method hide temporarily the assistant card deck to show the game
     */
    public void startPeekGame() {
        assistantCardsLayer.setOpacity(0.1);
    }

    /**
     * This method is called after the startPeekGame to show again the assistant card deck
     */
    public void endPeekGame() {
        assistantCardsLayer.setOpacity(1.0);
    }
}
