package br.com.viacidada.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

public final class AuthDTO {

    private AuthDTO() {
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RegisterRequest {
        @NotBlank(message = "O nome e obrigatorio")
        @Size(min = 2, max = 120, message = "O nome deve ter entre 2 e 120 caracteres")
        private String nome;

        @NotBlank(message = "O e-mail e obrigatorio")
        @Email(message = "Informe um e-mail valido")
        private String email;

        @NotBlank(message = "A senha e obrigatoria")
        @Size(min = 6, max = 100, message = "A senha deve ter no minimo 6 caracteres")
        private String senha;

        private Boolean possuiMobilidadeReduzida;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LoginRequest {
        @NotBlank(message = "O e-mail e obrigatorio")
        @Email(message = "Informe um e-mail valido")
        private String email;

        @NotBlank(message = "A senha e obrigatoria")
        private String senha;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TokenResponse {
        private String token;
        private String tipo;
        private Long expiraEmMs;
        private Long usuarioId;
        private String nome;
        private String email;
        private String role;
    }
}
