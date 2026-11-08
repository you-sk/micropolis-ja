// This file is part of MicropolisJ.
// Copyright (C) 2013 Jason Long
// Portions Copyright (C) 1989-2007 Electronic Arts Inc.
//
// MicropolisJ is free software; you can redistribute it and/or modify
// it under the terms of the GNU GPLv3, with additional terms.
// See the README file, included in this distribution, for details.

package micropolisj;

import javax.swing.SwingUtilities;
import micropolisj.audio.MusicService;
import micropolisj.gui.MainWindow;
import micropolisj.gui.SplashWindow;

class Micropolis {
  private static void createAndShowGUI(MusicService music) {
    MainWindow win = new MainWindow(music);
    win.setVisible(true);
    win.doNewCity(true);
  }

  public static void main(String[] args) {
    // Resource IO and image preparation happen before entering the Swing event thread.
    var artwork = SplashWindow.loadArtwork();
    SwingUtilities.invokeLater(
        () -> {
          MusicService music = new MusicService();
          SplashWindow.show(
              artwork,
              () -> createAndShowGUI(music),
              (engine, file) -> new MainWindow(engine, music, file).setVisible(true),
              music::close);
        });
  }
}
