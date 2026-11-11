package br.com.viacidada.repository;

import br.com.viacidada.entity.Role;
import br.com.viacidada.entity.Usuario;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    // Query Method derivado
    Optional<Usuario> findByEmailIgnoreCase(String email);

    // Query Method derivado
    boolean existsByEmailIgnoreCase(String email);

    // @Query com JPQL
    @Query("SELECT u FROM Usuario u WHERE (:role IS NULL OR u.role = :role) " +
           "AND (:mobilidadeReduzida IS NULL OR u.possuiMobilidadeReduzida = :mobilidadeReduzida)")
    Page<Usuario> buscarPorPerfilEAcessibilidade(
            @Param("role") Role role,
            @Param("mobilidadeReduzida") Boolean mobilidadeReduzida,
            Pageable pageable
    );
}
