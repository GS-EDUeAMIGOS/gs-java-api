package br.com.viacidada.specification;

import br.com.viacidada.entity.NivelGravidade;
import br.com.viacidada.entity.Ocorrencia;
import br.com.viacidada.entity.StatusOcorrencia;
import br.com.viacidada.entity.TipoOcorrencia;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

public final class OcorrenciaSpecification {

    private OcorrenciaSpecification() {
    }

    public static Specification<Ocorrencia> comFiltros(
            TipoOcorrencia tipo,
            StatusOcorrencia status,
            NivelGravidade gravidade,
            Integer codigoLinha,
            Long codigoParada,
            Boolean afetaAcessibilidade,
            String termo
    ) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (tipo != null) {
                predicates.add(cb.equal(root.get("tipo"), tipo));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (gravidade != null) {
                predicates.add(cb.equal(root.get("gravidade"), gravidade));
            }
            if (codigoLinha != null) {
                predicates.add(cb.equal(root.get("codigoLinha"), codigoLinha));
            }
            if (codigoParada != null) {
                predicates.add(cb.equal(root.get("codigoParada"), codigoParada));
            }
            if (afetaAcessibilidade != null) {
                predicates.add(cb.equal(root.get("afetaAcessibilidade"), afetaAcessibilidade));
            }
            if (termo != null && !termo.isBlank()) {
                String padrao = "%" + termo.trim().toLowerCase() + "%";
                Predicate tituloLike = cb.like(cb.lower(root.get("titulo")), padrao);
                Predicate descricaoLike = cb.like(cb.lower(root.get("descricao")), padrao);
                predicates.add(cb.or(tituloLike, descricaoLike));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
