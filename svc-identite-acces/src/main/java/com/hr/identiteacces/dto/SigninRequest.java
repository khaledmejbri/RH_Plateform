package com.hr.identiteacces.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SigninRequest {

    @NotBlank(message = "Le nom d'utilisateur est obligatoire")
    @JsonProperty("nom_utilisateur")
    private String username;

    @NotBlank(message = "Le mot de passe est obligatoire")
    @JsonProperty("mot_de_passe")
    private String password;
}
