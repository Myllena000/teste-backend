package br.com.desafio.contas.api.dto;

import br.com.desafio.contas.shared.DataHora;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record ComprovanteEstornoResponse(
        Long transferenciaId,
        Long transferenciaOriginalId,
        String contaOrigem,
        String contaDestino,
        BigDecimal valor,
        BigDecimal taxaDevolvida,
        BigDecimal totalCreditado,
        String motivo,
        @JsonFormat(pattern = DataHora.FORMATO_JSON)
        OffsetDateTime concluidaEm) {
}
