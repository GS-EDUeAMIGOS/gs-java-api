package br.com.viacidada.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Controller raiz de metadados da API (/api) para verificacao rapida em navegador e clientes HTTP.
 */
@RestController
@Tag(name = "0. Status da API", description = "Informacoes basicas de disponibilidade e atalhos de documentacao")
public class ApiInfoController {

    @GetMapping({"/api", "/api/"})
    @Operation(summary = "Exibir status e endpoints principais da API")
    public ResponseEntity<Map<String, Object>> obterInformacoesApi() {
        String baseHost = ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString();

        Map<String, String> links = new LinkedHashMap<>();
        links.put("swaggerUi", baseHost + "/swagger-ui/index.html");
        links.put("openApi", baseHost + "/v3/api-docs");
        links.put("health", baseHost + "/actuator/health");
        links.put("authLogin", baseHost + "/api/auth/login");
        links.put("ocorrencias", baseHost + "/api/ocorrencias");
        links.put("linhas", baseHost + "/api/linhas");
        links.put("paradas", baseHost + "/api/paradas");
        links.put("rotas", baseHost + "/api/rotas/planejar");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("aplicacao", "ViaCidada API");
        body.put("status", "UP");
        body.put("versao", "1.0.0");
        body.put("baseUrl", baseHost + "/api");
        body.put("links", links);

        return ResponseEntity.ok(body);
    }
}
