package com.hr.evaluation.dto;

import java.util.List;

/** Page de la liste des alertes (déjà triée côté serveur : le client ne retrie jamais). */
public record AlerteEcartPage(List<AlerteEcartResume> elements, long total, int page, int taille) {
}
