package br.com.desafio.contas.service;

import br.com.desafio.contas.api.dto.ComprovanteEstornoResponse;
import br.com.desafio.contas.api.dto.EstornoRequest;
import br.com.desafio.contas.shared.DataHora;
import br.com.desafio.contas.shared.FormatacaoMonetaria;
import br.com.desafio.contas.shared.Hash;
import br.com.desafio.contas.entity.Conta;
import br.com.desafio.contas.entity.ContasDoSistema;
import br.com.desafio.contas.entity.EstadoConta;
import br.com.desafio.contas.entity.NaturezaMovimento;
import br.com.desafio.contas.entity.TipoTransferencia;
import br.com.desafio.contas.entity.Transferencia;
import br.com.desafio.contas.exception.CodigoErro;
import br.com.desafio.contas.exception.ConflitoException;
import br.com.desafio.contas.exception.NaoEncontradoException;
import br.com.desafio.contas.exception.RegraDeNegocioException;
import br.com.desafio.contas.metrics.MetricaConflitos;
import br.com.desafio.contas.repository.TransferenciaRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class EstornoService {

    private final IdempotenciaService idempotenciaService;
    private final ContaService contaService;
    private final LancamentoService lancamentoService;
    private final TransferenciaRepository transferenciaRepository;
    private final MetricaConflitos metricaConflitos;
    private final Clock clock;

    public EstornoService(IdempotenciaService idempotenciaService, ContaService contaService,
                          LancamentoService lancamentoService, TransferenciaRepository transferenciaRepository,
                          MetricaConflitos metricaConflitos, Clock clock) {
        this.idempotenciaService = idempotenciaService;
        this.contaService = contaService;
        this.lancamentoService = lancamentoService;
        this.transferenciaRepository = transferenciaRepository;
        this.metricaConflitos = metricaConflitos;
        this.clock = clock;
    }

    @Transactional
    public Resultado<ComprovanteEstornoResponse> estornar(String chave, Long transferenciaId, EstornoRequest request) {
        String hash = Hash.sha256(transferenciaId + "|" + request.motivo());

        Optional<ComprovanteEstornoResponse> respostaAnterior = idempotenciaService.reservar(
                chave, IdempotenciaService.ESTORNO, hash, ComprovanteEstornoResponse.class);
        if (respostaAnterior.isPresent()) {
            return Resultado.repetido(respostaAnterior.get());
        }

        Transferencia original = transferenciaRepository.buscarPorIdSeNaoEstiverBloqueada(transferenciaId)
                .orElseGet(() -> aguardarBloqueio(transferenciaId));

        if (original.getTipo() != TipoTransferencia.TRANSFERENCIA) {
            throw new ConflitoException(CodigoErro.ESTORNO_NAO_PERMITIDO,
                    "Somente transferências entre contas podem ser estornadas. Esta operação é do tipo " + original.getTipo());
        }

        original.marcarComoEstornada();

        long valor = original.getValorCentavos();
        long taxa = original.getTaxaCentavos();

        List<Long> idsParaBloquear = new ArrayList<>(List.of(original.getContaOrigemId(), original.getContaDestinoId()));
        Long taxasId = null;
        if (taxa > 0) {
            taxasId = contaService.buscarIdPorNumero(ContasDoSistema.TAXAS);
            idsParaBloquear.add(taxasId);
        }
        Map<Long, Conta> contas = contaService.bloquearEmOrdemDeId(idsParaBloquear);
        Conta pagadora = contas.get(original.getContaDestinoId());
        Conta recebedora = contas.get(original.getContaOrigemId());

        if (pagadora.getEstado() == EstadoConta.ENCERRADA || recebedora.getEstado() == EstadoConta.ENCERRADA) {
            throw new ConflitoException(CodigoErro.CONTA_ENCERRADA,
                    "Não é possível estornar porque uma das contas envolvidas está ENCERRADA");
        }
        if (pagadora.getSaldoCentavos() < valor) {
            throw new RegraDeNegocioException(CodigoErro.SALDO_INSUFICIENTE, String.format(
                    "A conta %s não tem saldo para devolver %s. Saldo atual: %s",
                    pagadora.getNumero(), FormatacaoMonetaria.formatar(valor), FormatacaoMonetaria.formatar(pagadora.getSaldoCentavos())));
        }

        OffsetDateTime agora = OffsetDateTime.now(clock);
        Transferencia estorno = transferenciaRepository.save(Transferencia.estornoDe(original, request.motivo(), agora));

        lancamentoService.debitar(pagadora, estorno, NaturezaMovimento.VALOR, valor, agora);
        lancamentoService.creditar(recebedora, estorno, NaturezaMovimento.VALOR, valor, agora);
        if (taxa > 0) {
            lancamentoService.debitar(contas.get(taxasId), estorno, NaturezaMovimento.TAXA, taxa, agora);
            lancamentoService.creditar(recebedora, estorno, NaturezaMovimento.TAXA, taxa, agora);
        }
        estorno.confirmar(agora);

        ComprovanteEstornoResponse comprovante = new ComprovanteEstornoResponse(
                estorno.getId(),
                original.getId(),
                pagadora.getNumero(),
                recebedora.getNumero(),
                FormatacaoMonetaria.paraReais(valor),
                FormatacaoMonetaria.paraReais(taxa),
                FormatacaoMonetaria.paraReais(valor + taxa),
                estorno.getMotivo(),
                DataHora.noFusoBrasil(agora));

        idempotenciaService.registrarResposta(chave, IdempotenciaService.ESTORNO, comprovante, HttpStatus.CREATED.value());
        return Resultado.criado(comprovante);
    }

    private Transferencia aguardarBloqueio(Long transferenciaId) {
        if (!transferenciaRepository.existsById(transferenciaId)) {
            throw transferenciaNaoEncontrada(transferenciaId);
        }
        metricaConflitos.registrar(MetricaConflitos.RECURSO_TRANSFERENCIA);
        return transferenciaRepository.buscarPorIdComBloqueio(transferenciaId)
                .orElseThrow(() -> transferenciaNaoEncontrada(transferenciaId));
    }

    private NaoEncontradoException transferenciaNaoEncontrada(Long transferenciaId) {
        return new NaoEncontradoException(CodigoErro.TRANSFERENCIA_NAO_ENCONTRADA,
                "Transferência " + transferenciaId + " não encontrada");
    }
}
