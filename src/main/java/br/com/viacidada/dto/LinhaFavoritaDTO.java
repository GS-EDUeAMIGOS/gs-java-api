package br.com.viacidada.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;
import org.springframework.hateoas.RepresentationModel;

import java.time.LocalDateTime;

public final class LinhaFavoritaDTO {

    private LinhaFavoritaDTO() {
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Request {
        @NotNull(message = "O ID do usuario e obrigatorio")
        private Long usuarioId;

        @NotNull(message = "O codigo da linha SPTrans (cl) e obrigatorio")
        private Integer codigoLinha;

        @NotBlank(message = "O letreiro da linha e obrigatorio")
        @Size(max = 30, message = "O letreiro deve ter no maximo 30 caracteres")
        private String letreiro;

        @NotNull(message = "O sentido da linha (1 ou 2) e obrigatorio")
        private Integer sentido;

        @Size(max = 120)
        private String terminalOrigem;

        @Size(max = 120)
        private String terminalDestino;

        @Size(max = 100)
        private String apelido;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode(callSuper = false)
    public static class Response extends RepresentationModel<Response> {
        private Long id;
        private Long usuarioId;
        private Integer codigoLinha;
        private String letreiro;
        private Integer sentido;
        private String terminalOrigem;
        private String terminalDestino;
        private String apelido;
        private LocalDateTime criadoEm;
    }
}
