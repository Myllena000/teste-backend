package br.com.desafio.contas.api.dto;

import java.math.BigDecimal;

public record ResumoResponse(
        long contasAtivasAgora,
        BigDecimal saldoTotalUsuariosAgora,
        long agendamentosPendentesAgora,
        long transferenciasNaJanela,
        BigDecimal valorTransferidoNaJanela,
        BigDecimal taxasNaJanela,
        BigDecimal ticketMedioNaJanela,
        long estornosNaJanela,
        boolean somaDosMovimentosEhZero) {
}
