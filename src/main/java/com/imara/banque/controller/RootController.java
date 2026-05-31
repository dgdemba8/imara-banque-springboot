package com.imara.banque.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Redirige la racine / vers la page de connexion.
 * Équivalent de la route "" dans config/urls.py Django.
 */
@Controller
public class RootController {

    @GetMapping("/")
    public String root() {
        return "redirect:/auth/";
    }
}