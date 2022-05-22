package it.polimi.ingsw.client.cli.views;

import org.fusesource.jansi.Ansi;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;

public class ListView<T> extends BaseView{

    private List<T> listItems;
    private BiConsumer<T, Integer> listener;
    private SimpleInputView inputView;
    private String title;
    private String listEmpty;    
    private final Function<T, Ansi> customRenderer;

    public ListView(Function<T, Ansi> customRenderer) {
        this(new ArrayList<>(), customRenderer, "", "Scegli opzione", "N/A");
    }

    public ListView(List<T> listItems, Function<T, Ansi> customRenderer, String title, String prompt, String listEmpty){
        super();
        this.title = title;
        this.listEmpty = listEmpty;
        this.listItems = listItems;
        this.inputView = new SimpleInputView(prompt);
        this.customRenderer = customRenderer;
    }

    @Override
    public void draw() {
        cursor.clearScreen();
        cursor.printCentered(title, 1);
        if(listItems.isEmpty()){
            cursor.printCentered(listEmpty, 10);
            cursor.moveToXY(1, 23);
        } else {
            this.inputView.setListener(input -> {
                if(listener == null) return;

                try {
                    int index = Integer.parseInt(input) - 1;

                    if (!(index >= 0 && index <= listItems.size()))
                        inputView.showError("Selezione non valida");

                    listener.accept(listItems.get(index), index);
                } catch (NumberFormatException e) {
                    inputView.showError("Selezione non valida");
                }
            });

            for (int i = 0; i < Math.min(listItems.size(), 10); i++) {
                cursor.printCentered(
                        String.format("[%01d] %s", i + 1, customRenderer.apply(listItems.get(i))),
                        3 + i * 2
                );
                cursor.moveRelative(0, 1);
            }

            inputView.draw();
        }
    }

    public void setListItems(List<T> listItems) {
        this.listItems = listItems;
        draw();
    }

    public void showError(String error) {
        inputView.showError(error);
    }

    public void setListener(BiConsumer<T, Integer> listener) {
        this.listener = listener;
    }
}
