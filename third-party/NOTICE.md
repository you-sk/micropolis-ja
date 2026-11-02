# Audio decoder dependencies

This directory accompanies the unmodified dependency JARs in `lib/`.
Their corresponding source JARs are provided in `dependency-sources/` in the distribution ZIP.
The main application source is distributed separately alongside that ZIP.
The libraries remain separate, replaceable JARs. No restriction on modification or reverse engineering
for debugging modifications to these libraries is imposed by this application.

|             Maven artifact              | Version |                                      Authors / origin                                       |                           License                            |
|-----------------------------------------|---------|---------------------------------------------------------------------------------------------|--------------------------------------------------------------|
| com.googlecode.soundlibs:mp3spi         | 1.9.5.4 | JavaZOOM, packaged by Patrik Duditš; https://github.com/pdudits/soundlibs                   | LGPL 2.1 or later                                            |
| com.googlecode.soundlibs:jlayer         | 1.0.1.4 | JavaZOOM; https://github.com/pdudits/soundlibs                                              | LGPL 2.1 or later                                            |
| com.googlecode.soundlibs:tritonus-share | 0.3.7.4 | Tritonus project, Matthias Pfisterer and contributors; https://github.com/pdudits/soundlibs | LGPL 2.1 or later                                            |
| io.github.jseproject:jse-spi-vorbis     | 1.1.0   | JavaSound Enhancement Project, Naoko Mitsurugi, Xiph.Org contributors                       | Xiph.Org BSD variant; additional per-file notices in sources |
| io.github.jseproject:jse-api            | 1.1.0   | JavaSound Enhancement Project, Naoko Mitsurugi                                              | BSD 3-clause                                                 |
| org.gagravarr:vorbis-java-core          | 0.8     | Gagravarr VorbisJava contributors; https://github.com/Gagravarr/VorbisJava                  | BSD 3-clause                                                 |
| org.jflac:jflac-codec                   | 1.5.2   | jFLAC contributors; https://github.com/nguillaumin/jflac                                    | LGPL; historical details in jflac-license-history.txt        |

Full notices and copyright attributions in the supplied sources remain applicable.
The library code is not relicensed merely by inclusion with Micropolis.
LGPL text is supplied in LGPL-2.1.txt and LGPL-3.0.txt. The project's GPL text is at ../LICENSE.
No copyrighted game music or ROM content is included with these libraries or this project.

Source artifacts are available from Maven Central under the same artifact/version paths:
https://repo.maven.apache.org/maven2/
