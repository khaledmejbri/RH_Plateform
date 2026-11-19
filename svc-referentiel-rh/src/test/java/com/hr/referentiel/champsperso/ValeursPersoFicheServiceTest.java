package com.hr.referentiel.champsperso;

import com.hr.referentiel.champsperso.ValeursPersoDtos.ChampValeur;
import com.hr.referentiel.champsperso.ValeursPersoDtos.EcrireValeurs;
import com.hr.referentiel.champsperso.ValeursPersoDtos.LigneJournalValeur;
import com.hr.referentiel.champsperso.ValeursPersoDtos.ValeursFiche;
import com.hr.referentiel.dto.CollaborateurCreationRequest;
import com.hr.referentiel.dto.CollaborateurResponse;
import com.hr.referentiel.entity.Collaborateur;
import com.hr.referentiel.entity.UniteOrganisation;
import com.hr.referentiel.repository.CollaborateurRepository;
import com.hr.referentiel.repository.UniteOrganisationRepository;
import com.hr.referentiel.service.AuteurParametreResolver;
import com.hr.referentiel.service.CollaborateurConnecteService;
import com.hr.referentiel.service.CreationCollaborateurService;
import com.hr.referentiel.service.ReferentielRhService;
import com.hr.referentiel.web.ReferentielMetierException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** T6 (SCRUM-100) : valeurs d'une fiche V1 à V4 et création avec valeurs, sur base H2 réelle. */
class ValeursPersoFicheServiceTest extends ChampsPersoH2Base {

	@Autowired
	CollaborateurRepository collaborateurRepository;
	@Autowired
	UniteOrganisationRepository uniteRepository;

	private CollaborateurConnecteService connecte;
	private AuteurParametreResolver auteurResolver;
	private ValeursPersoFicheService service;
	private final Jwt jwt = Jwt.withTokenValue("t").header("alg", "none").subject("DRH01").build();

	private UniteOrganisation unite;
	private Collaborateur colWeb;
	private Collaborateur drh;
	private String pointure;
	private String code;
	private String badge;
	private String taille;

	@BeforeEach
	void init() {
		connecte = mock(CollaborateurConnecteService.class);
		auteurResolver = mock(AuteurParametreResolver.class);
		when(auteurResolver.depuis(jwt)).thenReturn(DRH01);
		when(connecte.resoudre(any())).thenReturn(Optional.empty());
		service = new ValeursPersoFicheService(collaborateurRepository, connecte, auteurResolver, definitionsLecture, valeursJdbc, jdbc, horlogeQuiAvance());
		unite = new UniteOrganisation();
		unite.setCode("SERV-WEB");
		unite.setLibelle("Service web");
		unite.setActif(true);
		unite = uniteRepository.save(unite);
		colWeb = fiche("COL-WEB", "Sarra", "Khemiri");
		drh = fiche("DRH01", "Amel", "Trabelsi");
		pointure = creer(COL, "Pointure", "NOMBRE", regles(null, 0, "30", "50"));
		code = creer(COL, "Code vestiaire", "TEXTE", regles(100, null, null, null));
		badge = creer(COL, "Date de badge", "DATE", SANS_REGLE);
		taille = creer(COL, "Taille de tenue", "LISTE", SANS_REGLE, "S", "M", "L");
	}

	/** Horloge qui avance d'une seconde à chaque lecture : des horodatages distincts, donc un ordre de journal vérifiable. */
	private static java.time.Clock horlogeQuiAvance() {
		java.util.concurrent.atomic.AtomicLong n = new java.util.concurrent.atomic.AtomicLong();
		return new java.time.Clock() {
			@Override
			public java.time.ZoneId getZone() {
				return java.time.ZoneOffset.UTC;
			}

			@Override
			public java.time.Clock withZone(java.time.ZoneId zone) {
				return this;
			}

			@Override
			public java.time.Instant instant() {
				return ChampsPersoH2Config.MAINTENANT.plusSeconds(n.incrementAndGet());
			}
		};
	}

	private Collaborateur fiche(String matricule, String prenom, String nom) {
		Collaborateur c = new Collaborateur();
		c.setMatricule(matricule);
		c.setPrenom(prenom);
		c.setNom(nom);
		c.setStatut("ACTIF");
		c.setProfilAcces("COLLABORATEUR");
		c.setUnite(unite);
		return collaborateurRepository.saveAndFlush(c);
	}

