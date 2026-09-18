package br.com.desafio.contas.service;

import br.com.desafio.contas.api.dto.ComprovanteTransferenciaResponse;
import br.com.desafio.contas.api.dto.TransferenciaRequest;
import br.com.desafio.contas.shared.FormatacaoMonetaria;
import br.com.desafio.contas.shared.Hash;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class TransferenciaService {

    private final IdempotenciaService idempotenciaService;
    private final ExecutorDeTransferencia executorDeTransferencia;

    public TransferenciaService(IdempotenciaService idempotenciaService, ExecutorDeTransferencia executorDeTransferencia) {
        this.idempotenciaService = idempotenciaService;
        this.executorDeTransferencia = executorDeTransferencia;
    }

    @Transactional
    public Resultado<ComprovanteTransferenciaResponse> transferir(String chave, TransferenciaRequest request) {
        long valor = FormatacaoMonetaria.paraCentavos(request.valor());
        String hash = Hash.sha256(request.contaOrigem() + "|" + request.contaDestino() + "|" + valor);

        Optional<ComprovanteTransferenciaResponse> respostaAnterior = idempotenciaService.reservar(
                chave, IdempotenciaService.TRANSFERENCIA, hash, ComprovanteTransferenciaResponse.class);
        if (respostaAnterior.isPresent()) {
            return Resultado.repetido(respostaAnterior.get());
        }

        ComprovanteTransferenciaResponse comprovante = executorDeTransferencia.executar(
                request.contaOrigem(), request.contaDestino(), valor);

        idempotenciaService.registrarResposta(chave, IdempotenciaService.TRANSFERENCIA, comprovante,
                HttpStatus.CREATED.value());
        return Resultado.criado(comprovante);
    }
}
