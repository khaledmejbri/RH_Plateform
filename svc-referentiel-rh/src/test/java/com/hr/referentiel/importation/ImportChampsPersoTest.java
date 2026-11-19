package com.hr.referentiel.importation;

import com.hr.referentiel.champsperso.ReglesPerso;
import com.hr.referentiel.champsperso.TypeValeurPerso;
import com.hr.referentiel.champsperso.ValeurTypee;
import com.hr.referentiel.champsperso.ValeursPersoLot;
import com.hr.referentiel.entity.Collaborateur;
import com.hr.referentiel.entity.UniteOrganisation;
import com.hr.referentiel.importation.ImportModeles.Colonne;
import com.hr.referentiel.importation.ImportModeles.Evaluation;
import com.hr.referentiel.importation.ImportModeles.LigneResultat;
import com.hr.referentiel.importation.lecture.Fichier.LigneBrute;
import com.hr.referentiel.importation.parametrage.ExigenceChamp;
import com.hr.referentiel.importation.parametrage.ParametrageImport;
import com.hr.referentiel.importation.parametrage.ParametrageImport.ChampRegle;
import com.hr.referentiel.importation.parametrage.ParametrageImport.DefinitionPerso;
import com.hr.referentiel.importation.parametrage.ParametrageImport.ValeurListe;
import com.hr.referentiel.importation.parametrage.TypeImportParametrable;
import com.hr.referentiel.kafka.CollaborateurCompteDemandePublisher;
import com.hr.referentiel.repository.CollaborateurRepository;
import com.hr.referentiel.service.AuteurParametre;
import com.hr.referentiel.service.ParametreService;
import com.hr.referentiel.service.ParametresCache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** T7 (SCRUM-101) : colonnes personnalisées d'un import de collaborateurs (évaluation sans écriture, puis application). */
class ImportChampsPersoTest {

	private static final String POINTURE = "PERSO_POINTURE";
	private static final String CODE = "PERSO_CODE_VESTIAIRE";
	private static final String TAILLE = "PERSO_TAILLE_DE_TENUE";
	private static final String BADGE = "PERSO_DATE_DE_BADGE";

	private ImportCollaborateursService service;
	private ImportCollaborateursService.Contexte ctx;
	private CollaborateurRepository repository;
	private ValeursPersoLot lot;
	private Collaborateur colWeb;
	private Collaborateur drh;
	private UniteOrganisation unite;

	@BeforeEach
	void setUp() {
		repository = mock(CollaborateurRepository.class);
		lot = mock(ValeursPersoLot.class);
		@SuppressWarnings("unchecked")
		ObjectProvider<CollaborateurCompteDemandePublisher> provider = mock(ObjectProvider.class);
		service = new ImportCollaborateursService(repository, null, null, mock(ParametreService.class), provider,
				Clock.fixed(Instant.parse("2026-11-13T10:00:00Z"), ZoneOffset.UTC), null, lot);
		ctx = new ImportCollaborateursService.Contexte();
		unite = new UniteOrganisation();
		unite.setId(UUID.randomUUID());
		unite.setCode("DTECH-SW");
		unite.setLibelle("Département Software");
		unite.setActif(true);
		ctx.unites.add(unite);
		ctx.unitesParId.put(unite.getId(), unite);
		colWeb = fiche("COL-WEB", "Sarra");
		drh = fiche("DRH01", "Amel");
		ParametresCache.Instantane ref = mock(ParametresCache.Instantane.class);
		when(ref.liste(any())).thenReturn(List.of());
		ctx.referentiels = ref;
		ctx.valeursPerso.put(colWeb.getId(), new HashMap<>(Map.of(POINTURE, ValeurTypee.nombre(new BigDecimal("42")),
				TAILLE, ValeurTypee.liste("M"))));
	}

	private Collaborateur fiche(String matricule, String prenom) {
		Collaborateur c = new Collaborateur();
		c.setId(UUID.randomUUID());
		c.setMatricule(matricule);
		c.setPrenom(prenom);
		c.setNom("Test");
		c.setUnite(unite);
		c.setStatut("ACTIF");
		c.setProfilAcces("COLLABORATEUR");
		ctx.parMatricule.put(matricule.toLowerCase(), c);
		return c;
	}

