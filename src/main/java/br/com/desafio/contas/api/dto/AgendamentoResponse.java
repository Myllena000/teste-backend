package br.com.desafio.contas.api.dto;

import br.com.desafio.contas.shared.DataHora;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record AgendamentoResponse(
        Long agendamentoId,
        String contaOrigem,
        String contaDestino,
        BigDecimal valor,
        @JsonFormat(pattern = DataHora.FORMATO_JSON)
        OffsetDateTime executarEm,
        String estado) {
}
