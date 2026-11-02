package micropolisj.audio;

import java.nio.file.Path;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.prefs.Preferences;

/** Owns configuration IO and the real-time interruption clock, never on the Swing thread. */
public final class MusicService implements AutoCloseable {
  private final ScheduledExecutorService control =
      Executors.newSingleThreadScheduledExecutor(
          r -> {
            Thread t = new Thread(r, "micropolis-music-control");
            t.setDaemon(true);
            return t;
          });
  private final Preferences prefs = Preferences.userNodeForPackage(MusicService.class);
  private final MusicDirector director;
  private volatile String error = "";
  private volatile String path;
  private volatile boolean loaded;
  private boolean enabled;
  private int volume;
  private int effectVolume;

  public MusicService() {
    director =
        new MusicDirector(new StreamingMusicPlayer(message -> error = message), System::nanoTime);
    enabled = prefs.getBoolean("enabled", true);
    volume = Math.max(0, Math.min(100, prefs.getInt("volume", 100)));
    effectVolume = Math.max(0, Math.min(100, prefs.getInt("effectVolume", 100)));
    director.setEnabled(enabled);
    director.setVolume(volume / 100.0);
    path = System.getProperty("micropolis.music", prefs.get("config", ""));
    if (!path.isBlank()) load(path);
    control.scheduleWithFixedDelay(director::refresh, 100, 100, TimeUnit.MILLISECONDS);
  }

  public void load(String value) {
    path = value;
    control.execute(
        () -> {
          loaded = false;
          error = "";
          director.configure(null);
          try {
            MusicConfig config = MusicConfig.load(Path.of(value));
            director.configure(config);
            loaded = true;
            prefs.put("config", Path.of(value).toAbsolutePath().toString());
          } catch (Exception e) {
            error = e.getMessage() == null ? e.toString() : e.getMessage();
          }
        });
  }

  public String path() {
    return path;
  }

  public String error() {
    return error;
  }

  public boolean loaded() {
    return loaded;
  }

  public boolean enabled() {
    return enabled;
  }

  public int volume() {
    return volume;
  }

  public int effectVolume() {
    return effectVolume;
  }

  public void setEffectVolume(int value) {
    effectVolume = Math.max(0, Math.min(100, value));
    prefs.putInt("effectVolume", effectVolume);
  }

  public void setEnabled(boolean value) {
    enabled = value;
    director.setEnabled(value);
    prefs.putBoolean("enabled", value);
  }

  public void setVolume(int value) {
    volume = Math.max(0, Math.min(100, value));
    director.setVolume(volume / 100.0);
    prefs.putInt("volume", volume);
  }

  public void title() {
    director.scene(MusicConfig.Scene.TITLE, 0);
    director.setPaused(false);
  }

  public void city(int cityClass, boolean paused) {
    director.scene(MusicConfig.Scene.CITY, cityClass);
    director.setPaused(paused);
  }

  public void disaster() {
    director.disaster();
  }

  @Override
  public void close() {
    control.shutdownNow();
    director.close();
  }
}
