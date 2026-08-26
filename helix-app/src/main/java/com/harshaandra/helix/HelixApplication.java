package com.harshaandra.helix;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Bootstrap. Entity and repository scanning are declared explicitly because the entities live in
 * helix-domain, not under this class's package — relying on the default "scan from here down"
 * would silently find nothing.
 */
@SpringBootApplication(scanBasePackages = "com.harshaandra.helix")
@EntityScan(basePackages = "com.harshaandra.helix.domain.model")
@EnableJpaRepositories(basePackages = "com.harshaandra.helix.domain.repository")
public class HelixApplication {

    public static void main(String[] args) {
        SpringApplication.run(HelixApplication.class, args);
    }
}
