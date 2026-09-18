package br.com.desafio.contas.service;

import br.com.desafio.contas.exception.CodigoErro;
import br.com.desafio.contas.exception.ConflitoException;
import br.com.desafio.contas.exception.RequisicaoInvalidaException;
import br.com.desafio.contas.metrics.MetricaConflitos;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;

@Component
public class IdempotenciaService {

    public static final String DEPOSITO = "POST /contas/{numero}/depositos";
    public static final String TRANSFERENCIA = "POST /transferencias";
    public static final String ESTORNO = "POST /transferencias/{id}/estorno";
    public static final String AGENDAMENTO = "POST /transferencias-agendadas";

    private static final int TAMANHO_MAXIMO_CHAVE = 100;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final MetricaConflitos metricaConflitos;
    private final Clock clock;

    public IdempotenciaService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper,
                               MetricaConflitos metricaConflitos, Clock clock) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.metricaConflitos = metricaConflitos;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public <T> Optional<T> reservar(String chave, String endpoint, String hashRequisicao, Class<T> tipoResposta) {
        validarChave(chave);

        Boolean chaveLivre = jdbcTemplate.queryForObject(
                "select pg_try_advisory_xact_lock(hashtext(?))", Boolean.class, endpoint + "|" + chave);
        if (!Boolean.TRUE.equals(chaveLivre)) {
            metricaConflitos.registrar(MetricaConflitos.RECURSO_IDEMPOTENCY_KEY);
        }

        int inseridas = jdbcTemplate.update("""
                insert into idempotencia (chave, endpoint, hash_requisicao, criado_em)
                values (?, ?, ?, ?)
                on conflict (chave, endpoint) do nothing
                """, chave, endpoint, hashRequisicao, OffsetDateTime.now(clock));

        if (inseridas == 1) {
            return Optional.empty();
        }

        Map<String, Object> registro = jdbcTemplate.queryForMap("""
                select hash_requisicao, resposta_json
                  from idempotencia
                 where chave = ? and endpoint = ?
                """, chave, endpoint);

        if (!hashRequisicao.equals(registro.get("hash_requisicao"))) {
            throw new ConflitoException(CodigoErro.IDEMPOTENCY_KEY_REUTILIZADA,
                    "A Idempotency-Key informada já foi usada com um corpo de requisição diferente");
        }

        return Optional.of(lerResposta((String) registro.get("resposta_json"), tipoResposta));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void registrarResposta(String chave, String endpoint, Object resposta, int statusHttp) {
        jdbcTemplate.update("""
                update idempotencia
                   set resposta_json = ?, status_http = ?
                 where chave = ? and endpoint = ?
                """, escreverResposta(resposta), statusHttp, chave, endpoint);
    }

    private void validarChave(String chave) {
        if (chave == null || chave.isBlank()) {
            throw new RequisicaoInvalidaException(CodigoErro.IDEMPOTENCY_KEY_INVALIDA,
                    "O header Idempotency-Key não pode ser vazio");
        }
        if (chave.length() > TAMANHO_MAXIMO_CHAVE) {
            throw new RequisicaoInvalidaException(CodigoErro.IDEMPOTENCY_KEY_INVALIDA,
                    "O header Idempotency-Key deve ter no máximo " + TAMANHO_MAXIMO_CHAVE + " caracteres");
        }
    }

    private <T> T lerResposta(String json, Class<T> tipo) {
        try {
            return objectMapper.readValue(json, tipo);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Resposta de idempotência gravada em formato inválido", e);
        }
    }

    private String escreverResposta(Object resposta) {
        try {
            return objectMapper.writeValueAsString(resposta);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Não foi possível gravar a resposta de idempotência", e);
        }
    }
}
