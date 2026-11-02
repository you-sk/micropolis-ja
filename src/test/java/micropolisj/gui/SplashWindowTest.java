package micropolisj.gui;

import static org.assertj.core.api.Assertions.*;

import java.util.HashSet;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class SplashWindowTest {
  @Test
  void artworkUsesLowResolutionAndLimitedPalette() {
    var artwork = SplashWindow.loadArtwork();
    assertThat(artwork).isNotNull();
    assertThat(artwork.getWidth()).isEqualTo(320);
    assertThat(artwork.getHeight()).isEqualTo(200);
    var colors = new HashSet<Integer>();
    for (int y = 0; y < artwork.getHeight(); y++)
      for (int x = 0; x < artwork.getWidth(); x++) colors.add(artwork.getRGB(x, y));
    assertThat(colors.size()).isBetween(16, 24);
  }

  @Test
  void missingArtworkContinuesStartupOnce() throws Exception {
    var calls = new AtomicInteger();
    SwingUtilities.invokeAndWait(() -> SplashWindow.show(null, calls::incrementAndGet));
    assertThat(calls.get()).isEqualTo(1);
  }
}
