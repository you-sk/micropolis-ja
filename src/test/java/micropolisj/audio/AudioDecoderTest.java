package micropolisj.audio;

import static org.assertj.core.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.sound.sampled.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AudioDecoderTest {
  @TempDir Path dir;

  @Test
  void decodesAllBundledFormatsToNonSilentStereoPcm() throws Exception {
    for (String extension : new String[] {"wav", "mp3", "ogg", "flac"}) {
      Path file = dir.resolve("音楽." + extension);
      try (var resource = getClass().getResourceAsStream("/music/tone." + extension)) {
        Files.copy(resource, file);
      }
      try (var decoded = AudioDecoder.open(file)) {
        byte[] pcm = decoded.readNBytes(44100 * 4);
        assertThat(decoded.getFormat().getSampleSizeInBits()).isEqualTo(16);
        assertThat(decoded.getFormat().getChannels()).isEqualTo(2);
        assertThat(pcm.length).as(extension).isBetween(20000, 65000);
        long energy = 0;
        for (int i = 0; i + 1 < pcm.length; i += 2) {
          short sample = (short) ((pcm[i] & 255) | pcm[i + 1] << 8);
          energy += (long) sample * sample;
        }
        assertThat(energy).as("decoded signal energy: %s", extension).isGreaterThan(1_000_000L);
      }
    }
  }

  @Test
  void decodesGeneratedPcmAndScalesSignedSamples() throws Exception {
    byte[] samples = new byte[4410 * 2];
    samples[0] = (byte) 0xff;
    samples[1] = 0x7f;
    samples[2] = 0;
    samples[3] = (byte) 0x80;
    Path wav = dir.resolve("確認.wav");
    try (var pcm =
        new AudioInputStream(
            new ByteArrayInputStream(samples), new AudioFormat(44100, 16, 1, true, false), 4410)) {
      AudioSystem.write(pcm, AudioFileFormat.Type.WAVE, wav.toFile());
    }
    try (var decoded = AudioDecoder.open(wav)) {
      byte[] actual = decoded.readAllBytes();
      assertThat(actual).isEqualTo(samples);
      StreamingMusicPlayer.scaleFrame(actual, 0, 1, .5);
      assertThat((short) ((actual[0] & 255) | actual[1] << 8)).isEqualTo((short) 16384);
      StreamingMusicPlayer.scaleFrame(actual, 2, 1, .5);
      assertThat((short) ((actual[2] & 255) | actual[3] << 8)).isEqualTo((short) -16384);
    }
  }

  @Test
  void rejectsDamagedInputWithoutAnAudioDevice() throws Exception {
    Path invalid = dir.resolve("invalid.mp3");
    Files.writeString(invalid, "not audio");
    assertThatThrownBy(() -> AudioDecoder.open(invalid)).isInstanceOf(java.io.IOException.class);
    Path invalidOgg = dir.resolve("invalid.ogg");
    Files.writeString(invalidOgg, "not audio");
    assertThatThrownBy(() -> AudioDecoder.open(invalidOgg)).isInstanceOf(java.io.IOException.class);
  }
}
