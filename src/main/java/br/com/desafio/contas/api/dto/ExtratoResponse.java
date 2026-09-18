package br.com.desafio.contas.api.dto;

import java.math.BigDecimal;
import java.util.List;

public record ExtratoResponse(
        String conta,
        String estado,
        BigDecimal saldoAtual,
        TitularResponse titular,
        int pagina,
        int tamanho,
        long totalElementos,
        int totalPaginas,
        List<MovimentoResponse> movimentos) {
}
