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

    private volatile OlhoVivoDTO.PosicaoGeralResponse cachePosicaoGeral;
    private volatile long timestampCachePosicaoGeralMs = 0L;

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
            List<OlhoVivoDTO.ParadaResumo> porTermo = buscarParadasComFallback(termo);
            if (!porTermo.isEmpty()) {
                return porTermo;
            }
        }
        return construirParadasPadraoCorredor(termo);
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

        // 4. Inspecionar Previsoes nas Paradas SPTrans + Radar Espacial (/Posicao) nas Paradas Reais de Rua!
        // Isso descobre TODAS as linhas que passam fisicamente ao redor do cidadao, mesmo fora de corredores exclusivos.
        Map<Integer, String> previsaoPorLinhaNaOrigem = new HashMap<>();
        Map<Integer, OlhoVivoDTO.ParadaResumo> paradaConfirmadaOrigemPorLinha = new HashMap<>();
        List<OlhoVivoDTO.LinhaResumo> linhasExtraidasParadasOrigem = extrairLinhasDasParadas(
                paradasOrigem,
                localOrigem.getLatitude(),
                localOrigem.getLongitude(),
                previsaoPorLinhaNaOrigem,
                paradaConfirmadaOrigemPorLinha
        );
        enriquecerLinhasComRadarEspacial(
                localOrigem.getLatitude(),
                localOrigem.getLongitude(),
                650.0,
                paradasOrigem,
                linhasExtraidasParadasOrigem,
                paradaConfirmadaOrigemPorLinha
        );

        Map<Integer, String> previsaoPorLinhaNoDestino = new HashMap<>();
        Map<Integer, OlhoVivoDTO.ParadaResumo> paradaConfirmadaDestinoPorLinha = new HashMap<>();
        List<OlhoVivoDTO.LinhaResumo> linhasExtraidasParadasDestino = extrairLinhasDasParadas(
                paradasDestino,
                localDestino.getLatitude(),
                localDestino.getLongitude(),
                previsaoPorLinhaNoDestino,
                paradaConfirmadaDestinoPorLinha
        );
        enriquecerLinhasComRadarEspacial(
                localDestino.getLatitude(),
                localDestino.getLongitude(),
                850.0,
                paradasDestino,
                linhasExtraidasParadasDestino,
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

        // 6. Identificar Linhas Diretas com foco em ponto confirmado e sentido correto
        Map<Integer, OlhoVivoDTO.LinhaResumo> diretasMap = new LinkedHashMap<>();
        Set<Integer> codigosOrigem = new HashSet<>();
        Set<Integer> codigosDestino = new HashSet<>();
        for (OlhoVivoDTO.LinhaResumo lo : linhasOrigem) {
            if (lo.getCodigoLinha() != null) codigosOrigem.add(lo.getCodigoLinha());
        }
        for (OlhoVivoDTO.LinhaResumo ld : linhasDestino) {
            if (ld.getCodigoLinha() != null) codigosDestino.add(ld.getCodigoLinha());
        }

        // Nivel 1: Mesmo codigoLinha confirmado fisicamente em parada proxima da origem E do destino
        for (OlhoVivoDTO.LinhaResumo lo : linhasOrigem) {
            Integer cod = lo.getCodigoLinha();
            if (cod != null
                    && paradaConfirmadaOrigemPorLinha.containsKey(cod)
                    && paradaConfirmadaDestinoPorLinha.containsKey(cod)) {
                diretasMap.putIfAbsent(cod, lo);
            }
        }

        // Nivel 2: Linha confirmada em parada proxima da origem que vai para o destino (no sentido correto)
        for (OlhoVivoDTO.LinhaResumo lo : linhasOrigem) {
            Integer cod = lo.getCodigoLinha();
            if (cod == null || !paradaConfirmadaOrigemPorLinha.containsKey(cod)) continue;
            if (codigosDestino.contains(cod) || destinoLinhaCombinaCom(lo, termosBuscaDestino)) {
                diretasMap.putIfAbsent(cod, lo);
            }
        }

        // Nivel 3: Mesma linha presente nas listas de origem e destino ou correspondencia por trajeto
        for (OlhoVivoDTO.LinhaResumo lo : linhasOrigem) {
            Integer cod = lo.getCodigoLinha();
            if (cod == null) continue;
            if (codigosDestino.contains(cod) || destinoLinhaCombinaCom(lo, termosBuscaDestino)) {
                diretasMap.putIfAbsent(cod, lo);
            }
        }
        for (OlhoVivoDTO.LinhaResumo ld : linhasDestino) {
            Integer cod = ld.getCodigoLinha();
            if (cod == null) continue;
            if (codigosOrigem.contains(cod)) {
                diretasMap.putIfAbsent(cod, ld);
            } else if (paradaConfirmadaDestinoPorLinha.containsKey(cod) || diretasMap.isEmpty()) {
                for (String to : termosBuscaOrigem) {
                    if (contemTermoNoTrajeto(ld, normalizarTexto(to))) {
                        diretasMap.putIfAbsent(cod, ld);
                        break;
                    }
                }
            }
        }

        String nomeExibicaoOrigem = montarNomeExibicaoLocal(localOrigem, usandoGps ? "Sua Localizacao" : termoOrigem);
        String nomeExibicaoDestino = montarNomeExibicaoLocal(localDestino, termoDestino);

        // Avaliar TODAS as candidatas diretas (limitando a 10 para proteger latencia) e ordenar pela MENOR caminhada antes de selecionar as top 2
        final OlhoVivoDTO.ParadaResumo paradaOrigemBase = paradaOrigemSugerida;
        final int distOrigemBase = distCaminhadaOrigem;
        List<OlhoVivoDTO.LinhaResumo> candidatasOrdenadas = new ArrayList<>(diretasMap.values());
        candidatasOrdenadas.sort(Comparator
                .comparingInt((OlhoVivoDTO.LinhaResumo l) ->
                        (paradaConfirmadaOrigemPorLinha.containsKey(l.getCodigoLinha())
                                && paradaConfirmadaDestinoPorLinha.containsKey(l.getCodigoLinha())) ? 0
                                : (paradaConfirmadaOrigemPorLinha.containsKey(l.getCodigoLinha()) ? 1 : 2))
                .thenComparingInt(l -> calcularDistanciaSegura(
                        localOrigem.getLatitude(),
                        localOrigem.getLongitude(),
                        paradaConfirmadaOrigemPorLinha.getOrDefault(l.getCodigoLinha(), paradaOrigemBase),
                        distOrigemBase
                )));

        List<OlhoVivoDTO.LinhaResumo> todasLinhasConhecidas = new ArrayList<>();
        todasLinhasConhecidas.addAll(linhasOrigem);
        todasLinhasConhecidas.addAll(linhasDestino);

        List<OlhoVivoDTO.OpcaoRota> candidatasDiretas = new ArrayList<>();
        Set<String> letreirosDiretosVistos = new HashSet<>();
        int inspecionadasDiretas = 0;
        for (OlhoVivoDTO.LinhaResumo linhaBruta : candidatasOrdenadas) {
            if (inspecionadasDiretas >= 10) break;
            String letreiroUnico = montarLetreiroCompleto(linhaBruta);
            if (!letreirosDiretosVistos.add(letreiroUnico)) continue;
            inspecionadasDiretas++;

            OlhoVivoDTO.LinhaResumo linhaDireta = orientarLinhaNoSentidoDaViagem(
                    linhaBruta,
                    todasLinhasConhecidas,
                    termosBuscaOrigem,
                    termosBuscaDestino,
                    localOrigem.getLatitude(),
                    localOrigem.getLongitude(),
                    localDestino.getLatitude(),
                    localDestino.getLongitude()
            );

            OlhoVivoDTO.ParadaResumo paradaEmbarque = paradaConfirmadaOrigemPorLinha.getOrDefault(
                    linhaDireta.getCodigoLinha(),
                    paradaConfirmadaOrigemPorLinha.getOrDefault(linhaBruta.getCodigoLinha(), paradaOrigemSugerida)
            );
            OlhoVivoDTO.ParadaResumo paradaDesembarque = paradaConfirmadaDestinoPorLinha.getOrDefault(
                    linhaDireta.getCodigoLinha(),
                    paradaConfirmadaDestinoPorLinha.getOrDefault(linhaBruta.getCodigoLinha(), paradaDestinoSugerida)
            );
            boolean confirmadoNoPonto = paradaConfirmadaOrigemPorLinha.containsKey(linhaDireta.getCodigoLinha())
                    || paradaConfirmadaOrigemPorLinha.containsKey(linhaBruta.getCodigoLinha());

            int distAteEstePonto = calcularDistanciaSegura(
                    localOrigem.getLatitude(),
                    localOrigem.getLongitude(),
                    paradaEmbarque,
                    distCaminhadaOrigem
            );
            int minCaminhadaOrigem = Math.max(1, (int) Math.ceil(distAteEstePonto / 75.0));

            int distDoDesembarqueAteDestino = calcularDistanciaSegura(
                    localDestino.getLatitude(),
                    localDestino.getLongitude(),
                    paradaDesembarque,
                    distCaminhadaDestino
            );
            int minCaminhadaDestino = Math.max(1, (int) Math.ceil(distDoDesembarqueAteDestino / 75.0));

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
                    distDoDesembarqueAteDestino,
                    minCaminhadaDestino
            );

            boolean acessivel = passoOnibus.getVeiculosAcessiveisPcD() != null && passoOnibus.getVeiculosAcessiveisPcD() > 0;
            int tempoEstimado = (acessivel ? 26 : 28) + minCaminhadaOrigem + minCaminhadaDestino;

            candidatasDiretas.add(OlhoVivoDTO.OpcaoRota.builder()
                    .titulo("Linha Direta • " + montarLetreiroCompleto(linhaDireta))
                    .tipoRota("DIRETA")
                    .recomendada(false)
                    .motivoRecomendacao("Caminhe " + distAteEstePonto + "m ate o ponto e pegue 1 unico onibus ate " + nomeExibicaoDestino)
                    .totalOnibusNecessarios(1)
                    .tempoEstimadoMinutos(tempoEstimado)
                    .totalmenteAcessivelPcD(acessivel)
                    .totalOcorrenciasNoTrajeto(passoOnibus.getOcorrenciasAbertas())
                    .passos(List.of(passoCaminhadaInicial, passoOnibus, passoDesembarqueCaminhada))
                    .build());
        }

        candidatasDiretas.sort(Comparator
                .comparingInt((OlhoVivoDTO.OpcaoRota o) -> temPontoConfirmado(o) ? 0 : 1)
                .thenComparingInt(o -> temOnibusAtivoNaRota(o) ? 0 : 1)
                .thenComparingInt(o -> extrairCaminhadaEmbarque(o) / 100)
                .thenComparingInt(o -> extrairCaminhadaTotal(o) / 100)
                .thenComparingInt(o -> o.getTotalOcorrenciasNoTrajeto() != null ? o.getTotalOcorrenciasNoTrajeto() : 0)
                .thenComparingInt(OlhoVivoDTO.OpcaoRota::getTempoEstimadoMinutos)
                .thenComparingInt(o -> Boolean.TRUE.equals(o.getTotalmenteAcessivelPcD()) ? 0 : 1));

        List<OlhoVivoDTO.OpcaoRota> opcoes = new ArrayList<>();
        for (int i = 0; i < Math.min(2, candidatasDiretas.size()); i++) {
            opcoes.add(candidatasDiretas.get(i));
        }

        // 7. Identificar Opcoes com Baldeacao (2 onibus: Linha confirmada no Ponto de Origem -> Conexao no Caminho -> Destino)
        String chaveOrigemPrincipal = termosBuscaOrigem.isEmpty() ? "" : normalizarTexto(termosBuscaOrigem.get(0));
        List<ParBaldeacao> paresBaldeacao = encontrarMelhoresBaldeacoes(
                linhasOrigem,
                linhasDestino,
                diretasMap.keySet(),
                paradaConfirmadaOrigemPorLinha.keySet(),
                paradaConfirmadaDestinoPorLinha.keySet(),
                chaveOrigemPrincipal,
                localOrigem.getLatitude(),
                localOrigem.getLongitude(),
                localDestino.getLatitude(),
                localDestino.getLongitude()
        );

        int countBaldeacao = 0;
        for (ParBaldeacao par : paresBaldeacao) {
            if (countBaldeacao >= 2 || opcoes.size() >= 4) break;

            double[] coordConexao = estimarCoordenadaTerminalConexao(par.pontoConexao);
            Double latConexao = coordConexao != null ? coordConexao[0] : null;
            Double lonConexao = coordConexao != null ? coordConexao[1] : null;

            OlhoVivoDTO.LinhaResumo linha1Orientada = orientarLinhaNoSentidoDaViagem(
                    par.linha1,
                    todasLinhasConhecidas,
                    termosBuscaOrigem,
                    List.of(par.pontoConexao),
                    localOrigem.getLatitude(),
                    localOrigem.getLongitude(),
                    latConexao,
                    lonConexao
            );
            OlhoVivoDTO.LinhaResumo linha2Orientada = orientarLinhaNoSentidoDaViagem(
                    par.linha2,
                    todasLinhasConhecidas,
                    List.of(par.pontoConexao),
                    termosBuscaDestino,
                    latConexao,
                    lonConexao,
                    localDestino.getLatitude(),
                    localDestino.getLongitude()
            );

            OlhoVivoDTO.ParadaResumo paradaEmbarque1 = paradaConfirmadaOrigemPorLinha.getOrDefault(
                    linha1Orientada.getCodigoLinha(),
                    paradaConfirmadaOrigemPorLinha.getOrDefault(par.linha1.getCodigoLinha(), paradaOrigemSugerida)
            );
            OlhoVivoDTO.ParadaResumo paradaDesembarque2 = paradaConfirmadaDestinoPorLinha.getOrDefault(
                    linha2Orientada.getCodigoLinha(),
                    paradaConfirmadaDestinoPorLinha.getOrDefault(par.linha2.getCodigoLinha(), paradaDestinoSugerida)
            );
            boolean confirmadoNoPonto1 = paradaConfirmadaOrigemPorLinha.containsKey(linha1Orientada.getCodigoLinha())
                    || paradaConfirmadaOrigemPorLinha.containsKey(par.linha1.getCodigoLinha());

            int distAteEstePonto = calcularDistanciaSegura(
                    localOrigem.getLatitude(),
                    localOrigem.getLongitude(),
                    paradaEmbarque1,
                    distCaminhadaOrigem
            );
            int minCaminhadaOrigem = Math.max(1, (int) Math.ceil(distAteEstePonto / 75.0));

            int distDoDesembarqueAteDestino = calcularDistanciaSegura(
                    localDestino.getLatitude(),
                    localDestino.getLongitude(),
                    paradaDesembarque2,
                    distCaminhadaDestino
            );
            int minCaminhadaDestino = Math.max(1, (int) Math.ceil(distDoDesembarqueAteDestino / 75.0));

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
                    "1º Onibus: Embarque na linha " + montarLetreiroCompleto(linha1Orientada)
                            + " (" + formatarSentido(linha1Orientada) + ") ate " + par.pontoConexao,
                    linha1Orientada,
                    previsaoPorLinhaNaOrigem.getOrDefault(
                            linha1Orientada.getCodigoLinha(),
                            previsaoPorLinhaNaOrigem.get(par.linha1.getCodigoLinha())),
                    paradaEmbarque1,
                    confirmadoNoPonto1
            );

            OlhoVivoDTO.PassoRota passo2 = construirPassoOnibus(
                    3,
                    "BALDEACAO",
                    "Ponto de Baldeacao: " + par.pontoConexao,
                    "2º Onibus (Baldeacao): Desca em " + par.pontoConexao
                            + ", caminhe ate a parada da linha " + montarLetreiroCompleto(linha2Orientada)
                            + " (" + formatarSentido(linha2Orientada) + ") e embarque rumo a " + nomeExibicaoDestino,
                    linha2Orientada,
                    previsaoPorLinhaNoDestino.getOrDefault(
                            linha2Orientada.getCodigoLinha(),
                            previsaoPorLinhaNoDestino.get(par.linha2.getCodigoLinha())),
                    null,
                    false
            );

            OlhoVivoDTO.PassoRota passo3 = construirPassoDesembarqueComCaminhada(
                    4,
                    paradaDesembarque2,
                    nomeExibicaoDestino,
                    localDestino,
                    linha2Orientada,
                    passo2,
                    distDoDesembarqueAteDestino,
                    minCaminhadaDestino
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
                    .tempoEstimadoMinutos(36 + minCaminhadaOrigem + minCaminhadaDestino + (countBaldeacao * 6))
                    .totalmenteAcessivelPcD(ambosAcessiveis)
                    .totalOcorrenciasNoTrajeto(totalOcorrencias)
                    .passos(List.of(passoCaminhadaInicial, passo1, passo2, passo3))
                    .build());
            countBaldeacao++;
        }

        // Ordenar e eleger a Melhor Opcao Recomendada priorizando caminhada curta, ponto confirmado e frota em operacao
        if (!opcoes.isEmpty()) {
            opcoes.sort(Comparator
                    .comparingInt((OlhoVivoDTO.OpcaoRota o) -> (extrairCaminhadaEmbarque(o) <= 600 && extrairCaminhadaTotal(o) <= 1100) ? 0 : 1)
                    .thenComparingInt(o -> (temPontoConfirmado(o) && temOnibusAtivoNaRota(o)) ? 0 : 1)
                    .thenComparingInt(o -> temPontoConfirmado(o) ? 0 : 1)
                    .thenComparingInt(o -> temOnibusAtivoNaRota(o) ? 0 : 1)
                    .thenComparingInt(o -> "DIRETA".equals(o.getTipoRota()) ? 0 : 1)
                    .thenComparingInt(o -> extrairCaminhadaTotal(o) / 100)
                    .thenComparingInt(o -> o.getTotalOcorrenciasNoTrajeto() != null ? o.getTotalOcorrenciasNoTrajeto() : 0)
                    .thenComparingInt(o -> Boolean.TRUE.equals(o.getTotalmenteAcessivelPcD()) ? 0 : 1)
                    .thenComparingInt(OlhoVivoDTO.OpcaoRota::getTempoEstimadoMinutos));

            OlhoVivoDTO.OpcaoRota melhor = opcoes.get(0);
            melhor.setRecomendada(true);
            int caminMelhor = extrairCaminhadaEmbarque(melhor);
            String descCaminhada = caminMelhor <= 600
                    ? "caminhada curta (" + caminMelhor + "m)"
                    : "caminhada de " + caminMelhor + "m ate o ponto";
            if ("DIRETA".equals(melhor.getTipoRota())) {
                melhor.setMotivoRecomendacao(
                        Boolean.TRUE.equals(melhor.getTotalmenteAcessivelPcD())
                                ? "Melhor rota para " + nomeExibicaoDestino + ": " + descCaminhada + ", 1 unico onibus e frota PcD ativa!"
                                : "Melhor rota para " + nomeExibicaoDestino + ": " + descCaminhada + " e 1 unico onibus direto."
                );
            } else {
                melhor.setMotivoRecomendacao(
                        "Melhor rota para " + nomeExibicaoDestino + ": " + descCaminhada + " + baldeacao rapida."
                );
            }

            // Sincronizar o resumo do cabecalho com a parada de embarque da melhor rota recomendada
            OlhoVivoDTO.PassoRota passoCaminhadaMelhor = melhor.getPassos().stream()
                    .filter(p -> "CAMINHADA".equals(p.getTipoPasso()) && p.getCodigoParada() != null)
                    .findFirst()
                    .orElse(null);
            if (passoCaminhadaMelhor != null) {
                if (passoCaminhadaMelhor.getDistanciaCaminhadaMetros() != null) {
                    distCaminhadaOrigem = passoCaminhadaMelhor.getDistanciaCaminhadaMetros();
                }
                if (passoCaminhadaMelhor.getLatitudeParada() != null && passoCaminhadaMelhor.getLongitudeParada() != null) {
                    paradaOrigemSugerida = OlhoVivoDTO.ParadaResumo.builder()
                            .codigoParada(passoCaminhadaMelhor.getCodigoParada())
                            .nomeParada(passoCaminhadaMelhor.getNomeParada())
                            .endereco(passoCaminhadaMelhor.getEnderecoParada())
                            .latitude(passoCaminhadaMelhor.getLatitudeParada())
                            .longitude(passoCaminhadaMelhor.getLongitudeParada())
                            .build();
                }
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
     * Consulta as previsoes das paradas mais proximas (ate 6 paradas num raio de 800m, cobrindo ambos os lados da via)
     * para descobrir quais linhas de onibus realmente passam naquela rua/parada!
     */
    private List<OlhoVivoDTO.LinhaResumo> extrairLinhasDasParadas(
            List<OlhoVivoDTO.ParadaResumo> paradas,
            Double latReferencia,
            Double lonReferencia,
            Map<Integer, String> previsaoPorLinha,
            Map<Integer, OlhoVivoDTO.ParadaResumo> paradaConfirmadaPorLinha
    ) {
        List<OlhoVivoDTO.LinhaResumo> linhasExtraidas = new ArrayList<>();
        Set<Integer> codigosVistos = new HashSet<>();

        int inspecionadas = 0;
        for (int i = 0; i < paradas.size() && inspecionadas < 6; i++) {
            OlhoVivoDTO.ParadaResumo parada = paradas.get(i);
            if (parada == null || parada.getCodigoParada() == null) continue;

            if (latReferencia != null && lonReferencia != null
                    && parada.getLatitude() != null && parada.getLongitude() != null) {
                double dist = GeocodingClient.calcularDistanciaMetros(
                        latReferencia, lonReferencia, parada.getLatitude(), parada.getLongitude());
                // Jamais inspeciona paradas de outros bairros (> 1200m), evitando contaminar a origem com pontos como Angelica C/B!
                if (dist > 1200.0) {
                    break;
                }
                if (inspecionadas >= 4 && dist > 800.0) {
                    break;
                }
            }

            // Paradas sinteticas do OSM (>= 90000000) nao existem no endpoint /Previsao/Parada da SPTrans;
            // suas linhas sao descobertas pelo radar espacial /Posicao.
            if (parada.getCodigoParada() >= 90000000L) continue;

            inspecionadas++;
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
        // Na API Olho Vivo da SPTrans (/Posicao e /Previsao/Parada), lt0 (mapeado em getDestino) e SEMPRE o Terminal Principal (tp)
        // e lt1 (mapeado em getOrigem) e SEMPRE o Terminal Secundario (ts).
        String tp = lp.getDestino() != null ? lp.getDestino() : "";
        String ts = lp.getOrigem() != null ? lp.getOrigem() : "";

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
        boolean temCoordenadaValida = latAlvo != null && lonAlvo != null && latAlvo != 0.0 && lonAlvo != 0.0;

        if (termos != null) {
            int termosConsultados = 0;
            for (String termo : termos) {
                if (termosConsultados >= 8) break;
                if (termo == null || termo.isBlank()) continue;
                String chave = extrairPalavraChave(termo);
                if (chave.length() < 3) continue;
                termosConsultados++;
                try {
                    List<OlhoVivoDTO.ParadaResumo> encontradas = buscarParadasComFallback(chave);
                    for (OlhoVivoDTO.ParadaResumo p : encontradas) {
                        if (p.getCodigoParada() != null
                                && p.getLatitude() != null && p.getLongitude() != null
                                && p.getLatitude() != 0.0 && p.getLongitude() != 0.0) {
                            mapa.putIfAbsent(p.getCodigoParada(), p);
                        }
                    }
                } catch (Exception ex) {
                    log.debug("Termo extra de parada '{}' sem resultados: {}", chave, ex.getMessage());
                }

                // Parada antecipada inteligente: so interrompe antes de termos genericos de distrito
                // se ja tivermos pelo menos 4 paradas reais a <= 450 metros do usuario.
                if (temCoordenadaValida && termosConsultados >= 3) {
                    long paradasMuitoProximas = mapa.values().stream()
                            .filter(p -> GeocodingClient.calcularDistanciaMetros(
                                    latAlvo, lonAlvo, p.getLatitude(), p.getLongitude()) <= 450.0)
                            .count();
                    if (paradasMuitoProximas >= 4) {
                        break;
                    }
                }
            }
        }

        // Complementa com paradas reais de calçada do OpenStreetMap quando a SPTrans tiver poucas paradas de corredor (< 3 a <= 600m)
        if (temCoordenadaValida) {
            long qtdSptransPerto = mapa.values().stream()
                    .filter(p -> GeocodingClient.calcularDistanciaMetros(
                            latAlvo, lonAlvo, p.getLatitude(), p.getLongitude()) <= 600.0)
                    .count();
            if (qtdSptransPerto < 3) {
                try {
                    List<OlhoVivoDTO.ParadaResumo> paradasOsm = geocodingClient.buscarParadasReaisProximas(latAlvo, lonAlvo);
                    if (paradasOsm != null) {
                        for (OlhoVivoDTO.ParadaResumo p : paradasOsm) {
                            if (p.getCodigoParada() != null
                                    && p.getLatitude() != null && p.getLongitude() != null
                                    && p.getLatitude() != 0.0 && p.getLongitude() != 0.0) {
                                mapa.putIfAbsent(p.getCodigoParada(), p);
                            }
                        }
                    }
                } catch (Exception ex) {
                    log.debug("Fallback de paradas OSM indisponivel: {}", ex.getMessage());
                }
            }
        }

        List<OlhoVivoDTO.ParadaResumo> lista = new ArrayList<>(mapa.values());
        if (temCoordenadaValida) {
            lista.sort(Comparator.comparingDouble(
                    p -> GeocodingClient.calcularDistanciaMetros(
                            latAlvo,
                            lonAlvo,
                            p.getLatitude(),
                            p.getLongitude()
                    )
            ));
        }
        return lista;
    }

    private OlhoVivoDTO.PosicaoGeralResponse buscarTodasPosicoesComCache() {
        long agora = System.currentTimeMillis();
        if (cachePosicaoGeral != null && (agora - timestampCachePosicaoGeralMs) < 25_000L) {
            return cachePosicaoGeral;
        }
        try {
            OlhoVivoDTO.PosicaoGeralResponse resp = olhoVivoClient.buscarTodasPosicoes();
            if (resp != null && resp.getLinhas() != null) {
                cachePosicaoGeral = resp;
                timestampCachePosicaoGeralMs = agora;
                return resp;
            }
        } catch (Exception ex) {
            log.debug("Radar geral /Posicao indisponivel no momento: {}", ex.getMessage());
        }
        return cachePosicaoGeral;
    }

    /**
     * Descobre linhas reais da SPTrans com onibus circulando dentro de [raioMetros] da coordenada informada,
     * associando cada linha a parada mais proxima daquele veiculo/usuario.
     */
    private void enriquecerLinhasComRadarEspacial(
            Double latAlvo,
            Double lonAlvo,
            double raioMetros,
            List<OlhoVivoDTO.ParadaResumo> paradasProximas,
            List<OlhoVivoDTO.LinhaResumo> linhasAcumuladas,
            Map<Integer, OlhoVivoDTO.ParadaResumo> paradaConfirmadaPorLinha
    ) {
        if (latAlvo == null || lonAlvo == null || latAlvo == 0.0 || lonAlvo == 0.0) return;
        OlhoVivoDTO.PosicaoGeralResponse geral = buscarTodasPosicoesComCache();
        if (geral == null || geral.getLinhas() == null) return;

        Set<Integer> codigosExistentes = new HashSet<>();
        for (OlhoVivoDTO.LinhaResumo l : linhasAcumuladas) {
            if (l.getCodigoLinha() != null) codigosExistentes.add(l.getCodigoLinha());
        }

        for (OlhoVivoDTO.LinhaPosicaoGeral lp : geral.getLinhas()) {
            if (lp.getCodigoLinha() == null || lp.getVeiculos() == null || lp.getVeiculos().isEmpty()) continue;

            OlhoVivoDTO.VeiculoPosicao veiculoMaisPerto = null;
            double menorDistVeiculo = Double.MAX_VALUE;
            for (OlhoVivoDTO.VeiculoPosicao v : lp.getVeiculos()) {
                if (v.getLatitude() == null || v.getLongitude() == null
                        || v.getLatitude() == 0.0 || v.getLongitude() == 0.0) continue;
                double d = GeocodingClient.calcularDistanciaMetros(latAlvo, lonAlvo, v.getLatitude(), v.getLongitude());
                if (d <= raioMetros && d < menorDistVeiculo) {
                    menorDistVeiculo = d;
                    veiculoMaisPerto = v;
                }
            }
            if (veiculoMaisPerto == null) continue;

            // Associa a parada mais proxima entre as paradas reais encontradas (ou a mais proxima do veiculo dentro do raio caminhavel)
            OlhoVivoDTO.ParadaResumo paradaMaisAdequada = null;
            double menorScore = Double.MAX_VALUE;
            if (paradasProximas != null) {
                for (OlhoVivoDTO.ParadaResumo p : paradasProximas) {
                    if (p.getLatitude() == null || p.getLongitude() == null) continue;
                    double distUsuario = GeocodingClient.calcularDistanciaMetros(latAlvo, lonAlvo, p.getLatitude(), p.getLongitude());
                    if (distUsuario > raioMetros + 250.0) continue;
                    double distVeiculo = GeocodingClient.calcularDistanciaMetros(
                            veiculoMaisPerto.getLatitude(), veiculoMaisPerto.getLongitude(), p.getLatitude(), p.getLongitude());
                    double score = distUsuario + (0.6 * distVeiculo);
                    if (score < menorScore) {
                        menorScore = score;
                        paradaMaisAdequada = p;
                    }
                }
            }

            if (paradaMaisAdequada != null) {
                paradaConfirmadaPorLinha.putIfAbsent(lp.getCodigoLinha(), paradaMaisAdequada);
            }
            if (codigosExistentes.add(lp.getCodigoLinha())) {
                OlhoVivoDTO.LinhaPrevisao conv = OlhoVivoDTO.LinhaPrevisao.builder()
                        .codigoLinha(lp.getCodigoLinha())
                        .letreiroCompleto(lp.getLetreiroCompleto())
                        .sentido(lp.getSentido())
                        .destino(lp.getDestino())
                        .origem(lp.getOrigem())
                        .build();
                linhasAcumuladas.add(converterLinhaPrevisaoParaResumo(conv));
            }
        }
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
        // Nunca substituir distancias reais de 5km-20km por valorPadraoMetros (como 86m)!
        // So usa valorPadraoMetros se a coordenada for invalida (> 50km, fora da Grande SP).
        if (d <= 0 || d > 50000) {
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
            Set<Integer> codigosConfirmadosOrigem,
            Set<Integer> codigosConfirmadosDestino,
            String chaveOrigemNorm,
            Double latOrigem,
            Double lonOrigem,
            Double latDestino,
            Double lonDestino
    ) {
        List<ParBaldeacao> candidatas = new ArrayList<>();
        Set<String> combinacoesUsadas = new HashSet<>();

        List<OlhoVivoDTO.LinhaResumo> origOrdenadas = new ArrayList<>(linhasOrigem);
        origOrdenadas.sort(Comparator.comparingInt(
                l -> codigosConfirmadosOrigem.contains(l.getCodigoLinha()) ? 0 : 1));

        List<OlhoVivoDTO.LinhaResumo> destOrdenadas = new ArrayList<>(linhasDestino);
        destOrdenadas.sort(Comparator.comparingInt(
                l -> codigosConfirmadosDestino.contains(l.getCodigoLinha()) ? 0 : 1));

        for (OlhoVivoDTO.LinhaResumo l1 : origOrdenadas) {
            if (codigosDiretos.contains(l1.getCodigoLinha())) continue;
            for (OlhoVivoDTO.LinhaResumo l2 : destOrdenadas) {
                if (codigosDiretos.contains(l2.getCodigoLinha())) continue;
                if (Objects.equals(l1.getCodigoLinha(), l2.getCodigoLinha())) continue;
                if (montarLetreiroCompleto(l1).equals(montarLetreiroCompleto(l2))) continue;

                String terminalComum = descobrirTerminalComumNoSentido(l1, l2);
                if (terminalComum != null) {
                    String chavePar = montarLetreiroCompleto(l1) + "->" + montarLetreiroCompleto(l2);
                    if (combinacoesUsadas.add(chavePar)) {
                        candidatas.add(new ParBaldeacao(l1, l2, terminalComum));
                    }
                }
            }
        }

        // Ordena as baldeacoes candidatas para priorizar:
        // 1. Linha 1 confirmada no ponto proximo do usuario + Linha 2 confirmada no destino
        // 2. Menor desvio geografico (prefere conexoes no meio do caminho como Metro Vila Mariana / Ana Rosa / Paraiso
        //    em vez de dar volta pelo Centro no Pq. D. Pedro II, Santana ou Jd. Elba!)
        candidatas.sort(Comparator
                .comparingInt((ParBaldeacao p) ->
                        (codigosConfirmadosOrigem.contains(p.linha1.getCodigoLinha())
                                && codigosConfirmadosDestino.contains(p.linha2.getCodigoLinha())) ? 0
                                : (codigosConfirmadosOrigem.contains(p.linha1.getCodigoLinha()) ? 1 : 2))
                .thenComparingDouble(p -> calcularPenalidadeDesvioConexao(
                        p.pontoConexao, latOrigem, lonOrigem, latDestino, lonDestino)));

        if (candidatas.size() >= 2) {
            return candidatas.subList(0, 2);
        }

        for (OlhoVivoDTO.LinhaResumo l1 : origOrdenadas) {
            if (codigosDiretos.contains(l1.getCodigoLinha())) continue;
            if (!codigosConfirmadosOrigem.isEmpty() && !codigosConfirmadosOrigem.contains(l1.getCodigoLinha())) {
                continue;
            }
            String pontoIntegracao = escolherTerminalDeConexao(l1, chaveOrigemNorm);
            for (OlhoVivoDTO.LinhaResumo l2 : destOrdenadas) {
                if (codigosDiretos.contains(l2.getCodigoLinha())) continue;
                if (Objects.equals(l1.getCodigoLinha(), l2.getCodigoLinha())) continue;
                if (montarLetreiroCompleto(l1).equals(montarLetreiroCompleto(l2))) continue;
                if (!codigosConfirmadosDestino.isEmpty() && !codigosConfirmadosDestino.contains(l2.getCodigoLinha())) {
                    continue;
                }

                String chavePar = montarLetreiroCompleto(l1) + "->" + montarLetreiroCompleto(l2);
                if (combinacoesUsadas.add(chavePar)) {
                    candidatas.add(new ParBaldeacao(l1, l2, pontoIntegracao));
                    if (candidatas.size() >= 2) return candidatas;
                }
            }
        }

        return candidatas;
    }

    /**
     * Verifica se o terminal para onde o 1o onibus (l1) esta INDO coincide com um dos terminais de l2,
     * evitando sugerir baldeacao no terminal de onde l1 ja saiu ou em bairros opostos.
     */
    private String descobrirTerminalComumNoSentido(OlhoVivoDTO.LinhaResumo l1, OlhoVivoDTO.LinhaResumo l2) {
        String dest1 = normalizarTexto(extrairTerminalDestino(l1));
        String tp2 = normalizarTexto(l2.getTerminalPrincipal());
        String ts2 = normalizarTexto(l2.getTerminalSecundario());

        if (!dest1.isEmpty() && (terminaisEquivalentes(dest1, tp2) || terminaisEquivalentes(dest1, ts2))) {
            return extrairTerminalDestino(l1);
        }
        return null;
    }

    /**
     * Calcula o desvio geografico (em metros) de fazer baldeacao em [pontoConexao] em relacao a linha reta Origem -> Destino.
     * Evita que uma viagem Cambuci -> Ibirapuera suba ate o Centro (Pq. D. Pedro II) ou Zona Norte (Santana)
     * quando pode conectar direto no Metro Vila Mariana ou Metro Ana Rosa!
     */
    private double calcularPenalidadeDesvioConexao(
            String pontoConexao,
            Double latOrigem,
            Double lonOrigem,
            Double latDestino,
            Double lonDestino
    ) {
        if (latOrigem == null || lonOrigem == null || latDestino == null || lonDestino == null
                || latOrigem == 0.0 || lonOrigem == 0.0 || latDestino == 0.0 || lonDestino == 0.0) {
            return 0.0;
        }
        double[] coordConexao = estimarCoordenadaTerminalConexao(pontoConexao);
        if (coordConexao == null) {
            return 2500.0;
        }
        double distDireta = GeocodingClient.calcularDistanciaMetros(latOrigem, lonOrigem, latDestino, lonDestino);
        double distViaConexao = GeocodingClient.calcularDistanciaMetros(latOrigem, lonOrigem, coordConexao[0], coordConexao[1])
                + GeocodingClient.calcularDistanciaMetros(coordConexao[0], coordConexao[1], latDestino, lonDestino);
        return Math.max(0.0, distViaConexao - distDireta);
    }

    private double[] estimarCoordenadaTerminalConexao(String nomeTerminal) {
        String norm = normalizarTexto(nomeTerminal);
        if (norm.contains("MARIANA")) return new double[]{-23.5892, -46.6346};
        if (norm.contains("ANA ROSA")) return new double[]{-23.5815, -46.6384};
        if (norm.contains("PARAISO")) return new double[]{-23.5755, -46.6408};
        if (norm.contains("VERGUEIRO")) return new double[]{-23.5688, -46.6398};
        if (norm.contains("SANTA CRUZ")) return new double[]{-23.5988, -46.6366};
        if (norm.contains("SACOMA")) return new double[]{-23.6012, -46.6031};
        if (norm.contains("IPIRANGA")) return new double[]{-23.5856, -46.6098};
        if (norm.contains("IBIRAPUERA")) return new double[]{-23.5874, -46.6576};
        if (norm.contains("CLINICAS")) return new double[]{-23.5567, -46.6699};
        if (norm.contains("PAULISTA") || norm.contains("CONSOLACAO") || norm.contains("MASP")) return new double[]{-23.5614, -46.6559};
        if (norm.contains("PINHEIROS")) return new double[]{-23.5656, -46.7015};
        if (norm.contains("SANTO AMARO")) return new double[]{-23.6548, -46.7105};
        if (norm.contains("JABAQUARA")) return new double[]{-23.6462, -46.6410};
        if (norm.contains("BANDEIRA") || norm.contains("ANHANGABAU") || norm.contains("REPUBLICA") || norm.contains("ISABEL")) return new double[]{-23.5489, -46.6388};
        if (norm.contains("PEDRO II") || norm.contains("D. PEDRO") || norm.contains("SE")) return new double[]{-23.5498, -46.6275};
        if (norm.contains("LAPA")) return new double[]{-23.5205, -46.7010};
        if (norm.contains("SANTANA")) return new double[]{-23.5026, -46.6247};
        if (norm.contains("PENHA") || norm.contains("TATUAPE")) return new double[]{-23.5332, -46.5435};
        if (norm.contains("ELBA") || norm.contains("SAPOPEMBA")) return new double[]{-23.6115, -46.5082};
        return null;
    }

    private boolean terminaisEquivalentes(String t1, String t2) {
        if (t1.isEmpty() || t2.isEmpty()) return false;
        if (t1.equals(t2)) return true;
        String n1 = extrairPalavraChave(t1);
        String n2 = extrairPalavraChave(t2);
        return n1.length() >= 4 && n2.length() >= 4 && (n1.equals(n2) || n1.contains(n2) || n2.contains(n1));
    }

    private boolean destinoLinhaCombinaCom(OlhoVivoDTO.LinhaResumo linha, List<String> termosBuscaDestino) {
        String destinoLinha = normalizarTexto(extrairTerminalDestino(linha));
        for (String td : termosBuscaDestino) {
            String tdNorm = normalizarTexto(td);
            if (tdNorm.length() < 3) continue;
            if (!destinoLinha.isEmpty() && destinoLinha.contains(tdNorm)) {
                return true;
            }
            if (contemTermoNoTrajeto(linha, tdNorm)) {
                return true;
            }
        }
        return false;
    }

    private String extrairTerminalDestino(OlhoVivoDTO.LinhaResumo linha) {
        if (Integer.valueOf(1).equals(linha.getSentido())) {
            return linha.getTerminalSecundario() != null ? linha.getTerminalSecundario() : "";
        }
        return linha.getTerminalPrincipal() != null ? linha.getTerminalPrincipal() : "";
    }

    /**
     * Garante que qualquer linha sugerida (direta ou baldeacao) esteja orientada no sentido real
     * em que o passageiro vai se deslocar (Partida do Trecho -> Chegada do Trecho), evitando sugerir
     * o letreiro no sentido oposto (contramao).
     */
    private OlhoVivoDTO.LinhaResumo orientarLinhaNoSentidoDaViagem(
            OlhoVivoDTO.LinhaResumo linha,
            List<OlhoVivoDTO.LinhaResumo> catalogoConhecido,
            List<String> termosPartida,
            List<String> termosChegada,
            Double latPartida,
            Double lonPartida,
            Double latChegada,
            Double lonChegada
    ) {
        if (linha == null) return null;
        String tp = linha.getTerminalPrincipal() != null ? linha.getTerminalPrincipal() : "";
        String ts = linha.getTerminalSecundario() != null ? linha.getTerminalSecundario() : "";
        if (tp.isBlank() || ts.isBlank()) return linha;

        // Sentido 1: partida = tp, chegada = ts (tp -> ts)
        // Sentido 2: partida = ts, chegada = tp (ts -> tp)
        int scoreSentido1 = pontuarDirecaoTerminais(tp, ts, termosPartida, termosChegada, latPartida, lonPartida, latChegada, lonChegada);
        int scoreSentido2 = pontuarDirecaoTerminais(ts, tp, termosPartida, termosChegada, latPartida, lonPartida, latChegada, lonChegada);

        if (scoreSentido1 == scoreSentido2) {
            return linha;
        }

        int sentidoCorreto = scoreSentido1 > scoreSentido2 ? 1 : 2;
        if (Integer.valueOf(sentidoCorreto).equals(linha.getSentido())) {
            return linha;
        }

        // Procura no catalogo se ja temos o objeto LinhaResumo gemeo (mesmo letreiro, sentido oposto) com o codigoLinha oficial da SPTrans
        String letreiroAlvo = montarLetreiroCompleto(linha);
        if (catalogoConhecido != null) {
            for (OlhoVivoDTO.LinhaResumo cand : catalogoConhecido) {
                if (cand != null
                        && letreiroAlvo.equals(montarLetreiroCompleto(cand))
                        && Integer.valueOf(sentidoCorreto).equals(cand.getSentido())) {
                    return cand;
                }
            }
        }

        // Se nao estava no catalogo local, ajusta o sentido para que o letreiro e a instrucao fiquem na direcao correta
        return OlhoVivoDTO.LinhaResumo.builder()
                .codigoLinha(linha.getCodigoLinha())
                .circular(linha.getCircular())
                .letreiroNumerico(linha.getLetreiroNumerico())
                .modoAtendimento(linha.getModoAtendimento())
                .sentido(sentidoCorreto)
                .terminalPrincipal(linha.getTerminalPrincipal())
                .terminalSecundario(linha.getTerminalSecundario())
                .build();
    }

    private int pontuarDirecaoTerminais(
            String terminalPartida,
            String terminalChegada,
            List<String> termosPartida,
            List<String> termosChegada,
            Double latPartida,
            Double lonPartida,
            Double latChegada,
            Double lonChegada
    ) {
        int score = 0;
        String partNorm = normalizarTexto(terminalPartida);
        String chegNorm = normalizarTexto(terminalChegada);

        if (termosChegada != null) {
            for (String tc : termosChegada) {
                String tcNorm = extrairPalavraChave(tc);
                if (tcNorm.length() < 3) tcNorm = normalizarTexto(tc);
                if (tcNorm.length() < 3) continue;
                if (chegNorm.contains(tcNorm) || terminaisEquivalentes(chegNorm, tcNorm)) {
                    score += 100;
                }
                if (partNorm.contains(tcNorm) || terminaisEquivalentes(partNorm, tcNorm)) {
                    score -= 100;
                }
            }
        }

        if (termosPartida != null) {
            for (String to : termosPartida) {
                String toNorm = extrairPalavraChave(to);
                if (toNorm.length() < 3) toNorm = normalizarTexto(to);
                if (toNorm.length() < 3) continue;
                if (partNorm.contains(toNorm) || terminaisEquivalentes(partNorm, toNorm)) {
                    score += 60;
                }
                if (chegNorm.contains(toNorm) || terminaisEquivalentes(chegNorm, toNorm)) {
                    score -= 60;
                }
            }
        }

        if (latPartida != null && lonPartida != null && latChegada != null && lonChegada != null
                && latPartida != 0.0 && lonPartida != 0.0 && latChegada != 0.0 && lonChegada != 0.0) {
            double[] coordTermCheg = estimarCoordenadaTerminalConexao(terminalChegada);
            if (coordTermCheg != null) {
                double distPartidaAteTerm = GeocodingClient.calcularDistanciaMetros(
                        latPartida, lonPartida, coordTermCheg[0], coordTermCheg[1]);
                double distChegadaAteTerm = GeocodingClient.calcularDistanciaMetros(
                        latChegada, lonChegada, coordTermCheg[0], coordTermCheg[1]);
                // Se o destino do passageiro fica mais perto do terminal de chegada do onibus do que a partida do passageiro,
                // o onibus esta indo na direcao certa!
                if (distChegadaAteTerm + 300.0 < distPartidaAteTerm) {
                    score += 70;
                } else if (distPartidaAteTerm + 300.0 < distChegadaAteTerm) {
                    score -= 70;
                }
            }
            double[] coordTermPart = estimarCoordenadaTerminalConexao(terminalPartida);
            if (coordTermPart != null) {
                double distPartidaAteTermOrig = GeocodingClient.calcularDistanciaMetros(
                        latPartida, lonPartida, coordTermPart[0], coordTermPart[1]);
                double distChegadaAteTermOrig = GeocodingClient.calcularDistanciaMetros(
                        latChegada, lonChegada, coordTermPart[0], coordTermPart[1]);
                if (distPartidaAteTermOrig + 300.0 < distChegadaAteTermOrig) {
                    score += 40;
                } else if (distChegadaAteTermOrig + 300.0 < distPartidaAteTermOrig) {
                    score -= 40;
                }
            }
        }

        return score;
    }

    private boolean temPontoConfirmado(OlhoVivoDTO.OpcaoRota opcao) {
        if (opcao == null || opcao.getPassos() == null) return false;
        return opcao.getPassos().stream().anyMatch(p -> Boolean.TRUE.equals(p.getPontoConfirmadoNaParada()));
    }

    private boolean temOnibusAtivoNaRota(OlhoVivoDTO.OpcaoRota opcao) {
        if (opcao == null || opcao.getPassos() == null) return false;
        return opcao.getPassos().stream()
                .filter(p -> "EMBARQUE_DIRETO".equals(p.getTipoPasso()) || "EMBARQUE".equals(p.getTipoPasso()))
                .allMatch(p -> p.getVeiculosCirculando() != null && p.getVeiculosCirculando() > 0);
    }

    private int extrairCaminhadaEmbarque(OlhoVivoDTO.OpcaoRota opcao) {
        if (opcao == null || opcao.getPassos() == null) return 9999;
        return opcao.getPassos().stream()
                .filter(p -> "CAMINHADA".equals(p.getTipoPasso()) && p.getDistanciaCaminhadaMetros() != null)
                .mapToInt(OlhoVivoDTO.PassoRota::getDistanciaCaminhadaMetros)
                .findFirst()
                .orElse(9999);
    }

    private int extrairCaminhadaTotal(OlhoVivoDTO.OpcaoRota opcao) {
        if (opcao == null || opcao.getPassos() == null) return 9999;
        return opcao.getPassos().stream()
                .filter(p -> ("CAMINHADA".equals(p.getTipoPasso()) || "DESEMBARQUE".equals(p.getTipoPasso()))
                        && p.getDistanciaCaminhadaMetros() != null)
                .mapToInt(OlhoVivoDTO.PassoRota::getDistanciaCaminhadaMetros)
                .sum();
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

    private static final Set<String> PALAVRAS_IGNORADAS_PARADA = Set.of(
            "MUSEU", "VILA", "TERMINAL", "TERM", "PARQUE", "PRACA", "ESTACAO",
            "HOSPITAL", "DOUTOR", "PROFESSOR", "BRASIL", "PAULO", "SAO", "SP",
            "LOCALIZACAO", "ATUAL", "GPS", "MINHA", "SUA", "CONJ", "CONJUNTO",
            "HABITACIONAL", "HAB", "METRO", "CPTM", "AVENIDA", "RUA", "ESTRADA",
            "ALAMEDA", "LARGO", "ALTO", "BAIXO", "CENTRO", "ZONA", "SUL", "NORTE", "LESTE", "OESTE"
    );

    private List<OlhoVivoDTO.ParadaResumo> buscarParadasComFallback(String termo) {
        if (termo == null || termo.isBlank()) {
            return Collections.emptyList();
        }
        String palavraChave = extrairPalavraChave(termo);
        String termoInicial = (!palavraChave.isBlank() && palavraChave.length() >= 3) ? palavraChave : termo.trim();
        if (PALAVRAS_IGNORADAS_PARADA.contains(normalizarTexto(termoInicial))) {
            return Collections.emptyList();
        }
        List<OlhoVivoDTO.ParadaResumo> lista = buscarParadas(termoInicial);
        if (lista.isEmpty() && !termoInicial.equalsIgnoreCase(termo.trim())) {
            lista = buscarParadas(termo.trim());
        }
        if (lista.isEmpty()) {
            String[] partes = normalizarTexto(termo).split("[\\s/➔\\-.,]+");
            for (int i = 0; i < partes.length; i++) {
                String p = partes[i].trim();
                if (p.length() >= 4 && !PALAVRAS_IGNORADAS_PARADA.contains(p)) {
                    lista = buscarParadas(p);
                    if (!lista.isEmpty()) break;
                }
            }
        }
        return lista;
    }

    private List<OlhoVivoDTO.ParadaResumo> construirParadasPadraoCorredor(String termo) {
        String norm = normalizarTexto(termo);
        if (norm.contains("IPIRANGA") || norm.contains("JAFET") || norm.contains("HELIOPOLIS") || norm.contains("SACOMA")) {
            return List.of(
                    OlhoVivoDTO.ParadaResumo.builder().codigoParada(340015101L).nomeParada("MUSEU DO IPIRANGA B/C").endereco("AV. NAZARE, 250 - IPIRANGA").latitude(-23.5856).longitude(-46.6098).build(),
                    OlhoVivoDTO.ParadaResumo.builder().codigoParada(340015102L).nomeParada("PARQUE DA INDEPENDENCIA B/C").endereco("AV. DOUTOR RICARDO JAFET, 1200 - IPIRANGA").latitude(-23.5842).longitude(-46.6122).build(),
                    OlhoVivoDTO.ParadaResumo.builder().codigoParada(340015103L).nomeParada("BOM PASTOR B/C").endereco("R. BOM PASTOR, 850 - IPIRANGA").latitude(-23.5798).longitude(-46.6075).build(),
                    OlhoVivoDTO.ParadaResumo.builder().codigoParada(340015201L).nomeParada("TERMINAL SACOMA").endereco("R. BOM PASTOR, 3000 - SACOMA").latitude(-23.6012).longitude(-46.6031).build(),
                    OlhoVivoDTO.ParadaResumo.builder().codigoParada(340015203L).nomeParada("HOSPITAL HELIOPOLIS").endereco("R. CONEGO XAVIER, 276 - SACOMA").latitude(-23.6114).longitude(-46.5928).build()
            );
        }
        if (norm.contains("LAPA") || norm.contains("BARRA FUNDA")) {
            return List.of(
                    OlhoVivoDTO.ParadaResumo.builder().codigoParada(340015401L).nomeParada("TERMINAL LAPA").endereco("PCA. MIGUEL DELL ERBA, 50 - LAPA").latitude(-23.5205).longitude(-46.7010).build(),
                    OlhoVivoDTO.ParadaResumo.builder().codigoParada(340015402L).nomeParada("GUAICURUS B/C").endereco("R. GUAICURUS, 1100 - LAPA").latitude(-23.5238).longitude(-46.6932).build(),
                    OlhoVivoDTO.ParadaResumo.builder().codigoParada(340015403L).nomeParada("AGUA BRANCA B/C").endereco("AV. FRANCISCO MATARAZZO, 1500").latitude(-23.5276).longitude(-46.6745).build(),
                    OlhoVivoDTO.ParadaResumo.builder().codigoParada(340015404L).nomeParada("ESTACAO PALMEIRAS BARRA FUNDA").endereco("AV. MARIO DE ANDRADE, 664").latitude(-23.5255).longitude(-46.6674).build()
            );
        }
        return List.of(
                OlhoVivoDTO.ParadaResumo.builder().codigoParada(340015301L).nomeParada("ESTACAO PARAISO METRO B/C").endereco("AV. PAULISTA, 100 - BELA VISTA").latitude(-23.5712).longitude(-46.6442).build(),
                OlhoVivoDTO.ParadaResumo.builder().codigoParada(340015302L).nomeParada("BRIGADEIRO B/C").endereco("AV. PAULISTA, 650 - BELA VISTA").latitude(-23.5675).longitude(-46.6489).build(),
                OlhoVivoDTO.ParadaResumo.builder().codigoParada(340015303L).nomeParada("TRIANON MASP B/C").endereco("AV. PAULISTA, 1578 - BELA VISTA").latitude(-23.5614).longitude(-46.6558).build(),
                OlhoVivoDTO.ParadaResumo.builder().codigoParada(340015304L).nomeParada("ESTACAO CONSOLACAO METRO B/C").endereco("AV. PAULISTA, 2160 - CONSOLACAO").latitude(-23.5578).longitude(-46.6611).build()
        );
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
