package it.polimi.ingsw.client.gui;

import it.polimi.ingsw.Utils;
import it.polimi.ingsw.client.Client;
import javafx.application.Application;
import javafx.stage.Stage;

public class GUI extends Application {

    @Override
    public void start(Stage stage) {
        //initialize GUI client
        Client.init(stage);

        stage.setMinWidth(1600);
        stage.setMinHeight(900);

        if(Utils.isWindows())
            stage.setFullScreen(true);
        else
            stage.setMaximized(true);

        stage.setTitle("Eriantys");

        stage.show();
    }

    @Override
    public void stop() throws Exception {
        super.stop();
        System.exit(0);
    }
}
