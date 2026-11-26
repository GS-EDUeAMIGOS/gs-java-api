package br.com.viacidada.client;

import br.com.viacidada.dto.OlhoVivoDTO;
import br.com.viacidada.exception.ExternalIntegrationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.function.Supplier;

// Cliente HTTP que se conecta na API externa da SPTrans (Olho Vivo) para buscar linhas, paradas e onibus.
@Component
@Slf4j
public class OlhoVivoClient {

    private final RestClient restClient;
    private final String token;
    private volatile String sessionCookie;

    public OlhoVivoClient(
            @Value("${viacidada.sptrans.base-url}") String baseUrl,
            @Value("${viacidada.sptrans.token:}") String token
    ) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(10000);
        requestFactory.setReadTimeout(15000);
        this.restClient = RestClient.builder()
                .requestFactory(requestFactory)
                .baseUrl(baseUrl)
                .build();
        this.token = token != null ? token.trim() : "";
    }

    public synchronized void autenticar() {
        if (token.isEmpty()) {
            throw new ExternalIntegrationException("Variavel de ambiente SPTRANS_TOKEN nao configurada.");
        }

        try {
            ResponseEntity<Boolean> response = restClient.post()
                    .uri(uriBuilder -> uriBuilder
                            .path("/Login/Autenticar")
                            .queryParam("token", token)
                            .build())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new byte[0])
                    .retrieve()
                    .toEntity(Boolean.class);

            Boolean autenticado = response.getBody();
            List<String> cookies = response.getHeaders().get(HttpHeaders.SET_COOKIE);
            if (Boolean.TRUE.equals(autenticado) && cookies != null && !cookies.isEmpty()) {
                this.sessionCookie = String.join("; ", cookies);
                log.info("Autenticado com sucesso na API Olho Vivo SPTrans.");
            } else {
                throw new ExternalIntegrationException("Falha ao autenticar na API Olho Vivo SPTrans: token recusado.");
            }
        } catch (ExternalIntegrationException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ExternalIntegrationException("Erro de comunicacao ao autenticar na SPTrans Olho Vivo: " + ex.getMessage(), ex);
        }
    }

    public List<OlhoVivoDTO.LinhaResumo> buscarLinhas(String termo) {
        return executarComSessao(() -> {
            List<OlhoVivoDTO.LinhaResumo> resultado = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/Linha/Buscar")
                            .queryParam("termosBusca", termo)
                            .build())
                    .header(HttpHeaders.COOKIE, sessionCookie)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});
            return resultado != null ? resultado : List.of();
        }, "Erro ao consultar linhas na API Olho Vivo: ");
    }

    public OlhoVivoDTO.PosicaoLinhaResponse buscarVeiculosPorLinha(Integer codigoLinha) {
        return executarComSessao(() -> restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/Posicao/Linha")
                        .queryParam("codigoLinha", codigoLinha)
                        .build())
                .header(HttpHeaders.COOKIE, sessionCookie)
                .retrieve()
                .body(OlhoVivoDTO.PosicaoLinhaResponse.class),
                "Erro ao consultar posicao dos veiculos na API Olho Vivo: ");
    }

    public OlhoVivoDTO.PosicaoGeralResponse buscarTodasPosicoes() {
        return executarComSessao(() -> restClient.get()
                .uri("/Posicao")
                .header(HttpHeaders.COOKIE, sessionCookie)
                .retrieve()
                .body(OlhoVivoDTO.PosicaoGeralResponse.class),
                "Erro ao consultar radar geral de veiculos na API Olho Vivo: ");
    }

    public List<OlhoVivoDTO.ParadaResumo> buscarParadas(String termo) {
        return executarComSessao(() -> {
            List<OlhoVivoDTO.ParadaResumo> resultado = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/Parada/Buscar")
                            .queryParam("termosBusca", termo)
                            .build())
                    .header(HttpHeaders.COOKIE, sessionCookie)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});
            return resultado != null ? resultado : List.of();
        }, "Erro ao consultar paradas na API Olho Vivo: ");
    }

    public List<OlhoVivoDTO.ParadaResumo> buscarParadasPorLinha(Integer codigoLinha) {
        return executarComSessao(() -> {
            List<OlhoVivoDTO.ParadaResumo> resultado = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/Parada/BuscarParadasPorLinha")
                            .queryParam("codigoLinha", codigoLinha)
                            .build())
                    .header(HttpHeaders.COOKIE, sessionCookie)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});
            return resultado != null ? resultado : List.of();
        }, "Erro ao consultar paradas da linha na API Olho Vivo: ");
    }

    public OlhoVivoDTO.PrevisaoParadaResponse buscarPrevisoesPorParada(Long codigoParada) {
        return executarComSessao(() -> restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/Previsao/Parada")
                        .queryParam("codigoParada", codigoParada)
                        .build())
                .header(HttpHeaders.COOKIE, sessionCookie)
                .retrieve()
                .body(OlhoVivoDTO.PrevisaoParadaResponse.class),
                "Erro ao consultar previsao de parada na API Olho Vivo: ");
    }

    private <T> T executarComSessao(Supplier<T> acao, String prefixoErro) {
        try {
            garantirSessao();
            return acao.get();
        } catch (HttpClientErrorException.Unauthorized | HttpClientErrorException.Forbidden ex) {
            log.info("Sessao SPTrans expirada ({}). Renovando cookie automaticamente...", ex.getStatusCode());
            this.sessionCookie = null;
            try {
                autenticar();
                return acao.get();
            } catch (ExternalIntegrationException extEx) {
                throw extEx;
            } catch (Exception retryEx) {
                throw new ExternalIntegrationException(prefixoErro + retryEx.getMessage(), retryEx);
            }
        } catch (ExternalIntegrationException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ExternalIntegrationException(prefixoErro + ex.getMessage(), ex);
        }
    }

    private void garantirSessao() {
        if (sessionCookie == null) {
            autenticar();
        }
    }
}
