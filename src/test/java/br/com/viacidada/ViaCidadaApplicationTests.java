package br.com.viacidada;

import br.com.viacidada.dto.AuthDTO;
import br.com.viacidada.dto.OcorrenciaDTO;
import br.com.viacidada.entity.NivelGravidade;
import br.com.viacidada.entity.TipoOcorrencia;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ViaCidadaApplicationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("Deve exigir token real da SPTrans quando nao configurado (sem mock)")
    void deveRetornarErroQuandoTokenSptransNaoConfigurado() throws Exception {
        mockMvc.perform(get("/api/linhas").param("termo", "8000"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.message").value("Variavel de ambiente SPTRANS_TOKEN nao configurada."));
    }

    @Test
    @DisplayName("Deve autenticar usuario de teste, obter JWT e criar ocorrencia cidada com HATEOAS")
    void deveAutenticarECriarOcorrenciaComJwt() throws Exception {
        AuthDTO.LoginRequest loginRequest = AuthDTO.LoginRequest.builder()
                .email("cidadao@viacidada.com.br")
                .senha("Cidadao@123")
                .build();

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andReturn();

        JsonNode jsonNode = objectMapper.readTree(loginResult.getResponse().getContentAsString());
        String token = jsonNode.get("token").asText();
        Long usuarioId = jsonNode.get("usuarioId").asLong();

        OcorrenciaDTO.Request novaOcorrencia = OcorrenciaDTO.Request.builder()
                .usuarioId(usuarioId)
                .titulo("Rampa de acesso com defeito")
                .descricao("O onibus nao conseguiu baixar a rampa na parada MASP.")
                .tipo(TipoOcorrencia.ACESSIBILIDADE)
                .gravidade(NivelGravidade.ALTA)
                .codigoLinha(2506)
                .codigoParada(340015330L)
                .afetaAcessibilidade(true)
                .build();

        mockMvc.perform(post("/api/ocorrencias")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(novaOcorrencia)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.titulo").value("Rampa de acesso com defeito"))
                .andExpect(jsonPath("$._links.self.href").exists())
                .andExpect(jsonPath("$._links.atualizar.href").exists())
                .andExpect(jsonPath("$._links.remover.href").exists());
    }

    @Test
    @DisplayName("Deve restringir rotas administrativas para ROLE_ADMIN e negar ROLE_CIDADAO com 403")
    void deveProtegerRotasAdminPorPapel() throws Exception {
        String tokenCidadao = autenticarEObterToken("cidadao@viacidada.com.br", "Cidadao@123");
        String tokenAdmin = autenticarEObterToken("admin@viacidada.com.br", "Admin@123");

        mockMvc.perform(get("/api/usuarios")
                        .header("Authorization", "Bearer " + tokenCidadao))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/sptrans/cache")
                        .header("Authorization", "Bearer " + tokenCidadao))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/usuarios")
                        .header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());

        mockMvc.perform(delete("/api/sptrans/cache")
                        .header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("Deve expor endpoints do Actuator (health e info)")
    void deveExporActuatorHealthEInfo() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));

        mockMvc.perform(get("/actuator/info"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.app.name").value("ViaCidada API"));
    }

    private String autenticarEObterToken(String email, String senha) throws Exception {
        AuthDTO.LoginRequest loginRequest = AuthDTO.LoginRequest.builder()
                .email(email)
                .senha(senha)
                .build();

        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString()).get("token").asText();
    }

    @Test
    @DisplayName("Deve filtrar ocorrencias dinamicamente via JPA Specification")
    void deveFiltrarOcorrenciasPorSpecification() throws Exception {
        mockMvc.perform(get("/api/ocorrencias")
                        .param("afetaAcessibilidade", "true")
                        .param("termo", "elevador"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].tipo").value("ELEVADOR_INOPERANTE"));
    }

    @Test
    @DisplayName("Deve expor endpoint publico de planejamento de rotas estilo Moovit (/api/rotas/planejar)")
    void deveExporEndpointPlanejarRotaPublicamente() throws Exception {
        mockMvc.perform(get("/api/rotas/planejar")
                        .param("origem", "LAPA")
                        .param("destino", "PAULISTA"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.message").value("Variavel de ambiente SPTRANS_TOKEN nao configurada."));
    }

    @Test
    @DisplayName("Deve responder 200 OK em /api respeitando headers de proxy reverso (Azure HTTPS e localhost)")
    void deveExporRaizDaApiComLinks() throws Exception {
        mockMvc.perform(get("/api")
                        .header("X-Forwarded-Proto", "https")
                        .header("X-Forwarded-Host", "viacidada-3a46802b7054.eastus.cloudapp.azure.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aplicacao").value("ViaCidada API"))
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.baseUrl").value("https://viacidada-3a46802b7054.eastus.cloudapp.azure.com/api"))
                .andExpect(jsonPath("$.links.swaggerUi").value("https://viacidada-3a46802b7054.eastus.cloudapp.azure.com/swagger-ui/index.html"));
    }

    @Test
    @DisplayName("Deve permitir preflight CORS para origem localhost e origem Azure")
    void devePermitirCorsParaLocalhostEAzure() throws Exception {
        mockMvc.perform(options("/api/auth/login")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "Content-Type,Authorization"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));

        mockMvc.perform(options("/api/auth/login")
                        .header("Origin", "https://viacidada-3a46802b7054.eastus.cloudapp.azure.com")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "Content-Type,Authorization"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "https://viacidada-3a46802b7054.eastus.cloudapp.azure.com"));
    }

    @Test
    @DisplayName("Deve expor servidores Azure e localhost na especificacao OpenAPI")
    void deveExporServidoresAzureELocalhostNoOpenApi() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("https://viacidada-3a46802b7054.eastus.cloudapp.azure.com")))
                .andExpect(content().string(containsString("http://localhost:8080")));
    }
}
