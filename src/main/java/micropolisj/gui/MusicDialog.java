package micropolisj.gui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Window;
import java.util.ResourceBundle;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.Timer;
import javax.swing.filechooser.FileNameExtensionFilter;
import micropolisj.audio.MusicService;

/** Optional local music pack settings; decoder errors remain non-modal. */
final class MusicDialog extends JDialog {
  MusicDialog(Window owner, MusicService music) {
    super(owner, MainWindow.strings.getString("music.title"));
    ResourceBundle s = MainWindow.strings;
    setDefaultCloseOperation(DISPOSE_ON_CLOSE);
    Box content = Box.createVerticalBox();
    content.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
    setContentPane(content);
    JCheckBox enabled = new JCheckBox(s.getString("music.enabled"), music.enabled());
    enabled.addActionListener(e -> music.setEnabled(enabled.isSelected()));
    content.add(enabled);
    content.add(new JLabel(s.getString("music.volume")));
    JSlider volume = new JSlider(0, 100, music.volume());
    volume.setMajorTickSpacing(25);
    volume.setPaintTicks(true);
    volume.setPaintLabels(true);
    volume.addChangeListener(e -> music.setVolume(volume.getValue()));
    content.add(volume);
    content.add(new JLabel(s.getString("music.effects_volume")));
    JSlider effects = new JSlider(0, 100, music.effectVolume());
    effects.setMajorTickSpacing(25);
    effects.setPaintTicks(true);
    effects.setPaintLabels(true);
    effects.addChangeListener(e -> music.setEffectVolume(effects.getValue()));
    content.add(effects);
    content.add(new JLabel(s.getString("music.file")));
    JTextField path = new JTextField(music.path(), 32);
    path.setEditable(false);
    content.add(path);
    JPanel files = new JPanel();
    JButton choose = new JButton(s.getString("music.choose"));
    choose.addActionListener(
        e -> {
          JFileChooser picker = new JFileChooser();
          picker.setFileFilter(new FileNameExtensionFilter("XML", "xml"));
          if (picker.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            String value = picker.getSelectedFile().getAbsolutePath();
            path.setText(value);
            music.load(value);
          }
        });
    files.add(choose);
    JButton reload = new JButton(s.getString("music.reload"));
    reload.addActionListener(
        e -> {
          if (!path.getText().isBlank()) music.load(path.getText());
        });
    files.add(reload);
    content.add(files);
    JTextArea status = new JTextArea(4, 34);
    status.setEditable(false);
    status.setLineWrap(true);
    status.setWrapStyleWord(true);
    JScrollPane scroll = new JScrollPane(status);
    scroll.setPreferredSize(new Dimension(430, 100));
    content.add(scroll);
    Runnable update =
        () -> {
          String value =
              !music.error().isEmpty()
                  ? s.getString("music.error") + "\n" + music.error()
                  : music.loaded()
                      ? s.getString("music.ready")
                      : music.path().isBlank()
                          ? s.getString("music.unconfigured")
                          : s.getString("music.loading");
          if (!value.equals(status.getText())) status.setText(value);
        };
    update.run();
    Timer timer = new Timer(300, e -> update.run());
    addWindowListener(
        new java.awt.event.WindowAdapter() {
          @Override
          public void windowClosed(java.awt.event.WindowEvent e) {
            timer.stop();
          }
        });
    timer.start();
    JPanel buttons = new JPanel(new BorderLayout());
    JButton close = new JButton(s.getString("notification.dismiss"));
    close.addActionListener(e -> dispose());
    buttons.add(close);
    content.add(buttons);
    pack();
    setLocationRelativeTo(owner);
  }
}
