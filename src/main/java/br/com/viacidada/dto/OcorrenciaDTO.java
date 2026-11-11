package br.com.viacidada.dto;

import br.com.viacidada.entity.NivelGravidade;
import br.com.viacidada.entity.StatusOcorrencia;
import br.com.viacidada.entity.TipoOcorrencia;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;
import org.springframework.hateoas.RepresentationModel;

import java.time.LocalDateTime;

public final class OcorrenciaDTO {

    private OcorrenciaDTO() {
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Request {
        @NotNull(message = "O ID do usuario e obrigatorio")
        private Long usuarioId;

        @NotBlank(message = "O titulo da ocorrencia e obrigatorio")
        @Size(min = 3, max = 120)
        private String titulo;

        @NotBlank(message = "A descricao da ocorrencia e obrigatoria")
        @Size(min = 5, max = 1000)
        private String descricao;

        @NotNull(message = "O tipo da ocorrencia e obrigatorio")
        private TipoOcorrencia tipo;

        private StatusOcorrencia status;
        private NivelGravidade gravidade;
        private Integer codigoLinha;
        private Long codigoParada;

        @Size(max = 30)
        private String prefixoVeiculo;

        private Boolean afetaAcessibilidade;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode(callSuper = false)
    public static class Response extends RepresentationModel<Response> {
        private Long id;
        private Long usuarioId;
        private String nomeUsuario;
        private String titulo;
        private String descricao;
        private TipoOcorrencia tipo;
        private StatusOcorrencia status;
        private NivelGravidade gravidade;
        private Integer codigoLinha;
        private Long codigoParada;
        private String prefixoVeiculo;
        private Boolean afetaAcessibilidade;
        private LocalDateTime criadoEm;
        private LocalDateTime atualizadoEm;
    }
}
