package br.com.viacidada.controller;

import br.com.viacidada.dto.LinhaFavoritaDTO;
import br.com.viacidada.service.LinhaFavoritaService;
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
@RequestMapping("/api/linhas-favoritas")
@RequiredArgsConstructor
@Tag(name = "4. Linhas Favoritas", description = "CRUD de linhas favoritas do cidadao com HATEOAS")
public class LinhaFavoritaController {

    private final LinhaFavoritaService linhaFavoritaService;

    @GetMapping
    @Operation(summary = "Listar linhas favoritas paginadas (opcionalmente filtrando por usuarioId)")
    public ResponseEntity<Page<LinhaFavoritaDTO.Response>> listar(
            @RequestParam(required = false) Long usuarioId,
            @ParameterObject @PageableDefault(size = 10, sort = "id") Pageable pageable
    ) {
        Page<LinhaFavoritaDTO.Response> page = linhaFavoritaService.listar(usuarioId, pageable)
                .map(this::adicionarLinksHateoas);
        return ResponseEntity.ok(page);
    }

    @GetMapping("/busca")
    @Operation(summary = "Buscar linhas favoritas de um usuario por termo (JPQL)")
    public ResponseEntity<List<LinhaFavoritaDTO.Response>> buscarPorTermo(
            @RequestParam Long usuarioId,
            @RequestParam String termo
    ) {
        List<LinhaFavoritaDTO.Response> lista = linhaFavoritaService.buscarPorTermo(usuarioId, termo)
                .stream()
                .map(this::adicionarLinksHateoas)
                .toList();
        return ResponseEntity.ok(lista);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Buscar linha favorita por ID")
    public ResponseEntity<LinhaFavoritaDTO.Response> buscarPorId(@PathVariable Long id) {
        return ResponseEntity.ok(adicionarLinksHateoas(linhaFavoritaService.buscarPorId(id)));
    }

    @PostMapping
    @Operation(summary = "Adicionar nova linha favorita")
    public ResponseEntity<LinhaFavoritaDTO.Response> criar(@Valid @RequestBody LinhaFavoritaDTO.Request request) {
        LinhaFavoritaDTO.Response criado = adicionarLinksHateoas(linhaFavoritaService.criar(request));
        return ResponseEntity.status(HttpStatus.CREATED).body(criado);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Atualizar linha favorita")
    public ResponseEntity<LinhaFavoritaDTO.Response> atualizar(
            @PathVariable Long id,
            @Valid @RequestBody LinhaFavoritaDTO.Request request
    ) {
        return ResponseEntity.ok(adicionarLinksHateoas(linhaFavoritaService.atualizar(id, request)));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Remover linha favorita")
    public ResponseEntity<Void> remover(@PathVariable Long id) {
        linhaFavoritaService.remover(id);
        return ResponseEntity.noContent().build();
    }

    private LinhaFavoritaDTO.Response adicionarLinksHateoas(LinhaFavoritaDTO.Response dto) {
        dto.add(linkTo(methodOn(LinhaFavoritaController.class).buscarPorId(dto.getId())).withSelfRel());
        dto.add(linkTo(methodOn(OlhoVivoController.class).buscarVeiculosPorLinha(dto.getCodigoLinha())).withRel("veiculos-tempo-real"));
        return dto;
    }
}
