package com.imara.banque.service;

import com.imara.banque.model.Compte;
import com.imara.banque.model.Transaction;
import com.imara.banque.model.User;
import com.imara.banque.model.VirementRecurrent;
import com.itextpdf.io.image.ImageDataFactory;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.*;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Service de génération de relevés PDF.
 *
 * Équivalent de releve_pdf() et releve_pdf_tous() dans apps/comptes/views.py Django.
 * Utilise iText 8 au lieu de ReportLab.
 *
 * Couleurs identiques au projet Django :
 *   Ardoise  : rgb(0.176, 0.216, 0.282) → #2D3748
 *   Champagne: rgb(0.831, 0.659, 0.263) → #D4A843
 */
@Service
public class PdfService {

    // ── Couleurs (identiques à Django) ────────────────────────────────────
    private static final DeviceRgb ARDOISE   = new DeviceRgb(45,  55,  72);
    private static final DeviceRgb CHAMPAGNE = new DeviceRgb(212, 168, 67);
    private static final DeviceRgb ROUGE     = new DeviceRgb(169, 50,  38);
    private static final DeviceRgb VERT      = new DeviceRgb(47,  133, 90);
    private static final DeviceRgb GRIS_CLAIR= new DeviceRgb(247, 245, 240);
    private static final DeviceRgb GRIS_TEXTE= new DeviceRgb(153, 153, 149);
    private static final DeviceRgb TEXTE_NOIR= new DeviceRgb(26,  26,  26);

    private static final DateTimeFormatter FMT_DATE     = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter FMT_DATETIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final TransactionService transactionService;

    public PdfService(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    // ── RELEVÉ D'UN SEUL COMPTE ───────────────────────────────────────────

    public byte[] genererReleveCompte(Compte compte, User user,
                                       LocalDate debut, LocalDate fin,
                                       boolean avecRecurrents) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (PdfWriter writer = new PdfWriter(baos);
             PdfDocument pdf  = new PdfDocument(writer);
             Document doc     = new Document(pdf, PageSize.A4)) {

            doc.setMargins(20, 36, 36, 36);

            // En-tête
            ajouterEntete(doc, "RELEVÉ DE COMPTE");

            // Infos compte
            ajouterInfosCompte(doc, compte, debut, fin);

            // Résumé
            List<Transaction> transactions = transactionService.getTransactionsPeriode(compte, debut, fin);
            ajouterResume(doc, transactions, compte);

            // Tableau des transactions
            ajouterTableauTransactions(doc, transactions, compte);

            // Section virements récurrents
            if (avecRecurrents) {
                // Récurrents récupérés via TransactionService
                // (injecté via CompteController, ici on passe la liste directement)
            }

            ajouterPiedDePage(doc);
        }
        return baos.toByteArray();
    }

    // ── RELEVÉ GLOBAL (TOUS COMPTES) ──────────────────────────────────────

