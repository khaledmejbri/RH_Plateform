package com.hr.referentiel.service;

import com.hr.referentiel.domain.OrigineMouvementSolde;
import com.hr.referentiel.domain.StatutDemandeAdministrativeRh;
import com.hr.referentiel.domain.TypeMouvementSolde;
import com.hr.referentiel.entity.MouvementSoldeConge;
import com.hr.referentiel.entity.SoldeConge;
import com.hr.referentiel.repository.DemandeAdministrativeRhRepository;
import com.hr.referentiel.repository.MouvementSoldeCongeRepository;
import com.hr.referentiel.repository.SoldeCongeRepository;
import com.hr.referentiel.web.ReferentielMetierException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Écritures du solde de congé annuel (D-S04, D-S08.a) : verrou pessimiste sur la ligne du solde (créée au besoin),
 * mouvements immuables, invariant « acquis = somme des mouvements », acquis jamais négatif. Ordre des verrous de
 * l'ADR § 4 : toujours la demande, puis le solde (jamais l'inverse).
 */
@Service
public class SoldeCongeService {

	/** Statuts d'une demande qui réserve des jours (D-S04). */
	public static final Set<StatutDemandeAdministrativeRh> STATUTS_RESERVANTS = Set.of(
			StatutDemandeAdministrativeRh.EN_VALIDATION_SUPERIEUR, StatutDemandeAdministrativeRh.EN_VALIDATION_RRH);

	private final SoldeCongeRepository soldeRepository;
	private final MouvementSoldeCongeRepository mouvementRepository;
	private final DemandeAdministrativeRhRepository demandeRepository;
	private final Clock clock;
	private final jakarta.persistence.EntityManager entityManager;

	public SoldeCongeService(SoldeCongeRepository soldeRepository, MouvementSoldeCongeRepository mouvementRepository,
			DemandeAdministrativeRhRepository demandeRepository, Clock clock,
			jakarta.persistence.EntityManager entityManager) {
		this.entityManager = entityManager;
		this.soldeRepository = soldeRepository;
		this.mouvementRepository = mouvementRepository;
		this.demandeRepository = demandeRepository;
		this.clock = clock;
	}

	/** Verrouille (et crée au besoin) la ligne du solde. À appeler dans une transaction, après le verrou de la demande. */
	public SoldeConge verrouiller(UUID collaborateurId) {
		soldeRepository.creerSiAbsent(collaborateurId);
		SoldeConge solde = soldeRepository.verrouiller(collaborateurId).orElseThrow(
				() -> new IllegalStateException("Ligne de solde introuvable après création."));
		// Une instance déjà gérée (ex. chargée par l'import) ne serait pas relue par le simple SELECT ... FOR UPDATE :
		// on relit l'état sous verrou pour que l'écart et l'invariant « acquis = somme des mouvements » restent exacts.
		entityManager.refresh(solde, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
		return solde;
	}

	public BigDecimal acquis(UUID collaborateurId) {
		return soldeRepository.findById(collaborateurId).map(SoldeConge::getAcquis).orElse(zero());
	}

	public BigDecimal reserve(UUID collaborateurId) {
		BigDecimal r = demandeRepository.sommeReserve(collaborateurId, STATUTS_RESERVANTS);
		return r == null ? zero() : r.setScale(1);
	}

	/** Acquis de plusieurs collaborateurs en une requête (listes, champs du décideur). */
	public Map<UUID, BigDecimal> acquisGroupes(Collection<UUID> ids) {
		Map<UUID, BigDecimal> m = new HashMap<>();
		if (ids.isEmpty()) {
			return m;
		}
		List<UUID> liste = List.copyOf(ids);
		for (int debut = 0; debut < liste.size(); debut += 1000) {
			for (Object[] ligne : soldeRepository.acquisParIds(liste.subList(debut, Math.min(liste.size(), debut + 1000)))) {
				m.put((UUID) ligne[0], (BigDecimal) ligne[1]);
			}
		}
		return m;
	}

	public List<SoldeConge> soldes(Collection<UUID> ids) {
		return ids.isEmpty() ? List.of() : soldeRepository.findParIds(ids);
	}

	/** Réservé de plusieurs collaborateurs en une requête. */
	public Map<UUID, BigDecimal> reservesGroupes(Collection<UUID> ids) {
		Map<UUID, BigDecimal> m = new HashMap<>();
		if (ids.isEmpty()) {
			return m;
		}
		for (Object[] ligne : demandeRepository.sommesReserve(ids, STATUTS_RESERVANTS)) {
			m.put((UUID) ligne[0], ((BigDecimal) ligne[1]).setScale(1));
		}
		return m;
	}

	/**
	 * Applique un mouvement signé sur le solde verrouillé : acquis mis à jour, version incrémentée (D-S08.a), mouvement
	 * immuable écrit dans la même transaction. L'acquis ne devient jamais négatif (422 {@code SOLDE_NEGATIF}).
	 */
	public MouvementSoldeConge enregistrer(SoldeConge solde, TypeMouvementSolde type, BigDecimal nombre,
			OrigineMouvementSolde origine, String motif, UUID demandeId, String demandeNumero, String moisAcquisition,
			UUID importId, AuteurParametre auteur) {
		BigDecimal n = nombre.setScale(1);
		BigDecimal apres = solde.getAcquis().add(n).setScale(1);
		if (apres.signum() < 0) {
			throw ReferentielMetierException.unprocessable("SOLDE_NEGATIF",
					"Le solde acquis ne peut pas devenir négatif.");
		}
		MouvementSoldeConge m = new MouvementSoldeConge(solde.getCollaborateurId(), type.name(), n, apres,
				origine == null ? null : origine.name(), motif, demandeId, demandeNumero, moisAcquisition, importId,
				auteur.compteId(), auteur.nom(), clock.instant());
		mouvementRepository.save(m);
		solde.setAcquis(apres);
		solde.setVersion(solde.getVersion() + 1);
		solde.setDernierMouvementId(m.getId());
		solde.setModifieLe(clock.instant());
		soldeRepository.save(solde);
		return m;
	}

	public List<MouvementSoldeConge> mouvementsDe(UUID collaborateurId) {
		return mouvementRepository.findByCollaborateurIdOrderByCreeLeDescIdDesc(collaborateurId);
	}

	private static BigDecimal zero() {
		return new BigDecimal("0.0");
	}
}
