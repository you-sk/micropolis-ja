package micropolisj.gui;

import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.awt.image.IndexColorModel;
import java.util.ResourceBundle;
import javax.imageio.ImageIO;
import javax.swing.*;

/** A short, skippable original title card. No sleeping or loading on the Swing event thread. */
public final class SplashWindow {
  private static final int WIDTH = 320;
  private static final int HEIGHT = 200;

  private SplashWindow() {}

  public static BufferedImage loadArtwork() {
    if (!Boolean.parseBoolean(System.getProperty("micropolis.splash", "true"))) return null;
    try (var input = SplashWindow.class.getResourceAsStream("/images/splash-city.png")) {
      if (input == null) return null;
      BufferedImage source = ImageIO.read(input);
      if (source == null) return null;
      int[] palette = {
        0x080d32, 0x111747, 0x1e2453, 0x35325b, 0x53426c, 0x7a526e, 0xa3697d, 0xc4838c, 0xe0a7a0,
        0xf2cc9b, 0xffe1a6, 0xe9ac5e, 0xb67d49, 0x795c48, 0x47413e, 0x092b35, 0x0d4549, 0x126264,
        0x25817b, 0x4b9b8b, 0x123827, 0x31582d, 0x598145, 0x8f994e
      };
      IndexColorModel colors =
          new IndexColorModel(
              8, palette.length, palette, 0, false, -1, java.awt.image.DataBuffer.TYPE_BYTE);
      BufferedImage result =
          new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_BYTE_INDEXED, colors);
      Graphics2D g = result.createGraphics();
      g.setRenderingHint(
          RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
      g.drawImage(source, 0, 0, WIDTH, HEIGHT, null);
      g.setColor(new Color(0x07152e));
      g.setFont(new Font(Font.MONOSPACED, Font.BOLD, 27));
      String title = "MICROPOLIS";
      int x = (WIDTH - g.getFontMetrics().stringWidth(title)) / 2;
      g.drawString(title, x + 1, 58);
      g.setColor(new Color(0xffd78a));
      g.drawString(title, x, 56);
      g.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 8));
      String subtitle = "JAPANESE EDITION";
      g.drawString(subtitle, (WIDTH - g.getFontMetrics().stringWidth(subtitle)) / 2, 70);
      g.dispose();
      return result;
    } catch (Exception e) {
      // Artwork is optional; failure must not prevent starting the game.
      return null;
    }
  }

  public static void show(BufferedImage artwork, Runnable next) {
    if (!SwingUtilities.isEventDispatchThread())
      throw new IllegalStateException("Swing thread required");
    if (artwork == null || GraphicsEnvironment.isHeadless()) {
      next.run();
      return;
    }
    ResourceBundle strings = ResourceBundle.getBundle("strings.GuiStrings");
    JFrame window = new JFrame("Micropolis — Startup");
    window.setUndecorated(true);
    window.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
    Rectangle screen = window.getGraphicsConfiguration().getBounds();
    Insets insets = Toolkit.getDefaultToolkit().getScreenInsets(window.getGraphicsConfiguration());
    int availableWidth = screen.width - insets.left - insets.right;
    int availableHeight = screen.height - insets.top - insets.bottom;
    int scale = Math.min(3, Math.min(availableWidth / WIDTH, availableHeight / HEIGHT));
    if (scale < 1) {
      window.dispose();
      next.run();
      return;
    }
    int duration = Integer.getInteger("micropolis.splashMillis", 1800);
    duration = Math.max(300, Math.min(60000, duration));
    final int millis = duration;
    long start = System.nanoTime();
    JPanel panel =
        new JPanel() {
          @Override
          protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D g = (Graphics2D) graphics.create();
            long elapsed = (System.nanoTime() - start) / 1_000_000;
            float alpha =
                Math.max(0, Math.min(1, Math.min(elapsed / 200f, (millis - elapsed) / 200f)));
            g.setComposite(AlphaComposite.SrcOver.derive(alpha));
            g.setRenderingHint(
                RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            g.drawImage(artwork, 0, 0, getWidth(), getHeight(), null);
            g.setFont(UIManager.getFont("Label.font"));
            g.setColor(new Color(0xffe6b5));
            String hint = strings.getString("splash.skip");
            int x = Math.max(4, (getWidth() - g.getFontMetrics().stringWidth(hint)) / 2);
            g.setColor(new Color(0x07152e));
            g.fillRect(0, getHeight() - 30, getWidth(), 30);
            g.setColor(new Color(0xffe6b5));
            g.drawString(hint, x, getHeight() - 10);
            g.dispose();
          }
        };
    panel.setBackground(Color.BLACK);
    panel.setPreferredSize(new Dimension(WIDTH * scale, HEIGHT * scale));
    panel.getAccessibleContext().setAccessibleName(strings.getString("splash.accessible"));
    window.setContentPane(panel);
    window.pack();
    window.setLocation(
        screen.x + insets.left + (availableWidth - window.getWidth()) / 2,
        screen.y + insets.top + (availableHeight - window.getHeight()) / 2);
    Timer timer = new Timer(25, null);
    KeyEventDispatcher[] dispatcher = new KeyEventDispatcher[1];
    boolean[] finished = {false};
    Runnable finish =
        () -> {
          if (finished[0]) return;
          finished[0] = true;
          timer.stop();
          KeyboardFocusManager.getCurrentKeyboardFocusManager()
              .removeKeyEventDispatcher(dispatcher[0]);
          window.dispose();
          SwingUtilities.invokeLater(next);
        };
    dispatcher[0] =
        e -> {
          if (e.getID() == KeyEvent.KEY_PRESSED && window.isFocused()) {
            finish.run();
            return true;
          }
          return false;
        };
    KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(dispatcher[0]);
    panel.addMouseListener(
        new MouseAdapter() {
          @Override
          public void mousePressed(MouseEvent e) {
            finish.run();
          }
        });
    window.addWindowListener(
        new WindowAdapter() {
          @Override
          public void windowClosing(WindowEvent e) {
            finish.run();
          }
        });
    timer.addActionListener(
        e -> {
          if ((System.nanoTime() - start) / 1_000_000 >= millis) finish.run();
          else panel.repaint();
        });
    window.setVisible(true);
    window.requestFocus();
    timer.start();
  }
}
