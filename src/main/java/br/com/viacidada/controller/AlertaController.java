package br.com.viacidada.controller;

import br.com.viacidada.dto.AlertaDTO;
import br.com.viacidada.service.AlertaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

@RestController
@RequestMapping("/api/alertas")
@RequiredArgsConstructor
@Tag(name = "6. Alertas", description = "CRUD de alertas personalizados do cidadao com cache e HATEOAS")
public class AlertaController {

    private final AlertaService alertaService;

    @GetMapping
    @Operation(summary = "Listar alertas paginados")
    public ResponseEntity<Page<AlertaDTO.Response>> listar(
            @RequestParam(required = false) Long usuarioId,
            @ParameterObject @PageableDefault(size = 10, sort = "id") Pageable pageable
    ) {
        Page<AlertaDTO.Response> page = alertaService.listar(usuarioId, pageable)
                .map(this::adicionarLinksHateoas);
        return ResponseEntity.ok(page);
    }

    @GetMapping("/ativos")
    @Operation(summary = "Buscar alertas ativos por linha ou parada (JPQL + @Cacheable)")
    public ResponseEntity<List<AlertaDTO.Response>> buscarAtivos(
            @RequestParam(required = false) Integer codigoLinha,
            @RequestParam(required = false) Long codigoParada
    ) {
        List<AlertaDTO.Response> lista = alertaService.buscarAtivosPorLinhaOuParada(codigoLinha, codigoParada)
                .stream()
                .map(this::adicionarLinksHateoas)
                .toList();
        return ResponseEntity.ok(lista);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Buscar alerta por ID")
    public ResponseEntity<AlertaDTO.Response> buscarPorId(@PathVariable Long id) {
        return ResponseEntity.ok(adicionarLinksHateoas(alertaService.buscarPorId(id)));
    }

    @PostMapping
    @Operation(summary = "Criar novo alerta (@CacheEvict)")
    public ResponseEntity<AlertaDTO.Response> criar(@Valid @RequestBody AlertaDTO.Request request) {
        AlertaDTO.Response criado = adicionarLinksHateoas(alertaService.criar(request));
        return ResponseEntity.status(HttpStatus.CREATED).body(criado);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Atualizar alerta existente (@CacheEvict)")
    public ResponseEntity<AlertaDTO.Response> atualizar(
            @PathVariable Long id,
            @Valid @RequestBody AlertaDTO.Request request
    ) {
        return ResponseEntity.ok(adicionarLinksHateoas(alertaService.atualizar(id, request)));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Remover alerta (@CacheEvict)")
    public ResponseEntity<Void> remover(@PathVariable Long id) {
        alertaService.remover(id);
        return ResponseEntity.noContent().build();
    }

    private AlertaDTO.Response adicionarLinksHateoas(AlertaDTO.Response dto) {
        dto.add(linkTo(methodOn(AlertaController.class).buscarPorId(dto.getId())).withSelfRel());
        dto.add(linkTo(methodOn(AlertaController.class).listar(dto.getUsuarioId(), Pageable.unpaged())).withRel("alertas-usuario"));
        return dto;
    }
}
