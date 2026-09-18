package br.com.desafio.contas.service;

import br.com.desafio.contas.entity.Conta;
import br.com.desafio.contas.exception.CodigoErro;
import br.com.desafio.contas.exception.NaoEncontradoException;
import br.com.desafio.contas.metrics.MetricaConflitos;
import br.com.desafio.contas.repository.ContaRepository;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class ContaService {

    private final ContaRepository contaRepository;
    private final MetricaConflitos metricaConflitos;

    public ContaService(ContaRepository contaRepository, MetricaConflitos metricaConflitos) {
        this.contaRepository = contaRepository;
        this.metricaConflitos = metricaConflitos;
    }

    public Long buscarIdPorNumero(String numero) {
        return contaRepository.buscarIdPorNumero(numero)
                .orElseThrow(() -> contaNaoEncontrada(numero));
    }

    public Conta buscarPorNumero(String numero) {
        return contaRepository.findByNumero(numero)
                .orElseThrow(() -> contaNaoEncontrada(numero));
    }

    public Map<Long, Conta> bloquearEmOrdemDeId(Collection<Long> ids) {
        List<Long> idsOrdenados = ids.stream().distinct().sorted().toList();
        Map<Long, Conta> contas = new HashMap<>();
        for (Long id : idsOrdenados) {
            Conta conta = contaRepository.buscarPorIdSeNaoEstiverBloqueada(id)
                    .orElseGet(() -> aguardarBloqueio(id));
            contas.put(id, conta);
        }
        return contas;
    }

    private Conta aguardarBloqueio(Long id) {
        metricaConflitos.registrar(MetricaConflitos.RECURSO_CONTA);
        return contaRepository.buscarPorIdComBloqueio(id)
                .orElseThrow(() -> new NaoEncontradoException(CodigoErro.CONTA_NAO_ENCONTRADA,
                        "Conta com id " + id + " não encontrada"));
    }

    private NaoEncontradoException contaNaoEncontrada(String numero) {
        return new NaoEncontradoException(CodigoErro.CONTA_NAO_ENCONTRADA, "Conta " + numero + " não encontrada");
    }
}
