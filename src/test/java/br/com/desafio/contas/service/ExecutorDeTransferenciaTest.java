package br.com.desafio.contas.service;

import br.com.desafio.contas.api.dto.ComprovanteTransferenciaResponse;
import br.com.desafio.contas.entity.CalculadoraDeTaxa;
import br.com.desafio.contas.entity.Conta;
import br.com.desafio.contas.entity.ContasDoSistema;
import br.com.desafio.contas.entity.EstadoConta;
import br.com.desafio.contas.entity.NaturezaMovimento;
import br.com.desafio.contas.entity.Transferencia;
import br.com.desafio.contas.entity.ValidadorLimiteDiario;
import br.com.desafio.contas.exception.CodigoErro;
import br.com.desafio.contas.exception.NegocioException;
import br.com.desafio.contas.repository.MovimentoRepository;
import br.com.desafio.contas.repository.TransferenciaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExecutorDeTransferenciaTest {

    private static final long ORIGEM_ID = 3L;
    private static final long DESTINO_ID = 4L;
    private static final long TAXAS_ID = 2L;

    @Mock
    private ContaService contaService;
    @Mock
    private LancamentoService lancamentoService;
    @Mock
    private TransferenciaRepository transferenciaRepository;
    @Mock
    private MovimentoRepository movimentoRepository;

    private ExecutorDeTransferencia executor;

    @BeforeEach
    void configurar() {
        Clock clock = Clock.fixed(Instant.parse("2026-09-11T02:30:00Z"), ZoneOffset.UTC);
        executor = new ExecutorDeTransferencia(contaService, lancamentoService, transferenciaRepository,
                movimentoRepository, new CalculadoraDeTaxa(), new ValidadorLimiteDiario(), clock);
    }

    private Conta conta(long id, long saldo, EstadoConta estado) {
        return new Conta(id, "CONTA-00" + id, id, saldo, 200_000L, estado);
    }

    @Test
    void deveRecusarTransferenciaDeContaBloqueadaSemGravarNada() {
        prepararContas(conta(ORIGEM_ID, 100_000L, EstadoConta.BLOQUEADA), conta(DESTINO_ID, 0L, EstadoConta.ATIVA), false);

        assertThatThrownBy(() -> executor.executar(ORIGEM_ID, DESTINO_ID, 5_000L))
                .isInstanceOf(NegocioException.class)
                .extracting("codigo")
                .isEqualTo(CodigoErro.CONTA_ORIGEM_BLOQUEADA);

        verifyNoInteractions(lancamentoService, transferenciaRepository);
    }

    @Test
    void deveRecusarTransferenciaParaContaEncerrada() {
        prepararContas(conta(ORIGEM_ID, 100_000L, EstadoConta.ATIVA), conta(DESTINO_ID, 0L, EstadoConta.ENCERRADA), false);

        assertThatThrownBy(() -> executor.executar(ORIGEM_ID, DESTINO_ID, 5_000L))
                .extracting("codigo")
                .isEqualTo(CodigoErro.CONTA_DESTINO_ENCERRADA);
    }

    @Test
    void deveRecusarQuandoSaldoNaoCobreValorMaisTaxa() {
        prepararContas(conta(ORIGEM_ID, 15_100L, EstadoConta.ATIVA), conta(DESTINO_ID, 0L, EstadoConta.ATIVA), true);

        assertThatThrownBy(() -> executor.executar(ORIGEM_ID, DESTINO_ID, 15_000L))
                .isInstanceOf(NegocioException.class)
                .hasMessage("Saldo insuficiente para transferir R$ 150,00 mais taxa de R$ 1,50")
                .extracting("codigo")
                .isEqualTo(CodigoErro.SALDO_INSUFICIENTE);

        verifyNoInteractions(lancamentoService, transferenciaRepository);
    }

    @Test
    void deveConsultarLimiteUsandoODiaDeSaoPaulo() {
        prepararContas(conta(ORIGEM_ID, 500_000L, EstadoConta.ATIVA), conta(DESTINO_ID, 0L, EstadoConta.ATIVA), true);
        OffsetDateTime inicioDoDia = OffsetDateTime.parse("2026-09-10T00:00:00-03:00");
        OffsetDateTime inicioDoDiaSeguinte = OffsetDateTime.parse("2026-09-11T00:00:00-03:00");
        when(movimentoRepository.somarValoresTransferidosNoPeriodo(eq(ORIGEM_ID), any(), any())).thenReturn(190_000L);

        assertThatThrownBy(() -> executor.executar(ORIGEM_ID, DESTINO_ID, 15_000L))
                .extracting("codigo")
                .isEqualTo(CodigoErro.LIMITE_DIARIO_EXCEDIDO);

        verify(movimentoRepository).somarValoresTransferidosNoPeriodo(
                eq(ORIGEM_ID),
                eq(inicioDoDia),
                eq(inicioDoDiaSeguinte));
        verify(transferenciaRepository, never()).save(any());
    }

    @Test
    void deveRecusarOrigemIgualAoDestino() {
        assertThatThrownBy(() -> executor.executar("CONTA-001", "CONTA-001", 1_000L))
                .extracting("codigo")
                .isEqualTo(CodigoErro.CONTAS_IGUAIS);
        verifyNoInteractions(contaService);
    }

    @Test
    void deveRecusarValorZero() {
        assertThatThrownBy(() -> executor.executar(ORIGEM_ID, DESTINO_ID, 0L))
                .extracting("codigo")
                .isEqualTo(CodigoErro.VALOR_INVALIDO);
    }

    @Test
    void deveGerarMovimentosDeValorEDeTaxaQuandoHaTaxa() {
        Conta origem = conta(ORIGEM_ID, 100_000L, EstadoConta.ATIVA);
        Conta destino = conta(DESTINO_ID, 0L, EstadoConta.ATIVA);
        Conta taxas = new Conta(TAXAS_ID, ContasDoSistema.TAXAS, null, 0L, 0L, EstadoConta.ATIVA);
        when(contaService.buscarIdPorNumero(ContasDoSistema.TAXAS)).thenReturn(TAXAS_ID);
        when(contaService.bloquearEmOrdemDeId(anyCollection()))
                .thenReturn(Map.of(ORIGEM_ID, origem, DESTINO_ID, destino, TAXAS_ID, taxas));
        when(movimentoRepository.somarValoresTransferidosNoPeriodo(eq(ORIGEM_ID), any(), any())).thenReturn(0L);
        when(transferenciaRepository.save(any(Transferencia.class))).thenAnswer(invocacao -> invocacao.getArgument(0));

        ComprovanteTransferenciaResponse comprovante = executor.executar(ORIGEM_ID, DESTINO_ID, 15_000L);

        verify(contaService).bloquearEmOrdemDeId(List.of(ORIGEM_ID, DESTINO_ID, TAXAS_ID));
        verify(lancamentoService).debitar(eq(origem), any(), eq(NaturezaMovimento.VALOR), eq(15_000L), any());
        verify(lancamentoService).creditar(eq(destino), any(), eq(NaturezaMovimento.VALOR), eq(15_000L), any());
        verify(lancamentoService).debitar(eq(origem), any(), eq(NaturezaMovimento.TAXA), eq(150L), any());
        verify(lancamentoService).creditar(eq(taxas), any(), eq(NaturezaMovimento.TAXA), eq(150L), any());
        assertThat(comprovante.taxa()).isEqualByComparingTo("1.50");
        assertThat(comprovante.totalDebitado()).isEqualByComparingTo("151.50");
    }

    @Test
    void naoDeveBloquearContaDeTaxasQuandoTransferenciaEhIsenta() {
        Conta origem = conta(ORIGEM_ID, 100_000L, EstadoConta.ATIVA);
        Conta destino = conta(DESTINO_ID, 0L, EstadoConta.ATIVA);
        when(contaService.bloquearEmOrdemDeId(anyCollection())).thenReturn(Map.of(ORIGEM_ID, origem, DESTINO_ID, destino));
        when(transferenciaRepository.save(any(Transferencia.class))).thenAnswer(invocacao -> invocacao.getArgument(0));

        executor.executar(ORIGEM_ID, DESTINO_ID, 10_000L);

        verify(contaService, never()).buscarIdPorNumero(ContasDoSistema.TAXAS);
        verify(lancamentoService, never()).debitar(any(), any(), eq(NaturezaMovimento.TAXA), anyLong(), any());
    }

    private void prepararContas(Conta origem, Conta destino, boolean comTaxa) {
        if (comTaxa) {
            Conta taxas = new Conta(TAXAS_ID, ContasDoSistema.TAXAS, null, 0L, 0L, EstadoConta.ATIVA);
            when(contaService.buscarIdPorNumero(ContasDoSistema.TAXAS)).thenReturn(TAXAS_ID);
            when(contaService.bloquearEmOrdemDeId(anyCollection()))
                    .thenReturn(Map.of(ORIGEM_ID, origem, DESTINO_ID, destino, TAXAS_ID, taxas));
        } else {
            when(contaService.bloquearEmOrdemDeId(anyCollection())).thenReturn(Map.of(ORIGEM_ID, origem, DESTINO_ID, destino));
        }
    }
}
