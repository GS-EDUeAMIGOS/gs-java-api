package br.com.viacidada.service;

import br.com.viacidada.client.GeocodingClient;
import br.com.viacidada.client.OlhoVivoClient;
import br.com.viacidada.config.CacheConfig;
import br.com.viacidada.dto.OlhoVivoDTO;
import br.com.viacidada.entity.Ocorrencia;
import br.com.viacidada.entity.StatusOcorrencia;
import br.com.viacidada.repository.OcorrenciaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class OlhoVivoService {

    private final OlhoVivoClient olhoVivoClient;
    private final GeocodingClient geocodingClient;
    private final OcorrenciaRepository ocorrenciaRepository;

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

    @Cacheable(value = CacheConfig.CACHE_PARADAS_SPTRANS, key = "'linha_' + #codigoLinha + '_' + (#termo != null ? #termo.trim().toLowerCase() : '')")
    public List<OlhoVivoDTO.ParadaResumo> buscarParadasPorLinha(Integer codigoLinha, String termo) {
        List<OlhoVivoDTO.ParadaResumo> paradasDaLinha = olhoVivoClient.buscarParadasPorLinha(codigoLinha);
        if (paradasDaLinha != null && !paradasDaLinha.isEmpty()) {
            return paradasDaLinha;
        }
        if (termo != null && !termo.isBlank()) {
            return buscarParadasComFallback(termo);
        }
        return List.of();
    }

    public OlhoVivoDTO.PrevisaoParadaResponse buscarPrevisoesPorParada(Long codigoParada) {
        return olhoVivoClient.buscarPrevisoesPorParada(codigoParada);
    }

    public List<OlhoVivoDTO.LocalResolvido> buscarSugestoesLocais(String termo) {
        return geocodingClient.buscarSugestoesLocais(termo);
    }

    /**
     * Descobre onde o usuario esta pelo GPS (latitude/longitude),
     * busca as paradas de onibus EXCLUSIVAMENTE daquela rua/bairro e ordena pela distancia real em metros!
     */
    public OlhoVivoDTO.ParadasProximasGpsResponse buscarParadasProximasPorGps(
            double latitude,
            double longitude,
            String termoOpcional
    ) {
        OlhoVivoDTO.LocalResolvido localAtual = geocodingClient.resolverPorCoordenadas(latitude, longitude);

        List<String> termosTentativa = new ArrayList<>();
        if (termoOpcional != null && !termoOpcional.isBlank()) {
            termosTentativa.add(termoOpcional.trim());
        }
        if (localAtual.getTermosCandidatosSptrans() != null) {
            termosTentativa.addAll(localAtual.getTermosCandidatosSptrans());
        }
        if (localAtual.getLogradouro() != null && !localAtual.getLogradouro().isBlank()) {
            termosTentativa.add(geocodingClient.limparTermoParaSptrans(localAtual.getLogradouro()));
        }
        if (localAtual.getBairro() != null && !localAtual.getBairro().isBlank()) {
            termosTentativa.add(geocodingClient.limparTermoParaSptrans(localAtual.getBairro()));
        }

        List<OlhoVivoDTO.ParadaResumo> paradasCandidatas = combinarParadasOrdenadasPorProximidade(
                latitude,
                longitude,
                termosTentativa
        );

        OlhoVivoDTO.ParadaResumo maisProxima = paradasCandidatas.isEmpty() ? null : paradasCandidatas.get(0);
        int distMetros = 0;
        if (maisProxima != null && maisProxima.getLatitude() != null && maisProxima.getLongitude() != null) {
            double d = GeocodingClient.calcularDistanciaMetros(
                    latitude,
                    longitude,
                    maisProxima.getLatitude(),
                    maisProxima.getLongitude()
            );
            distMetros = d > 50000 ? 150 : (int) Math.round(d);
        }

        int tempoCaminhada = Math.max(1, (int) Math.ceil(distMetros / 75.0));
        boolean estaNoPonto = distMetros <= 85;

        String msg;
        if (maisProxima == null) {
            msg = "Nenhuma parada encontrada nas imediacoes de " + localAtual.getNomeLocal() + ".";
        } else if (estaNoPonto) {
            msg = "Voce ja esta no ponto! A parada " + maisProxima.getNomeParada()
                    + " (" + maisProxima.getEndereco() + ") fica a apenas " + distMetros + "m de voce.";
        } else {
            msg = "Local detectado: " + localAtual.getLogradouro() + " (" + localAtual.getBairro() + "). "
                    + "O ponto mais proximo fica a " + distMetros + "m (~" + tempoCaminhada + " min a pe): "
                    + maisProxima.getNomeParada() + " (" + maisProxima.getEndereco() + ").";
        }

        return OlhoVivoDTO.ParadasProximasGpsResponse.builder()
                .localAtual(localAtual)
                .paradaMaisProxima(maisProxima)
                .distanciaAtePontoMaisProximoMetros(distMetros)
                .tempoCaminhadaMinutos(tempoCaminhada)
                .usuarioEstaNoPonto(estaNoPonto)
                .mensagemProximidade(msg)
                .paradasProximas(paradasCandidatas.stream().limit(12).toList())
                .build();
    }

    public OlhoVivoDTO.RotaPlanejadaResponse planejarRota(String origem, String destino) {
        return planejarRota(origem, destino, null, null);
    }

    /**
     * Planejador Inteligente de Rotas (estilo Moovit / Google Maps Transit):
     * 1. Resolve a origem (por coordenadas GPS ou por nome de rua/bairro/local) SEM misturar com Paulista.
     * 2. Resolve o destino (ex.: "Extra Ricardo Jafet", "Hospital Heliopolis") extraindo logradouro e distrito.
     * 3. Busca as paradas mais proximas da origem e do destino (ordenadas por Haversine).
     * 4. Inspeciona as previsoes reais das paradas de origem e destino (/Previsao/Parada) para descobrir
     *    quais linhas de onibus realmente passam naquela rua/avenida!
     */
    public OlhoVivoDTO.RotaPlanejadaResponse planejarRota(
            String origem,
            String destino,
            Double latOrigem,
            Double lonOrigem
    ) {
        String termoOrigem = (origem == null || origem.isBlank()) ? "LAPA" : origem.trim();
        String termoDestino = (destino == null || destino.isBlank()) ? "PAULISTA" : destino.trim();

        // Garante que a sessao SPTrans seja validada imediatamente (para lancar erro 502 se token nao configurado nos testes)
        olhoVivoClient.autenticar();

        // 1. Resolver Origem (exclusivamente pelo GPS se enviado, ou pelo nome do local/rua digitado)
        boolean usandoGps = latOrigem != null && lonOrigem != null && latOrigem != 0.0 && lonOrigem != 0.0;
        OlhoVivoDTO.LocalResolvido localOrigem;
        if (usandoGps) {
            localOrigem = geocodingClient.resolverPorCoordenadas(latOrigem, lonOrigem);
        } else {
            localOrigem = geocodingClient.resolverLocalPorNome(termoOrigem);
        }

        // 2. Resolver Destino (ex.: "extra ricardo jafet" -> Av. Dr. Ricardo Jafet / Vila Dom Pedro I / Ipiranga)
        OlhoVivoDTO.LocalResolvido localDestino = geocodingClient.resolverLocalPorNome(termoDestino);

        List<String> termosBuscaOrigem = montarListaTermosLocal(localOrigem, usandoGps ? null : termoOrigem);
        List<String> termosBuscaDestino = montarListaTermosLocal(localDestino, termoDestino);

        // 3. Buscar Paradas da Origem e do Destino ordenadas pela menor distancia fisica (Haversine)
        List<OlhoVivoDTO.ParadaResumo> paradasOrigem = combinarParadasOrdenadasPorProximidade(
                localOrigem.getLatitude(),
                localOrigem.getLongitude(),
                termosBuscaOrigem
        );

        List<OlhoVivoDTO.ParadaResumo> paradasDestino = combinarParadasOrdenadasPorProximidade(
                localDestino.getLatitude(),
                localDestino.getLongitude(),
                termosBuscaDestino
        );

        OlhoVivoDTO.ParadaResumo paradaOrigemSugerida = paradasOrigem.isEmpty() ? null : paradasOrigem.get(0);
        OlhoVivoDTO.ParadaResumo paradaDestinoSugerida = paradasDestino.isEmpty() ? null : paradasDestino.get(0);

        // 4. Inspecionar Previsoes nas Paradas de Origem e Destino!
        // Isso descobre TODAS as linhas que passam naquela parada mesmo que o nome da rua nao esteja no letreiro!
        Map<Integer, String> previsaoPorLinhaNaOrigem = new HashMap<>();
        Map<Integer, OlhoVivoDTO.ParadaResumo> paradaConfirmadaOrigemPorLinha = new HashMap<>();
        List<OlhoVivoDTO.LinhaResumo> linhasExtraidasParadasOrigem = extrairLinhasDasParadas(
                paradasOrigem,
                previsaoPorLinhaNaOrigem,
                paradaConfirmadaOrigemPorLinha
        );

        Map<Integer, String> previsaoPorLinhaNoDestino = new HashMap<>();
        Map<Integer, OlhoVivoDTO.ParadaResumo> paradaConfirmadaDestinoPorLinha = new HashMap<>();
        List<OlhoVivoDTO.LinhaResumo> linhasExtraidasParadasDestino = extrairLinhasDasParadas(
                paradasDestino,
                previsaoPorLinhaNoDestino,
                paradaConfirmadaDestinoPorLinha
        );

        // 5. Combinar as linhas que passam fisicamente nas paradas com as linhas buscadas pelos termos da regiao
        List<OlhoVivoDTO.LinhaResumo> linhasOrigem = combinarLinhasDeMultiplosTermos(
                linhasExtraidasParadasOrigem,
                termosBuscaOrigem
        );

        List<OlhoVivoDTO.LinhaResumo> linhasDestino = combinarLinhasDeMultiplosTermos(
                linhasExtraidasParadasDestino,
                termosBuscaDestino
        );

        int distCaminhadaOrigem = calcularDistanciaSegura(
                localOrigem.getLatitude(),
                localOrigem.getLongitude(),
                paradaOrigemSugerida,
                180
        );
        int tempoCaminhadaOrigem = Math.max(1, (int) Math.ceil(distCaminhadaOrigem / 75.0));

        int distCaminhadaDestino = calcularDistanciaSegura(
                localDestino.getLatitude(),
                localDestino.getLongitude(),
                paradaDestinoSugerida,
                190
        );
        int tempoCaminhadaDestino = Math.max(1, (int) Math.ceil(distCaminhadaDestino / 75.0));

        // 6. Identificar Linhas Diretas (mesma linha presente nas paradas/regiao de origem e destino)
        Map<Integer, OlhoVivoDTO.LinhaResumo> diretasMap = new LinkedHashMap<>();
        Set<String> letreirosOrigem = new HashSet<>();
        Set<Integer> codigosOrigem = new HashSet<>();
        for (OlhoVivoDTO.LinhaResumo lo : linhasOrigem) {
            letreirosOrigem.add(montarLetreiroCompleto(lo));
            codigosOrigem.add(lo.getCodigoLinha());
            for (String td : termosBuscaDestino) {
                if (contemTermoNoTrajeto(lo, normalizarTexto(td))) {
                    diretasMap.putIfAbsent(lo.getCodigoLinha(), lo);
                }
            }
        }
        for (OlhoVivoDTO.LinhaResumo ld : linhasDestino) {
            String letreiro = montarLetreiroCompleto(ld);
            if (codigosOrigem.contains(ld.getCodigoLinha()) || letreirosOrigem.contains(letreiro)) {
                diretasMap.putIfAbsent(ld.getCodigoLinha(), ld);
            } else {
                for (String to : termosBuscaOrigem) {
                    if (contemTermoNoTrajeto(ld, normalizarTexto(to))) {
                        diretasMap.putIfAbsent(ld.getCodigoLinha(), ld);
                    }
                }
            }
        }

        String nomeExibicaoOrigem = montarNomeExibicaoLocal(localOrigem, usandoGps ? "Sua Localizacao" : termoOrigem);
        String nomeExibicaoDestino = montarNomeExibicaoLocal(localDestino, termoDestino);

        List<OlhoVivoDTO.OpcaoRota> opcoes = new ArrayList<>();

        // Montar ate 2 opcoes de Linha Direta
        int countDiretas = 0;
        for (OlhoVivoDTO.LinhaResumo linhaDireta : diretasMap.values()) {
            if (countDiretas >= 2) break;
            OlhoVivoDTO.ParadaResumo paradaEmbarque = paradaConfirmadaOrigemPorLinha.getOrDefault(
                    linhaDireta.getCodigoLinha(),
                    paradaOrigemSugerida
            );
            OlhoVivoDTO.ParadaResumo paradaDesembarque = paradaConfirmadaDestinoPorLinha.getOrDefault(
                    linhaDireta.getCodigoLinha(),
                    paradaDestinoSugerida
            );
            boolean confirmadoNoPonto = paradaConfirmadaOrigemPorLinha.containsKey(linhaDireta.getCodigoLinha());

            int distAteEstePonto = calcularDistanciaSegura(
                    localOrigem.getLatitude(),
                    localOrigem.getLongitude(),
                    paradaEmbarque,
                    distCaminhadaOrigem
            );
            int minCaminhadaOrigem = Math.max(1, (int) Math.ceil(distAteEstePonto / 75.0));

            OlhoVivoDTO.PassoRota passoCaminhadaInicial = construirPassoCaminhadaOrigem(
                    1,
                    nomeExibicaoOrigem,
                    paradaEmbarque,
                    distAteEstePonto,
                    minCaminhadaOrigem
            );

            OlhoVivoDTO.PassoRota passoOnibus = construirPassoOnibus(
                    2,
                    "EMBARQUE_DIRETO",
                    paradaEmbarque != null
                            ? "Ponto: " + paradaEmbarque.getNomeParada() + " (" + paradaEmbarque.getEndereco() + ")"
                            : "Ponto proximo a " + nomeExibicaoOrigem,
                    "No ponto, pegue o onibus direto " + montarLetreiroCompleto(linhaDireta)
                            + " sentido " + formatarSentido(linhaDireta),
                    linhaDireta,
                    previsaoPorLinhaNaOrigem.get(linhaDireta.getCodigoLinha()),
                    paradaEmbarque,
                    confirmadoNoPonto
            );

            OlhoVivoDTO.PassoRota passoDesembarqueCaminhada = construirPassoDesembarqueComCaminhada(
                    3,
                    paradaDesembarque,
                    nomeExibicaoDestino,
                    localDestino,
                    linhaDireta,
                    passoOnibus,
                    distCaminhadaDestino,
                    tempoCaminhadaDestino
            );

            boolean acessivel = passoOnibus.getVeiculosAcessiveisPcD() != null && passoOnibus.getVeiculosAcessiveisPcD() > 0;
            int tempoEstimado = (acessivel ? 26 : 32) + minCaminhadaOrigem + tempoCaminhadaDestino;

            opcoes.add(OlhoVivoDTO.OpcaoRota.builder()
                    .titulo("Linha Direta • " + montarLetreiroCompleto(linhaDireta))
                    .tipoRota("DIRETA")
                    .recomendada(false)
                    .motivoRecomendacao("Caminhe " + distAteEstePonto + "m ate o ponto e pegue 1 unico onibus ate " + nomeExibicaoDestino)
                    .totalOnibusNecessarios(1)
                    .tempoEstimadoMinutos(tempoEstimado + (countDiretas * 4))
                    .totalmenteAcessivelPcD(acessivel)
                    .totalOcorrenciasNoTrajeto(passoOnibus.getOcorrenciasAbertas())
                    .passos(List.of(passoCaminhadaInicial, passoOnibus, passoDesembarqueCaminhada))
                    .build());
            countDiretas++;
        }

        // 7. Identificar Opcoes com Baldeacao (2 onibus: Linha que passa no Ponto de Origem -> Linha que chega no Ponto de Destino)
        String chaveOrigemPrincipal = termosBuscaOrigem.isEmpty() ? "" : normalizarTexto(termosBuscaOrigem.get(0));
        List<ParBaldeacao> paresBaldeacao = encontrarMelhoresBaldeacoes(
                linhasOrigem,
                linhasDestino,
                diretasMap.keySet(),
                chaveOrigemPrincipal
        );

        int countBaldeacao = 0;
        for (ParBaldeacao par : paresBaldeacao) {
            if (countBaldeacao >= 2 || opcoes.size() >= 4) break;

            OlhoVivoDTO.ParadaResumo paradaEmbarque1 = paradaConfirmadaOrigemPorLinha.getOrDefault(
                    par.linha1.getCodigoLinha(),
                    paradaOrigemSugerida
            );
            OlhoVivoDTO.ParadaResumo paradaDesembarque2 = paradaConfirmadaDestinoPorLinha.getOrDefault(
                    par.linha2.getCodigoLinha(),
                    paradaDestinoSugerida
            );
            boolean confirmadoNoPonto1 = paradaConfirmadaOrigemPorLinha.containsKey(par.linha1.getCodigoLinha());

            int distAteEstePonto = calcularDistanciaSegura(
                    localOrigem.getLatitude(),
                    localOrigem.getLongitude(),
                    paradaEmbarque1,
                    distCaminhadaOrigem
            );
            int minCaminhadaOrigem = Math.max(1, (int) Math.ceil(distAteEstePonto / 75.0));

            OlhoVivoDTO.PassoRota passoCaminhadaInicial = construirPassoCaminhadaOrigem(
                    1,
                    nomeExibicaoOrigem,
                    paradaEmbarque1,
                    distAteEstePonto,
                    minCaminhadaOrigem
            );

            OlhoVivoDTO.PassoRota passo1 = construirPassoOnibus(
                    2,
                    "EMBARQUE",
                    paradaEmbarque1 != null
                            ? "Ponto: " + paradaEmbarque1.getNomeParada() + " (" + paradaEmbarque1.getEndereco() + ")"
                            : "Ponto de partida em " + nomeExibicaoOrigem,
                    "1º Onibus: Embarque na linha " + montarLetreiroCompleto(par.linha1)
                            + " (" + formatarSentido(par.linha1) + ") ate " + par.pontoConexao,
                    par.linha1,
                    previsaoPorLinhaNaOrigem.get(par.linha1.getCodigoLinha()),
                    paradaEmbarque1,
                    confirmadoNoPonto1
            );

            OlhoVivoDTO.PassoRota passo2 = construirPassoOnibus(
                    3,
                    "BALDEACAO",
                    "Ponto de Baldeacao: " + par.pontoConexao,
                    "2º Onibus (Baldeacao): Desca em " + par.pontoConexao
                            + ", caminhe ate a parada da linha " + montarLetreiroCompleto(par.linha2)
                            + " (" + formatarSentido(par.linha2) + ") e embarque rumo a " + nomeExibicaoDestino,
                    par.linha2,
                    previsaoPorLinhaNoDestino.get(par.linha2.getCodigoLinha()),
                    null,
                    false
            );

            OlhoVivoDTO.PassoRota passo3 = construirPassoDesembarqueComCaminhada(
                    4,
                    paradaDesembarque2,
                    nomeExibicaoDestino,
                    localDestino,
                    par.linha2,
                    passo2,
                    distCaminhadaDestino,
                    tempoCaminhadaDestino
            );

            boolean ambosAcessiveis = (passo1.getVeiculosAcessiveisPcD() != null && passo1.getVeiculosAcessiveisPcD() > 0)
                    && (passo2.getVeiculosAcessiveisPcD() != null && passo2.getVeiculosAcessiveisPcD() > 0);
            int totalOcorrencias = (passo1.getOcorrenciasAbertas() != null ? passo1.getOcorrenciasAbertas() : 0)
                    + (passo2.getOcorrenciasAbertas() != null ? passo2.getOcorrenciasAbertas() : 0);

            opcoes.add(OlhoVivoDTO.OpcaoRota.builder()
                    .titulo("Baldeacao • " + montarLetreiroCompleto(par.linha1) + " ➔ " + montarLetreiroCompleto(par.linha2))
                    .tipoRota("BALDEACAO")
                    .recomendada(false)
                    .motivoRecomendacao("Caminhe " + distAteEstePonto + "m ate o ponto, pegue o " + montarLetreiroCompleto(par.linha1)
                            + " e faca baldeacao em " + par.pontoConexao + " para o " + montarLetreiroCompleto(par.linha2))
                    .totalOnibusNecessarios(2)
                    .tempoEstimadoMinutos(38 + minCaminhadaOrigem + tempoCaminhadaDestino + (countBaldeacao * 6))
                    .totalmenteAcessivelPcD(ambosAcessiveis)
                    .totalOcorrenciasNoTrajeto(totalOcorrencias)
                    .passos(List.of(passoCaminhadaInicial, passo1, passo2, passo3))
                    .build());
            countBaldeacao++;
        }

        // Ordenar e eleger a Melhor Opcao Recomendada
        if (!opcoes.isEmpty()) {
            opcoes.sort(Comparator
                    .comparingInt((OlhoVivoDTO.OpcaoRota o) -> "DIRETA".equals(o.getTipoRota()) ? 0 : 1)
                    .thenComparingInt(o -> o.getTotalOcorrenciasNoTrajeto() != null ? o.getTotalOcorrenciasNoTrajeto() : 0)
                    .thenComparingInt(o -> Boolean.TRUE.equals(o.getTotalmenteAcessivelPcD()) ? 0 : 1)
                    .thenComparingInt(OlhoVivoDTO.OpcaoRota::getTempoEstimadoMinutos));

            OlhoVivoDTO.OpcaoRota melhor = opcoes.get(0);
            melhor.setRecomendada(true);
            if ("DIRETA".equals(melhor.getTipoRota())) {
                melhor.setMotivoRecomendacao(
                        Boolean.TRUE.equals(melhor.getTotalmenteAcessivelPcD())
                                ? "Melhor rota para " + nomeExibicaoDestino + ": 1 unico onibus (sem baldeacao) e frota PcD ativa!"
                                : "Melhor rota para " + nomeExibicaoDestino + ": caminho mais rapido sem precisar trocar de onibus."
                );
            } else {
                melhor.setMotivoRecomendacao(
                        "Melhor rota para " + nomeExibicaoDestino + ": caminhada ate o ponto mais proximo + baldeacao rapida."
                );
            }
        }

        return OlhoVivoDTO.RotaPlanejadaResponse.builder()
                .origemBuscada(usandoGps ? nomeExibicaoOrigem : termoOrigem)
                .destinoBuscado(termoDestino)
                .horarioConsulta(LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")))
                .localOrigemResolvido(localOrigem)
                .localDestinoResolvido(localDestino)
                .distanciaCaminhadaOrigemMetros(distCaminhadaOrigem)
                .distanciaCaminhadaDestinoMetros(distCaminhadaDestino)
                .paradaOrigemSugerida(paradaOrigemSugerida)
                .paradaDestinoSugerida(paradaDestinoSugerida)
                .paradasProximasOrigem(paradasOrigem.stream().limit(8).toList())
                .opcoes(opcoes)
                .build();
    }

    /**
     * Consulta as previsoes das paradas mais proximas para descobrir quais linhas de onibus
     * realmente passam naquela rua/parada!
     */
    private List<OlhoVivoDTO.LinhaResumo> extrairLinhasDasParadas(
            List<OlhoVivoDTO.ParadaResumo> paradas,
            Map<Integer, String> previsaoPorLinha,
            Map<Integer, OlhoVivoDTO.ParadaResumo> paradaConfirmadaPorLinha
    ) {
        List<OlhoVivoDTO.LinhaResumo> linhasExtraidas = new ArrayList<>();
        Set<Integer> codigosVistos = new HashSet<>();

        for (int i = 0; i < Math.min(3, paradas.size()); i++) {
            OlhoVivoDTO.ParadaResumo parada = paradas.get(i);
            if (parada == null || parada.getCodigoParada() == null) continue;
            try {
                OlhoVivoDTO.PrevisaoParadaResponse prev = buscarPrevisoesPorParada(parada.getCodigoParada());
                if (prev != null && prev.getParada() != null && prev.getParada().getLinhas() != null) {
                    for (OlhoVivoDTO.LinhaPrevisao lp : prev.getParada().getLinhas()) {
                        if (lp.getCodigoLinha() == null) continue;
                        paradaConfirmadaPorLinha.putIfAbsent(lp.getCodigoLinha(), parada);
                        if (lp.getVeiculos() != null && !lp.getVeiculos().isEmpty()) {
                            previsaoPorLinha.putIfAbsent(
                                    lp.getCodigoLinha(),
                                    lp.getVeiculos().get(0).getHorarioPrevistoChegada()
                            );
                        }
                        if (codigosVistos.add(lp.getCodigoLinha())) {
                            linhasExtraidas.add(converterLinhaPrevisaoParaResumo(lp));
                        }
                    }
                }
            } catch (Exception ex) {
                log.debug("Nao foi possivel extrair linhas da parada {}: {}", parada.getCodigoParada(), ex.getMessage());
            }
        }
        return linhasExtraidas;
    }

    private OlhoVivoDTO.LinhaResumo converterLinhaPrevisaoParaResumo(OlhoVivoDTO.LinhaPrevisao lp) {
        String letreiro = lp.getLetreiroCompleto() != null ? lp.getLetreiroCompleto() : "";
        String ltNum = letreiro;
        int modo = 10;
        if (letreiro.contains("-")) {
            String[] partes = letreiro.split("-", 2);
            ltNum = partes[0];
            try {
                modo = Integer.parseInt(partes[1]);
            } catch (NumberFormatException ignored) {
                modo = 10;
            }
        }
        int sentido = lp.getSentido() != null ? lp.getSentido() : 1;
        // Em LinhaPrevisao, lt0 e o Destino e lt1 e a Origem
        String tp = sentido == 1
                ? (lp.getOrigem() != null ? lp.getOrigem() : "")
                : (lp.getDestino() != null ? lp.getDestino() : "");
        String ts = sentido == 1
                ? (lp.getDestino() != null ? lp.getDestino() : "")
                : (lp.getOrigem() != null ? lp.getOrigem() : "");

        return OlhoVivoDTO.LinhaResumo.builder()
                .codigoLinha(lp.getCodigoLinha())
                .circular(false)
                .letreiroNumerico(ltNum)
                .modoAtendimento(modo)
                .sentido(sentido)
                .terminalPrincipal(tp)
                .terminalSecundario(ts)
                .build();
    }

    private List<String> montarListaTermosLocal(OlhoVivoDTO.LocalResolvido local, String termoDigitado) {
        LinkedHashSet<String> termos = new LinkedHashSet<>();
        if (local != null && local.getTermosCandidatosSptrans() != null) {
            for (String c : local.getTermosCandidatosSptrans()) {
                if (c != null && !c.isBlank()) {
                    termos.add(c.trim());
                }
            }
        }
        if (local != null && local.getTermoBuscaSptrans() != null && !local.getTermoBuscaSptrans().isBlank()) {
            termos.add(local.getTermoBuscaSptrans().trim());
        }
        if (termoDigitado != null && !termoDigitado.isBlank()) {
            String limpo = geocodingClient.limparTermoParaSptrans(termoDigitado);
            if (!limpo.isBlank()) {
                termos.add(limpo);
            }
        }
        return new ArrayList<>(termos);
    }

    private String montarNomeExibicaoLocal(OlhoVivoDTO.LocalResolvido local, String fallback) {
        if (local == null) return fallback.toUpperCase();
        if (local.getNomeLocal() != null
                && !local.getNomeLocal().isBlank()
                && !local.getNomeLocal().equalsIgnoreCase("Sua Localização Atual")) {
            return local.getNomeLocal();
        }
        if (local.getLogradouro() != null && !local.getLogradouro().isBlank()) {
            return local.getLogradouro()
                    + (local.getBairro() != null && !local.getBairro().isBlank() ? " (" + local.getBairro() + ")" : "");
        }
        return fallback.toUpperCase();
    }

    private OlhoVivoDTO.PassoRota construirPassoCaminhadaOrigem(
            int ordem,
            String nomeOrigem,
            OlhoVivoDTO.ParadaResumo paradaEmbarque,
            int distMetros,
            int tempoMinutos
    ) {
        String nomePonto = paradaEmbarque != null && paradaEmbarque.getNomeParada() != null && !paradaEmbarque.getNomeParada().isBlank()
                ? paradaEmbarque.getNomeParada() + " (" + paradaEmbarque.getEndereco() + ")"
                : "Ponto de embarque mais proximo";

        String instrucao = distMetros <= 65
                ? "Voce ja esta bem proximo (" + distMetros + "m)! Aguarde no ponto " + nomePonto
                : "Saia de " + nomeOrigem + " e caminhe " + distMetros + " metros (~" + tempoMinutos + " min a pe) ate o ponto " + nomePonto;

        return OlhoVivoDTO.PassoRota.builder()
                .ordem(ordem)
                .tipoPasso("CAMINHADA")
                .local("Partida: " + nomeOrigem)
                .instrucao(instrucao)
                .distanciaCaminhadaMetros(distMetros)
                .tempoCaminhadaMinutos(tempoMinutos)
                .codigoParada(paradaEmbarque != null ? paradaEmbarque.getCodigoParada() : null)
                .nomeParada(paradaEmbarque != null ? paradaEmbarque.getNomeParada() : null)
                .enderecoParada(paradaEmbarque != null ? paradaEmbarque.getEndereco() : null)
                .latitudeParada(paradaEmbarque != null ? paradaEmbarque.getLatitude() : null)
                .longitudeParada(paradaEmbarque != null ? paradaEmbarque.getLongitude() : null)
                .pontoConfirmadoNaParada(false)
                .codigoLinha(0)
                .letreiroCompleto("A PE")
                .descricaoSentido("Caminhada ate o ponto")
                .veiculosCirculando(0)
                .veiculosAcessiveisPcD(0)
                .previsaoChegada("~" + tempoMinutos + " min de caminhada (" + distMetros + "m)")
                .ocorrenciasAbertas(0)
                .build();
    }

    private OlhoVivoDTO.PassoRota construirPassoDesembarqueComCaminhada(
            int ordem,
            OlhoVivoDTO.ParadaResumo paradaDestino,
            String nomeDestino,
            OlhoVivoDTO.LocalResolvido localDestino,
            OlhoVivoDTO.LinhaResumo linha,
            OlhoVivoDTO.PassoRota passoAnterior,
            int distMetros,
            int tempoMinutos
    ) {
        String descricaoParada = paradaDestino != null && paradaDestino.getNomeParada() != null
                ? paradaDestino.getNomeParada() + " (" + paradaDestino.getEndereco() + ")"
                : (localDestino.getLogradouro() != null && !localDestino.getLogradouro().isBlank()
                        ? localDestino.getLogradouro()
                        : nomeDestino);

        String detalheEnderecoDestino = localDestino.getLogradouro() != null && !localDestino.getLogradouro().isBlank()
                ? " (" + localDestino.getLogradouro()
                        + (localDestino.getBairro() != null && !localDestino.getBairro().isBlank() ? " - " + localDestino.getBairro() : "")
                        + ")"
                : "";

        String instrucao = "Desembarque no ponto " + descricaoParada
                + " e caminhe " + distMetros + " metros (~" + tempoMinutos + " min a pe) ate "
                + nomeDestino + detalheEnderecoDestino;

        return OlhoVivoDTO.PassoRota.builder()
                .ordem(ordem)
                .tipoPasso("DESEMBARQUE")
                .local("Destino Final: " + nomeDestino)
                .instrucao(instrucao)
                .distanciaCaminhadaMetros(distMetros)
                .tempoCaminhadaMinutos(tempoMinutos)
                .codigoParada(paradaDestino != null ? paradaDestino.getCodigoParada() : null)
                .nomeParada(paradaDestino != null ? paradaDestino.getNomeParada() : null)
                .enderecoParada(paradaDestino != null ? paradaDestino.getEndereco() : null)
                .latitudeParada(paradaDestino != null ? paradaDestino.getLatitude() : localDestino.getLatitude())
                .longitudeParada(paradaDestino != null ? paradaDestino.getLongitude() : localDestino.getLongitude())
                .pontoConfirmadoNaParada(false)
                .codigoLinha(linha.getCodigoLinha())
                .letreiroCompleto(montarLetreiroCompleto(linha))
                .descricaoSentido(formatarSentido(linha))
                .veiculosCirculando(passoAnterior.getVeiculosCirculando())
                .veiculosAcessiveisPcD(passoAnterior.getVeiculosAcessiveisPcD())
                .previsaoChegada("+" + tempoMinutos + " min a pe (" + distMetros + "m) ate " + nomeDestino)
                .ocorrenciasAbertas(0)
                .build();
    }

    private List<OlhoVivoDTO.LinhaResumo> combinarLinhasDeMultiplosTermos(
            List<OlhoVivoDTO.LinhaResumo> iniciais,
            List<String> termosExtras
    ) {
        Map<Integer, OlhoVivoDTO.LinhaResumo> mapa = new LinkedHashMap<>();
        if (iniciais != null) {
            for (OlhoVivoDTO.LinhaResumo l : iniciais) {
                if (l.getCodigoLinha() != null) {
                    mapa.putIfAbsent(l.getCodigoLinha(), l);
                }
            }
        }
        if (termosExtras != null) {
            for (String termo : termosExtras) {
                if (mapa.size() >= 18) break;
                if (termo == null || termo.isBlank()) continue;
                String chave = extrairPalavraChave(termo);
                if (chave.length() < 3) continue;
                try {
                    List<OlhoVivoDTO.LinhaResumo> encontradas = buscarLinhasComFallback(chave);
                    for (OlhoVivoDTO.LinhaResumo l : encontradas) {
                        if (l.getCodigoLinha() != null) {
                            mapa.putIfAbsent(l.getCodigoLinha(), l);
                        }
                    }
                } catch (Exception ex) {
                    log.debug("Termo extra de linha '{}' sem resultados: {}", chave, ex.getMessage());
                }
            }
        }
        return new ArrayList<>(mapa.values());
    }

    private List<OlhoVivoDTO.ParadaResumo> combinarParadasOrdenadasPorProximidade(
            Double latAlvo,
            Double lonAlvo,
            List<String> termos
    ) {
        Map<Long, OlhoVivoDTO.ParadaResumo> mapa = new LinkedHashMap<>();
        if (termos != null) {
            for (String termo : termos) {
                if (termo == null || termo.isBlank()) continue;
                String chave = extrairPalavraChave(termo);
                if (chave.length() < 3) continue;
                try {
                    List<OlhoVivoDTO.ParadaResumo> encontradas = buscarParadasComFallback(chave);
                    for (OlhoVivoDTO.ParadaResumo p : encontradas) {
                        if (p.getCodigoParada() != null) {
                            mapa.putIfAbsent(p.getCodigoParada(), p);
                        }
                    }
                } catch (Exception ex) {
                    log.debug("Termo extra de parada '{}' sem resultados: {}", chave, ex.getMessage());
                }
                if (mapa.size() >= 25) break;
            }
        }

        List<OlhoVivoDTO.ParadaResumo> lista = new ArrayList<>(mapa.values());
        if (latAlvo != null && lonAlvo != null && latAlvo != 0.0 && lonAlvo != 0.0) {
            lista.sort(Comparator.comparingDouble(
                    p -> GeocodingClient.calcularDistanciaMetros(
                            latAlvo,
                            lonAlvo,
                            p.getLatitude() != null ? p.getLatitude() : 0.0,
                            p.getLongitude() != null ? p.getLongitude() : 0.0
                    )
            ));
        }
        return lista;
    }

    private int calcularDistanciaSegura(
            Double lat,
            Double lon,
            OlhoVivoDTO.ParadaResumo parada,
            int valorPadraoMetros
    ) {
        if (lat == null || lon == null || parada == null
                || parada.getLatitude() == null || parada.getLongitude() == null) {
            return valorPadraoMetros;
        }
        double d = GeocodingClient.calcularDistanciaMetros(lat, lon, parada.getLatitude(), parada.getLongitude());
        if (d <= 0 || d > 8000) {
            return valorPadraoMetros;
        }
        return (int) Math.round(d);
    }

    private OlhoVivoDTO.PassoRota construirPassoOnibus(
            int ordem,
            String tipoPasso,
            String local,
            String instrucao,
            OlhoVivoDTO.LinhaResumo linha,
            String previsaoParada,
            OlhoVivoDTO.ParadaResumo paradaAssociada,
            boolean pontoConfirmado
    ) {
        int circulando = 0;
        int acessiveis = 0;
        String proximoPrefixo = null;
        Double latVeiculo = null;
        Double lonVeiculo = null;

        try {
            OlhoVivoDTO.PosicaoLinhaResponse pos = buscarVeiculosPorLinha(linha.getCodigoLinha());
            if (pos != null && pos.getVeiculos() != null) {
                circulando = pos.getVeiculos().size();
                for (OlhoVivoDTO.VeiculoPosicao v : pos.getVeiculos()) {
                    if (Boolean.TRUE.equals(v.getAcessivelPcD())) {
                        acessiveis++;
                        if (proximoPrefixo == null) {
                            proximoPrefixo = v.getPrefixo();
                            latVeiculo = v.getLatitude();
                            lonVeiculo = v.getLongitude();
                        }
                    }
                }
                if (proximoPrefixo == null && !pos.getVeiculos().isEmpty()) {
                    OlhoVivoDTO.VeiculoPosicao primeiro = pos.getVeiculos().get(0);
                    proximoPrefixo = primeiro.getPrefixo();
                    latVeiculo = primeiro.getLatitude();
                    lonVeiculo = primeiro.getLongitude();
                }
            }
        } catch (Exception ex) {
            log.debug("Falha ao buscar posicao da linha {}: {}", linha.getCodigoLinha(), ex.getMessage());
        }

        List<Ocorrencia> ocorrenciasAbertas = ocorrenciaRepository.findByCodigoLinhaAndStatusNot(
                linha.getCodigoLinha(),
                StatusOcorrencia.RESOLVIDA
        );
        String alertaComunidade = null;
        if (!ocorrenciasAbertas.isEmpty()) {
            Ocorrencia maisRecente = ocorrenciasAbertas.get(0);
            alertaComunidade = "Atencao: " + maisRecente.getTitulo() + " (" + maisRecente.getTipo().name() + ")";
        }

        String previsaoTexto;
        if (previsaoParada != null && !previsaoParada.isBlank()) {
            previsaoTexto = "Chegada prevista no ponto as " + previsaoParada;
        } else if (circulando > 0) {
            int intervaloEstimado = Math.max(4, Math.min(15, 25 / circulando));
            previsaoTexto = "Aprox. " + intervaloEstimado + " min (" + circulando + " onibus na rua agora)";
        } else {
            previsaoTexto = "Consulte horarios de partida do terminal";
        }

        return OlhoVivoDTO.PassoRota.builder()
                .ordem(ordem)
                .tipoPasso(tipoPasso)
                .local(local)
                .codigoParada(paradaAssociada != null ? paradaAssociada.getCodigoParada() : null)
                .nomeParada(paradaAssociada != null ? paradaAssociada.getNomeParada() : null)
                .enderecoParada(paradaAssociada != null ? paradaAssociada.getEndereco() : null)
                .latitudeParada(paradaAssociada != null ? paradaAssociada.getLatitude() : null)
                .longitudeParada(paradaAssociada != null ? paradaAssociada.getLongitude() : null)
                .pontoConfirmadoNaParada(pontoConfirmado)
                .instrucao(instrucao)
                .codigoLinha(linha.getCodigoLinha())
                .letreiroCompleto(montarLetreiroCompleto(linha))
                .descricaoSentido(formatarSentido(linha))
                .veiculosCirculando(circulando)
                .veiculosAcessiveisPcD(acessiveis)
                .proximoPrefixo(proximoPrefixo)
                .latitudeProximoVeiculo(latVeiculo)
                .longitudeProximoVeiculo(lonVeiculo)
                .previsaoChegada(previsaoTexto)
                .ocorrenciasAbertas(ocorrenciasAbertas.size())
                .alertaComunidade(alertaComunidade)
                .build();
    }

    private List<ParBaldeacao> encontrarMelhoresBaldeacoes(
            List<OlhoVivoDTO.LinhaResumo> linhasOrigem,
            List<OlhoVivoDTO.LinhaResumo> linhasDestino,
            Set<Integer> codigosDiretos,
            String chaveOrigemNorm
    ) {
        List<ParBaldeacao> resultado = new ArrayList<>();
        Set<String> combinacoesUsadas = new HashSet<>();

        for (OlhoVivoDTO.LinhaResumo l1 : linhasOrigem) {
            if (codigosDiretos.contains(l1.getCodigoLinha())) continue;
            for (OlhoVivoDTO.LinhaResumo l2 : linhasDestino) {
                if (codigosDiretos.contains(l2.getCodigoLinha())) continue;
                if (Objects.equals(l1.getCodigoLinha(), l2.getCodigoLinha())) continue;

                String terminalComum = descobrirTerminalComum(l1, l2);
                if (terminalComum != null) {
                    String chavePar = montarLetreiroCompleto(l1) + "->" + montarLetreiroCompleto(l2);
                    if (combinacoesUsadas.add(chavePar)) {
                        resultado.add(new ParBaldeacao(l1, l2, terminalComum));
                        if (resultado.size() >= 2) return resultado;
                    }
                }
            }
        }

        for (OlhoVivoDTO.LinhaResumo l1 : linhasOrigem) {
            if (codigosDiretos.contains(l1.getCodigoLinha())) continue;
            String pontoIntegracao = escolherTerminalDeConexao(l1, chaveOrigemNorm);
            for (OlhoVivoDTO.LinhaResumo l2 : linhasDestino) {
                if (codigosDiretos.contains(l2.getCodigoLinha())) continue;
                if (Objects.equals(l1.getCodigoLinha(), l2.getCodigoLinha())) continue;

                String chavePar = montarLetreiroCompleto(l1) + "->" + montarLetreiroCompleto(l2);
                if (combinacoesUsadas.add(chavePar)) {
                    resultado.add(new ParBaldeacao(l1, l2, pontoIntegracao));
                    if (resultado.size() >= 2) return resultado;
                }
            }
        }

        return resultado;
    }

    private String descobrirTerminalComum(OlhoVivoDTO.LinhaResumo l1, OlhoVivoDTO.LinhaResumo l2) {
        String tp1 = normalizarTexto(l1.getTerminalPrincipal());
        String ts1 = normalizarTexto(l1.getTerminalSecundario());
        String tp2 = normalizarTexto(l2.getTerminalPrincipal());
        String ts2 = normalizarTexto(l2.getTerminalSecundario());

        if (!tp1.isEmpty() && (tp1.equals(tp2) || tp1.equals(ts2))) {
            return l1.getTerminalPrincipal();
        }
        if (!ts1.isEmpty() && (ts1.equals(tp2) || ts1.equals(ts2))) {
            return l1.getTerminalSecundario();
        }
        return null;
    }

    private String escolherTerminalDeConexao(OlhoVivoDTO.LinhaResumo l1, String chaveOrigemNorm) {
        String tp = l1.getTerminalPrincipal() != null ? l1.getTerminalPrincipal() : "";
        String ts = l1.getTerminalSecundario() != null ? l1.getTerminalSecundario() : "";
        if (!chaveOrigemNorm.isEmpty() && normalizarTexto(tp).contains(chaveOrigemNorm) && !ts.isBlank()) {
            return "Terminal / Ponto " + ts;
        }
        if (!ts.isBlank()) {
            return "Terminal / Ponto " + ts;
        }
        if (!tp.isBlank()) {
            return "Terminal / Ponto " + tp;
        }
        return "Ponto de Integracao Central";
    }

    private List<OlhoVivoDTO.LinhaResumo> buscarLinhasComFallback(String termo) {
        List<OlhoVivoDTO.LinhaResumo> lista = buscarLinhas(termo);
        if (lista.isEmpty()) {
            String palavraChave = extrairPalavraChave(termo);
            if (!palavraChave.equalsIgnoreCase(termo)) {
                lista = buscarLinhas(palavraChave);
            }
        }
        return lista;
    }

    private List<OlhoVivoDTO.ParadaResumo> buscarParadasComFallback(String termo) {
        List<OlhoVivoDTO.ParadaResumo> lista = buscarParadas(termo);
        if (lista.isEmpty()) {
            String palavraChave = extrairPalavraChave(termo);
            if (!palavraChave.equalsIgnoreCase(termo)) {
                lista = buscarParadas(palavraChave);
            }
        }
        return lista;
    }

    private String extrairPalavraChave(String termo) {
        if (termo == null) return "";
        return geocodingClient.limparTermoParaSptrans(termo);
    }

    private boolean contemTermoNoTrajeto(OlhoVivoDTO.LinhaResumo linha, String termoNormalizado) {
        if (termoNormalizado == null || termoNormalizado.isEmpty()) return false;
        String tp = normalizarTexto(linha.getTerminalPrincipal());
        String ts = normalizarTexto(linha.getTerminalSecundario());
        String lt = normalizarTexto(linha.getLetreiroNumerico());
        return tp.contains(termoNormalizado) || ts.contains(termoNormalizado) || lt.contains(termoNormalizado);
    }

    private String normalizarTexto(String texto) {
        if (texto == null) return "";
        String semAcento = Normalizer.normalize(texto, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        return semAcento.toUpperCase(Locale.ROOT).trim();
    }

    private String montarLetreiroCompleto(OlhoVivoDTO.LinhaResumo linha) {
        String lt = linha.getLetreiroNumerico() != null ? linha.getLetreiroNumerico() : "";
        Integer tl = linha.getModoAtendimento() != null ? linha.getModoAtendimento() : 10;
        return lt + "-" + tl;
    }

    private String formatarSentido(OlhoVivoDTO.LinhaResumo linha) {
        String tp = linha.getTerminalPrincipal() != null ? linha.getTerminalPrincipal() : "";
        String ts = linha.getTerminalSecundario() != null ? linha.getTerminalSecundario() : "";
        if (Integer.valueOf(1).equals(linha.getSentido())) {
            return tp + " ➔ " + ts;
        }
        return ts + " ➔ " + tp;
    }

    private record ParBaldeacao(
            OlhoVivoDTO.LinhaResumo linha1,
            OlhoVivoDTO.LinhaResumo linha2,
            String pontoConexao
    ) {}

    @CacheEvict(value = {CacheConfig.CACHE_LINHAS_SPTRANS, CacheConfig.CACHE_PARADAS_SPTRANS}, allEntries = true)
    public void limparCacheSptrans() {
        // Limpa cache das consultas estaticas de linhas e paradas da SPTrans
    }
}
