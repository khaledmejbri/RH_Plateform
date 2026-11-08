package com.hr.notification.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Compteur des notifications non lues. */
public record CompteurNotifications(@JsonProperty("non_lues") long nonLues) {
}
