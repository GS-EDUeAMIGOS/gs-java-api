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
@Tag(name = "2. SPTrans Olho Vivo", description = "Endpoints proprios que consomem e protegem a API publica Olho Vivo da SPTrans")
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

    @GetMapping("/paradas")
    @Operation(summary = "Buscar paradas da SPTrans por nome ou endereco (com @Cacheable)")
    public ResponseEntity<List<OlhoVivoDTO.ParadaResumo>> buscarParadas(@RequestParam(defaultValue = "PAULISTA") String termo) {
        return ResponseEntity.ok(olhoVivoService.buscarParadas(termo));
    }

    @GetMapping("/paradas/{codigo}/previsoes")
    @Operation(summary = "Consultar previsao de chegada dos veiculos em uma parada especifica")
    public ResponseEntity<OlhoVivoDTO.PrevisaoParadaResponse> buscarPrevisoesPorParada(@PathVariable Long codigo) {
        return ResponseEntity.ok(olhoVivoService.buscarPrevisoesPorParada(codigo));
    }

    @DeleteMapping("/sptrans/cache")
    @Operation(summary = "Limpar cache de linhas e paradas da SPTrans (@CacheEvict)")
    public ResponseEntity<Void> limparCache() {
        olhoVivoService.limparCacheSptrans();
        return ResponseEntity.noContent().build();
    }
}
