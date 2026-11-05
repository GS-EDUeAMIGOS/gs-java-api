# Checklist — Java Advanced

## Fonte e interpretação

`2TDS Agosto - Global Solution 2026-2.pptx`, slides 51–59. Orientações gerais: slides 11–16 e 20; submissão: slides 81–85. A numeração é a posição real dos slides no PPTX, não o contador interno de cada disciplina.

Todos os itens abaixo permanecem pendentes. A documentação é uma síntese derivada, não transcrição integral nem promessa de nota. O PPTX contém notas editoriais e placeholders; consulte [pendências](pendencias.md) antes de tratar pontos ambíguos como definitivos.

## Requisitos e evidências

- [ ] **Slide(s) 52–53:** Desenvolver API REST Java/Spring Boot com domínio e regras de negócio definidos e relacionamentos JPA.
- [ ] **Slide(s) 52–53:** Usar Spring Web, Spring Data JPA, Bean Validation, Spring Security/JWT, Spring Cache, Spring HATEOAS, Actuator e Swagger/OpenAPI.
- [ ] **Slide(s) 53:** Implementar CRUD com DTOs, paginação, ordenação e códigos HTTP corretos.
- [ ] **Slide(s) 53:** Demonstrar query method derivado, @Query JPQL e filtros dinâmicos via Specification ou Query by Example.
- [ ] **Slide(s) 53:** Padronizar erros com Bean Validation e @RestControllerAdvice.
- [ ] **Slide(s) 53:** Aplicar @Cacheable a consultas frequentes e invalidar com @CacheEvict nas escritas; justificar escolhas.
- [ ] **Slide(s) 53:** Adicionar links HATEOAS self, recursos relacionados e ações possíveis nos recursos principais.
- [ ] **Slide(s) 53:** Configurar endpoints Actuator health, info e metrics.
- [ ] **Slide(s) 53:** Documentar todos os endpoints no Swagger UI; implementar cadastro/login JWT, senha com criptografia, autorização por papel e CORS para Mobile.
- [ ] **Slide(s) 55–56:** Manter histórico de evolução real, projeto na raiz e repositório público; não versionar senhas/chaves reais.
- [ ] **Slide(s) 56:** Completar README com integrantes, link do pitch, execução, usuários de teste por papel, diagrama e justificativas de consultas/cache.
- [ ] **Slide(s) 57:** Demonstrar Mobile consumindo a API, modelagem/cascade/fetch, fluxo JWT, consultas, cache, HATEOAS e Actuator.
- [ ] **Slide(s) 58–59:** Produzir pitch de até 5 minutos; link acessível no YouTube público/não listado ou Drive aberto, referenciado no README; abordar problema, solução, demonstração, negócio e impacto.

## Condições gerais

- [ ] Relacionar solução ao tema CitizenTech, problema real e cidadão beneficiado (slides 3–7).
- [ ] Explicar ODS e contribuição às metas (slide 11).
- [ ] Confirmar grupo de até cinco integrantes, identificação e cadastro (slides 12–14).
- [ ] Confirmar instruções finais no Portal e submeter até 19/11/2026 às 23h55 (slide 14).
- [ ] Conferir acesso aos links e legibilidade dos materiais, sem segredos ou dados reais.
- [ ] Preparar a apresentação da disciplina conforme agenda confirmada.

## Formato específico de entrega

Arquivo .txt contendo somente o link do repositório público do GitHub, na plataforma FIAP. O link do pitch fica no README (slide 56; pitch nos slides 58–59).

A orientação geral de .txt em .zip não deve sobrescrever automaticamente formatos específicos .txt/PDF: conferir exigência da disciplina e do Portal.

## Limites e observações

O slide 56 escreve READE.md; este repositório usa README.md. Usuários de teste devem ser sintéticos, sem credenciais reais ou acesso a ambientes compartilhados.

## Critério de conclusão

Só marcar um requisito como concluído após anexar referência verificável (arquivo, commit, PR, teste, captura ou vídeo conforme o item) e revisar aderência ao enunciado. Não marcar automaticamente tarefas ou evidências pela criação do repositório.
