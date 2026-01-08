package com.shop.ecommerceengine.common.config;

import io.github.cdimascio.dotenv.Dotenv;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration class for loading environment variables from .env file.
 * The .env file should be placed in the project root and should be gitignored.
 */
@Configuration
public class DotenvConfig {

    private static final Logger log = LoggerFactory.getLogger(DotenvConfig.class);

    @Bean
    public Dotenv dotenv() {
        Dotenv dotenv = Dotenv.configure()
                .ignoreIfMissing()
                .load();
        log.info("Dotenv configuration loaded successfully");
        return dotenv;
    }
}
