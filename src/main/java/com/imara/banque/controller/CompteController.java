package com.imara.banque.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.imara.banque.model.Compte;
import com.imara.banque.model.Transaction;
import com.imara.banque.model.User;
import com.imara.banque.repository.TransactionRepository;
import com.imara.banque.service.CompteService;
import com.imara.banque.service.PdfService;
import com.imara.banque.service.TransactionService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Contrôleur des comptes bancaires.
 *
 * Équivalent de {@code apps/comptes/views.py} Django :
 *   solde()          -> GET /comptes/
 *   releve_pdf()     -> GET /comptes/releve/{id}/pdf/
 *   releve_pdf_tous() -> GET /comptes/releve/tous/pdf/
 */
@Controller
@RequestMapping("/comptes")
public class CompteController {

    private final CompteService      compteService;
    private final TransactionService transactionService;
    private final PdfService         pdfService;
    private final ObjectMapper       objectMapper;

    // Palette de couleurs identique au Django
    private static final String[][] COULEURS = {
        {"212,168,67",  "#d4a843"},
        {"45,55,72",    "#2d3748"},
        {"74,124,89",   "#4a7c59"},
        {"169,50,38",   "#a93226"},
    };

    public CompteController(CompteService compteService,
                             TransactionService transactionService,
                             PdfService pdfService) {
        this.compteService      = compteService;
        this.transactionService = transactionService;
        this.pdfService         = pdfService;
        this.objectMapper       = new ObjectMapper();
    }



    /** GET /comptes/ */
    @GetMapping({"/", "/solde"})
    public String solde(@AuthenticationPrincipal User user, Model model) throws Exception {
        List<Compte> comptes = compteService.getComptesActifs(user);

        LocalDate aujourd_hui = LocalDate.now();
        LocalDate debut       = aujourd_hui.minusDays(29);

        // Labels des 30 derniers jours
        List<String> labels = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            labels.add(debut.plusDays(i).format(DateTimeFormatter.ofPattern("dd/MM")));
        }

        List<Map<String, Object>> datasets = new ArrayList<>();
        for (int idx = 0; idx < comptes.size(); idx++) {
            Compte compte = comptes.get(idx);
            String[] couleur = COULEURS[idx % COULEURS.length];

            // Transactions des 30 derniers jours pour ce compte
            List<Transaction> txns = transactionService.getTransactionsPeriode(
                compte, debut, aujourd_hui
            );

            // Calcul des deltas par jour (reconstitution historique du solde)
            Map<LocalDate, BigDecimal> deltas = new HashMap<>();
            for (Transaction t : txns) {
                LocalDate jour = t.getDateTransaction().toLocalDate();
                deltas.putIfAbsent(jour, BigDecimal.ZERO);
                if (t.getCompteSource().getId().equals(compte.getId())) {
                    deltas.merge(jour, t.getMontant().negate(), BigDecimal::add);
                }
                if (t.getCompteDestination() != null &&
                    t.getCompteDestination().getId().equals(compte.getId())) {
                    deltas.merge(jour, t.getMontant(), BigDecimal::add);
                }
            }

            // Reconstitution du solde jour par jour en remontant dans le passé
            Map<LocalDate, Double> soldeParDate = new HashMap<>();
            soldeParDate.put(aujourd_hui, compte.getSolde().doubleValue());
            for (int i = 1; i < 30; i++) {
                LocalDate jour      = aujourd_hui.minusDays(i);
                LocalDate jourSuiv  = aujourd_hui.minusDays(i - 1);
                double delta = deltas.getOrDefault(jourSuiv, BigDecimal.ZERO).doubleValue();
                soldeParDate.put(jour,
                    Math.round((soldeParDate.get(jourSuiv) - delta) * 100.0) / 100.0);
            }

            List<Double> dataPoints = new ArrayList<>();
            for (int i = 0; i < 30; i++) {
                dataPoints.add(soldeParDate.getOrDefault(debut.plusDays(i), 0.0));
            }

            Map<String, Object> dataset = new LinkedHashMap<>();
            dataset.put("label",           compte.getNumeroCompte());
            dataset.put("data",            dataPoints);
            dataset.put("borderColor",     couleur[1]);
            dataset.put("backgroundColor", "rgba(" + couleur[0] + ",0.08)");
            dataset.put("borderWidth",     2);
            dataset.put("pointRadius",     3);
            dataset.put("pointHoverRadius",6);
            dataset.put("tension",         0.4);
            dataset.put("fill",            true);
            datasets.add(dataset);
        }

        Map<String, Object> graphData = new LinkedHashMap<>();
        graphData.put("labels",   labels);
        graphData.put("datasets", datasets);

        model.addAttribute("comptes",        comptes);
        model.addAttribute("graphique_data", objectMapper.writeValueAsString(graphData));
        return "comptes/solde";
    }



    /** GET /comptes/releve/{compteId}/pdf/ */
    @GetMapping("/releve/{compteId}/pdf/")
    public ResponseEntity<byte[]> relevePdf(
            @PathVariable Long compteId,
            @AuthenticationPrincipal User user,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date_debut,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date_fin,
            @RequestParam(defaultValue = "true") boolean avec_recurrents) throws Exception {

        Compte compte = compteService.getCompteActif(compteId, user)
            .orElseThrow(() -> new RuntimeException("Compte introuvable"));

        LocalDate debut = date_debut != null ? date_debut : LocalDate.now().minusDays(30);
        LocalDate fin   = date_fin   != null ? date_fin   : LocalDate.now();

        byte[] pdf = pdfService.genererReleveCompte(compte, user, debut, fin, avec_recurrents);

        String filename = "releve_" + compte.getNumeroCompte() + "_" + debut + "_" + fin + ".pdf";
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
            .contentType(MediaType.APPLICATION_PDF)
            .body(pdf);
    }



    /** GET /comptes/releve/tous/pdf/ */
    @GetMapping("/releve/tous/pdf/")
    public ResponseEntity<byte[]> relevePdfTous(
            @AuthenticationPrincipal User user,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date_debut,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date_fin,
            @RequestParam(defaultValue = "true") boolean avec_recurrents) throws Exception {

        List<Compte> comptes = compteService.getComptesActifs(user);
        if (comptes.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        LocalDate debut = date_debut != null ? date_debut : LocalDate.now().minusDays(30);
        LocalDate fin   = date_fin   != null ? date_fin   : LocalDate.now();

        byte[] pdf = pdfService.genererReleveGlobal(comptes, user, debut, fin, avec_recurrents);

        String filename = "releve_global_" + debut + "_" + fin + ".pdf";
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
            .contentType(MediaType.APPLICATION_PDF)
            .body(pdf);
    }
}
