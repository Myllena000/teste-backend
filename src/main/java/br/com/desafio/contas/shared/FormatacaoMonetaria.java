package br.com.desafio.contas.shared;

import br.com.desafio.contas.exception.CodigoErro;
import br.com.desafio.contas.exception.RegraDeNegocioException;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

public final class FormatacaoMonetaria {

    private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");

    private FormatacaoMonetaria() {
    }

    public static long paraCentavos(BigDecimal valor) {
        if (valor.stripTrailingZeros().scale() > 2) {
            throw new RegraDeNegocioException(CodigoErro.VALOR_INVALIDO, "O valor deve ter no máximo duas casas decimais");
        }
        try {
            return valor.movePointRight(2).longValueExact();
        } catch (ArithmeticException e) {
            throw new RegraDeNegocioException(CodigoErro.VALOR_INVALIDO, "Valor fora do intervalo aceito");
        }
    }

    public static BigDecimal paraReais(long centavos) {
        return BigDecimal.valueOf(centavos, 2);
    }

    public static String formatar(long centavos) {
        DecimalFormat formato = new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(PT_BR));
        return "R$ " + formato.format(paraReais(centavos));
    }
}
