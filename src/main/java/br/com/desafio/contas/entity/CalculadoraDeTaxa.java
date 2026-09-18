package br.com.desafio.contas.entity;

import org.springframework.stereotype.Component;

@Component
public class CalculadoraDeTaxa {

    private static final long VALOR_ISENTO_ATE = 10_000L;
    private static final long TAXA_MINIMA = 100L;
    private static final long TAXA_MAXIMA = 2_000L;

    public long calcular(long valorCentavos) {
        if (valorCentavos <= VALOR_ISENTO_ATE) {
            return 0L;
        }
        long umPorCento = (valorCentavos + 50L) / 100L;
        return Math.min(TAXA_MAXIMA, Math.max(TAXA_MINIMA, umPorCento));
    }
}
