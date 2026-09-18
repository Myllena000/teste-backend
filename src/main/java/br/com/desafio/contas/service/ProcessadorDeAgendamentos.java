package br.com.desafio.contas.service;

import br.com.desafio.contas.repository.AgendamentoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;

@Component
public class ProcessadorDeAgendamentos {

    private static final Logger log = LoggerFactory.getLogger(ProcessadorDeAgendamentos.class);

    private final AgendamentoRepository agendamentoRepository;
    private final ExecucaoDeAgendamento execucaoDeAgendamento;
    private final Clock clock;
    private final int tamanhoDoLote;

    public ProcessadorDeAgendamentos(AgendamentoRepository agendamentoRepository,
                                     ExecucaoDeAgendamento execucaoDeAgendamento, Clock clock,
                                     @Value("${app.agendamento.job.lote}") int tamanhoDoLote) {
        this.agendamentoRepository = agendamentoRepository;
        this.execucaoDeAgendamento = execucaoDeAgendamento;
        this.clock = clock;
        this.tamanhoDoLote = tamanhoDoLote;
    }

    public int processarVencidos() {
        List<Long> ids = agendamentoRepository.buscarIdsVencidos(OffsetDateTime.now(clock), tamanhoDoLote);
        int processados = 0;
        for (Long id : ids) {
            try {
                if (execucaoDeAgendamento.executar(id)) {
                    processados++;
                }
            } catch (RuntimeException e) {
                log.error("Erro técnico ao executar o agendamento {}", id, e);
                execucaoDeAgendamento.registrarErroTecnico(id, e.getClass().getSimpleName() + ": " + e.getMessage());
            }
        }
        return processados;
    }
}
