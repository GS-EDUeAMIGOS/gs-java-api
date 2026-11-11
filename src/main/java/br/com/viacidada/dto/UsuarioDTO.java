package br.com.viacidada.dto;

import br.com.viacidada.entity.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;
import org.springframework.hateoas.RepresentationModel;

import java.time.LocalDateTime;

public final class UsuarioDTO {

    private UsuarioDTO() {
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Request {
        @NotBlank(message = "O nome e obrigatorio")
        @Size(min = 2, max = 120, message = "O nome deve ter entre 2 e 120 caracteres")
        private String nome;

        @NotBlank(message = "O e-mail e obrigatorio")
        @Email(message = "Informe um e-mail valido")
        private String email;

        @Size(min = 6, max = 100, message = "A senha deve ter no minimo 6 caracteres")
        private String senha;

        private Role role;
        private Boolean possuiMobilidadeReduzida;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode(callSuper = false)
    public static class Response extends RepresentationModel<Response> {
        private Long id;
        private String nome;
        private String email;
        private Role role;
        private Boolean possuiMobilidadeReduzida;
        private LocalDateTime criadoEm;
    }
}
