package com.hr.presence.service;

import com.hr.presence.config.PresenceProperties;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

/**
 * QR dynamique de borne écran (D-P05) : {@code d1.<siteId>.<borneVersion>.<fenetre>.<hmac>},
 * fenêtre de 30 s, accepté sur la fenêtre courante ± 1. Une photo du QR devient inutile en moins d'une minute.
 */
@Service
public class QrDynamiqueService {

	public static final String PREFIXE = "d1";
	public static final long FENETRE_SECONDES = 30;
	/** Fenêtres de tolérance de part et d'autre (horloges, délai de scan). */
	public static final long TOLERANCE_FENETRES = 1;

	public enum Verdict { VALIDE, EXPIRE, INVALIDE }

	public record Resultat(Verdict verdict, UUID siteId, int borneVersion) {
	}

	private final PresenceProperties properties;
	private final Base64.Encoder urlEncoder = Base64.getUrlEncoder().withoutPadding();

	public QrDynamiqueService(PresenceProperties properties) {
		this.properties = properties;
	}

	public static boolean estDynamique(String token) {
		return token != null && token.trim().startsWith(PREFIXE + ".");
	}

	public static long fenetre(Instant instant) {
		return Math.floorDiv(instant.getEpochSecond(), FENETRE_SECONDES);
	}

	/** Secondes restantes avant le prochain renouvellement. */
	public static long secondesRestantes(Instant instant) {
		return FENETRE_SECONDES - Math.floorMod(instant.getEpochSecond(), FENETRE_SECONDES);
	}

	public String construire(UUID siteId, int borneVersion, long fenetre) {
		String corps = PREFIXE + "." + siteId + "." + borneVersion + "." + fenetre;
		return corps + "." + signer(corps);
	}

	public Resultat verifier(String token, Instant maintenant) {
		Optional<String[]> parts = decouper(token);
		if (parts.isEmpty()) {
			return new Resultat(Verdict.INVALIDE, null, 0);
		}
		String[] p = parts.get();
		String corps = p[0] + "." + p[1] + "." + p[2] + "." + p[3];
		if (!MessageDigest.isEqual(signer(corps).getBytes(StandardCharsets.UTF_8), p[4].getBytes(StandardCharsets.UTF_8))) {
			return new Resultat(Verdict.INVALIDE, null, 0);
		}
		UUID siteId;
		int version;
		long fen;
		try {
			siteId = UUID.fromString(p[1]);
			version = Integer.parseInt(p[2]);
			fen = Long.parseLong(p[3]);
		} catch (IllegalArgumentException e) {
			return new Resultat(Verdict.INVALIDE, null, 0);
		}
		long ecart = Math.abs(fenetre(maintenant) - fen);
		return new Resultat(ecart <= TOLERANCE_FENETRES ? Verdict.VALIDE : Verdict.EXPIRE, siteId, version);
	}

	/** Empreinte stockée du jeton de borne (le jeton clair n'est montré qu'une fois au RH). */
	public static String empreinte(String jetonBorne) {
		try {
			byte[] h = MessageDigest.getInstance("SHA-256").digest(jetonBorne.getBytes(StandardCharsets.UTF_8));
			StringBuilder sb = new StringBuilder();
			for (byte b : h) {
				sb.append(String.format("%02x", b));
			}
			return sb.toString();
		} catch (Exception e) {
			throw new IllegalStateException("SHA-256 indisponible", e);
		}
	}

	private static Optional<String[]> decouper(String token) {
		if (!estDynamique(token)) {
			return Optional.empty();
		}
		String[] p = token.trim().split("\\.");
		return p.length == 5 ? Optional.of(p) : Optional.empty();
	}

	private String signer(String corps) {
		try {
			Mac mac = Mac.getInstance("HmacSHA256");
			// Séparation de domaine : la même clé ne doit pas signer un QR papier et un QR dynamique
			String secret = properties.getQr().getHmacSecret() + "|qr-dynamique";
			mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
			return urlEncoder.encodeToString(mac.doFinal(corps.getBytes(StandardCharsets.UTF_8)));
		} catch (Exception e) {
			throw new IllegalStateException("Erreur crypto HMAC QR dynamique", e);
		}
	}
}
