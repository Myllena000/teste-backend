package br.com.desafio.contas.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record EstornoRequest(
        @Schema(example = "Pedido do usuário")
        @NotBlank(message = "motivo é obrigatório")
        @Size(max = 255, message = "motivo deve ter no máximo 255 caracteres")
        String motivo) {
}
