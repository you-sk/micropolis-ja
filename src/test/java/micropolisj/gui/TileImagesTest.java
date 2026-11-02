package micropolisj.gui;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import javax.imageio.ImageIO;
import micropolisj.engine.TileSpec;
import org.junit.jupiter.api.Test;

class TileImagesTest {
  @Test
  void smokeFramesCompositeOverBuildingsInRecipeOrder() throws Exception {
    Properties recipe = new Properties();
    try (InputStreamReader reader =
        new InputStreamReader(
            getClass().getResourceAsStream("/graphics/tiles.rc"), StandardCharsets.UTF_8)) {
      recipe.load(reader);
    }

    for (String smoke : new String[] {"ind03", "ind04", "ind07", "ind08", "coal"}) {
      int checked = 0;
      BufferedImage smokeSheet =
          ImageIO.read(getClass().getResource("/graphics/" + smoke + "_smoke_frames.png"));
      for (String key : recipe.stringPropertyNames()) {
        if (!key.matches("\\d+")) {
          continue;
        }
        TileSpec spec = TileSpec.parse(Integer.parseInt(key), key, recipe.getProperty(key), recipe);
        String[] layers = spec.getImages();
        if (layers.length != 2 || !layers[1].startsWith(smoke + "_smoke_frames@")) {
          continue;
        }
        checked++;
        BufferedImage actual = render(spec);
        BufferedImage building = render(TileSpec.parse(0, "building", layers[0], recipe));
        BufferedImage expected = new BufferedImage(16, 16, BufferedImage.TYPE_INT_RGB);
        Graphics2D gr = expected.createGraphics();
        gr.drawImage(building, 0, 0, null);
        String[] offset = layers[1].split("@")[1].split(",");
        int x = Integer.parseInt(offset[0]);
        int y = Integer.parseInt(offset[1]);
        gr.drawImage(smokeSheet, 0, 0, 16, 16, x, y, x + 16, y + 16, null);
        gr.dispose();
        boolean transparent = false;
        for (int py = 0; py < 16; py++) {
          for (int px = 0; px < 16; px++) {
            assertThat(actual.getRGB(px, py))
                .as("%s frame %s pixel %s,%s", smoke, key, px, py)
                .isEqualTo(expected.getRGB(px, py));
            if ((smokeSheet.getRGB(x + px, y + py) >>> 24) == 0) {
              transparent = true;
              assertThat(actual.getRGB(px, py)).isEqualTo(building.getRGB(px, py));
            }
          }
        }
        assertThat(transparent).as("transparent pixels in %s", key).isTrue();
      }
      assertThat(checked).as("frames for %s", smoke).isGreaterThan(0);
    }
  }

  @Test
  void singleLayerIsUnchanged() throws Exception {
    TileSpec spec = TileSpec.parse(0, "single", "ind_zones@32,144", new Properties());
    BufferedImage source = ImageIO.read(getClass().getResource("/graphics/ind_zones.png"));
    BufferedImage actual = render(spec);
    for (int y = 0; y < 16; y++) {
      for (int x = 0; x < 16; x++) {
        assertThat(actual.getRGB(x, y)).isEqualTo(source.getRGB(x + 32, y + 144));
      }
    }
  }

  private BufferedImage render(TileSpec spec) {
    BufferedImage result = new BufferedImage(16, 16, BufferedImage.TYPE_INT_RGB);
    Graphics2D gr = result.createGraphics();
    TileImages.drawTileLayers(gr, spec, 16, 16);
    gr.dispose();
    return result;
  }
}
