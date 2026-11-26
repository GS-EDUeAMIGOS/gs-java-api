package br.com.viacidada.repository;

import br.com.viacidada.entity.Ocorrencia;
import br.com.viacidada.entity.StatusOcorrencia;
import br.com.viacidada.entity.TipoOcorrencia;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

// Faz o acesso ao banco de dados para ocorrencias; estende JpaSpecificationExecutor para suportar filtros dinamicos (OcorrenciaSpecification).
@Repository
public interface OcorrenciaRepository extends JpaRepository<Ocorrencia, Long>, JpaSpecificationExecutor<Ocorrencia> {

    // Query Method derivado: consulta simples gerada pelo nome do metodo para filtrar por um unico status com paginacao.
    Page<Ocorrencia> findByStatus(StatusOcorrencia status, Pageable pageable);

    // Query Method derivado: consulta simples pelo nome do metodo (ocorrencias da linha que nao estao com certo status).
    List<Ocorrencia> findByCodigoLinhaAndStatusNot(Integer codigoLinha, StatusOcorrencia status);

    // @Query com JPQL: regra de negocio especifica (acessibilidade critica, lista de status ativos, filtro opcional e ordenacao).
    @Query("SELECT o FROM Ocorrencia o WHERE o.afetaAcessibilidade = true " +
           "AND o.status IN :statusAtivos " +
           "AND (:tipo IS NULL OR o.tipo = :tipo) " +
           "ORDER BY o.criadoEm DESC")
    List<Ocorrencia> buscarOcorrenciasCriticasAcessibilidade(
            @Param("statusAtivos") List<StatusOcorrencia> statusAtivos,
            @Param("tipo") TipoOcorrencia tipo
    );
}
