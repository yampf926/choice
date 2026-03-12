public class MainSmokeTest {
    public static void main(String[] args) throws Exception {
        javax.swing.SwingUtilities.invokeAndWait(() -> {
            Main main = new Main();
            main.dispose();
        });
    }
}