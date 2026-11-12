package com.hr.evaluation.service;

import com.hr.evaluation.domain.ValeursInitialesEvaluation;
import com.hr.evaluation.repository.ParametreValeurRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.mockito.Mockito.mock;

/** Fabrique de {@link ParametresEvaluation} sur les valeurs initiales (niveaux 2 / 3 / 4), modifiables à chaud. */
public final class ParametresTestes {

    private ParametresTestes() {
    }

    public static List<ParametresInstantane.Valeur> initiales() {
        List<ParametresInstantane.Valeur> l = new ArrayList<>();
        ValeursInitialesEvaluation.toutes().forEach(v -> l.add(new ParametresInstantane.Valeur(v.referentiel(),
                v.code(), v.nature(), v.libelle(), v.description(), v.couleur(), v.ordre(), true, v.systeme(),
                v.seuilEcart())));
        return l;
    }

    public static ParametresEvaluation parDefaut() {
        return avec(ParametresInstantane.de(initiales(), null)).parametres();
    }

    /** Parametres dont l'instantané peut être remplacé ; repository mocké. */
    public record Banc(ParametresEvaluation parametres, AtomicReference<ParametresInstantane> courant,
                       ParametreValeurRepository repository) {
    }

    public static Banc avec(ParametresInstantane instantane) {
        AtomicReference<ParametresInstantane> ref = new AtomicReference<>(instantane);
        ParametreValeurRepository repo = mock(ParametreValeurRepository.class);
        ParametresCache cache = new ParametresCache(ref::get, 30);
        // lecture « en base » (validation d'écriture) : suit l'instantané courant du banc
        org.mockito.Mockito.lenient().when(repo.findByReferentielAndCode(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString())).thenAnswer(inv -> ref.get()
                .valeur(inv.getArgument(0), inv.getArgument(1)).map(ParametresTestes::enEntite));
        return new Banc(new ParametresEvaluation(cache, repo), ref, repo);
    }

    static com.hr.evaluation.entity.ParametreValeur enEntite(ParametresInstantane.Valeur v) {
        com.hr.evaluation.entity.ParametreValeur e = new com.hr.evaluation.entity.ParametreValeur();
        e.setReferentiel(v.referentiel());
        e.setCode(v.code());
        e.setNature(v.nature());
        e.setLibelle(v.libelle());
        e.setDescription(v.description());
        e.setCouleur(v.couleur());
        e.setOrdre(v.ordre());
        e.setActif(v.actif());
        e.setSysteme(v.systeme());
        e.setSeuilEcart(v.seuilEcart());
        return e;
    }
}
