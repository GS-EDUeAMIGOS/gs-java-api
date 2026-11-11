# ViaCidadã API — Global Solution FIAP 2026/2 (CitizenTech)

API RESTful em **Java 21 + Spring Boot 3.4** para mobilidade urbana acessível, informações públicas de transporte em tempo real (integração com a API pública **Olho Vivo da SPTrans**) e participação cidadã.

O backend atua como camada segura para o aplicativo mobile em Flutter, garantindo que a chave de autenticação da SPTrans (`SPTRANS_TOKEN`) permaneça protegida no servidor.

---

## 1. Arquitetura e Pacotes

```text
br.com.viacidada
 ├─ config          # Configurações de Cache, OpenAPI/Swagger e DataSeeder (H2)
 ├─ controller      # Endpoints RESTful com paginação, ordenação e links HATEOAS
 ├─ service         # Regras de negócio, @Cacheable e @CacheEvict
 ├─ repository      # Interfaces Spring Data JPA (Query Methods, @Query JPQL e Specification)
 ├─ entity          # Entidades JPA e Enums de domínio
 ├─ dto             # Objetos de transferência de dados com Bean Validation e HATEOAS
 ├─ mapper          # Conversão entre Entidades e DTOs
 ├─ exception       # Tratamento global de erros com @RestControllerAdvice
 ├─ security        # Autenticação/Autorização Stateless com JWT, BCrypt e CORS para Mobile
 ├─ client          # Cliente HTTP para a API Olho Vivo da SPTrans (com fallback local)
 └─ specification   # Filtros dinâmicos com JPA Criteria/Specification
```

---

## 2. Modelagem de Domínio e Relacionamentos

A solução possui 5 entidades principais com relacionamentos `1:N` a partir de `Usuario`:

- **`Usuario` (`tb_vc_usuario`)**: Cidadão ou administrador (`Role`: `ROLE_CIDADAO`, `ROLE_ADMIN`), com indicador `possuiMobilidadeReduzida`.
  - `1:N` -> **`LinhaFavorita` (`tb_vc_linha_favorita`)**: Linhas da SPTrans monitoradas pelo usuário (`codigoLinha`, `letreiro`, `sentido`, `terminalOrigem`, `terminalDestino`, `apelido`).
  - `1:N` -> **`ParadaFavorita` (`tb_vc_parada_favorita`)**: Paradas monitoradas com indicadores de acessibilidade (`possuiRampaAcessivel`, `possuiPisoTatil`, coordenadas).
  - `1:N` -> **`Alerta` (`tb_vc_alerta`)**: Alertas configurados pelo cidadão para linhas/paradas específicas, incluindo filtro `somenteVeiculoAcessivel`.
  - `1:N` -> **`Ocorrencia` (`tb_vc_ocorrencia`)**: Relatos de participação cidadã sobre problemas no transporte (`ACESSIBILIDADE`, `ELEVADOR_INOPERANTE`, `LOTACAO_EXCESSIVA`, `INFRAESTRUTURA_PARADA`, etc.), com gravidade e status.

---

## 3. Consultas Personalizadas, Filtros Dinâmicos e Cache

### Query Methods Derivados
- `UsuarioRepository.findByEmailIgnoreCase(String email)`
- `LinhaFavoritaRepository.findByUsuarioId(Long usuarioId, Pageable pageable)`
- `ParadaFavoritaRepository.findByUsuarioIdAndPossuiRampaAcessivelTrue(Long usuarioId)`
- `AlertaRepository.findByUsuarioIdAndAtivoTrue(Long usuarioId)`
- `OcorrenciaRepository.findByStatus(StatusOcorrencia status, Pageable pageable)`

### Consultas `@Query` com JPQL
- `UsuarioRepository.buscarPorPerfilEAcessibilidade(Role role, Boolean mobilidadeReduzida, Pageable pageable)`
- `LinhaFavoritaRepository.buscarFavoritasPorTermo(Long usuarioId, String termo)`
- `ParadaFavoritaRepository.buscarParadasAcessiveisDoUsuario(Long usuarioId)`
- `AlertaRepository.buscarAlertasAtivosPorLinhaOuParada(Integer codigoLinha, Long codigoParada)`
- `OcorrenciaRepository.buscarOcorrenciasCriticasAcessibilidade(List<StatusOcorrencia> statusAtivos, TipoOcorrencia tipo)`

