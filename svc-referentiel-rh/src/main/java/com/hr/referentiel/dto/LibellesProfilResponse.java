package com.hr.referentiel.dto;

import java.util.Map;

/** Libellés français des codes de profil (familles métier, niveaux de séniorité) pour les services internes. */
public record LibellesProfilResponse(Map<String, String> familles, Map<String, String> niveaux) {
}
