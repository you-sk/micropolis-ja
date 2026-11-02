package micropolisj.audio;

/** Non-blocking control surface, independent of audio devices for selection tests. */
public interface MusicPlayer extends AutoCloseable {
  void play(MusicConfig.Track track, int fadeMillis);

  void stop(int fadeMillis);

  void setVolume(double volume);

  void setPaused(boolean paused);

  @Override
  void close();
}