### Filtros Dinâmicos (`Specification`)
- `OcorrenciaSpecification.comFiltros(...)` permite filtrar ocorrências em `GET /api/ocorrencias` combinando dinamicamente `tipo`, `status`, `gravidade`, `codigoLinha`, `codigoParada`, `afetaAcessibilidade` e busca textual (`termo`).

### Estratégia de Cache (`@Cacheable` e `@CacheEvict`)
- **`linhasSptrans`** e **`paradasSptrans`**: Cacheiam consultas à API Olho Vivo (`GET /api/linhas` e `GET /api/paradas`), reduzindo latência e chamadas externas; limpeza manual em `DELETE /api/sptrans/cache`.
- **`ocorrencias`**: Cacheia a listagem de ocorrências críticas de acessibilidade (`GET /api/ocorrencias/acessibilidade-criticas`) e invalida automaticamente (`@CacheEvict`) ao criar, atualizar ou remover ocorrências.
- **`alertas`**: Cacheia alertas ativos por linha/parada (`GET /api/alertas/ativos`) e invalida ao alterar alertas.

---

## 4. Integração SPTrans Olho Vivo

Endpoints próprios expostos pelo backend:
- `GET /api/linhas?termo={termo}` — Busca linhas por número ou nome.
- `GET /api/linhas/{codigo}/veiculos` — Posição em tempo real dos ônibus da linha e indicador `a` (acessível para PcD).
- `GET /api/paradas?termo={termo}` — Busca paradas por nome ou endereço.
- `GET /api/paradas/{codigo}/previsoes` — Previsão de chegada dos veículos na parada.

> **Modo Simulado Automático (Fallback)**: Caso a variável `SPTRANS_TOKEN` não esteja definida no ambiente de desenvolvimento/avaliação, o `OlhoVivoClient` responde com dados simulados realistas da SPTrans (`mock-fallback: true`), permitindo testar todos os endpoints imediatamente. Quando `SPTRANS_TOKEN` é informado, a API autentica em `POST /Login/Autenticar?token=...` e consome a SPTrans real.

---

## 5. Usuários de Teste (Perfil H2)

Ao iniciar no perfil padrão `h2`, o `DataSeederConfig` cria automaticamente:

| Perfil | E-mail | Senha | Role |
|---|---|---|---|
| **Cidadão (PcD)** | `cidadao@viacidada.com.br` | `Cidadao@123` | `ROLE_CIDADAO` |
| **Administrador** | `admin@viacidada.com.br` | `Admin@123` | `ROLE_ADMIN` |

---

## 6. Perfis de Banco e Execução

### Perfis Disponíveis
- **`h2` (padrão)**: Banco H2 em memória com dados iniciais e console em `/h2-console`.
- **`oracle`**: Configuração para Oracle Database via variáveis de ambiente (`ORACLE_DB_URL`, `ORACLE_DB_USERNAME`, `ORACLE_DB_PASSWORD`) com `ddl-auto=validate` por segurança.
- **`test`**: Banco H2 isolado para testes automatizados (`mvn test`).

### Como Executar Localmente (Perfil H2)

```powershell
# Compilar e rodar os testes automatizados
.\mvnw.cmd test

# Iniciar a aplicação (usa o perfil h2 por padrão)
# Opcional: definir o token da SPTrans antes de rodar
$env:SPTRANS_TOKEN="SEU_TOKEN_AQUI"
.\mvnw.cmd spring-boot:run
```

### Links Úteis com a Aplicação Rodando
- **Swagger UI**: `http://localhost:8080/swagger-ui.html`
- **OpenAPI JSON**: `http://localhost:8080/v3/api-docs`
- **H2 Console**: `http://localhost:8080/h2-console` (JDBC URL: `jdbc:h2:mem:viacidadadb`, User: `sa`, Password: em branco)
- **Actuator Health**: `http://localhost:8080/actuator/health`
