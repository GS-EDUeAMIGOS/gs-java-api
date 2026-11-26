package br.com.viacidada.repository;

import br.com.viacidada.entity.Alerta;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

// Faz o acesso ao banco de dados para a tabela de alertas (CRUD, Query Methods e JPQL).
@Repository
public interface AlertaRepository extends JpaRepository<Alerta, Long> {

    // Query Method derivado: consulta simples e fixa gerada pelo nome do metodo (busca alertas do usuario com paginacao).
    Page<Alerta> findByUsuarioId(Long usuarioId, Pageable pageable);

    // Query Method derivado: consulta simples pelo nome do metodo para buscar apenas alertas ativos do usuario.
    List<Alerta> findByUsuarioIdAndAtivoTrue(Long usuarioId);

    // @Query com JPQL: regra de negocio especifica com filtros opcionais (linha/parada) portavel entre H2 e Oracle.
    @Query("SELECT a FROM Alerta a WHERE a.ativo = true " +
           "AND (:codigoLinha IS NULL OR a.codigoLinha = :codigoLinha) " +
           "AND (:codigoParada IS NULL OR a.codigoParada = :codigoParada)")
    List<Alerta> buscarAlertasAtivosPorLinhaOuParada(
            @Param("codigoLinha") Integer codigoLinha,
            @Param("codigoParada") Long codigoParada
    );
}
