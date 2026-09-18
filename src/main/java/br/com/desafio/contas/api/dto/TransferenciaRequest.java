package br.com.desafio.contas.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record TransferenciaRequest(
        @Schema(example = "CONTA-001")
        @NotBlank(message = "contaOrigem é obrigatória")
        String contaOrigem,

        @Schema(example = "CONTA-002")
        @NotBlank(message = "contaDestino é obrigatória")
        String contaDestino,

        @Schema(description = "Valor em reais", example = "150.00")
        @NotNull(message = "valor é obrigatório")
        BigDecimal valor) {
}
