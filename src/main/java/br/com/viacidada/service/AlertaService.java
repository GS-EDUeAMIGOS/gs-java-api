package br.com.viacidada.service;

import br.com.viacidada.config.CacheConfig;
import br.com.viacidada.dto.AlertaDTO;
import br.com.viacidada.entity.Alerta;
import br.com.viacidada.entity.Usuario;
import br.com.viacidada.exception.ResourceNotFoundException;
import br.com.viacidada.mapper.DomainMapper;
import br.com.viacidada.repository.AlertaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AlertaService {

    private final AlertaRepository alertaRepository;
    private final UsuarioService usuarioService;

    @Transactional(readOnly = true)
    public Page<AlertaDTO.Response> listar(Long usuarioId, Pageable pageable) {
        if (usuarioId != null) {
            return alertaRepository.findByUsuarioId(usuarioId, pageable)
                    .map(DomainMapper::toAlertaResponse);
        }
        return alertaRepository.findAll(pageable)
                .map(DomainMapper::toAlertaResponse);
    }

    @Transactional(readOnly = true)
    @Cacheable(value = CacheConfig.CACHE_ALERTAS, key = "T(String).valueOf(#codigoLinha).concat('-').concat(T(String).valueOf(#codigoParada))")
    public List<AlertaDTO.Response> buscarAtivosPorLinhaOuParada(Integer codigoLinha, Long codigoParada) {
        return alertaRepository.buscarAlertasAtivosPorLinhaOuParada(codigoLinha, codigoParada)
                .stream()
                .map(DomainMapper::toAlertaResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public AlertaDTO.Response buscarPorId(Long id) {
        return DomainMapper.toAlertaResponse(buscarEntidade(id));
    }

    @Transactional
    @CacheEvict(value = CacheConfig.CACHE_ALERTAS, allEntries = true)
    public AlertaDTO.Response criar(AlertaDTO.Request request) {
        Usuario usuario = usuarioService.buscarEntidadePorId(request.getUsuarioId());

        Alerta entidade = Alerta.builder()
                .usuario(usuario)
                .titulo(request.getTitulo())
                .mensagem(request.getMensagem())
                .codigoLinha(request.getCodigoLinha())
                .codigoParada(request.getCodigoParada())
                .somenteVeiculoAcessivel(Boolean.TRUE.equals(request.getSomenteVeiculoAcessivel()))
                .horarioNotificacao(request.getHorarioNotificacao())
                .ativo(request.getAtivo() == null || request.getAtivo())
                .build();

        return DomainMapper.toAlertaResponse(alertaRepository.save(entidade));
    }

    @Transactional
    @CacheEvict(value = CacheConfig.CACHE_ALERTAS, allEntries = true)
    public AlertaDTO.Response atualizar(Long id, AlertaDTO.Request request) {
        Alerta entidade = buscarEntidade(id);
        entidade.setTitulo(request.getTitulo());
        entidade.setMensagem(request.getMensagem());
        entidade.setCodigoLinha(request.getCodigoLinha());
        entidade.setCodigoParada(request.getCodigoParada());
        if (request.getSomenteVeiculoAcessivel() != null) {
            entidade.setSomenteVeiculoAcessivel(request.getSomenteVeiculoAcessivel());
        }
        entidade.setHorarioNotificacao(request.getHorarioNotificacao());
        if (request.getAtivo() != null) {
            entidade.setAtivo(request.getAtivo());
        }
        return DomainMapper.toAlertaResponse(alertaRepository.save(entidade));
    }

    @Transactional
    @CacheEvict(value = CacheConfig.CACHE_ALERTAS, allEntries = true)
    public void remover(Long id) {
        Alerta entidade = buscarEntidade(id);
        alertaRepository.delete(entidade);
    }

    private Alerta buscarEntidade(Long id) {
        return alertaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Alerta nao encontrado com ID: " + id));
    }
}
