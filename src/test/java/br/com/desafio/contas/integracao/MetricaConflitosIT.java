package br.com.desafio.contas.integracao;

import br.com.desafio.contas.metrics.MetricaConflitos;
import com.jayway.jsonpath.JsonPath;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MetricaConflitosIT extends IntegracaoBase {

    @Autowired
    private MeterRegistry meterRegistry;

    @Autowired
    private DataSource dataSource;

    private double conflitos(String endpoint, String recurso) {
        Counter contador = meterRegistry.find(MetricaConflitos.NOME)
                .tag("endpoint", endpoint)
                .tag("recurso", recurso)
                .counter();
        return contador == null ? 0.0 : contador.count();
    }

    @Test
    void transferenciaQueEncontraContaTravadaRegistraConflito() throws Exception {
        double conflitosDeContaAntes = conflitos("POST /transferencias", MetricaConflitos.RECURSO_CONTA);
        double conflitosDeBancoAntes = conflitos("POST /transferencias", MetricaConflitos.RECURSO_BANCO);

        try (Connection outraTransacao = dataSource.getConnection()) {
            outraTransacao.setAutoCommit(false);
            try (Statement comando = outraTransacao.createStatement()) {
                comando.execute("select id from conta where numero = 'CONTA-001' for update");
            }

            transferir("CONTA-001", "CONTA-002", "10.00", "conflito-conta")
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.codigo").value("CONFLITO_DE_CONCORRENCIA"));

            outraTransacao.rollback();
        }

        assertThat(conflitos("POST /transferencias", MetricaConflitos.RECURSO_CONTA) - conflitosDeContaAntes).isEqualTo(1.0);
        assertThat(conflitos("POST /transferencias", MetricaConflitos.RECURSO_BANCO) - conflitosDeBancoAntes).isEqualTo(1.0);
        assertThat(saldo("CONTA-001")).isEqualTo(100_000L);

        transferir("CONTA-001", "CONTA-002", "10.00", "conflito-conta").andExpect(status().isCreated());
    }

    @Test
    void transferenciaSemDisputaNaoRegistraConflito() throws Exception {
        double antes = conflitos("POST /transferencias", MetricaConflitos.RECURSO_CONTA);

        transferir("CONTA-001", "CONTA-002", "150.00", "sem-conflito").andExpect(status().isCreated());

        assertThat(conflitos("POST /transferencias", MetricaConflitos.RECURSO_CONTA)).isEqualTo(antes);
    }

    @Test
    void estornoQueEncontraTransferenciaTravadaRegistraConflitoNoEndpointDeEstorno() throws Exception {
        String resposta = transferir("CONTA-001", "CONTA-002", "50.00", "tr-para-estorno")
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long id = JsonPath.parse(resposta).read("$.transferenciaId", Long.class);
        double antes = conflitos("POST /transferencias/{id}/estorno", MetricaConflitos.RECURSO_TRANSFERENCIA);

        try (Connection outraTransacao = dataSource.getConnection()) {
            outraTransacao.setAutoCommit(false);
            try (PreparedStatement comando = outraTransacao.prepareStatement(
                    "select id from transferencia where id = ? for update")) {
                comando.setLong(1, id);
                comando.executeQuery();
            }

            estornar(id, "estorno-travado").andExpect(status().isConflict());

            outraTransacao.rollback();
        }

        assertThat(conflitos("POST /transferencias/{id}/estorno", MetricaConflitos.RECURSO_TRANSFERENCIA) - antes)
                .isEqualTo(1.0);
    }

    @Test
    void idempotencyKeyEmUsoPorOutraTransacaoRegistraConflito() throws Exception {
        double antes = conflitos("POST /contas/{numero}/depositos", MetricaConflitos.RECURSO_IDEMPOTENCY_KEY);

        try (Connection outraTransacao = dataSource.getConnection()) {
            outraTransacao.setAutoCommit(false);
            try (PreparedStatement comando = outraTransacao.prepareStatement(
                    "select pg_advisory_xact_lock(hashtext(?))")) {
                comando.setString(1, "POST /contas/{numero}/depositos|chave-disputada");
                comando.executeQuery();
            }

            depositar("CONTA-003", "chave-disputada", "10.00").andExpect(status().isCreated());

            outraTransacao.rollback();
        }

        assertThat(conflitos("POST /contas/{numero}/depositos", MetricaConflitos.RECURSO_IDEMPOTENCY_KEY) - antes)
                .isEqualTo(1.0);
    }

    @Test
    void metricaDisponivelNoActuatorComTagsDeEndpointERecurso() throws Exception {
        try (Connection outraTransacao = dataSource.getConnection()) {
            outraTransacao.setAutoCommit(false);
            try (Statement comando = outraTransacao.createStatement()) {
                comando.execute("select id from conta where numero = 'CONTA-004' for update");
            }
            depositar("CONTA-004", "deposito-travado", "10.00").andExpect(status().isConflict());
            outraTransacao.rollback();
        }

        mockMvc.perform(get("/actuator/metrics/" + MetricaConflitos.NOME)
                        .param("tag", "endpoint:POST /contas/{numero}/depositos")
                        .param("tag", "recurso:conta"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.measurements[0].value").isNumber());
    }

}
