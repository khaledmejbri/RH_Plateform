package com.hr.referentiel.champsperso;

import com.hr.referentiel.champsperso.DefinitionsPerso.Def;
import com.hr.referentiel.champsperso.ValeursPersoJdbc.Ecriture;
import com.hr.referentiel.champsperso.ValeursPersoJdbc.LigneJournal;
import com.hr.referentiel.importation.parametrage.ParametrageImport;
import com.hr.referentiel.importation.parametrage.ParametrageImport.DefinitionPerso;
import com.hr.referentiel.service.AuteurParametre;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Écriture groupée des valeurs personnalisées d'un import confirmé (ADR partie 2, P2-8 et P2-9). À appeler dans la
 * transaction de confirmation : {@code FOR SHARE} sur la ligne du type, relecture EN BASE des définitions, puis JDBC
 * groupé (jamais une requête par ligne). Une valeur d'un champ supprimé, dont le type a changé, ou d'une valeur de liste
 * supprimée depuis l'analyse n'est pas écrite (avertissement) ; un champ ou une valeur seulement désactivés reste écrit
 * (paramétrage figé, D-I25).
 */
@Component
public class ValeursPersoLot {

	private final DefinitionsPerso definitions;
	private final ValeursPersoJdbc jdbc;
	private final Clock clock;

	public ValeursPersoLot(DefinitionsPerso definitions, ValeursPersoJdbc jdbc, Clock clock) {
		this.definitions = definitions;
		this.jdbc = jdbc;
		this.clock = clock;
	}

	/** Valeurs canoniques d'une ligne importée : fiche (ou identifiant du mouvement) et numéro de ligne du fichier. */
	public record Demande(UUID cible, int ligne, Map<String, String> canoniques) {
	}

	/** Valeur retenue après contrôle contre l'état courant des définitions. */
	private record Retenue(UUID cible, String code, ValeurTypee valeur, Def def) {
	}

	/** Fiches : insert / update / journal, version + 1 par fiche changée. Rend les avertissements de ligne. */
	public List<String> ecrireFiches(List<Demande> demandes, ParametrageImport snapshot, UUID importId, AuteurParametre auteur) {
		List<String> avertissements = new ArrayList<>();
		if (demandes.isEmpty() || snapshot.definitionsPerso().isEmpty()) {
			return avertissements;
		}
		Map<String, Def> base = definitions.lireSousVerrou(ValeursPersoFicheService.TYPE);
		List<Retenue> retenues = retenir(demandes, snapshot, base, avertissements);
		if (retenues.isEmpty()) {
			return avertissements;
		}
		Instant maintenant = clock.instant();
		List<UUID> ids = retenues.stream().map(Retenue::cible).distinct().sorted(Comparator.naturalOrder()).toList();
		jdbc.creerEtats(ids, maintenant, auteur.nom());
		jdbc.verrouillerEtats(ids); // verrou par lots triés : aucun interblocage
		Map<UUID, Map<String, ValeurTypee>> actuelles = jdbc.fiches(ids);
		List<Ecriture> ajouts = new ArrayList<>();
		List<Ecriture> modifs = new ArrayList<>();
		List<LigneJournal> journal = new ArrayList<>();
		Set<UUID> changees = new LinkedHashSet<>();
		for (Retenue r : retenues) {
			ValeurTypee avant = actuelles.getOrDefault(r.cible(), Map.of()).get(r.code());
			if (r.valeur().memeValeur(avant)) {
				continue;
			}
			(avant == null ? ajouts : modifs).add(new Ecriture(r.cible(), r.code(), r.valeur()));
			journal.add(new LigneJournal(r.cible(), r.code(), r.def().champ().getLibelle(),
					avant == null ? null : FormatValeurPerso.affichage(avant, r.def().definition()),
					FormatValeurPerso.affichage(r.valeur(), r.def().definition()), OrigineValeurPerso.IMPORT, importId,
					auteur.compteId(), auteur.nom(), maintenant));
			changees.add(r.cible());
		}
		jdbc.inserer(ajouts, maintenant);
		jdbc.modifier(modifs, maintenant);
		jdbc.journaliser(journal);
		jdbc.incrementerVersions(new ArrayList<>(changees), maintenant, auteur.nom());
		return avertissements;
	}

	/** Mouvements d'ajustement : insertion seule dans {@code rh_valeur_perso_mouvement}. */
	public List<String> ecrireMouvements(List<Demande> demandes, ParametrageImport snapshot) {
		List<String> avertissements = new ArrayList<>();
		if (demandes.isEmpty() || snapshot.definitionsPerso().isEmpty()) {
			return avertissements;
		}
		Map<String, Def> base = definitions.lireSousVerrou("SOLDES");
		List<Ecriture> lignes = new ArrayList<>();
		for (Retenue r : retenir(demandes, snapshot, base, avertissements)) {
			lignes.add(new Ecriture(r.cible(), r.code(), r.valeur()));
		}
		jdbc.insererMouvements(lignes);
		return avertissements;
	}

	private List<Retenue> retenir(List<Demande> demandes, ParametrageImport snapshot, Map<String, Def> base,
			List<String> avertissements) {
		List<Retenue> r = new ArrayList<>();
		for (Demande d : demandes) {
			for (Map.Entry<String, String> e : d.canoniques().entrySet()) {
				DefinitionPerso figee = snapshot.definition(e.getKey());
				Def courante = base.get(e.getKey());
				if (figee == null) {
					continue;
				}
				ValeurTypee v = FormatValeurPerso.depuisCanonique(figee.typeValeur(), e.getValue());
				boolean ok = courante != null && courante.champ().getTypeValeur() == figee.typeValeur() && v != null
						&& (figee.typeValeur() != TypeValeurPerso.LISTE || courante.definition().valeur(v.listeCode()) != null);
				if (!ok) {
					avertissements.add("Ligne " + d.ligne() + " : champ « " + figee.libelle()
							+ " » supprimé ou modifié depuis l'analyse : valeur non enregistrée.");
					continue;
				}
				r.add(new Retenue(d.cible(), e.getKey(), v, courante));
			}
		}
		return r;
	}
}
