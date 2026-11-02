package micropolisj.audio;

import static org.assertj.core.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SpcDecoderTest {
  @TempDir Path dir;

  /** A tiny original SPC700 program and BRR waveform; no commercial ROM or music data. */
  static byte[] tone() {
    byte[] spc = new byte[0x10200];
    byte[] header = "SNES-SPC700 Sound File Data v0.30".getBytes(StandardCharsets.US_ASCII);
    System.arraycopy(header, 0, spc, 0, header.length);
    spc[0x21] = 0x1a;
    spc[0x22] = 0x1a;
    spc[0x23] = 0x1a;
    spc[0x24] = 30;
    spc[0x25] = 0;
    spc[0x26] = 2;
    spc[0x2b] = (byte) 0xef;
    // MOV $f2,#$4c; MOV $f3,#1; BRA $-2 -- key on voice 0 and keep running.
    byte[] program = {
      (byte) 0x8f, 0x4c, (byte) 0xf2, (byte) 0x8f, 1, (byte) 0xf3, 0x2f, (byte) 0xfe
    };
    System.arraycopy(program, 0, spc, 0x100 + 0x200, program.length);
    int directory = 0x100 + 0x1000;
    spc[directory + 1] = 0x11;
    spc[directory + 3] = 0x11;
    byte[] brr = {(byte) 0xc3, 0x03, 0x57, 0x75, 0x30, 0x0d, (byte) 0xb9, (byte) 0x9b, (byte) 0xd0};
    System.arraycopy(brr, 0, spc, 0x100 + 0x1100, brr.length);
    spc[0x10100] = 96;
    spc[0x10101] = 96;
    spc[0x10103] = 0x10;
    spc[0x10107] = 0x7f;
    spc[0x1010c] = 0x7f;
    spc[0x1011c] = 0x7f;
    spc[0x1015d] = 0x10;
    spc[0x1016c] = 0x20;
    return spc;
  }

  @Test
  void rejectsInvalidDataBeforeLoadingNativeCode() throws Exception {
    Path file = dir.resolve("invalid.spc");
    Files.write(file, new byte[4]);
    assertThatThrownBy(() -> AudioDecoder.open(file)).isInstanceOf(java.io.IOException.class);
  }

  @Test
  void generatedSpcDecodesAndClosesWhenNativeLibraryIsPresent() throws Exception {
    Path library = SpcPcmStream.libraryPath();
    org.junit.jupiter.api.Assumptions.assumeTrue(
        Files.isRegularFile(library), "Build libgme to run the native decoder test");
    Path file = dir.resolve("自作テスト音.spc");
    Files.write(file, tone());
    for (int repetition = 0; repetition < 3; repetition++) {
      try (var audio = AudioDecoder.open(file)) {
        byte[] pcm = audio.readNBytes(44100 * 4);
        assertThat(pcm.length).isEqualTo(44100 * 4);
        long energy = 0;
        for (int i = 0; i < pcm.length; i += 2) {
          short sample = (short) ((pcm[i] & 255) | pcm[i + 1] << 8);
          energy += (long) sample * sample;
        }
        assertThat(energy).isGreaterThan(1_000_000L);
      }
    }
  }
}
