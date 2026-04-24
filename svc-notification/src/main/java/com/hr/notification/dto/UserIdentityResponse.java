package com.hr.notification.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserIdentityResponse {
    @JsonProperty("identifiant")
    private UUID id;

    @JsonProperty("nom_utilisateur")
    private String username;
}
