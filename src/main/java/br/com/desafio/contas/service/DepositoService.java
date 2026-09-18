package br.com.desafio.contas.service;

import br.com.desafio.contas.api.dto.ComprovanteDepositoResponse;
import br.com.desafio.contas.api.dto.DepositoRequest;
import br.com.desafio.contas.shared.DataHora;
import br.com.desafio.contas.shared.FormatacaoMonetaria;
import br.com.desafio.contas.shared.Hash;
import br.com.desafio.contas.entity.Conta;
import br.com.desafio.contas.entity.ContasDoSistema;
import br.com.desafio.contas.entity.NaturezaMovimento;
import br.com.desafio.contas.entity.Transferencia;
import br.com.desafio.contas.exception.CodigoErro;
import br.com.desafio.contas.exception.ConflitoException;
import br.com.desafio.contas.exception.RegraDeNegocioException;
import br.com.desafio.contas.repository.TransferenciaRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class DepositoService {

    private static final long VALOR_MAXIMO_CENTAVOS = 1_000_000L;

    private final IdempotenciaService idempotenciaService;
    private final ContaService contaService;
    private final LancamentoService lancamentoService;
    private final TransferenciaRepository transferenciaRepository;
    private final Clock clock;

    public DepositoService(IdempotenciaService idempotenciaService, ContaService contaService,
                           LancamentoService lancamentoService, TransferenciaRepository transferenciaRepository,
                           Clock clock) {
        this.idempotenciaService = idempotenciaService;
        this.contaService = contaService;
        this.lancamentoService = lancamentoService;
        this.transferenciaRepository = transferenciaRepository;
        this.clock = clock;
    }

    @Transactional
    public Resultado<ComprovanteDepositoResponse> depositar(String chave, String numeroConta, DepositoRequest request) {
        long valor = FormatacaoMonetaria.paraCentavos(request.valor());
        String hash = Hash.sha256(numeroConta + "|" + valor);

        Optional<ComprovanteDepositoResponse> respostaAnterior = idempotenciaService.reservar(
                chave, IdempotenciaService.DEPOSITO, hash, ComprovanteDepositoResponse.class);
        if (respostaAnterior.isPresent()) {
            return Resultado.repetido(respostaAnterior.get());
        }

        validarValor(valor);

        Long contaId = contaService.buscarIdPorNumero(numeroConta);
        Long entradaId = contaService.buscarIdPorNumero(ContasDoSistema.ENTRADA);
        Map<Long, Conta> contas = contaService.bloquearEmOrdemDeId(List.of(contaId, entradaId));
        Conta conta = contas.get(contaId);
        Conta entrada = contas.get(entradaId);

        if (conta.isContaDoSistema()) {
            throw new ConflitoException(CodigoErro.CONTA_DO_SISTEMA, "Não é permitido depositar em conta do sistema");
        }
        if (!conta.getEstado().podeReceber()) {
            throw new ConflitoException(CodigoErro.CONTA_ENCERRADA,
                    "A conta " + numeroConta + " está ENCERRADA e não pode receber depósitos");
        }

        OffsetDateTime agora = OffsetDateTime.now(clock);
        Transferencia deposito = transferenciaRepository.save(Transferencia.deposito(entradaId, contaId, valor, agora));
        lancamentoService.debitar(entrada, deposito, NaturezaMovimento.VALOR, valor, agora);
        lancamentoService.creditar(conta, deposito, NaturezaMovimento.VALOR, valor, agora);
        deposito.confirmar(agora);

        ComprovanteDepositoResponse comprovante = new ComprovanteDepositoResponse(
                deposito.getId(),
                conta.getNumero(),
                FormatacaoMonetaria.paraReais(valor),
                FormatacaoMonetaria.paraReais(conta.getSaldoCentavos()),
                DataHora.noFusoBrasil(agora));

        idempotenciaService.registrarResposta(chave, IdempotenciaService.DEPOSITO, comprovante, HttpStatus.CREATED.value());
        return Resultado.criado(comprovante);
    }

    private void validarValor(long valor) {
        if (valor <= 0) {
            throw new RegraDeNegocioException(CodigoErro.VALOR_INVALIDO, "O valor do depósito deve ser maior que zero");
        }
        if (valor > VALOR_MAXIMO_CENTAVOS) {
            throw new RegraDeNegocioException(CodigoErro.VALOR_ACIMA_DO_PERMITIDO,
                    "O valor do depósito não pode ser maior que " + FormatacaoMonetaria.formatar(VALOR_MAXIMO_CENTAVOS));
        }
    }
}
