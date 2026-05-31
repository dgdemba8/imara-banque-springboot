package com.imara.banque.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Contrôleur du tableau de bord.
 *
 * Équivalent de {@code apps/dashboard/views.py} Django.
 */
@Controller
@RequestMapping("/dashboard")
public class DashboardController {

    /** GET /dashboard/ — Page d'accueil après connexion. */
    @GetMapping("/")
    public String dashboard() {
        return "dashboard/index";
    }
}
