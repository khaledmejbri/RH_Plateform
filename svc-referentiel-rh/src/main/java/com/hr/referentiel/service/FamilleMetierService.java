package com.hr.referentiel.service;

import com.hr.referentiel.dto.FamilleMetierCreationRequest;
import com.hr.referentiel.dto.FamilleMetierMiseAJourRequest;
import com.hr.referentiel.dto.FamilleMetierResponse;
import com.hr.referentiel.dto.LibellesProfilResponse;
import com.hr.referentiel.domain.NiveauSeniorite;
import com.hr.referentiel.entity.FamilleMetier;
import com.hr.referentiel.repository.FamilleMetierRepository;
import com.hr.referentiel.web.ReferentielMetierException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;

@Service
public class FamilleMetierService {

	public static final String CODE_INCONNUE = "FAMILLE_METIER_INCONNUE";

	private final FamilleMetierRepository familleMetierRepository;

	public FamilleMetierService(FamilleMetierRepository familleMetierRepository) {
		this.familleMetierRepository = familleMetierRepository;
	}

	@Transactional(readOnly = true)
	public List<FamilleMetierResponse> lister(Boolean actifSeulement) {
		List<FamilleMetier> rows = Boolean.TRUE.equals(actifSeulement)
				? familleMetierRepository.findByActifTrueOrderByCodeAsc()
				: familleMetierRepository.findAllByOrderByCodeAsc();
		return rows.stream().map(this::toResponse).toList();
	}

	/** Familles (actives et inactives, libellé saisi) et niveaux du catalogue : code → libellé français. */
	@Transactional(readOnly = true)
	public LibellesProfilResponse libellesProfil() {
		Map<String, String> familles = new LinkedHashMap<>();
		familleMetierRepository.findAllByOrderByCodeAsc().forEach(f -> familles.put(f.getCode(), f.getLibelle()));
		Map<String, String> niveaux = new LinkedHashMap<>();
		NiveauSeniorite.catalogue().forEach(n -> niveaux.put(n.name(), n.getLibelle()));
		return new LibellesProfilResponse(familles, niveaux);
	}

	@Transactional
	public FamilleMetierResponse creer(FamilleMetierCreationRequest req) {
		String code = normalizeCode(req.getCode());
		if (familleMetierRepository.existsById(code) || familleMetierRepository.existsByCodeIgnoreCase(code)) {
			throw ReferentielMetierException.conflict("FAMILLE_METIER_EXISTANTE",
					"Une famille métier existe déjà avec le code " + code);
		}
		FamilleMetier f = new FamilleMetier();
		f.setCode(code);
		f.setLibelle(req.getLibelle().trim());
		f.setActif(true);
		f.setSysteme(false);
		return toResponse(familleMetierRepository.save(f));
	}

	@Transactional
	public FamilleMetierResponse mettreAJour(String code, FamilleMetierMiseAJourRequest req) {
		FamilleMetier f = familleMetierRepository.findById(normalizeCode(code))
				.orElseThrow(() -> ReferentielMetierException.unprocessable(CODE_INCONNUE,
						"Famille métier inconnue : " + code));
		f.setLibelle(req.getLibelle().trim());
		f.setActif(Boolean.TRUE.equals(req.getActif()));
		return toResponse(familleMetierRepository.save(f));
	}

	@Transactional(readOnly = true)
	public FamilleMetier exigerActive(String code) {
		FamilleMetier f = familleMetierRepository.findById(normalizeCode(code))
				.orElseThrow(() -> ReferentielMetierException.unprocessable(CODE_INCONNUE,
						"Famille métier inconnue : " + code));
		if (!f.isActif()) {
			throw ReferentielMetierException.unprocessable(CODE_INCONNUE,
					"Famille métier inactive : " + code);
		}
		return f;
	}

	@Transactional(readOnly = true)
	public String libelleOuNull(String code) {
		if (code == null || code.isBlank()) {
			return null;
		}
		return familleMetierRepository.findById(normalizeCode(code))
				.map(FamilleMetier::getLibelle)
				.orElse(null);
	}

	static String normalizeCode(String code) {
		if (code == null || code.isBlank()) {
			throw ReferentielMetierException.unprocessable(CODE_INCONNUE, "Code famille métier manquant");
		}
		return code.trim().toUpperCase(Locale.ROOT);
	}

	private FamilleMetierResponse toResponse(FamilleMetier f) {
		return new FamilleMetierResponse(
				f.getCode(),
				f.getLibelle(),
				f.isActif(),
				f.isSysteme(),
				f.getCreeLe(),
				f.getModifieLe());
	}
}
