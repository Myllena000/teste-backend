package br.com.desafio.contas.entity;

import br.com.desafio.contas.exception.TransicaoInvalidaException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MaquinaDeEstadosTest {

    private static final OffsetDateTime AGORA = OffsetDateTime.parse("2026-09-10T12:00:00Z");

    @ParameterizedTest
    @CsvSource({
            "ATIVA, BLOQUEADA",
            "BLOQUEADA, ATIVA",
            "ATIVA, ENCERRADA",
            "BLOQUEADA, ENCERRADA"
    })
    void devePermitirTransicoesValidasDeConta(EstadoConta atual, EstadoConta novo) {
        assertThat(MaquinaDeEstados.transicionar(atual, novo)).isEqualTo(novo);
    }

    @ParameterizedTest
    @CsvSource({
            "ATIVA, ATIVA",
            "BLOQUEADA, BLOQUEADA",
            "ENCERRADA, ATIVA",
            "ENCERRADA, BLOQUEADA",
            "ENCERRADA, ENCERRADA"
    })
    void deveImpedirTransicoesProibidasDeConta(EstadoConta atual, EstadoConta novo) {
        assertThatThrownBy(() -> MaquinaDeEstados.transicionar(atual, novo))
                .isInstanceOf(TransicaoInvalidaException.class);
    }

    @ParameterizedTest
    @CsvSource({
            "CRIADA, CONFIRMADA",
            "CRIADA, FALHADA",
            "CONFIRMADA, ESTORNADA"
    })
    void devePermitirTransicoesValidasDeTransferencia(EstadoTransferencia atual, EstadoTransferencia novo) {
        assertThat(MaquinaDeEstados.transicionar(atual, novo)).isEqualTo(novo);
    }

    @ParameterizedTest
    @CsvSource({
            "CRIADA, ESTORNADA",
            "CRIADA, CRIADA",
            "CONFIRMADA, FALHADA",
            "CONFIRMADA, CRIADA",
            "ESTORNADA, ESTORNADA",
            "ESTORNADA, CONFIRMADA",
            "FALHADA, CONFIRMADA",
            "FALHADA, ESTORNADA"
    })
    void deveImpedirTransicoesProibidasDeTransferencia(EstadoTransferencia atual, EstadoTransferencia novo) {
        assertThatThrownBy(() -> MaquinaDeEstados.transicionar(atual, novo))
                .isInstanceOf(TransicaoInvalidaException.class);
    }

    @ParameterizedTest
    @CsvSource({
            "EXECUTADO, FALHADO",
            "FALHADO, EXECUTADO",
            "EXECUTADO, AGENDADO",
            "FALHADO, AGENDADO"
    })
    void deveImpedirTransicoesProibidasDeAgendamento(EstadoAgendamento atual, EstadoAgendamento novo) {
        assertThatThrownBy(() -> MaquinaDeEstados.transicionar(atual, novo))
                .isInstanceOf(TransicaoInvalidaException.class);
    }

    @Test
    void contaEncerradaNaoPodeSerReativada() {
        Conta conta = new Conta(1L, "CONTA-001", 1L, 0L, 200_000L, EstadoConta.ATIVA);
        conta.encerrar();

        assertThat(conta.getEstado()).isEqualTo(EstadoConta.ENCERRADA);
        assertThatThrownBy(conta::desbloquear).isInstanceOf(TransicaoInvalidaException.class);
        assertThatThrownBy(conta::bloquear).isInstanceOf(TransicaoInvalidaException.class);
    }

    @Test
    void transferenciaNaoPodeSerEstornadaAntesDeConfirmada() {
        Transferencia transferencia = Transferencia.entreContas(1L, 2L, 10_000L, 0L, AGORA);

        assertThatThrownBy(transferencia::marcarComoEstornada).isInstanceOf(TransicaoInvalidaException.class);
    }

    @Test
    void transferenciaNaoPodeSerEstornadaDuasVezes() {
        Transferencia transferencia = Transferencia.entreContas(1L, 2L, 10_000L, 0L, AGORA);
        transferencia.confirmar(AGORA);
        transferencia.marcarComoEstornada();

        assertThat(transferencia.getEstado()).isEqualTo(EstadoTransferencia.ESTORNADA);
        assertThatThrownBy(transferencia::marcarComoEstornada).isInstanceOf(TransicaoInvalidaException.class);
    }

    @Test
    void agendamentoExecutadoNaoPodeFalharDepois() {
        Agendamento agendamento = new Agendamento(1L, 2L, 10_000L, AGORA, AGORA);
        agendamento.marcarComoExecutado(10L, AGORA);

        assertThatThrownBy(() -> agendamento.marcarComoFalhado("teste", AGORA))
                .isInstanceOf(TransicaoInvalidaException.class);
    }

    @Test
    void agendamentoSoFalhaPorErroTecnicoAoAtingirMaximoDeTentativas() {
        Agendamento agendamento = new Agendamento(1L, 2L, 10_000L, AGORA, AGORA);

        agendamento.registrarErroTecnico("erro 1", 3, AGORA);
        agendamento.registrarErroTecnico("erro 2", 3, AGORA);
        assertThat(agendamento.getEstado()).isEqualTo(EstadoAgendamento.AGENDADO);

        agendamento.registrarErroTecnico("erro 3", 3, AGORA);
        assertThat(agendamento.getEstado()).isEqualTo(EstadoAgendamento.FALHADO);
        assertThat(agendamento.getTentativas()).isEqualTo(3);
    }
}
