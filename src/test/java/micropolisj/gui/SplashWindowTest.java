package micropolisj.gui;

import static org.assertj.core.api.Assertions.*;

import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class SplashWindowTest {
  @Test
  void eachSeasonHasLoadableArtwork() {
    for (int month = 1; month <= 12; month++) {
      var artwork = SplashWindow.loadArtwork(month);
      assertThat(artwork).isNotNull();
      assertThat(artwork.getWidth()).isEqualTo(480);
      assertThat(artwork.getHeight()).isEqualTo(300);
    }
  }

  @Test
  void seasonsFollowJapaneseCalendarMonths() {
    assertThat(java.util.stream.IntStream.rangeClosed(1, 12).mapToObj(SplashWindow::seasonForMonth))
        .containsExactly(
            "winter", "winter", "spring", "spring", "spring", "summer", "summer", "summer",
            "autumn", "autumn", "autumn", "winter");
    assertThatThrownBy(() -> SplashWindow.seasonForMonth(0))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void missingArtworkContinuesStartupOnce() throws Exception {
    var calls = new AtomicInteger();
    SwingUtilities.invokeAndWait(() -> SplashWindow.show(null, calls::incrementAndGet));
    assertThat(calls.get()).isEqualTo(1);
  }
}
