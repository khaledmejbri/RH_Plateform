package com.hr.evaluation.service;

import com.hr.evaluation.domain.ActionJournalParametre;
import com.hr.evaluation.dto.ParametreDto;
import com.hr.evaluation.entity.ParametreJournal;
import com.hr.evaluation.repository.ParametreJournalRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.UUID;

/**
 * Journal des paramètres (D-R02.g) : écrit dans la <b>même transaction</b> que la modification, ajout seul, jamais
 * purgé. Auteur lu dans le JWT par l'appelant.
 */
@Service
public class ParametreJournalService {

    public static final int TAILLE_MAX = 100;

    /** Auteur d'une écriture : compte du JWT et nom lisible. */
    public record Auteur(UUID compteId, String nom) {
    }

    private final ParametreJournalRepository repository;
    private final JsonMapper mapper;

    public ParametreJournalService(ParametreJournalRepository repository, JsonMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Transactional
    public void ecrire(String referentiel, String code, ActionJournalParametre action, Object avant, Object apres,
                       Auteur auteur) {
        repository.save(new ParametreJournal(referentiel, code, action, json(avant), json(apres),
                auteur != null ? auteur.compteId() : null,
                auteur != null ? auteur.nom() : ParametreJournal.AUTEUR_SYSTEME));
    }

    @Transactional(readOnly = true)
    public ParametreDto.PageJournal lire(String referentiel, int page, int taille) {
        int p = Math.max(0, page);
        int t = Math.min(Math.max(1, taille), TAILLE_MAX);
        Page<ParametreJournal> lignes = repository.findByReferentielOrderByHorodatageDesc(referentiel, PageRequest.of(p, t));
        List<ParametreDto.Journal> contenu = lignes.getContent().stream()
                .map(j -> new ParametreDto.Journal(j.getAction().name(), j.getCode(), j.getAuteurNom(),
                        j.getHorodatage(), lire(j.getAvantJson()), lire(j.getApresJson())))
                .toList();
        return new ParametreDto.PageJournal(contenu, p, t, lignes.getTotalElements());
    }

    private String json(Object objet) {
        if (objet == null) {
            return null;
        }
        return mapper.writeValueAsString(objet);
    }

    private Object lire(String json) {
        if (json == null) {
            return null;
        }
        try {
            return mapper.readValue(json, Object.class);
        } catch (RuntimeException e) {
            return json;
        }
    }
}
