package micropolisj.audio;

import static org.assertj.core.api.Assertions.*;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class MusicDirectorTest {
  static class Player implements MusicPlayer {
    MusicConfig.Track track;
    int starts;
    boolean paused;
    boolean closed;
    double volume;

    public void play(MusicConfig.Track t, int fade) {
      track = t;
      starts++;
    }

    public void stop(int fade) {
      track = null;
    }

    public void setVolume(double v) {
      volume = v;
    }

    public void setPaused(boolean v) {
      paused = v;
    }

    public void close() {
      closed = true;
    }
  }

  @Test
  void returnsFromInterruptionWithoutRestartingIdenticalCityMusic() {
    Player p = new Player();
    AtomicLong time = new AtomicLong();
    MusicDirector d = new MusicDirector(p, time::get);
    MusicConfig.Track normal = new MusicConfig.Track("normal", Path.of("n.wav"), true, 0);
    MusicConfig.Track event = new MusicConfig.Track("event", Path.of("e.wav"), false, 0);
    d.configure(
        new MusicConfig(
            .5,
            100,
            true,
            List.of(normal, event),
            List.of(
                new MusicConfig.Rule(MusicConfig.Scene.CITY, -1, "normal", 0, 15),
                new MusicConfig.Rule(MusicConfig.Scene.DISASTER, -1, "event", 100, 2))));
    d.scene(MusicConfig.Scene.CITY, 0);
    d.scene(MusicConfig.Scene.CITY, 1);
    assertThat(p.starts).isEqualTo(1);
    d.disaster();
    assertThat(p.track).isEqualTo(event);
    time.set(1_000_000_000L);
    d.disaster(); // Repeated reports must not extend the interruption indefinitely.
    time.set(2_000_000_001L);
    d.refresh();
    assertThat(p.track).isEqualTo(normal);
    assertThat(p.starts).isEqualTo(3);
    d.setPaused(true);
    assertThat(p.paused).isTrue();
    d.setVolume(.4);
    assertThat(p.volume).isCloseTo(.2, within(.0001));
    d.setEnabled(false);
    assertThat(p.track).isNull();
    d.setEnabled(true);
    assertThat(p.track).isEqualTo(normal);
    d.close();
    assertThat(p.closed).isTrue();
  }

  @Test
  void unconfiguredMusicIsSilentAndLowPriorityEventsDoNotOverrideCity() {
    Player p = new Player();
    MusicDirector d = new MusicDirector(p, () -> 0);
    d.scene(MusicConfig.Scene.CITY, 3);
    d.disaster();
    assertThat(p.starts).isZero();
    var t = new MusicConfig.Track("x", Path.of("x.wav"), true, 0);
    d.configure(
        new MusicConfig(
            .6,
            0,
            false,
            List.of(t),
            List.of(
                new MusicConfig.Rule(MusicConfig.Scene.CITY, -1, "x", 100, 1),
                new MusicConfig.Rule(MusicConfig.Scene.DISASTER, -1, "x", 1, 1))));
    d.disaster();
    assertThat(p.starts).isEqualTo(1);
    d.setPaused(true);
    assertThat(p.paused).isFalse();
    d.configure(null);
    assertThat(p.track).isNull();
  }
}
