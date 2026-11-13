package com.hr.referentiel.service;

import com.hr.referentiel.domain.OrigineMouvementSolde;
import com.hr.referentiel.domain.TypeMouvementSolde;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Ajustements de soldes en lot (import des soldes, D-I13.k : 5 000 lignes en moins de 60 s). Le contrat est celui de
 * {@link SoldeCongeService} (acquis jamais négatif, mouvement immuable, version incrémentée, acquis = somme des
 * mouvements) mais sans une requête par ligne :
 * <ol>
 *   <li>création des soldes manquants (insertion groupée {@code ON CONFLICT DO NOTHING}) ;</li>
 *   <li>verrou de tous les soldes visés par lots d'identifiants triés (ordre stable : aucun interblocage) avec une
 *       lecture fraîche de l'acquis et de la version, hors contexte de persistance (jamais d'entité périmée, B2) ;</li>
 *   <li>mouvements et mises à jour de soldes en JDBC groupé.</li>
 * </ol>
 * À appeler dans la transaction de confirmation.
 */
@Service
public class SoldeLotService {

	static final int TAILLE_LOT_VERROU = 500;

	/** Ligne à ajuster : solde cible absolu et motif. */
	public record Ajustement(UUID collaborateurId, BigDecimal cible, String motif) {
	}

	/** Résultat par fiche : écart réellement appliqué ({@code null} = déjà égal à la cible, aucun mouvement). */
	public record Resultat(UUID collaborateurId, BigDecimal ecart, BigDecimal soldeApres, String motif) {
	}

	private final JdbcTemplate jdbc;
	private final Clock clock;

	public SoldeLotService(JdbcTemplate jdbc, Clock clock) {
		this.jdbc = jdbc;
		this.clock = clock;
	}

	public List<Resultat> ajuster(List<Ajustement> demandes, UUID importId, AuteurParametre auteur) {
		List<Ajustement> ordre = new ArrayList<>(demandes);
		ordre.sort(Comparator.comparing(Ajustement::collaborateurId));
		Timestamp maintenant = Timestamp.from(clock.instant());
		List<UUID> ids = ordre.stream().map(Ajustement::collaborateurId).toList();

		jdbc.batchUpdate("insert into rh_solde_conge (collaborateur_id, acquis, version, modifie_le) "
				+ "values (?, 0, 0, ?) on conflict do nothing", ids, 1000, (ps, id) -> {
					ps.setObject(1, id);
					ps.setTimestamp(2, maintenant);
				});

		Map<UUID, BigDecimal> etat = verrouiller(ids);

		List<Resultat> resultats = new ArrayList<>();
		List<Object[]> mouvements = new ArrayList<>();
		List<Object[]> majSoldes = new ArrayList<>();
		for (Ajustement a : ordre) {
			BigDecimal actuel = etat.get(a.collaborateurId());
			BigDecimal ecart = a.cible().subtract(actuel).setScale(1);
			if (ecart.signum() == 0) {
				resultats.add(new Resultat(a.collaborateurId(), null, actuel, a.motif()));
				continue;
			}
			BigDecimal apres = actuel.add(ecart).setScale(1);
			if (apres.signum() < 0) {
				throw com.hr.referentiel.web.ReferentielMetierException.unprocessable("SOLDE_NEGATIF",
						"Le solde acquis ne peut pas devenir négatif.");
			}
			UUID mouvementId = UUID.randomUUID();
			mouvements.add(new Object[] {mouvementId, a.collaborateurId(), TypeMouvementSolde.AJUSTEMENT.name(), ecart, apres,
					OrigineMouvementSolde.IMPORT.name(), a.motif(), importId, auteur.compteId(), auteur.nom(), maintenant});
			majSoldes.add(new Object[] {apres, mouvementId, maintenant, a.collaborateurId()});
			resultats.add(new Resultat(a.collaborateurId(), ecart, apres, a.motif()));
		}
		jdbc.batchUpdate("insert into rh_mouvement_solde_conge (id, collaborateur_id, type, nombre, solde_apres, origine, motif, "
				+ "import_id, auteur_compte_id, auteur_nom, cree_le) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", mouvements);
		jdbc.batchUpdate("update rh_solde_conge set acquis = ?, version = version + 1, dernier_mouvement_id = ?, modifie_le = ? "
				+ "where collaborateur_id = ?", majSoldes);
		return resultats;
	}

	/** Verrou par lots d'identifiants triés, lecture fraîche de l'acquis. */
	private Map<UUID, BigDecimal> verrouiller(List<UUID> idsTries) {
		Map<UUID, BigDecimal> acquis = new HashMap<>();
		for (int debut = 0; debut < idsTries.size(); debut += TAILLE_LOT_VERROU) {
			List<UUID> lot = idsTries.subList(debut, Math.min(idsTries.size(), debut + TAILLE_LOT_VERROU));
			String marques = String.join(",", java.util.Collections.nCopies(lot.size(), "?"));
			jdbc.query("select collaborateur_id, acquis from rh_solde_conge where collaborateur_id in (" + marques
					+ ") order by collaborateur_id for update", rs -> {
						acquis.put(rs.getObject(1, UUID.class), rs.getBigDecimal(2));
					}, lot.toArray());
		}
		return acquis;
	}
}