	private ValeursFiche ecrire(UUID id, long version, Map<String, Object> valeurs) {
		return service.ecrire(id, new EcrireValeurs(version, valeurs), jwt, true);
	}

	private static Map<String, Object> valeurs(Object... kv) {
		Map<String, Object> m = new HashMap<>();
		for (int i = 0; i < kv.length; i += 2) {
			m.put((String) kv[i], kv[i + 1]);
		}
		return m;
	}

	private ChampValeur lu(ValeursFiche v, String codeChamp) {
		return v.champs().stream().filter(c -> c.code().equals(codeChamp)).findFirst().orElseThrow();
	}

	@Test
	@DisplayName("T6-3a : écriture de 4 valeurs : V1 les relit, V3 trace 4 lignes « Saisie sur la fiche » de l'auteur, version 0 -> 1")
	void ecritureLectureEtJournal() {
		ValeursFiche r = ecrire(colWeb.getId(), 0, valeurs(taille, "M", pointure, "42", badge, "15/11/2026", code, "B-12"));
		assertThat(r.version()).isEqualTo(1);
		assertThat(lu(r, pointure).valeur()).isEqualTo("42");
		assertThat(lu(r, badge).valeurAffichee()).isEqualTo("15/11/2026");
		assertThat(lu(r, taille).valeurAffichee()).isEqualTo("M");
		assertThat(lu(r, code).valeur()).isEqualTo("B-12");
		List<LigneJournalValeur> j = service.journal(colWeb.getId(), 0, 20).contenu();
		assertThat(j).hasSize(4).allMatch(l -> l.auteurNom().equals("Amel Trabelsi") && l.origineLibelle().equals("Saisie sur la fiche")
				&& l.avant() == null);
		assertThat(j).extracting(LigneJournalValeur::champLibelle).containsExactlyInAnyOrder("Pointure", "Code vestiaire", "Date de badge",
				"Taille de tenue");
	}

	@Test
	@DisplayName("T6-4 : valeur null -> effacée et journalisée « B-12 -> (vide) » ; chaîne vide aussi")
	void effacement() {
		ecrire(colWeb.getId(), 0, valeurs(code, "B-12"));
		ValeursFiche r = ecrire(colWeb.getId(), 1, valeurs(code, null));
		assertThat(lu(r, code).valeur()).isNull();
		LigneJournalValeur l = service.journal(colWeb.getId(), 0, 20).contenu().get(0);
		assertThat(l.avant()).isEqualTo("B-12");
		assertThat(l.apres()).isNull();
		ecrire(colWeb.getId(), 2, valeurs(code, "X1"));
		assertThat(lu(ecrire(colWeb.getId(), 3, valeurs(code, "")), code).valeur()).isNull();
	}

	@Test
	@DisplayName("T6-5 : même valeur ou « 42,0 » pour 42 -> 200 sans ligne de journal ni nouvelle version")
	void aucunChangementReel() {
		ecrire(colWeb.getId(), 0, valeurs(pointure, "42"));
		ValeursFiche r = ecrire(colWeb.getId(), 1, valeurs(pointure, "42,0"));
		assertThat(r.version()).isEqualTo(1);
		assertThat(service.journal(colWeb.getId(), 0, 20).total()).isEqualTo(1);
	}

	@Test
	@DisplayName("T6-3 règle 3 : version absente -> 400 ; périmée -> 409 VALEURS_MODIFIEES_ENTRE_TEMPS, rien n'est écrit")
	void versionDesValeurs() {
		erreur(echec(() -> service.ecrire(colWeb.getId(), new EcrireValeurs(null, valeurs(code, "A")), jwt, true)), 400, "VERSION_OBLIGATOIRE");
		ecrire(colWeb.getId(), 0, valeurs(code, "A"));
		erreur(echec(() -> ecrire(colWeb.getId(), 0, valeurs(code, "B"))), 409, "VALEURS_MODIFIEES_ENTRE_TEMPS");
		assertThat(lu(service.lire(colWeb.getId(), jwt, true), code).valeur()).isEqualTo("A");
	}

