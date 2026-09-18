package br.com.desafio.contas.api.dto;

import br.com.desafio.contas.shared.DataHora;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record ComprovanteDepositoResponse(
        Long transferenciaId,
        String conta,
        BigDecimal valor,
        BigDecimal saldoApos,
        @JsonFormat(pattern = DataHora.FORMATO_JSON)
        OffsetDateTime concluidaEm) {
}
