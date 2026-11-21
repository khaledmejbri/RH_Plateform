package com.hr.workflow.moteur;

import com.hr.workflow.client.WorkflowReferentielClient;
import com.hr.workflow.client.WorkflowReferentielClient.ContexteDemandeur;
import com.hr.workflow.client.WorkflowReferentielClient.IntervenantResolu;
import com.hr.workflow.client.WorkflowReferentielClient.RegleResolutionRequest;
import com.hr.workflow.client.WorkflowReferentielClient.ResolutionLotRequest;
import com.hr.workflow.client.WorkflowReferentielClient.ResolutionLotResponse;
import com.hr.workflow.client.WorkflowReferentielClient.ResultatResolution;
import com.hr.workflow.definition.EtapeDefinition.RepliSiVideDefinition;
import com.hr.workflow.definition.IntervenantsDefinition;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Résolution des intervenants auprès du référentiel (§ 9.5.3, § 9.6.1, D-W14 à D-W16, I1).
 * <strong>Jamais appelé dans une transaction.</strong>
 */
@Service
public class ResolutionIntervenantsService {

	private final WorkflowReferentielClient referentielClient;

	public ResolutionIntervenantsService(WorkflowReferentielClient referentielClient) {
		this.referentielClient = referentielClient;
	}

	public record RegleAResoudre(
			String cle,
			IntervenantsDefinition intervenants,
			RepliSiVideDefinition repliSiVide,
			boolean estValidation
	) {
	}

	public record ResultatRegleResolue(
			List<IntervenantResolu> candidats,
			boolean passer,
			boolean bloquer,
			String raisonVide
	) {
	}

	public record LotResolu(
			Map<String, ResultatRegleResolue> resultats,
			ContexteDemandeur contexteDemandeur
	) {
	}

	/**
	 * Résout un lot de règles en un appel groupé (plus un éventuel appel de repli {@code ROLE}),
	 * revérifie les exclusions I1 et applique le repli D-W16.
	 */
	public LotResolu resoudre(
			LocalDate date,
			UUID demandeurId,
			List<UUID> beneficiaires,
			List<UUID> exclureSupplementaires,
			List<RegleAResoudre> regles) {
		List<UUID> benefs = beneficiaires != null ? beneficiaires : List.of();
		List<UUID> exclSup = exclureSupplementaires != null ? exclureSupplementaires : List.of();

		if (regles == null || regles.isEmpty()) {
			// On résout tout de même le contexte demandeur via une requête minimale si besoin
			return new LotResolu(Map.of(), null);
		}

		List<RegleResolutionRequest> requetes = new ArrayList<>();
		for (RegleAResoudre r : regles) {
			requetes.add(new RegleResolutionRequest(
					r.cle(),
					r.intervenants().regle(),
					r.intervenants().params() != null ? r.intervenants().params() : Map.of()
			));
		}

		ResolutionLotResponse reponse = referentielClient.resoudreLot(
				new ResolutionLotRequest(date, demandeurId, benefs, exclSup, requetes));

		Map<String, ResultatRegleResolue> finaux = new LinkedHashMap<>();
		List<RegleAResoudre> replisRole = new ArrayList<>();
		Map<String, String> raisonsVideInitiales = new LinkedHashMap<>();

		for (RegleAResoudre r : regles) {
			ResultatResolution brut = reponse.resultats() != null ? reponse.resultats().get(r.cle()) : null;
			List<IntervenantResolu> filtres = filtrerExclusions(
					brut != null ? brut.candidats() : List.of(),
					r.estValidation(), demandeurId, benefs, exclSup);
			String raisonVide = brut != null ? brut.raisonVide() : "AUCUN_TITULAIRE";
			if (!filtres.isEmpty()) {
				finaux.put(r.cle(), new ResultatRegleResolue(filtres, false, false, null));
			} else {
				raisonsVideInitiales.put(r.cle(), raisonVide != null ? raisonVide : "TOUS_EXCLUS");
				RepliSiVideDefinition repli = r.repliSiVide();
				if (repli != null && "ROLE".equals(repli.action()) && repli.roles() != null && !repli.roles().isEmpty()) {
					replisRole.add(r);
				} else if (repli != null && "PASSER".equals(repli.action())) {
					finaux.put(r.cle(), new ResultatRegleResolue(List.of(), true, false, raisonsVideInitiales.get(r.cle())));
				} else {
					finaux.put(r.cle(), new ResultatRegleResolue(List.of(), false, true, raisonsVideInitiales.get(r.cle())));
				}
			}
		}

		// Second appel groupé si au moins un repli ROLE est nécessaire (D-W16)
		if (!replisRole.isEmpty()) {
			List<RegleResolutionRequest> reqRepli = new ArrayList<>();
			for (RegleAResoudre r : replisRole) {
				reqRepli.add(new RegleResolutionRequest(
						r.cle(),
						"ROLE",
						Map.of("roles", r.repliSiVide().roles())
				));
			}
			ResolutionLotResponse repRepli = referentielClient.resoudreLot(
					new ResolutionLotRequest(date, demandeurId, benefs, exclSup, reqRepli));
			for (RegleAResoudre r : replisRole) {
				ResultatResolution brutRepli = repRepli.resultats() != null ? repRepli.resultats().get(r.cle()) : null;
				List<IntervenantResolu> filtresRepli = filtrerExclusions(
						brutRepli != null ? brutRepli.candidats() : List.of(),
						r.estValidation(), demandeurId, benefs, exclSup);
				if (!filtresRepli.isEmpty()) {
					finaux.put(r.cle(), new ResultatRegleResolue(filtresRepli, false, false, null));
				} else {
					// D-W16 : si le pool ROLE est vide aussi -> BLOQUER
					finaux.put(r.cle(), new ResultatRegleResolue(List.of(), false, true,
							brutRepli != null && brutRepli.raisonVide() != null
									? brutRepli.raisonVide() : raisonsVideInitiales.get(r.cle())));
				}
			}
		}

		return new LotResolu(finaux, reponse.contexteDemandeur());
	}

