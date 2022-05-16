package it.polimi.ingsw.client.gui;

import it.polimi.ingsw.Utils;
import it.polimi.ingsw.client.NavigationManager;
import it.polimi.ingsw.client.Screen;
import it.polimi.ingsw.client.ScreenController;
import javafx.animation.FadeTransition;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.paint.Paint;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.util.Arrays;
import java.util.Map;
import java.util.Stack;
import java.util.concurrent.ConcurrentHashMap;

public class GUINavigationManager implements NavigationManager {

    private final Stage stage;

    private Scene scene;

    private final Stack<BackstackEntry> backstack;

    private final Map<Screen, Parent> screens;

    private final Map<Screen, ScreenController> screenControllers;

    private boolean currentlyNavigating = false;

    private Screen currentScreen;

    public GUINavigationManager(Stage stage) {
        this.stage = stage;
        this.backstack = new Stack<>();
        this.screens = new ConcurrentHashMap<>();
        this.screenControllers = new ConcurrentHashMap<>();

        //load first screen
        screens.put(Screen.SERVER_CONNECTION_MENU, loadScreen(Screen.SERVER_CONNECTION_MENU));

        //load other screens in background thread
        new Thread(() -> {
            for (Screen s : Arrays.stream(Screen.values()).filter(s -> s != Screen.SERVER_CONNECTION_MENU).toList()) {
                screens.put(s, loadScreen(s));
            }
            Utils.LOGGER.info("Finished loading screens");
        }).start();
    }

    private Parent loadScreen(Screen screen) {
        try {
            var loader = new FXMLLoader(getClass().getResource("/fxml/" + screen.name().toLowerCase() + ".fxml"));
            var root = (Parent) loader.load();
            var controller = (ScreenController) loader.getController();
            screenControllers.put(screen, controller);
            controller.onCreate();

            return root;
        } catch (Exception e) {
            e.printStackTrace();
            return new Label("Error loading screen " + screen.name() + ": " + e.getMessage());
        }
    }

    @Override
    public void navigateTo(Screen destination) {
        navigateTo(destination, true);
    }

    @Override
    public void navigateTo(Screen destination, boolean withBackStack) {
        if(currentlyNavigating) return;

        //just reset the screen if we're already on it
        if(destination == currentScreen) {
            screenControllers.get(destination).onHide();
            screenControllers.get(destination).onShow();
            return;
        }

        Utils.LOGGER.info("Navigate to destination " + destination.name());

        var lastScreen = currentScreen;

        var newRoot = screens.get(destination);
        newRoot.setOpacity(1.0);

        if (scene == null) {
            scene = new Scene(newRoot);
            scene.setFill(Paint.valueOf("#000000"));
            stage.setScene(scene);

            currentScreen = destination;
            currentlyNavigating = false;

            if(lastScreen != null)
                screenControllers.get(lastScreen).onHide();
            screenControllers.get(destination).onShow();
        } else {
            currentlyNavigating = true;

            var ft = new FadeTransition(new Duration(200), scene.getRoot());
            ft.setFromValue(1.0);
            ft.setToValue(0.0);
            ft.setOnFinished(event -> {
                if(withBackStack && currentScreen != null)
                    backstack.push(new BackstackEntry(currentScreen, scene.getRoot()));
                scene.setRoot(newRoot);

                currentScreen = destination;
                currentlyNavigating = false;

                if(lastScreen != null)
                    screenControllers.get(lastScreen).onHide();
                screenControllers.get(destination).onShow();
            });

            ft.play();
        }
    }

    @Override
    public void clearBackStack() {
        backstack.clear();
    }

    @Override
    public void goBack() {
        if(currentlyNavigating) return;

        if (!backstack.isEmpty()) {
            currentlyNavigating = true;

            if(currentScreen != null)
                screenControllers.get(currentScreen).onHide();
            screenControllers.get(backstack.peek().screen()).onShow();

            scene.setRoot(backstack.peek().root());

            var ft = new FadeTransition(new Duration(200), scene.getRoot());
            ft.setFromValue(0.0);
            ft.setToValue(1.0);
            ft.setOnFinished(event -> {
                currentScreen = backstack.pop().screen();

                currentlyNavigating = false;

            });
            ft.play();
        }
        Utils.LOGGER.info("Go back");
    }

    @Override
    public void exitApp() {
        stage.close();
        Platform.exit();
        System.exit(0);
    }

    @Override
    public Screen getCurrentScreen() {
        return currentScreen;
    }

    private record BackstackEntry(Screen screen, Parent root) { }

}