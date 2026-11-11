package br.com.viacidada.service;

import br.com.viacidada.dto.ParadaFavoritaDTO;
import br.com.viacidada.entity.ParadaFavorita;
import br.com.viacidada.entity.Usuario;
import br.com.viacidada.exception.ResourceNotFoundException;
import br.com.viacidada.mapper.DomainMapper;
import br.com.viacidada.repository.ParadaFavoritaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ParadaFavoritaService {

    private final ParadaFavoritaRepository paradaFavoritaRepository;
    private final UsuarioService usuarioService;

    @Transactional(readOnly = true)
    public Page<ParadaFavoritaDTO.Response> listar(Long usuarioId, Pageable pageable) {
        if (usuarioId != null) {
            return paradaFavoritaRepository.findByUsuarioId(usuarioId, pageable)
                    .map(DomainMapper::toParadaFavoritaResponse);
        }
        return paradaFavoritaRepository.findAll(pageable)
                .map(DomainMapper::toParadaFavoritaResponse);
    }

    @Transactional(readOnly = true)
    public List<ParadaFavoritaDTO.Response> listarAcessiveisDoUsuario(Long usuarioId) {
        return paradaFavoritaRepository.buscarParadasAcessiveisDoUsuario(usuarioId)
                .stream()
                .map(DomainMapper::toParadaFavoritaResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public ParadaFavoritaDTO.Response buscarPorId(Long id) {
        return DomainMapper.toParadaFavoritaResponse(buscarEntidade(id));
    }

    @Transactional
    public ParadaFavoritaDTO.Response criar(ParadaFavoritaDTO.Request request) {
        Usuario usuario = usuarioService.buscarEntidadePorId(request.getUsuarioId());

        ParadaFavorita entidade = ParadaFavorita.builder()
                .usuario(usuario)
                .codigoParada(request.getCodigoParada())
                .nomeParada(request.getNomeParada())
                .endereco(request.getEndereco())
                .latitude(request.getLatitude())
                .longitude(request.getLongitude())
                .possuiRampaAcessivel(Boolean.TRUE.equals(request.getPossuiRampaAcessivel()))
                .possuiPisoTatil(Boolean.TRUE.equals(request.getPossuiPisoTatil()))
                .build();

        return DomainMapper.toParadaFavoritaResponse(paradaFavoritaRepository.save(entidade));
    }

    @Transactional
    public ParadaFavoritaDTO.Response atualizar(Long id, ParadaFavoritaDTO.Request request) {
        ParadaFavorita entidade = buscarEntidade(id);
        entidade.setCodigoParada(request.getCodigoParada());
        entidade.setNomeParada(request.getNomeParada());
        entidade.setEndereco(request.getEndereco());
        entidade.setLatitude(request.getLatitude());
        entidade.setLongitude(request.getLongitude());
        if (request.getPossuiRampaAcessivel() != null) {
            entidade.setPossuiRampaAcessivel(request.getPossuiRampaAcessivel());
        }
        if (request.getPossuiPisoTatil() != null) {
            entidade.setPossuiPisoTatil(request.getPossuiPisoTatil());
        }
        return DomainMapper.toParadaFavoritaResponse(paradaFavoritaRepository.save(entidade));
    }

    @Transactional
    public void remover(Long id) {
        ParadaFavorita entidade = buscarEntidade(id);
        paradaFavoritaRepository.delete(entidade);
    }

    private ParadaFavorita buscarEntidade(Long id) {
        return paradaFavoritaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Parada favorita nao encontrada com ID: " + id));
    }
}
