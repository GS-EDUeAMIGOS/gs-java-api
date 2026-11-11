package br.com.viacidada.config;

import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class CacheConfig {

    public static final String CACHE_LINHAS_SPTRANS = "linhasSptrans";
    public static final String CACHE_PARADAS_SPTRANS = "paradasSptrans";
    public static final String CACHE_OCORRENCIAS = "ocorrencias";
    public static final String CACHE_ALERTAS = "alertas";

    @Bean
    public CacheManager cacheManager() {
        ConcurrentMapCacheManager cacheManager = new ConcurrentMapCacheManager();
        cacheManager.setCacheNames(List.of(
                CACHE_LINHAS_SPTRANS,
                CACHE_PARADAS_SPTRANS,
                CACHE_OCORRENCIAS,
                CACHE_ALERTAS
        ));
        return cacheManager;
    }
}
