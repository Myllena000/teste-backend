package br.com.desafio.contas.service;

import br.com.desafio.contas.api.dto.ComprovanteTransferenciaResponse;
import br.com.desafio.contas.entity.Agendamento;
import br.com.desafio.contas.exception.NegocioException;
import br.com.desafio.contas.repository.AgendamentoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Optional;

@Service
public class ExecucaoDeAgendamento {

    private static final Logger log = LoggerFactory.getLogger(ExecucaoDeAgendamento.class);

    private final AgendamentoRepository agendamentoRepository;
    private final ExecutorDeTransferencia executorDeTransferencia;
    private final Clock clock;
    private final int maximoTentativas;

    public ExecucaoDeAgendamento(AgendamentoRepository agendamentoRepository,
                                 ExecutorDeTransferencia executorDeTransferencia, Clock clock,
                                 @Value("${app.agendamento.job.max-tentativas}") int maximoTentativas) {
        this.agendamentoRepository = agendamentoRepository;
        this.executorDeTransferencia = executorDeTransferencia;
        this.clock = clock;
        this.maximoTentativas = maximoTentativas;
    }

    @Transactional
    public boolean executar(Long agendamentoId) {
        Optional<Agendamento> pendente = agendamentoRepository.buscarPendenteComBloqueio(agendamentoId);
        if (pendente.isEmpty()) {
            return false;
        }

        Agendamento agendamento = pendente.get();
        OffsetDateTime agora = OffsetDateTime.now(clock);
        try {
            ComprovanteTransferenciaResponse comprovante = executorDeTransferencia.executar(
                    agendamento.getContaOrigemId(), agendamento.getContaDestinoId(), agendamento.getValorCentavos());
            agendamento.marcarComoExecutado(comprovante.transferenciaId(), agora);
            log.info("Agendamento {} executado, transferência {}", agendamentoId, comprovante.transferenciaId());
        } catch (NegocioException e) {
            agendamento.marcarComoFalhado(e.getCodigo() + ": " + e.getMessage(), agora);
            log.info("Agendamento {} falhou: {}", agendamentoId, e.getCodigo());
        }
        return true;
    }

    @Transactional
    public void registrarErroTecnico(Long agendamentoId, String motivo) {
        agendamentoRepository.buscarPendenteComBloqueio(agendamentoId)
                .ifPresent(agendamento -> agendamento.registrarErroTecnico(motivo, maximoTentativas, OffsetDateTime.now(clock)));
    }
}
