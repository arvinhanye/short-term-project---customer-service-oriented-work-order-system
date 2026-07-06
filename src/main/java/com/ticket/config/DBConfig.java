package com.ticket.config;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public final class DBConfig {
    private static final String CONFIG_FILE = "db.properties";
    private static final Properties PROPERTIES = loadProperties();

    private DBConfig() {
    }

    private static Properties loadProperties() {
        Properties properties = new Properties();
        try (InputStream inputStream = DBConfig.class.getClassLoader().getResourceAsStream(CONFIG_FILE)) {
            if (inputStream == null) {
                throw new IllegalStateException("Missing configuration file: " + CONFIG_FILE);
            }
            properties.load(inputStream);
            return properties;
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to load database configuration", ex);
        }
    }

    public static String get(String key) {
        return PROPERTIES.getProperty(key);
    }

    public static int getInt(String key, int defaultValue) {
        String value = PROPERTIES.getProperty(key);
        return value == null ? defaultValue : Integer.parseInt(value);
    }
}
