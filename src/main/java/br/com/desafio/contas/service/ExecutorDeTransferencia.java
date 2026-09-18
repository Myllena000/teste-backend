package br.com.desafio.contas.service;

import br.com.desafio.contas.api.dto.ComprovanteTransferenciaResponse;
import br.com.desafio.contas.shared.DataHora;
import br.com.desafio.contas.shared.FormatacaoMonetaria;
import br.com.desafio.contas.entity.CalculadoraDeTaxa;
import br.com.desafio.contas.entity.Conta;
import br.com.desafio.contas.entity.ContasDoSistema;
import br.com.desafio.contas.entity.EstadoConta;
import br.com.desafio.contas.entity.NaturezaMovimento;
import br.com.desafio.contas.entity.Transferencia;
import br.com.desafio.contas.entity.ValidadorLimiteDiario;
import br.com.desafio.contas.exception.CodigoErro;
import br.com.desafio.contas.exception.ConflitoException;
import br.com.desafio.contas.exception.RegraDeNegocioException;
import br.com.desafio.contas.repository.MovimentoRepository;
import br.com.desafio.contas.repository.TransferenciaRepository;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class ExecutorDeTransferencia {

    private final ContaService contaService;
    private final LancamentoService lancamentoService;
    private final TransferenciaRepository transferenciaRepository;
    private final MovimentoRepository movimentoRepository;
    private final CalculadoraDeTaxa calculadoraDeTaxa;
    private final ValidadorLimiteDiario validadorLimiteDiario;
    private final Clock clock;

    public ExecutorDeTransferencia(ContaService contaService, LancamentoService lancamentoService,
                                   TransferenciaRepository transferenciaRepository,
                                   MovimentoRepository movimentoRepository, CalculadoraDeTaxa calculadoraDeTaxa,
                                   ValidadorLimiteDiario validadorLimiteDiario, Clock clock) {
        this.contaService = contaService;
        this.lancamentoService = lancamentoService;
        this.transferenciaRepository = transferenciaRepository;
        this.movimentoRepository = movimentoRepository;
        this.calculadoraDeTaxa = calculadoraDeTaxa;
        this.validadorLimiteDiario = validadorLimiteDiario;
        this.clock = clock;
    }

    public ComprovanteTransferenciaResponse executar(String numeroOrigem, String numeroDestino, long valorCentavos) {
        validarValor(valorCentavos);
        if (numeroOrigem.equals(numeroDestino)) {
            throw contasIguais();
        }
        Long origemId = contaService.buscarIdPorNumero(numeroOrigem);
        Long destinoId = contaService.buscarIdPorNumero(numeroDestino);
        return executar(origemId, destinoId, valorCentavos);
    }

    public ComprovanteTransferenciaResponse executar(Long origemId, Long destinoId, long valorCentavos) {
        validarValor(valorCentavos);
        if (origemId.equals(destinoId)) {
            throw contasIguais();
        }

        long taxa = calculadoraDeTaxa.calcular(valorCentavos);
        long totalDebitado = valorCentavos + taxa;

        List<Long> idsParaBloquear = new ArrayList<>(List.of(origemId, destinoId));
        Long taxasId = null;
        if (taxa > 0) {
            taxasId = contaService.buscarIdPorNumero(ContasDoSistema.TAXAS);
            idsParaBloquear.add(taxasId);
        }

        Map<Long, Conta> contas = contaService.bloquearEmOrdemDeId(idsParaBloquear);
        Conta origem = contas.get(origemId);
        Conta destino = contas.get(destinoId);

        validarContas(origem, destino);

        if (origem.getSaldoCentavos() < totalDebitado) {
            throw new RegraDeNegocioException(CodigoErro.SALDO_INSUFICIENTE, String.format(
                    "Saldo insuficiente para transferir %s mais taxa de %s",
                    FormatacaoMonetaria.formatar(valorCentavos), FormatacaoMonetaria.formatar(taxa)));
        }

        OffsetDateTime agora = OffsetDateTime.now(clock);
        long utilizadoHoje = movimentoRepository.somarValoresTransferidosNoPeriodo(
                origemId, DataHora.inicioDoDia(agora), DataHora.inicioDoDiaSeguinte(agora));
        validadorLimiteDiario.validar(origem.getLimiteDiarioCentavos(), utilizadoHoje, valorCentavos);

        Transferencia transferencia = transferenciaRepository.save(
                Transferencia.entreContas(origemId, destinoId, valorCentavos, taxa, agora));

        lancamentoService.debitar(origem, transferencia, NaturezaMovimento.VALOR, valorCentavos, agora);
        lancamentoService.creditar(destino, transferencia, NaturezaMovimento.VALOR, valorCentavos, agora);
        if (taxa > 0) {
            lancamentoService.debitar(origem, transferencia, NaturezaMovimento.TAXA, taxa, agora);
            lancamentoService.creditar(contas.get(taxasId), transferencia, NaturezaMovimento.TAXA, taxa, agora);
        }

        transferencia.confirmar(agora);

        return new ComprovanteTransferenciaResponse(
                transferencia.getId(),
                origem.getNumero(),
                destino.getNumero(),
                FormatacaoMonetaria.paraReais(valorCentavos),
                FormatacaoMonetaria.paraReais(taxa),
                FormatacaoMonetaria.paraReais(totalDebitado),
                FormatacaoMonetaria.paraReais(origem.getSaldoCentavos()),
                DataHora.noFusoBrasil(agora));
    }

    private void validarValor(long valorCentavos) {
        if (valorCentavos <= 0) {
            throw new RegraDeNegocioException(CodigoErro.VALOR_INVALIDO,
                    "O valor da transferência deve ser maior que zero");
        }
    }

    private void validarContas(Conta origem, Conta destino) {
        if (origem.isContaDoSistema() || destino.isContaDoSistema()) {
            throw new ConflitoException(CodigoErro.CONTA_DO_SISTEMA,
                    "Contas do sistema não podem ser usadas em transferências");
        }
        if (!origem.getEstado().podeEnviar()) {
            CodigoErro codigo = origem.getEstado() == EstadoConta.BLOQUEADA
                    ? CodigoErro.CONTA_ORIGEM_BLOQUEADA
                    : CodigoErro.CONTA_ORIGEM_ENCERRADA;
            throw new ConflitoException(codigo,
                    "A conta de origem " + origem.getNumero() + " está " + origem.getEstado() + " e não pode enviar");
        }
        if (!destino.getEstado().podeReceber()) {
            throw new ConflitoException(CodigoErro.CONTA_DESTINO_ENCERRADA,
                    "A conta de destino " + destino.getNumero() + " está ENCERRADA e não pode receber");
        }
    }

    private ConflitoException contasIguais() {
        return new ConflitoException(CodigoErro.CONTAS_IGUAIS, "A conta de origem deve ser diferente da conta de destino");
    }
}
