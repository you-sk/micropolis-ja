package micropolisj.gui;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Properties;
import java.util.ResourceBundle;
import javax.swing.JMenuItem;
import org.junit.jupiter.api.Test;

class JapaneseResourcesTest {
  @Test
  void japaneseMenusAndEnglishFallback() {
    ResourceBundle japanese = ResourceBundle.getBundle("strings.GuiStrings", Locale.JAPAN);
    ResourceBundle english = ResourceBundle.getBundle("strings.GuiStrings", Locale.ROOT);

    assertThat(japanese.getString("menu.game")).isEqualTo("ゲーム");
    assertThat(japanese.getString("menu.game.save")).isEqualTo("都市を保存");
    assertThat(japanese.getString("menu.game.save")).doesNotContain("�");
    assertThat(japanese.getString("menu.game.save.shortcut")).isEqualTo("ctrl S");
    assertThat(japanese.getString("menu.overlays")).isEqualTo("地図表示");
    assertThat(english.getString("menu.game")).isEqualTo("Game");

    for (String key :
        new String[] {
          "menu.game.new",
          "menu.game.load",
          "menu.game.save",
          "menu.game.save_as",
          "menu.game.exit",
          "menu.options.sound"
        }) {
      JMenuItem item = new JMenuItem(japanese.getString(key));
      assertThat(item.getPreferredSize().width)
          .as("menu item width: %s", key)
          .isGreaterThan(item.getFontMetrics(item.getFont()).stringWidth(item.getText()));
    }
  }

  @Test
  void translatedMessagesAndEnglishFallback() {
    ResourceBundle messages = ResourceBundle.getBundle("strings.CityMessages", Locale.JAPAN);
    ResourceBundle city = ResourceBundle.getBundle("strings.CityStrings", Locale.JAPAN);
    ResourceBundle status = ResourceBundle.getBundle("strings.StatusMessages", Locale.JAPAN);
    ResourceBundle gui = ResourceBundle.getBundle("strings.GuiStrings", Locale.JAPAN);

    assertThat(messages.getString("NEED_RES")).isEqualTo("住宅地を増やす必要があります。");
    assertThat(messages.getString("FIRE_REPORT")).isEqualTo("火災が報告されています。");
    assertThat(messages.getString("FIRE_REPORT.title")).isEqualTo("火災の報告");
    assertThat(messages.getString("NEED_PARKS")).isEqualTo("公園を増やす必要があります。");
    assertThat(city.getString("problem.TRAFFIC")).isEqualTo("交通渋滞");
    assertThat(city.getString("problem.HOUSING")).isEqualTo("住宅費");
    assertThat(status.getString("zone.10")).isEqualTo("住宅地");
    assertThat(status.getString("zone.5")).isEqualTo("放射性廃棄物");
    assertThat(status.getString("zone.24")).isEqualTo("Steelers 38  Bears 3");
    assertThat(gui.getString("menu.windows")).isEqualTo("ウィンドウ");
    assertThat(gui.getString("menu.options")).isEqualTo("設定");
    assertThat(gui.getString("menu.game.new")).isEqualTo("新しい都市...");
    assertThat(gui.getString("menu.game.new.shortcut")).isEqualTo("ctrl N");
    assertThat(ResourceBundle.getBundle("strings.CityMessages", Locale.ROOT).getString("NEED_RES"))
        .isEqualTo("More residential zones needed.");
  }

  @Test
  void displayedKeysAreTranslatedAndNonDisplayKeysFallBack() throws Exception {
    for (String name :
        new String[] {"GuiStrings", "CityMessages", "CityStrings", "StatusMessages"}) {
      Properties original = load(name + ".properties");
      Properties japanese = load(name + "_ja.properties");
      ResourceBundle bundle = ResourceBundle.getBundle("strings." + name, Locale.JAPAN);
      for (String key : original.stringPropertyNames()) {
        boolean nonDisplay =
            name.equals("GuiStrings")
                    && (key.endsWith(".key")
                        || key.endsWith(".shortcut")
                        || key.endsWith(".icon")
                        || key.endsWith(".selected_icon")
                        || key.endsWith(".border")
                        || key.endsWith(".bgcolor")
                        || key.startsWith("graph_button.")
                        || key.startsWith("graph_color.")
                        || key.startsWith("legend_image.")
                        || key.equals("funds")
                        || key.equals("citytime"))
                || name.equals("CityMessages") && key.endsWith(".color")
                || name.equals("StatusMessages")
                    && (key.equals("zone.23") || key.equals("zone.25") || key.equals("zone.27"));
        boolean unknown =
            name.equals("StatusMessages") && (key.equals("zone.24") || key.equals("zone.26"));
        assertThat(japanese.containsKey(key))
            .as("%s.%s", name, key)
            .isEqualTo(!nonDisplay && !unknown);
        if (nonDisplay || unknown) {
          assertThat(bundle.getString(key)).isEqualTo(original.getProperty(key));
        } else {
          assertThat(bundle.getString(key)).doesNotContain("�");
        }
      }
      assertThat(japanese.stringPropertyNames()).isSubsetOf(original.stringPropertyNames());
    }
    ResourceBundle gui = ResourceBundle.getBundle("strings.GuiStrings", Locale.JAPAN);
    assertThat(gui.getString("main.version_string"))
        .contains("{0}", "%java.version%", "%java.vendor%");
    assertThat(gui.getString("main.caption_named_city")).contains("{0}");
    assertThat(gui.getString("main.about_text")).contains("<html>", "<p>", "<br>", "</html>");
    assertThat(
            ResourceBundle.getBundle("strings.CityMessages", Locale.JAPAN)
                .getString("MELTDOWN_REPORT.detail"))
        .contains("<br><br>");
  }

  private Properties load(String file) throws Exception {
    Properties properties = new Properties();
    try (InputStreamReader reader =
        new InputStreamReader(
            getClass().getClassLoader().getResourceAsStream("strings/" + file),
            StandardCharsets.UTF_8)) {
      properties.load(reader);
    }
    return properties;
  }
}
