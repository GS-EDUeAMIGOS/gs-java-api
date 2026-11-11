package br.com.viacidada.repository;

import br.com.viacidada.entity.LinhaFavorita;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LinhaFavoritaRepository extends JpaRepository<LinhaFavorita, Long> {

    // Query Method derivado
    Page<LinhaFavorita> findByUsuarioId(Long usuarioId, Pageable pageable);

    // Query Method derivado
    boolean existsByUsuarioIdAndCodigoLinha(Long usuarioId, Integer codigoLinha);

    // @Query com JPQL
    @Query("SELECT lf FROM LinhaFavorita lf WHERE lf.usuario.id = :usuarioId " +
           "AND (LOWER(lf.letreiro) LIKE LOWER(CONCAT('%', :termo, '%')) " +
           "OR LOWER(lf.apelido) LIKE LOWER(CONCAT('%', :termo, '%')) " +
           "OR LOWER(lf.terminalDestino) LIKE LOWER(CONCAT('%', :termo, '%')))")
    List<LinhaFavorita> buscarFavoritasPorTermo(
            @Param("usuarioId") Long usuarioId,
            @Param("termo") String termo
    );
}
