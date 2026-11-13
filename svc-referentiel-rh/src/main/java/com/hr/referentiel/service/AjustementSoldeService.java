package com.hr.referentiel.service;

import com.hr.referentiel.domain.OrigineMouvementSolde;
import com.hr.referentiel.domain.TypeMouvementSolde;
import com.hr.referentiel.dto.SoldeDtos.AjustementRequest;
import com.hr.referentiel.dto.SoldeDtos.AjustementResponse;
import com.hr.referentiel.entity.Collaborateur;
import com.hr.referentiel.entity.MouvementSoldeConge;
import com.hr.referentiel.entity.SoldeConge;
import com.hr.referentiel.kafka.RhNotificationPublisher;
import com.hr.referentiel.repository.CollaborateurRepository;
import com.hr.referentiel.web.ReferentielMetierException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Ajustement manuel du solde (D-S08, B6) : écart ou nouveau solde, motif 5 à 500, version obligatoire comparée sous
 * verrou. Ordre des contrôles D-S15.f : 404, 403 identité, 400 corps, 422 règle, 409 état.
 */
@Service
public class AjustementSoldeService {

	public static final BigDecimal BORNE = new BigDecimal("365");
	public static final int MOTIF_MIN = 5;
	public static final int MOTIF_MAX = 500;
	static final String AVERTISSEMENT = "Des demandes en cours dépassent le nouveau solde";

	private final CollaborateurRepository collaborateurRepository;
	private final CollaborateurConnecteService collaborateurConnecteService;
	private final AuteurParametreResolver auteurResolver;
	private final SoldeCongeService soldeService;
	private final SoldeCongeLectureService lectureService;
	private final RhNotificationPublisher notificationPublisher;

	public AjustementSoldeService(CollaborateurRepository collaborateurRepository,
			CollaborateurConnecteService collaborateurConnecteService, AuteurParametreResolver auteurResolver,
			SoldeCongeService soldeService, SoldeCongeLectureService lectureService,
			RhNotificationPublisher notificationPublisher) {
		this.collaborateurRepository = collaborateurRepository;
		this.collaborateurConnecteService = collaborateurConnecteService;
		this.auteurResolver = auteurResolver;
		this.soldeService = soldeService;
		this.lectureService = lectureService;
		this.notificationPublisher = notificationPublisher;
	}

	/** Valeur saisie : exactement un des deux modes. */
	record Saisie(BigDecimal ecart, BigDecimal nouveauSolde, String motif) {
	}

	@Transactional
	public AjustementResponse ajuster(UUID collaborateurId, AjustementRequest req, Jwt jwt) {
		Collaborateur cible = collaborateurRepository.findDetailById(collaborateurId).orElseThrow(
				() -> ReferentielMetierException.notFound("COLLABORATEUR_INTROUVABLE", "Collaborateur introuvable."));
		Collaborateur appelant = collaborateurConnecteService.resoudre(jwt).orElse(null);
		if (appelant != null && appelant.getId().equals(cible.getId())) {
			throw ReferentielMetierException.forbidden("AUTO_AJUSTEMENT_INTERDIT",
					"Vous ne pouvez pas ajuster votre propre solde : un autre RH ou un administrateur doit le faire.");
		}
		Saisie saisie = valider(req);
		AuteurParametre auteur = auteurResolver.depuis(jwt);

		SoldeConge solde = soldeService.verrouiller(cible.getId());
		BigDecimal ecart = saisie.ecart() != null ? saisie.ecart()
				: saisie.nouveauSolde().subtract(solde.getAcquis());
		if (ecart.signum() == 0) {
			throw ReferentielMetierException.unprocessable("AJUSTEMENT_NUL", "L'ajustement ne change pas le solde.");
		}
		if (solde.getAcquis().add(ecart).signum() < 0) {
			throw ReferentielMetierException.unprocessable("SOLDE_NEGATIF",
					"Le solde acquis ne peut pas devenir négatif (solde actuel : " + FormatJours.jours(solde.getAcquis()) + ").");
		}
		if (req.version() != solde.getVersion()) {
			throw ReferentielMetierException.conflict("SOLDE_MODIFIE_ENTRE_TEMPS",
					"Le solde a changé entre-temps. Rechargez-le puis recommencez.");
		}
		MouvementSoldeConge m = soldeService.enregistrer(solde, TypeMouvementSolde.AJUSTEMENT, ecart,
				OrigineMouvementSolde.SAISIE_MANUELLE, saisie.motif(), null, null, null, null, auteur);
		String avertissement = solde.getAcquis().compareTo(soldeService.reserve(cible.getId())) < 0 ? AVERTISSEMENT : null;
		BigDecimal apres = m.getSoldeApres();
		ApresCommit.executer(() -> notificationPublisher.notifierSoldeAjuste(cible, ecart, apres, saisie.motif()));
		return new AjustementResponse(lectureService.ligne(cible, appelant == null ? null : appelant.getId()),
				lectureService.vers(m), avertissement);
	}

