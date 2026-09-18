package br.com.desafio.contas.job;

import br.com.desafio.contas.service.ProcessadorDeAgendamentos;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.agendamento.job.habilitado", havingValue = "true")
public class ExecucaoAgendamentosJob {

    private final ProcessadorDeAgendamentos processadorDeAgendamentos;

    public ExecucaoAgendamentosJob(ProcessadorDeAgendamentos processadorDeAgendamentos) {
        this.processadorDeAgendamentos = processadorDeAgendamentos;
    }

    @Scheduled(fixedDelayString = "${app.agendamento.job.intervalo-ms}")
    public void executar() {
        processadorDeAgendamentos.processarVencidos();
    }
}
