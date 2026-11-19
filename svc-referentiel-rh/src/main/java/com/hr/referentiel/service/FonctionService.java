package com.hr.referentiel.service;

import com.hr.referentiel.dto.FonctionDtos.AffectationRequest;
import com.hr.referentiel.dto.FonctionDtos.AffectationResponse;
import com.hr.referentiel.dto.FonctionDtos.FonctionRequest;
import com.hr.referentiel.dto.FonctionDtos.FonctionResponse;
import com.hr.referentiel.entity.Collaborateur;
import com.hr.referentiel.entity.Fonction;
import com.hr.referentiel.entity.FonctionAffectation;
import com.hr.referentiel.entity.UniteOrganisation;
import com.hr.referentiel.repository.CollaborateurRepository;
import com.hr.referentiel.repository.FonctionAffectationRepository;
import com.hr.referentiel.repository.FonctionRepository;
import com.hr.referentiel.repository.UniteOrganisationRepository;
import com.hr.referentiel.web.ReferentielMetierException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

@Service
public class FonctionService {

	private static final int CODE_MAX = 40;

	private final FonctionRepository fonctionRepository;
	private final FonctionAffectationRepository affectationRepository;
	private final CollaborateurRepository collaborateurRepository;
	private final UniteOrganisationRepository uniteRepository;
	private final Clock clock;

	public FonctionService(
			FonctionRepository fonctionRepository,
			FonctionAffectationRepository affectationRepository,
			CollaborateurRepository collaborateurRepository,
			UniteOrganisationRepository uniteRepository,
			Clock clock) {
		this.fonctionRepository = fonctionRepository;
		this.affectationRepository = affectationRepository;
		this.collaborateurRepository = collaborateurRepository;
		this.uniteRepository = uniteRepository;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public List<FonctionResponse> lister(boolean actifsSeulement) {
		List<Fonction> liste = actifsSeulement
				? fonctionRepository.findAllByActifTrueOrderByLibelleAsc()
				: fonctionRepository.findAllByOrderByLibelleAsc();
		return liste.stream().map(FonctionService::versResponse).toList();
	}

	@Transactional(readOnly = true)
	public FonctionResponse obtenir(UUID id) {
		return versResponse(chargerFonction(id));
	}

	@Transactional
	public FonctionResponse creer(FonctionRequest req) {
		String libelle = req.libelle().trim();
		String normalise = NormalisationLibelle.normaliser(libelle);
		if (fonctionRepository.existsByLibelleNormalise(normalise)) {
			throw ReferentielMetierException.conflict("LIBELLE_EN_DOUBLE",
					"Une fonction avec ce libellé existe déjà.");
		}
		String code = genererCodeUnique(normalise);
		Instant maintenant = Instant.now(clock);
		Fonction fonction = new Fonction(code, libelle, normalise, nettoyerDescription(req.description()), maintenant);
		return versResponse(fonctionRepository.saveAndFlush(fonction));
	}

	@Transactional
	public FonctionResponse modifier(UUID id, FonctionRequest req) {
		Fonction fonction = chargerFonction(id);
		if (req.version() == null || !Objects.equals(req.version(), fonction.getVersion())) {
			throw ReferentielMetierException.conflict("PARAMETRE_MODIFIE_ENTRE_TEMPS",
					"Cette fonction a été modifiée entre-temps.");
		}
		String libelle = req.libelle().trim();
		String normalise = NormalisationLibelle.normaliser(libelle);
		if (fonctionRepository.existsByLibelleNormaliseAndIdNot(normalise, id)) {
			throw ReferentielMetierException.conflict("LIBELLE_EN_DOUBLE",
					"Une fonction avec ce libellé existe déjà.");
		}
		fonction.setLibelle(libelle);
		fonction.setLibelleNormalise(normalise);
		fonction.setDescription(nettoyerDescription(req.description()));
		fonction.setModifieLe(Instant.now(clock));
		return versResponse(fonctionRepository.saveAndFlush(fonction));
	}

	@Transactional
	public FonctionResponse changerActivation(UUID id, boolean actif) {
		Fonction fonction = chargerFonction(id);
		fonction.setActif(actif);
		fonction.setModifieLe(Instant.now(clock));
		return versResponse(fonctionRepository.saveAndFlush(fonction));
	}

	@Transactional
	public void supprimer(UUID id) {
		Fonction fonction = chargerFonction(id);
		if (fonction.isUtilisee() || affectationRepository.existsByFonctionId(id)) {
			throw ReferentielMetierException.conflict("PARAMETRE_UTILISE",
					"Impossible de supprimer cette fonction car elle est déjà utilisée ou affectée.");
		}
		fonctionRepository.delete(fonction);
	}

	@Transactional
	public void marquerUtiliseeParCode(String code) {
		if (code == null || code.isBlank()) {
			return;
		}
		fonctionRepository.findByCode(code.trim().toUpperCase(Locale.ROOT)).ifPresent(fonction -> {
			if (!fonction.isUtilisee()) {
				fonction.setUtilisee(true);
				fonction.setModifieLe(Instant.now(clock));
				fonctionRepository.save(fonction);
			}
		});
	}

	private Fonction chargerFonction(UUID id) {
		return fonctionRepository.findById(id)
				.orElseThrow(() -> ReferentielMetierException.notFound("FONCTION_INTROUVABLE",
						"Fonction introuvable : " + id));
	}

	private String genererCodeUnique(String libelleNormalise) {
		String base = libelleNormalise.toUpperCase(Locale.ROOT)
				.replaceAll("[^A-Z0-9]+", "_")
				.replaceAll("^_+|_+$", "");
		if (base.isEmpty()) {
			base = "FONCTION";
		}
		if (base.length() > CODE_MAX) {
			base = base.substring(0, CODE_MAX);
		}
		if (!fonctionRepository.existsByCode(base)) {
			return base;
		}
		int suffixe = 2;
		while (true) {
			String fin = "_" + suffixe;
			String prefixe = base.length() + fin.length() > CODE_MAX
					? base.substring(0, CODE_MAX - fin.length())
					: base;
			String candidat = prefixe + fin;
			if (!fonctionRepository.existsByCode(candidat)) {
				return candidat;
			}
			suffixe++;
		}
	}

	private static String nettoyerDescription(String description) {
		if (description == null) {
			return null;
		}
		String trim = description.trim();
		return trim.isEmpty() ? null : trim;
	}

	private static FonctionResponse versResponse(Fonction f) {
		return new FonctionResponse(
				f.getId(), f.getCode(), f.getLibelle(), f.getDescription(),
				f.isActif(), f.isUtilisee(), f.getVersion(), f.getCreeLe(), f.getModifieLe());
	}
}
