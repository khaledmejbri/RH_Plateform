package com.hr.referentiel.service;

import com.hr.referentiel.domain.ValeursInitialesReferentiel;
import com.hr.referentiel.dto.FamilleMetierCreationRequest;
import com.hr.referentiel.dto.FamilleMetierMiseAJourRequest;
import com.hr.referentiel.dto.FamilleMetierResponse;
import com.hr.referentiel.dto.LibellesProfilResponse;
import com.hr.referentiel.dto.NiveauSenioriteResponse;
import com.hr.referentiel.dto.ParametreDto;
import com.hr.referentiel.service.ParametresCache.ParametreVue;
import com.hr.referentiel.web.ReferentielMetierException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Façade « profil métier » (familles et niveaux de séniorité) sur le service générique de paramétrage
 * (ADR-UAT-lot3 § 3). Les familles et les niveaux sont des lignes de {@code rh_parametre_valeur} ; cette classe
 * garde les anciennes signatures (anciens endpoints {@code /familles-metier} et {@code /niveaux-seniorite},
 * validation à l'écriture d'une fiche) le temps de la transition. Elle disparaîtra avec le contrôleur du même nom.
 */
@Service
public class FamilleMetierService {

	public static final String CODE_INCONNUE = "FAMILLE_METIER_INCONNUE";
	public static final String NIVEAU_INVALIDE = "NIVEAU_SENIORITE_INVALIDE";

	static final String FAMILLE = "FAMILLE_METIER";
	static final String NIVEAU = "NIVEAU_SENIORITE";

	private final ParametreService parametres;

	public FamilleMetierService(ParametreService parametres) {
		this.parametres = parametres;
	}

	/** Familles triées par ordre puis libellé ; les dates ne sont plus portées par la façade (null). */
	@Transactional(readOnly = true)
	public List<FamilleMetierResponse> lister(Boolean actifSeulement) {
		return parametres.instantane().liste(FAMILLE).stream()
				.filter(v -> !Boolean.TRUE.equals(actifSeulement) || v.actif())
				.map(v -> new FamilleMetierResponse(v.code(), v.libelle(), v.actif(), v.systeme(), null, null))
				.toList();
	}

	/** Niveaux actifs, dans l'ordre choisi par la RH. */
	@Transactional(readOnly = true)
	public List<NiveauSenioriteResponse> niveaux() {
		return parametres.instantane().liste(NIVEAU).stream()
				.filter(ParametreVue::actif)
				.map(v -> new NiveauSenioriteResponse(v.code(), v.libelle()))
				.toList();
	}

	/** Familles et niveaux (actifs et inactifs) : code → libellé courant, pour les textes de svc-evaluation. */
	@Transactional(readOnly = true)
	public LibellesProfilResponse libellesProfil() {
		Map<String, String> familles = new LinkedHashMap<>();
		parametres.instantane().liste(FAMILLE).forEach(v -> familles.put(v.code(), v.libelle()));
		Map<String, String> niveaux = new LinkedHashMap<>();
		parametres.instantane().liste(NIVEAU).forEach(v -> niveaux.put(v.code(), v.libelle()));
		return new LibellesProfilResponse(familles, niveaux);
	}

	/** Le code fourni par l'ancien client est ignoré : il est généré depuis le libellé (D-R02.a). */
	@Transactional
	public FamilleMetierResponse creer(FamilleMetierCreationRequest req, AuteurParametre auteur) {
		ParametreDto.ValeurResponse v = parametres.creer(FAMILLE,
				new ParametreDto.ValeurRequest(req.getLibelle(), null, null, null, null, null, null), auteur);
		return versFamille(v);
	}

	@Transactional
	public FamilleMetierResponse mettreAJour(String code, FamilleMetierMiseAJourRequest req, AuteurParametre auteur) {
		ParametreDto.ValeurResponse v = parametres.modifier(FAMILLE, code,
				new ParametreDto.ValeurRequest(req.getLibelle(), null, null, null, null, null, null), null, auteur);
		if (Boolean.TRUE.equals(req.getActif()) && !v.actif()) {
			v = parametres.reactiver(FAMILLE, code, null, auteur);
		} else if (!Boolean.TRUE.equals(req.getActif()) && v.actif()) {
			v = parametres.desactiver(FAMILLE, code, null, auteur);
		}
		return versFamille(v);
	}

	/**
	 * Famille active à l'écriture d'une fiche : renvoie le code canonique et pose la marque « utilisée ».
	 * Inconnue ou inactive : 422 {@code FAMILLE_METIER_INCONNUE}.
	 */
	@Transactional
	public String exigerActive(String code) {
		String canonique = normaliserCode(code);
		ParametreVue v = parametres.exigerActive(FAMILLE, canonique,
				() -> ReferentielMetierException.unprocessable(CODE_INCONNUE, "Famille métier inconnue : " + code),
				() -> ReferentielMetierException.unprocessable(CODE_INCONNUE, "Famille métier inactive : " + code));
		parametres.marquerUtilisee(FAMILLE, v.code());
		return v.code();
	}

	/**
	 * Niveau actif à l'écriture d'une fiche (alias historiques acceptés : {@code CONFIRMED} → {@code CONFIRME}) :
	 * renvoie le code canonique et pose la marque « utilisée ». Inconnu ou inactif : 422 {@code NIVEAU_SENIORITE_INVALIDE}.
	 */
	@Transactional
	public String exigerNiveauActif(String brut) {
		String canonique = ValeursInitialesReferentiel.resoudreAliasNiveau(brut);
		ParametreVue v = parametres.exigerActive(NIVEAU, canonique,
				() -> ReferentielMetierException.unprocessable(NIVEAU_INVALIDE,
						"Niveau de séniorité invalide : " + brut),
				() -> ReferentielMetierException.unprocessable(NIVEAU_INVALIDE,
						"Niveau de séniorité inactif : " + brut));
		parametres.marquerUtilisee(NIVEAU, v.code());
		return v.code();
	}

	/** Code de famille sous sa graphie canonique (majuscules) ; vide : 422. */
	public static String normaliserCode(String code) {
		if (code == null || code.isBlank()) {
			throw ReferentielMetierException.unprocessable(CODE_INCONNUE, "Code famille métier manquant");
		}
		return code.trim().toUpperCase(Locale.ROOT);
	}

	/** Libellé courant de la famille (lu dans l'instantané, après le cache des fiches) ; null si inconnue. */
	public String libelleOuNull(String code) {
		return code == null || code.isBlank() ? null : parametres.libelle(FAMILLE, normaliserCode(code));
	}

	/** Libellé courant du niveau ; null si inconnu. */
	public String libelleNiveauOuNull(String code) {
		return code == null || code.isBlank() ? null
				: parametres.libelle(NIVEAU, ValeursInitialesReferentiel.resoudreAliasNiveau(code));
	}

	private static FamilleMetierResponse versFamille(ParametreDto.ValeurResponse v) {
		return new FamilleMetierResponse(v.code(), v.libelle(), v.actif(), v.systeme(), null, null);
	}
}
