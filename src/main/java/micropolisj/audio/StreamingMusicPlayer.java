package micropolisj.audio;

import java.io.IOException;
import java.util.function.Consumer;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;

/** A single bounded-buffer audio worker; commands never wait for decoding or an audio device. */
public final class StreamingMusicPlayer implements MusicPlayer {
  private record Request(MusicConfig.Track track, int fadeMillis) {}

  private final Consumer<String> errors;
  private final Thread worker;
  private volatile Request request = new Request(null, 0);
  private volatile boolean closed;
  private volatile boolean paused;
  private volatile double volume = .6;
  private volatile SourceDataLine output;

  public StreamingMusicPlayer(Consumer<String> errors) {
    this.errors = errors;
    worker = new Thread(this::run, "micropolis-music");
    worker.setDaemon(true);
    worker.start();
  }

  @Override
  public synchronized void play(MusicConfig.Track track, int fadeMillis) {
    if (closed) return;
    request = new Request(track, fadeMillis);
    notifyAll();
  }

  @Override
  public void stop(int fadeMillis) {
    play(null, fadeMillis);
  }

  @Override
  public void setVolume(double value) {
    volume = Math.max(0, Math.min(1, value));
  }

  @Override
  public void setPaused(boolean value) {
    paused = value;
  }

  private void run() {
    Request completed = null;
    while (!closed) {
      Request started;
      synchronized (this) {
        try {
          while (!closed && (request == completed || request.track() == null)) wait();
        } catch (InterruptedException e) {
          if (closed) return;
        }
        if (closed) return;
        started = request;
      }
      try {
        stream(started);
      } catch (Exception e) {
        if (!closed && request == started) {
          errors.accept(started.track().file().getFileName() + ": " + e.getMessage());
        }
      }
      completed = started;
    }
  }

  private void stream(Request started) throws Exception {
    MusicConfig.Track track = started.track();
    long totalFrames = 0;
    long fadeOutFrames = -1;
    long fadedFrames = 0;
    double gain = 0;
    try (AudioInputStream initial = AudioDecoder.open(track.file())) {
      AudioFormat format = initial.getFormat();
      try (SourceDataLine line = AudioSystem.getSourceDataLine(format)) {
        output = line;
        line.open(
            format,
            Math.max(
                format.getFrameSize(), (int) (format.getFrameRate() / 20) * format.getFrameSize()));
        line.start();
        AudioInputStream decoded = initial;
        try {
          byte[] buffer = new byte[1024 * format.getFrameSize()];
          long loopFrames = 0;
          long fadeInFrames = (long) (format.getFrameRate() * started.fadeMillis() / 1000);
          double seconds = track.seconds();
          if (seconds == 0
              && track.file().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".spc"))
            seconds = 180;
          long limit =
              seconds > 0 ? Math.max(1, (long) (format.getFrameRate() * seconds)) : Long.MAX_VALUE;
          while (!closed) {
            if (request != started && fadeOutFrames < 0) {
              if (paused) break;
              fadeOutFrames = (long) (format.getFrameRate() * request.fadeMillis() / 1000);
            }
            if (fadeOutFrames >= 0 && fadedFrames >= fadeOutFrames) break;
            if (paused && request == started) {
              line.stop();
              Thread.sleep(10);
              continue;
            }
            line.start();
            int frames = (int) Math.min(1024, limit - loopFrames);
            int count = frames == 0 ? -1 : decoded.read(buffer, 0, frames * format.getFrameSize());
            if (count < 0) {
              if (!track.loop() || request != started) {
                // Drain in short polls so a replacement/close can always interrupt the tail.
                while (!closed && request == started && line.available() < line.getBufferSize()) {
                  if (paused) {
                    line.stop();
                  } else {
                    line.start();
                  }
                  Thread.sleep(10);
                }
                break;
              }
              if (loopFrames == 0) throw new IOException("Empty audio stream");
              decoded.close();
              decoded = AudioDecoder.open(track.file());
              if (!decoded.getFormat().matches(format))
                throw new IOException("Audio format changed while looping");
              loopFrames = 0;
              continue;
            }
            if (count == 0) throw new IOException("Decoder returned no audio");
            int actualFrames = count / format.getFrameSize();
            for (int frame = 0; frame < actualFrames; frame++) {
              double envelope =
                  fadeInFrames == 0 ? 1 : Math.min(1, (double) totalFrames / fadeInFrames);
              if (fadeOutFrames >= 0)
                envelope *= Math.max(0, 1 - (double) fadedFrames / Math.max(1, fadeOutFrames));
              double target = volume * envelope;
              // Smooth volume-slider changes without depending on hardware gain controls.
              gain += (target - gain) * .005;
              scaleFrame(buffer, frame * format.getFrameSize(), format.getChannels(), gain);
              totalFrames++;
              if (fadeOutFrames >= 0) fadedFrames++;
            }
            line.write(buffer, 0, count);
            loopFrames += actualFrames;
          }
        } finally {
          decoded.close();
          line.stop();
          line.flush();
        }
      } finally {
        output = null;
      }
    }
  }

  public static void scaleFrame(byte[] pcm, int offset, int channels, double gain) {
    for (int c = 0; c < channels; c++) {
      int i = offset + c * 2;
      short sample = (short) ((pcm[i] & 255) | (pcm[i + 1] << 8));
      int scaled = (int) Math.round(sample * gain);
      pcm[i] = (byte) scaled;
      pcm[i + 1] = (byte) (scaled >> 8);
    }
  }

  @Override
  public synchronized void close() {
    closed = true;
    notifyAll();
    worker.interrupt();
    SourceDataLine line = output;
    if (line != null) line.close();
  }
}
