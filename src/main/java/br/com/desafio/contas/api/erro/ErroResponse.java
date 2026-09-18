package br.com.desafio.contas.api.erro;

import br.com.desafio.contas.shared.DataHora;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.OffsetDateTime;

public record ErroResponse(
        @JsonFormat(pattern = DataHora.FORMATO_JSON)
        OffsetDateTime timestamp,
        int status,
        String error,
        String codigo,
        String message,
        String path) {
}
