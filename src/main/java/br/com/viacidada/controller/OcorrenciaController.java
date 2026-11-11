package br.com.viacidada.controller;

import br.com.viacidada.dto.OcorrenciaDTO;
import br.com.viacidada.entity.NivelGravidade;
import br.com.viacidada.entity.StatusOcorrencia;
import br.com.viacidada.entity.TipoOcorrencia;
import br.com.viacidada.service.OcorrenciaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

@RestController
@RequestMapping("/api/ocorrencias")
@RequiredArgsConstructor
@Tag(name = "7. Ocorrencias Cidadas", description = "Participacao cidada com filtros dinamicos (Specification), paginacao, cache e HATEOAS")
public class OcorrenciaController {

    private final OcorrenciaService ocorrenciaService;

    @GetMapping
    @Operation(summary = "Listar ocorrencias com filtros dinamicos (JPA Specification), paginacao e ordenacao")
    public ResponseEntity<Page<OcorrenciaDTO.Response>> listarComFiltros(
            @RequestParam(required = false) TipoOcorrencia tipo,
            @RequestParam(required = false) StatusOcorrencia status,
            @RequestParam(required = false) NivelGravidade gravidade,
            @RequestParam(required = false) Integer codigoLinha,
            @RequestParam(required = false) Long codigoParada,
            @RequestParam(required = false) Boolean afetaAcessibilidade,
            @RequestParam(required = false) String termo,
            @ParameterObject @PageableDefault(size = 10, sort = "criadoEm", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        Page<OcorrenciaDTO.Response> page = ocorrenciaService.buscarComFiltros(
                tipo, status, gravidade, codigoLinha, codigoParada, afetaAcessibilidade, termo, pageable
        ).map(this::adicionarLinksHateoas);
        return ResponseEntity.ok(page);
    }

    @GetMapping("/acessibilidade-criticas")
    @Operation(summary = "Listar ocorrencias ativas que afetam a acessibilidade (JPQL + @Cacheable)")
    public ResponseEntity<List<OcorrenciaDTO.Response>> listarCriticasAcessibilidade(
            @RequestParam(required = false) TipoOcorrencia tipo
    ) {
        List<OcorrenciaDTO.Response> lista = ocorrenciaService.listarCriticasAcessibilidade(tipo)
                .stream()
                .map(this::adicionarLinksHateoas)
                .toList();
        return ResponseEntity.ok(lista);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Buscar ocorrencia por ID")
    public ResponseEntity<OcorrenciaDTO.Response> buscarPorId(@PathVariable Long id) {
        return ResponseEntity.ok(adicionarLinksHateoas(ocorrenciaService.buscarPorId(id)));
    }

    @PostMapping
    @Operation(summary = "Registrar nova ocorrencia cidada (@CacheEvict)")
    public ResponseEntity<OcorrenciaDTO.Response> criar(@Valid @RequestBody OcorrenciaDTO.Request request) {
        OcorrenciaDTO.Response criado = adicionarLinksHateoas(ocorrenciaService.criar(request));
        return ResponseEntity.status(HttpStatus.CREATED).body(criado);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Atualizar ocorrencia cidada (@CacheEvict)")
    public ResponseEntity<OcorrenciaDTO.Response> atualizar(
            @PathVariable Long id,
            @Valid @RequestBody OcorrenciaDTO.Request request
    ) {
        return ResponseEntity.ok(adicionarLinksHateoas(ocorrenciaService.atualizar(id, request)));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Remover ocorrencia cidada (@CacheEvict)")
    public ResponseEntity<Void> remover(@PathVariable Long id) {
        ocorrenciaService.remover(id);
        return ResponseEntity.noContent().build();
    }

    private OcorrenciaDTO.Response adicionarLinksHateoas(OcorrenciaDTO.Response dto) {
        dto.add(linkTo(methodOn(OcorrenciaController.class).buscarPorId(dto.getId())).withSelfRel());
        dto.add(linkTo(methodOn(OcorrenciaController.class)
                .listarComFiltros(null, null, null, null, null, null, null, Pageable.unpaged()))
                .withRel("ocorrencias"));
        if (dto.getCodigoLinha() != null) {
            dto.add(linkTo(methodOn(OlhoVivoController.class).buscarVeiculosPorLinha(dto.getCodigoLinha())).withRel("veiculos-linha"));
        }
        return dto;
    }
}
