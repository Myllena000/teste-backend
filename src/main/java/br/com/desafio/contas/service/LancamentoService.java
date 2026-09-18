package br.com.desafio.contas.service;

import br.com.desafio.contas.entity.Conta;
import br.com.desafio.contas.entity.Movimento;
import br.com.desafio.contas.entity.NaturezaMovimento;
import br.com.desafio.contas.entity.TipoMovimento;
import br.com.desafio.contas.entity.Transferencia;
import br.com.desafio.contas.repository.MovimentoRepository;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;

@Component
public class LancamentoService {

    private final MovimentoRepository movimentoRepository;

    public LancamentoService(MovimentoRepository movimentoRepository) {
        this.movimentoRepository = movimentoRepository;
    }

    public void debitar(Conta conta, Transferencia transferencia, NaturezaMovimento natureza, long valorCentavos,
                        OffsetDateTime momento) {
        conta.debitar(valorCentavos);
        registrar(conta, transferencia, TipoMovimento.SAIDA, natureza, valorCentavos, momento);
    }

    public void creditar(Conta conta, Transferencia transferencia, NaturezaMovimento natureza, long valorCentavos,
                         OffsetDateTime momento) {
        conta.creditar(valorCentavos);
        registrar(conta, transferencia, TipoMovimento.ENTRADA, natureza, valorCentavos, momento);
    }

    private void registrar(Conta conta, Transferencia transferencia, TipoMovimento tipo, NaturezaMovimento natureza,
                           long valorCentavos, OffsetDateTime momento) {
        long sequencia = movimentoRepository.buscarUltimaSequencia(conta.getId()) + 1;
        movimentoRepository.save(new Movimento(conta.getId(), transferencia.getId(), sequencia, tipo, natureza,
                valorCentavos, conta.getSaldoCentavos(), momento));
    }
}
