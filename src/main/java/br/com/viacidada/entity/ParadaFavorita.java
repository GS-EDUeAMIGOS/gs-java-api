package br.com.viacidada.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "tb_vc_parada_favorita")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ParadaFavorita {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "codigo_parada", nullable = false)
    private Long codigoParada;

    @Column(name = "nome_parada", nullable = false, length = 150)
    private String nomeParada;

    @Column(length = 250)
    private String endereco;

    @Column
    private Double latitude;

    @Column
    private Double longitude;

    @Column(name = "possui_rampa_acessivel", nullable = false)
    @Builder.Default
    private Boolean possuiRampaAcessivel = false;

    @Column(name = "possui_piso_tatil", nullable = false)
    @Builder.Default
    private Boolean possuiPisoTatil = false;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private LocalDateTime criadoEm;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @PrePersist
    protected void onCreate() {
        if (this.criadoEm == null) {
            this.criadoEm = LocalDateTime.now();
        }
        if (this.possuiRampaAcessivel == null) {
            this.possuiRampaAcessivel = false;
        }
        if (this.possuiPisoTatil == null) {
            this.possuiPisoTatil = false;
        }
    }
}
