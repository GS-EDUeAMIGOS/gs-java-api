package br.com.viacidada.config;

import br.com.viacidada.entity.*;
import br.com.viacidada.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@Profile({"h2", "test"})
@RequiredArgsConstructor
@Slf4j
public class DataSeederConfig implements CommandLineRunner {

    private final UsuarioRepository usuarioRepository;
    private final LinhaFavoritaRepository linhaFavoritaRepository;
    private final ParadaFavoritaRepository paradaFavoritaRepository;
    private final AlertaRepository alertaRepository;
    private final OcorrenciaRepository ocorrenciaRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        if (usuarioRepository.count() > 0) {
            return;
        }

        log.info("Inicializando dados de demonstracao no banco H2 para o ViaCidada...");

        Usuario admin = usuarioRepository.save(Usuario.builder()
                .nome("Administrador ViaCidada")
                .email("admin@viacidada.com.br")
                .senha(passwordEncoder.encode("Admin@123"))
                .role(Role.ROLE_ADMIN)
                .possuiMobilidadeReduzida(false)
                .build());

        Usuario cidadao = usuarioRepository.save(Usuario.builder()
                .nome("Maria Silva")
                .email("cidadao@viacidada.com.br")
                .senha(passwordEncoder.encode("Cidadao@123"))
                .role(Role.ROLE_CIDADAO)
                .possuiMobilidadeReduzida(true)
                .build());

        linhaFavoritaRepository.save(LinhaFavorita.builder()
                .usuario(cidadao)
                .codigoLinha(2506)
                .letreiro("8000-10")
                .sentido(1)
                .terminalOrigem("TERM. LAPA")
                .terminalDestino("PCA. RAMOS DE AZEVEDO")
                .apelido("Trabalho - Centro")
                .build());

        paradaFavoritaRepository.save(ParadaFavorita.builder()
                .usuario(cidadao)
                .codigoParada(340015329L)
                .nomeParada("PARADA PAULISTA B/C")
                .endereco("AV. PAULISTA, 1106")
                .latitude(-23.5650)
                .longitude(-46.6521)
                .possuiRampaAcessivel(true)
                .possuiPisoTatil(true)
                .build());

        alertaRepository.save(Alerta.builder()
                .usuario(cidadao)
                .titulo("Aviso de onibus acessivel na Paulista")
                .mensagem("Notificar quando veiculo com elevador para cadeirante estiver a menos de 5 minutos.")
                .codigoLinha(2506)
                .codigoParada(340015329L)
                .somenteVeiculoAcessivel(true)
                .horarioNotificacao("07:30")
                .ativo(true)
                .build());

        ocorrenciaRepository.save(Ocorrencia.builder()
                .usuario(cidadao)
                .titulo("Elevador de acessibilidade travado")
                .descricao("O veiculo prefixo 12540 da linha 8000-10 apresentou falha no elevador na Parada Paulista.")
                .tipo(TipoOcorrencia.ELEVADOR_INOPERANTE)
                .status(StatusOcorrencia.ABERTA)
                .gravidade(NivelGravidade.ALTA)
                .codigoLinha(2506)
                .codigoParada(340015329L)
                .prefixoVeiculo("12540")
                .afetaAcessibilidade(true)
                .build());

        ocorrenciaRepository.save(Ocorrencia.builder()
                .usuario(admin)
                .titulo("Piso tatil danificado na calcada da parada")
                .descricao("Placas do piso tatil soltas proximo ao abrigo da parada 340015329.")
                .tipo(TipoOcorrencia.INFRAESTRUTURA_PARADA)
                .status(StatusOcorrencia.CONFIRMADA)
                .gravidade(NivelGravidade.MEDIA)
                .codigoParada(340015329L)
                .afetaAcessibilidade(true)
                .build());

        log.info("Dados iniciais carregados com sucesso!");
    }
}
