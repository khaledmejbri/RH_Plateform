package com.hr.identiteacces.service;

import com.hr.identiteacces.config.SocieteProprietes;
import com.hr.identiteacces.domain.StatutSociete;
import com.hr.identiteacces.dto.SocieteDtos.AdminInitialRequest;
import com.hr.identiteacces.dto.SocieteDtos.CreerSocieteRequest;
import com.hr.identiteacces.dto.SocieteDtos.ModifierSocieteRequest;
import com.hr.identiteacces.dto.SocieteDtos.MotifSocieteRequest;
import com.hr.identiteacces.dto.SocieteDtos.PageResponse;
import com.hr.identiteacces.dto.SocieteDtos.SocieteResponse;
import com.hr.identiteacces.dto.UserResponse;
import com.hr.identiteacces.entity.Societe;
import com.hr.identiteacces.entity.User;
import com.hr.identiteacces.kafka.SocieteEvenementMessage;
import com.hr.identiteacces.kafka.SocieteEvenementPublisher;
import com.hr.identiteacces.repository.SocieteRepository;
import com.hr.identiteacces.repository.UserRepository;
import com.hr.identiteacces.security.ApplicationRoleMatrix;
import com.hr.identiteacces.web.IdentiteMetierException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class SocieteService {

	private static final Pattern CODE_SOCIETE_PATTERN = Pattern.compile("^[A-Za-z0-9-]{2,12}$");
	private static final Pattern COURRIEL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

	private final SocieteRepository societeRepository;
	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;
	private final GenerateurMotDePasse generateurMotDePasse;
	private final CollaborateurWelcomeMailService collaborateurWelcomeMailService;
	private final SocieteEvenementPublisher societeEvenementPublisher;

	@Transactional(readOnly = true)
	public PageResponse<SocieteResponse> listerPagines(int page, int taille) {
		if (page < 0 || taille < 1 || taille > 100) {
			throw IdentiteMetierException.badRequest("CORPS_INVALIDE", "Les paramètres de pagination sont invalides.");
		}
		Page<Societe> resultat = societeRepository.findAllByOrderByCreeLeDesc(PageRequest.of(page, taille));
		List<SocieteResponse> elements = resultat.getContent().stream()
				.map(SocieteResponse::from)
				.toList();
		return new PageResponse<>(elements, page, taille, resultat.getTotalElements());
	}

	@Transactional
	public SocieteResponse creer(CreerSocieteRequest req) {
		if (req == null) {
			throw IdentiteMetierException.badRequest("CORPS_INVALIDE", "Le corps de la requête est obligatoire.");
		}
		String codeBrut = req.code() != null ? req.code().trim() : "";
		if (!CODE_SOCIETE_PATTERN.matcher(codeBrut).matches()) {
			throw IdentiteMetierException.badRequest("CORPS_INVALIDE",
					"Le code société doit contenir de 2 à 12 caractères (A-Z, 0-9, -).");
		}
		String raisonSociale = validerRaisonSociale(req.raisonSociale());
		String fuseau = validerFuseau(req.fuseau());
		String codeNormalise = codeBrut.toUpperCase(Locale.ROOT);

		if (AuthService.CODE_RESERVE_PLATEFORME.equalsIgnoreCase(codeNormalise)
				|| societeRepository.existsByCodeIgnoreCase(codeNormalise)) {
			throw IdentiteMetierException.conflict("SOCIETE_CODE_EXISTANT",
					"Une société porte déjà ce code.");
		}

		Instant now = Instant.now();
		Societe societe = Societe.builder()
				.code(codeNormalise)
				.raisonSociale(raisonSociale)
				.statut(StatutSociete.ACTIVE)
				.fuseau(fuseau)
				.creeLe(now)
				.modifieLe(now)
				.build();
		societe = societeRepository.saveAndFlush(societe);
		societeEvenementPublisher.publierApresCommit(
				SocieteEvenementMessage.SOCIETE_CREEE, societe.getId(), societe.getCode());
		return SocieteResponse.from(societe);
	}

	@Transactional
	public SocieteResponse modifier(UUID id, ModifierSocieteRequest req) {
		if (req == null || req.version() == null) {
			throw IdentiteMetierException.badRequest("CORPS_INVALIDE",
					"La raison sociale et la version sont obligatoires.");
		}
		String raisonSociale = validerRaisonSociale(req.raisonSociale());
		String fuseau = validerFuseau(req.fuseau());

		Societe societe = societeRepository.findById(id)
				.orElseThrow(() -> IdentiteMetierException.notFound("SOCIETE_INTROUVABLE", "Société introuvable."));

		if (!Objects.equals(societe.getVersion(), req.version())) {
			throw IdentiteMetierException.conflict("MODIFIE_ENTRE_TEMPS",
					"La société a été modifiée entre-temps. Rechargez puis recommencez.");
		}

		societe.setRaisonSociale(raisonSociale);
		societe.setFuseau(fuseau);
		societe.setModifieLe(Instant.now());
		societe = societeRepository.saveAndFlush(societe);
		return SocieteResponse.from(societe);
	}

	@Transactional
	public SocieteResponse suspendre(UUID id, MotifSocieteRequest req) {
		validerMotif(req);
		Societe societe = societeRepository.findById(id)
				.orElseThrow(() -> IdentiteMetierException.notFound("SOCIETE_INTROUVABLE", "Société introuvable."));
		if (societe.getStatut() == StatutSociete.SUSPENDUE) {
			throw IdentiteMetierException.conflict("ETAT_INCORRECT", "La société est déjà suspendue.");
		}
		societe.setStatut(StatutSociete.SUSPENDUE);
		societe.setModifieLe(Instant.now());
		societe = societeRepository.saveAndFlush(societe);
		societeEvenementPublisher.publierApresCommit(
				SocieteEvenementMessage.SOCIETE_SUSPENDUE, societe.getId(), societe.getCode());
		return SocieteResponse.from(societe);
	}

	@Transactional
	public SocieteResponse reactiver(UUID id, MotifSocieteRequest req) {
		validerMotif(req);
		Societe societe = societeRepository.findById(id)
				.orElseThrow(() -> IdentiteMetierException.notFound("SOCIETE_INTROUVABLE", "Société introuvable."));
		if (societe.getStatut() == StatutSociete.ACTIVE) {
			throw IdentiteMetierException.conflict("ETAT_INCORRECT", "La société est déjà active.");
		}
		societe.setStatut(StatutSociete.ACTIVE);
		societe.setModifieLe(Instant.now());
		societe = societeRepository.saveAndFlush(societe);
		societeEvenementPublisher.publierApresCommit(
				SocieteEvenementMessage.SOCIETE_REACTIVEE, societe.getId(), societe.getCode());
		return SocieteResponse.from(societe);
	}

	@Transactional
	public UserResponse creerAdminInitial(UUID societeId, AdminInitialRequest req) {
		if (req == null) {
			throw IdentiteMetierException.badRequest("CORPS_INVALIDE", "Le corps de la requête est obligatoire.");
		}
		String matricule = req.matricule() != null ? req.matricule().trim() : "";
		String prenom = req.prenom() != null ? req.prenom().trim() : "";
		String nom = req.nom() != null ? req.nom().trim() : "";
		String courriel = req.courriel() != null ? req.courriel().trim() : "";

		if (matricule.isEmpty() || matricule.length() > 100
				|| prenom.isEmpty() || prenom.length() > 120
				|| nom.isEmpty() || nom.length() > 120
				|| courriel.isEmpty() || courriel.length() > 255
				|| !COURRIEL_PATTERN.matcher(courriel).matches()) {
			throw IdentiteMetierException.badRequest("CORPS_INVALIDE",
					"Matricule, prénom, nom et courriel valide sont obligatoires.");
		}

		// Verrou pessimiste sur la ligne societe (amélioration 1) contre deux appels simultanés
		Societe societe = societeRepository.findByIdPourMiseAJour(societeId)
				.or(() -> societeRepository.findById(societeId))
				.orElseThrow(() -> IdentiteMetierException.notFound("SOCIETE_INTROUVABLE", "Société introuvable."));

		if (userRepository.existsBySocieteIdAndRole(societe.getId(), "ADMIN")) {
			throw IdentiteMetierException.conflict("ADMIN_INITIAL_EXISTANT",
					"Le compte administrateur initial a déjà été créé pour cette société.");
		}
		if (userRepository.existsByEmailIgnoreCase(courriel)) {
			throw IdentiteMetierException.conflict("COURRIEL_DEJA_UTILISE",
					"Ce courriel est déjà utilisé par un autre compte.");
		}

		String motDePasse = generateurMotDePasse.generer();
		User admin = User.builder()
				.societeId(societe.getId())
				.username(matricule)
				.email(courriel)
				.password(passwordEncoder.encode(motDePasse))
				.roles(new HashSet<>(ApplicationRoleMatrix.rolesPourProfil("ADMIN")))
				.build();
		admin = userRepository.saveAndFlush(admin);

		planifierCourrielBienvenueApresCommit(courriel, prenom, nom, matricule, motDePasse);

		return UserResponse.builder()
				.id(admin.getId())
				.username(admin.getUsername())
				.email(admin.getEmail())
				.roles(admin.getRoles())
				.createdAt(admin.getCreatedAt())
				.build();
	}

	private void planifierCourrielBienvenueApresCommit(
			String courriel, String prenom, String nom, String matricule, String motDePasse) {
		if (TransactionSynchronizationManager.isSynchronizationActive()) {
			TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
				@Override
				public void afterCommit() {
					collaborateurWelcomeMailService.scheduleWelcomeEmail(courriel, prenom, nom, matricule, motDePasse);
				}
			});
			return;
		}
		collaborateurWelcomeMailService.scheduleWelcomeEmail(courriel, prenom, nom, matricule, motDePasse);
	}

	@Transactional(readOnly = true)
	public List<SocieteResponse> listerInterne(String statutRaw) {
		if (statutRaw == null || statutRaw.isBlank()) {
			return societeRepository.findAllByOrderByCodeAsc().stream()
					.map(SocieteResponse::from)
					.toList();
		}
		StatutSociete statut;
		try {
			statut = StatutSociete.valueOf(statutRaw.trim().toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException e) {
			throw IdentiteMetierException.badRequest("CORPS_INVALIDE", "Statut de société non reconnu.");
		}
		return societeRepository.findByStatutOrderByCodeAsc(statut).stream()
				.map(SocieteResponse::from)
				.toList();
	}

	@Transactional(readOnly = true)
	public SocieteResponse trouverParCode(String code) {
		if (code == null || code.isBlank()) {
			throw IdentiteMetierException.notFound("SOCIETE_INTROUVABLE", "Société introuvable.");
		}
		return societeRepository.findByCodeIgnoreCase(code.trim())
				.map(SocieteResponse::from)
				.orElseThrow(() -> IdentiteMetierException.notFound("SOCIETE_INTROUVABLE", "Société introuvable."));
	}

	@Transactional(readOnly = true)
	public SocieteResponse trouverParId(UUID id) {
		return societeRepository.findById(id)
				.map(SocieteResponse::from)
				.orElseThrow(() -> IdentiteMetierException.notFound("SOCIETE_INTROUVABLE", "Société introuvable."));
	}

	private static String validerRaisonSociale(String raisonSociale) {
		String rs = raisonSociale != null ? raisonSociale.trim() : "";
		if (rs.length() < 2 || rs.length() > 120) {
			throw IdentiteMetierException.badRequest("CORPS_INVALIDE",
					"La raison sociale doit contenir de 2 à 120 caractères.");
		}
		return rs;
	}

	private static String validerFuseau(String fuseau) {
		if (fuseau == null || fuseau.isBlank()) {
			return SocieteProprietes.FUSEAU_DEFAUT;
		}
		String f = fuseau.trim();
		if (f.length() > 40) {
			throw IdentiteMetierException.badRequest("CORPS_INVALIDE",
					"Le fuseau horaire ne doit pas dépasser 40 caractères.");
		}
		try {
			ZoneId.of(f);
			return f;
		} catch (DateTimeException e) {
			throw IdentiteMetierException.badRequest("CORPS_INVALIDE", "Fuseau horaire IANA invalide.");
		}
	}

	private static void validerMotif(MotifSocieteRequest req) {
		if (req == null || req.motif() == null || req.motif().trim().isEmpty()) {
			throw IdentiteMetierException.badRequest("CORPS_INVALIDE", "Le motif est obligatoire.");
		}
	}
}
