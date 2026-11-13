package com.hr.referentiel.service;

import com.hr.referentiel.domain.ActionJournalParametre;
import com.hr.referentiel.dto.ParametreDto.JournalEntree;
import com.hr.referentiel.dto.ParametreDto.PageJournal;
import com.hr.referentiel.entity.ParametreJournal;
import com.hr.referentiel.repository.ParametreJournalRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.util.Map;

/** Écriture et lecture du journal des paramètres pour les réglages et les jours fériés (même table que les référentiels). */
@Component
public class JournalParametres {

	public static final String REF_REGLAGE = "REGLAGE";
	public static final String REF_JOUR_FERIE = "JOUR_FERIE";

	private final ParametreJournalRepository repository;
	private final JsonMapper jsonMapper;
	private final Clock clock;

	public JournalParametres(ParametreJournalRepository repository, JsonMapper jsonMapper, Clock clock) {
		this.repository = repository;
		this.jsonMapper = jsonMapper;
		this.clock = clock;
	}

	public void tracer(String referentiel, String code, ActionJournalParametre action, Map<String, Object> avant,
			Map<String, Object> apres, AuteurParametre auteur) {
		repository.save(new ParametreJournal(referentiel, code, action,
				avant == null ? null : jsonMapper.writeValueAsString(avant),
				apres == null ? null : jsonMapper.writeValueAsString(apres),
				auteur.compteId(), auteur.nom(), clock.instant()));
	}

	public PageJournal lire(String referentiel, int page, int taille) {
		int t = Math.min(ParametreService.JOURNAL_TAILLE_MAX, Math.max(1, taille));
		int p = Math.max(0, page);
		Page<ParametreJournal> r = repository.findByReferentielOrderByHorodatageDesc(referentiel, PageRequest.of(p, t));
		return new PageJournal(r.getContent().stream().map(this::vers).toList(), p, t, r.getTotalElements());
	}

	private JournalEntree vers(ParametreJournal j) {
		return new JournalEntree(j.getAction().name(), j.getCode(), j.getAuteurNom(), j.getHorodatage(),
				lire(j.getAvantJson()), lire(j.getApresJson()));
	}

	private JsonNode lire(String texte) {
		return texte == null || texte.isBlank() ? null : jsonMapper.readTree(texte);
	}
}
