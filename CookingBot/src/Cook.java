import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.event.KeyEvent;
import java.util.concurrent.TimeUnit;

import org.osbot.rs07.api.map.Area;
import org.osbot.rs07.api.map.Position;
import org.osbot.rs07.api.model.RS2Object;
import org.osbot.rs07.api.ui.Message;
import org.osbot.rs07.api.ui.Skill;
import org.osbot.rs07.script.Script;
import org.osbot.rs07.script.ScriptManifest;

@ScriptManifest(
        author = "Spookydevs",
        info = "Cooks fish at Al Kharid and banks automatically",
        name = "Al Kharid Cooker",
        version = 1.1,
        logo = ""
)
public class Cook extends Script {

    private Gui gui;
    private int itemID;
    private boolean itemSelected = false;

    private int fishCooked = 0;
    private int fishBurnt = 0;
    private long timeBegan;
    private int beginningXP;

    private static final Area BANK_AREA = new Area(3269, 3171, 3273, 3167);
    private static final Area RANGE_AREA = new Area(3273, 3181, 3276, 3179);

    private enum State {
        COOK,
        BANK,
        WALK_TO_RANGE,
        WAIT
    }

    @Override
    public void onStart() {
        log("Al Kharid Cooker Started");
        gui = new Gui();
        timeBegan = System.currentTimeMillis();
        beginningXP = skills.getExperience(Skill.COOKING);
    }

    @Override
    public void onMessage(Message message) {
        String text = message.getMessage().toLowerCase();
        if (text.contains("burn")) fishBurnt++;
        if (text.contains("successfully cook") || text.contains("roast") || text.contains("manage to cook")) fishCooked++;
    }

    private State getState() {
        if (!gui.isFinished()) return State.WAIT;

        if (!itemSelected) {
            switch (gui.getKeuze()) {
                case "Salmon": itemID = 331; break;
                case "Shrimp": itemID = 317; break;
                case "Tuna": itemID = 359; break;
                case "Trout": itemID = 335; break;
                case "Swordfish": itemID = 371; break;
                case "Anchovies": itemID = 321; break;
                case "Lobster": itemID = 377; break;
                default:
                    log("No valid fish selected. Stopping script.");
                    stop();
            }
            itemSelected = true;
        }

        // If already cooking, just wait (highest priority)
        if (myPlayer().isAnimating()) return State.WAIT;

        // If out of fish, bank
        if (!inventory.contains(itemID)) return State.BANK;

        // If not at range, walk there
        if (!RANGE_AREA.contains(myPlayer())) return State.WALK_TO_RANGE;

        // If at range, not animating, and no dialogue open → cook
        if (!getDialogues().inDialogue()) return State.COOK;

        return State.WAIT;
    }

    @Override
    public int onLoop() throws InterruptedException {
        switch (getState()) {
            case COOK:
                cook();
                break;
            case BANK:
                bank();
                break;
            case WALK_TO_RANGE:
                walkToRange();
                break;
            case WAIT:
                sleep(random(600, 1200));
                break;
        }
        return random(250, 350);
    }

    private void cook() throws InterruptedException {
        RS2Object range = objects.closest("Range");
        if (range == null || !range.exists()) return;

        // Walk if more than 1 tile away
        if (myPlayer().getPosition().distance(range.getPosition()) > 1) {
            walking.walk(range.getPosition());
            while (myPlayer().isMoving()) sleep(100);
        }

        // Ensure range is on screen
        if (range.getPosition() == null || range.getPosition().getPolygon(getBot()) == null) {
            getCamera().toEntity(range);
            sleep(random(200, 400));
        }

        // Interact if range polygon exists
        if (range.getPosition() != null && range.getPosition().getPolygon(getBot()) != null) {
            if (range.interact("Cook")) {
                // Wait for production interface
                long start = System.currentTimeMillis();
                while (!getDialogues().inDialogue() && System.currentTimeMillis() - start < 5000) {
                    sleep(100);
                }

                if (getDialogues().inDialogue()) {
                    log("Production interface detected, pressing space to start cooking.");
                    getKeyboard().pressKey(KeyEvent.VK_SPACE);
                    sleep(random(500, 800));
                }

                // Wait until player starts animating
                long animStart = System.currentTimeMillis();
                while (!myPlayer().isAnimating() && System.currentTimeMillis() - animStart < 5000) {
                    sleep(100);
                }
            }
        }
    }

