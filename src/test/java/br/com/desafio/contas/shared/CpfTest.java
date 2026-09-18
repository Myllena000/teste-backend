package br.com.desafio.contas.shared;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CpfTest {

    @Test
    void deveMascararNoFormatoCombinado() {
        assertThat(Cpf.mascarar("12345678901")).isEqualTo("***.456.789-**");
    }

    @Test
    void naoDeveExporValorForaDoFormato() {
        assertThat(Cpf.mascarar("123")).isEqualTo("***.***.***-**");
        assertThat(Cpf.mascarar(null)).isEqualTo("***.***.***-**");
    }
}
