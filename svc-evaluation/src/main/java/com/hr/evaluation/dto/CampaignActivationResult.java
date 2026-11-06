package com.hr.evaluation.dto;

import java.util.UUID;

/**
 * Résumé d'activation campagne (E4).
 */
public record CampaignActivationResult(
		UUID campagneId,
		String statut,
		int evaluationsCreees,
		int ignoresDejaExistantes,
		int ignoresManagerManquant,
		int profilsIncomplets,
		/** D-E13.a : évaluations créées sans template TECHNICAL (avertissement, jamais un blocage). */
		int evaluationsSansTemplateTechnique
) {
}
