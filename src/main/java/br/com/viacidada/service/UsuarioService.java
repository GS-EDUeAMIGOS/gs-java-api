package br.com.viacidada.service;

import br.com.viacidada.dto.UsuarioDTO;
import br.com.viacidada.entity.Role;
import br.com.viacidada.entity.Usuario;
import br.com.viacidada.exception.BusinessRuleException;
import br.com.viacidada.exception.ResourceNotFoundException;
import br.com.viacidada.mapper.DomainMapper;
import br.com.viacidada.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UsuarioService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional(readOnly = true)
    public Page<UsuarioDTO.Response> listar(Role role, Boolean mobilidadeReduzida, Pageable pageable) {
        return usuarioRepository.buscarPorPerfilEAcessibilidade(role, mobilidadeReduzida, pageable)
                .map(DomainMapper::toUsuarioResponse);
    }

    @Transactional(readOnly = true)
    public UsuarioDTO.Response buscarPorId(Long id) {
        Usuario usuario = buscarEntidadePorId(id);
        return DomainMapper.toUsuarioResponse(usuario);
    }

    @Transactional
    public UsuarioDTO.Response atualizar(Long id, UsuarioDTO.Request request) {
        Usuario usuario = buscarEntidadePorId(id);

        if (!usuario.getEmail().equalsIgnoreCase(request.getEmail())
                && usuarioRepository.existsByEmailIgnoreCase(request.getEmail())) {
            throw new BusinessRuleException("O e-mail informado ja esta em uso por outro usuario.");
        }

        usuario.setNome(request.getNome());
        usuario.setEmail(request.getEmail().toLowerCase().trim());

        if (request.getSenha() != null && !request.getSenha().isBlank()) {
            usuario.setSenha(passwordEncoder.encode(request.getSenha()));
        }
        if (request.getRole() != null) {
            usuario.setRole(request.getRole());
        }
        if (request.getPossuiMobilidadeReduzida() != null) {
            usuario.setPossuiMobilidadeReduzida(request.getPossuiMobilidadeReduzida());
        }

        return DomainMapper.toUsuarioResponse(usuarioRepository.save(usuario));
    }

    @Transactional
    public void remover(Long id) {
        Usuario usuario = buscarEntidadePorId(id);
        usuarioRepository.delete(usuario);
    }

    @Transactional(readOnly = true)
    public Usuario buscarEntidadePorId(Long id) {
        return usuarioRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario nao encontrado com ID: " + id));
    }
}