	@Test
	@DisplayName("T6-2 : toutes les erreurs de valeur sont listées dans erreurs[] et rien n'est enregistré")
	void toutesLesErreursListees() {
		Throwable t = echec(() -> ecrire(colWeb.getId(), 0, valeurs(pointure, "42,5", badge, "31/02/2026", taille, "XXS", code, "ok")));
		erreur(t, 400, "VALEUR_INVALIDE");
		ReferentielMetierException e = (ReferentielMetierException) t;
		@SuppressWarnings("unchecked")
		List<Map<String, String>> erreurs = (List<Map<String, String>>) e.getDetails().get("erreurs");
		assertThat(erreurs).hasSize(3).extracting(m -> m.get("code")).containsExactlyInAnyOrder("VALEUR_INVALIDE", "DATE_INVALIDE", "VALEUR_INCONNUE");
		assertThat(service.lire(colWeb.getId(), jwt, true).version()).isZero();
		assertThat(lu(service.lire(colWeb.getId(), jwt, true), code).valeur()).isNull();
	}

	@Test
	@DisplayName("T6-7 : un RH ou ADMIN ne modifie jamais sa propre fiche (403 AUTO_MODIFICATION_INTERDITE) ; la fiche d'un autre RH est modifiable ; 404 avant 403")
	void autoModificationInterdite() {
		when(connecte.resoudre(any())).thenReturn(Optional.of(drh));
		erreur(echec(() -> ecrire(drh.getId(), 0, valeurs(code, "A"))), 403, "AUTO_MODIFICATION_INTERDITE");
		assertThat(ecrire(colWeb.getId(), 0, valeurs(code, "A")).version()).isEqualTo(1);
		erreur(echec(() -> ecrire(UUID.randomUUID(), 0, valeurs(code, "A"))), 404, "COLLABORATEUR_INTROUVABLE");
		ValeursFiche soi = service.lire(drh.getId(), jwt, true);
		assertThat(soi.modifiable()).isFalse();
		assertThat(soi.raisonNonModifiable()).isEqualTo("Vous ne pouvez pas modifier vos propres informations.");
	}

	@Test
	@DisplayName("T6-8 : champ inactif -> 422 CHAMP_INACTIF ; code inconnu -> 400 CHAMP_INCONNU ; V1 : DIRECTION ne peut pas modifier (consultation uniquement)")
	void champInactifInconnuEtLectureSeule() {
		definitions.desactiver(COL, code, version(COL), DRH01);
		erreur(echec(() -> ecrire(colWeb.getId(), 0, valeurs(code, "A"))), 422, "CHAMP_INACTIF");
		erreur(echec(() -> ecrire(colWeb.getId(), 0, valeurs("PERSO_X", "A"))), 400, "CHAMP_INCONNU");
		ValeursFiche lecture = service.lire(colWeb.getId(), jwt, false);
		assertThat(lecture.modifiable()).isFalse();
		assertThat(lecture.raisonNonModifiable()).isEqualTo("Consultation uniquement.");
	}

	@Test
	@DisplayName("T6-9 : un champ désactivé renseigné passe dans champs_desactives (lecture seule) et revient à la réactivation")
	void champDesactiveRenseigne() {
		ecrire(colWeb.getId(), 0, valeurs(pointure, "42"));
		definitions.desactiver(COL, pointure, version(COL), DRH01);
		ValeursFiche v = service.lire(colWeb.getId(), jwt, true);
		assertThat(v.champs()).noneMatch(c -> c.code().equals(pointure));
		assertThat(v.champsDesactives()).singleElement().satisfies(c -> {
			assertThat(c.code()).isEqualTo(pointure);
			assertThat(c.valeurAffichee()).isEqualTo("42");
		});
		definitions.reactiver(COL, pointure, version(COL), DRH01);
		assertThat(lu(service.lire(colWeb.getId(), jwt, true), pointure).valeur()).isEqualTo("42");
	}

