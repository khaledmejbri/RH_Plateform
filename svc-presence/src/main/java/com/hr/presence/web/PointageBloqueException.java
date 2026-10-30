package com.hr.presence.web;

import java.time.Instant;

/** 409 POINTAGE_BLOQUE_VISAGE : blocage de 15 min après 3 échecs faciaux consécutifs (D-P10.1). */
public class PointageBloqueException extends PresenceMetierException {

	public static final String CODE = "POINTAGE_BLOQUE_VISAGE";

	private final Instant bloqueJusqua;

	public PointageBloqueException(String message, Instant bloqueJusqua) {
		super(CODE, message, 409);
		this.bloqueJusqua = bloqueJusqua;
	}

	public Instant getBloqueJusqua() {
		return bloqueJusqua;
	}
}
