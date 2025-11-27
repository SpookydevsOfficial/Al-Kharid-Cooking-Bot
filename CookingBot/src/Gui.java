import javax.swing.*;

public class Gui {

    private String choice = "Shrimp";
    private boolean finished = false;

    public Gui() {
        String[] options = {"Shrimp", "Trout", "Salmon", "Tuna", "Swordfish", "Anchovies", "Lobster"};
        choice = (String) JOptionPane.showInputDialog(null,
                "Select the fish to cook:",
                "Fish Cooker",
                JOptionPane.PLAIN_MESSAGE,
                null,
                options,
                options[0]);
        finished = true;
    }

    public String getKeuze() {
        return choice;
    }

    public boolean isFinished() {
        return finished;
    }
}
