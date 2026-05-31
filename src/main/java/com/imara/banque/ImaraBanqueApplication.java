package com.imara.banque;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Point d'entrée de l'application Imara Banque.
 * Équivalent de manage.py + config/wsgi.py Django.
 *
 * @EnableScheduling → active le scheduler Spring qui remplace Celery Beat.
 */
@SpringBootApplication
@EnableScheduling
public class ImaraBanqueApplication {

    public static void main(String[] args) {
        SpringApplication.run(ImaraBanqueApplication.class, args);
    }
}
