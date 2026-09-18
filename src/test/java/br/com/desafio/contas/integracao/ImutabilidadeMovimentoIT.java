package br.com.desafio.contas.integracao;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ImutabilidadeMovimentoIT extends IntegracaoBase {

    @Test
    void bancoDeveRecusarUpdateEmMovimento() {
        assertThatThrownBy(() -> jdbcTemplate.update("update movimento set valor_centavos = 1 where sequencia = 1"))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("Movimentos nao podem ser alterados nem apagados");
    }

    @Test
    void bancoDeveRecusarDeleteEmMovimento() {
        long antes = quantidadeDeMovimentos();

        assertThatThrownBy(() -> jdbcTemplate.update("delete from movimento"))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("Movimentos nao podem ser alterados nem apagados");

        assertThat(quantidadeDeMovimentos()).isEqualTo(antes);
    }

    @Test
    void bancoDeveRecusarTruncateForaDoResetDeDados() {
        assertThatThrownBy(() -> jdbcTemplate.execute("truncate table movimento cascade"))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("Movimentos nao podem ser apagados");
    }

    @Test
    void bancoDeveRecusarMovimentoComSequenciaForaDeOrdem() {
        Long contaId = jdbcTemplate.queryForObject("select id from conta where numero = 'CONTA-001'", Long.class);
        Long transferenciaId = jdbcTemplate.queryForObject("select max(id) from transferencia", Long.class);

        assertThatThrownBy(() -> jdbcTemplate.update("""
                insert into movimento (conta_id, transferencia_id, sequencia, tipo, natureza, valor_centavos,
                                       saldo_apos_centavos, criado_em)
                values (?, ?, 5, 'ENTRADA', 'VALOR', 100, 100100, now())
                """, contaId, transferenciaId))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("Sequencia 5 invalida");
    }

    @Test
    void bancoDeveRecusarContaDeUsuarioComSaldoNegativo() {
        assertThatThrownBy(() -> jdbcTemplate.update("update conta set saldo_centavos = -1 where numero = 'CONTA-001'"))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("ck_conta_saldo_usuario");
    }
}
