package br.com.viacidada.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "tb_vc_alerta")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Alerta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String titulo;

    @Column(nullable = false, length = 500)
    private String mensagem;

    @Column(name = "codigo_linha")
    private Integer codigoLinha;

    @Column(name = "codigo_parada")
    private Long codigoParada;

    @Column(name = "somente_veiculo_acessivel", nullable = false)
    @Builder.Default
    private Boolean somenteVeiculoAcessivel = false;

    @Column(name = "horario_notificacao", length = 20)
    private String horarioNotificacao;

    @Column(nullable = false)
    @Builder.Default
    private Boolean ativo = true;

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
        if (this.ativo == null) {
            this.ativo = true;
        }
        if (this.somenteVeiculoAcessivel == null) {
            this.somenteVeiculoAcessivel = false;
        }
    }
}
