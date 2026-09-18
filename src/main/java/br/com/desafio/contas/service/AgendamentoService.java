package br.com.desafio.contas.service;

import br.com.desafio.contas.api.dto.AgendamentoRequest;
import br.com.desafio.contas.api.dto.AgendamentoResponse;
import br.com.desafio.contas.shared.DataHora;
import br.com.desafio.contas.shared.FormatacaoMonetaria;
import br.com.desafio.contas.shared.Hash;
import br.com.desafio.contas.entity.Agendamento;
import br.com.desafio.contas.entity.Conta;
import br.com.desafio.contas.exception.CodigoErro;
import br.com.desafio.contas.exception.ConflitoException;
import br.com.desafio.contas.exception.RegraDeNegocioException;
import br.com.desafio.contas.repository.AgendamentoRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Optional;

@Service
public class AgendamentoService {

    private final IdempotenciaService idempotenciaService;
    private final ContaService contaService;
    private final AgendamentoRepository agendamentoRepository;
    private final Clock clock;

    public AgendamentoService(IdempotenciaService idempotenciaService, ContaService contaService,
                              AgendamentoRepository agendamentoRepository, Clock clock) {
        this.idempotenciaService = idempotenciaService;
        this.contaService = contaService;
        this.agendamentoRepository = agendamentoRepository;
        this.clock = clock;
    }

    @Transactional
    public Resultado<AgendamentoResponse> agendar(String chave, AgendamentoRequest request) {
        long valor = FormatacaoMonetaria.paraCentavos(request.valor());
        String hash = Hash.sha256(request.contaOrigem() + "|" + request.contaDestino() + "|" + valor + "|"
                + request.executarEm().toInstant());

        Optional<AgendamentoResponse> respostaAnterior = idempotenciaService.reservar(
                chave, IdempotenciaService.AGENDAMENTO, hash, AgendamentoResponse.class);
        if (respostaAnterior.isPresent()) {
            return Resultado.repetido(respostaAnterior.get());
        }

        if (valor <= 0) {
            throw new RegraDeNegocioException(CodigoErro.VALOR_INVALIDO, "O valor do agendamento deve ser maior que zero");
        }

        OffsetDateTime agora = OffsetDateTime.now(clock);
        if (!request.executarEm().isAfter(agora)) {
            throw new RegraDeNegocioException(CodigoErro.DATA_DE_EXECUCAO_INVALIDA,
                    "A data de execução deve estar no futuro");
        }

        if (request.contaOrigem().equals(request.contaDestino())) {
            throw new ConflitoException(CodigoErro.CONTAS_IGUAIS,
                    "A conta de origem deve ser diferente da conta de destino");
        }

        Conta origem = contaService.buscarPorNumero(request.contaOrigem());
        Conta destino = contaService.buscarPorNumero(request.contaDestino());
        if (origem.isContaDoSistema() || destino.isContaDoSistema()) {
            throw new ConflitoException(CodigoErro.CONTA_DO_SISTEMA,
                    "Contas do sistema não podem ser usadas em agendamentos");
        }

        Agendamento agendamento = agendamentoRepository.save(
                new Agendamento(origem.getId(), destino.getId(), valor, request.executarEm(), agora));

        AgendamentoResponse resposta = new AgendamentoResponse(
                agendamento.getId(),
                origem.getNumero(),
                destino.getNumero(),
                FormatacaoMonetaria.paraReais(valor),
                DataHora.noFusoBrasil(agendamento.getExecutarEm()),
                agendamento.getEstado().name());

        idempotenciaService.registrarResposta(chave, IdempotenciaService.AGENDAMENTO, resposta, HttpStatus.CREATED.value());
        return Resultado.criado(resposta);
    }
}
