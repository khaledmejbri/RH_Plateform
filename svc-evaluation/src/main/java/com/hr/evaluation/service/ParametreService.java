package com.hr.evaluation.service;

import com.hr.evaluation.domain.ActionJournalParametre;
import com.hr.evaluation.domain.NatureParametre;
import com.hr.evaluation.domain.QuestionType;
import com.hr.evaluation.domain.ReferentielEvaluation;
import com.hr.evaluation.domain.ValeursInitialesEvaluation;
import com.hr.evaluation.domain.ValeursInitialesEvaluation.Descripteur;
import com.hr.evaluation.dto.ParametreDto;
import com.hr.evaluation.entity.ParametreValeur;
import com.hr.evaluation.repository.AlerteEcartRepository;
import com.hr.evaluation.repository.EvaluationQuestionRepository;
import com.hr.evaluation.repository.ParametreValeurRepository;
import com.hr.evaluation.service.ParametreJournalService.Auteur;
import com.hr.evaluation.web.EvaluationMetierException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Service générique du paramétrage des référentiels de svc-evaluation (ADR lot 3 §§ 5 et 6) : création,
 * modification, désactivation, réactivation, ordre, suppression, journal. Les règles propres aux niveaux d'alerte
 * (D-R04) y sont appliquées. Toutes les écritures sont tracées au journal dans la même transaction et invalident
 * le cache après commit.
 */
@Service
public class ParametreService {

    public static final String REFERENTIEL_INCONNU = "REFERENTIEL_INCONNU";
    public static final String PARAMETRE_INTROUVABLE = "PARAMETRE_INTROUVABLE";
    public static final String LIBELLE_INVALIDE = "LIBELLE_INVALIDE";
    public static final String LIBELLE_EN_DOUBLE = "LIBELLE_EN_DOUBLE";
    public static final String DESCRIPTION_TROP_LONGUE = "DESCRIPTION_TROP_LONGUE";
    public static final String COULEUR_INVALIDE = "COULEUR_INVALIDE";
    public static final String VERSION_OBLIGATOIRE = "VERSION_OBLIGATOIRE";
    public static final String PARAMETRE_MODIFIE_ENTRE_TEMPS = "PARAMETRE_MODIFIE_ENTRE_TEMPS";
    public static final String PARAMETRE_SYSTEME = "PARAMETRE_SYSTEME";
    public static final String PARAMETRE_COMPORTEMENT = "PARAMETRE_COMPORTEMENT";
    public static final String PARAMETRE_UTILISE = "PARAMETRE_UTILISE";
    public static final String ORDRE_INCOMPLET = "ORDRE_INCOMPLET";
    public static final String SEUIL_INVALIDE = "SEUIL_INVALIDE";
    public static final String SEUIL_EN_DOUBLE = "SEUIL_EN_DOUBLE";
    public static final String NIVEAUX_ACTIFS_MAX = "NIVEAUX_ACTIFS_MAX";
    public static final String NIVEAU_ACTIF_MIN = "NIVEAU_ACTIF_MIN";

    public static final int LIBELLE_MIN = 2;
    public static final int LIBELLE_MAX = 80;
    public static final int DESCRIPTION_MAX = 300;

    /** Palette fermée (ADR § 5), le rendu WCAG AA est à la charge des fronts. */
    public static final Set<String> PALETTE =
            Set.of("NEUTRE", "BLEU", "CYAN", "VERT", "AMBRE", "ORANGE", "ROUGE", "VIOLET");

    /** Référentiels visibles d'un utilisateur non back-office (liste blanche, ADR § 7). */
    static final Set<String> LISTE_BLANCHE_MOBILE = Set.of(
            ReferentielEvaluation.STATUT_EVALUATION, ReferentielEvaluation.ECHELLE_ETOILES,
            ReferentielEvaluation.APPRECIATION);

    private final ParametreValeurRepository repository;
    private final ParametreJournalService journal;
    private final ParametresCache cache;
    private final AlerteEcartRepository alertes;
    private final EvaluationQuestionRepository questions;

    public ParametreService(ParametreValeurRepository repository, ParametreJournalService journal,
                            ParametresCache cache, AlerteEcartRepository alertes,
                            EvaluationQuestionRepository questions) {
        this.repository = repository;
        this.journal = journal;
        this.cache = cache;
        this.alertes = alertes;
        this.questions = questions;
    }

    // ----------------------------------------------------------------------------------------------- lecture

