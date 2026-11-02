package micropolisj.audio;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import org.xiph.vorbis.Block;
import org.xiph.vorbis.Comment;
import org.xiph.vorbis.DspState;
import org.xiph.vorbis.Info;
import org.xiph.vorbis.Packet;
import org.xiph.vorbis.Page;
import org.xiph.vorbis.StreamState;
import org.xiph.vorbis.SyncState;

/** Synchronous streaming adapter. Always drains buffered Ogg pages before reading more input. */
final class VorbisPcmStream extends InputStream {
  private final InputStream input;
  private final SyncState sync = new SyncState();
  private final StreamState stream = new StreamState();
  private final Page page = new Page();
  private final Packet packet = new Packet();
  private final Info info = new Info();
  private final Comment comment = new Comment();
  private final DspState dsp = new DspState();
  private final Block block = new Block();
  private final byte[] pcm = new byte[4096 * 4];
  private Integer serial;
  private int position;
  private int size;
  private boolean closed;
  private boolean eos;

  private VorbisPcmStream(Path file) throws IOException {
    input = Files.newInputStream(file);
    try {
      sync.init();
      info.init();
      comment.init();
      for (int i = 0; i < 3; i++) {
        if (!packet() || Info.synthesis_headerin(info, comment, packet) < 0) {
          throw new IOException("Invalid Ogg Vorbis headers");
        }
      }
      if (info.channels < 1 || info.channels > 2 || info.rate < 8000 || info.rate > 192000) {
        throw new IOException("Unsupported Vorbis rate or channels");
      }
      dsp.synthesis_init(info);
      dsp.block_init(block);
    } catch (Exception e) {
      close();
      throw new IOException("Cannot decode Ogg Vorbis", e);
    }
  }

  static AudioInputStream open(Path file) throws IOException {
    VorbisPcmStream stream = new VorbisPcmStream(file);
    return new AudioInputStream(
        stream,
        new AudioFormat(stream.info.rate, 16, stream.info.channels, true, false),
        AudioSystem.NOT_SPECIFIED);
  }

  private boolean packet() throws IOException {
    int scanned = 0;
    while (true) {
      if (serial != null) {
        int result = stream.packetout(packet);
        if (result == 1) return true;
        if (result < 0) throw new IOException("Damaged Ogg packet");
      }
      int result = sync.pageout(page);
      if (result == 1) {
        if (serial == null) {
          serial = page.serialno();
          stream.init(serial);
        }
        if (serial != page.serialno())
          throw new IOException("Chained Ogg streams are not supported");
        if (stream.pagein(page) < 0) throw new IOException("Damaged Ogg page");
        eos = page.eos();
        continue;
      }
      if (result < 0) throw new IOException("Invalid Ogg page checksum or framing");
      if (eos) return false;
      int offset = sync.buffer(4096);
      int count = input.read(sync.data, offset, 4096);
      if (count < 0) throw new IOException("Truncated Ogg stream");
      if ((scanned += count) > 1_048_576) throw new IOException("Ogg packet exceeds 1 MiB");
      sync.wrote(count);
    }
  }

  @Override
  public int read(byte[] bytes, int offset, int length) throws IOException {
    java.util.Objects.checkFromIndexSize(offset, length, bytes.length);
    if (length == 0) return 0;
    if (closed) throw new IOException("Stream is closed");
    while (position == size) {
      var samples = dsp.synthesis_pcmout(true);
      if (samples.samples > 0) {
        int frames = Math.min(4096, samples.samples);
        size = frames * info.channels * 2;
        position = 0;
        for (int f = 0; f < frames; f++) {
          for (int c = 0; c < info.channels; c++) {
            int sample = (int) Math.round(samples.pcm[c][samples.pcmret + f] * 32767);
            sample = Math.max(-32768, Math.min(32767, sample));
            int at = (f * info.channels + c) * 2;
            pcm[at] = (byte) sample;
            pcm[at + 1] = (byte) (sample >> 8);
          }
        }
        dsp.synthesis_read(frames);
        break;
      }
      if (!packet()) return -1;
      if (block.synthesis(packet) != 0 || dsp.synthesis_blockin(block) != 0) {
        throw new IOException("Damaged Vorbis audio packet");
      }
    }
    int count = Math.min(length, size - position);
    System.arraycopy(pcm, position, bytes, offset, count);
    position += count;
    return count;
  }

  @Override
  public int read() throws IOException {
    byte[] one = new byte[1];
    return read(one, 0, 1) < 0 ? -1 : one[0] & 255;
  }

  @Override
  public void close() throws IOException {
    if (!closed) {
      closed = true;
      try {
        input.close();
      } finally {
        block.clear();
        dsp.clear();
        info.clear();
        comment.clear();
        stream.clear();
        sync.clear();
      }
    }
  }
}