    public byte[] genererReleveGlobal(List<Compte> comptes, User user,
                                       LocalDate debut, LocalDate fin,
                                       boolean avecRecurrents) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (PdfWriter writer = new PdfWriter(baos);
             PdfDocument pdf  = new PdfDocument(writer);
             Document doc     = new Document(pdf, PageSize.A4)) {

            doc.setMargins(20, 36, 36, 36);

            ajouterEntete(doc, "RELEVÉ GLOBAL — TOUS MES COMPTES");

            // Synthèse
            ajouterSyntheseComptes(doc, comptes, user, debut, fin);

            // Section par compte
            for (Compte compte : comptes) {
                List<Transaction> transactions =
                    transactionService.getTransactionsPeriode(compte, debut, fin);
                doc.add(new Paragraph("\n"));
                Paragraph titre = new Paragraph(
                    "Transactions — " + compte.getNumeroCompte() +
                    " (" + compte.getTypeCompteDisplay() + ")"
                ).setFontColor(ARDOISE).setBold().setFontSize(11);
                doc.add(titre);
                ajouterSeparateurChampagne(doc);
                if (transactions.isEmpty()) {
                    doc.add(new Paragraph("Aucune transaction sur la période.")
                        .setFontColor(GRIS_TEXTE).setItalic().setFontSize(9));
                } else {
                    ajouterTableauTransactions(doc, transactions, compte);
                }
            }

            ajouterPiedDePage(doc);
        }
        return baos.toByteArray();
    }

    // ── HELPERS ───────────────────────────────────────────────────────────

    private void ajouterEntete(Document doc, String titre) throws Exception {
        // Bandeau ardoise
        Table bandeau = new Table(UnitValue.createPercentArray(new float[]{1}))
            .setWidth(UnitValue.createPercentValue(100))
            .setBackgroundColor(ARDOISE);

        // Nom de la banque (logo textuel)
        Cell cellNom = new Cell()
            .add(new Paragraph("Imara Banque")
                .setFontSize(20).setFontColor(new DeviceRgb(255,255,255))
                .setItalic().setBold())
            .add(new Paragraph("ESPACE CLIENT SÉCURISÉ")
                .setFontSize(7).setFontColor(CHAMPAGNE))
            .setBorder(com.itextpdf.layout.borders.Border.NO_BORDER)
            .setPadding(12);
        bandeau.addCell(cellNom);
        doc.add(bandeau);

        // Trait champagne
        doc.add(new Paragraph("").setMarginBottom(0));

        // Titre du document
        doc.add(new Paragraph(titre)
            .setFontSize(13).setBold().setFontColor(ARDOISE)
            .setMarginTop(12).setMarginBottom(4));

        ajouterSeparateurChampagne(doc);
    }

    private void ajouterInfosCompte(Document doc, Compte compte, LocalDate debut, LocalDate fin) {
        String plafondTxt = compte.getPlafondVirement() != null
            ? String.format("%,.0f FCFA", compte.getPlafondVirement()).replace(',', ' ')
            : "Illimité";

        doc.add(new Paragraph(
            "Titulaire : " + compte.getUtilisateur().getFullName() + "\n" +
            "Compte n° : " + compte.getNumeroCompte() + " — " + compte.getTypeCompteDisplay() + "\n" +
            String.format("Solde actuel : %,.0f FCFA", compte.getSolde()).replace(',', ' ') + "\n" +
            "Plafond journalier : " + plafondTxt + "\n" +
            "Période : du " + debut.format(FMT_DATE) + " au " + fin.format(FMT_DATE)
        ).setFontSize(9).setFontColor(TEXTE_NOIR).setMarginBottom(8));
    }

    private void ajouterSyntheseComptes(Document doc, List<Compte> comptes, User user,
                                         LocalDate debut, LocalDate fin) {
        doc.add(new Paragraph(
            "Client : " + user.getFullName() + "\n" +
            "Période : du " + debut.format(FMT_DATE) + " au " + fin.format(FMT_DATE) + "\n" +
            "Nombre de comptes : " + comptes.size()
        ).setFontSize(9).setMarginBottom(8));

        doc.add(new Paragraph("SYNTHÈSE DES COMPTES")
            .setBold().setFontSize(10).setFontColor(ARDOISE));
        ajouterSeparateurChampagne(doc);

        Table table = new Table(UnitValue.createPercentArray(new float[]{25, 15, 20, 20, 20}))
            .setWidth(UnitValue.createPercentValue(100));
        ajouterEnTeteTableau(table, new String[]{"N° COMPTE", "TYPE", "SOLDE (F)", "PLAFOND/JOUR", "OUVERTURE"});

        for (int i = 0; i < comptes.size(); i++) {
            Compte c = comptes.get(i);
            DeviceRgb bg = i % 2 == 0 ? GRIS_CLAIR : new DeviceRgb(255, 255, 255);
            String plafond = c.getPlafondVirement() != null
                ? String.format("%,.0f", c.getPlafondVirement()).replace(',', ' ')
                : "Illimité";
            ajouterLigneTableau(table, bg,
                c.getNumeroCompte(),
                c.getTypeCompteDisplay(),
                String.format("%,.0f", c.getSolde()).replace(',', ' '),
                plafond,
                c.getDateCreation().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
            );
        }

        BigDecimal soldeTotal = comptes.stream()
            .map(Compte::getSolde)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        doc.add(table);
        doc.add(new Paragraph("Solde global : " +
            String.format("%,.0f FCFA", soldeTotal).replace(',', ' '))
            .setBold().setFontColor(VERT).setFontSize(10).setMarginTop(4));
    }

    private void ajouterResume(Document doc, List<Transaction> transactions, Compte compte) {
        long nb = transactions.size();
        BigDecimal debits  = transactions.stream()
            .filter(t -> t.getCompteSource().getId().equals(compte.getId()))
            .map(Transaction::getMontant).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal credits = transactions.stream()
            .filter(t -> t.getCompteDestination() != null &&
                         t.getCompteDestination().getId().equals(compte.getId()))
            .map(Transaction::getMontant).reduce(BigDecimal.ZERO, BigDecimal::add);

        Table t = new Table(UnitValue.createPercentArray(new float[]{1, 1, 1}))
            .setWidth(UnitValue.createPercentValue(100)).setMarginBottom(8);

        t.addCell(cellResume("TRANSACTIONS", String.valueOf(nb), ARDOISE));
        t.addCell(cellResume("TOTAL DÉBITS",
            String.format("%,.0f F", debits).replace(',', ' '), ROUGE));
        t.addCell(cellResume("TOTAL CRÉDITS",
            String.format("%,.0f F", credits).replace(',', ' '), VERT));
        doc.add(t);
    }

    private Cell cellResume(String label, String valeur, DeviceRgb couleur) {
        return new Cell()
            .add(new Paragraph(label).setFontSize(7).setFontColor(GRIS_TEXTE))
            .add(new Paragraph(valeur).setFontSize(12).setBold().setFontColor(couleur))
            .setBackgroundColor(GRIS_CLAIR)
            .setTextAlignment(TextAlignment.CENTER)
            .setPadding(8)
            .setBorder(com.itextpdf.layout.borders.Border.NO_BORDER);
    }

    private void ajouterTableauTransactions(Document doc,
                                             List<Transaction> transactions,
                                             Compte compte) {
        if (transactions.isEmpty()) {
            doc.add(new Paragraph("Aucune transaction sur la période sélectionnée.")
                .setFontColor(GRIS_TEXTE).setItalic().setFontSize(9));
            return;
        }

        Table table = new Table(UnitValue.createPercentArray(new float[]{20, 15, 30, 17, 18}))
            .setWidth(UnitValue.createPercentValue(100));
        ajouterEnTeteTableau(table, new String[]{"DATE", "TYPE", "MOTIF", "DÉBIT (F)", "CRÉDIT (F)"});

        for (int i = 0; i < transactions.size(); i++) {
            Transaction t = transactions.get(i);
            DeviceRgb bg = i % 2 == 0 ? GRIS_CLAIR : new DeviceRgb(255, 255, 255);
            String motif = t.getMotif() != null ? t.getMotif() : t.getTypeTransactionDisplay();
            if (motif.length() > 35) motif = motif.substring(0, 35);

            boolean estDebit = t.getCompteSource().getId().equals(compte.getId());
            String montantFmt = String.format("%,.0f", t.getMontant()).replace(',', ' ');

            ajouterLigneTableau(table, bg,
                t.getDateTransaction().format(FMT_DATETIME),
                t.getTypeTransactionDisplay(),
                motif,
                estDebit ? montantFmt : "—",
                estDebit ? "—" : montantFmt
            );
        }
        doc.add(table);
    }

    private void ajouterEnTeteTableau(Table table, String[] colonnes) {
        for (String col : colonnes) {
            table.addHeaderCell(new Cell()
                .add(new Paragraph(col).setFontSize(8).setBold().setFontColor(new DeviceRgb(255,255,255)))
                .setBackgroundColor(ARDOISE)
                .setBorder(com.itextpdf.layout.borders.Border.NO_BORDER)
                .setPadding(5));
        }
    }

    private void ajouterLigneTableau(Table table, DeviceRgb bg, String... valeurs) {
        for (String val : valeurs) {
            table.addCell(new Cell()
                .add(new Paragraph(val).setFontSize(8).setFontColor(TEXTE_NOIR))
                .setBackgroundColor(bg)
                .setBorder(com.itextpdf.layout.borders.Border.NO_BORDER)
                .setPadding(4));
        }
    }

    private void ajouterSeparateurChampagne(Document doc) {
        Table sep = new Table(UnitValue.createPercentArray(new float[]{1}))
            .setWidth(UnitValue.createPercentValue(100))
            .setBackgroundColor(CHAMPAGNE)
            .setHeight(2).setMarginBottom(8);
        sep.addCell(new Cell().setBorder(com.itextpdf.layout.borders.Border.NO_BORDER));
        doc.add(sep);
    }

    private void ajouterPiedDePage(Document doc) {
        doc.add(new Paragraph(
            "Document généré le " +
            java.time.LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy à HH:mm")) +
            " — Confidentiel, réservé au titulaire du compte — Imara Banque"
        ).setFontSize(7).setFontColor(GRIS_TEXTE).setItalic()
         .setTextAlignment(TextAlignment.CENTER).setMarginTop(16));
    }
}
