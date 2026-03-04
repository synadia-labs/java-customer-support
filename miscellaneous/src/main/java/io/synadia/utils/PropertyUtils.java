package io.synadia.utils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.Properties;

public class PropertyUtils {

    public static Properties loadProperties(final String propFile) throws IOException {
        Properties properties = new Properties();
        if (propFile == null || propFile.trim().isEmpty()) {
            throw new FileNotFoundException("Empty file name provided to load properties");
        }

        // Try external file first, then classpath
        File externalFile = new File(propFile);
        if (externalFile.exists()) {
            try (InputStream propStream = Files.newInputStream(externalFile.toPath())) {
                properties.load(propStream);
                return properties;
            }
        }

        try (InputStream propStream = PropertyUtils.class.getClassLoader().getResourceAsStream(propFile)) {
            if (propStream == null) {
                throw new FileNotFoundException(
                    String.format("Configuration file [%s] is invalid", propFile));
            }
            properties.load(propStream);
        }
        return properties;
    }
}
