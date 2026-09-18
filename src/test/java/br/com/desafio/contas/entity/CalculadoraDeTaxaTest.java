package br.com.desafio.contas.entity;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class CalculadoraDeTaxaTest {

    private CalculadoraDeTaxa calculadora = new CalculadoraDeTaxa();

    @ParameterizedTest(name = "valor {0} centavos gera taxa de {1} centavos")
    @CsvSource({
            "1000, 0",
            "10000, 0",
            "10001, 100",
            "11250, 113",
            "15000, 150",
            "123456, 1235",
            "200000, 2000",
            "500000, 2000"
    })
    void deveCalcularTaxaConformeTabelaDeReferencia(long valorCentavos, long taxaEsperada) {
        assertThat(calculadora.calcular(valorCentavos)).isEqualTo(taxaEsperada);
    }

    @ParameterizedTest
    @CsvSource({
            "10050, 101",
            "10049, 100",
            "12345, 123",
            "12350, 124"
    })
    void deveArredondarMeioCentavoParaCima(long valorCentavos, long taxaEsperada) {
        assertThat(calculadora.calcular(valorCentavos)).isEqualTo(taxaEsperada);
    }
}