	@Test
	@DisplayName("T6-10 : une règle resserrée ne revalide pas la valeur existante ; une nouvelle saisie hors règle est refusée")
	void regleResserreeSansRevalidation() {
		ecrire(colWeb.getId(), 0, valeurs(pointure, "42"));
		definitions.modifier(COL, pointure, new ChampPersoDtos.ModifierChampPerso(version(COL), "Pointure", "Finalité de test", "NOMBRE",
				regles(null, 0, "30", "40"), null, false), DRH01);
		ValeursFiche v = service.lire(colWeb.getId(), jwt, true);
		assertThat(lu(v, pointure).valeur()).isEqualTo("42");
		assertThat(ecrire(colWeb.getId(), v.version(), valeurs(code, "A")).version()).isEqualTo(2); // ne touche que le code
		erreur(echec(() -> ecrire(colWeb.getId(), 2, valeurs(pointure, "41,0"))), 400, "VALEUR_HORS_BORNES");
	}

	@Test
	@DisplayName("T6-9 règle 9 : V4 ne renvoie que les champs actifs, visibles et renseignés ; un appelant sans fiche reçoit une liste vide")
	void mesChamps() {
		definitions.modifier(COL, taille, new ChampPersoDtos.ModifierChampPerso(version(COL), "Taille de tenue", "Finalité de test", "LISTE",
				SANS_REGLE, null, true), DRH01); // visible
		ecrire(colWeb.getId(), 0, valeurs(taille, "M", code, "B-12"));
		when(connecte.resoudre(any())).thenReturn(Optional.of(colWeb));
		assertThat(service.moi(jwt).champs()).singleElement().satisfies(c -> {
			assertThat(c.libelle()).isEqualTo("Taille de tenue");
			assertThat(c.valeurAffichee()).isEqualTo("M");
		});
		when(connecte.resoudre(any())).thenReturn(Optional.empty());
		assertThat(service.moi(jwt).champs()).isEmpty();
	}

	@Test
	@DisplayName("T6-6 règle 11 : création avec valeurs : contrôlées avant la fiche (invalide -> 400, aucune fiche) puis journalisées « Création de la fiche », version 1")
	void creationAvecValeurs() {
		ReferentielRhService rh = mock(ReferentielRhService.class);
		CreationCollaborateurService facade = new CreationCollaborateurService(rh, service, definitionsLecture);
		CollaborateurCreationRequest invalide = new CollaborateurCreationRequest();
		invalide.setChampsPersonnalises(valeurs(pointure, "abc"));
		erreur(echec(() -> facade.creer(invalide, jwt)), 400, "FORMAT_INVALIDE");
		verify(rh, never()).creerCollaborateur(any());

		Collaborateur nouvelle = fiche("NEW-1", "Nour", "Ben Ali");
		CollaborateurResponse reponse = new CollaborateurResponse();
		reponse.setIdentifiant(nouvelle.getId());
		when(rh.creerCollaborateur(any())).thenReturn(reponse);
		CollaborateurCreationRequest ok = new CollaborateurCreationRequest();
		ok.setChampsPersonnalises(valeurs(taille, "L"));
		facade.creer(ok, jwt);
		ValeursFiche v = service.lire(nouvelle.getId(), jwt, true);
		assertThat(v.version()).isEqualTo(1);
		assertThat(lu(v, taille).valeur()).isEqualTo("L");
		assertThat(service.journal(nouvelle.getId(), 0, 20).contenu()).singleElement()
				.satisfies(l -> assertThat(l.origineLibelle()).isEqualTo("Création de la fiche"));
		CollaborateurCreationRequest inactif = new CollaborateurCreationRequest();
		definitions.desactiver(COL, taille, version(COL), DRH01);
		inactif.setChampsPersonnalises(valeurs(taille, "L"));
		erreur(echec(() -> facade.creer(inactif, jwt)), 422, "CHAMP_INACTIF");
	}

	@Test
	@DisplayName("T6-6 règle 6 : le journal est paginé, du plus récent au plus ancien, 20 par page, taille limitée à 100")
	void journalPagine() {
		for (int i = 0; i < 25; i++) {
			ecrire(colWeb.getId(), i, valeurs(code, "V" + i));
		}
		assertThat(service.journal(colWeb.getId(), 0, 20).contenu()).hasSize(20);
		assertThat(service.journal(colWeb.getId(), 1, 20).contenu()).hasSize(5);
		assertThat(service.journal(colWeb.getId(), 0, 20).contenu().get(0).apres()).isEqualTo("V24");
		assertThat(service.journal(colWeb.getId(), 0, 20).total()).isEqualTo(25);
		erreur(echec(() -> service.journal(colWeb.getId(), 0, 101)), 400, "CORPS_INVALIDE");
	}
}
