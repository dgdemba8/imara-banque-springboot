package com.imara.banque.controller;

import com.imara.banque.model.Compte;
import com.imara.banque.model.Transaction;
import com.imara.banque.model.User;
import com.imara.banque.model.VirementRecurrent;
import com.imara.banque.service.CompteService;
import com.imara.banque.service.TransactionService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;


@Controller
@RequestMapping("/transactions")
public class TransactionController {

    private final TransactionService transactionService;
    private final CompteService      compteService;

    public TransactionController(TransactionService transactionService,
                                  CompteService compteService) {
        this.transactionService = transactionService;
        this.compteService      = compteService;
    }


    /** GET /transactions/ */
    @GetMapping("/")
    public String historique(
            @AuthenticationPrincipal User user,
            @RequestParam(required = false) Long compte,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date_debut,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date_fin,
            Model model) {

        List<Compte> comptes = compteService.getComptesActifs(user);

        // Filtrage optionnel par compte
        List<Compte> comptesFiltre = comptes;
        if (compte != null) {
            Optional<Compte> cf = compteService.getCompteActif(compte, user);
            if (cf.isPresent()) comptesFiltre = List.of(cf.get());
        }

        // Filtrage par type
        Transaction.TypeTransaction typeTxn = null;
        if (type != null && !type.isBlank()) {
            try { typeTxn = Transaction.TypeTransaction.valueOf(type); }
            catch (IllegalArgumentException ignored) {}
        }

        List<Transaction> transactions = transactionService.getHistorique(
            comptesFiltre, typeTxn, date_debut, date_fin
        );

        // Enrichissement : peut annuler ?
        final List<Compte> comptesFinaux = comptes;
        final int DELAI = 5; // minutes (depuis AppConfig idéalement)
        transactions.forEach(t ->
            t.setStatut(t.isAnnulable(DELAI) && comptesFinaux.contains(t.getCompteSource()))
        );

        model.addAttribute("transactions",  transactions);
        model.addAttribute("comptes",       comptes);
        model.addAttribute("filtre_compte", compte);
        model.addAttribute("filtre_type",   type);
        model.addAttribute("filtre_debut",  date_debut);
        model.addAttribute("filtre_fin",    date_fin);
        model.addAttribute("nb_resultats",  transactions.size());
        return "transactions/historique";
    }

    @GetMapping("/virement/")
    public String virementGet(@AuthenticationPrincipal User user, Model model) {
        model.addAttribute("comptes", compteService.getComptesActifs(user));
        return "transactions/virement";
    }

    /** POST /transactions/virement/ */
    @PostMapping("/virement/")
    public String virementPost(
            @AuthenticationPrincipal User user,
            @RequestParam Long compte_source,
            @RequestParam String numero_destination,
            @RequestParam String montant,
            @RequestParam(required = false, defaultValue = "") String motif,
            RedirectAttributes redirectAttrs,
            Model model) {

        List<Compte> comptes = compteService.getComptesActifs(user);


        Optional<Compte> source = compteService.getCompteActif(compte_source, user);
        Optional<Compte> dest   = compteService.getCompteParNumero(numero_destination.trim());

        if (source.isEmpty() || dest.isEmpty()) {
            model.addAttribute("erreur", "Compte introuvable.");
            model.addAttribute("comptes", comptes);
            return "transactions/virement";
        }

        BigDecimal montantBd;
        try {
            montantBd = new BigDecimal(montant.trim().replace(" ", "").replace(",", "."));
        } catch (NumberFormatException e) {
            model.addAttribute("erreur", "Montant invalide.");
            model.addAttribute("comptes", comptes);
            return "transactions/virement";
        }

        TransactionService.ResultatVirement result =
            transactionService.effectuerVirement(source.get(), dest.get(), montantBd, motif, user);

        if (result.succes()) {
            redirectAttrs.addFlashAttribute("success", result.message());
            return "redirect:/transactions/";
        } else {
            model.addAttribute("erreur", result.message());
            model.addAttribute("comptes", comptes);
            return "transactions/virement";
        }
    }

