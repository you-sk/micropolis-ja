package micropolisj.gui;

import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.time.LocalDate;
import java.util.ResourceBundle;
import java.util.function.BiConsumer;
import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import micropolisj.engine.Micropolis;

/** Persistent seasonal title menu. Artwork IO happens before entering the Swing thread. */
public final class SplashWindow {
  private static final int WIDTH = 480;
  private static final int HEIGHT = 300;

  private SplashWindow() {}

  static String seasonForMonth(int month) {
    if (month < 1 || month > 12) throw new IllegalArgumentException("Month must be 1..12");
    return switch (month) {
      case 3, 4, 5 -> "spring";
      case 6, 7, 8 -> "summer";
      case 9, 10, 11 -> "autumn";
      default -> "winter";
    };
  }

  public static BufferedImage loadArtwork() {
    return loadArtwork(LocalDate.now().getMonthValue());
  }

  static BufferedImage loadArtwork(int month) {
    if (!Boolean.parseBoolean(System.getProperty("micropolis.splash", "true"))) return null;
    try (var input =
        SplashWindow.class.getResourceAsStream("/images/title/" + seasonForMonth(month) + ".png")) {
      if (input == null) return null;
      BufferedImage source = ImageIO.read(input);
      if (source == null) return null;
      BufferedImage result = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
      Graphics2D g = result.createGraphics();
      g.setRenderingHint(
          RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
      g.drawImage(source, 0, 0, WIDTH, HEIGHT, null);
      g.dispose();
      return result;
    } catch (Exception e) {
      // Optional artwork must never prevent starting the game.
      return null;
    }
  }

  public static void show(BufferedImage artwork, Runnable next) {
    show(artwork, next, (engine, file) -> next.run(), () -> {});
  }

  public static void show(
      BufferedImage artwork, Runnable next, BiConsumer<Micropolis, File> loaded, Runnable quit) {
    if (!SwingUtilities.isEventDispatchThread())
      throw new IllegalStateException("Swing thread required");
    if (artwork == null || GraphicsEnvironment.isHeadless()) {
      next.run();
      return;
    }
    ResourceBundle strings = ResourceBundle.getBundle("strings.GuiStrings");
    JFrame window = new JFrame(strings.getString("splash.accessible"));
    window.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
    Rectangle screen = window.getGraphicsConfiguration().getBounds();
    Insets insets = Toolkit.getDefaultToolkit().getScreenInsets(window.getGraphicsConfiguration());
    int availableWidth = screen.width - insets.left - insets.right;
    int availableHeight = screen.height - insets.top - insets.bottom;
    int scale =
        Math.min(2, Math.min((availableWidth - 24) / WIDTH, (availableHeight - 64) / HEIGHT));
    if (scale < 1) {
      window.dispose();
      next.run();
      return;
    }
    JPanel panel =
        new JPanel(null) {
          @Override
          protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D g = (Graphics2D) graphics.create();
            g.scale(scale, scale);
            g.setRenderingHint(
                RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            g.drawImage(artwork, 0, 0, null);
            paintSign(g);
            g.dispose();
          }
        };
    panel.setPreferredSize(new Dimension(WIDTH * scale, HEIGHT * scale));
    panel.getAccessibleContext().setAccessibleName(strings.getString("splash.accessible"));
    JLabel title = new JLabel("MICROPOLIS", SwingConstants.CENTER);
    title.setForeground(new Color(0xf4efdb));
    title.setFont(new Font(Font.SERIF, Font.BOLD, 33 * scale));
    title.setBounds(65 * scale, 49 * scale, 350 * scale, 47 * scale);
    panel.add(title);
    JLabel subtitle = new JLabel(strings.getString("title.subtitle"), SwingConstants.CENTER);
    subtitle.setForeground(new Color(0xd6dfcc));
    subtitle.setBounds(70 * scale, 96 * scale, 340 * scale, 18 * scale);
    subtitle.setFont(new Font(Font.DIALOG, Font.PLAIN, 10 * scale));
    panel.add(subtitle);
    boolean[] finished = {false};
    Runnable finish =
        () -> {
          if (finished[0]) return;
          finished[0] = true;
          window.dispose();
        };
    JButton newCity = button(strings.getString("title.new"), scale);
    newCity.setBounds(90 * scale, 125 * scale, 300 * scale, 25 * scale);
    newCity.addActionListener(
        e -> {
          if (finished[0]) return;
          finish.run();
          SwingUtilities.invokeLater(next);
        });
    panel.add(newCity);
    JButton load = button(strings.getString("title.load"), scale);
    load.setBounds(90 * scale, 158 * scale, 300 * scale, 25 * scale);
    load.addActionListener(
        e -> {
          JFileChooser chooser = new JFileChooser();
          chooser.setFileFilter(new FileNameExtensionFilter(strings.getString("cty_file"), "cty"));
          if (chooser.showOpenDialog(window) != JFileChooser.APPROVE_OPTION) return;
          try {
            Micropolis engine = new Micropolis();
            engine.load(chooser.getSelectedFile());
            loaded.accept(engine, chooser.getSelectedFile());
            finish.run();
          } catch (Exception ex) {
            JOptionPane.showMessageDialog(
                window,
                ex.getMessage(),
                strings.getString("main.error_caption"),
                JOptionPane.ERROR_MESSAGE);
          }
        });
    panel.add(load);
    JButton exit = button(strings.getString("title.quit"), scale);
    exit.setBounds(165 * scale, 193 * scale, 150 * scale, 23 * scale);
    Runnable close =
        () -> {
          if (finished[0]) return;
          finish.run();
          quit.run();
        };
    exit.addActionListener(e -> close.run());
    panel.add(exit);
    window.addWindowListener(
        new WindowAdapter() {
          @Override
          public void windowClosing(WindowEvent e) {
            close.run();
          }
        });
    window.setContentPane(panel);
    window.getRootPane().setDefaultButton(newCity);
    window.setResizable(false);
    window.pack();
    window.setLocationRelativeTo(null);
    window.setVisible(true);
    newCity.requestFocusInWindow();
  }

  private static JButton button(String text, int scale) {
    JButton button = new JButton(text);
    button.setFont(new Font(Font.DIALOG, Font.BOLD, 12 * scale));
    button.setForeground(new Color(0xf4efdb));
    button.setBackground(new Color(0x164b2f));
    button.setOpaque(true);
    button.setContentAreaFilled(true);
    button.setBorder(
        BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(0xeee9d5), scale),
            BorderFactory.createEmptyBorder(0, 4 * scale, 0, 4 * scale)));
    return button;
  }

  private static void paintSign(Graphics2D g) {
    g.setColor(new Color(0x30302e));
    g.fillRect(116, 230, 12, 70);
    g.fillRect(352, 230, 12, 70);
    int[] colors = {0x292b29, 0xd9d8c9, 0x858d86, 0xf4efdb, 0x626d61, 0x164b2f};
    for (int i = 0; i < colors.length; i++) {
      g.setColor(new Color(colors[i]));
      g.fillRect(53 + i * 2, 28 + i * 2, 374 - i * 4, 207 - i * 4);
    }
    for (int x : new int[] {69, 405})
      for (int y : new int[] {44, 216}) {
        g.setColor(new Color(0x28382a));
        g.fillRect(x - 1, y - 1, 7, 7);
        g.setColor(new Color(0xcbd0c5));
        g.fillRect(x, y, 5, 5);
        g.setColor(new Color(0x646c65));
        g.drawLine(x + 1, y + 3, x + 3, y + 1);
      }
  }
}
