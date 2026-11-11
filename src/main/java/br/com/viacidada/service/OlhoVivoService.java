package br.com.viacidada.service;

import br.com.viacidada.client.OlhoVivoClient;
import br.com.viacidada.config.CacheConfig;
import br.com.viacidada.dto.OlhoVivoDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class OlhoVivoService {

    private final OlhoVivoClient olhoVivoClient;

    @Cacheable(value = CacheConfig.CACHE_LINHAS_SPTRANS, key = "#termo != null ? #termo.trim().toLowerCase() : 'todas'")
    public List<OlhoVivoDTO.LinhaResumo> buscarLinhas(String termo) {
        return olhoVivoClient.buscarLinhas(termo);
    }

    public OlhoVivoDTO.PosicaoLinhaResponse buscarVeiculosPorLinha(Integer codigoLinha) {
        return olhoVivoClient.buscarVeiculosPorLinha(codigoLinha);
    }

    @Cacheable(value = CacheConfig.CACHE_PARADAS_SPTRANS, key = "#termo != null ? #termo.trim().toLowerCase() : 'todas'")
    public List<OlhoVivoDTO.ParadaResumo> buscarParadas(String termo) {
        return olhoVivoClient.buscarParadas(termo);
    }

    public OlhoVivoDTO.PrevisaoParadaResponse buscarPrevisoesPorParada(Long codigoParada) {
        return olhoVivoClient.buscarPrevisoesPorParada(codigoParada);
    }

    @CacheEvict(value = {CacheConfig.CACHE_LINHAS_SPTRANS, CacheConfig.CACHE_PARADAS_SPTRANS}, allEntries = true)
    public void limparCacheSptrans() {
        // Limpa cache das consultas estaticas de linhas e paradas da SPTrans
    }
}
