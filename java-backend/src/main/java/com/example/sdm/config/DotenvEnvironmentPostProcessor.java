package com.example.sdm.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * Custom EnvironmentPostProcessor to load the .env file at startup.
 * Compatible with Spring Boot 3.x and 4.x service loader mechanism.
 */
public class DotenvEnvironmentPostProcessor implements EnvironmentPostProcessor {

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        Path envPath = Paths.get(".env");
        
        // If not found in current directory, check java-backend/.env (useful if started from root project directory)
        if (!Files.exists(envPath)) {
            envPath = Paths.get("java-backend/.env");
        }
        
        // If still not found, check parent directory (useful for nested test/build directories)
        if (!Files.exists(envPath)) {
            envPath = Paths.get("../.env");
        }

        if (Files.exists(envPath)) {
            try {
                Properties properties = new Properties();
                try (InputStream inputStream = Files.newInputStream(envPath)) {
                    properties.load(inputStream);
                }

                Map<String, Object> dotenvMap = new HashMap<>();
                for (String name : properties.stringPropertyNames()) {
                    String value = properties.getProperty(name);
                    if (value != null) {
                        value = value.trim();
                        // Remove surrounding single or double quotes
                        if ((value.startsWith("\"") && value.endsWith("\"")) || (value.startsWith("'") && value.endsWith("'"))) {
                            value = value.substring(1, value.length() - 1);
                        }
                        dotenvMap.put(name, value);
                    }
                }

                if (!dotenvMap.isEmpty()) {
                    // Load .env properties as highest priority or standard system property source
                    environment.getPropertySources().addFirst(new MapPropertySource("dotenvProperties", dotenvMap));
                    System.out.println(">>> Loaded environment properties from " + envPath.toAbsolutePath());
                }
            } catch (Exception e) {
                System.err.println(">>> Failed to load .env file: " + e.getMessage());
            }
        } else {
            System.out.println(">>> No .env file found at: " + envPath.toAbsolutePath() + ". Using system environment variables.");
        }
    }
}
