package br.com.viacidada.repository;

import br.com.viacidada.entity.ParadaFavorita;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ParadaFavoritaRepository extends JpaRepository<ParadaFavorita, Long> {

    // Query Method derivado
    Page<ParadaFavorita> findByUsuarioId(Long usuarioId, Pageable pageable);

    // Query Method derivado
    List<ParadaFavorita> findByUsuarioIdAndPossuiRampaAcessivelTrue(Long usuarioId);

    // @Query com JPQL
    @Query("SELECT pf FROM ParadaFavorita pf WHERE pf.usuario.id = :usuarioId " +
           "AND (pf.possuiRampaAcessivel = true OR pf.possuiPisoTatil = true)")
    List<ParadaFavorita> buscarParadasAcessiveisDoUsuario(@Param("usuarioId") Long usuarioId);
}
