package com.imara.banque.controller;

import com.imara.banque.model.JournalConnexion;
import com.imara.banque.model.User;
import com.imara.banque.repository.JournalConnexionRepository;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;


@Controller
@RequestMapping("/journal")
public class JournalController {

    private final JournalConnexionRepository journalRepo;

    public JournalController(JournalConnexionRepository journalRepo) {
        this.journalRepo = journalRepo;
    }

    /** GET /journal/ — Historique des connexions de l'utilisateur. */
    @GetMapping("/")
    public String journal(@AuthenticationPrincipal User user, Model model) {
        List<JournalConnexion> connexions =
            journalRepo.findByUtilisateurOrderByDateConnexionDesc(user);
        model.addAttribute("connexions", connexions);
        return "journal/journal";
    }
}
