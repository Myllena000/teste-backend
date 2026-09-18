package br.com.desafio.contas.integracao;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TransferenciaIT extends IntegracaoBase {

    @Test
    void deveTransferirCobrandoTaxa() throws Exception {
        transferir("CONTA-001", "CONTA-002", "150.00", "tr-1")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.transferenciaId").isNumber())
                .andExpect(jsonPath("$.valor").value(150.00))
                .andExpect(jsonPath("$.taxa").value(1.50))
                .andExpect(jsonPath("$.totalDebitado").value(151.50))
                .andExpect(jsonPath("$.saldoOrigemApos").value(848.50))
                .andExpect(jsonPath("$.concluidaEm").exists());

        assertThat(saldo("CONTA-001")).isEqualTo(84_850L);
        assertThat(saldo("CONTA-002")).isEqualTo(115_000L);
        assertThat(saldo("SISTEMA-TAXAS")).isEqualTo(150L);
        verificarConsistenciaDoExtrato();
    }

    @Test
    void transferenciaGeraDoisParesDeMovimentosQuandoHaTaxa() throws Exception {
        long antes = quantidadeDeMovimentos();
        transferir("CONTA-001", "CONTA-002", "150.00", "tr-pares").andExpect(status().isCreated());
        assertThat(quantidadeDeMovimentos() - antes).isEqualTo(4);

        antes = quantidadeDeMovimentos();
        transferir("CONTA-001", "CONTA-002", "100.00", "tr-isenta").andExpect(status().isCreated());
        assertThat(quantidadeDeMovimentos() - antes).isEqualTo(2);
    }

    @Test
    void deveRecusarTransferenciaSaindoDeContaBloqueada() throws Exception {
        transferir("CONTA-006", "CONTA-001", "10.00", "tr-bloqueada")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("CONTA_ORIGEM_BLOQUEADA"));

        assertThat(saldo("CONTA-006")).isEqualTo(30_000L);
    }

    @Test
    void deveRecusarTransferenciaSaindoDeContaEncerrada() throws Exception {
        transferir("CONTA-007", "CONTA-001", "10.00", "tr-origem-encerrada")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("CONTA_ORIGEM_ENCERRADA"));
    }

    @Test
    void devePermitirTransferenciaParaContaBloqueada() throws Exception {
        transferir("CONTA-001", "CONTA-006", "10.00", "tr-para-bloqueada")
                .andExpect(status().isCreated());

        assertThat(saldo("CONTA-006")).isEqualTo(31_000L);
    }

    @Test
    void deveRecusarTransferenciaParaContaEncerrada() throws Exception {
        transferir("CONTA-001", "CONTA-007", "10.00", "tr-destino-encerrada")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("CONTA_DESTINO_ENCERRADA"));
    }

    @Test
    void deveRecusarOrigemIgualAoDestino() throws Exception {
        transferir("CONTA-001", "CONTA-001", "10.00", "tr-mesma")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("CONTAS_IGUAIS"));
    }

    @Test
    void deveRecusarUsoDeContaDoSistema() throws Exception {
        transferir("SISTEMA-ENTRADA", "CONTA-001", "10.00", "tr-sistema")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("CONTA_DO_SISTEMA"));
    }

    @Test
    void deveRetornar404ParaContaInexistente() throws Exception {
        transferir("CONTA-001", "CONTA-999", "10.00", "tr-404")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("CONTA_NAO_ENCONTRADA"));
    }

    @Test
    void deveRecusarSaldoInsuficienteConsiderandoATaxa() throws Exception {
        transferir("CONTA-001", "CONTA-002", "995.00", "tr-sem-saldo")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("SALDO_INSUFICIENTE"))
                .andExpect(jsonPath("$.message").value("Saldo insuficiente para transferir R$ 995,00 mais taxa de R$ 9,95"));

        assertThat(saldo("CONTA-001")).isEqualTo(100_000L);
    }

    @Test
    void deveRecusarValorZero() throws Exception {
        transferir("CONTA-001", "CONTA-002", "0.00", "tr-zero")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("VALOR_INVALIDO"));
    }

    @Test
    void deveRecusarTransferenciaQuePassaDoLimiteDiario() throws Exception {
        depositar("CONTA-001", "dep-limite", "5000.00").andExpect(status().isCreated());

        transferir("CONTA-001", "CONTA-002", "1500.00", "tr-limite-1").andExpect(status().isCreated());
        transferir("CONTA-001", "CONTA-003", "500.00", "tr-limite-2").andExpect(status().isCreated());

        transferir("CONTA-001", "CONTA-004", "0.01", "tr-limite-3")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("LIMITE_DIARIO_EXCEDIDO"));
    }

    @Test
    void valoresRecebidosNaoAumentamNemConsomemOLimite() throws Exception {
        depositar("CONTA-002", "dep-limite-2", "5000.00").andExpect(status().isCreated());
        transferir("CONTA-002", "CONTA-001", "1900.00", "tr-recebido").andExpect(status().isCreated());

        transferir("CONTA-001", "CONTA-003", "2000.00", "tr-limite-cheio")
                .andExpect(status().isCreated());
    }

    @Test
    void reenvioComMesmaChaveNaoGeraSegundaTransferencia() throws Exception {
        String primeira = transferir("CONTA-001", "CONTA-002", "150.00", "tr-repetida")
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long movimentos = quantidadeDeMovimentos();

        String segunda = transferir("CONTA-001", "CONTA-002", "150", "tr-repetida")
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(segunda).isEqualTo(primeira);
        assertThat(quantidadeDeMovimentos()).isEqualTo(movimentos);
        assertThat(saldo("CONTA-001")).isEqualTo(84_850L);
    }

    @Test
    void reenvioComMesmaChaveECorpoDiferenteRetorna409() throws Exception {
        transferir("CONTA-001", "CONTA-002", "150.00", "tr-diferente").andExpect(status().isCreated());

        transferir("CONTA-001", "CONTA-003", "150.00", "tr-diferente")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("IDEMPOTENCY_KEY_REUTILIZADA"));

        assertThat(saldo("CONTA-003")).isEqualTo(100_000L);
    }

    @Test
    void mesmaChaveEmEndpointsDiferentesNaoConflita() throws Exception {
        depositar("CONTA-001", "chave-compartilhada", "10.00").andExpect(status().isCreated());
        transferir("CONTA-001", "CONTA-002", "10.00", "chave-compartilhada").andExpect(status().isCreated());
    }

    @Test
    void deveRetornar400SemHeaderOuComCorpoInvalido() throws Exception {
        mockMvc.perform(post("/transferencias")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contaOrigem\": \"CONTA-001\", \"contaDestino\": \"CONTA-002\", \"valor\": 10}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("IDEMPOTENCY_KEY_AUSENTE"));

        postar("/transferencias", "tr-sem-destino", "{\"contaOrigem\": \"CONTA-001\", \"valor\": 10}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("CORPO_INVALIDO"));
    }

    @Test
    void transferenciasNosDoisSentidosMantemExtratoConsistente() throws Exception {
        for (int i = 0; i < 5; i++) {
            transferir("CONTA-001", "CONTA-002", "120.00", "ida-" + i).andExpect(status().isCreated());
            transferir("CONTA-002", "CONTA-001", "80.00", "volta-" + i).andExpect(status().isCreated());
        }
        depositar("CONTA-006", "dep-consistencia", "30.00").andExpect(status().isCreated());

        verificarConsistenciaDoExtrato();
    }
}
