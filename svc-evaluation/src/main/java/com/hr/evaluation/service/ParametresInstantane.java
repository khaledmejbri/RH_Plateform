package com.hr.evaluation.service;

import com.hr.evaluation.domain.NatureParametre;
import com.hr.evaluation.domain.NiveauAlerte;
import com.hr.evaluation.domain.ReferentielEvaluation;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Instantané immuable de tous les paramètres de svc-evaluation (ADR lot 3 § 4) : toute lecture métier (détection,
 * libellés, seuils) passe par lui, sans accès base à chaque appel. Quelques centaines de lignes au plus.
 */
public final class ParametresInstantane {

    /** Seuil d'insuffisance appliqué tant que la société n'a rien réglé (D-R06). */
    public static final int SEUIL_INSUFFISANCE_DEFAUT = 3;

    /** Copie immuable d'une ligne de paramètre. */
    public record Valeur(String referentiel, String code, NatureParametre nature, String libelle, String description,
                         String couleur, int ordre, boolean actif, boolean systeme, Integer seuilEcart) {
    }

    private final Map<String, List<Valeur>> parReferentiel;
    private final int seuilInsuffisance;
    private final String empreinte;

    private ParametresInstantane(Map<String, List<Valeur>> parReferentiel, int seuilInsuffisance) {
        this.parReferentiel = parReferentiel;
        this.seuilInsuffisance = seuilInsuffisance;
        this.empreinte = calculerEmpreinte(parReferentiel, seuilInsuffisance);
    }

    /** @param valeurs déjà triées par ordre puis code */
    public static ParametresInstantane de(List<Valeur> valeurs, Integer seuilInsuffisance) {
        Map<String, List<Valeur>> groupes = new LinkedHashMap<>();
        for (Valeur v : valeurs) {
            groupes.computeIfAbsent(v.referentiel(), k -> new java.util.ArrayList<>()).add(v);
        }
        Map<String, List<Valeur>> figes = new LinkedHashMap<>();
        groupes.forEach((k, v) -> figes.put(k, List.copyOf(v)));
        return new ParametresInstantane(Map.copyOf(figes),
                seuilInsuffisance != null ? seuilInsuffisance : SEUIL_INSUFFISANCE_DEFAUT);
    }

    public Map<String, List<Valeur>> parReferentiel() {
        return parReferentiel;
    }

    public List<Valeur> valeurs(String referentiel) {
        return parReferentiel.getOrDefault(referentiel, List.of());
    }

    public Optional<Valeur> valeur(String referentiel, String code) {
        return valeurs(referentiel).stream().filter(v -> v.code().equals(code)).findFirst();
    }

    /** Libellé d'un code, ou {@code null} s'il n'existe pas dans le référentiel. */
    public String libelle(String referentiel, String code) {
        return valeur(referentiel, code).map(Valeur::libelle).orElse(null);
    }

    /** Niveaux d'alerte (actifs et inactifs), du seuil le plus bas au plus haut. */
    public List<NiveauAlerte> niveaux() {
        return valeurs(ReferentielEvaluation.NIVEAU_ALERTE).stream()
                .filter(v -> v.seuilEcart() != null)
                .map(v -> new NiveauAlerte(v.code(), v.libelle(), v.couleur(), v.seuilEcart(), v.actif()))
                .sorted(java.util.Comparator.comparingInt(NiveauAlerte::seuil))
                .toList();
    }

    public List<NiveauAlerte> niveauxActifs() {
        return niveaux().stream().filter(NiveauAlerte::actif).toList();
    }

    public int seuilInsuffisance() {
        return seuilInsuffisance;
    }

    /** Empreinte stable de l'instantané, servie comme ETag des libellés. */
    public String empreinte() {
        return empreinte;
    }

    private static String calculerEmpreinte(Map<String, List<Valeur>> donnees, int seuil) {
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            StringBuilder sb = new StringBuilder("seuil=").append(seuil).append('\n');
            donnees.keySet().stream().sorted().forEach(ref -> donnees.get(ref).forEach(v -> sb.append(v).append('\n')));
            return HexFormat.of().formatHex(sha.digest(sb.toString().getBytes(StandardCharsets.UTF_8)), 0, 8);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
