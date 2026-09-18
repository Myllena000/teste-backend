package br.com.desafio.contas.service;

import br.com.desafio.contas.exception.CodigoErro;
import br.com.desafio.contas.exception.NegocioException;
import br.com.desafio.contas.metrics.MetricaConflitos;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Clock;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IdempotenciaServiceTest {

    private static final String ENDPOINT = "POST /transferencias";

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private MetricaConflitos metricaConflitos;

    private IdempotenciaService service;

    @BeforeEach
    void configurar() {
        service = new IdempotenciaService(jdbcTemplate, new ObjectMapper(), metricaConflitos, Clock.systemUTC());
    }

    @Test
    void chaveNovaDevePermitirExecucaoSemRegistrarConflito() {
        chaveLivre(true);
        when(jdbcTemplate.update(anyString(), eq("chave-1"), eq(ENDPOINT), eq("hash"), any())).thenReturn(1);

        Optional<Map> resposta = service.reservar("chave-1", ENDPOINT, "hash", Map.class);

        assertThat(resposta).isEmpty();
        verifyNoInteractions(metricaConflitos);
    }

    @Test
    void chaveEmUsoPorOutraTransacaoRegistraConflito() {
        chaveLivre(false);
        when(jdbcTemplate.update(anyString(), eq("chave-1"), eq(ENDPOINT), eq("hash"), any())).thenReturn(1);

        service.reservar("chave-1", ENDPOINT, "hash", Map.class);

        verify(metricaConflitos).registrar(MetricaConflitos.RECURSO_IDEMPOTENCY_KEY);
    }

    @Test
    void chaveRepetidaComMesmoCorpoDeveDevolverRespostaGravada() {
        chaveLivre(true);
        when(jdbcTemplate.update(anyString(), eq("chave-1"), eq(ENDPOINT), eq("hash"), any())).thenReturn(0);
        when(jdbcTemplate.queryForMap(anyString(), eq("chave-1"), eq(ENDPOINT)))
                .thenReturn(Map.of("hash_requisicao", "hash", "resposta_json", "{\"transferenciaId\":42}"));

        Optional<Map> resposta = service.reservar("chave-1", ENDPOINT, "hash", Map.class);

        assertThat(resposta).isPresent();
        assertThat(resposta.get()).containsEntry("transferenciaId", 42);
    }

    @Test
    void chaveRepetidaComCorpoDiferenteDeveGerarConflito() {
        chaveLivre(true);
        when(jdbcTemplate.update(anyString(), eq("chave-1"), eq(ENDPOINT), eq("outro-hash"), any())).thenReturn(0);
        when(jdbcTemplate.queryForMap(anyString(), eq("chave-1"), eq(ENDPOINT)))
                .thenReturn(Map.of("hash_requisicao", "hash", "resposta_json", "{}"));

        assertThatThrownBy(() -> service.reservar("chave-1", ENDPOINT, "outro-hash", Map.class))
                .isInstanceOf(NegocioException.class)
                .extracting("codigo")
                .isEqualTo(CodigoErro.IDEMPOTENCY_KEY_REUTILIZADA);
    }

    @Test
    void chaveEmBrancoDeveSerRecusada() {
        assertThatThrownBy(() -> service.reservar("  ", ENDPOINT, "hash", Map.class))
                .extracting("codigo")
                .isEqualTo(CodigoErro.IDEMPOTENCY_KEY_INVALIDA);
        verifyNoInteractions(jdbcTemplate);
    }

    private void chaveLivre(boolean livre) {
        when(jdbcTemplate.queryForObject(anyString(), eq(Boolean.class), eq(ENDPOINT + "|chave-1")))
                .thenReturn(livre);
    }
}
