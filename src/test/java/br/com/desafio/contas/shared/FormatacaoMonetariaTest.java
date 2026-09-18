package br.com.desafio.contas.shared;

import br.com.desafio.contas.exception.RegraDeNegocioException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FormatacaoMonetariaTest {

    @Test
    void deveConverterReaisParaCentavos() {
        assertThat(FormatacaoMonetaria.paraCentavos(new BigDecimal("150.00"))).isEqualTo(15_000L);
        assertThat(FormatacaoMonetaria.paraCentavos(new BigDecimal("150"))).isEqualTo(15_000L);
        assertThat(FormatacaoMonetaria.paraCentavos(new BigDecimal("0.01"))).isEqualTo(1L);
        assertThat(FormatacaoMonetaria.paraCentavos(new BigDecimal("1234.5"))).isEqualTo(123_450L);
    }

    @Test
    void deveRecusarMaisDeDuasCasasDecimais() {
        assertThatThrownBy(() -> FormatacaoMonetaria.paraCentavos(new BigDecimal("10.001")))
                .isInstanceOf(RegraDeNegocioException.class);
    }

    @Test
    void deveConverterCentavosParaReaisComDuasCasas() {
        assertThat(FormatacaoMonetaria.paraReais(15_000L)).isEqualByComparingTo("150.00");
        assertThat(FormatacaoMonetaria.paraReais(15_000L).scale()).isEqualTo(2);
    }

    @Test
    void deveFormatarNoPadraoBrasileiro() {
        assertThat(FormatacaoMonetaria.formatar(180_000L)).isEqualTo("R$ 1.800,00");
        assertThat(FormatacaoMonetaria.formatar(1_800L)).isEqualTo("R$ 18,00");
    }
}
