package br.com.viacidada.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

public final class OlhoVivoDTO {

    private OlhoVivoDTO() {
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class LinhaResumo {
        @JsonProperty("cl")
        private Integer codigoLinha;

        @JsonProperty("lc")
        private Boolean circular;

        @JsonProperty("lt")
        private String letreiroNumerico;

        @JsonProperty("tl")
        private Integer modoAtendimento;

        @JsonProperty("sl")
        private Integer sentido;

        @JsonProperty("tp")
        private String terminalPrincipal;

        @JsonProperty("ts")
        private String terminalSecundario;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ParadaResumo {
        @JsonProperty("cp")
        private Long codigoParada;

        @JsonProperty("np")
        private String nomeParada;

        @JsonProperty("ed")
        private String endereco;

        @JsonProperty("py")
        private Double latitude;

        @JsonProperty("px")
        private Double longitude;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class VeiculoPosicao {
        @JsonProperty("p")
        private String prefixo;

        @JsonProperty("a")
        private Boolean acessivelPcD;

        @JsonProperty("ta")
        private String horarioCaptura;

        @JsonProperty("py")
        private Double latitude;

        @JsonProperty("px")
        private Double longitude;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PosicaoLinhaResponse {
        @JsonProperty("hr")
        private String horarioReferencia;

        @JsonProperty("vs")
        private List<VeiculoPosicao> veiculos;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class VeiculoPrevisao {
        @JsonProperty("p")
        private String prefixo;

        @JsonProperty("t")
        private String horarioPrevistoChegada;

        @JsonProperty("a")
        private Boolean acessivelPcD;

        @JsonProperty("ta")
        private String horarioCaptura;

        @JsonProperty("py")
        private Double latitude;

        @JsonProperty("px")
        private Double longitude;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class LinhaPrevisao {
        @JsonProperty("c")
        private String letreiroCompleto;

        @JsonProperty("cl")
        private Integer codigoLinha;

        @JsonProperty("sl")
        private Integer sentido;

        @JsonProperty("lt0")
        private String destino;

        @JsonProperty("lt1")
        private String origem;

        @JsonProperty("qv")
        private Integer quantidadeVeiculos;

        @JsonProperty("vs")
        private List<VeiculoPrevisao> veiculos;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ParadaComPrevisoes {
        @JsonProperty("cp")
        private Long codigoParada;

        @JsonProperty("np")
        private String nomeParada;

        @JsonProperty("py")
        private Double latitude;

        @JsonProperty("px")
        private Double longitude;

        @JsonProperty("l")
        private List<LinhaPrevisao> linhas;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PrevisaoParadaResponse {
        @JsonProperty("hr")
        private String horarioReferencia;

        @JsonProperty("p")
        private ParadaComPrevisoes parada;
    }
}
