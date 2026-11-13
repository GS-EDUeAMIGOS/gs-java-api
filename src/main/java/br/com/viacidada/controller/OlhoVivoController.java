package br.com.viacidada.controller;

import br.com.viacidada.dto.OlhoVivoDTO;
import br.com.viacidada.service.OlhoVivoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "2. SPTrans Olho Vivo & Geolocalizacao", description = "Endpoints que consomem a API publica Olho Vivo da SPTrans e Geocodificacao OpenStreetMap")
public class OlhoVivoController {

    private final OlhoVivoService olhoVivoService;

    @GetMapping("/linhas")
    @Operation(summary = "Buscar linhas da SPTrans por termo ou numero (com @Cacheable)")
    public ResponseEntity<List<OlhoVivoDTO.LinhaResumo>> buscarLinhas(@RequestParam(defaultValue = "8000") String termo) {
        return ResponseEntity.ok(olhoVivoService.buscarLinhas(termo));
    }

    @GetMapping("/linhas/{codigo}/veiculos")
    @Operation(summary = "Consultar posicao em tempo real e acessibilidade PcD dos veiculos de uma linha")
    public ResponseEntity<OlhoVivoDTO.PosicaoLinhaResponse> buscarVeiculosPorLinha(@PathVariable Integer codigo) {
        return ResponseEntity.ok(olhoVivoService.buscarVeiculosPorLinha(codigo));
    }

    @GetMapping("/linhas/{codigo}/paradas")
    @Operation(summary = "Consultar paradas geolocalizadas por onde uma linha de onibus passa (com fallback inteligente por regiao)")
    public ResponseEntity<List<OlhoVivoDTO.ParadaResumo>> buscarParadasPorLinha(
            @PathVariable Integer codigo,
            @RequestParam(required = false) String termo
    ) {
        return ResponseEntity.ok(olhoVivoService.buscarParadasPorLinha(codigo, termo));
    }

    @GetMapping("/paradas")
    @Operation(summary = "Buscar paradas da SPTrans por nome ou endereco (com @Cacheable)")
    public ResponseEntity<List<OlhoVivoDTO.ParadaResumo>> buscarParadas(@RequestParam(defaultValue = "PAULISTA") String termo) {
        return ResponseEntity.ok(olhoVivoService.buscarParadas(termo));
    }

    @GetMapping("/paradas/proximas")
    @Operation(summary = "Descobrir a parada de onibus mais proxima a partir do GPS do celular do cidadao e verificar se ele esta no ponto")
    public ResponseEntity<OlhoVivoDTO.ParadasProximasGpsResponse> buscarParadasProximasPorGps(
            @RequestParam double lat,
            @RequestParam double lon,
            @RequestParam(required = false) String termo
    ) {
        return ResponseEntity.ok(olhoVivoService.buscarParadasProximasPorGps(lat, lon, termo));
    }

    @GetMapping("/paradas/{codigo}/previsoes")
    @Operation(summary = "Consultar previsao de chegada dos veiculos em uma parada especifica")
    public ResponseEntity<OlhoVivoDTO.PrevisaoParadaResponse> buscarPrevisoesPorParada(@PathVariable Long codigo) {
        return ResponseEntity.ok(olhoVivoService.buscarPrevisoesPorParada(codigo));
    }

    @GetMapping("/locais/buscar")
    @Operation(summary = "Geocodificar locais reais (ex.: Hospital Heliopolis, Shopping, USP) para coordenadas e bairros de Sao Paulo")
    public ResponseEntity<List<OlhoVivoDTO.LocalResolvido>> buscarLocais(@RequestParam String q) {
        return ResponseEntity.ok(olhoVivoService.buscarSugestoesLocais(q));
    }

    @GetMapping("/rotas/planejar")
    @Operation(summary = "Planejar rota completa (caminhada ate o ponto + onibus direto ou baldeacao + caminhada ate o destino) usando GPS ou nome de local")
    public ResponseEntity<OlhoVivoDTO.RotaPlanejadaResponse> planejarRota(
            @RequestParam(defaultValue = "LAPA") String origem,
            @RequestParam(defaultValue = "PAULISTA") String destino,
            @RequestParam(required = false) Double latOrigem,
            @RequestParam(required = false) Double lonOrigem
    ) {
        return ResponseEntity.ok(olhoVivoService.planejarRota(origem, destino, latOrigem, lonOrigem));
    }

    @DeleteMapping("/sptrans/cache")
    @Operation(summary = "Limpar cache de linhas e paradas da SPTrans (@CacheEvict)")
    public ResponseEntity<Void> limparCache() {
        olhoVivoService.limparCacheSptrans();
        return ResponseEntity.noContent().build();
    }
}
