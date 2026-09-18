package br.com.desafio.contas.integracao;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("teste")
abstract class IntegracaoBase {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @BeforeEach
    void restaurarCargaInicial() throws Exception {
        mockMvc.perform(delete("/dados")).andExpect(status().isOk());
    }

    protected ResultActions postar(String url, String chave, String corpo) throws Exception {
        return mockMvc.perform(post(url)
                .header("Idempotency-Key", chave)
                .contentType(MediaType.APPLICATION_JSON)
                .content(corpo));
    }

    protected ResultActions depositar(String conta, String chave, String valor) throws Exception {
        return postar("/contas/" + conta + "/depositos", chave, "{\"valor\": " + valor + "}");
    }

    protected ResultActions transferir(String origem, String destino, String valor, String chave) throws Exception {
        return postar("/transferencias", chave, """
                {"contaOrigem": "%s", "contaDestino": "%s", "valor": %s}
                """.formatted(origem, destino, valor));
    }

    protected ResultActions estornar(long transferenciaId, String chave) throws Exception {
        return postar("/transferencias/" + transferenciaId + "/estorno", chave, """
                {"motivo": "Pedido do usuário"}
                """);
    }

    protected long saldo(String conta) {
        return jdbcTemplate.queryForObject("select saldo_centavos from conta where numero = ?", Long.class, conta);
    }

    protected long quantidadeDeMovimentos() {
        return jdbcTemplate.queryForObject("select count(*) from movimento", Long.class);
    }

    protected void verificarConsistenciaDoExtrato() {
        Long contasComSaldoDivergente = jdbcTemplate.queryForObject("""
                select count(*) from conta c
                 where c.saldo_centavos <> coalesce((
                       select sum(case when m.tipo = 'ENTRADA' then m.valor_centavos else -m.valor_centavos end)
                         from movimento m where m.conta_id = c.id), 0)
                """, Long.class);
        assertThat(contasComSaldoDivergente).as("saldo da conta igual à soma dos movimentos").isZero();

        Long contasComUltimoSaldoDivergente = jdbcTemplate.queryForObject("""
                select count(*) from conta c
                  join movimento m on m.conta_id = c.id
                 where m.sequencia = (select max(sequencia) from movimento where conta_id = c.id)
                   and m.saldo_apos_centavos <> c.saldo_centavos
                """, Long.class);
        assertThat(contasComUltimoSaldoDivergente).as("saldo da conta igual ao saldo após do último movimento")
                .isZero();

        Long contasComSequenciaQuebrada = jdbcTemplate.queryForObject("""
                select count(*) from (
                    select conta_id, min(sequencia) as menor, max(sequencia) as maior,
                           count(*) as total, count(distinct sequencia) as distintas
                      from movimento group by conta_id) s
                 where s.menor <> 1 or s.maior <> s.total or s.distintas <> s.total
                """, Long.class);
        assertThat(contasComSequenciaQuebrada).as("sequência sem buracos e sem repetição").isZero();

        Long somaGeral = jdbcTemplate.queryForObject("""
                select coalesce(sum(case when tipo = 'ENTRADA' then valor_centavos else -valor_centavos end), 0)
                  from movimento
                """, Long.class);
        assertThat(somaGeral).as("soma de todos os movimentos").isZero();
    }
}
