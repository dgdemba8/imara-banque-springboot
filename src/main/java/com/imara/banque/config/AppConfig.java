package com.imara.banque.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;


@Configuration
public class AppConfig {

    /** Seuil (en FCFA) au-delà duquel un email de confirmation est envoyé. */
    @Value("${app.virement.seuil-email:200000}")
    public long seuilEmailVirement;

    /** Délai en minutes pendant lequel un virement peut être annulé. */
    @Value("${app.virement.delai-annulation-minutes:5}")
    public int delaiAnnulationMinutes;

    /** Nombre max de tentatives de connexion avant blocage. */
    @Value("${app.connexion.max-tentatives:3}")
    public int maxTentatives;

    /** Durée de blocage en minutes après trop de tentatives échouées. */
    @Value("${app.connexion.duree-blocage-minutes:15}")
    public int dureeBlocageMinutes;

    /** Fenêtre de temps (en minutes) pour détecter les connexions multiples. */
    @Value("${app.connexion.fenetre-alerte-minutes:10}")
    public int fenetreAlerteMinutes;

    /** Nombre de connexions dans la fenêtre déclenchant l'alerte. */
    @Value("${app.connexion.nb-connexions-alerte:2}")
    public int nbConnexionsAlerte;
}
