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

// Faz o acesso ao banco de dados para a tabela de usuarios (busca por e-mail, verificacao de duplicidade e JPQL).
@Repository
public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    // Query Method derivado: consulta simples gerada pelo nome do metodo para buscar usuario pelo e-mail no login.
    Optional<Usuario> findByEmailIgnoreCase(String email);

    // Query Method derivado: consulta simples para validar se o e-mail ja esta cadastrado antes de criar conta.
    boolean existsByEmailIgnoreCase(String email);

    // @Query com JPQL: regra de negocio especifica para filtrar usuarios por perfil e mobilidade reduzida (aceita filtros opcionais).
    @Query("SELECT u FROM Usuario u WHERE (:role IS NULL OR u.role = :role) " +
           "AND (:mobilidadeReduzida IS NULL OR u.possuiMobilidadeReduzida = :mobilidadeReduzida)")
    Page<Usuario> buscarPorPerfilEAcessibilidade(
            @Param("role") Role role,
            @Param("mobilidadeReduzida") Boolean mobilidadeReduzida,
            Pageable pageable
    );
}
