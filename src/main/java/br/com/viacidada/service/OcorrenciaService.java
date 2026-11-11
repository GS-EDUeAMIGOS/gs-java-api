package br.com.viacidada.service;

import br.com.viacidada.config.CacheConfig;
import br.com.viacidada.dto.OcorrenciaDTO;
import br.com.viacidada.entity.*;
import br.com.viacidada.exception.ResourceNotFoundException;
import br.com.viacidada.mapper.DomainMapper;
import br.com.viacidada.repository.OcorrenciaRepository;
import br.com.viacidada.specification.OcorrenciaSpecification;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class OcorrenciaService {

    private final OcorrenciaRepository ocorrenciaRepository;
    private final UsuarioService usuarioService;

    @Transactional(readOnly = true)
    public Page<OcorrenciaDTO.Response> buscarComFiltros(
            TipoOcorrencia tipo,
            StatusOcorrencia status,
            NivelGravidade gravidade,
            Integer codigoLinha,
            Long codigoParada,
            Boolean afetaAcessibilidade,
            String termo,
            Pageable pageable
    ) {
        Specification<Ocorrencia> spec = OcorrenciaSpecification.comFiltros(
                tipo, status, gravidade, codigoLinha, codigoParada, afetaAcessibilidade, termo
        );
        return ocorrenciaRepository.findAll(spec, pageable)
                .map(DomainMapper::toOcorrenciaResponse);
    }

    @Transactional(readOnly = true)
    @Cacheable(value = CacheConfig.CACHE_OCORRENCIAS, key = "'criticas-' + (#tipo != null ? #tipo.name() : 'TODAS')")
    public List<OcorrenciaDTO.Response> listarCriticasAcessibilidade(TipoOcorrencia tipo) {
        List<StatusOcorrencia> statusAtivos = List.of(
                StatusOcorrencia.ABERTA,
                StatusOcorrencia.EM_ANALISE,
                StatusOcorrencia.CONFIRMADA
        );
        return ocorrenciaRepository.buscarOcorrenciasCriticasAcessibilidade(statusAtivos, tipo)
                .stream()
                .map(DomainMapper::toOcorrenciaResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public OcorrenciaDTO.Response buscarPorId(Long id) {
        return DomainMapper.toOcorrenciaResponse(buscarEntidade(id));
    }

    @Transactional
    @CacheEvict(value = CacheConfig.CACHE_OCORRENCIAS, allEntries = true)
    public OcorrenciaDTO.Response criar(OcorrenciaDTO.Request request) {
        Usuario usuario = usuarioService.buscarEntidadePorId(request.getUsuarioId());

        Ocorrencia entidade = Ocorrencia.builder()
                .usuario(usuario)
                .titulo(request.getTitulo())
                .descricao(request.getDescricao())
                .tipo(request.getTipo())
                .status(request.getStatus() != null ? request.getStatus() : StatusOcorrencia.ABERTA)
                .gravidade(request.getGravidade() != null ? request.getGravidade() : NivelGravidade.MEDIA)
                .codigoLinha(request.getCodigoLinha())
                .codigoParada(request.getCodigoParada())
                .prefixoVeiculo(request.getPrefixoVeiculo())
                .afetaAcessibilidade(Boolean.TRUE.equals(request.getAfetaAcessibilidade()))
                .build();

        return DomainMapper.toOcorrenciaResponse(ocorrenciaRepository.save(entidade));
    }

    @Transactional
    @CacheEvict(value = CacheConfig.CACHE_OCORRENCIAS, allEntries = true)
    public OcorrenciaDTO.Response atualizar(Long id, OcorrenciaDTO.Request request) {
        Ocorrencia entidade = buscarEntidade(id);
        entidade.setTitulo(request.getTitulo());
        entidade.setDescricao(request.getDescricao());
        entidade.setTipo(request.getTipo());
        if (request.getStatus() != null) {
            entidade.setStatus(request.getStatus());
        }
        if (request.getGravidade() != null) {
            entidade.setGravidade(request.getGravidade());
        }
        entidade.setCodigoLinha(request.getCodigoLinha());
        entidade.setCodigoParada(request.getCodigoParada());
        entidade.setPrefixoVeiculo(request.getPrefixoVeiculo());
        if (request.getAfetaAcessibilidade() != null) {
            entidade.setAfetaAcessibilidade(request.getAfetaAcessibilidade());
        }

        return DomainMapper.toOcorrenciaResponse(ocorrenciaRepository.save(entidade));
    }

    @Transactional
    @CacheEvict(value = CacheConfig.CACHE_OCORRENCIAS, allEntries = true)
    public void remover(Long id) {
        Ocorrencia entidade = buscarEntidade(id);
        ocorrenciaRepository.delete(entidade);
    }

    private Ocorrencia buscarEntidade(Long id) {
        return ocorrenciaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Ocorrencia nao encontrada com ID: " + id));
    }
}
