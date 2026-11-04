package com.hr.evaluation.dto;

/**
 * Corps des actions RH. Volontairement sans {@code @Valid} : la validation est faite par le service, après le 404 et le
 * conflit d'intérêts (précédence Q-H). {@code conclusion} reste une chaîne pour renvoyer un 400 métier si elle est inconnue.
 * Prise en charge : seul {@code commentaire} est lu (facultatif). Clôture : les deux sont obligatoires.
 */
public record AlerteEcartActionRequest(String conclusion, String commentaire) {
}
