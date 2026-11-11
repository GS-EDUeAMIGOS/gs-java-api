package br.com.viacidada.controller;

import br.com.viacidada.dto.ParadaFavoritaDTO;
import br.com.viacidada.service.ParadaFavoritaService;
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
@RequestMapping("/api/paradas-favoritas")
@RequiredArgsConstructor
@Tag(name = "5. Paradas Favoritas", description = "CRUD de paradas favoritas com informacoes de acessibilidade e HATEOAS")
public class ParadaFavoritaController {

    private final ParadaFavoritaService paradaFavoritaService;

    @GetMapping
    @Operation(summary = "Listar paradas favoritas paginadas")
    public ResponseEntity<Page<ParadaFavoritaDTO.Response>> listar(
            @RequestParam(required = false) Long usuarioId,
            @ParameterObject @PageableDefault(size = 10, sort = "id") Pageable pageable
    ) {
        Page<ParadaFavoritaDTO.Response> page = paradaFavoritaService.listar(usuarioId, pageable)
                .map(this::adicionarLinksHateoas);
        return ResponseEntity.ok(page);
    }

    @GetMapping("/acessiveis")
    @Operation(summary = "Listar paradas favoritas do usuario que possuem rampa ou piso tatil (JPQL)")
    public ResponseEntity<List<ParadaFavoritaDTO.Response>> listarAcessiveis(@RequestParam Long usuarioId) {
        List<ParadaFavoritaDTO.Response> lista = paradaFavoritaService.listarAcessiveisDoUsuario(usuarioId)
                .stream()
                .map(this::adicionarLinksHateoas)
                .toList();
        return ResponseEntity.ok(lista);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Buscar parada favorita por ID")
    public ResponseEntity<ParadaFavoritaDTO.Response> buscarPorId(@PathVariable Long id) {
        return ResponseEntity.ok(adicionarLinksHateoas(paradaFavoritaService.buscarPorId(id)));
    }

    @PostMapping
    @Operation(summary = "Adicionar nova parada favorita")
    public ResponseEntity<ParadaFavoritaDTO.Response> criar(@Valid @RequestBody ParadaFavoritaDTO.Request request) {
        ParadaFavoritaDTO.Response criado = adicionarLinksHateoas(paradaFavoritaService.criar(request));
        return ResponseEntity.status(HttpStatus.CREATED).body(criado);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Atualizar parada favorita")
    public ResponseEntity<ParadaFavoritaDTO.Response> atualizar(
            @PathVariable Long id,
            @Valid @RequestBody ParadaFavoritaDTO.Request request
    ) {
        return ResponseEntity.ok(adicionarLinksHateoas(paradaFavoritaService.atualizar(id, request)));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Remover parada favorita")
    public ResponseEntity<Void> remover(@PathVariable Long id) {
        paradaFavoritaService.remover(id);
        return ResponseEntity.noContent().build();
    }

    private ParadaFavoritaDTO.Response adicionarLinksHateoas(ParadaFavoritaDTO.Response dto) {
        dto.add(linkTo(methodOn(ParadaFavoritaController.class).buscarPorId(dto.getId())).withSelfRel());
        dto.add(linkTo(methodOn(OlhoVivoController.class).buscarPrevisoesPorParada(dto.getCodigoParada())).withRel("previsoes-parada"));
        return dto;
    }
}