	/** Contrôles de corps (400) : liste tous les champs fautifs dans {@code champs}. */
	Saisie valider(AjustementRequest req) {
		List<String> champs = new ArrayList<>();
		String code = null;
		BigDecimal ecart = null;
		BigDecimal nouveau = null;
		Object brutEcart = req == null ? null : req.ecart();
		Object brutNouveau = req == null ? null : req.nouveauSolde();
		if ((brutEcart == null) == (brutNouveau == null)) {
			champs.add("ecart");
			champs.add("nouveau_solde");
			code = "VALEUR_INVALIDE";
		} else if (brutEcart != null) {
			ecart = lireDecimal(brutEcart);
			if (ecart == null || !multipleDeDemi(ecart) || ecart.abs().compareTo(BORNE) > 0) {
				champs.add("ecart");
				code = "VALEUR_INVALIDE";
			}
		} else {
			nouveau = lireDecimal(brutNouveau);
			if (nouveau == null || !multipleDeDemi(nouveau) || nouveau.signum() < 0 || nouveau.compareTo(BORNE) > 0) {
				champs.add("nouveau_solde");
				code = "VALEUR_INVALIDE";
			}
		}
		String motif = req == null || req.motif() == null ? "" : req.motif().trim();
		if (motif.length() < MOTIF_MIN || motif.length() > MOTIF_MAX) {
			champs.add("motif");
			if (code == null) {
				code = "MOTIF_OBLIGATOIRE";
			}
		}
		if (req == null || req.version() == null) {
			champs.add("version");
			if (code == null) {
				code = "CORPS_INVALIDE";
			}
		}
		if (code != null) {
			Map<String, Object> details = new HashMap<>();
			details.put("champs", champs);
			throw new ReferentielMetierException(code, messagePour(code), 400, details);
		}
		return new Saisie(ecart == null ? null : ecart.setScale(1), nouveau == null ? null : nouveau.setScale(1), motif);
	}

	private static String messagePour(String code) {
		return switch (code) {
			case "VALEUR_INVALIDE" -> "La valeur est invalide : un écart (ou un nouveau solde) par multiple de 0,5, "
					+ "dans la limite de 365 jours.";
			case "MOTIF_OBLIGATOIRE" -> "Le motif est obligatoire (5 à 500 caractères).";
			default -> "La version du solde est obligatoire.";
		};
	}

	/** Décimal tolérant la virgule ; {@code null} si illisible. */
	static BigDecimal lireDecimal(Object brut) {
		try {
			String t = brut instanceof Number ? brut.toString() : String.valueOf(brut).trim().replace(',', '.');
			return new BigDecimal(t);
		} catch (NumberFormatException e) {
			return null;
		}
	}

	static boolean multipleDeDemi(BigDecimal v) {
		return v.multiply(new BigDecimal("2")).stripTrailingZeros().scale() <= 0;
	}
}
