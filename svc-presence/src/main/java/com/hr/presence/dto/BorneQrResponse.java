package com.hr.presence.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/** QR courant d'une borne écran (D-P05). */
public record BorneQrResponse(
		@JsonProperty("site_libelle") String siteLibelle,
		@JsonProperty("qr_token") String qrToken,
		@JsonProperty("qr_png_base64") String qrPngBase64,
		@JsonProperty("expire_dans_secondes") long expireDansSecondes,
		@JsonProperty("periode_secondes") long periodeSecondes
) {
}
