package it.polimi.ingsw.client.cli.views;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.fusesource.jansi.Ansi.ansi;

public class CommandInputView extends BaseView {

    private final Map<Command, CommandListener> listeners = new HashMap<>(); // keyword -> listener
    private Thread thread;

    @Override
    public void draw() {
        cursor.clearRow(22);
        cursor.clearRow(23);

        cursor.print("Scegli opzione [", 1, 22);

        var first = false;
        for (var c : listeners.keySet()) {
            if(first)
                cursor.print(" | ");
            cursor.print(ansi().fgBrightYellow().a(c.triggerWord).reset());
            cursor.print(": ");
            cursor.print(c.description);
            first = true;
        }

        cursor.print("]");
        cursor.moveToXY(1, 23);

        //TODO: check if new thread is necessary
        thread = new Thread(() -> {
            var cmd = cursor.input();
            try {
                var elements = Arrays.asList(cmd.split(" "));

                var commandListeners = listeners.entrySet().stream()
                        .filter(e -> e.getKey().triggerWord.equals(elements.get(0)))
                        .map(Map.Entry::getValue)
                        .toList();

                if(commandListeners.isEmpty())
                    showError("Comando non riconosciuto");
                else
                    commandListeners.forEach(l -> l.onCommand(
                            elements.get(0),
                            elements.subList(1, elements.size())
                    ));

            } catch (Exception e) {
                showError("Comando non riconosciuto");
            }
        });
        thread.start();
    }

    public void showError(String error) {
        error += " [Premi invio per riprovare]";
        cursor.print(ansi().fgRed().a(error).reset(), 1, 23);
        cursor.input();
        draw();
    }

    public void addCommandListener(String triggerWord, String description, CommandListener listener) {
        this.listeners.put(new Command(triggerWord, description), listener);
    }

    public void removeCommandListener(String triggerWord) {
        this.listeners.entrySet().removeIf(e -> e.getKey().triggerWord.equals(triggerWord));
    }

    public void stop() {
        try {
            thread.interrupt();
        } catch (Exception e) {
        }
    }

    public interface CommandListener {
        void onCommand(String command, List<String> args);
    }

    private record Command(String triggerWord, String description) { }
}
