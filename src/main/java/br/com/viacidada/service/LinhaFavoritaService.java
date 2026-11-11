package br.com.viacidada.service;

import br.com.viacidada.dto.LinhaFavoritaDTO;
import br.com.viacidada.entity.LinhaFavorita;
import br.com.viacidada.entity.Usuario;
import br.com.viacidada.exception.BusinessRuleException;
import br.com.viacidada.exception.ResourceNotFoundException;
import br.com.viacidada.mapper.DomainMapper;
import br.com.viacidada.repository.LinhaFavoritaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class LinhaFavoritaService {

    private final LinhaFavoritaRepository linhaFavoritaRepository;
    private final UsuarioService usuarioService;

    @Transactional(readOnly = true)
    public Page<LinhaFavoritaDTO.Response> listar(Long usuarioId, Pageable pageable) {
        if (usuarioId != null) {
            return linhaFavoritaRepository.findByUsuarioId(usuarioId, pageable)
                    .map(DomainMapper::toLinhaFavoritaResponse);
        }
        return linhaFavoritaRepository.findAll(pageable)
                .map(DomainMapper::toLinhaFavoritaResponse);
    }

    @Transactional(readOnly = true)
    public List<LinhaFavoritaDTO.Response> buscarPorTermo(Long usuarioId, String termo) {
        return linhaFavoritaRepository.buscarFavoritasPorTermo(usuarioId, termo)
                .stream()
                .map(DomainMapper::toLinhaFavoritaResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public LinhaFavoritaDTO.Response buscarPorId(Long id) {
        return DomainMapper.toLinhaFavoritaResponse(buscarEntidade(id));
    }

    @Transactional
    public LinhaFavoritaDTO.Response criar(LinhaFavoritaDTO.Request request) {
        if (linhaFavoritaRepository.existsByUsuarioIdAndCodigoLinha(request.getUsuarioId(), request.getCodigoLinha())) {
            throw new BusinessRuleException("Esta linha ja esta cadastrada nos favoritos do usuario.");
        }
        Usuario usuario = usuarioService.buscarEntidadePorId(request.getUsuarioId());

        LinhaFavorita entidade = LinhaFavorita.builder()
                .usuario(usuario)
                .codigoLinha(request.getCodigoLinha())
                .letreiro(request.getLetreiro())
                .sentido(request.getSentido())
                .terminalOrigem(request.getTerminalOrigem())
                .terminalDestino(request.getTerminalDestino())
                .apelido(request.getApelido())
                .build();

        return DomainMapper.toLinhaFavoritaResponse(linhaFavoritaRepository.save(entidade));
    }

    @Transactional
    public LinhaFavoritaDTO.Response atualizar(Long id, LinhaFavoritaDTO.Request request) {
        LinhaFavorita entidade = buscarEntidade(id);
        entidade.setCodigoLinha(request.getCodigoLinha());
        entidade.setLetreiro(request.getLetreiro());
        entidade.setSentido(request.getSentido());
        entidade.setTerminalOrigem(request.getTerminalOrigem());
        entidade.setTerminalDestino(request.getTerminalDestino());
        entidade.setApelido(request.getApelido());
        return DomainMapper.toLinhaFavoritaResponse(linhaFavoritaRepository.save(entidade));
    }

    @Transactional
    public void remover(Long id) {
        LinhaFavorita entidade = buscarEntidade(id);
        linhaFavoritaRepository.delete(entidade);
    }

    private LinhaFavorita buscarEntidade(Long id) {
        return linhaFavoritaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Linha favorita nao encontrada com ID: " + id));
    }
}
