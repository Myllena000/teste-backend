package br.com.desafio.contas.entity;

import br.com.desafio.contas.exception.TransicaoInvalidaException;

import java.util.Map;
import java.util.Set;

public final class MaquinaDeEstados {

    private static final Map<EstadoConta, Set<EstadoConta>> TRANSICOES_CONTA = Map.of(
            EstadoConta.ATIVA, Set.of(EstadoConta.BLOQUEADA, EstadoConta.ENCERRADA),
            EstadoConta.BLOQUEADA, Set.of(EstadoConta.ATIVA, EstadoConta.ENCERRADA),
            EstadoConta.ENCERRADA, Set.of()
    );

    private static final Map<EstadoTransferencia, Set<EstadoTransferencia>> TRANSICOES_TRANSFERENCIA = Map.of(
            EstadoTransferencia.CRIADA, Set.of(EstadoTransferencia.CONFIRMADA, EstadoTransferencia.FALHADA),
            EstadoTransferencia.CONFIRMADA, Set.of(EstadoTransferencia.ESTORNADA),
            EstadoTransferencia.ESTORNADA, Set.of(),
            EstadoTransferencia.FALHADA, Set.of()
    );

    private static final Map<EstadoAgendamento, Set<EstadoAgendamento>> TRANSICOES_AGENDAMENTO = Map.of(
            EstadoAgendamento.AGENDADO, Set.of(EstadoAgendamento.EXECUTADO, EstadoAgendamento.FALHADO),
            EstadoAgendamento.EXECUTADO, Set.of(),
            EstadoAgendamento.FALHADO, Set.of()
    );

    private MaquinaDeEstados() {
    }

    public static EstadoConta transicionar(EstadoConta atual, EstadoConta novo) {
        return validar("conta", TRANSICOES_CONTA, atual, novo);
    }

    public static EstadoTransferencia transicionar(EstadoTransferencia atual, EstadoTransferencia novo) {
        return validar("transferência", TRANSICOES_TRANSFERENCIA, atual, novo);
    }

    public static EstadoAgendamento transicionar(EstadoAgendamento atual, EstadoAgendamento novo) {
        return validar("agendamento", TRANSICOES_AGENDAMENTO, atual, novo);
    }

    private static <E extends Enum<E>> E validar(String entidade, Map<E, Set<E>> transicoes, E atual, E novo) {
        if (!transicoes.get(atual).contains(novo)) {
            throw new TransicaoInvalidaException(
                    "Não é permitido mudar o estado da " + entidade + " de " + atual + " para " + novo);
        }
        return novo;
    }
}