	/**
	 * Résout les destinataires (comptes UUID) d'une liste de publics de notification (D-W27).
	 */
	public List<UUID> resoudreComptesPublics(
			LocalDate date,
			List<String> publics,
			UUID demandeurId,
			UUID demandeurCompteId,
			Collection<IntervenantResolu> candidatsEtape) {
		if (publics == null || publics.isEmpty()) {
			return List.of();
		}
		Set<UUID> comptes = new HashSet<>();
		List<RegleResolutionRequest> reqs = new ArrayList<>();
		int idx = 0;

		for (String pub : publics) {
			if ("DEMANDEUR".equals(pub)) {
				if (demandeurCompteId != null) {
					comptes.add(demandeurCompteId);
				}
			} else if ("CANDIDATS".equals(pub)) {
				if (candidatsEtape != null) {
					for (IntervenantResolu c : candidatsEtape) {
						if (c.compteId() != null) {
							comptes.add(c.compteId());
						}
					}
				}
			} else if ("ROLE:RH".equals(pub)) {
				reqs.add(new RegleResolutionRequest("p" + (idx++), "ROLE", Map.of("roles", List.of("RH"))));
			} else if ("ROLE:ADMIN".equals(pub)) {
				reqs.add(new RegleResolutionRequest("p" + (idx++), "ROLE", Map.of("roles", List.of("ADMIN"))));
			} else if ("MANAGER_NOEUD".equals(pub)) {
				reqs.add(new RegleResolutionRequest("p" + (idx++), "MANAGER_NOEUD", Map.of()));
			} else if (pub != null && pub.startsWith("FONCTION:")) {
				String code = pub.substring("FONCTION:".length());
				reqs.add(new RegleResolutionRequest("p" + (idx++), "FONCTION",
						Map.of("fonction_code", code, "portee", "SOCIETE")));
			}
		}

		if (!reqs.isEmpty()) {
			ResolutionLotResponse rep = referentielClient.resoudreLot(
					new ResolutionLotRequest(date, demandeurId, List.of(), List.of(), reqs));
			if (rep.resultats() != null) {
				for (ResultatResolution r : rep.resultats().values()) {
					if (r != null && r.candidats() != null) {
						for (IntervenantResolu i : r.candidats()) {
							if (i.compteId() != null) {
								comptes.add(i.compteId());
							}
						}
					}
				}
			}
		}
		return new ArrayList<>(comptes);
	}

	/**
	 * Résout les comptes titulaires d'au moins un des rôles donnés (D-W16, D-W29).
	 */
	public List<UUID> resoudreComptesRoles(LocalDate date, List<String> roles) {
		if (roles == null || roles.isEmpty()) {
			return List.of();
		}
		ResolutionLotResponse rep = referentielClient.resoudreLot(new ResolutionLotRequest(
				date, null, List.of(), List.of(),
				List.of(new RegleResolutionRequest("roles", "ROLE", Map.of("roles", roles)))));
		if (rep == null || rep.resultats() == null) {
			return List.of();
		}
		ResultatResolution res = rep.resultats().get("roles");
		if (res == null || res.candidats() == null) {
			return List.of();
		}
		Set<UUID> comptes = new HashSet<>();
		for (IntervenantResolu i : res.candidats()) {
			if (i != null && i.compteId() != null) {
				comptes.add(i.compteId());
			}
		}
		return new ArrayList<>(comptes);
	}

	/**
	 * Revérification stricte de l'invariant I1 et des exclusions côté moteur (§ 9.5.3).
	 */
	public static List<IntervenantResolu> filtrerExclusions(
			List<IntervenantResolu> bruts,
			boolean estValidation,
			UUID demandeurId,
			List<UUID> beneficiaires,
			List<UUID> exclureSupplementaires) {
		if (bruts == null || bruts.isEmpty()) {
			return List.of();
		}
		Set<UUID> exclus = new HashSet<>();
		if (estValidation) {
			if (demandeurId != null) {
				exclus.add(demandeurId);
			}
			if (beneficiaires != null) {
				exclus.addAll(beneficiaires);
			}
		}
		if (exclureSupplementaires != null) {
			exclus.addAll(exclureSupplementaires);
		}
		List<IntervenantResolu> out = new ArrayList<>();
		Set<UUID> vus = new HashSet<>();
		for (IntervenantResolu c : bruts) {
			if (c == null || c.collaborateurId() == null || c.compteId() == null) {
				continue;
			}
			if (exclus.contains(c.collaborateurId())) {
				continue;
			}
			if (vus.add(c.collaborateurId())) {
				out.add(c);
			}
		}
		return out;
	}
}
