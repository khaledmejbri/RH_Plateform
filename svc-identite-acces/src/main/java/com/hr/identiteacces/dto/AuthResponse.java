package com.hr.identiteacces.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuthResponse {

    @JsonProperty("jeton_acces")
    private String accessToken;

    @JsonProperty("jeton_rafraichissement")
    private String refreshToken;

    @JsonProperty("type_jeton")
    private String tokenType;

    @JsonProperty("expire_dans")
    private long expiresIn;

    @JsonProperty("utilisateur")
    private UserResponse user;
}
