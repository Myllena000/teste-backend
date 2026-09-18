package br.com.desafio.contas.api.dto;

import br.com.desafio.contas.shared.DataHora;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record MovimentoResponse(
        long sequencia,
        String tipo,
        String natureza,
        BigDecimal valor,
        BigDecimal saldoApos,
        Long transferenciaId,
        @JsonFormat(pattern = DataHora.FORMATO_JSON)
        OffsetDateTime criadoEm) {
}
