package br.com.viacidada.controller;

import br.com.viacidada.dto.UsuarioDTO;
import br.com.viacidada.entity.Role;
import br.com.viacidada.service.UsuarioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

@RestController
@RequestMapping("/api/usuarios")
@RequiredArgsConstructor
@Tag(name = "3. Usuarios", description = "Gerenciamento de usuarios com paginacao, JPQL e HATEOAS")
public class UsuarioController {

    private final UsuarioService usuarioService;

    @GetMapping
    @Operation(summary = "Listar usuarios com paginacao e filtro JPQL por papel e mobilidade reduzida")
    public ResponseEntity<Page<UsuarioDTO.Response>> listar(
            @RequestParam(required = false) Role role,
            @RequestParam(required = false) Boolean mobilidadeReduzida,
            @ParameterObject @PageableDefault(size = 10, sort = "nome") Pageable pageable
    ) {
        Page<UsuarioDTO.Response> page = usuarioService.listar(role, mobilidadeReduzida, pageable)
                .map(this::adicionarLinksHateoas);
        return ResponseEntity.ok(page);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Buscar usuario por ID")
    public ResponseEntity<UsuarioDTO.Response> buscarPorId(@PathVariable Long id) {
        return ResponseEntity.ok(adicionarLinksHateoas(usuarioService.buscarPorId(id)));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Atualizar dados de um usuario")
    public ResponseEntity<UsuarioDTO.Response> atualizar(
            @PathVariable Long id,
            @Valid @RequestBody UsuarioDTO.Request request
    ) {
        return ResponseEntity.ok(adicionarLinksHateoas(usuarioService.atualizar(id, request)));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Remover usuario por ID")
    public ResponseEntity<Void> remover(@PathVariable Long id) {
        usuarioService.remover(id);
        return ResponseEntity.noContent().build();
    }

    private UsuarioDTO.Response adicionarLinksHateoas(UsuarioDTO.Response dto) {
        dto.add(linkTo(methodOn(UsuarioController.class).buscarPorId(dto.getId())).withSelfRel());
        dto.add(linkTo(methodOn(UsuarioController.class).listar(null, null, Pageable.unpaged())).withRel("usuarios"));
        return dto;
    }
}
