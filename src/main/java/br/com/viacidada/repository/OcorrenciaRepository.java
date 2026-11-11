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

@Repository
public interface OcorrenciaRepository extends JpaRepository<Ocorrencia, Long>, JpaSpecificationExecutor<Ocorrencia> {

    // Query Method derivado
    Page<Ocorrencia> findByStatus(StatusOcorrencia status, Pageable pageable);

    // Query Method derivado
    List<Ocorrencia> findByCodigoLinhaAndStatusNot(Integer codigoLinha, StatusOcorrencia status);

    // @Query com JPQL
    @Query("SELECT o FROM Ocorrencia o WHERE o.afetaAcessibilidade = true " +
           "AND o.status IN :statusAtivos " +
           "AND (:tipo IS NULL OR o.tipo = :tipo) " +
           "ORDER BY o.criadoEm DESC")
    List<Ocorrencia> buscarOcorrenciasCriticasAcessibilidade(
            @Param("statusAtivos") List<StatusOcorrencia> statusAtivos,
            @Param("tipo") TipoOcorrencia tipo
    );
}