    @GetMapping("/annuler/{id}/")
    public String confirmerAnnulation(
            @PathVariable Long id,
            @AuthenticationPrincipal User user,
            RedirectAttributes redirectAttrs,
            Model model) {

        Optional<Transaction> t = transactionService.getTransactionPourAnnulation(id, user);
        if (t.isEmpty()) {
            redirectAttrs.addFlashAttribute("erreur", "Transaction introuvable.");
            return "redirect:/transactions/";
        }
        if (!t.get().isAnnulable(5)) {
            redirectAttrs.addFlashAttribute("erreur",
                "Ce virement ne peut plus être annulé (délai de 5 minutes dépassé).");
            return "redirect:/transactions/";
        }
        model.addAttribute("transaction", t.get());
        return "transactions/confirmer_annulation";
    }

    /** POST /transactions/annuler/{id}/ — exécution annulation */
    @PostMapping("/annuler/{id}/")
    public String executerAnnulation(
            @PathVariable Long id,
            @AuthenticationPrincipal User user,
            RedirectAttributes redirectAttrs) {

        TransactionService.ResultatVirement result = transactionService.annulerVirement(id, user);
        if (result.succes()) {
            redirectAttrs.addFlashAttribute("success", result.message());
        } else {
            redirectAttrs.addFlashAttribute("erreur", result.message());
        }
        return "redirect:/transactions/";
    }

  /** GET /transactions/plafond/{compteId}/ */
    @GetMapping("/plafond/{compteId}/")
    public String modifierPlafondGet(
            @PathVariable Long compteId,
            @AuthenticationPrincipal User user,
            Model model) {

        Compte compte = compteService.getCompteActif(compteId, user)
            .orElseThrow(() -> new RuntimeException("Compte introuvable"));
        model.addAttribute("compte", compte);
        return "transactions/modifier_plafond";
    }

    /** POST /transactions/plafond/{compteId}/ */
    @PostMapping("/plafond/{compteId}/")
    public String modifierPlafondPost(
            @PathVariable Long compteId,
            @AuthenticationPrincipal User user,
            @RequestParam(required = false) String plafond,
            RedirectAttributes redirectAttrs) {

        Compte compte = compteService.getCompteActif(compteId, user)
            .orElseThrow(() -> new RuntimeException("Compte introuvable"));

        if (plafond == null || plafond.isBlank()) {
            compteService.modifierPlafond(compte, null);
            redirectAttrs.addFlashAttribute("success", "Plafond supprimé — aucune limite journalière.");
        } else {
            try {
                BigDecimal val = new BigDecimal(plafond.trim().replace(" ", "").replace(",", "."));
                if (val.compareTo(BigDecimal.ZERO) <= 0) throw new NumberFormatException();
                compteService.modifierPlafond(compte, val);
                redirectAttrs.addFlashAttribute("success",
                    String.format("Plafond journalier fixé à %,.0f FCFA.", val));
            } catch (NumberFormatException e) {
                redirectAttrs.addFlashAttribute("erreur", "Valeur invalide pour le plafond.");
            }
        }
        return "redirect:/comptes/";
    }

    /** GET /transactions/recurrents/ */
    @GetMapping("/recurrents/")
    public String virementsRecurrents(@AuthenticationPrincipal User user, Model model) {
        model.addAttribute("comptes",    compteService.getComptesActifs(user));
        model.addAttribute("recurrents", transactionService.getVirements(user));
        return "transactions/virements_recurrents";
    }

    /** GET /transactions/recurrents/creer/ */
    @GetMapping("/recurrents/creer/")
    public String creerGet(@AuthenticationPrincipal User user, Model model) {
        model.addAttribute("comptes", compteService.getComptesActifs(user));
        return "transactions/creer_virement_recurrent";
    }

