package br.com.viacidada.repository;

import br.com.viacidada.entity.Alerta;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AlertaRepository extends JpaRepository<Alerta, Long> {

    // Query Method derivado
    Page<Alerta> findByUsuarioId(Long usuarioId, Pageable pageable);

    // Query Method derivado
    List<Alerta> findByUsuarioIdAndAtivoTrue(Long usuarioId);

    // @Query com JPQL
    @Query("SELECT a FROM Alerta a WHERE a.ativo = true " +
           "AND (:codigoLinha IS NULL OR a.codigoLinha = :codigoLinha) " +
           "AND (:codigoParada IS NULL OR a.codigoParada = :codigoParada)")
    List<Alerta> buscarAlertasAtivosPorLinhaOuParada(
            @Param("codigoLinha") Integer codigoLinha,
            @Param("codigoParada") Long codigoParada
    );
}
