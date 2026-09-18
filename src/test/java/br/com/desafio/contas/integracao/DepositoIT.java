package br.com.desafio.contas.integracao;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DepositoIT extends IntegracaoBase {

    @Test
    void deveDepositarERetornarComprovanteComSaldoNovo() throws Exception {
        depositar("CONTA-001", "dep-1", "250.00")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.conta").value("CONTA-001"))
                .andExpect(jsonPath("$.valor").value(250.00))
                .andExpect(jsonPath("$.saldoApos").value(1250.00));

        assertThat(saldo("CONTA-001")).isEqualTo(125_000L);
        assertThat(saldo("SISTEMA-ENTRADA")).isEqualTo(-555_000L);
        verificarConsistenciaDoExtrato();
    }

    @Test
    void deveAceitarDepositoEmContaBloqueada() throws Exception {
        depositar("CONTA-006", "dep-bloqueada", "100.00")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.saldoApos").value(400.00));
    }

    @Test
    void deveRecusarDepositoEmContaEncerrada() throws Exception {
        depositar("CONTA-007", "dep-encerrada", "100.00")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("CONTA_ENCERRADA"))
                .andExpect(jsonPath("$.path").value("/contas/CONTA-007/depositos"));
    }

    @Test
    void deveRetornar404ParaContaInexistente() throws Exception {
        depositar("CONTA-999", "dep-404", "100.00")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("CONTA_NAO_ENCONTRADA"));
    }

    @Test
    void deveRetornar422ParaValorZeroOuAcimaDoMaximo() throws Exception {
        depositar("CONTA-001", "dep-zero", "0")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("VALOR_INVALIDO"));

        depositar("CONTA-001", "dep-negativo", "-10.00")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("VALOR_INVALIDO"));

        depositar("CONTA-001", "dep-alto", "10000.01")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("VALOR_ACIMA_DO_PERMITIDO"));
    }

    @Test
    void deveRetornar400SemIdempotencia() throws Exception {
        mockMvc.perform(post("/contas/CONTA-001/depositos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"valor\": 10.00}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("IDEMPOTENCY_KEY_AUSENTE"))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void deveRetornar400ParaCorpoInvalido() throws Exception {
        postar("/contas/CONTA-001/depositos", "dep-corpo", "{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("CORPO_INVALIDO"));

        postar("/contas/CONTA-001/depositos", "dep-json", "{valor:")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("CORPO_INVALIDO"));
    }

    @Test
    void reenvioComMesmaChaveEMesmoCorpoDevolveRespostaOriginalSemNovosMovimentos() throws Exception {
        String primeira = depositar("CONTA-001", "dep-repetido", "50.00")
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long movimentosDepoisDaPrimeira = quantidadeDeMovimentos();

        String segunda = depositar("CONTA-001", "dep-repetido", "50.00")
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(segunda).isEqualTo(primeira);
        assertThat(quantidadeDeMovimentos()).isEqualTo(movimentosDepoisDaPrimeira);
        assertThat(saldo("CONTA-001")).isEqualTo(105_000L);
    }

    @Test
    void reenvioComMesmaChaveECorpoDiferenteRetorna409() throws Exception {
        depositar("CONTA-001", "dep-diferente", "50.00").andExpect(status().isCreated());

        depositar("CONTA-001", "dep-diferente", "60.00")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("IDEMPOTENCY_KEY_REUTILIZADA"));

        assertThat(saldo("CONTA-001")).isEqualTo(105_000L);
    }
}
