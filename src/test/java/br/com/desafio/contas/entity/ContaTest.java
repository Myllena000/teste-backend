package br.com.desafio.contas.entity;

import br.com.desafio.contas.exception.RegraDeNegocioException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ContaTest {

    @Test
    void contaDeUsuarioNaoPodeFicarNegativa() {
        Conta conta = new Conta(3L, "CONTA-001", 1L, 5_000L, 200_000L, EstadoConta.ATIVA);

        assertThatThrownBy(() -> conta.debitar(5_001L)).isInstanceOf(RegraDeNegocioException.class);
        assertThat(conta.getSaldoCentavos()).isEqualTo(5_000L);
    }

    @Test
    void contaDoSistemaPodeFicarNegativa() {
        Conta entrada = new Conta(1L, ContasDoSistema.ENTRADA, null, 0L, 0L, EstadoConta.ATIVA);

        entrada.debitar(100_000L);

        assertThat(entrada.getSaldoCentavos()).isEqualTo(-100_000L);
    }

    @Test
    void contaBloqueadaRecebeMasNaoEnvia() {
        assertThat(EstadoConta.BLOQUEADA.podeReceber()).isTrue();
        assertThat(EstadoConta.BLOQUEADA.podeEnviar()).isFalse();
        assertThat(EstadoConta.ENCERRADA.podeReceber()).isFalse();
        assertThat(EstadoConta.ENCERRADA.podeEnviar()).isFalse();
        assertThat(EstadoConta.ATIVA.podeEnviar()).isTrue();
    }
}
