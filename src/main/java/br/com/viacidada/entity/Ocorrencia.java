package br.com.viacidada.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "tb_vc_ocorrencia")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Ocorrencia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String titulo;

    @Column(nullable = false, length = 1000)
    private String descricao;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private TipoOcorrencia tipo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private StatusOcorrencia status = StatusOcorrencia.ABERTA;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private NivelGravidade gravidade = NivelGravidade.MEDIA;

    @Column(name = "codigo_linha")
    private Integer codigoLinha;

    @Column(name = "codigo_parada")
    private Long codigoParada;

    @Column(name = "prefixo_veiculo", length = 30)
    private String prefixoVeiculo;

    @Column(name = "afeta_acessibilidade", nullable = false)
    @Builder.Default
    private Boolean afetaAcessibilidade = false;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private LocalDateTime criadoEm;

    @Column(name = "atualizado_em")
    private LocalDateTime atualizadoEm;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @PrePersist
    protected void onCreate() {
        LocalDateTime agora = LocalDateTime.now();
        if (this.criadoEm == null) {
            this.criadoEm = agora;
        }
        this.atualizadoEm = agora;
        if (this.status == null) {
            this.status = StatusOcorrencia.ABERTA;
        }
        if (this.gravidade == null) {
            this.gravidade = NivelGravidade.MEDIA;
        }
        if (this.afetaAcessibilidade == null) {
            this.afetaAcessibilidade = false;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.atualizadoEm = LocalDateTime.now();
    }
}
