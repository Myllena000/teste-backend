package br.com.desafio.contas.integracao;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ExtratoEResumoIT extends IntegracaoBase {

    @Test
    void extratoDeveVirEmOrdemDecrescenteComSaldoAtualECpfMascarado() throws Exception {
        transferir("CONTA-001", "CONTA-002", "150.00", "ext-1");
        depositar("CONTA-001", "ext-2", "20.00");

        String cpf = jdbcTemplate.queryForObject(
                "select u.cpf from usuario u join conta c on c.usuario_id = u.id where c.numero = 'CONTA-001'", String.class);

        mockMvc.perform(get("/contas/CONTA-001/extrato"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.saldoAtual").value(868.50))
                .andExpect(jsonPath("$.titular.cpf").value("***." + cpf.substring(3, 6) + "." + cpf.substring(6, 9) + "-**"))
                .andExpect(content().string(not(containsString(cpf))))
                .andExpect(jsonPath("$.totalElementos").value(4))
                .andExpect(jsonPath("$.movimentos[0].sequencia").value(4))
                .andExpect(jsonPath("$.movimentos[0].tipo").value("ENTRADA"))
                .andExpect(jsonPath("$.movimentos[0].saldoApos").value(868.50))
                .andExpect(jsonPath("$.movimentos[1].natureza").value("TAXA"))
                .andExpect(jsonPath("$.movimentos[3].sequencia").value(1));
    }

    @Test
    void extratoDevePaginarEFiltrarPorData() throws Exception {
        for (int i = 0; i < 3; i++) {
            depositar("CONTA-003", "pag-" + i, "1.00");
        }
        String hoje = LocalDate.now(ZoneId.of("America/Sao_Paulo")).toString();
        String amanha = LocalDate.now(ZoneId.of("America/Sao_Paulo")).plusDays(1).toString();

        mockMvc.perform(get("/contas/CONTA-003/extrato").param("page", "1").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElementos").value(4))
                .andExpect(jsonPath("$.totalPaginas").value(2))
                .andExpect(jsonPath("$.movimentos.length()").value(2))
                .andExpect(jsonPath("$.movimentos[0].sequencia").value(2));

        mockMvc.perform(get("/contas/CONTA-003/extrato").param("de", hoje).param("ate", hoje))
                .andExpect(jsonPath("$.totalElementos").value(4));

        mockMvc.perform(get("/contas/CONTA-003/extrato").param("de", amanha))
                .andExpect(jsonPath("$.totalElementos").value(0))
                .andExpect(jsonPath("$.saldoAtual").value(1003.00));
    }

    @Test
    void extratoDeveValidarParametros() throws Exception {
        mockMvc.perform(get("/contas/CONTA-999/extrato"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("CONTA_NAO_ENCONTRADA"));

        mockMvc.perform(get("/contas/CONTA-001/extrato").param("de", "2026-09-10").param("ate", "2026-09-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("PARAMETRO_INVALIDO"));

        mockMvc.perform(get("/contas/CONTA-001/extrato").param("de", "10/09/2026"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("PARAMETRO_INVALIDO"));
    }

    @Test
    void resumoSemMovimentacaoRetornaZeros() throws Exception {
        mockMvc.perform(get("/resumo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contasAtivasAgora").value(5))
                .andExpect(jsonPath("$.saldoTotalUsuariosAgora").value(5300.00))
                .andExpect(jsonPath("$.agendamentosPendentesAgora").value(0))
                .andExpect(jsonPath("$.transferenciasNaJanela").value(0))
                .andExpect(jsonPath("$.valorTransferidoNaJanela").value(0))
                .andExpect(jsonPath("$.ticketMedioNaJanela").value(0))
                .andExpect(jsonPath("$.somaDosMovimentosEhZero").value(true));
    }

    @Test
    void resumoDeveAgregarTransferenciasEEstornosDaJanela() throws Exception {
        transferir("CONTA-001", "CONTA-002", "150.00", "res-1");
        transferir("CONTA-003", "CONTA-004", "50.00", "res-2");
        String resposta = transferir("CONTA-004", "CONTA-005", "200.00", "res-3")
                .andReturn().getResponse().getContentAsString();
        long id = com.jayway.jsonpath.JsonPath.parse(resposta).read("$.transferenciaId", Long.class);
        estornar(id, "res-estorno");

        mockMvc.perform(get("/resumo").param("janelaMinutos", "30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transferenciasNaJanela").value(3))
                .andExpect(jsonPath("$.valorTransferidoNaJanela").value(400.00))
                .andExpect(jsonPath("$.taxasNaJanela").value(3.50))
                .andExpect(jsonPath("$.ticketMedioNaJanela").value(133.33))
                .andExpect(jsonPath("$.estornosNaJanela").value(1))
                .andExpect(jsonPath("$.saldoTotalUsuariosAgora").value(5298.50))
                .andExpect(jsonPath("$.somaDosMovimentosEhZero").value(true));

        mockMvc.perform(get("/resumo").param("janelaMinutos", "0"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void restaurarDadosVoltaParaCargaInicial() throws Exception {
        transferir("CONTA-001", "CONTA-002", "150.00", "reset-1");

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/dados"))
                .andExpect(status().isOk());

        assertThat(saldo("CONTA-001")).isEqualTo(100_000L);
        assertThat(saldo("SISTEMA-ENTRADA")).isEqualTo(-530_000L);
        assertThat(jdbcTemplate.queryForObject("select count(*) from idempotencia", Long.class)).isZero();
        assertThat(quantidadeDeMovimentos()).isEqualTo(12);
        verificarConsistenciaDoExtrato();
    }
}
