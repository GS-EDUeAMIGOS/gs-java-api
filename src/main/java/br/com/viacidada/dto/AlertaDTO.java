package br.com.viacidada.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;
import org.springframework.hateoas.RepresentationModel;

import java.time.LocalDateTime;

public final class AlertaDTO {

    private AlertaDTO() {
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Request {
        @NotNull(message = "O ID do usuario e obrigatorio")
        private Long usuarioId;

        @NotBlank(message = "O titulo do alerta e obrigatorio")
        @Size(max = 120)
        private String titulo;

        @NotBlank(message = "A mensagem do alerta e obrigatoria")
        @Size(max = 500)
        private String mensagem;

        private Integer codigoLinha;
        private Long codigoParada;
        private Boolean somenteVeiculoAcessivel;

        @Size(max = 20)
        private String horarioNotificacao;

        private Boolean ativo;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode(callSuper = false)
    public static class Response extends RepresentationModel<Response> {
        private Long id;
        private Long usuarioId;
        private String titulo;
        private String mensagem;
        private Integer codigoLinha;
        private Long codigoParada;
        private Boolean somenteVeiculoAcessivel;
        private String horarioNotificacao;
        private Boolean ativo;
        private LocalDateTime criadoEm;
    }
}
