# Imara Banque — Spring Boot

Conversion complète du projet Django vers Spring Boot 3.3 / Java 21.

## Correspondance Django → Spring Boot

| Django | Spring Boot |
|--------|-------------|
| `models.py` | Entités JPA (`model/`) |
| `views.py` | Controllers (`controller/`) |
| `urls.py` | `@RequestMapping` dans les controllers |
| `forms.py` | Validation Bean Validation (`@Valid`) |
| `apps/*/migrations/` | Hibernate DDL (`spring.jpa.hibernate.ddl-auto`) |
| `config/settings.py` | `application.properties` |
| `django.contrib.auth` | Spring Security + `UserDetailsService` |
| `@login_required` | `.anyRequest().authenticated()` (Spring Security) |
| `send_mail()` | `JavaMailSender` dans `EmailService` |
| `Celery Beat + tasks.py` | `@Scheduled` dans `VirementRecurrentScheduler` |
| `request.session` | `HttpSession` Spring |
| `messages.success/error` | `RedirectAttributes` (flash attributes) |
| Templates Django | Templates Thymeleaf |
| `{% url 'name' %}` | `@{/chemin/}` Thymeleaf |
| `{{ variable }}` | `${variable}` Thymeleaf |
| `{% if %}` | `th:if` Thymeleaf |
| `{% for %}` | `th:each` Thymeleaf |
| ReportLab (PDF) | iText 8 |

---

## Structure du projet

```
imara-banque-springboot/
├── pom.xml
├── .env.example
└── src/
    └── main/
        ├── java/com/imara/banque/
        │   ├── ImaraBanqueApplication.java     ← Point d'entrée (@SpringBootApplication)
        │   │
        │   ├── config/
        │   │   ├── SecurityConfig.java         ← Spring Security (auth, CSRF, sessions)
        │   │   └── AppConfig.java              ← Constantes métier (application.properties)
        │   │
        │   ├── model/                          ← Entités JPA (≡ models.py)
        │   │   ├── User.java                   ← Remplace django.contrib.auth.models.User
        │   │   ├── Compte.java                 ← apps/comptes/models.py
        │   │   ├── JournalConnexion.java        ← apps/accounts/models.py
        │   │   ├── TentativeConnexion.java      ← apps/accounts/models.py
        │   │   ├── Transaction.java             ← apps/transactions/models.py
        │   │   └── VirementRecurrent.java       ← apps/transactions/models.py
        │   │
        │   ├── repository/                     ← Couche données (≡ objects.filter/get)
        │   │   ├── UserRepository.java
        │   │   ├── CompteRepository.java
        │   │   ├── JournalConnexionRepository.java
        │   │   ├── TentativeConnexionRepository.java
        │   │   ├── TransactionRepository.java
        │   │   └── VirementRecurrentRepository.java
        │   │
        │   ├── service/                        ← Logique métier
        │   │   ├── AuthService.java            ← accounts/views.py (logique)
        │   │   ├── CompteService.java          ← comptes/views.py + Compte.peut_virer()
        │   │   ├── TransactionService.java     ← transactions/views.py (logique)
        │   │   ├── EmailService.java           ← send_mail() Django
        │   │   └── PdfService.java             ← releve_pdf() Django (iText 8)
        │   │
        │   ├── controller/                     ← Couche HTTP (≡ views.py + urls.py)
        │   │   ├── AuthController.java         ← accounts/urls.py
        │   │   ├── DashboardController.java    ← dashboard/urls.py
        │   │   ├── JournalController.java      ← journal/urls.py
        │   │   ├── CompteController.java       ← comptes/urls.py
        │   │   └── TransactionController.java  ← transactions/urls.py
        │   │
        │   ├── scheduler/
        │   │   └── VirementRecurrentScheduler.java ← Celery Beat + tasks.py
        │   │
        │   └── security/
        │       └── UserDetailsServiceImpl.java ← Backend auth Django
        │
        └── resources/
            ├── application.properties          ← config/settings.py
            ├── templates/                      ← Templates Thymeleaf (≡ templates/ Django)
            │   ├── base.html
            │   ├── accounts/
            │   ├── comptes/
            │   ├── dashboard/
            │   ├── journal/
            │   └── transactions/
            └── static/                         ← Fichiers statiques
                ├── css/
                ├── js/
                └── images/
```

