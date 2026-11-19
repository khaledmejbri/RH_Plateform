package com.hr.referentiel.importation;

import com.hr.referentiel.champsperso.ReglesPerso;
import com.hr.referentiel.champsperso.TypeValeurPerso;
import com.hr.referentiel.champsperso.ValeurTypee;
import com.hr.referentiel.entity.Collaborateur;
import com.hr.referentiel.entity.UniteOrganisation;
import com.hr.referentiel.importation.ImportModeles.Colonne;
import com.hr.referentiel.importation.ImportModeles.Evaluation;
import com.hr.referentiel.importation.lecture.Fichier.LigneBrute;
import com.hr.referentiel.importation.parametrage.ExigenceChamp;
import com.hr.referentiel.importation.parametrage.ParametrageImport;
import com.hr.referentiel.importation.parametrage.ParametrageImport.ChampRegle;
import com.hr.referentiel.importation.parametrage.ParametrageImport.DefinitionPerso;
import com.hr.referentiel.importation.parametrage.TypeImportParametrable;
import com.hr.referentiel.kafka.CollaborateurCompteDemandePublisher;
import com.hr.referentiel.service.ParametreService;
import com.hr.referentiel.service.ParametresCache;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.zip.GZIPOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * T7-12 : 5 000 lignes x 30 champs personnalisés évaluées (comparaison typée avec les valeurs actuelles) et sérialisées
 * comme le résultat stocké de l'analyse, en 10 s au plus. La confirmation (150 000 insertions groupées) se mesure sur
 * la recette PostgreSQL : elle n'est pas couverte ici.
 */
class ImportPerformanceChampsPersoTest {

	private static final int LIGNES = 5_000;
	private static final int CHAMPS = 30;
	private static final Duration BORNE = Duration.ofSeconds(10);

	@Test
	@DisplayName("T7-12 : analyse de 5 000 lignes x 30 champs personnalisés en moins de 10 s (durée affichée)")
	@SuppressWarnings("unchecked")
	void analyseDeCinqMilleLignesTrenteChamps() throws Exception {
		ObjectProvider<CollaborateurCompteDemandePublisher> provider = mock(ObjectProvider.class);
		ImportCollaborateursService service = new ImportCollaborateursService(null, null, null, mock(ParametreService.class), provider,
				Clock.fixed(Instant.parse("2026-11-13T10:00:00Z"), ZoneOffset.UTC), null, null);
		ImportCollaborateursService.Contexte ctx = new ImportCollaborateursService.Contexte();
		UniteOrganisation unite = new UniteOrganisation();
		unite.setId(UUID.randomUUID());
		unite.setCode("DTECH-SW");
		unite.setLibelle("Département Software");
		unite.setActif(true);
		ctx.unites.add(unite);
		ctx.unitesParId.put(unite.getId(), unite);
		ParametresCache.Instantane ref = mock(ParametresCache.Instantane.class);
		when(ref.liste(any())).thenReturn(List.of());
		ctx.referentiels = ref;

		ParametrageImport defaut = ParametrageImport.parDefaut(TypeImportParametrable.COLLABORATEURS);
		List<ChampRegle> champs = new ArrayList<>(defaut.champs());
		List<DefinitionPerso> definitions = new ArrayList<>();
		List<String> entetes = new ArrayList<>(List.of("Matricule", "Prénom", "Nom", "Unité"));
		for (int k = 0; k < CHAMPS; k++) {
			String code = "PERSO_CHAMP_" + k;
			String enTete = "Champ perso " + k;
			champs.add(new ChampRegle(code, enTete, true, ExigenceChamp.FACULTATIF, champs.size(), List.of(), true));
			definitions.add(new DefinitionPerso(code, enTete, k % 2 == 0 ? TypeValeurPerso.NOMBRE : TypeValeurPerso.TEXTE,
					new ReglesPerso(null, 0, null, null, null, null), List.of()));
			entetes.add(enTete);
		}
		ParametrageImport p = new ParametrageImport("COLLABORATEURS", 1, champs, definitions);
		ReconnaissanceEnTetes reco = new ReconnaissanceEnTetes(p);
		List<Colonne> colonnes = new ArrayList<>();
		for (int i = 0; i < entetes.size(); i++) {
			String champ = reco.reconnaitre(entetes.get(i)).map(ChampImport::name).orElse(null);
			colonnes.add(new Colonne(i, entetes.get(i), champ, ImportModeles.RECONNUE));
		}
		assertThat(colonnes).allMatch(c -> c.champ() != null);

		List<LigneBrute> lignes = new ArrayList<>();
		for (int i = 0; i < LIGNES; i++) {
			List<String> cellules = new ArrayList<>(List.of("PERF-" + i, "Prenom" + i, "Nom" + i, "DTECH-SW"));
			for (int k = 0; k < CHAMPS; k++) {
				cellules.add(k % 2 == 0 ? String.valueOf(i + k) : "valeur " + i + "-" + k);
			}
			lignes.add(new LigneBrute(i + 2, cellules));
			if (i % 2 == 0) { // la moitié des fiches existe déjà, avec des valeurs actuelles à comparer
				Collaborateur c = new Collaborateur();
				c.setId(UUID.randomUUID());
				c.setMatricule("PERF-" + i);
				c.setPrenom("Prenom" + i);
				c.setNom("Nom" + i);
				c.setUnite(unite);
				c.setStatut("ACTIF");
				ctx.parMatricule.put(("PERF-" + i).toLowerCase(), c);
				Map<String, ValeurTypee> actuelles = new HashMap<>();
				for (int k = 0; k < CHAMPS; k++) {
					actuelles.put("PERSO_CHAMP_" + k, k % 2 == 0 ? ValeurTypee.nombre(new BigDecimal(i + k)) : ValeurTypee.texte("ancien"));
				}
				ctx.valeursPerso.put(c.getId(), actuelles);
			}
		}

		long t0 = System.nanoTime();
		Evaluation e = service.evaluer(lignes, colonnes, false, Set.of(), ctx, p, null);
		JsonMapper mapper = JsonMapper.builder().build();
		java.io.ByteArrayOutputStream sortie = new java.io.ByteArrayOutputStream();
		try (GZIPOutputStream gz = new GZIPOutputStream(sortie)) {
			gz.write(mapper.writeValueAsBytes(e)); // même travail que le stockage du résultat de l'analyse
		}
		Duration duree = Duration.ofNanos(System.nanoTime() - t0);
		System.out.println("PERF champs perso : analyse de " + LIGNES + " lignes x " + CHAMPS + " champs en " + duree.toMillis() + " ms");
		assertThat(e.lignes()).hasSize(LIGNES);
		assertThat(e.lignes()).noneMatch(ImportModeles.LigneResultat::enErreur);
		assertThat(e.totaux().creations()).isEqualTo(LIGNES / 2);
		assertThat(e.totaux().misesAJour()).isEqualTo(LIGNES / 2); // les valeurs « ancien » diffèrent des valeurs du fichier
		assertThat(duree).isLessThan(BORNE);
	}
}