    /** POST /transactions/recurrents/creer/ */
    @PostMapping("/recurrents/creer/")
    public String creerPost(
            @AuthenticationPrincipal User user,
            @RequestParam Long compte_source,
            @RequestParam String numero_destination,
            @RequestParam String montant,
            @RequestParam(required = false, defaultValue = "") String motif,
            @RequestParam(defaultValue = "mensuel") String frequence,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date_debut,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date_fin,
            RedirectAttributes redirectAttrs,
            Model model) {

        List<Compte> comptes = compteService.getComptesActifs(user);

        Optional<Compte> source = compteService.getCompteActif(compte_source, user);
        Optional<Compte> dest   = compteService.getCompteParNumero(numero_destination.trim());

        if (source.isEmpty() || dest.isEmpty()) {
            model.addAttribute("erreur", "Compte introuvable.");
            model.addAttribute("comptes", comptes);
            return "transactions/creer_virement_recurrent";
        }

        BigDecimal montantBd;
        try {
            montantBd = new BigDecimal(montant.trim().replace(" ", "").replace(",", "."));
        } catch (NumberFormatException e) {
            model.addAttribute("erreur", "Données invalides. Vérifiez les champs.");
            model.addAttribute("comptes", comptes);
            return "transactions/creer_virement_recurrent";
        }

        if (source.get().getId().equals(dest.get().getId())) {
            model.addAttribute("erreur", "Compte source et destination identiques.");
            model.addAttribute("comptes", comptes);
            return "transactions/creer_virement_recurrent";
        }
        if (montantBd.compareTo(BigDecimal.ZERO) <= 0) {
            model.addAttribute("erreur", "Le montant doit être supérieur à 0.");
            model.addAttribute("comptes", comptes);
            return "transactions/creer_virement_recurrent";
        }
        if (date_fin != null && date_fin.isBefore(date_debut)) {
            model.addAttribute("erreur", "La date de fin doit être postérieure à la date de début.");
            model.addAttribute("comptes", comptes);
            return "transactions/creer_virement_recurrent";
        }

        VirementRecurrent.Frequence freq;
        try { freq = VirementRecurrent.Frequence.valueOf(frequence); }
        catch (IllegalArgumentException e) { freq = VirementRecurrent.Frequence.mensuel; }

        transactionService.creerVirementRecurrent(
            user, source.get(), dest.get(), montantBd, motif, freq, date_debut, date_fin
        );
        redirectAttrs.addFlashAttribute("success", "Virement récurrent programmé avec succès.");
        return "redirect:/transactions/recurrents/";
    }


    /** POST /transactions/recurrents/{id}/toggle/ */
    @PostMapping("/recurrents/{id}/toggle/")
    public String toggle(
            @PathVariable Long id,
            @AuthenticationPrincipal User user,
            RedirectAttributes redirectAttrs) {

        transactionService.toggleVirementRecurrent(id, user)
            .ifPresentOrElse(
                msg -> redirectAttrs.addFlashAttribute("success", msg),
                ()  -> redirectAttrs.addFlashAttribute("erreur", "Virement introuvable.")
            );
        return "redirect:/transactions/recurrents/";
    }


    /** GET /transactions/recurrents/{id}/supprimer/ — page de confirmation */
    @GetMapping("/recurrents/{id}/supprimer/")
    public String supprimerGet(
            @PathVariable Long id,
            @AuthenticationPrincipal User user,
            Model model) {

        VirementRecurrent v = transactionService.getVirementRecurrent(id, user)
            .orElseThrow(() -> new RuntimeException("Virement introuvable"));
        model.addAttribute("virement", v);
        return "transactions/supprimer_virement_recurrent";
    }

    /** POST /transactions/recurrents/{id}/supprimer/ */
    @PostMapping("/recurrents/{id}/supprimer/")
    public String supprimerPost(
            @PathVariable Long id,
            @AuthenticationPrincipal User user,
            RedirectAttributes redirectAttrs) {

        boolean ok = transactionService.supprimerVirementRecurrent(id, user);
        if (ok) redirectAttrs.addFlashAttribute("success", "Virement récurrent supprimé.");
        else    redirectAttrs.addFlashAttribute("erreur", "Virement introuvable.");
        return "redirect:/transactions/recurrents/";
    }
}