	/** Instantané : champs fixes par défaut + Pointure (nombre), Code (texte 5), Taille (liste), Badge (date). */
	private ParametrageImport snapshot(ExigenceChamp exigencePointure) {
		ParametrageImport defaut = ParametrageImport.parDefaut(TypeImportParametrable.COLLABORATEURS);
		List<ChampRegle> champs = new ArrayList<>(defaut.champs());
		int o = champs.size();
		champs.add(new ChampRegle(POINTURE, "Pointure", true, exigencePointure, o++, List.of("pointure chaussures"), true));
		champs.add(new ChampRegle(CODE, "Code vestiaire", true, ExigenceChamp.FACULTATIF, o++, List.of(), true));
		champs.add(new ChampRegle(TAILLE, "Taille de tenue", true, ExigenceChamp.FACULTATIF, o++, List.of(), true));
		champs.add(new ChampRegle(BADGE, "Date de badge", true, ExigenceChamp.FACULTATIF, o, List.of(), true));
		return new ParametrageImport("COLLABORATEURS", 7, champs, List.of(
				new DefinitionPerso(POINTURE, "Pointure", TypeValeurPerso.NOMBRE,
						new ReglesPerso(null, 0, new BigDecimal("30"), new BigDecimal("50"), null, null), List.of()),
				new DefinitionPerso(CODE, "Code vestiaire", TypeValeurPerso.TEXTE, new ReglesPerso(5, null, null, null, null, null), List.of()),
				new DefinitionPerso(TAILLE, "Taille de tenue", TypeValeurPerso.LISTE, ReglesPerso.AUCUNE,
						List.of(new ValeurListe("S", "Small", true), new ValeurListe("M", "Médium", true), new ValeurListe("XL", "XL", false))),
				new DefinitionPerso(BADGE, "Date de badge", TypeValeurPerso.DATE, ReglesPerso.AUCUNE, List.of())));
	}

	private List<Colonne> colonnes(ParametrageImport p, String... entetes) {
		ReconnaissanceEnTetes reco = new ReconnaissanceEnTetes(p);
		List<Colonne> r = new ArrayList<>();
		for (int i = 0; i < entetes.length; i++) {
			String champ = reco.reconnaitre(entetes[i]).map(ChampImport::name).orElse(null);
			r.add(new Colonne(i, entetes[i], champ, champ == null ? ImportModeles.IGNOREE : ImportModeles.RECONNUE));
		}
		return r;
	}

	private static List<LigneBrute> lignes(String[]... v) {
		List<LigneBrute> r = new ArrayList<>();
		for (int i = 0; i < v.length; i++) {
			r.add(new LigneBrute(i + 2, List.of(v[i])));
		}
		return r;
	}

	private static String[] l(String... v) {
		return v;
	}

	private Evaluation evaluer(ParametrageImport p, List<Colonne> c, List<LigneBrute> lignes, UUID auteurFiche) {
		return service.evaluer(lignes, c, false, Set.of(), ctx, p, auteurFiche);
	}

	private static boolean erreur(LigneResultat r, String code) {
		return r.erreurs().stream().anyMatch(e -> e.code().equals(code));
	}

	@Test
	@DisplayName("T7-1 : les colonnes personnalisées sont reconnues par leur en-tête ou un synonyme ; les valeurs canoniques accompagnent la ligne ; aucune IllegalArgumentException sur un code PERSO_")
	void colonnesReconnuesEtValeursCanoniques() {
		ParametrageImport p = snapshot(ExigenceChamp.FACULTATIF);
		List<Colonne> c = colonnes(p, "Matricule", "Prénom", "Nom", "Unité", "Pointure chaussures", "Taille de tenue", "Date de badge");
		assertThat(c).extracting(Colonne::champ).containsExactly("MATRICULE", "PRENOM", "NOM", "UNITE", POINTURE, TAILLE, BADGE);
		Evaluation e = evaluer(p, c, lignes(l("NEW-1", "Ada", "Lovelace", "DTECH-SW", "42,0", "Médium", "15/11/2026")), null);
		LigneResultat r = e.lignes().get(0);
		assertThat(r.erreurs()).isEmpty();
		assertThat(r.action()).isEqualTo("CREATION");
		assertThat(r.valeurs()).containsEntry(POINTURE, "42").containsEntry(TAILLE, "M").containsEntry(BADGE, "2026-11-15");
	}

