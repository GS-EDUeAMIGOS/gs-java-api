package br.com.viacidada;

import br.com.viacidada.client.GeocodingClient;
import br.com.viacidada.client.OlhoVivoClient;
import br.com.viacidada.dto.OlhoVivoDTO;
import br.com.viacidada.repository.OcorrenciaRepository;
import br.com.viacidada.service.OlhoVivoService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OlhoVivoRoteamentoTests {

    @Mock
    private OlhoVivoClient olhoVivoClient;

    @Mock
    private GeocodingClient geocodingClient;

    @Mock
    private OcorrenciaRepository ocorrenciaRepository;

    @InjectMocks
    private OlhoVivoService olhoVivoService;

    @Test
    @DisplayName("GeocodingClient deve remover numero de imovel e extrair nucleo contra abreviacoes SPTrans")
    void deveLimparNumeroEExtrairNucleoDeLogradouro() {
        GeocodingClient clientReal = new GeocodingClient();

        assertEquals("VERGUEIRO", clientReal.limparTermoParaSptrans("Rua Vergueiro, 1000"));
        assertEquals("VILA DOM PEDRO", clientReal.limparTermoParaSptrans("Vila Dom Pedro I"));
        assertEquals("XAVIER", clientReal.extrairNucleoLogradouro("Rua Cônego Xavier, 276"));

        List<String> coletoras = clientReal.descobrirAvenidasColetorasProximas(-23.5880, -46.6090);
        assertFalse(coletoras.isEmpty());
        assertTrue(coletoras.contains("NAZARE") || coletoras.contains("BOM PASTOR"));
    }

    @Test
    @DisplayName("Planejador deve priorizar parada a ~80m em vez de parada distante (>1km) mesmo quando ha muitas paradas")
    void devePriorizarParadaMaisProximaSemCortePrematuro() {
        double latUsuario = -23.5880;
        double lonUsuario = -46.6095;

        OlhoVivoDTO.LocalResolvido localOrigem = OlhoVivoDTO.LocalResolvido.builder()
                .termoOriginal("GPS")
                .nomeLocal("Rua Residencial Sem Ponto")
                .logradouro("RUA RESIDENCIAL")
                .bairro("IPIRANGA")
                .termoBuscaSptrans("NAZARE")
                .termosCandidatosSptrans(new ArrayList<>(List.of("IPIRANGA", "NAZARE")))
                .latitude(latUsuario)
                .longitude(lonUsuario)
                .build();

        OlhoVivoDTO.LocalResolvido localDestino = OlhoVivoDTO.LocalResolvido.builder()
                .termoOriginal("Paulista")
                .nomeLocal("Av. Paulista")
                .logradouro("AV PAULISTA")
                .bairro("BELA VISTA")
                .termoBuscaSptrans("PAULISTA")
                .termosCandidatosSptrans(new ArrayList<>(List.of("PAULISTA")))
                .latitude(-23.5614)
                .longitude(-46.6559)
                .build();

        // Gera 30 paradas distantes (~1.4km) no primeiro termo ("IPIRANGA") para garantir que nao haja corte antes da ordenacao
        List<OlhoVivoDTO.ParadaResumo> paradasDistantes = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            paradasDistantes.add(OlhoVivoDTO.ParadaResumo.builder()
                    .codigoParada(1000L + i)
                    .nomeParada("PARADA DISTANTE " + i)
                    .endereco("R. DISTANTE, " + (i * 100))
                    .latitude(-23.6005 - (i * 0.0002))
                    .longitude(-46.6095)
                    .build());
        }

        // Parada real na esquina a ~65m do usuario no segundo termo ("NAZARE")
        OlhoVivoDTO.ParadaResumo paradaEsquina = OlhoVivoDTO.ParadaResumo.builder()
                .codigoParada(2001L)
                .nomeParada("AV NAZARE B/C")
                .endereco("AV. NAZARE, 260")
                .latitude(-23.5885)
                .longitude(-46.6095)
                .build();

        OlhoVivoDTO.ParadaResumo paradaPaulista = OlhoVivoDTO.ParadaResumo.builder()
                .codigoParada(3001L)
                .nomeParada("TRIANON MASP")
                .endereco("AV. PAULISTA, 1578")
                .latitude(-23.5614)
                .longitude(-46.6559)
                .build();

        when(geocodingClient.resolverPorCoordenadas(latUsuario, lonUsuario)).thenReturn(localOrigem);
        when(geocodingClient.resolverLocalPorNome("Paulista")).thenReturn(localDestino);
        when(geocodingClient.limparTermoParaSptrans(anyString())).thenAnswer(inv -> inv.getArgument(0));

        when(olhoVivoClient.buscarParadas("IPIRANGA")).thenReturn(paradasDistantes);
        when(olhoVivoClient.buscarParadas("NAZARE")).thenReturn(List.of(paradaEsquina));
        when(olhoVivoClient.buscarParadas("PAULISTA")).thenReturn(List.of(paradaPaulista));

        // Linha distante (1111) na parada 1000 e Linha perto (2222) na parada da esquina (2001)
        when(olhoVivoClient.buscarPrevisoesPorParada(2001L)).thenReturn(
                OlhoVivoDTO.PrevisaoParadaResponse.builder()
                        .parada(OlhoVivoDTO.ParadaComPrevisoes.builder()
                                .codigoParada(2001L)
                                .nomeParada("AV NAZARE B/C")
                                .linhas(List.of(OlhoVivoDTO.LinhaPrevisao.builder()
                                        .codigoLinha(2222)
                                        .letreiroCompleto("476G-10")
                                        .sentido(1)
                                        .origem("IPIRANGA")
                                        .destino("PAULISTA")
                                        .build()))
                                .build())
                        .build()
        );
        when(olhoVivoClient.buscarPrevisoesPorParada(3001L)).thenReturn(
                OlhoVivoDTO.PrevisaoParadaResponse.builder()
                        .parada(OlhoVivoDTO.ParadaComPrevisoes.builder()
                                .codigoParada(3001L)
                                .nomeParada("TRIANON MASP")
                                .linhas(List.of(OlhoVivoDTO.LinhaPrevisao.builder()
                                        .codigoLinha(2222)
                                        .letreiroCompleto("476G-10")
                                        .sentido(1)
                                        .origem("IPIRANGA")
                                        .destino("PAULISTA")
                                        .build()))
                                .build())
                        .build()
        );
        when(olhoVivoClient.buscarVeiculosPorLinha(anyInt())).thenReturn(
                OlhoVivoDTO.PosicaoLinhaResponse.builder().veiculos(List.of()).build()
        );
        when(ocorrenciaRepository.findByCodigoLinhaAndStatusNot(anyInt(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of());

        OlhoVivoDTO.RotaPlanejadaResponse resposta = olhoVivoService.planejarRota(
                "GPS", "Paulista", latUsuario, lonUsuario);

        assertNotNull(resposta);
        assertNotNull(resposta.getParadaOrigemSugerida());
        assertEquals(2001L, resposta.getParadaOrigemSugerida().getCodigoParada());
        assertTrue(resposta.getDistanciaCaminhadaOrigemMetros() < 150,
                "Deveria sugerir a parada da esquina (<150m), mas retornou " + resposta.getDistanciaCaminhadaOrigemMetros() + "m");
        assertFalse(resposta.getOpcoes().isEmpty());
        assertTrue(resposta.getOpcoes().get(0).getRecomendada());
        assertTrue(resposta.getOpcoes().get(0).getTitulo().contains("476G-10"));
    }

    @Test
    @DisplayName("Planejador deve orientar o sentido do letreiro na direcao real do destino (ex.: TERM. PINHEIROS -> SACOMA)")
    void deveOrientarSentidoCorretoDaLinhaParaDestino() {
        double latCambuci = -23.5746;
        double lonCambuci = -46.6187;

        OlhoVivoDTO.LocalResolvido localOrigem = OlhoVivoDTO.LocalResolvido.builder()
                .termoOriginal("Rua Eiras Garcia")
                .nomeLocal("Rua Eiras Garcia")
                .logradouro("RUA EIRAS GARCIA")
                .bairro("CAMBUCI")
                .termoBuscaSptrans("CAMBUCI")
                .termosCandidatosSptrans(new ArrayList<>(List.of("CAMBUCI")))
                .latitude(latCambuci)
                .longitude(lonCambuci)
                .build();

        OlhoVivoDTO.LocalResolvido localDestino = OlhoVivoDTO.LocalResolvido.builder()
                .termoOriginal("Hospital Heliopolis")
                .nomeLocal("Hospital Heliópolis")
                .logradouro("RUA CONEGO XAVIER")
                .bairro("SACOMA")
                .termoBuscaSptrans("SACOMA")
                .termosCandidatosSptrans(new ArrayList<>(List.of("SACOMA", "HELIOPOLIS")))
                .latitude(-23.6114)
                .longitude(-46.5928)
                .build();

        OlhoVivoDTO.ParadaResumo paradaCambuci = OlhoVivoDTO.ParadaResumo.builder()
                .codigoParada(90001001L)
                .nomeParada("PONTO RUA HEITOR PEIXOTO")
                .endereco("RUA HEITOR PEIXOTO - CAMBUCI")
                .latitude(-23.5754)
                .longitude(-46.6187)
                .build();

        OlhoVivoDTO.ParadaResumo paradaHeliopolis = OlhoVivoDTO.ParadaResumo.builder()
                .codigoParada(90002001L)
                .nomeParada("RUA CEL. SILVA CASTRO, 195")
                .endereco("RUA CORONEL SILVA CASTRO - NOVA HELIOPOLIS")
                .latitude(-23.6105)
                .longitude(-46.5925)
                .build();

        when(geocodingClient.resolverPorCoordenadas(latCambuci, lonCambuci)).thenReturn(localOrigem);
        when(geocodingClient.resolverLocalPorNome("Hospital Heliopolis")).thenReturn(localDestino);
        when(geocodingClient.limparTermoParaSptrans(anyString())).thenAnswer(inv -> inv.getArgument(0));
        when(geocodingClient.buscarParadasReaisProximas(latCambuci, lonCambuci)).thenReturn(List.of(paradaCambuci));
        when(geocodingClient.buscarParadasReaisProximas(-23.6114, -46.5928)).thenReturn(List.of(paradaHeliopolis));

        // Simula /Posicao retornando a linha 477A-10 (lt0 = TERM. PINHEIROS, lt1 = SACOMA)
        when(olhoVivoClient.buscarTodasPosicoes()).thenReturn(
                OlhoVivoDTO.PosicaoGeralResponse.builder()
                        .horarioReferencia("13:30")
                        .linhas(List.of(
                                OlhoVivoDTO.LinhaPosicaoGeral.builder()
                                        .codigoLinha(346)
                                        .letreiroCompleto("477A-10")
                                        .sentido(1)
                                        .destino("TERM. PINHEIROS")
                                        .origem("SACOMÃ")
                                        .veiculos(List.of(
                                                OlhoVivoDTO.VeiculoPosicao.builder()
                                                        .prefixo("61001")
                                                        .acessivelPcD(true)
                                                        .latitude(-23.5752)
                                                        .longitude(-46.6185)
                                                        .build(),
                                                OlhoVivoDTO.VeiculoPosicao.builder()
                                                        .prefixo("61002")
                                                        .acessivelPcD(true)
                                                        .latitude(-23.6108)
                                                        .longitude(-46.5926)
                                                        .build()
                                        ))
                                        .build()
                        ))
                        .build()
        );
        when(olhoVivoClient.buscarVeiculosPorLinha(anyInt())).thenReturn(
                OlhoVivoDTO.PosicaoLinhaResponse.builder()
                        .veiculos(List.of(OlhoVivoDTO.VeiculoPosicao.builder()
                                .prefixo("61001")
                                .acessivelPcD(true)
                                .latitude(-23.5752)
                                .longitude(-46.6185)
                                .build()))
                        .build()
        );
        when(ocorrenciaRepository.findByCodigoLinhaAndStatusNot(anyInt(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of());

        OlhoVivoDTO.RotaPlanejadaResponse resposta = olhoVivoService.planejarRota(
                "Rua Eiras Garcia", "Hospital Heliopolis", latCambuci, lonCambuci);

        assertFalse(resposta.getOpcoes().isEmpty());
        OlhoVivoDTO.OpcaoRota melhor = resposta.getOpcoes().get(0);
        OlhoVivoDTO.PassoRota passoOnibus = melhor.getPassos().get(1);
        assertEquals("TERM. PINHEIROS ➔ SACOMÃ", passoOnibus.getDescricaoSentido(),
                "Para ir do Cambuci ao Hospital Heliopolis (Sacoma), o sentido deve ser TERM. PINHEIROS -> SACOMA");
        assertTrue(passoOnibus.getInstrucao().contains("TERM. PINHEIROS ➔ SACOMÃ"));
    }

    @Test
    @DisplayName("GeocodingClient deve extrair gtfs_id oficial (9 digitos nao sintetico), route_ref e referencia da esquina")
    void deveExtrairGtfsIdERouteRefDoNominatim() {
        GeocodingClient clientReal = new GeocodingClient();

        GeocodingClient.NominatimResult r = new GeocodingClient.NominatimResult();
        r.setName("Ponto Rua Heitor Peixoto");
        r.setLat("-23.5754");
        r.setLon("-46.6187");
        r.setAddress(Map.of("road", "Rua Heitor Peixoto", "suburb", "Cambuci"));
        r.setExtratags(Map.of(
                "gtfs_id", "140016034",
                "route_ref", "407M-10;4111-10",
                "gtfs_stop_desc", "Ref.: R Heitor Peixoto/ R Basilio Da Cunha"
        ));

        OlhoVivoDTO.ParadaResumo parada = clientReal.converterNominatimParaParada(r, 0);
        assertNotNull(parada);
        assertEquals(140016034L, parada.getCodigoParada());
        assertFalse(GeocodingClient.isParadaSintetica(parada.getCodigoParada()));
        assertFalse(GeocodingClient.isParadaSintetica(920016190L));
        assertEquals(List.of("407M-10", "4111-10"), parada.getLetreirosNoPonto());
        assertTrue(parada.getEndereco().contains("Ref.: R Heitor Peixoto/ R Basilio Da Cunha"));

        // Sem gtfs_id -> gera ID sintetico >= 1_900_000_000L
        GeocodingClient.NominatimResult semGtfs = new GeocodingClient.NominatimResult();
        semGtfs.setName("Ponto Sem GTFS");
        semGtfs.setLat("-23.5760");
        semGtfs.setLon("-46.6190");
        OlhoVivoDTO.ParadaResumo paradaSintetica = clientReal.converterNominatimParaParada(semGtfs, 1);
        assertNotNull(paradaSintetica);
        assertTrue(GeocodingClient.isParadaSintetica(paradaSintetica.getCodigoParada()));
    }

    @Test
    @DisplayName("OlhoVivoService deve consolidar todas as linhas do route_ref mesmo quando /Previsao/Parada retornar p == null")
    void deveConsolidarLinhasDoRouteRefMesmoSemPrevisaoImediata() {
        when(olhoVivoClient.buscarPrevisoesPorParada(140016034L))
                .thenReturn(OlhoVivoDTO.PrevisaoParadaResponse.builder().horarioReferencia("23:45").parada(null).build());
        when(olhoVivoClient.buscarLinhas("407M-10")).thenReturn(List.of(
                OlhoVivoDTO.LinhaResumo.builder()
                        .codigoLinha(101)
                        .letreiroNumerico("407M")
                        .modoAtendimento(10)
                        .sentido(1)
                        .terminalPrincipal("METRÔ VILA MARIANA")
                        .terminalSecundario("LGO. CAMBUCI")
                        .build()
        ));
        when(olhoVivoClient.buscarLinhas("4111-10")).thenReturn(List.of());

        OlhoVivoDTO.PrevisaoParadaResponse resposta = olhoVivoService.buscarLinhasDaParada(
                140016034L, List.of("407M-10", "4111-10"));

        assertNotNull(resposta);
        assertNotNull(resposta.getParada());
        assertEquals(2, resposta.getParada().getLinhas().size());
        assertEquals("407M-10", resposta.getParada().getLinhas().get(0).getLetreiroCompleto());
        assertEquals("METRÔ VILA MARIANA", resposta.getParada().getLinhas().get(0).getDestino());
        assertEquals("4111-10", resposta.getParada().getLinhas().get(1).getLetreiroCompleto());
    }

    @Test
    @DisplayName("OlhoVivoService deve acionar fallback geocodificado ao buscar paradas por nome de rua de bairro (ex.: Eiras Garcia)")
    void deveAcionarFallbackGeocodificadoNaBuscaDeParadasPorTexto() {
        when(olhoVivoClient.buscarParadas("Eiras Garcia")).thenReturn(List.of());
        when(geocodingClient.limparTermoParaSptrans(anyString())).thenAnswer(inv -> inv.getArgument(0));
        when(geocodingClient.resolverLocalPorNome("Eiras Garcia")).thenReturn(
                OlhoVivoDTO.LocalResolvido.builder()
                        .termoOriginal("Eiras Garcia")
                        .nomeLocal("Rua Eiras Garcia")
                        .logradouro("Rua Eiras Garcia")
                        .bairro("Cambuci")
                        .termoBuscaSptrans("CAMBUCI")
                        .termosCandidatosSptrans(new ArrayList<>(List.of("CAMBUCI")))
                        .latitude(-23.5746)
                        .longitude(-46.6187)
                        .build()
        );
        when(olhoVivoClient.buscarParadas("CAMBUCI")).thenReturn(List.of());
        when(geocodingClient.buscarParadasReaisProximas(-23.5746, -46.6187)).thenReturn(List.of(
                OlhoVivoDTO.ParadaResumo.builder()
                        .codigoParada(140016034L)
                        .nomeParada("PONTO RUA HEITOR PEIXOTO")
                        .endereco("RUA HEITOR PEIXOTO - CAMBUCI")
                        .latitude(-23.5754)
                        .longitude(-46.6187)
                        .letreirosNoPonto(List.of("407M-10", "4111-10"))
                        .build()
        ));

        List<OlhoVivoDTO.ParadaResumo> resultado = olhoVivoService.buscarParadas("Eiras Garcia");
        assertFalse(resultado.isEmpty());
        assertEquals(140016034L, resultado.get(0).getCodigoParada());
        assertEquals(List.of("407M-10", "4111-10"), resultado.get(0).getLetreirosNoPonto());
    }
}
