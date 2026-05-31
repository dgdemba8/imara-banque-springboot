package com.imara.banque.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Configuration Web MVC.
 *
 * Rend les URLs tolérantes au slash final :
 *   /journal  →  accepté même si le mapping est /journal/
 *   /comptes  →  accepté même si le mapping est /comptes/
 *
 * Évite les 404 quand l'utilisateur omet le slash de fin.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void configurePathMatch(PathMatchConfigurer configurer) {
        // Accepte /journal ET /journal/ pour le même controller
        configurer.setUseTrailingSlashMatch(true);
    }
}