	@Test
	@DisplayName("T7-2 : comparaison typée avec la valeur actuelle : « 42,0 » = 42 -> Inchangée ; « 43 » -> Mise à jour avec libellé du champ, avant 42 et après 43")
	void comparaisonTypee() {
		ParametrageImport p = snapshot(ExigenceChamp.FACULTATIF);
		List<Colonne> c = colonnes(p, "Matricule", "Pointure", "Taille de tenue");
		LigneResultat egal = evaluer(p, c, lignes(l("COL-WEB", "42,0", "M")), null).lignes().get(0);
		assertThat(egal.action()).isEqualTo("INCHANGEE");
		assertThat(egal.modifications()).isEmpty();
		LigneResultat change = evaluer(p, c, lignes(l("COL-WEB", "43", "small")), null).lignes().get(0);
		assertThat(change.action()).isEqualTo("MISE_A_JOUR");
		assertThat(change.modifications()).extracting(m -> m.champLibelle() + ":" + m.avant() + ">" + m.apres())
				.containsExactlyInAnyOrder("Pointure:42>43", "Taille de tenue:Médium>Small");
	}

	@Test
	@DisplayName("T7-3 : une cellule vide ne change rien (jamais un effacement)")
	void celluleVide() {
		ParametrageImport p = snapshot(ExigenceChamp.FACULTATIF);
		LigneResultat r = evaluer(p, colonnes(p, "Matricule", "Pointure", "Code vestiaire"), lignes(l("COL-WEB", "", "")), null).lignes().get(0);
		assertThat(r.action()).isEqualTo("INCHANGEE");
		assertThat(r.valeurs()).doesNotContainKeys(POINTURE, CODE);
	}

	@Test
	@DisplayName("T7-4 : erreurs de ligne par type, avec l'en-tête réglé figé comme colonne : DATE_INVALIDE, VALEUR_HORS_BORNES, VALEUR_TROP_LONGUE, VALEUR_INCONNUE (liste désactivée comprise), VALEUR_INVALIDE")
	void erreursDeLigne() {
		ParametrageImport p = snapshot(ExigenceChamp.FACULTATIF);
		List<Colonne> c = colonnes(p, "Matricule", "Pointure", "Code vestiaire", "Taille de tenue", "Date de badge");
		LigneResultat r = evaluer(p, c, lignes(l("COL-WEB", "12", "TROP-LONG", "XL", "31/02/2026")), null).lignes().get(0);
		assertThat(r.action()).isEqualTo("ERREUR");
		assertThat(r.erreurs()).extracting(e -> e.code()).containsExactlyInAnyOrder("VALEUR_HORS_BORNES", "VALEUR_TROP_LONGUE",
				"VALEUR_INCONNUE", "DATE_INVALIDE");
		assertThat(r.erreurs()).extracting(e -> e.colonne()).containsExactlyInAnyOrder("Pointure", "Code vestiaire", "Taille de tenue",
				"Date de badge");
		assertThat(evaluer(p, c, lignes(l("COL-WEB", "42,5", "", "", "")), null).lignes().get(0).erreurs())
				.extracting(e -> e.code()).containsExactly("VALEUR_INVALIDE");
	}

	@Test
	@DisplayName("T7-5 : exigence « toujours obligatoire » d'un champ personnalisé : cellule vide -> CHAMP_OBLIGATOIRE « Pointure obligatoire. » ; « à la création » : seulement sur une création")
	void exigencesDesChampsPersonnalises() {
		ParametrageImport toujours = snapshot(ExigenceChamp.TOUJOURS);
		LigneResultat r = evaluer(toujours, colonnes(toujours, "Matricule", "Pointure"), lignes(l("COL-WEB", "")), null).lignes().get(0);
		assertThat(erreur(r, "CHAMP_OBLIGATOIRE")).isTrue();
		assertThat(r.erreurs().get(0).message()).isEqualTo("Pointure obligatoire.");
		ParametrageImport creation = snapshot(ExigenceChamp.CREATION);
		List<Colonne> c = colonnes(creation, "Matricule", "Prénom", "Nom", "Unité", "Pointure");
		Evaluation e = evaluer(creation, c, lignes(l("NEW-1", "Ada", "Lovelace", "DTECH-SW", ""), l("COL-WEB", "", "", "", "")), null);
		assertThat(erreur(e.lignes().get(0), "CHAMP_OBLIGATOIRE")).isTrue();
		assertThat(e.lignes().get(1).erreurs()).isEmpty();
	}

