package micropolisj.audio;

import static org.assertj.core.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MusicConfigTest {
  @TempDir Path dir;

  MusicConfig load(String xml) throws Exception {
    Path file = dir.resolve("音楽.xml");
    Files.writeString(file, xml);
    return MusicConfig.load(file);
  }

  @Test
  void resolvesJapanesePathsAndPrefersSpecificRuleAtEqualPriority() throws Exception {
    MusicConfig c =
        load(
            """
        <music volume="0.4" fadeMillis="250" pauseWithGame="true">
          <track id="default" file="音楽/通常.wav"/>
          <track id="town" file="音楽/町.mp3" loop="false" seconds="30"/>
          <rule scene="city" track="default"/>
          <rule scene="city" cityClass="1" track="town"/>
        </music>
        """);
    assertThat(c.select(MusicConfig.Scene.CITY, 1).file()).isEqualTo(dir.resolve("音楽/町.mp3"));
    assertThat(c.select(MusicConfig.Scene.CITY, 0).id()).isEqualTo("default");
    assertThat(c.select(MusicConfig.Scene.TITLE, 0)).isNull();
    assertThat(c.pauseWithGame()).isTrue();
  }

  @Test
  void rejectsExternalEntitiesAndUnknownOrInvalidSettings() {
    for (String xml :
        new String[] {
          "<!DOCTYPE music [<!ENTITY x SYSTEM 'file:///missing'>]><music>&x;</music>",
          "<music volume='NaN'/>",
          "<music fadeMillis='0.5'/>",
          "<music pauseWithGame='yes'/>",
          "<music command='run'/>",
          "<music><rule scene='city' track='missing'/></music>",
          "<music><track id='x' file='x'/><track id='x' file='y'/></music>",
          "<music><track id='x' file='x'/><rule scene='title' cityClass='2' track='x'/></music>"
        }) {
      assertThatThrownBy(() -> load(xml)).as(xml).isInstanceOf(java.io.IOException.class);
    }
  }

  @Test
  void enforcesEntryAndFileSizeLimits() throws Exception {
    assertThatThrownBy(() -> load("<music>" + "<track id='x' file='x'/>".repeat(300) + "</music>"))
        .isInstanceOf(java.io.IOException.class);
    Path big = dir.resolve("large.xml");
    Files.writeString(big, " ".repeat(1_048_577));
    assertThatThrownBy(() -> MusicConfig.load(big)).isInstanceOf(java.io.IOException.class);
  }
}
