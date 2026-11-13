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
                    if (ruaLimpa.length() >= 3 && !local.getTermosCandidatosSptrans().contains(ruaLimpa)) {
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
        if (!termoUsuarioLimpo.isBlank()
                && !termoUsuarioLimpo.equalsIgnoreCase("SUA LOCALIZACAO ATUAL")
                && !termoUsuarioLimpo.equalsIgnoreCase("MINHA LOCALIZACAO")
                && termoUsuarioLimpo.length() >= 3) {
            set.add(termoUsuarioLimpo);
        }

        // 2. Logradouro oficial (ex.: "Avenida Doutor Ricardo Jafet" -> "RICARDO JAFET", "Rua Eiras Garcia" -> "EIRAS GARCIA")
        String ruaLimpa = limparTermoParaSptrans(road);
        if (ruaLimpa.length() >= 3) {
            set.add(ruaLimpa);
        }

        // 3. Distrito oficial de Sao Paulo (ex.: "Ipiranga", "Sacomã", "Cambuci", "Vila Mariana", "Pinheiros")
        String distritoLimpo = limparTermoParaSptrans(district);
        if (distritoLimpo.length() >= 3) {
            set.add(distritoLimpo);
        }

        // 4. Sub-bairro / Vila (ex.: "Vila Dom Pedro I" -> "VILA DOM PEDRO", "Cerqueira César")
        String suburbLimpo = limparTermoParaSptrans(suburb);
        if (suburbLimpo.length() >= 3) {
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
        if (tudoNorm.contains("CAMBUCI")) {
            set.add("CAMBUCI");
            set.add("IPIRANGA");
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

        return OlhoVivoDTO.LocalResolvido.builder()
                .termoOriginal("GPS")
                .nomeLocal(maisProxima.nome())
                .enderecoCompleto(maisProxima.rua() + " - " + maisProxima.bairro() + ", São Paulo - SP")
                .logradouro(maisProxima.rua())
                .bairro(maisProxima.bairro())
                .termoBuscaSptrans(maisProxima.termoSptrans())
                .termosCandidatosSptrans(new ArrayList<>(List.of(maisProxima.termoSptrans(), maisProxima.rua())))
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
     * Remove prefixos de tipo de via e palavras de estabelecimentos comerciais ("extra", "supermercado",
     * "hospital", "shopping", "doutor", "dr", etc.) para sobrar apenas o nome que a SPTrans usa!
     * Exemplo: "Extra Ricardo Jafet" -> "RICARDO JAFET"
     * Exemplo: "Avenida Doutor Ricardo Jafet" -> "RICARDO JAFET"
     */
    public String limparTermoParaSptrans(String texto) {
        if (texto == null) return "";
        String norm = normalizar(texto);
        // Remove parenteses como "(ENTRADA ESTACIONAMENTO)"
        norm = norm.replaceAll("\\([^)]*\\)", " ");
        // Remove prefixos e palavras de comercios/instituicoes
        norm = norm.replaceAll(
                "\\b(AV|AVENIDA|RUA|R|PRACA|PCA|TERMINAL|TERM|PARQUE|PQ|ESTACAO|METRO|HOSPITAL|HOSP|CLINICA|SHOPPING|SHOP|EXTRA|CARREFOUR|ASSAI|ATACADAO|SUPERMERCADO|MERCADO|HIPERMERCADO|FACULDADE|UNIVERSIDADE|ESCOLA|COLEGIO|DOUTOR|DR|DOUTORA|DRA|PROFESSOR|PROF|BRIGADEIRO|BRIG|ENGENHEIRO|ENG|CORONEL|CEL|PADRE|PE|DOM|DONA|ENTRADA|ESTACIONAMENTO|UNIDADE|CAMPUS)\\b",
                " "
        );
        // Remove algarismos romanos soltos no final como "VILA DOM PEDRO I" -> "VILA DOM PEDRO"
        norm = norm.replaceAll("\\b(I|II|III|IV|V)\\b$", " ");
        norm = norm.replaceAll("\\s+", " ").trim();
        return norm.isEmpty() ? normalizar(texto) : norm;
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