	@Test
	@DisplayName("T7-6 : la ligne de l'auteur qui changerait une de ses valeurs personnalisées -> AUTO_MODIFICATION_INTERDITE ; sans changement réel -> acceptée ; une autre fiche -> acceptée")
	void autoModificationInterdite() {
		ctx.valeursPerso.put(drh.getId(), new HashMap<>(Map.of(POINTURE, ValeurTypee.nombre(new BigDecimal("40")))));
		ParametrageImport p = snapshot(ExigenceChamp.FACULTATIF);
		List<Colonne> c = colonnes(p, "Matricule", "Pointure");
		assertThat(erreur(evaluer(p, c, lignes(l("DRH01", "41")), drh.getId()).lignes().get(0), "AUTO_MODIFICATION_INTERDITE")).isTrue();
		assertThat(evaluer(p, c, lignes(l("DRH01", "40")), drh.getId()).lignes().get(0).erreurs()).isEmpty();
		assertThat(evaluer(p, c, lignes(l("COL-WEB", "41")), drh.getId()).lignes().get(0).erreurs()).isEmpty();
	}

	@Test
	@DisplayName("T7-7 : un champ personnalisé inactif (absent de l'instantané) n'est pas reconnu : colonne ignorée")
	void champInactifIgnore() {
		ParametrageImport p = snapshot(ExigenceChamp.FACULTATIF);
		List<ChampRegle> sans = p.champs().stream().filter(x -> !x.code().equals(POINTURE)).toList();
		List<DefinitionPerso> defs = p.definitionsPerso().stream().filter(d -> !d.code().equals(POINTURE)).toList();
		ParametrageImport figee = new ParametrageImport(p.typeImport(), p.version(), sans, defs);
		List<Colonne> c = colonnes(figee, "Matricule", "Pointure");
		assertThat(c.get(1).champ()).isNull();
		assertThat(evaluer(figee, c, lignes(l("COL-WEB", "99")), null).lignes().get(0).action()).isEqualTo("INCHANGEE");
	}

	@Test
	@DisplayName("T7-8 : à l'application, les valeurs personnalisées partent en lot vers ValeursPersoLot (fiche existante ou créée), avec l'identifiant de l'import et l'instantané")
	void applicationEnLot() {
		when(repository.saveAll(any())).thenAnswer(i -> {
			List<Collaborateur> liste = i.getArgument(0);
			liste.forEach(x -> {
				if (x.getId() == null) {
					x.setId(UUID.randomUUID());
				}
			});
			return liste;
		});
		when(lot.ecrireFiches(any(), any(), any(), any())).thenReturn(List.of("Ligne 3 : avertissement"));
		ParametrageImport p = snapshot(ExigenceChamp.FACULTATIF);
		List<Colonne> c = colonnes(p, "Matricule", "Prénom", "Nom", "Unité", "Pointure");
		Evaluation e = evaluer(p, c, lignes(l("NEW-1", "Ada", "Lovelace", "DTECH-SW", "44"), l("COL-WEB", "", "", "", "45")), null);
		UUID importId = UUID.randomUUID();
		AuteurParametre auteur = new AuteurParametre(UUID.randomUUID(), "Amel Trabelsi");
		ImportCollaborateursService.Application app = service.appliquer(e.lignes(), ctx, importId, p, auteur);
		@SuppressWarnings("unchecked")
		ArgumentCaptor<List<ValeursPersoLot.Demande>> demandes = ArgumentCaptor.forClass(List.class);
		verify(lot).ecrireFiches(demandes.capture(), any(), org.mockito.ArgumentMatchers.eq(importId), org.mockito.ArgumentMatchers.eq(auteur));
		assertThat(demandes.getValue()).hasSize(2);
		assertThat(demandes.getValue()).extracting(ValeursPersoLot.Demande::cible).contains(colWeb.getId());
		assertThat(demandes.getValue()).allMatch(d -> d.canoniques().keySet().equals(Set.of(POINTURE)));
		assertThat(app.avertissements()).containsExactly("Ligne 3 : avertissement");
	}

