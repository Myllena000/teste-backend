package br.com.desafio.contas.integracao;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class EstornoIT extends IntegracaoBase {

    @Test
    void deveEstornarDevolvendoValorETaxaSemAlterarOriginal() throws Exception {
        long id = transferirERetornarId("CONTA-001", "CONTA-002", "150.00", "tr-estorno");
        long movimentosDaOriginal = movimentosDaTransferencia(id);

        estornar(id, "est-1")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.transferenciaOriginalId").value(id))
                .andExpect(jsonPath("$.contaOrigem").value("CONTA-002"))
                .andExpect(jsonPath("$.contaDestino").value("CONTA-001"))
                .andExpect(jsonPath("$.valor").value(150.00))
                .andExpect(jsonPath("$.taxaDevolvida").value(1.50));

        assertThat(saldo("CONTA-001")).isEqualTo(100_000L);
        assertThat(saldo("CONTA-002")).isEqualTo(100_000L);
        assertThat(saldo("SISTEMA-TAXAS")).isZero();

        String estadoOriginal = jdbcTemplate.queryForObject("select estado from transferencia where id = ?", String.class, id);
        Long valorOriginal = jdbcTemplate.queryForObject("select valor_centavos from transferencia where id = ?", Long.class, id);
        assertThat(estadoOriginal).isEqualTo("ESTORNADA");
        assertThat(valorOriginal).isEqualTo(15_000L);
        assertThat(movimentosDaTransferencia(id)).isEqualTo(movimentosDaOriginal);
        verificarConsistenciaDoExtrato();
    }

    @Test
    void naoDeveEstornarDuasVezes() throws Exception {
        long id = transferirERetornarId("CONTA-001", "CONTA-002", "50.00", "tr-duplo");
        estornar(id, "est-a").andExpect(status().isCreated());

        estornar(id, "est-b")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("TRANSICAO_DE_ESTADO_INVALIDA"));

        Long estornos = jdbcTemplate.queryForObject(
                "select count(*) from transferencia where transferencia_original_id = ?", Long.class, id);
        assertThat(estornos).isEqualTo(1);
    }

    @Test
    void reenvioDoMesmoEstornoDevolveRespostaOriginal() throws Exception {
        long id = transferirERetornarId("CONTA-001", "CONTA-002", "50.00", "tr-reenvio");
        estornar(id, "est-reenvio").andExpect(status().isCreated());

        estornar(id, "est-reenvio").andExpect(status().isOk());
    }

    @Test
    void deveRetornar404ParaTransferenciaInexistente() throws Exception {
        estornar(999_999L, "est-404")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("TRANSFERENCIA_NAO_ENCONTRADA"));
    }

    @Test
    void naoDeveEstornarDeposito() throws Exception {
        long depositoId = JsonPath.parse(depositar("CONTA-001", "dep-est", "10.00")
                        .andReturn().getResponse().getContentAsString())
                .read("$.transferenciaId", Long.class);

        estornar(depositoId, "est-deposito")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("ESTORNO_NAO_PERMITIDO"));
    }

    @Test
    void deveRecusarEstornoQuandoQuemRecebeuNaoTemMaisSaldo() throws Exception {
        long id = transferirERetornarId("CONTA-001", "CONTA-002", "800.00", "tr-gasto");
        transferir("CONTA-002", "CONTA-003", "1750.00", "tr-gastou").andExpect(status().isCreated());

        estornar(id, "est-sem-saldo")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("SALDO_INSUFICIENTE"));

        String estado = jdbcTemplate.queryForObject("select estado from transferencia where id = ?", String.class, id);
        assertThat(estado).isEqualTo("CONFIRMADA");
    }

    @Test
    void estornoNaoDevolveOLimiteDiario() throws Exception {
        depositar("CONTA-001", "dep-limite-estorno", "5000.00").andExpect(status().isCreated());
        long id = transferirERetornarId("CONTA-001", "CONTA-002", "1500.00", "tr-limite-estorno");
        estornar(id, "est-limite").andExpect(status().isCreated());

        transferir("CONTA-001", "CONTA-003", "600.00", "tr-depois-estorno")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("LIMITE_DIARIO_EXCEDIDO"));

        transferir("CONTA-001", "CONTA-003", "500.00", "tr-restante").andExpect(status().isCreated());
    }

    @Test
    void estornoRecebidoPorContaBloqueadaEAceito() throws Exception {
        long id = transferirERetornarId("CONTA-001", "CONTA-002", "40.00", "tr-antes-bloqueio");
        jdbcTemplate.update("update conta set estado = 'BLOQUEADA' where numero = 'CONTA-001'");

        estornar(id, "est-bloqueada").andExpect(status().isCreated());

        assertThat(saldo("CONTA-001")).isEqualTo(100_000L);
    }

    private long transferirERetornarId(String origem, String destino, String valor, String chave) throws Exception {
        String resposta = transferir(origem, destino, valor, chave)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.parse(resposta).read("$.transferenciaId", Long.class);
    }

    private long movimentosDaTransferencia(long id) {
        return jdbcTemplate.queryForObject("select count(*) from movimento where transferencia_id = ?", Long.class, id);
    }
}
