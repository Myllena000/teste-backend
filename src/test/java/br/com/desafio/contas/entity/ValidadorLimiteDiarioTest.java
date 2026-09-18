package br.com.desafio.contas.entity;

import br.com.desafio.contas.exception.CodigoErro;
import br.com.desafio.contas.exception.RegraDeNegocioException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ValidadorLimiteDiarioTest {

    private static final long LIMITE = 200_000L;

    private final ValidadorLimiteDiario validador = new ValidadorLimiteDiario();

    @Test
    void deveAceitarQuandoNadaFoiUsadoNoDia() {
        assertThatCode(() -> validador.validar(LIMITE, 0L, 150_000L))
                .doesNotThrowAnyException();
    }

    @Test
    void deveAceitarValorQueCompletaExatamenteOLimite() {
        assertThatCode(() -> validador.validar(LIMITE, 150_000L, 50_000L))
                .doesNotThrowAnyException();
    }

    @Test
    void deveRecusarQuandoPassaDoLimiteEmUmCentavo() {
        assertThatThrownBy(() -> validador.validar(LIMITE, 150_000L, 50_001L))
                .isInstanceOf(RegraDeNegocioException.class)
                .extracting("codigo")
                .isEqualTo(CodigoErro.LIMITE_DIARIO_EXCEDIDO);
    }

    @Test
    void deveRecusarQuandoOLimiteJaFoiTodoUsado() {
        assertThatThrownBy(() -> validador.validar(LIMITE, LIMITE, 1L))
                .isInstanceOf(RegraDeNegocioException.class)
                .hasMessageContaining("R$ 2.000,00");
    }

    @Test
    void deveRecusarValorSozinhoMaiorQueOLimite() {
        assertThatThrownBy(() -> validador.validar(LIMITE, 0L, 200_001L))
                .isInstanceOf(RegraDeNegocioException.class);
    }
}
