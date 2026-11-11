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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
                .andExpect(jsonPath("$._links.self.href").exists());
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
}
