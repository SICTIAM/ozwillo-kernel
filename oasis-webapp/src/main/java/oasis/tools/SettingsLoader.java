package oasis.tools;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

public class SettingsLoader {
  private static final Logger logger = LoggerFactory.getLogger(SettingsLoader.class);

  public static Config load(Path path) {
    Config applicationConfig = ConfigFactory.empty();
    if (path != null) {
      if (Files.isRegularFile(path) && Files.isReadable(path)) {
        applicationConfig = ConfigFactory.parseFileAnySyntax(path.toFile())
            .withFallback(ConfigFactory.parseMap(Collections.singletonMap("oasis.conf-dir", path.getParent().toAbsolutePath().toString())));
      } else {
        logger.warn("Configuration file not found or not readable. Using default configuration.");
      }
    } else {
      logger.debug("No configuration file specified. Using default configuration.");
    }
    // TODO: handle fallback manually, to fallback with a warning in case of illegal values, instead of throwing
    return ConfigFactory.defaultOverrides()
        .withFallback(applicationConfig)
        .withFallback(ConfigFactory.defaultReference())
        .resolve();
  }
}