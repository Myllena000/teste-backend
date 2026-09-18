package br.com.desafio.contas.entity;

import br.com.desafio.contas.shared.FormatacaoMonetaria;
import br.com.desafio.contas.exception.CodigoErro;
import br.com.desafio.contas.exception.RegraDeNegocioException;
import org.springframework.stereotype.Component;

@Component
public class ValidadorLimiteDiario {

    public void validar(long limiteDiarioCentavos, long utilizadoNoDiaCentavos, long valorCentavos) {
        long disponivel = limiteDiarioCentavos - utilizadoNoDiaCentavos;
        if (valorCentavos > disponivel) {
            throw new RegraDeNegocioException(CodigoErro.LIMITE_DIARIO_EXCEDIDO, String.format(
                    "Limite diário de %s excedido. Já utilizado hoje: %s. Valor solicitado: %s",
                    FormatacaoMonetaria.formatar(limiteDiarioCentavos),
                    FormatacaoMonetaria.formatar(utilizadoNoDiaCentavos),
                    FormatacaoMonetaria.formatar(valorCentavos)));
        }
    }
}
