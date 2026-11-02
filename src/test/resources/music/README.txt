These four test fixtures contain only a generated 440 Hz sine wave.
No third-party music, recordings, ROM data or samples are included.
Generated for this project on 2026-11-02; dedicated to the public domain (CC0-1.0).

Regenerate with FFmpeg (development tool, not a runtime dependency):
ffmpeg -f lavfi -i "sine=frequency=440:duration=0.25:sample_rate=44100" -ac 2 tone.wav
Repeat with output extensions .mp3, .ogg and .flac using the default encoders.
Reference generator: FFmpeg 9.0.2-essentials_build-www.gyan.dev, obtained through imageio-ffmpeg.
CC0: https://creativecommons.org/publicdomain/zero/1.0/
