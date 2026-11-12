package com.hr.evaluation.web;

import com.hr.evaluation.dto.ParametreDto;
import com.hr.evaluation.entity.EvaluationParametres;
import com.hr.evaluation.security.IdentiteAppelant;
import com.hr.evaluation.security.IdentiteAppelantResolver;
import com.hr.evaluation.service.CampagneCalendrier;
import com.hr.evaluation.service.EvaluationParametresService;
import com.hr.evaluation.service.ParametreJournalService.Auteur;
import com.hr.evaluation.service.ParametreService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Paramètres société des évaluations (SCRUM-41) et paramétrage des référentiels (lot 3, ADR § 7, JSON snake_case).
 * <ul>
 *   <li>{@code GET} / {@code PUT} racine : durée par défaut d'une campagne (contrat historique conservé).</li>
 *   <li>{@code referentiels/**} : valeurs des référentiels, ordre, journal.</li>
 *   <li>{@code reglages/**} : durée de campagne et seuil d'insuffisance.</li>
 * </ul>
 * Lecture : RH, DIRECTION, ADMIN (journal compris). Écriture : RH et ADMIN seulement. L'auteur est lu dans le JWT.
 * Couvert par la route gateway {@code /api/rh/v1/admin/evaluations/**}.
 */
@RestController
@RequestMapping("/api/rh/v1/admin/evaluations/parametres")
public class EvaluationParametresController {

    private final EvaluationParametresService parametresService;
    private final ParametreService parametreService;
    private final IdentiteAppelantResolver identiteResolver;

    public EvaluationParametresController(EvaluationParametresService parametresService,
                                          ParametreService parametreService,
                                          IdentiteAppelantResolver identiteResolver) {
        this.parametresService = parametresService;
        this.parametreService = parametreService;
        this.identiteResolver = identiteResolver;
    }

    // ----------------------------------------------------------------------- contrat historique (durée)

    @GetMapping
    @PreAuthorize(EvaluationSecurityExpressions.BACKOFFICE_LECTURE)
    public Map<String, Object> obtenir() {
        return reponse(parametresService.obtenir());
    }

    @PutMapping
    @PreAuthorize(EvaluationSecurityExpressions.BACKOFFICE_ECRITURE)
    public Map<String, Object> mettreAJour(@RequestBody Map<String, Object> corps) {
        Object valeur = corps == null ? null : corps.get("dureeCampagneJours");
        Integer duree = valeur instanceof Number n ? n.intValue() : null;
        return reponse(parametresService.mettreAJour(duree, auteur()));
    }

    private static Map<String, Object> reponse(EvaluationParametres p) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("dureeCampagneJours", p.getDureeCampagneJours());
        r.put("jourDebutDefaut", CampagneCalendrier.JOUR_DEBUT_DEFAUT);
        r.put("dureeMinJours", CampagneCalendrier.DUREE_MIN_JOURS);
        r.put("dureeMaxJours", CampagneCalendrier.DUREE_MAX_JOURS);
        r.put("modifieLe", p.getModifieLe());
        return r;
    }

    // ------------------------------------------------------------------------------------- référentiels

    @GetMapping("/referentiels")
    @PreAuthorize(EvaluationSecurityExpressions.BACKOFFICE_LECTURE)
    public List<ParametreDto.Descripteur> referentiels() {
        return parametreService.referentiels();
    }

    @GetMapping("/referentiels/{ref}/valeurs")
    @PreAuthorize(EvaluationSecurityExpressions.BACKOFFICE_LECTURE)
    public List<ParametreDto.Valeur> valeurs(@PathVariable String ref) {
        return parametreService.valeurs(ref);
    }

    @PostMapping("/referentiels/{ref}/valeurs")
    @PreAuthorize(EvaluationSecurityExpressions.BACKOFFICE_ECRITURE)
    public ResponseEntity<ParametreDto.Valeur> creer(@PathVariable String ref,
                                                     @RequestBody ParametreDto.CorpsValeur corps) {
        return ResponseEntity.status(HttpStatus.CREATED).body(parametreService.creer(ref, corps, auteur()));
    }

    /** Le {@code code} du corps, s'il y en a un, est ignoré : le code est immuable (D-R02.a). */
    @PutMapping("/referentiels/{ref}/valeurs/{code}")
    @PreAuthorize(EvaluationSecurityExpressions.BACKOFFICE_ECRITURE)
    public ParametreDto.Valeur modifier(@PathVariable String ref, @PathVariable String code,
                                        @RequestBody ParametreDto.CorpsValeur corps) {
        return parametreService.modifier(ref, code, corps, auteur());
    }

    @PostMapping("/referentiels/{ref}/valeurs/{code}/desactiver")
    @PreAuthorize(EvaluationSecurityExpressions.BACKOFFICE_ECRITURE)
    public ParametreDto.Valeur desactiver(@PathVariable String ref, @PathVariable String code,
                                          @RequestBody ParametreDto.CorpsVersion corps) {
        return parametreService.desactiver(ref, code, corps.version(), auteur());
    }

    @PostMapping("/referentiels/{ref}/valeurs/{code}/reactiver")
    @PreAuthorize(EvaluationSecurityExpressions.BACKOFFICE_ECRITURE)
    public ParametreDto.Valeur reactiver(@PathVariable String ref, @PathVariable String code,
                                         @RequestBody ParametreDto.CorpsVersion corps) {
        return parametreService.reactiver(ref, code, corps.version(), auteur());
    }

    @PutMapping("/referentiels/{ref}/ordre")
    @PreAuthorize(EvaluationSecurityExpressions.BACKOFFICE_ECRITURE)
    public List<ParametreDto.Valeur> reordonner(@PathVariable String ref, @RequestBody ParametreDto.CorpsOrdre corps) {
        return parametreService.reordonner(ref, corps.codes(), auteur());
    }

    @DeleteMapping("/referentiels/{ref}/valeurs/{code}")
    @PreAuthorize(EvaluationSecurityExpressions.BACKOFFICE_ECRITURE)
    public ResponseEntity<Void> supprimer(@PathVariable String ref, @PathVariable String code,
                                          @RequestParam(required = false) Long version) {
        parametreService.supprimer(ref, code, version, auteur());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/referentiels/{ref}/journal")
    @PreAuthorize(EvaluationSecurityExpressions.BACKOFFICE_LECTURE)
    public ParametreDto.PageJournal journal(@PathVariable String ref,
                                            @RequestParam(defaultValue = "0") int page,
                                            @RequestParam(defaultValue = "20") int taille) {
        return parametreService.journal(ref, page, taille);
    }

    // ---------------------------------------------------------------------------------------- réglages

    @GetMapping("/reglages")
    @PreAuthorize(EvaluationSecurityExpressions.BACKOFFICE_LECTURE)
    public List<ParametreDto.Reglage> reglages() {
        return parametresService.reglages();
    }

    @GetMapping("/reglages/journal")
    @PreAuthorize(EvaluationSecurityExpressions.BACKOFFICE_LECTURE)
    public ParametreDto.PageJournal journalReglages(@RequestParam(defaultValue = "0") int page,
                                                    @RequestParam(defaultValue = "20") int taille) {
        return parametresService.journalReglages(page, taille);
    }

    @PutMapping("/reglages/{cle}")
    @PreAuthorize(EvaluationSecurityExpressions.BACKOFFICE_ECRITURE)
    public ParametreDto.Reglage modifierReglage(@PathVariable String cle,
                                                @RequestBody ParametreDto.CorpsReglage corps) {
        return parametresService.modifierReglage(cle, corps.valeur(), corps.version(), auteur());
    }

    private Auteur auteur() {
        IdentiteAppelant appelant = identiteResolver.courant();
        return new Auteur(appelant.compteJwtObligatoire(), appelant.libelle());
    }
}
