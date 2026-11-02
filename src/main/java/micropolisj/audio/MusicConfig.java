package micropolisj.audio;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/** An external, local-only music pack. Paths are relative to its XML file. */
public record MusicConfig(
    double volume, int fadeMillis, boolean pauseWithGame, List<Track> tracks, List<Rule> rules) {
  public enum Scene {
    TITLE,
    CITY,
    DISASTER,
    EVALUATION_GOOD,
    EVALUATION_BAD,
    REWARD
  }

  public record Track(String id, Path file, boolean loop, double seconds) {}

  public record Rule(Scene scene, int cityClass, String track, int priority, double seconds) {}

  public MusicConfig {
    tracks = List.copyOf(tracks);
    rules = List.copyOf(rules);
  }

  public Track select(Scene scene, int cityClass) {
    Rule rule = selectRule(scene, cityClass);
    return rule == null
        ? null
        : tracks.stream().filter(t -> t.id().equals(rule.track())).findFirst().orElse(null);
  }

  public Rule selectRule(Scene scene, int cityClass) {
    Rule best = null;
    for (Rule rule : rules) {
      if (rule.scene() != scene || (rule.cityClass() >= 0 && rule.cityClass() != cityClass))
        continue;
      if (best == null
          || rule.priority() > best.priority()
          || (rule.priority() == best.priority()
              && rule.cityClass() >= 0
              && best.cityClass() < 0)) {
        best = rule;
      }
    }
    return best;
  }

  public static MusicConfig load(Path file) throws IOException {
    Path absolute = file.toAbsolutePath().normalize();
    if (!Files.isRegularFile(absolute) || Files.size(absolute) > 1_048_576) {
      throw new IOException("Music XML must be a regular file of at most 1 MiB");
    }
    try (InputStream input = Files.newInputStream(absolute)) {
      DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
      factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
      factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
      factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
      factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
      factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
      factory.setXIncludeAware(false);
      factory.setExpandEntityReferences(false);
      var builder = factory.newDocumentBuilder();
      builder.setErrorHandler(
          new DefaultHandler() {
            @Override
            public void error(org.xml.sax.SAXParseException e) throws SAXException {
              throw e;
            }

            @Override
            public void fatalError(org.xml.sax.SAXParseException e) throws SAXException {
              throw e;
            }
          });
      Element root = builder.parse(input).getDocumentElement();
      if (!root.getTagName().equals("music")) throw new IOException("Expected <music>");
      checkAttributes(root, "volume", "fadeMillis", "pauseWithGame");
      double volume = number(root, "volume", .6, 0, 1);
      int fade = (int) integer(root, "fadeMillis", 1000, 0, 10000);
      boolean pause = bool(root, "pauseWithGame", false);
      Map<String, Track> tracks = new HashMap<>();
      List<Rule> rules = new ArrayList<>();
      for (Node child = root.getFirstChild(); child != null; child = child.getNextSibling()) {
        if (!(child instanceof Element e)) continue;
        if (tracks.size() + rules.size() >= 256) throw new IOException("Too many music entries");
        if (e.getElementsByTagName("*").getLength() != 0)
          throw new IOException("Nested entries are not allowed");
        switch (e.getTagName()) {
          case "track" -> {
            checkAttributes(e, "id", "file", "loop", "seconds");
            String id = required(e, "id");
            Path path = absolute.getParent().resolve(required(e, "file")).normalize();
            if (tracks.putIfAbsent(
                    id,
                    new Track(id, path, bool(e, "loop", true), number(e, "seconds", 0, 0, 86400)))
                != null) throw new IOException("Duplicate track: " + id);
          }
          case "rule" -> {
            checkAttributes(e, "scene", "cityClass", "track", "priority", "seconds");
            Scene scene = Scene.valueOf(required(e, "scene").toUpperCase(java.util.Locale.ROOT));
            int cityClass = (int) integer(e, "cityClass", -1, -1, 5);
            if (scene != Scene.CITY && cityClass != -1)
              throw new IOException("cityClass is only valid for city rules");
            rules.add(
                new Rule(
                    scene,
                    cityClass,
                    required(e, "track"),
                    (int) integer(e, "priority", 0, -10000, 10000),
                    number(e, "seconds", 15, .1, 3600)));
          }
          default -> throw new IOException("Unknown music element: " + e.getTagName());
        }
      }
      for (Rule rule : rules) {
        if (!tracks.containsKey(rule.track()))
          throw new IOException("Unknown track: " + rule.track());
      }
      return new MusicConfig(volume, fade, pause, new ArrayList<>(tracks.values()), rules);
    } catch (IOException e) {
      throw e;
    } catch (Exception e) {
      throw new IOException("Invalid music configuration: " + e.getMessage(), e);
    }
  }

  private static void checkAttributes(Element e, String... names) throws IOException {
    var allowed = java.util.Set.of(names);
    for (int i = 0; i < e.getAttributes().getLength(); i++) {
      String name = e.getAttributes().item(i).getNodeName();
      if (!allowed.contains(name)) throw new IOException("Unknown attribute: " + name);
    }
  }

  private static String required(Element e, String key) throws IOException {
    String value = e.getAttribute(key).trim();
    if (value.isEmpty()) throw new IOException("Missing " + key);
    return value;
  }

  private static boolean bool(Element e, String key, boolean fallback) throws IOException {
    if (!e.hasAttribute(key)) return fallback;
    return switch (e.getAttribute(key)) {
      case "true" -> true;
      case "false" -> false;
      default -> throw new IOException("Invalid boolean: " + key);
    };
  }

  private static long integer(Element e, String key, long fallback, long min, long max)
      throws IOException {
    double value = number(e, key, fallback, min, max);
    if (value != Math.rint(value)) throw new IOException("Expected an integer: " + key);
    return (long) value;
  }

  private static double number(Element e, String key, double fallback, double min, double max)
      throws IOException {
    double value;
    try {
      value = e.hasAttribute(key) ? Double.parseDouble(e.getAttribute(key)) : fallback;
    } catch (NumberFormatException ex) {
      throw new IOException("Invalid number: " + key, ex);
    }
    if (!Double.isFinite(value) || value < min || value > max)
      throw new IOException("Out of range: " + key);
    return value;
  }
}
