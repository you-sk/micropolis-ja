package micropolisj.audio;

import java.io.IOException;
import java.nio.file.Path;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;

/** Decodes installed Java Sound formats to signed 16-bit little-endian PCM. */
public final class AudioDecoder {
  private AudioDecoder() {}

  public static AudioInputStream open(Path path) throws IOException {
    if (path.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".ogg")) {
      return VorbisPcmStream.open(path);
    }
    AudioInputStream source = null;
    try {
      source = AudioSystem.getAudioInputStream(path.toFile());
      AudioFormat format = source.getFormat();
      float rate = format.getSampleRate();
      int channels = format.getChannels();
      if (!Float.isFinite(rate) || rate < 8000 || rate > 192000 || channels < 1 || channels > 2) {
        throw new IOException("Unsupported sample rate or channel count: " + format);
      }
      AudioFormat pcm = new AudioFormat(rate, 16, channels, true, false);
      if (!AudioSystem.isConversionSupported(pcm, format)) {
        throw new IOException("No PCM decoder for " + format);
      }
      return AudioSystem.getAudioInputStream(pcm, source);
    } catch (UnsupportedAudioFileException | IllegalArgumentException e) {
      if (source != null) source.close();
      throw new IOException("Unsupported or damaged audio file: " + path.getFileName(), e);
    } catch (IOException e) {
      if (source != null) source.close();
      throw e;
    }
  }
}
