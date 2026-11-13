package com.hr.referentiel.service;

import com.hr.referentiel.domain.StatutDemandeAdministrativeRh;
import com.hr.referentiel.domain.TypeDemandeAdministrativeRh;
import com.hr.referentiel.dto.DemandeAdministrativeRhResponse;
import com.hr.referentiel.entity.Collaborateur;
import com.hr.referentiel.entity.DemandeAdministrativeRh;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Champs d'une demande qui dépendent de l'appelant (D-S15.c, D4/D5) : solde du demandeur montré au seul décideur de
 * l'étape en cours (valideur attendu à l'étape responsable, RH / ADMIN autre que le demandeur à l'étape RH), et
 * indicateur « peut annuler après approbation ». L'appelant est lu dans le contexte de sécurité (JWT) ; sans contexte
 * authentifié, rien n'est ajouté. Les acquis sont lus en une requête groupée.
 */
@Component
public class ChampsDecideurSolde {

	private final CollaborateurConnecteService collaborateurConnecteService;
	private final SoldeCongeService soldeService;
	private final Clock clock;

	public ChampsDecideurSolde(CollaborateurConnecteService collaborateurConnecteService, SoldeCongeService soldeService,
			Clock clock) {
		this.collaborateurConnecteService = collaborateurConnecteService;
		this.soldeService = soldeService;
		this.clock = clock;
	}

	/** Appelant : fiche et droit d'écriture RH (RH ou ADMIN, jamais DIRECTION seule). */
	record Appelant(Collaborateur fiche, boolean ecritureRh) {
	}

	Appelant appelantCourant() {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		if (!(auth instanceof JwtAuthenticationToken jwtAuth) || !auth.isAuthenticated()) {
			return null;
		}
		Jwt jwt = jwtAuth.getToken();
		boolean ecriture = auth.getAuthorities().stream().map(GrantedAuthority::getAuthority)
				.anyMatch(a -> switch (a) {
					case "ROLE_RH", "ROLE_ADMIN", "RH", "ADMIN" -> true;
					default -> false;
				});
		return new Appelant(collaborateurConnecteService.resoudre(jwt).orElse(null), ecriture);
	}

	public void annoter(DemandeAdministrativeRh demande, DemandeAdministrativeRhResponse reponse) {
		annoter(List.of(demande), List.of(reponse));
	}

	public void annoter(List<DemandeAdministrativeRh> demandes, List<DemandeAdministrativeRhResponse> reponses) {
		Appelant appelant = appelantCourant();
		if (appelant == null || demandes.isEmpty()) {
			return;
		}
		// un RH / ADMIN sans fiche reste un décideur : seules les règles « propre demande » et « valideur attendu » exigent une fiche
		UUID moi = appelant.fiche() == null ? null : appelant.fiche().getId();
		LocalDate aujourdhui = LocalDate.now(clock.withZone(FormatJours.TUNIS));
		List<UUID> demandeurs = new ArrayList<>();
		for (DemandeAdministrativeRh d : demandes) {
			if (voitLeSolde(d, appelant)) {
				demandeurs.add(d.getDemandeur().getId());
			}
		}
		Map<UUID, BigDecimal> acquis = soldeService.acquisGroupes(demandeurs);
		Map<UUID, BigDecimal> reserves = soldeService.reservesGroupes(demandeurs);
		for (int i = 0; i < demandes.size(); i++) {
			DemandeAdministrativeRh d = demandes.get(i);
			DemandeAdministrativeRhResponse r = reponses.get(i);
			r.setPeutAnnuler(moi != null && d.getDemandeur().getId().equals(moi)
					&& (d.getStatut() == StatutDemandeAdministrativeRh.EN_VALIDATION_SUPERIEUR
					|| d.getStatut() == StatutDemandeAdministrativeRh.EN_VALIDATION_RRH));
			if (d.getTypeDemande() == TypeDemandeAdministrativeRh.CONGE) {
				r.setPeutAnnulerApresApprobation(appelant.ecritureRh() && !d.getDemandeur().getId().equals(moi)
						&& d.getStatut() == StatutDemandeAdministrativeRh.APPROUVEE && d.getPeriodeDebut() != null
						&& d.getPeriodeDebut().isAfter(aujourdhui));
			}
			if (voitLeSolde(d, appelant)) {
				BigDecimal a = acquis.getOrDefault(d.getDemandeur().getId(), new BigDecimal("0.0"));
				BigDecimal reserve = reserves.getOrDefault(d.getDemandeur().getId(), new BigDecimal("0.0"));
				r.setSoldeDisponibleDemandeur(a);
				r.setSoldeApresDemande(a.subtract(d.getJoursDecomptes()));
				r.setSoldeReserveAutresDemandes(reserve.subtract(d.getJoursDecomptes()).max(new BigDecimal("0.0")));
			}
		}
	}

	private static boolean voitLeSolde(DemandeAdministrativeRh d, Appelant appelant) {
		UUID moi = appelant.fiche() == null ? null : appelant.fiche().getId();
		if (d.getJoursDecomptes() == null || !CongeDecompteService.estAnnuel(d) || d.getDemandeur().getId().equals(moi)) {
			return false;
		}
		return switch (d.getStatut()) {
			case EN_VALIDATION_SUPERIEUR -> moi != null && d.getValideurAttendu() != null
					&& d.getValideurAttendu().getId().equals(moi);
			case EN_VALIDATION_RRH -> appelant.ecritureRh();
			default -> false;
		};
	}
}
