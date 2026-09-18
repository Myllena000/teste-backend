# Sistema de Contas e Transferências 👩🏽‍💻
Fiz esse projeto como resolução de um desafio técnico focado em consistência de dados, concorrência e regras rígidas de saldo. Fui bem direta e objetiva nas decisões de arquitetura para garantir que o dinheiro nunca se perdesse, duplicasse ou ficasse travado.

## Links Úteis ✅
- **Swagger UI:** http://localhost:8080/swagger-ui.html
- **OpenAPI em JSON:** http://localhost:8080/v3/api-docs
- **Actuator (Health, Info, Metrics):** http://localhost:8080/actuator/health
- **Collection do Postman:** O arquivo está em `postman/contas.postman_collection.json` (já vem com as operações mapeadas e a métrica de conflitos configurada).

## Stack Tecnológica
- Java 21
- Spring Boot 3.x (Web, Data JPA, Validation)
- PostgreSQL 16
- Flyway (para as migrations)
- springdoc-openapi
- Docker Compose
- JUnit 5 e Mockito

## Como lidei com a concorrência
Para garantir que o saldo e as regras não quebrassem com várias requisições ao mesmo tempo, analisei algumas opções e escolhi a melhor para cada caso:

- **Saldo e Limite Diário:** Escolhi usar *lock pessimista* (`SELECT ... FOR UPDATE`). Pensei em usar o campo `@Version` (lock otimista), mas ele geraria falhas para o cliente pedindo para tentar de novo caso houvesse colisão. Com o lock pessimista, eu tranco a linha da conta; a segunda requisição apenas espera um pouquinho e lê o dado já atualizado. É mais seguro e não quebra a experiência de quem chama a API.
- **Sequência do Extrato:** Fui de *Unique Constraint* direto no banco (`conta_id` + `sequencia`). O banco de dados faz o trabalho duro e simplesmente recusa qualquer tentativa de criar um buraco ou repetir o número no extrato.
- **Idempotência:** Também usei *Unique Constraint* (`chave` + `endpoint`). Se caírem duas requisições idênticas ao mesmo tempo, o banco barra a segunda. A chave é salva na mesma transação que mexe no dinheiro, então não tem perigo do saldo ser atualizado sem a chave ser registrada.
- **Estorno:** Apliquei uma *Unique Constraint* no ID da transferência original. Assim, é impossível estornar a mesma transferência duas vezes no banco.

## Transferências Cruzadas (Sem Deadlock)
Se a Conta A manda pra B, e a Conta B manda pra A ao mesmo tempo, uma poderia ficar esperando a outra liberar o *lock* para sempre. Pra evitar isso, implementei uma regra simples no código: eu pego os locks das contas sempre ordenando pelo **menor ID primeiro**, não importa quem é a origem e quem é o destino. Dessa forma, as duas threads tentam trancar os registros na exata mesma ordem, fazendo com que uma apenas espere a outra terminar, sem travar o sistema.

## Extrato Imutável no Banco
O desafio pedia que a imutabilidade não dependesse só da confiança no código. Então, criei *Triggers* no PostgreSQL (na migration `V2__protecao_movimento.sql`). Se algo ou alguém tentar dar um `UPDATE` ou `DELETE` na tabela de movimentos, o banco lança uma exceção e bloqueia a ação na raiz. Na aplicação, reforcei isso usando a anotação `@Immutable` na entidade do Hibernate.

## Job de Agendamentos com 2 Instâncias
Para o job rodar em duas instâncias da API ao mesmo tempo sem duplicar os envios, usei `SELECT ... FOR UPDATE SKIP LOCKED`.
A primeira instância que bate no banco pega os agendamentos vencidos e tranca eles. A segunda instância vê que eles estão trancados e simplesmente os ignora (skip), pegando os próximos da fila. Se uma instância cair no meio do processamento, a conexão morre, o banco dá *rollback*, solta os locks, e a outra instância consegue pegar esses registros de novo na próxima varredura.

## Índices Criados
Criei os índices focando nas consultas que poderiam ser gargalos:
- `(conta_id, sequencia DESC)`: Para deixar a paginação do extrato (que vem do maior pro menor) bem rápida.
- `(criada_em, estado)` na transferência: Focado no endpoint de resumo. Isso ajuda o banco a fazer os cálculos de agregações (SUM, AVG, COUNT) dos valores dentro da janela de tempo sem precisar ler a tabela inteira.
- Um índice parcial em `(estado, executar_em)` no agendamento: Para o Job achar rapidamente o que está como `AGENDADO` e com a data vencida.

## Decisões em Aberto
1. **Estorno sem saldo (O destinatário já gastou o dinheiro):**
   Decidi **recusar o estorno com erro 422**. A regra principal é que o saldo de usuário nunca pode ficar negativo. Forçar esse estorno deixaria o saldo negativo e quebraria a consistência inteira do sistema. A resolução para o usuário lesado precisa virar um processo de atendimento, mas via sistema, a operação tem que ser barrada.
2. **Limite Diário e Estorno:**
   Decidi que **o estorno não devolve o limite diário**. Se devolvesse, uma pessoa mal-intencionada poderia ficar transferindo e estornando para burlar o limite do dia infinitamente. O limite serve para restringir a saída no dia, e essa saída aconteceu.

## Diferenciais Implementados
- **OpenAPI:** Documentação gerada com os códigos de erro listados nos endpoints.
- **SHA-256 no Extrato:** Adicionei um hash criptográfico em cada movimento encadeado com o hash do movimento anterior da mesma conta, garantindo auditoria à prova de adulteração.
- **Actuator com Métrica de Conflito:** Criei uma métrica que avalia conflitos de concorrência separados por endpoint.

### O que ficou de fora e o que faria com mais tempo
Para focar em entregar um núcleo 100% consistente em relação à concorrência, acabei cortando algumas coisas. Com mais tempo eu faria:
- O endpoint para cancelar agendamentos que ainda não rodaram.
- Scripts de teste de carga (como k6 ou Gatling) para comprovar o comportamento e os *locks* sob estresse pesado.
- Implementação de *Testcontainers* com testes automatizados baseados em threads no JUnit (embora a concorrência esteja blindada no banco e eu tenha feito as validações necessárias).

---
*Nota técnica: Durante o desenvolvimento desse projeto, utilizei a IA (Gemini e Claude) como apoio para tirar dúvidas e buscar referências de implementações. Pedi ajuda principalmente na sintaxe das anotações `@Lock` e paginação com `@Page`, na lógica de criptografia com `@sha256` e `@Hash`, além de estruturar algumas consultas SQL e outras utilizações.*