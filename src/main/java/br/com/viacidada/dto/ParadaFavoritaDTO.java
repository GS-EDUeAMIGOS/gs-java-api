package br.com.viacidada.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;
import org.springframework.hateoas.RepresentationModel;

import java.time.LocalDateTime;

public final class ParadaFavoritaDTO {

    private ParadaFavoritaDTO() {
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Request {
        @NotNull(message = "O ID do usuario e obrigatorio")
        private Long usuarioId;

        @NotNull(message = "O codigo da parada SPTrans (cp) e obrigatorio")
        private Long codigoParada;

        @NotBlank(message = "O nome da parada e obrigatorio")
        @Size(max = 150)
        private String nomeParada;

        @Size(max = 250)
        private String endereco;

        private Double latitude;
        private Double longitude;
        private Boolean possuiRampaAcessivel;
        private Boolean possuiPisoTatil;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode(callSuper = false)
    public static class Response extends RepresentationModel<Response> {
        private Long id;
        private Long usuarioId;
        private Long codigoParada;
        private String nomeParada;
        private String endereco;
        private Double latitude;
        private Double longitude;
        private Boolean possuiRampaAcessivel;
        private Boolean possuiPisoTatil;
        private LocalDateTime criadoEm;
    }
}
