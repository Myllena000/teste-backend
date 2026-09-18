package br.com.desafio.contas.service;

import br.com.desafio.contas.entity.Conta;
import br.com.desafio.contas.entity.EstadoConta;
import br.com.desafio.contas.exception.NaoEncontradoException;
import br.com.desafio.contas.metrics.MetricaConflitos;
import br.com.desafio.contas.repository.ContaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ContaServiceTest {

    @Mock
    private ContaRepository contaRepository;

    @Mock
    private MetricaConflitos metricaConflitos;

    @InjectMocks
    private ContaService contaService;

    private Conta conta(Long id) {
        return new Conta(id, "CONTA-" + id, id, 100_000L, 200_000L, EstadoConta.ATIVA);
    }

    @Test
    void deveBloquearContasSempreEmOrdemCrescenteDeId() {
        when(contaRepository.buscarPorIdSeNaoEstiverBloqueada(anyLong()))
                .thenAnswer(invocacao -> Optional.of(conta(invocacao.getArgument(0))));

        Map<Long, Conta> contas = contaService.bloquearEmOrdemDeId(List.of(9L, 3L, 2L, 3L));

        InOrder ordem = inOrder(contaRepository);
        ordem.verify(contaRepository).buscarPorIdSeNaoEstiverBloqueada(2L);
        ordem.verify(contaRepository).buscarPorIdSeNaoEstiverBloqueada(3L);
        ordem.verify(contaRepository).buscarPorIdSeNaoEstiverBloqueada(9L);
        verify(contaRepository, times(3)).buscarPorIdSeNaoEstiverBloqueada(anyLong());
        assertThat(contas).containsOnlyKeys(2L, 3L, 9L);
    }

    @Test
    void transferenciasCruzadasBloqueiamNaMesmaOrdem() {
        when(contaRepository.buscarPorIdSeNaoEstiverBloqueada(anyLong()))
                .thenAnswer(invocacao -> Optional.of(conta(invocacao.getArgument(0))));

        contaService.bloquearEmOrdemDeId(List.of(4L, 3L));
        contaService.bloquearEmOrdemDeId(List.of(3L, 4L));

        InOrder ordem = inOrder(contaRepository);
        ordem.verify(contaRepository).buscarPorIdSeNaoEstiverBloqueada(3L);
        ordem.verify(contaRepository).buscarPorIdSeNaoEstiverBloqueada(4L);
        ordem.verify(contaRepository).buscarPorIdSeNaoEstiverBloqueada(3L);
        ordem.verify(contaRepository).buscarPorIdSeNaoEstiverBloqueada(4L);
    }

    @Test
    void contaLivreNaoRegistraConflitoNemEspera() {
        when(contaRepository.buscarPorIdSeNaoEstiverBloqueada(3L)).thenReturn(Optional.of(conta(3L)));

        contaService.bloquearEmOrdemDeId(List.of(3L));

        verify(contaRepository, never()).buscarPorIdComBloqueio(anyLong());
        verifyNoInteractions(metricaConflitos);
    }

    @Test
    void contaBloqueadaPorOutraTransacaoRegistraConflitoEAguardaNaMesmaOrdem() {
        when(contaRepository.buscarPorIdSeNaoEstiverBloqueada(3L)).thenReturn(Optional.empty());
        when(contaRepository.buscarPorIdComBloqueio(3L)).thenReturn(Optional.of(conta(3L)));
        when(contaRepository.buscarPorIdSeNaoEstiverBloqueada(4L)).thenReturn(Optional.of(conta(4L)));

        contaService.bloquearEmOrdemDeId(List.of(4L, 3L));

        InOrder ordem = inOrder(contaRepository, metricaConflitos);
        ordem.verify(contaRepository).buscarPorIdSeNaoEstiverBloqueada(3L);
        ordem.verify(metricaConflitos).registrar(MetricaConflitos.RECURSO_CONTA);
        ordem.verify(contaRepository).buscarPorIdComBloqueio(3L);
        ordem.verify(contaRepository).buscarPorIdSeNaoEstiverBloqueada(4L);
    }

    @Test
    void deveLancarNaoEncontradoQuandoNumeroNaoExiste() {
        when(contaRepository.buscarIdPorNumero("CONTA-999")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> contaService.buscarIdPorNumero("CONTA-999"))
                .isInstanceOf(NaoEncontradoException.class)
                .hasMessageContaining("CONTA-999");
    }
}
