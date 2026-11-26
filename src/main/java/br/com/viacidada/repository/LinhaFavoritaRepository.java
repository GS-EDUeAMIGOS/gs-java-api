package br.com.viacidada.repository;

import br.com.viacidada.entity.LinhaFavorita;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

// Faz o acesso ao banco de dados para a tabela de linhas favoritas (CRUD, Query Methods e JPQL).
@Repository
public interface LinhaFavoritaRepository extends JpaRepository<LinhaFavorita, Long> {

    // Query Method derivado: consulta simples gerada pelo nome do metodo (lista linhas favoritas do usuario com paginacao).
    Page<LinhaFavorita> findByUsuarioId(Long usuarioId, Pageable pageable);

    // Query Method derivado: consulta simples para checar se o usuario ja favoritou essa linha antes de salvar.
    boolean existsByUsuarioIdAndCodigoLinha(Long usuarioId, Integer codigoLinha);

    // @Query com JPQL: regra de negocio especifica para buscar por texto em letreiro, apelido ou terminal sem poluir o nome do metodo.
    @Query("SELECT lf FROM LinhaFavorita lf WHERE lf.usuario.id = :usuarioId " +
           "AND (LOWER(lf.letreiro) LIKE LOWER(CONCAT('%', :termo, '%')) " +
           "OR LOWER(lf.apelido) LIKE LOWER(CONCAT('%', :termo, '%')) " +
           "OR LOWER(lf.terminalDestino) LIKE LOWER(CONCAT('%', :termo, '%')))")
    List<LinhaFavorita> buscarFavoritasPorTermo(
            @Param("usuarioId") Long usuarioId,
            @Param("termo") String termo
    );
}
