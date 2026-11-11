<<<<<<< HEAD
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
=======
# gs-java-api

**FIAP — Global Solution 2026/2 — CitizenTech**  
**Disciplina:** Java Advanced

> Estado: preparação documental inicial. Não há aplicação, testes executados, infraestrutura provisionada ou entrega concluída neste repositório.

## Finalidade e limites

API Spring Boot com JPA, validação, JWT, cache, HATEOAS, Actuator e OpenAPI, integrada ao Mobile.

Este repositório representa um **entregável acadêmico da GS**, não o produto completo nem um entregável do Challenge. Os sete repositórios devem representar a mesma solução CitizenTech (slide 20). A solução, o nome do grupo, os integrantes, RMs, turma e responsáveis ainda precisam de confirmação específica para esta GS.

Nenhum código, modelo de domínio ou identidade do Challenge foi copiado. Reutilização futura deve ter origem principal, versão e revisão combinadas; separar entregas não significa duplicar implementação sem controle.

## Fonte e prazo

Fonte primária fornecida pelo usuário: `2TDS Agosto - Global Solution 2026-2.pptx`, slides 51–59. Orientações gerais: slides 11–16 e 20; submissão: slides 81–85. A numeração é a posição real dos slides no PPTX, não o contador interno de cada disciplina.

- **Entrega indicada: 19/11/2026, até 23h55, no Portal FIAP** (slide 14).
- Cadastro do grupo indicado até **13/11/2026** (slides 12 e 14); notas do arquivo pedem confirmação desse prazo.
- Apresentações indicadas de **23 a 27/11/2026**; conferir modalidade e agenda com o professor.
- Este checklist é derivado do PPTX, não substitui rubrica final e instruções do Portal. O original não foi republicado.

## Escopo e checklist

Veja [requisitos da disciplina](docs/requisitos-gs.md) e [decisões e pendências](docs/pendencias.md).

O slide 56 escreve READE.md; este repositório usa README.md. Usuários de teste devem ser sintéticos, sem credenciais reais ou acesso a ambientes compartilhados.

## Equipe e responsabilidade

| Informação | Situação |
|---|---|
| Grupo, solução e cidadão beneficiado | A confirmar para a GS |
| Integrantes, RM e turma | A confirmar para a GS |
| Responsável principal desta disciplina | A confirmar — um único dono por tarefa |
| Revisor | A combinar conforme fronteiras afetadas |

Não interpretar membros da organização ou integrantes do Challenge como composição aprovada da GS. Documentar ODS e contribuição às metas conforme slide 11, usando fontes para alegações de impacto.

## Execução e testes

Ainda não há software executável. Na implementação, substituir esta seção por pré-requisitos e versões reais, configuração sem segredos, comandos reproduzíveis, dados sintéticos e resultados de testes efetivamente executados. Não há build ou teste aprovado apenas pela existência desta documentação.

## Evidências e submissão

Arquivo .txt contendo somente o link do repositório público do GitHub, na plataforma FIAP. O link do pitch fica no README (slide 56; pitch nos slides 58–59).

Organização das evidências: [evidence/README.md](evidence/README.md). Orientações de submissão: [delivery/README.md](delivery/README.md). Links de vídeo, diagramas e publicação permanecem pendentes; nenhum artefato final foi gerado.

## Repositórios relacionados da GS

- [gs-dotnet-api](https://github.com/GS-EDUeAMIGOS/gs-dotnet-api) — Advanced Business Development with .NET
- [gs-arquitetura-qa](https://github.com/GS-EDUeAMIGOS/gs-arquitetura-qa) — Compliance, Quality Assurance & Tests
- [gs-devops-cloud](https://github.com/GS-EDUeAMIGOS/gs-devops-cloud) — DevOps Tools & Cloud Computing
- [gs-iot-redes](https://github.com/GS-EDUeAMIGOS/gs-iot-redes) — Disruptive Architectures: IoT, IoB & Generative IA
- [gs-database](https://github.com/GS-EDUeAMIGOS/gs-database) — Mastering Relational and Non-Relational Database
- [gs-mobile-flutter](https://github.com/GS-EDUeAMIGOS/gs-mobile-flutter) — Mobile Application Development

As referências acima organizam entregas; não significam que todas as aplicações serão serviços separados do produto. Registrar contratos entre Java/.NET, Banco, Mobile, IoT e DevOps antes de integrar.

## Contribuição e segurança

Veja [CONTRIBUTING.md](CONTRIBUTING.md). Não versionar senhas, tokens, strings de conexão reais ou dados pessoais de usuários. Exemplos devem usar valores fictícios.

A preparação não configura equipes, permissões, CODEOWNERS, proteção de branches, CI ou deploy. Repositório público não substitui entrega no Portal nem comprova atendimento da rubrica.
>>>>>>> origin/main
