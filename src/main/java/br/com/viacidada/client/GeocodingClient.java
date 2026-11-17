package br.com.viacidada.client;

import br.com.viacidada.dto.OlhoVivoDTO;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.text.Normalizer;
import java.util.*;

/**
 * Camada: Client (Integracao Externa de Geocodificacao — OpenStreetMap Nominatim)
 * Responsabilidade Unica: Converter nomes de locais/estabelecimentos (ex.: "Hospital Heliopolis",
 * "Extra Ricardo Jafet", "Shopping Ibirapuera") ou enderecos em coordenadas GPS (latitude/longitude),
 * logradouro, bairro e distrito de Sao Paulo, gerando termos otimizados para busca na SPTrans.
 */
@Component
@Slf4j
public class GeocodingClient {

    private final RestClient restClient;

    public GeocodingClient() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(4500);
        requestFactory.setReadTimeout(6000);
        this.restClient = RestClient.builder()
                .requestFactory(requestFactory)
                .baseUrl("https://nominatim.openstreetmap.org")
                .defaultHeader(HttpHeaders.USER_AGENT, "ViaCidada-FIAP-CitizenTech/1.0")
                .defaultHeader(HttpHeaders.ACCEPT_LANGUAGE, "pt-BR,pt;q=0.9")
                .build();
    }

    /**
     * Busca um local ou endereco pelo nome (ex.: "Extra Ricardo Jafet", "Hospital Heliopolis", "Rua Vergueiro, 1000") em Sao Paulo.
     */
    public OlhoVivoDTO.LocalResolvido resolverLocalPorNome(String termo) {
        if (termo == null || termo.isBlank()) {
            return criarFallbackConhecido("PAULISTA");
        }
        String limpo = termo.trim();

        // 1. Consulta a API publica Nominatim (OpenStreetMap) restrita a cidade de Sao Paulo
        try {
            String queryCompleta = limpo.toLowerCase(Locale.ROOT).contains("sao paulo")
                    || limpo.toLowerCase(Locale.ROOT).contains("são paulo")
                    ? limpo
                    : limpo + ", São Paulo, SP, Brasil";

            List<NominatimResult> resultados = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/search")
                            .queryParam("q", queryCompleta)
                            .queryParam("format", "jsonv2")
                            .queryParam("addressdetails", 1)
                            .queryParam("limit", 3)
                            .build())
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});

            if (resultados != null && !resultados.isEmpty()) {
                NominatimResult r = resultados.get(0);
                OlhoVivoDTO.LocalResolvido resolvido = converterParaLocalResolvido(limpo, r);

                // Se for um POI (ex.: "Extra Ricardo Jafet (Entrada Estacionamento)") que veio sem o nome da rua no address,
                // fazemos um reverse rapido na coordenada para pegar o nome oficial da avenida/rua ao lado!
                if ((resolvido.getLogradouro() == null || resolvido.getLogradouro().isBlank())
                        && resolvido.getLatitude() != null && resolvido.getLongitude() != null) {
                    enriquecerLogradouroPorCoordenada(resolvido);
                }
                return resolvido;
            }
        } catch (Exception ex) {
            log.debug("Fallback local acionado para '{}' (Nominatim indisponivel ou timeout: {})", limpo, ex.getMessage());
        }

        // 2. Fallback inteligente para locais e bairros de Sao Paulo
        return criarFallbackConhecido(limpo);
    }

    /**
     * Lista sugestoes de locais reais em Sao Paulo.
     */
    public List<OlhoVivoDTO.LocalResolvido> buscarSugestoesLocais(String termo) {
        List<OlhoVivoDTO.LocalResolvido> lista = new ArrayList<>();
        if (termo == null || termo.isBlank()) {
            return lista;
        }
        String limpo = termo.trim();
        try {
            String queryCompleta = limpo + ", São Paulo, SP, Brasil";
            List<NominatimResult> resultados = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/search")
                            .queryParam("q", queryCompleta)
                            .queryParam("format", "jsonv2")
                            .queryParam("addressdetails", 1)
                            .queryParam("limit", 5)
                            .build())
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});

            if (resultados != null) {
                for (NominatimResult r : resultados) {
                    lista.add(converterParaLocalResolvido(limpo, r));
                }
            }
        } catch (Exception ex) {
            log.debug("Erro ao listar sugestoes no Nominatim para '{}': {}", limpo, ex.getMessage());
        }

        if (lista.isEmpty()) {
            lista.add(criarFallbackConhecido(limpo));
        }
        return lista;
    }

    /**
     * Geocodificacao reversa: descobre a rua, bairro e distrito a partir do GPS (latitude/longitude).
     */
    public OlhoVivoDTO.LocalResolvido resolverPorCoordenadas(double latitude, double longitude) {
        try {
            NominatimResult r = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/reverse")
                            .queryParam("lat", latitude)
                            .queryParam("lon", longitude)
                            .queryParam("format", "jsonv2")
                            .queryParam("addressdetails", 1)
                            .build())
                    .retrieve()
                    .body(NominatimResult.class);

            if (r != null) {
                OlhoVivoDTO.LocalResolvido resolvido = converterParaLocalResolvido("Sua Localização Atual", r);
                resolvido.setLatitude(latitude);
                resolvido.setLongitude(longitude);
                return resolvido;
            }
        } catch (Exception ex) {
            log.debug("Fallback de geocodificacao reversa para ({}, {}): {}", latitude, longitude, ex.getMessage());
        }

        return inferirRegiaoPorCoordenadaMaisProxima(latitude, longitude);
    }

    /**
     * Descobre paradas de onibus reais de rua/calcada num raio de ~500m pelo OpenStreetMap (Nominatim bounded viewbox).
     * Essencial para complementar o endpoint /Parada/Buscar da SPTrans, que indexa apenas corredores exclusivos.
     */
    public List<OlhoVivoDTO.ParadaResumo> buscarParadasReaisProximas(double latitude, double longitude) {
        if (latitude == 0.0 || longitude == 0.0) {
            return List.of();
        }
        try {
            double delta = 0.0045; // ~500m em Sao Paulo
            String viewbox = String.format(
                    Locale.US,
                    "%.6f,%.6f,%.6f,%.6f",
                    longitude - delta,
                    latitude + delta,
                    longitude + delta,
                    latitude - delta
            );

            List<NominatimResult> resultados = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/search")
                            .queryParam("q", "bus_stop")
                            .queryParam("format", "jsonv2")
                            .queryParam("addressdetails", 1)
                            .queryParam("limit", 12)
                            .queryParam("viewbox", viewbox)
                            .queryParam("bounded", 1)
                            .build())
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});

            if (resultados == null || resultados.isEmpty()) {
                return List.of();
            }

            List<OlhoVivoDTO.ParadaResumo> paradas = new ArrayList<>();
            long seq = 0;
            for (NominatimResult r : resultados) {
                Double latP = parseDoubleSafe(r.getLat());
                Double lonP = parseDoubleSafe(r.getLon());
                if (latP == null || lonP == null || latP == 0.0 || lonP == 0.0) continue;

                String road = r.getAddress() != null
                        ? primeiroNaoVazio(r.getAddress(), "road", "pedestrian", "avenue", "street")
                        : "";
                String suburb = r.getAddress() != null
                        ? primeiroNaoVazio(r.getAddress(), "suburb", "neighbourhood", "quarter")
                        : "";

                String nome = (r.getName() != null && !r.getName().isBlank())
                        ? r.getName().toUpperCase(Locale.ROOT)
                        : (!road.isBlank() ? "PONTO " + road.toUpperCase(Locale.ROOT) : "PONTO DE ONIBUS PROXIMO");
                String endereco = !road.isBlank()
                        ? road.toUpperCase(Locale.ROOT) + (!suburb.isBlank() ? " - " + suburb.toUpperCase(Locale.ROOT) : "")
                        : (r.getDisplayName() != null ? r.getDisplayName() : nome);

                long codigoSintetico = 90000000L + Math.abs((long) Objects.hash(
                        Math.round(latP * 100000), Math.round(lonP * 100000), seq++));

                paradas.add(OlhoVivoDTO.ParadaResumo.builder()
                        .codigoParada(codigoSintetico)
                        .nomeParada(nome)
                        .endereco(endereco)
                        .latitude(latP)
                        .longitude(lonP)
                        .build());
            }
            return paradas;
        } catch (Exception ex) {
            log.debug("Nao foi possivel consultar paradas OSM proximas a ({}, {}): {}", latitude, longitude, ex.getMessage());
            return List.of();
        }
    }

    private void enriquecerLogradouroPorCoordenada(OlhoVivoDTO.LocalResolvido local) {
        try {
            NominatimResult rev = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/reverse")
                            .queryParam("lat", local.getLatitude())
                            .queryParam("lon", local.getLongitude())
                            .queryParam("format", "jsonv2")
                            .queryParam("addressdetails", 1)
                            .build())
                    .retrieve()
                    .body(NominatimResult.class);
            if (rev != null && rev.getAddress() != null) {
                String rua = primeiroNaoVazio(rev.getAddress(), "road", "pedestrian", "avenue", "street");
                if (!rua.isBlank()) {
                    local.setLogradouro(rua);
                    String ruaLimpa = limparTermoParaSptrans(rua);
                    String nucleoRua = extrairNucleoLogradouro(rua);
                    if (isTermoSptransValido(nucleoRua) && !local.getTermosCandidatosSptrans().contains(nucleoRua)) {
                        local.getTermosCandidatosSptrans().add(0, nucleoRua);
                    }
                    if (isTermoSptransValido(ruaLimpa) && !local.getTermosCandidatosSptrans().contains(ruaLimpa)) {
                        local.getTermosCandidatosSptrans().add(0, ruaLimpa);
                        local.setTermoBuscaSptrans(ruaLimpa);
                    }
                }
            }
        } catch (Exception ignored) {
            // Segue com os termos ja extraidos
        }
    }

    private OlhoVivoDTO.LocalResolvido converterParaLocalResolvido(String termoOriginal, NominatimResult r) {
        String road = "";
        String suburb = "";
        String district = "";
        if (r.getAddress() != null) {
            Map<String, Object> addr = r.getAddress();
            road = primeiroNaoVazio(addr, "road", "pedestrian", "avenue", "street");
            suburb = primeiroNaoVazio(addr, "suburb", "neighbourhood", "quarter");
            district = primeiroNaoVazio(addr, "city_district", "district", "borough", "municipality");
        }

        String bairroCombinado = !suburb.isBlank() && !district.isBlank() && !suburb.equalsIgnoreCase(district)
                ? suburb + " / " + district
                : (!suburb.isBlank() ? suburb : district);

        String nome = (r.getName() != null && !r.getName().isBlank()) ? r.getName() : termoOriginal;
        if (nome.isBlank() && !road.isBlank()) {
            nome = road;
        }
        Double lat = parseDoubleSafe(r.getLat());
        Double lon = parseDoubleSafe(r.getLon());

        // Gera lista ordenada de termos candidatos para a SPTrans (ex.: ["RICARDO JAFET", "IPIRANGA", "VILA DOM PEDRO", "SACOMA"])
        List<String> candidatos = construirTermosCandidatosSptrans(
                termoOriginal,
                nome,
                road,
                suburb,
                district,
                lat,
                lon
        );

        String termoPrincipal = candidatos.isEmpty() ? limparTermoParaSptrans(termoOriginal) : candidatos.get(0);

        return OlhoVivoDTO.LocalResolvido.builder()
                .termoOriginal(termoOriginal)
                .nomeLocal(nome)
                .enderecoCompleto(r.getDisplayName() != null ? r.getDisplayName() : nome)
                .logradouro(road)
                .bairro(bairroCombinado)
                .termoBuscaSptrans(termoPrincipal)
                .termosCandidatosSptrans(candidatos)
                .latitude(lat)
                .longitude(lon)
                .build();
    }

    /**
     * Extrai todos os termos possiveis que a SPTrans entende (nome da via sem "Extra/Hospital/Shopping",
     * nome do logradouro, nome do distrito como "IPIRANGA", "SACOMA", "CAMBUCI", etc.).
     */
    private List<String> construirTermosCandidatosSptrans(
            String termoOriginal,
            String nomeLocal,
            String road,
            String suburb,
            String district,
            Double lat,
            Double lon
    ) {
        LinkedHashSet<String> set = new LinkedHashSet<>();

        // 1. Se o usuario digitou algo como "extra ricardo jafet" ou "hospital heliopolis",
        // extrai a via/referencia limpa (ex.: "RICARDO JAFET", "HELIOPOLIS")
        String termoUsuarioLimpo = limparTermoParaSptrans(termoOriginal);
        if (isTermoSptransValido(termoUsuarioLimpo)) {
            set.add(termoUsuarioLimpo);
        }
        String nucleoUsuario = extrairNucleoLogradouro(termoOriginal);
        if (isTermoSptransValido(nucleoUsuario)) {
            set.add(nucleoUsuario);
        }

        // 2. Logradouro oficial (ex.: "Avenida Doutor Ricardo Jafet" -> "RICARDO JAFET", "Rua Conego Xavier" -> "XAVIER")
        String ruaLimpa = limparTermoParaSptrans(road);
        if (isTermoSptransValido(ruaLimpa)) {
            set.add(ruaLimpa);
        }
        String nucleoRua = extrairNucleoLogradouro(road);
        if (isTermoSptransValido(nucleoRua)) {
            set.add(nucleoRua);
        }

        // 3. Avenidas coletoras e corredores reais de onibus num raio de 1200m (evita cair direto no distrito gigante!)
        if (lat != null && lon != null && lat != 0.0 && lon != 0.0) {
            for (String viaColetora : descobrirAvenidasColetorasProximas(lat, lon)) {
                if (isTermoSptransValido(viaColetora)) {
                    set.add(viaColetora);
                }
            }
        }

        // 4. Distrito oficial de Sao Paulo (ex.: "Ipiranga", "Sacomã", "Cambuci", "Vila Mariana", "Pinheiros")
        String distritoLimpo = limparTermoParaSptrans(district);
        if (isTermoSptransValido(distritoLimpo)) {
            set.add(distritoLimpo);
        }

        // 5. Sub-bairro / Vila (ex.: "Vila Dom Pedro I" -> "VILA DOM PEDRO", "Cerqueira César")
        String suburbLimpo = limparTermoParaSptrans(suburb);
        if (isTermoSptransValido(suburbLimpo)) {
            set.add(suburbLimpo);
        }

        // 5. Mapeamentos diretos de grandes polos da SPTrans
        String tudoNorm = normalizar(termoOriginal + " " + nomeLocal + " " + road + " " + suburb + " " + district);
        if (tudoNorm.contains("HELIOPOLIS") || tudoNorm.contains("SACOMA")) {
            set.add("SACOMA");
            set.add("IPIRANGA");
        }
        if (tudoNorm.contains("RICARDO JAFET") || tudoNorm.contains("JAFET")) {
            set.add("RICARDO JAFET");
            set.add("IPIRANGA");
            set.add("SACOMA");
        }
        if (tudoNorm.contains("CLINICAS")) {
            set.add("CLINICAS");
            set.add("PAULISTA");
        }
        if (tudoNorm.contains("CAMBUCI") || tudoNorm.contains("EIRAS GARCIA") || tudoNorm.contains("MONUMENTO")) {
            set.add("CAMBUCI");
            set.add("MARIANA");
            set.add("ANA ROSA");
            set.add("VERGUEIRO");
        }

        // 6. Regiao SPTrans mais proxima pelas coordenadas geograficas (garante que nunca fique sem linhas)
        if (lat != null && lon != null && lat != 0.0 && lon != 0.0) {
            OlhoVivoDTO.LocalResolvido refProx = inferirRegiaoPorCoordenadaMaisProxima(lat, lon);
            if (refProx.getTermoBuscaSptrans() != null && !refProx.getTermoBuscaSptrans().isBlank()) {
                set.add(refProx.getTermoBuscaSptrans());
            }
        }

        return new ArrayList<>(set);
    }

    private OlhoVivoDTO.LocalResolvido criarFallbackConhecido(String termo) {
        String norm = normalizar(termo);

        if (norm.contains("HELIOPOLIS")) {
            return OlhoVivoDTO.LocalResolvido.builder()
                    .termoOriginal(termo)
                    .nomeLocal("Hospital Heliópolis")
                    .enderecoCompleto("Rua Cônego Xavier, 276 - Sacomã / Heliópolis, São Paulo - SP")
                    .logradouro("RUA CONEGO XAVIER")
                    .bairro("SACOMA")
                    .termoBuscaSptrans("SACOMA")
                    .termosCandidatosSptrans(new ArrayList<>(List.of("CONEGO XAVIER", "SACOMA", "IPIRANGA")))
                    .latitude(-23.6163)
                    .longitude(-46.5919)
                    .build();
        }
        if (norm.contains("JAFET")) {
            return OlhoVivoDTO.LocalResolvido.builder()
                    .termoOriginal(termo)
                    .nomeLocal("Av. Dr. Ricardo Jafet / Extra")
                    .enderecoCompleto("Av. Dr. Ricardo Jafet, Vila Dom Pedro I - Ipiranga, São Paulo - SP")
                    .logradouro("AV DR RICARDO JAFET")
                    .bairro("IPIRANGA")
                    .termoBuscaSptrans("RICARDO JAFET")
                    .termosCandidatosSptrans(new ArrayList<>(List.of("RICARDO JAFET", "IPIRANGA", "SACOMA")))
                    .latitude(-23.5919)
                    .longitude(-46.6096)
                    .build();
        }
        if (norm.contains("CLINICAS")) {
            return OlhoVivoDTO.LocalResolvido.builder()
                    .termoOriginal(termo)
                    .nomeLocal("Hospital das Clínicas FMUSP")
                    .enderecoCompleto("Av. Dr. Enéas Carvalho de Aguiar, 255 - Cerqueira César / Paulista, São Paulo - SP")
                    .logradouro("AV PAULISTA")
                    .bairro("CERQUEIRA CESAR")
                    .termoBuscaSptrans("PAULISTA")
                    .termosCandidatosSptrans(new ArrayList<>(List.of("CLINICAS", "PAULISTA")))
                    .latitude(-23.5567)
                    .longitude(-46.6699)
                    .build();
        }
        if (norm.contains("IBIRAPUERA")) {
            return OlhoVivoDTO.LocalResolvido.builder()
                    .termoOriginal(termo)
                    .nomeLocal("Parque Ibirapuera")
                    .enderecoCompleto("Av. Pedro Álvares Cabral - Vila Mariana / Ibirapuera, São Paulo - SP")
                    .logradouro("AV PEDRO ALVARES CABRAL")
                    .bairro("IBIRAPUERA")
                    .termoBuscaSptrans("IBIRAPUERA")
                    .termosCandidatosSptrans(new ArrayList<>(List.of("IBIRAPUERA", "VILA MARIANA")))
                    .latitude(-23.5874)
                    .longitude(-46.6576)
                    .build();
        }
        if (norm.contains("LAPA")) {
            return OlhoVivoDTO.LocalResolvido.builder()
                    .termoOriginal(termo)
                    .nomeLocal("Terminal Lapa")
                    .enderecoCompleto("Pça. Miguel Dell'Erba - Lapa, São Paulo - SP")
                    .logradouro("RUA GUAICURUS")
                    .bairro("LAPA")
                    .termoBuscaSptrans("LAPA")
                    .termosCandidatosSptrans(new ArrayList<>(List.of("LAPA")))
                    .latitude(-23.5204)
                    .longitude(-46.7016)
                    .build();
        }

        String limpo = limparTermoParaSptrans(termo);
        return OlhoVivoDTO.LocalResolvido.builder()
                .termoOriginal(termo)
                .nomeLocal(termo.toUpperCase(Locale.ROOT))
                .enderecoCompleto(termo.toUpperCase(Locale.ROOT) + ", São Paulo - SP")
                .logradouro(limpo)
                .bairro(limpo)
                .termoBuscaSptrans(limpo)
                .termosCandidatosSptrans(new ArrayList<>(List.of(limpo, "PAULISTA")))
                .latitude(-23.5614)
                .longitude(-46.6559)
                .build();
    }

    private OlhoVivoDTO.LocalResolvido inferirRegiaoPorCoordenadaMaisProxima(double lat, double lon) {
        record ReferenciaGeo(String nome, String rua, String bairro, String termoSptrans, double lat, double lon) {}
        List<ReferenciaGeo> refs = List.of(
                new ReferenciaGeo("Região do Ipiranga / Ricardo Jafet", "AV DR RICARDO JAFET", "IPIRANGA", "IPIRANGA", -23.5919, -46.6096),
                new ReferenciaGeo("Região do Sacomã / Heliópolis", "RUA CONEGO XAVIER", "SACOMA", "SACOMA", -23.6030, -46.6025),
                new ReferenciaGeo("Região do Cambuci", "AV LINS DE VASCONCELOS", "CAMBUCI", "CAMBUCI", -23.5675, -46.6215),
                new ReferenciaGeo("Região da Vila Mariana", "RUA DOMINGOS DE MORAIS", "VILA MARIANA", "VILA MARIANA", -23.5874, -46.6360),
                new ReferenciaGeo("Região da Av. Paulista", "AV PAULISTA", "BELA VISTA", "PAULISTA", -23.5614, -46.6559),
                new ReferenciaGeo("Região da Lapa", "RUA GUAICURUS", "LAPA", "LAPA", -23.5204, -46.7016),
                new ReferenciaGeo("Região de Pinheiros", "AV BRIG FARIA LIMA", "PINHEIROS", "PINHEIROS", -23.5672, -46.7019),
                new ReferenciaGeo("Região do Butantã", "AV VITAL BRASIL", "BUTANTA", "BUTANTA", -23.5719, -46.7081),
                new ReferenciaGeo("Região da Mooca / Tatuapé", "RUA DA MOOCA", "MOOCA", "MOOCA", -23.5558, -46.5967),
                new ReferenciaGeo("Região de Santo Amaro", "AV ADOLFO PINHEIRO", "SANTO AMARO", "SANTO AMARO", -23.6548, -46.7105),
                new ReferenciaGeo("Região de Santana", "AV CRUZEIRO DO SUL", "SANTANA", "SANTANA", -23.5026, -46.6247),
                new ReferenciaGeo("Região Central / Praça Ramos", "PCA RAMOS DE AZEVEDO", "REPUBLICA", "RAMOS", -23.5453, -46.6395)
        );

        ReferenciaGeo maisProxima = refs.get(0);
        double menorDist = Double.MAX_VALUE;
        for (ReferenciaGeo ref : refs) {
            double d = calcularDistanciaMetros(lat, lon, ref.lat(), ref.lon());
            if (d < menorDist) {
                menorDist = d;
                maisProxima = ref;
            }
        }

        LinkedHashSet<String> candidatos = new LinkedHashSet<>(descobrirAvenidasColetorasProximas(lat, lon));
        candidatos.add(maisProxima.termoSptrans());
        String ruaLimpa = limparTermoParaSptrans(maisProxima.rua());
        if (isTermoSptransValido(ruaLimpa)) {
            candidatos.add(ruaLimpa);
        }

        return OlhoVivoDTO.LocalResolvido.builder()
                .termoOriginal("GPS")
                .nomeLocal(maisProxima.nome())
                .enderecoCompleto(maisProxima.rua() + " - " + maisProxima.bairro() + ", São Paulo - SP")
                .logradouro(maisProxima.rua())
                .bairro(maisProxima.bairro())
                .termoBuscaSptrans(maisProxima.termoSptrans())
                .termosCandidatosSptrans(new ArrayList<>(candidatos))
                .latitude(lat)
                .longitude(lon)
                .build();
    }

    public static double calcularDistanciaMetros(double lat1, double lon1, double lat2, double lon2) {
        if (lat1 == 0.0 || lon1 == 0.0 || lat2 == 0.0 || lon2 == 0.0) {
            return 999999.0;
        }
        final double R = 6371000.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }

    private String primeiroNaoVazio(Map<String, Object> map, String... chaves) {
        for (String c : chaves) {
            Object val = map.get(c);
            if (val != null && !val.toString().isBlank()) {
                return val.toString().trim();
            }
        }
        return "";
    }

    /**
     * Descobre os eixos e avenidas coletoras reais de onibus proximas a uma coordenada GPS (ate 1200m),
     * ordenadas da mais proxima para a mais distante. Evita que ruas residenciais sem ponto caiam direto
     * na busca generica pelo nome inteiro do distrito.
     */
    public List<String> descobrirAvenidasColetorasProximas(double lat, double lon) {
        if (lat == 0.0 || lon == 0.0) {
            return List.of();
        }

        record ViaColetora(String termoSptrans, double lat, double lon) {}
        List<ViaColetora> vias = List.of(
                // Ipiranga / Sacoma / Heliopolis / Cursino
                new ViaColetora("NAZARE", -23.5882, -46.6095),
                new ViaColetora("BOM PASTOR", -23.5868, -46.6071),
                new ViaColetora("RICARDO JAFET", -23.5919, -46.6096),
                new ViaColetora("GENTIL DE MOURA", -23.5948, -46.6068),
                new ViaColetora("SILVA BUENO", -23.5892, -46.6012),
                new ViaColetora("AGOSTINHO GOMES", -23.5835, -46.6038),
                new ViaColetora("CIPRIANO BARATA", -23.5847, -46.6054),
                new ViaColetora("XAVIER", -23.6114, -46.5928),
                new ViaColetora("LAGRIMAS", -23.6068, -46.5974),
                new ViaColetora("ARLINDO VIEIRA", -23.6215, -46.6042),
                new ViaColetora("CURSINO", -23.6145, -46.6182),
                new ViaColetora("TANCREDO NEVES", -23.6042, -46.6134),
                new ViaColetora("ANHAIA MELLO", -23.5825, -46.5842),
                new ViaColetora("JUNTAS PROVISORIAS", -23.5855, -46.5948),
                new ViaColetora("DO ESTADO", -23.5750, -46.6112),
                // Vila Mariana / Saude / Jabaquara / Ibirapuera
                new ViaColetora("DOMINGOS DE MORAIS", -23.5874, -46.6360),
                new ViaColetora("VERGUEIRO", -23.5782, -46.6378),
                new ViaColetora("JABAQUARA", -23.6185, -46.6392),
                new ViaColetora("PEDRO ALVARES CABRAL", -23.5874, -46.6576),
                new ViaColetora("23 DE MAIO", -23.5780, -46.6470),
                new ViaColetora("IBIRAPUERA", -23.5985, -46.6632),
                new ViaColetora("SENA MADUREIRA", -23.5955, -46.6495),
                // Cambuci / Mooca / Centro
                new ViaColetora("LINS DE VASCONCELOS", -23.5675, -46.6215),
                new ViaColetora("LAVAPES", -23.5612, -46.6245),
                new ViaColetora("PAES DE BARROS", -23.5632, -46.5948),
                new ViaColetora("DA MOOCA", -23.5558, -46.5967),
                new ViaColetora("ALCANTARA MACHADO", -23.5482, -46.6088),
                new ViaColetora("RANGEL PESTANA", -23.5478, -46.6275),
                new ViaColetora("LIBERDADE", -23.5552, -46.6358),
                // Paulista / Jardins / Pinheiros / Butanta / Lapa / Santo Amaro / Santana
                new ViaColetora("PAULISTA", -23.5614, -46.6559),
                new ViaColetora("CONSOLACAO", -23.5545, -46.6625),
                new ViaColetora("REBOUCAS", -23.5612, -46.6742),
                new ViaColetora("BRIGADEIRO LUIS ANTONIO", -23.5635, -46.6492),
                new ViaColetora("NOVE DE JULHO", -23.5628, -46.6585),
                new ViaColetora("SANTO AMARO", -23.5975, -46.6782),
                new ViaColetora("FARIA LIMA", -23.5672, -46.6938),
                new ViaColetora("TEODORO SAMPAIO", -23.5618, -46.6825),
                new ViaColetora("CARDEAL ARCOVERDE", -23.5605, -46.6875),
                new ViaColetora("DOS PINHEIROS", -23.5661, -46.6841),
                new ViaColetora("VITAL BRASIL", -23.5719, -46.7081),
                new ViaColetora("FRANCISCO MORATO", -23.5792, -46.7155),
                new ViaColetora("GUAICURUS", -23.5238, -46.6932),
                new ViaColetora("CLELIA", -23.5262, -46.6905),
                new ViaColetora("FRANCISCO MATARAZZO", -23.5276, -46.6745),
                new ViaColetora("CRUZEIRO DO SUL", -23.5026, -46.6247),
                new ViaColetora("VOLUNTARIOS DA PATRIA", -23.4985, -46.6278),
                new ViaColetora("BRAZ LEME", -23.5038, -46.6432),
                new ViaColetora("ADOLFO PINHEIRO", -23.6548, -46.7105),
                new ViaColetora("VEREADOR JOSE DINIZ", -23.6352, -46.6852),
                new ViaColetora("WASHINGTON LUIS", -23.6425, -46.6628)
        );

        record ViaComDistancia(String termo, double dist) {}
        return vias.stream()
                .map(v -> new ViaComDistancia(v.termoSptrans(), calcularDistanciaMetros(lat, lon, v.lat(), v.lon())))
                .filter(vd -> vd.dist() <= 1200.0)
                .sorted(Comparator.comparingDouble(ViaComDistancia::dist))
                .map(ViaComDistancia::termo)
                .distinct()
                .limit(3)
                .toList();
    }

    /**
     * Remove prefixos de tipo de via, numeros de imoveis, palavras de estabelecimentos comerciais
     * ("extra", "supermercado", "hospital", "shopping") e termos geograficos genericos ("Brasil", "Sao Paulo", "GPS")
     * para sobrar apenas o nome real da rua/bairro que a SPTrans usa.
     * Exemplo: "Rua Vergueiro, 1000" -> "VERGUEIRO"
     * Exemplo: "Extra Ricardo Jafet" -> "RICARDO JAFET"
     */
    public String limparTermoParaSptrans(String texto) {
        if (texto == null) return "";
        String norm = normalizar(texto);
        // Remove parenteses como "(ENTRADA ESTACIONAMENTO)" ou "(GPS)"
        norm = norm.replaceAll("\\([^)]*\\)", " ");
        // Remove CEPs
        norm = norm.replaceAll("\\b\\d{5}-?\\d{3}\\b", " ");
        // Remove pontuacao antes de limpar numeros e prefixos
        norm = norm.replaceAll("[,.;:/\\-]+", " ");
        // Remove numeros de imovel isolados (ex.: "VERGUEIRO 1000" -> "VERGUEIRO") preservando se o texto inteiro for um codigo de linha
        String semNumeros = norm.replaceAll("\\b\\d+\\b", " ").replaceAll("\\s+", " ").trim();
        if (semNumeros.length() >= 3) {
            norm = semNumeros;
        }
        // Remove prefixos, comercios e palavras genericas sem apagar "DOM", "SAO", "BRASIL" de nomes de vias
        norm = norm.replaceAll(
                "\\b(AV|AVENIDA|RUA|R|PRACA|PCA|TERMINAL|TERM|PARQUE|PQ|ESTACAO|METRO|CPTM|HOSPITAL|HOSP|CLINICA|SHOPPING|SHOP|EXTRA|CARREFOUR|ASSAI|ATACADAO|SUPERMERCADO|MERCADO|HIPERMERCADO|FACULDADE|UNIVERSIDADE|ESCOLA|COLEGIO|DOUTOR|DR|DOUTORA|DRA|PROFESSOR|PROF|BRIGADEIRO|BRIG|ENGENHEIRO|ENG|CORONEL|CEL|PADRE|PE|ENTRADA|ESTACIONAMENTO|UNIDADE|CAMPUS|SAO PAULO|SP|SUA|MINHA|LOCALIZACAO|ATUAL|GPS|REGIAO|METROPOLITANA|MUNICIPIO|ESTADO|ZONA|SUL|NORTE|LESTE|OESTE|CENTRO)\\b",
                " "
        );
        // Remove algarismos romanos soltos no final como "VILA DOM PEDRO I" -> "VILA DOM PEDRO"
        norm = norm.replaceAll("\\s+", " ").trim();
        norm = norm.replaceAll("\\b(I|II|III|IV|V)$", "").trim();
        return norm;
    }

    /**
     * Extrai o nucleo/sobrenome do logradouro removendo titulos que a SPTrans costuma abreviar
     * (ex.: "Rua Conego Xavier" -> "XAVIER", encontrando "R. CON. XAVIER" na SPTrans).
     */
    public String extrairNucleoLogradouro(String texto) {
        String base = limparTermoParaSptrans(texto);
        if (base.isBlank()) return "";
        String nucleo = base.replaceAll(
                "\\b(CONEGO|CON|CONSELHEIRO|CONS|DEPUTADO|DEP|SENADOR|SEN|GENERAL|GEN|MARECHAL|MAL|PRESIDENTE|PRES|MONSENHOR|MONS|CAPITAO|CAP|MAJOR|MAJ|TENENTE|TEN|ALMIRANTE|ALM|VISCONDE|VISC|BARAO|MARQUES|DOM|DONA|ALAMEDA|AL|ESTRADA|ESTR|LARGO|LGO|TRAVESSA|TV|VIADUTO|VD)\\b",
                " "
        ).replaceAll("\\s+", " ").trim();
        return nucleo.length() >= 3 ? nucleo : "";
    }

    public boolean isTermoSptransValido(String termo) {
        if (termo == null || termo.isBlank() || termo.length() < 3) {
            return false;
        }
        String norm = normalizar(termo);
        return !Set.of(
                "BRASIL", "SAO PAULO", "PAULO", "SAO", "SP", "GPS",
                "LOCALIZACAO", "ATUAL", "SUA LOCALIZACAO", "MINHA LOCALIZACAO",
                "SUA LOCALIZACAO ATUAL", "MINHA LOCALIZACAO ATUAL", "CENTRO", "ZONA SUL", "ZONA NORTE", "ZONA LESTE", "ZONA OESTE"
        ).contains(norm);
    }

    private String normalizar(String texto) {
        if (texto == null) return "";
        return Normalizer.normalize(texto, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "")
                .toUpperCase(Locale.ROOT)
                .trim();
    }

    private Double parseDoubleSafe(String val) {
        if (val == null || val.isBlank()) return null;
        try {
            return Double.parseDouble(val);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class NominatimResult {
        @JsonProperty("name")
        private String name;

        @JsonProperty("display_name")
        private String displayName;

        @JsonProperty("lat")
        private String lat;

        @JsonProperty("lon")
        private String lon;

        @JsonProperty("address")
        private Map<String, Object> address;
    }
}
