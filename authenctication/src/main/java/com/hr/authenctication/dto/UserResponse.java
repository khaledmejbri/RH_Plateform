package com.hr.authenctication.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserResponse {

    @JsonProperty("identifiant")
    private UUID id;

    @JsonProperty("nom_utilisateur")
    private String username;

    @JsonProperty("courriel")
    private String email;

    @JsonProperty("roles")
    private Set<String> roles;

    @JsonProperty("date_creation")
    private Instant createdAt;
}
