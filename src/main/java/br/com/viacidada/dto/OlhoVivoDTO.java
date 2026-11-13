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

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LocalResolvido {
        private String termoOriginal;
        private String nomeLocal;
        private String enderecoCompleto;
        private String logradouro;
        private String bairro;
        private String termoBuscaSptrans;
        private List<String> termosCandidatosSptrans;
        private Double latitude;
        private Double longitude;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ParadasProximasGpsResponse {
        private LocalResolvido localAtual;
        private ParadaResumo paradaMaisProxima;
        private Integer distanciaAtePontoMaisProximoMetros;
        private Integer tempoCaminhadaMinutos;
        private Boolean usuarioEstaNoPonto;
        private String mensagemProximidade;
        private List<ParadaResumo> paradasProximas;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PassoRota {
        private Integer ordem;
        private String tipoPasso;
        private String instrucao;
        private String local;
        private Integer distanciaCaminhadaMetros;
        private Integer tempoCaminhadaMinutos;
        private Long codigoParada;
        private String nomeParada;
        private String enderecoParada;
        private Double latitudeParada;
        private Double longitudeParada;
        private Boolean pontoConfirmadoNaParada;
        private Integer codigoLinha;
        private String letreiroCompleto;
        private String descricaoSentido;
        private Integer veiculosCirculando;
        private Integer veiculosAcessiveisPcD;
        private String proximoPrefixo;
        private Double latitudeProximoVeiculo;
        private Double longitudeProximoVeiculo;
        private String previsaoChegada;
        private Integer ocorrenciasAbertas;
        private String alertaComunidade;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OpcaoRota {
        private String titulo;
        private String tipoRota;
        private Boolean recomendada;
        private String motivoRecomendacao;
        private Integer totalOnibusNecessarios;
        private Integer tempoEstimadoMinutos;
        private Boolean totalmenteAcessivelPcD;
        private Integer totalOcorrenciasNoTrajeto;
        private List<PassoRota> passos;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RotaPlanejadaResponse {
        private String origemBuscada;
        private String destinoBuscado;
        private String horarioConsulta;
        private LocalResolvido localOrigemResolvido;
        private LocalResolvido localDestinoResolvido;
        private Integer distanciaCaminhadaOrigemMetros;
        private Integer distanciaCaminhadaDestinoMetros;
        private ParadaResumo paradaOrigemSugerida;
        private ParadaResumo paradaDestinoSugerida;
        private List<ParadaResumo> paradasProximasOrigem;
        private List<OpcaoRota> opcoes;
    }
}
