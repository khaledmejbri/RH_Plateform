package com.hr.referentiel.service;

import com.hr.referentiel.domain.TypeDemandeAdministrativeRh;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class DemandeAdministrativeValidationService {

	public void validerContenu(TypeDemandeAdministrativeRh type, Map<String, Object> contenu) {
		if (contenu == null || contenu.isEmpty()) {
			throw new IllegalArgumentException("Le contenu de la demande est obligatoire.");
		}
		switch (type) {
			case CONGE -> {
				exigerCle(contenu, "date_debut");
				exigerCle(contenu, "date_fin");
				exigerCle(contenu, "type_conge");
			}
			case AUTORISATION_SORTIE -> {
				exigerCle(contenu, "date_jour");
				exigerCle(contenu, "heure_debut");
				exigerCle(contenu, "heure_fin");
				exigerCle(contenu, "type_sortie");
				String ts = String.valueOf(contenu.get("type_sortie"));
				if (!ts.equalsIgnoreCase("ADMINISTRATIF") && !ts.equalsIgnoreCase("PERSONNEL")
						&& !ts.equalsIgnoreCase("EXCEPTIONNEL")) {
					throw new IllegalArgumentException(
							"type_sortie doit être ADMINISTRATIF, PERSONNEL ou EXCEPTIONNEL.");
				}
			}
			case ORDRE_MISSION -> {
				exigerCle(contenu, "lieu");
				exigerCle(contenu, "date_debut");
				exigerCle(contenu, "date_fin");
				exigerCle(contenu, "motif");
			}
		}
	}

	private static void exigerCle(Map<String, Object> contenu, String cle) {
		if (!contenu.containsKey(cle) || contenu.get(cle) == null
				|| String.valueOf(contenu.get(cle)).isBlank()) {
			throw new IllegalArgumentException("Champ obligatoire manquant dans contenu : " + cle);
		}
	}
}
