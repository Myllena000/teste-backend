package br.com.desafio.contas.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record DepositoRequest(
        @Schema(description = "Valor em reais", example = "250.00")
        @NotNull(message = "valor é obrigatório")
        BigDecimal valor) {
}
