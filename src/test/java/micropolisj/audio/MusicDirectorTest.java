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

  private static MusicConfig eventConfig() {
    var tracks =
        java.util.Arrays.stream(MusicConfig.Scene.values())
            .map(s -> new MusicConfig.Track(s.name(), Path.of(s.name() + ".wav"), true, 0))
            .toList();
    return new MusicConfig(
        .5,
        100,
        false,
        tracks,
        List.of(
            new MusicConfig.Rule(MusicConfig.Scene.TITLE, -1, "TITLE", 0, 1),
            new MusicConfig.Rule(MusicConfig.Scene.CITY, -1, "CITY", 0, 1),
            new MusicConfig.Rule(MusicConfig.Scene.EVALUATION_GOOD, -1, "EVALUATION_GOOD", 10, 1),
            new MusicConfig.Rule(MusicConfig.Scene.EVALUATION_BAD, -1, "EVALUATION_BAD", 10, 1),
            new MusicConfig.Rule(MusicConfig.Scene.REWARD, -1, "REWARD", 50, 3),
            new MusicConfig.Rule(MusicConfig.Scene.DISASTER, -1, "DISASTER", 100, 2)));
  }

  @Test
  void evaluationUsesApprovalThresholdAndEndsWhenThePaneCloses() {
    Player p = new Player();
    AtomicLong time = new AtomicLong();
    MusicDirector d = new MusicDirector(p, time::get);
    d.configure(eventConfig());
    d.scene(MusicConfig.Scene.CITY, 0);
    d.evaluation(49);
    assertThat(p.track.id()).isEqualTo("EVALUATION_BAD");
    d.evaluation(50);
    assertThat(p.track.id()).isEqualTo("EVALUATION_GOOD");
    int starts = p.starts;
    d.evaluation(75);
    d.scene(MusicConfig.Scene.CITY, 2);
    time.set(100_000_000_000L);
    d.refresh();
    assertThat(p.starts).isEqualTo(starts); // A visible evaluation is not a timed event.
    d.endEvaluation();
    assertThat(p.track.id()).isEqualTo("CITY");
  }

  @Test
  void disasterPreemptsRewardThenReturnsToTheCurrentEvaluation() {
    Player p = new Player();
    AtomicLong time = new AtomicLong();
    MusicDirector d = new MusicDirector(p, time::get);
    d.configure(eventConfig());
    d.scene(MusicConfig.Scene.CITY, 0);
    d.evaluation(70);
    d.reward();
    assertThat(p.track.id()).isEqualTo("REWARD");
    time.set(1_000_000_000L);
    d.disaster();
    assertThat(p.track.id()).isEqualTo("DISASTER");
    int starts = p.starts;
    d.reward();
    d.disaster();
    assertThat(p.starts).isEqualTo(starts);
    d.evaluation(40);
    d.scene(MusicConfig.Scene.CITY, 3);
    time.set(3_000_000_001L);
    d.refresh();
    assertThat(p.track.id()).isEqualTo("EVALUATION_BAD");
    d.reward();
    d.endEvaluation();
    time.set(7_000_000_000L);
    d.refresh();
    assertThat(p.track.id()).isEqualTo("CITY");
  }

  @Test
  void optionalEventsFallBackToCityAndTitleCancelsAllEvents() {
    Player p = new Player();
    AtomicLong time = new AtomicLong();
    MusicDirector d = new MusicDirector(p, time::get);
    var city = new MusicConfig.Track("city", Path.of("city.wav"), true, 0);
    d.configure(
        new MusicConfig(
            1,
            0,
            false,
            List.of(city),
            List.of(new MusicConfig.Rule(MusicConfig.Scene.CITY, -1, "city", 0, 1))));
    d.scene(MusicConfig.Scene.CITY, 0);
    int starts = p.starts;
    d.evaluation(80);
    d.reward();
    d.disaster();
    assertThat(p.starts).isEqualTo(starts);
    assertThat(p.track).isEqualTo(city);
    d.configure(eventConfig());
    d.evaluation(80);
    d.reward();
    d.scene(MusicConfig.Scene.TITLE, 0);
    d.reward();
    d.evaluation(40);
    assertThat(p.track.id()).isEqualTo("TITLE");
    d.scene(MusicConfig.Scene.CITY, 0);
    assertThat(p.track.id()).isEqualTo("CITY");
  }
}
