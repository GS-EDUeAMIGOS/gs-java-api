package br.com.viacidada.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "tb_vc_linha_favorita")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LinhaFavorita {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "codigo_linha", nullable = false)
    private Integer codigoLinha;

    @Column(nullable = false, length = 30)
    private String letreiro;

    @Column(name = "sentido", nullable = false)
    private Integer sentido;

    @Column(name = "terminal_origem", length = 120)
    private String terminalOrigem;

    @Column(name = "terminal_destino", length = 120)
    private String terminalDestino;

    @Column(length = 100)
    private String apelido;

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
    }
}