    private void bank() throws InterruptedException {
        RS2Object booth = objects.closest("Bank booth");

        if (!bank.isOpen() && booth != null) {
            booth.interact("Bank");
            sleep(random(2500, 3300));
        }

        if (bank.isOpen()) {
            bank.depositAll();
            sleep(random(500, 800));

            if (!bank.withdraw(itemID, 28)) {
                log("No more fish found. Stopping script.");
                stop();
            }
        }
    }

    private void walkToRange() throws InterruptedException {
        RS2Object range = objects.closest("Range");
        if (range == null || !range.exists()) return;

        // Pick a random tile inside the RANGE_AREA
        Position randomTile = RANGE_AREA.getRandomPosition();
        
        walking.walk(randomTile);
        while (myPlayer().isMoving()) sleep(100);

        // Small random delay
        sleep(random(300, 700));
    }


    private long[] formatTime(long duration) {
        long hours = TimeUnit.MILLISECONDS.toHours(duration);
        long minutes = TimeUnit.MILLISECONDS.toMinutes(duration) % 60;
        long seconds = TimeUnit.MILLISECONDS.toSeconds(duration) % 60;
        return new long[]{hours, minutes, seconds};
    }

    @Override
    public void onPaint(Graphics2D g) {
        int xpGained = skills.getExperience(Skill.COOKING) - beginningXP;
        long runtime = System.currentTimeMillis() - timeBegan;
        int perHour = (int) (fishCooked / (runtime / 3600000.0));
        long[] t = formatTime(runtime);

        // --- Dimensions for banner above chatbox ---
        int chatboxWidth = 510;   // width of OSRS chatbox
        int chatboxHeight = 165;  // height of OSRS chatbox
        int boxHeight = 70;       // your banner height
        int margin = 5;           // space above chatbox

        int boxX = 5; // align with left edge of chatbox
        int boxY = 512 - chatboxHeight - boxHeight - margin; // 512 is client height
        int boxWidth = chatboxWidth;

        // Background
        g.setColor(new Color(0, 0, 0, 150)); // semi-transparent black
        g.fillRect(boxX, boxY, boxWidth, boxHeight);

        // Border
        g.setColor(Color.WHITE);
        g.drawRect(boxX, boxY, boxWidth, boxHeight);

        // Rainbow title above the box
        double time = System.currentTimeMillis() / 200.0;
        int r = (int) (128 + 127 * Math.sin(time));
        int gCol = (int) (128 + 127 * Math.sin(time + 2));
        int b = (int) (128 + 127 * Math.sin(time + 4));
        g.setColor(new Color(r, gCol, b));
        g.drawString("Al Kharid Cooker v1.1", boxX + 10, boxY - 10);

        // Stats inside banner
        g.setColor(Color.YELLOW);
        int textY = boxY + 25;
        g.drawString("Cooked: " + fishCooked, boxX + 20, textY);
        g.drawString("Burned: " + fishBurnt, boxX + 150, textY);
        g.drawString("XP: " + xpGained, boxX + 280, textY);
        g.drawString("p/h: " + perHour, boxX + 400, textY);

        g.drawString(String.format("Time: %02d:%02d:%02d", t[0], t[1], t[2]), boxX + 20, textY + 20);

        // Optional: highlight range and bank booth
        try {
            RS2Object range = objects.closest("Range");
            RS2Object booth = objects.closest("Bank booth");

            if (range != null && range.getPosition() != null && range.getPosition().getPolygon(getBot()) != null) {
                g.setColor(new Color(255, 0, 0, 180));
                g.draw(range.getPosition().getPolygon(getBot()));
            }

            if (booth != null && booth.getPosition() != null && booth.getPosition().getPolygon(getBot()) != null) {
                g.setColor(new Color(0, 255, 0, 180));
                g.draw(booth.getPosition().getPolygon(getBot()));
            }
        } catch (Exception e) {
            // Prevent paint crash
        }
    }

    @Override
    public void onExit() {
        log("Script stopped.");
        log("Fish cooked: " + fishCooked);
        log("Fish burned: " + fishBurnt);
    }
}
