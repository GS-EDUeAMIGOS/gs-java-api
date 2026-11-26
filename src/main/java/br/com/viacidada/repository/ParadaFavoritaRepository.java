package br.com.viacidada.repository;

import br.com.viacidada.entity.ParadaFavorita;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

// Faz o acesso ao banco de dados para a tabela de paradas favoritas (CRUD, Query Methods e JPQL).
@Repository
public interface ParadaFavoritaRepository extends JpaRepository<ParadaFavorita, Long> {

    // Query Method derivado: consulta simples gerada pelo nome do metodo (lista paradas favoritas do usuario com paginacao).
    Page<ParadaFavorita> findByUsuarioId(Long usuarioId, Pageable pageable);

    // Query Method derivado: consulta simples pelo nome do metodo para buscar paradas do usuario que possuem rampa.
    List<ParadaFavorita> findByUsuarioIdAndPossuiRampaAcessivelTrue(Long usuarioId);

    // @Query com JPQL: regra de negocio especifica (paradas com rampa OU piso tatil) sem gerar um nome de metodo gigante.
    @Query("SELECT pf FROM ParadaFavorita pf WHERE pf.usuario.id = :usuarioId " +
           "AND (pf.possuiRampaAcessivel = true OR pf.possuiPisoTatil = true)")
    List<ParadaFavorita> buscarParadasAcessiveisDoUsuario(@Param("usuarioId") Long usuarioId);
}
