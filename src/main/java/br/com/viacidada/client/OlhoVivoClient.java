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
import org.springframework.web.client.RestClient;

import java.util.List;

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
        try {
            garantirSessao();
            List<OlhoVivoDTO.LinhaResumo> resultado = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/Linha/Buscar")
                            .queryParam("termosBusca", termo)
                            .build())
                    .header(HttpHeaders.COOKIE, sessionCookie)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});
            return resultado != null ? resultado : List.of();
        } catch (ExternalIntegrationException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ExternalIntegrationException("Erro ao consultar linhas na API Olho Vivo: " + ex.getMessage(), ex);
        }
    }

    public OlhoVivoDTO.PosicaoLinhaResponse buscarVeiculosPorLinha(Integer codigoLinha) {
        try {
            garantirSessao();
            return restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/Posicao/Linha")
                            .queryParam("codigoLinha", codigoLinha)
                            .build())
                    .header(HttpHeaders.COOKIE, sessionCookie)
                    .retrieve()
                    .body(OlhoVivoDTO.PosicaoLinhaResponse.class);
        } catch (ExternalIntegrationException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ExternalIntegrationException("Erro ao consultar posicao dos veiculos na API Olho Vivo: " + ex.getMessage(), ex);
        }
    }

    public List<OlhoVivoDTO.ParadaResumo> buscarParadas(String termo) {
        try {
            garantirSessao();
            List<OlhoVivoDTO.ParadaResumo> resultado = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/Parada/Buscar")
                            .queryParam("termosBusca", termo)
                            .build())
                    .header(HttpHeaders.COOKIE, sessionCookie)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});
            return resultado != null ? resultado : List.of();
        } catch (ExternalIntegrationException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ExternalIntegrationException("Erro ao consultar paradas na API Olho Vivo: " + ex.getMessage(), ex);
        }
    }

    public OlhoVivoDTO.PrevisaoParadaResponse buscarPrevisoesPorParada(Long codigoParada) {
        try {
            garantirSessao();
            return restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/Previsao/Parada")
                            .queryParam("codigoParada", codigoParada)
                            .build())
                    .header(HttpHeaders.COOKIE, sessionCookie)
                    .retrieve()
                    .body(OlhoVivoDTO.PrevisaoParadaResponse.class);
        } catch (ExternalIntegrationException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ExternalIntegrationException("Erro ao consultar previsao de parada na API Olho Vivo: " + ex.getMessage(), ex);
        }
    }

    private void garantirSessao() {
        if (sessionCookie == null) {
            autenticar();
        }
    }
}
