package micropolisj.audio;

import java.util.Objects;
import java.util.function.LongSupplier;

/** Pure scene selection and timed interruptions, independent of Swing and the simulation clock. */
public final class MusicDirector implements AutoCloseable {
  private final MusicPlayer player;
  private final LongSupplier clock;
  private MusicConfig config;
  private MusicConfig.Scene scene = MusicConfig.Scene.TITLE;
  private int cityClass;
  private boolean enabled = true;
  private boolean paused;
  private double volume = 1;
  private long interruptionEnd;
  private MusicConfig.Track selected;

  public MusicDirector(MusicPlayer player, LongSupplier clock) {
    this.player = player;
    this.clock = clock;
  }

  public synchronized void configure(MusicConfig value) {
    player.stop(0);
    selected = null;
    config = value;
    interruptionEnd = 0;
    refresh();
  }

  public synchronized void scene(MusicConfig.Scene value, int cityClass) {
    if (scene != value) interruptionEnd = 0;
    this.scene = value;
    this.cityClass = cityClass;
    refresh();
  }

  public synchronized void disaster() {
    if (config == null || scene != MusicConfig.Scene.CITY || interruptionEnd > clock.getAsLong())
      return;
    MusicConfig.Rule event = config.selectRule(MusicConfig.Scene.DISASTER, cityClass);
    MusicConfig.Rule normal = config.selectRule(scene, cityClass);
    if (event == null || (normal != null && event.priority() < normal.priority())) return;
    interruptionEnd = clock.getAsLong() + (long) (event.seconds() * 1_000_000_000L);
    refresh();
  }

  public synchronized void setEnabled(boolean value) {
    enabled = value;
    refresh();
  }

  public synchronized void setPaused(boolean value) {
    paused = value;
    refresh();
  }

  public synchronized void setVolume(double value) {
    volume = Math.max(0, Math.min(1, value));
    refresh();
  }

  public synchronized void refresh() {
    boolean interruption = interruptionEnd > clock.getAsLong();
    MusicConfig.Track next =
        config == null || !enabled
            ? null
            : config.select(interruption ? MusicConfig.Scene.DISASTER : scene, cityClass);
    player.setVolume(config == null ? 0 : config.volume() * volume);
    player.setPaused(
        config != null && config.pauseWithGame() && paused && scene == MusicConfig.Scene.CITY);
    if (!Objects.equals(next, selected)) {
      selected = next;
      int fade = config == null ? 0 : config.fadeMillis();
      if (next == null) player.stop(fade);
      else player.play(next, fade);
    }
  }

  @Override
  public synchronized void close() {
    player.close();
  }
}
