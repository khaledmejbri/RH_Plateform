package com.hr.evaluation.service;

import com.hr.evaluation.domain.NiveauAlerte;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;

/**
 * Calcul pur (sans état) des niveaux d'alerte d'écart (ADR lot 3 § 3, D-R04), remplace {@code EcartEtoiles.niveau}.
 * <ul>
 *   <li>{@code niveau(ecart, niveauxActifs)} = le niveau actif de plus grand seuil inférieur ou égal à l'écart, sinon
 *       {@link NiveauAlerte#ALIGNE}.</li>
 *   <li>Toute comparaison de niveaux compare les <b>seuils actuels</b> des codes : un code désactivé garde son seuil.
 *       Un code inconnu vaut 0 (comme {@code ALIGNE}).</li>
 * </ul>
 */
public final class NiveauxAlerte {

    /**
     * Niveaux initiaux (écart de 2 / 3 / 4 étoiles) : repli quand le paramétrage ne renvoie aucun niveau actif
     * (amorçage non terminé, tous désactivés par erreur), pour ne jamais répondre « aligné » à un vrai écart.
     */
    public static final List<NiveauAlerte> PAR_DEFAUT = List.of(
            new NiveauAlerte("ORANGE", "Alerte", "ORANGE", 2, true),
            new NiveauAlerte("ROUGE", "Critique", "ROUGE", 3, true),
            new NiveauAlerte("URGENTE", "Urgente", "VIOLET", 4, true));

    private NiveauxAlerte() {
    }

    public static String niveau(int ecart, Collection<NiveauAlerte> niveauxActifs) {
        return niveauxActifs.stream()
                .filter(n -> n.actif() && n.seuil() <= ecart)
                .max(Comparator.comparingInt(NiveauAlerte::seuil))
                .map(NiveauAlerte::code)
                .orElse(NiveauAlerte.ALIGNE);
    }

    /** Seuil d'un code parmi tous les niveaux (actifs et inactifs) ; 0 pour {@code ALIGNE}, null ou inconnu. */
    public static int seuil(String code, Collection<NiveauAlerte> tousLesNiveaux) {
        if (code == null || NiveauAlerte.ALIGNE.equals(code)) {
            return 0;
        }
        return tousLesNiveaux.stream()
                .filter(n -> n.code().equals(code))
                .mapToInt(NiveauAlerte::seuil)
                .findFirst()
                .orElse(0);
    }

    public static int comparer(String a, String b, Collection<NiveauAlerte> tousLesNiveaux) {
        return Integer.compare(seuil(a, tousLesNiveaux), seuil(b, tousLesNiveaux));
    }

    public static String max(String a, String b, Collection<NiveauAlerte> tousLesNiveaux) {
        return comparer(a, b, tousLesNiveaux) >= 0 ? a : b;
    }
}
