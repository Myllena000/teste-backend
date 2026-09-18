package br.com.desafio.contas.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.HandlerMapping;

@Component
public class MetricaConflitos {

    public static final String NOME = "contas.conflitos.concorrencia";

    public static final String RECURSO_CONTA = "conta";
    public static final String RECURSO_TRANSFERENCIA = "transferencia";
    public static final String RECURSO_IDEMPOTENCY_KEY = "idempotency-key";
    public static final String RECURSO_BANCO = "banco";

    private static final String FORA_DE_REQUISICAO = "job-agendamento";

    private final MeterRegistry meterRegistry;

    public MetricaConflitos(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void registrar(String recurso) {
        Counter.builder(NOME)
                .description("Conflitos de concorrência detectados, por endpoint e recurso disputado")
                .tag("endpoint", endpointAtual())
                .tag("recurso", recurso)
                .register(meterRegistry)
                .increment();
    }

    private String endpointAtual() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes atributos) {
            HttpServletRequest request = atributos.getRequest();
            Object rota = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
            return request.getMethod() + " " + (rota == null ? "desconhecido" : rota);
        }
        return FORA_DE_REQUISICAO;
    }
}
