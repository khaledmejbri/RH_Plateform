package com.hr.referentiel.service;

import com.hr.referentiel.domain.StatutDemandeAdministrativeRh;
import com.hr.referentiel.entity.DemandeAdministrativeRh;
import com.hr.referentiel.repository.DemandeAdministrativeRhRepository;

/**
 * Logique commune d'approbation d'une demande M01 sous verrou (circuit hérité {@code validerRrh} et effet moteur
 * {@code APPROUVER}, D-W5-07) : contrôle de l'acquis, débit « Congé pris » si congé annuel décompté, passage en
 * {@code APPROUVEE} et sauvegarde.
 */
final class DemandeApprobationSupport {

	private DemandeApprobationSupport() {
	}

	/**
	 * @return {@code true} si la demande est un congé annuel antérieur aux soldes (non décompté).
	 */
	static boolean approuverSousVerrou(
			DemandeAdministrativeRh d,
			AuteurParametre auteur,
			CongeDecompteService congeDecompteService,
			DemandeAdministrativeRhRepository demandeRepo) {
		boolean anterieure = CongeDecompteService.estAnnuel(d) && d.getJoursDecomptes() == null;
		congeDecompteService.approuver(d, auteur);
		d.setStatut(StatutDemandeAdministrativeRh.APPROUVEE);
		demandeRepo.save(d);
		return anterieure;
	}
}
