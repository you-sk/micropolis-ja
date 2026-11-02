package micropolisj.audio;

import java.io.IOException;
import java.io.InputStream;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;

/** Optional libgme adapter. ROM loading/extraction is deliberately outside this decoder. */
final class SpcPcmStream extends InputStream {
  private final Arena arena = Arena.ofConfined();
  private final MemorySegment samples = arena.allocate(4096, 2);
  private final byte[] pcm = new byte[4096];
  private MemorySegment emulator = MemorySegment.NULL;
  private MethodHandle delete;
  private MethodHandle play;
  private MethodHandle ended;
  private int position;
  private int size;
  private boolean closed;

  private SpcPcmStream(Path file) throws IOException {
    try {
      long length = Files.size(file);
      if (length < 0x10200 || length > 1_048_576) throw new IOException("Invalid SPC size");
      byte[] bytes = Files.readAllBytes(file);
      byte[] signature =
          "SNES-SPC700 Sound File Data".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
      for (int i = 0; i < signature.length; i++) {
        if (bytes[i] != signature[i]) throw new IOException("Invalid SPC signature");
      }
      Linker linker = Linker.nativeLinker();
      SymbolLookup symbols = SymbolLookup.libraryLookup(libraryPath(), arena);
      MemoryLayout cLong = linker.canonicalLayouts().get("long");
      MethodHandle open =
          function(
              linker,
              symbols,
              "gme_open_data",
              FunctionDescriptor.of(
                  ValueLayout.ADDRESS,
                  ValueLayout.ADDRESS,
                  cLong,
                  ValueLayout.ADDRESS,
                  ValueLayout.JAVA_INT));
      MethodHandle start =
          function(
              linker,
              symbols,
              "gme_start_track",
              FunctionDescriptor.of(
                  ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
      delete =
          function(linker, symbols, "gme_delete", FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
      play =
          function(
              linker,
              symbols,
              "gme_play",
              FunctionDescriptor.of(
                  ValueLayout.ADDRESS,
                  ValueLayout.ADDRESS,
                  ValueLayout.JAVA_INT,
                  ValueLayout.ADDRESS));
      ended =
          function(
              linker,
              symbols,
              "gme_track_ended",
              FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
      var limit =
          function(
              linker,
              symbols,
              "gme_set_autoload_playback_limit",
              FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
      var silence =
          function(
              linker,
              symbols,
              "gme_ignore_silence",
              FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
      MemorySegment data = arena.allocate(bytes.length);
      data.copyFrom(MemorySegment.ofArray(bytes));
      MemorySegment out = arena.allocate(ValueLayout.ADDRESS);
      Object nativeLength;
      if (cLong.byteSize() == 4) nativeLength = Integer.valueOf(bytes.length);
      else nativeLength = Long.valueOf(bytes.length);
      MemorySegment error =
          (MemorySegment) open.invokeWithArguments(data, nativeLength, out, 44100);
      emulator = out.get(ValueLayout.ADDRESS, 0);
      check(error);
      limit.invoke(emulator, 0);
      silence.invoke(emulator, 1);
      check((MemorySegment) start.invoke(emulator, 0));
    } catch (Throwable e) {
      close();
      throw new IOException("Cannot open SPC (libgme required): " + e.getMessage(), e);
    }
  }

  static Path libraryPath() throws Exception {
    String override = System.getProperty("micropolis.gme.library", "");
    if (!override.isBlank()) return Path.of(override).toAbsolutePath();
    Path code =
        Path.of(SpcPcmStream.class.getProtectionDomain().getCodeSource().getLocation().toURI());
    return code.getParent().resolve("native").resolve(System.mapLibraryName("gme"));
  }

  private static MethodHandle function(
      Linker linker, SymbolLookup lookup, String name, FunctionDescriptor descriptor)
      throws IOException {
    MemorySegment address =
        lookup.find(name).orElseThrow(() -> new IOException("Missing libgme symbol: " + name));
    return linker.downcallHandle(address, descriptor);
  }

  private static void check(MemorySegment error) throws IOException {
    if (!error.equals(MemorySegment.NULL))
      throw new IOException(error.reinterpret(2048).getString(0));
  }

  static AudioInputStream open(Path file) throws IOException {
    return new AudioInputStream(
        new SpcPcmStream(file),
        new AudioFormat(44100, 16, 2, true, false),
        AudioSystem.NOT_SPECIFIED);
  }

  @Override
  public int read(byte[] bytes, int offset, int length) throws IOException {
    java.util.Objects.checkFromIndexSize(offset, length, bytes.length);
    if (length == 0) return 0;
    if (closed) throw new IOException("SPC stream closed");
    try {
      if (position == size) {
        if ((int) ended.invoke(emulator) != 0) return -1;
        check((MemorySegment) play.invoke(emulator, 2048, samples));
        for (int i = 0; i < 2048; i++) {
          short sample = samples.get(ValueLayout.JAVA_SHORT, i * 2L);
          pcm[i * 2] = (byte) sample;
          pcm[i * 2 + 1] = (byte) (sample >> 8);
        }
        position = 0;
        size = pcm.length;
      }
      int count = Math.min(length, size - position);
      System.arraycopy(pcm, position, bytes, offset, count);
      position += count;
      return count;
    } catch (Throwable e) {
      throw new IOException("SPC decoding failed", e);
    }
  }

  @Override
  public int read() throws IOException {
    byte[] one = new byte[1];
    return read(one, 0, 1) < 0 ? -1 : one[0] & 255;
  }

  @Override
  public void close() throws IOException {
    if (closed) return;
    closed = true;
    try {
      if (delete != null && !emulator.equals(MemorySegment.NULL)) delete.invoke(emulator);
    } catch (Throwable e) {
      throw new IOException("SPC cleanup failed", e);
    } finally {
      arena.close();
    }
  }
}
