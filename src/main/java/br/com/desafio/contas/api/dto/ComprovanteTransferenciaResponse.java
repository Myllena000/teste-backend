package br.com.desafio.contas.api.dto;

import br.com.desafio.contas.shared.DataHora;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record ComprovanteTransferenciaResponse(
        Long transferenciaId,
        String contaOrigem,
        String contaDestino,
        BigDecimal valor,
        BigDecimal taxa,
        BigDecimal totalDebitado,
        BigDecimal saldoOrigemApos,
        @JsonFormat(pattern = DataHora.FORMATO_JSON)
        OffsetDateTime concluidaEm) {
}