	@Test
	@DisplayName("Instantané étendu : sérialisé en JSON avec les définitions ; un résultat stocké avant le déploiement (sans le composant) se lit avec une liste vide")
	void instantaneEtendu() {
		JsonMapper mapper = JsonMapper.builder().build();
		ParametrageImport p = snapshot(ExigenceChamp.FACULTATIF);
		ParametrageImport relu = mapper.readValue(mapper.writeValueAsString(p), ParametrageImport.class);
		assertThat(relu.definitionsPerso()).hasSize(4);
		assertThat(relu.definition(TAILLE).valeur("XL").actif()).isFalse();
		assertThat(relu.definition(POINTURE).regles().maximum()).isEqualByComparingTo("50");
		assertThat(relu.champ(POINTURE).personnalise()).isTrue();
		assertThat(relu.champ("MATRICULE").personnalise()).isFalse();
		String ancien = "{\"typeImport\":\"COLLABORATEURS\",\"version\":3,\"champs\":[{\"code\":\"MATRICULE\",\"enTete\":\"Matricule\","
				+ "\"dansModele\":true,\"exigence\":\"TOUJOURS\",\"ordre\":0,\"formesActives\":[]}]}";
		ParametrageImport vieux = mapper.readValue(ancien, ParametrageImport.class);
		assertThat(vieux.definitionsPerso()).isEmpty();
		assertThat(vieux.champ("MATRICULE").personnalise()).isFalse();
		assertThat(ParametrageImport.parDefaut(TypeImportParametrable.SOLDES).definitionsPerso()).isEmpty();
	}

	@Test
	@DisplayName("Reconnaissance en deux temps : « Solde au 30/11 » reste SOLDE ; « Taille 2 » ne désigne pas « Taille » ; « Adresse 1 » et « Adresse 2 » sont deux champs ; « Identifiant 10 » reste MATRICULE")
	void reconnaissanceDeuxTemps() {
		ParametrageImport defaut = ParametrageImport.parDefaut(TypeImportParametrable.SOLDES);
		assertThat(new ReconnaissanceEnTetes(defaut).reconnaitre("Solde au 30/11").map(ChampImport::name)).contains("SOLDE");
		ParametrageImport collab = ParametrageImport.parDefaut(TypeImportParametrable.COLLABORATEURS);
		List<ChampRegle> champs = new ArrayList<>(collab.champs());
		champs.add(new ChampRegle("PERSO_ADRESSE_1", "Adresse 1", true, ExigenceChamp.FACULTATIF, 14, List.of(), true));
		champs.add(new ChampRegle("PERSO_ADRESSE_2", "Adresse 2", true, ExigenceChamp.FACULTATIF, 15, List.of(), true));
		champs.add(new ChampRegle("PERSO_TAILLE", "Taille", true, ExigenceChamp.FACULTATIF, 16, List.of(), true));
		ParametrageImport p = new ParametrageImport("COLLABORATEURS", 1, champs, List.of(
				new DefinitionPerso("PERSO_ADRESSE_1", "Adresse 1", TypeValeurPerso.TEXTE, null, null),
				new DefinitionPerso("PERSO_ADRESSE_2", "Adresse 2", TypeValeurPerso.TEXTE, null, null),
				new DefinitionPerso("PERSO_TAILLE", "Taille", TypeValeurPerso.TEXTE, null, null)));
		ReconnaissanceEnTetes reco = new ReconnaissanceEnTetes(p);
		assertThat(reco.reconnaitre("Adresse 1").map(ChampImport::name)).contains("PERSO_ADRESSE_1");
		assertThat(reco.reconnaitre("ADRESSE 2").map(ChampImport::name)).contains("PERSO_ADRESSE_2");
		assertThat(reco.reconnaitre("Taille 2")).isEmpty();
		assertThat(reco.reconnaitre("Taille").map(ChampImport::name)).contains("PERSO_TAILLE");
		assertThat(reco.reconnaitre("Identifiant 10").map(ChampImport::name)).contains("MATRICULE");
	}
}