---

## Démarrage rapide

### Prérequis
- Java 21+
- Maven 3.9+
- MySQL 8+

### Configuration

1. Copier `.env.example` en `.env` et renseigner les variables
2. Créer la base de données MySQL :
```sql
CREATE DATABASE imara_banque CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

3. Configurer les variables d'environnement (ou modifier `application.properties`) :
```bash
export DB_NAME=imara_banque
export DB_USER=root
export DB_PASSWORD=votre_mot_de_passe
export EMAIL_HOST_USER=votre@email.com
export EMAIL_HOST_PASSWORD=votre_app_password
export SECRET_KEY=votre-cle-secrete
```

### Lancer l'application

```bash
mvn spring-boot:run
```

L'application sera disponible sur : http://localhost:8080/auth/

### Build production

```bash
mvn clean package -DskipTests
java -jar target/banque-1.0.0.jar
```

---

## Routes principales

| URL Spring Boot | Équivalent Django | Description |
|----------------|-------------------|-------------|
| `GET  /auth/` | `etape1_username` | Saisie du username |
| `POST /auth/` | `etape1_username` | Vérification username |
| `GET  /auth/password/` | `etape2_password` | Saisie mot de passe |
| `POST /auth/password/` | `etape2_password` | Tentative connexion |
| `GET  /auth/bloque/` | `compte_bloque` | Page blocage |
| `POST /auth/deconnexion/` | `deconnexion` | Déconnexion |
| `POST /auth/refresh-session/` | `refresh_session` | Refresh AJAX |
| `GET  /dashboard/` | `dashboard` | Tableau de bord |
| `GET  /comptes/` | `solde` | Solde + graphique |
| `GET  /comptes/releve/{id}/pdf/` | `releve_pdf` | Relevé PDF |
| `GET  /comptes/releve/tous/pdf/` | `releve_pdf_tous` | Relevé global PDF |
| `GET  /transactions/` | `historique` | Historique |
| `GET/POST /transactions/virement/` | `virement` | Effectuer virement |
| `GET/POST /transactions/annuler/{id}/` | `annuler_virement` | Annuler virement |
| `GET/POST /transactions/plafond/{id}/` | `modifier_plafond` | Modifier plafond |
| `GET  /transactions/recurrents/` | `virements_recurrents` | Liste récurrents |
| `GET/POST /transactions/recurrents/creer/` | `creer_virement_recurrent` | Créer récurrent |
| `POST /transactions/recurrents/{id}/toggle/` | `toggle_virement_recurrent` | Pause/Activer |
| `GET/POST /transactions/recurrents/{id}/supprimer/` | `supprimer_virement_recurrent` | Supprimer |
| `GET  /journal/` | `journal` | Journal connexions |

---

## Notes importantes

### Remplacement de Celery
Le scheduler Spring (`@Scheduled`) remplace Celery Beat. Pas besoin de Redis ni de worker séparé.
La fréquence est configurée dans `VirementRecurrentScheduler.java` via l'annotation `@Scheduled(cron = "0 0 * * * *")`.

### Mots de passe existants
Si vous migrez depuis Django, les mots de passe Django utilisent PBKDF2. Spring Security utilise BCrypt.
**Tous les utilisateurs devront réinitialiser leur mot de passe** après migration, OU implémenter un `PasswordEncoder` compatible PBKDF2.

### Tables de la base de données
Hibernate crée automatiquement les tables au démarrage (`ddl-auto=update`).
Pour la production, passer à `ddl-auto=validate` et gérer les migrations avec Flyway.
