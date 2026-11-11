package br.com.viacidada.mapper;

import br.com.viacidada.dto.*;
import br.com.viacidada.entity.*;

public final class DomainMapper {

    private DomainMapper() {
    }

    public static UsuarioDTO.Response toUsuarioResponse(Usuario entity) {
        return UsuarioDTO.Response.builder()
                .id(entity.getId())
                .nome(entity.getNome())
                .email(entity.getEmail())
                .role(entity.getRole())
                .possuiMobilidadeReduzida(entity.getPossuiMobilidadeReduzida())
                .criadoEm(entity.getCriadoEm())
                .build();
    }

    public static LinhaFavoritaDTO.Response toLinhaFavoritaResponse(LinhaFavorita entity) {
        return LinhaFavoritaDTO.Response.builder()
                .id(entity.getId())
                .usuarioId(entity.getUsuario() != null ? entity.getUsuario().getId() : null)
                .codigoLinha(entity.getCodigoLinha())
                .letreiro(entity.getLetreiro())
                .sentido(entity.getSentido())
                .terminalOrigem(entity.getTerminalOrigem())
                .terminalDestino(entity.getTerminalDestino())
                .apelido(entity.getApelido())
                .criadoEm(entity.getCriadoEm())
                .build();
    }

    public static ParadaFavoritaDTO.Response toParadaFavoritaResponse(ParadaFavorita entity) {
        return ParadaFavoritaDTO.Response.builder()
                .id(entity.getId())
                .usuarioId(entity.getUsuario() != null ? entity.getUsuario().getId() : null)
                .codigoParada(entity.getCodigoParada())
                .nomeParada(entity.getNomeParada())
                .endereco(entity.getEndereco())
                .latitude(entity.getLatitude())
                .longitude(entity.getLongitude())
                .possuiRampaAcessivel(entity.getPossuiRampaAcessivel())
                .possuiPisoTatil(entity.getPossuiPisoTatil())
                .criadoEm(entity.getCriadoEm())
                .build();
    }

    public static AlertaDTO.Response toAlertaResponse(Alerta entity) {
        return AlertaDTO.Response.builder()
                .id(entity.getId())
                .usuarioId(entity.getUsuario() != null ? entity.getUsuario().getId() : null)
                .titulo(entity.getTitulo())
                .mensagem(entity.getMensagem())
                .codigoLinha(entity.getCodigoLinha())
                .codigoParada(entity.getCodigoParada())
                .somenteVeiculoAcessivel(entity.getSomenteVeiculoAcessivel())
                .horarioNotificacao(entity.getHorarioNotificacao())
                .ativo(entity.getAtivo())
                .criadoEm(entity.getCriadoEm())
                .build();
    }

    public static OcorrenciaDTO.Response toOcorrenciaResponse(Ocorrencia entity) {
        return OcorrenciaDTO.Response.builder()
                .id(entity.getId())
                .usuarioId(entity.getUsuario() != null ? entity.getUsuario().getId() : null)
                .nomeUsuario(entity.getUsuario() != null ? entity.getUsuario().getNome() : null)
                .titulo(entity.getTitulo())
                .descricao(entity.getDescricao())
                .tipo(entity.getTipo())
                .status(entity.getStatus())
                .gravidade(entity.getGravidade())
                .codigoLinha(entity.getCodigoLinha())
                .codigoParada(entity.getCodigoParada())
                .prefixoVeiculo(entity.getPrefixoVeiculo())
                .afetaAcessibilidade(entity.getAfetaAcessibilidade())
                .criadoEm(entity.getCriadoEm())
                .atualizadoEm(entity.getAtualizadoEm())
                .build();
    }
}