    public List<ParametreDto.Descripteur> referentiels() {
        return ValeursInitialesEvaluation.DESCRIPTEURS.stream()
                .map(d -> new ParametreDto.Descripteur(d.referentiel(), d.libelle(), d.domaine(), d.nature().name(),
                        new ParametreDto.Operations(d.creer(), d.supprimer(), d.desactiver()),
                        d.attributs().stream().map(a -> new ParametreDto.Attribut(a.nom(), a.type())).toList()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ParametreDto.Valeur> valeurs(String referentiel) {
        Descripteur d = descripteur(referentiel);
        return repository.findByReferentielOrderByOrdreAscCodeAsc(d.referentiel()).stream()
                .map(this::versDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public ParametreDto.PageJournal journal(String referentiel, int page, int taille) {
        return journal.lire(descripteur(referentiel).referentiel(), page, taille);
    }

    /**
     * Libellés servis aux fronts depuis l'instantané en cache. Un utilisateur non back-office ne reçoit que les valeurs
     * actives de la liste blanche, sans description.
     */
    public ParametreDto.Libelles libelles(boolean backoffice, boolean actifsSeulement, ParametresInstantane instantane) {
        boolean actifs = actifsSeulement || !backoffice;
        Map<String, List<ParametreDto.LibelleValeur>> resultat = new LinkedHashMap<>();
        for (Descripteur d : ValeursInitialesEvaluation.DESCRIPTEURS) {
            if (!backoffice && !LISTE_BLANCHE_MOBILE.contains(d.referentiel())) {
                continue;
            }
            List<ParametreDto.LibelleValeur> valeurs = instantane.valeurs(d.referentiel()).stream()
                    .filter(v -> !actifs || v.actif())
                    .map(v -> new ParametreDto.LibelleValeur(v.code(), v.libelle(),
                            backoffice ? v.description() : null, v.couleur(), v.ordre(), v.actif(), v.seuilEcart()))
                    .toList();
            resultat.put(d.referentiel(), valeurs);
        }
        String variante = (backoffice ? "b" : "m") + (actifs ? "a" : "t");
        return new ParametreDto.Libelles("W/\"" + instantane.empreinte() + variante + "\"", resultat);
    }

    // ---------------------------------------------------------------------------------------------- création

    @Transactional
    public ParametreDto.Valeur creer(String referentiel, ParametreDto.CorpsValeur corps, Auteur auteur) {
        Descripteur d = descripteur(referentiel);
        if (d.nature() == NatureParametre.COMPORTEMENT) {
            throw comportement("Les valeurs de « " + d.libelle() + " » sont fixées par l'application : "
                    + "seuls le libellé, l'aide, la couleur et l'ordre se modifient.");
        }
        String libelle = libelleValide(corps.libelle());
        String description = descriptionValide(corps.description());
        String couleur = couleurValide(corps.couleur());
        verifierLibelleLibre(d.referentiel(), libelle, null);

        Integer seuil = null;
        if (ReferentielEvaluation.NIVEAU_ALERTE.equals(d.referentiel())) {
            seuil = seuilValide(corps.seuilEcart());
            if (repository.countByReferentielAndActifTrue(d.referentiel()) >= ReferentielEvaluation.NIVEAUX_ACTIFS_MAX) {
                throw EvaluationMetierException.unprocessable(NIVEAUX_ACTIFS_MAX,
                        "Quatre niveaux d'alerte sont déjà actifs : désactivez-en un avant d'en créer un autre.");
            }
            verifierSeuilLibre(d.referentiel(), seuil, null, 400);
        }

        ParametreValeur v = new ParametreValeur();
        v.setReferentiel(d.referentiel());
        v.setCode(CodeParametre.depuisLibelle(libelle, c -> repository.existsByReferentielAndCode(d.referentiel(), c)));
        v.setNature(d.nature());
        v.setLibelle(libelle);
        v.setLibelleNormalise(CodeParametre.normaliser(libelle));
        v.setDescription(description);
        v.setCouleur(couleur);
        v.setOrdre(corps.ordre() != null ? corps.ordre() : prochainOrdre(d.referentiel()));
        v.setActif(true);
        v.setSysteme(false);
        v.setSeuilEcart(seuil);
        tracerAuteur(v, auteur);
        sauver(v, 400);

        journal.ecrire(d.referentiel(), v.getCode(), ActionJournalParametre.CREATION, null, instantane(v), auteur);
        cache.invaliderApresCommit();
        return versDto(v);
    }

    // -------------------------------------------------------------------------------------- modification

    @Transactional
    public ParametreDto.Valeur modifier(String referentiel, String code, ParametreDto.CorpsValeur corps, Auteur auteur) {
        Descripteur d = descripteur(referentiel);
        ParametreValeur v = charger(d.referentiel(), code);
        verifierVersion(v, corps.version());
        Map<String, Object> avant = instantane(v);

        // null = inchangé ; vide ou espaces = 400 LIBELLE_INVALIDE
        String libelle = corps.libelle() == null ? v.getLibelle() : libelleValide(corps.libelle());
        // null = inchangée ; chaîne vide = effacer (même sémantique que le référentiel et la présence)
        String description = corps.description() == null ? v.getDescription() : descriptionValide(corps.description());
        String couleur = corps.couleur() == null ? v.getCouleur() : couleurValide(corps.couleur());
        verifierLibelleLibre(d.referentiel(), libelle, v);

        if (ReferentielEvaluation.NIVEAU_ALERTE.equals(d.referentiel()) && corps.seuilEcart() != null
                && !corps.seuilEcart().equals(v.getSeuilEcart())) {
            int seuil = seuilValide(corps.seuilEcart());
            if (v.isActif()) {
                verifierSeuilLibre(d.referentiel(), seuil, v, 400);
            }
            v.setSeuilEcart(seuil);
        }
        v.setLibelle(libelle);
        v.setLibelleNormalise(CodeParametre.normaliser(libelle));
        v.setDescription(description);
        v.setCouleur(couleur);
        if (corps.ordre() != null) {
            v.setOrdre(corps.ordre());
        }
        if (avant.equals(instantane(v))) {
            // Aucun changement : ni écriture, ni entrée de journal, version inchangée
            return versDto(v);
        }
        tracerAuteur(v, auteur);
        sauver(v, 400);

        journal.ecrire(d.referentiel(), v.getCode(), ActionJournalParametre.MODIFICATION, avant, instantane(v), auteur);
        cache.invaliderApresCommit();
        return versDto(v);
    }

    // -------------------------------------------------------------------------- désactivation, réactivation

    @Transactional
    public ParametreDto.Valeur desactiver(String referentiel, String code, Long version, Auteur auteur) {
        Descripteur d = descripteur(referentiel);
        ParametreValeur v = charger(d.referentiel(), code);
        verifierVersion(v, version);
        if (d.nature() == NatureParametre.COMPORTEMENT) {
            if (!ReferentielEvaluation.TYPE_QUESTION.equals(d.referentiel())) {
                throw comportement("Cette valeur est pilotée par une règle de l'application : elle ne peut pas être désactivée.");
            }
            if (ReferentielEvaluation.TYPE_QUESTION_RATING.equals(v.getCode())) {
                throw systeme("Le type « " + v.getLibelle() + " » pilote l'évaluation en étoiles : il reste toujours actif.");
            }
        } else if (v.isSysteme()) {
            throw systeme("« " + v.getLibelle() + " » est une valeur système : elle ne peut pas être désactivée.");
        }
        if (!v.isActif()) {
            return versDto(v);
        }
        if (ReferentielEvaluation.NIVEAU_ALERTE.equals(d.referentiel())
                && repository.countByReferentielAndActifTrue(d.referentiel()) <= 1) {
            throw EvaluationMetierException.unprocessable(NIVEAU_ACTIF_MIN,
                    "Au moins un niveau d'alerte doit rester actif : sinon plus aucune alerte ne serait détectée.");
        }
        Map<String, Object> avant = instantane(v);
        v.setActif(false);
        tracerAuteur(v, auteur);
        sauver(v, 400);
        journal.ecrire(d.referentiel(), v.getCode(), ActionJournalParametre.DESACTIVATION, avant, instantane(v), auteur);
        cache.invaliderApresCommit();
        return versDto(v);
    }

    @Transactional
    public ParametreDto.Valeur reactiver(String referentiel, String code, Long version, Auteur auteur) {
        Descripteur d = descripteur(referentiel);
        ParametreValeur v = charger(d.referentiel(), code);
        verifierVersion(v, version);
        if (d.nature() == NatureParametre.COMPORTEMENT && !ReferentielEvaluation.TYPE_QUESTION.equals(d.referentiel())) {
            throw comportement("Cette valeur est pilotée par une règle de l'application : elle est toujours active.");
        }
        if (v.isActif()) {
            return versDto(v);
        }
        if (ReferentielEvaluation.NIVEAU_ALERTE.equals(d.referentiel())) {
            if (repository.countByReferentielAndActifTrue(d.referentiel()) >= ReferentielEvaluation.NIVEAUX_ACTIFS_MAX) {
                throw EvaluationMetierException.unprocessable(NIVEAUX_ACTIFS_MAX,
                        "Quatre niveaux d'alerte sont déjà actifs : désactivez-en un avant d'en réactiver un autre.");
            }
            verifierSeuilLibre(d.referentiel(), v.getSeuilEcart(), v, 422);
        }
        Map<String, Object> avant = instantane(v);
        v.setActif(true);
        tracerAuteur(v, auteur);
        sauver(v, 422);
        journal.ecrire(d.referentiel(), v.getCode(), ActionJournalParametre.REACTIVATION, avant, instantane(v), auteur);
        cache.invaliderApresCommit();
        return versDto(v);
    }

    // ---------------------------------------------------------------------------------------------- ordre

    @Transactional
    public List<ParametreDto.Valeur> reordonner(String referentiel, List<String> codes, Auteur auteur) {
        Descripteur d = descripteur(referentiel);
        if (ReferentielEvaluation.ECHELLE_ETOILES.equals(d.referentiel())) {
            // La note est l'indice de l'étoile : l'échelle reste triée par code (405, comme les autres actions interdites)
            throw comportement("L'ordre de « " + d.libelle() + " » est fixé par l'application : "
                    + "les étoiles restent classées de 1 à 5.");
        }
        List<ParametreValeur> existantes = repository.findByReferentielOrderByOrdreAscCodeAsc(d.referentiel());
        Set<String> attendus = new LinkedHashSet<>();
        existantes.forEach(v -> attendus.add(v.getCode()));
        if (codes == null || codes.size() != attendus.size() || !attendus.equals(new LinkedHashSet<>(codes))) {
            throw EvaluationMetierException.badRequest(ORDRE_INCOMPLET,
                    "La liste doit contenir une fois chacun des " + attendus.size() + " codes du référentiel.");
        }
        Map<String, ParametreValeur> parCode = new LinkedHashMap<>();
        existantes.forEach(v -> parCode.put(v.getCode(), v));
        Map<String, Object> avant = new LinkedHashMap<>();
        existantes.forEach(v -> avant.put(v.getCode(), v.getOrdre()));
        Map<String, Object> apres = new LinkedHashMap<>();
        int rang = 1;
        for (String c : codes) {
            apres.put(c, rang++);
        }
        if (avant.equals(apres)) {
            // Ordre inchangé : rien à écrire ni à journaliser
            return valeurs(d.referentiel());
        }
        for (String c : codes) {
            ParametreValeur v = parCode.get(c);
            v.setOrdre((Integer) apres.get(c));
            tracerAuteur(v, auteur);
        }
        repository.saveAllAndFlush(existantes);
        journal.ecrire(d.referentiel(), null, ActionJournalParametre.ORDRE, avant, apres, auteur);
        cache.invaliderApresCommit();
        return valeurs(d.referentiel());
    }

    // ------------------------------------------------------------------------------------------ suppression

    @Transactional
    public void supprimer(String referentiel, String code, Long version, Auteur auteur) {
        Descripteur d = descripteur(referentiel);
        ParametreValeur v = charger(d.referentiel(), code);
        if (d.nature() == NatureParametre.COMPORTEMENT) {
            throw comportement("Les valeurs de « " + d.libelle() + " » sont fixées par l'application : "
                    + "elles ne se suppriment pas.");
        }
        if (v.isSysteme()) {
            throw systeme("« " + v.getLibelle() + " » est une valeur système : elle ne peut pas être supprimée.");
        }
        verifierVersion(v, version);
        long utilisations = utilisations(v);
        if (v.getPremiereUtilisationLe() != null || utilisations > 0) {
            String message = utilisations > 0
                    ? "« " + v.getLibelle() + " » est utilisée " + utilisations + " fois : vous pouvez la désactiver."
                    : "« " + v.getLibelle() + " » a déjà été utilisée : vous pouvez la désactiver.";
            throw EvaluationMetierException.conflict(PARAMETRE_UTILISE, message,
                    Map.of("utilisations", utilisations, "deja_utilisee", true));
        }
        if (ReferentielEvaluation.NIVEAU_ALERTE.equals(d.referentiel()) && v.isActif()
                && repository.countByReferentielAndActifTrue(d.referentiel()) <= 1) {
            throw EvaluationMetierException.unprocessable(NIVEAU_ACTIF_MIN,
                    "Au moins un niveau d'alerte doit rester actif : sinon plus aucune alerte ne serait détectée.");
        }
        Map<String, Object> avant = instantane(v);
        repository.delete(v);
        repository.flush();
        journal.ecrire(d.referentiel(), code, ActionJournalParametre.SUPPRESSION, avant, null, auteur);
        cache.invaliderApresCommit();
    }

    // ------------------------------------------------------------------------------------------------ outils

    private Descripteur descripteur(String referentiel) {
        return ValeursInitialesEvaluation.DESCRIPTEURS.stream()
                .filter(d -> d.referentiel().equals(referentiel))
                .findFirst()
                .orElseThrow(() -> EvaluationMetierException.notFound(REFERENTIEL_INCONNU,
                        "Référentiel inconnu : " + referentiel));
    }

    /** Charge la ligne en la verrouillant (course avec la marque « utilisée », ADR § 6). */
    private ParametreValeur charger(String referentiel, String code) {
        return repository.findForUpdate(referentiel, code)
                .orElseThrow(() -> EvaluationMetierException.notFound(PARAMETRE_INTROUVABLE,
                        "Valeur introuvable : " + code));
    }

    private static void verifierVersion(ParametreValeur v, Long version) {
        if (version == null) {
            throw EvaluationMetierException.badRequest(VERSION_OBLIGATOIRE,
                    "La version lue est obligatoire pour modifier une valeur.");
        }
        if (version != v.getVersion()) {
            throw modifieEntreTemps();
        }
    }

    static EvaluationMetierException modifieEntreTemps() {
        return EvaluationMetierException.conflict(PARAMETRE_MODIFIE_ENTRE_TEMPS,
                "Cette valeur a été modifiée par quelqu'un d'autre : rechargez la liste puis recommencez.");
    }

    private static EvaluationMetierException comportement(String message) {
        return EvaluationMetierException.methodNotAllowed(PARAMETRE_COMPORTEMENT, message);
    }

    private static EvaluationMetierException systeme(String message) {
        return EvaluationMetierException.conflict(PARAMETRE_SYSTEME, message);
    }

    private static String libelleValide(String brut) {
        String libelle = brut == null ? "" : brut.strip().replaceAll("\\s+", " ");
        if (libelle.length() < LIBELLE_MIN || libelle.length() > LIBELLE_MAX) {
            throw EvaluationMetierException.badRequest(LIBELLE_INVALIDE,
                    "Le libellé doit faire entre " + LIBELLE_MIN + " et " + LIBELLE_MAX + " caractères.");
        }
        return libelle;
    }

    private static String descriptionValide(String brute) {
        if (brute == null || brute.isBlank()) {
            return null;
        }
        String description = brute.strip();
        if (description.length() > DESCRIPTION_MAX) {
            throw EvaluationMetierException.badRequest(DESCRIPTION_TROP_LONGUE,
                    "L'aide ne doit pas dépasser " + DESCRIPTION_MAX + " caractères.");
        }
        return description;
    }

    private static String couleurValide(String brute) {
        if (brute == null || brute.isBlank()) {
            return "NEUTRE";
        }
        if (!PALETTE.contains(brute)) {
            throw EvaluationMetierException.badRequest(COULEUR_INVALIDE,
                    "Couleur inconnue : choisissez dans " + String.join(", ", PALETTE.stream().sorted().toList()) + ".");
        }
        return brute;
    }

    private static int seuilValide(Integer seuil) {
        if (seuil == null || seuil < ReferentielEvaluation.SEUIL_ECART_MIN || seuil > ReferentielEvaluation.SEUIL_ECART_MAX) {
            throw EvaluationMetierException.badRequest(SEUIL_INVALIDE,
                    "Le seuil doit être compris entre " + ReferentielEvaluation.SEUIL_ECART_MIN + " et "
                            + ReferentielEvaluation.SEUIL_ECART_MAX + " étoiles d'écart.");
        }
        return seuil;
    }

    /** Unicité du libellé normalisé, valeurs actives et inactives (D-R02.b). */
    private void verifierLibelleLibre(String referentiel, String libelle, ParametreValeur courante) {
        String normalise = CodeParametre.normaliser(libelle);
        if (courante != null && normalise.equals(courante.getLibelleNormalise())) {
            return;
        }
        if (repository.existsByReferentielAndLibelleNormalise(referentiel, normalise)) {
            throw EvaluationMetierException.badRequest(LIBELLE_EN_DOUBLE,
                    "Une valeur de ce référentiel porte déjà le libellé « " + libelle + " ».");
        }
    }

    /** Un seul niveau actif par seuil (D-R04.a). */
    private void verifierSeuilLibre(String referentiel, Integer seuil, ParametreValeur courante, int statut) {
        boolean pris = repository.findByReferentielOrderByOrdreAscCodeAsc(referentiel).stream()
                .anyMatch(n -> n.isActif() && seuil.equals(n.getSeuilEcart())
                        && (courante == null || !n.getCode().equals(courante.getCode())));
        if (pris) {
            throw seuilEnDouble(statut);
        }
    }

    private static EvaluationMetierException seuilEnDouble(int statut) {
        String message = "Un autre niveau actif utilise déjà ce seuil : chaque niveau actif a un seuil différent.";
        return statut == 422
                ? EvaluationMetierException.unprocessable(SEUIL_EN_DOUBLE, message)
                : EvaluationMetierException.badRequest(SEUIL_EN_DOUBLE, message);
    }

    private int prochainOrdre(String referentiel) {
        return repository.findByReferentielOrderByOrdreAscCodeAsc(referentiel).stream()
                .mapToInt(ParametreValeur::getOrdre).max().orElse(0) + 1;
    }

    private static void tracerAuteur(ParametreValeur v, Auteur auteur) {
        v.setModifieLe(Instant.now());
        v.setModifieParCompteId(auteur != null ? auteur.compteId() : null);
    }

    /**
     * Enregistre et force l'écriture : conflit de version -> 409 ; index unique partiel violé par une activation
     * concurrente -> doublon de seuil ({@code statutDoublon}).
     */
    private void sauver(ParametreValeur v, int statutDoublon) {
        try {
            repository.saveAndFlush(v);
        } catch (ObjectOptimisticLockingFailureException e) {
            throw modifieEntreTemps();
        } catch (DataIntegrityViolationException e) {
            String cause = String.valueOf(e.getMostSpecificCause().getMessage());
            if (cause.contains("seuil")) {
                throw seuilEnDouble(statutDoublon);
            }
            if (cause.contains("libelle")) {
                throw EvaluationMetierException.badRequest(LIBELLE_EN_DOUBLE,
                        "Une valeur de ce référentiel porte déjà ce libellé.");
            }
            throw EvaluationMetierException.conflict(PARAMETRE_MODIFIE_ENTRE_TEMPS,
                    "La valeur a été modifiée en parallèle : rechargez la liste puis recommencez.");
        }
    }

    private static Map<String, Object> instantane(ParametreValeur v) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("libelle", v.getLibelle());
        m.put("description", v.getDescription());
        m.put("couleur", v.getCouleur());
        m.put("ordre", v.getOrdre());
        m.put("actif", v.isActif());
        if (v.getSeuilEcart() != null) {
            m.put("seuil_ecart", v.getSeuilEcart());
        }
        return m;
    }

    /** Nombre d'utilisations local (comptage par code) ; 0 pour les codes de comportement sans compteur. */
    long utilisations(ParametreValeur v) {
        return switch (v.getReferentiel()) {
            case ReferentielEvaluation.NIVEAU_ALERTE -> alertes.compterUtilisationsNiveau(v.getCode());
            case ReferentielEvaluation.CONCLUSION_ALERTE -> alertes.compterUtilisationsConclusion(v.getCode());
            case ReferentielEvaluation.TYPE_QUESTION -> typeQuestion(v.getCode())
                    .map(questions::countByTypeQuestion).orElse(0L);
            default -> 0L;
        };
    }

    private static Optional<QuestionType> typeQuestion(String code) {
        try {
            return Optional.of(QuestionType.valueOf(code));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private ParametreDto.Valeur versDto(ParametreValeur v) {
        long utilisations = utilisations(v);
        return new ParametreDto.Valeur(v.getCode(), v.getLibelle(), v.getDescription(), v.getCouleur(), v.getOrdre(),
                v.isActif(), v.isSysteme(), v.getNature().name(), v.getVersion(), utilisations,
                v.getPremiereUtilisationLe() != null || utilisations > 0, v.getSeuilEcart());
    }
}
