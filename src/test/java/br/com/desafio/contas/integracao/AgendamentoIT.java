package br.com.desafio.contas.integracao;

import br.com.desafio.contas.service.ProcessadorDeAgendamentos;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AgendamentoIT extends IntegracaoBase {

    @Autowired
    private ProcessadorDeAgendamentos processadorDeAgendamentos;

    private long criarAgendamentoVencido(String origem, String destino, String valor, String chave) throws Exception {
        String resposta = agendar(origem, destino, valor, OffsetDateTime.now().plusHours(1), chave)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long id = JsonPath.parse(resposta).read("$.agendamentoId", Long.class);
        jdbcTemplate.update("update agendamento set executar_em = now() - interval '1 minute' where id = ?", id);
        return id;
    }

    private org.springframework.test.web.servlet.ResultActions agendar(String origem, String destino, String valor,
                                                                       OffsetDateTime executarEm, String chave) throws Exception {
        return postar("/transferencias-agendadas", chave, """
                {"contaOrigem": "%s", "contaDestino": "%s", "valor": %s, "executarEm": "%s"}
                """.formatted(origem, destino, valor, executarEm.withOffsetSameInstant(ZoneOffset.ofHours(-3))));
    }

    @Test
    void deveCriarAgendamentoSemMovimentarDinheiro() throws Exception {
        long movimentos = quantidadeDeMovimentos();

        agendar("CONTA-001", "CONTA-002", "150.00", OffsetDateTime.now().plusDays(1), "ag-1")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("AGENDADO"))
                .andExpect(jsonPath("$.agendamentoId").isNumber());

        assertThat(quantidadeDeMovimentos()).isEqualTo(movimentos);
        assertThat(saldo("CONTA-001")).isEqualTo(100_000L);
    }

    @Test
    void deveRecusarDataNoPassado() throws Exception {
        agendar("CONTA-001", "CONTA-002", "150.00", OffsetDateTime.now().minusMinutes(1), "ag-passado")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("DATA_DE_EXECUCAO_INVALIDA"));
    }

    @Test
    void deveRecusarValorZeroEContaInexistente() throws Exception {
        agendar("CONTA-001", "CONTA-002", "0", OffsetDateTime.now().plusDays(1), "ag-zero")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("VALOR_INVALIDO"));

        agendar("CONTA-001", "CONTA-999", "10.00", OffsetDateTime.now().plusDays(1), "ag-404")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("CONTA_NAO_ENCONTRADA"));
    }

    @Test
    void deveRetornar400SemDataDeExecucao() throws Exception {
        postar("/transferencias-agendadas", "ag-sem-data", """
                {"contaOrigem": "CONTA-001", "contaDestino": "CONTA-002", "valor": 10}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("CORPO_INVALIDO"));
    }

    @Test
    void jobDeveExecutarAgendamentoVencidoApenasUmaVez() throws Exception {
        long id = criarAgendamentoVencido("CONTA-001", "CONTA-002", "150.00", "ag-vencido");

        assertThat(processadorDeAgendamentos.processarVencidos()).isEqualTo(1);
        assertThat(processadorDeAgendamentos.processarVencidos()).isZero();

        String estado = jdbcTemplate.queryForObject("select estado from agendamento where id = ?", String.class, id);
        Long transferenciaId = jdbcTemplate.queryForObject(
                "select transferencia_id from agendamento where id = ?", Long.class, id);
        assertThat(estado).isEqualTo("EXECUTADO");
        assertThat(transferenciaId).isNotNull();
        assertThat(saldo("CONTA-001")).isEqualTo(84_850L);
        assertThat(saldo("CONTA-002")).isEqualTo(115_000L);
        verificarConsistenciaDoExtrato();
    }

    @Test
    void jobDeveMarcarComoFalhadoQuandoFaltaSaldoNaHoraDaExecucao() throws Exception {
        long id = criarAgendamentoVencido("CONTA-001", "CONTA-002", "900.00", "ag-sem-saldo");
        transferir("CONTA-001", "CONTA-003", "500.00", "tr-antes-do-job").andExpect(status().isCreated());

        processadorDeAgendamentos.processarVencidos();

        String estado = jdbcTemplate.queryForObject("select estado from agendamento where id = ?", String.class, id);
        String motivo = jdbcTemplate.queryForObject("select motivo_falha from agendamento where id = ?", String.class, id);
        assertThat(estado).isEqualTo("FALHADO");
        assertThat(motivo).startsWith("SALDO_INSUFICIENTE");
        assertThat(processadorDeAgendamentos.processarVencidos()).isZero();
        verificarConsistenciaDoExtrato();
    }

    @Test
    void agendamentoNaoVencidoNaoEhExecutado() throws Exception {
        agendar("CONTA-001", "CONTA-002", "10.00", OffsetDateTime.now().plusHours(2), "ag-futuro")
                .andExpect(status().isCreated());

        assertThat(processadorDeAgendamentos.processarVencidos()).isZero();
    }

}
