package br.com.desafio.contas.api.erro;

import br.com.desafio.contas.shared.DataHora;
import br.com.desafio.contas.exception.CodigoErro;
import br.com.desafio.contas.exception.NegocioException;
import br.com.desafio.contas.metrics.MetricaConflitos;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.stream.Collectors;

@RestControllerAdvice
public class TratadorDeErros {

    private static final Logger log = LoggerFactory.getLogger(TratadorDeErros.class);

    private final Clock clock;
    private final MetricaConflitos metricaConflitos;

    public TratadorDeErros(Clock clock, MetricaConflitos metricaConflitos) {
        this.clock = clock;
        this.metricaConflitos = metricaConflitos;
    }

    @ExceptionHandler(NegocioException.class)
    public ResponseEntity<ErroResponse> negocio(NegocioException e, HttpServletRequest request) {
        return responder(e.getStatus(), e.getCodigo(), e.getMessage(), request);
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ErroResponse> headerAusente(MissingRequestHeaderException e, HttpServletRequest request) {
        if ("Idempotency-Key".equalsIgnoreCase(e.getHeaderName())) {
            return responder(HttpStatus.BAD_REQUEST, CodigoErro.IDEMPOTENCY_KEY_AUSENTE,
                    "O header Idempotency-Key é obrigatório", request);
        }
        return responder(HttpStatus.BAD_REQUEST, CodigoErro.PARAMETRO_INVALIDO,
                "O header " + e.getHeaderName() + " é obrigatório", request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErroResponse> corpoComCamposInvalidos(MethodArgumentNotValidException e,
                                                               HttpServletRequest request) {
        String mensagem = e.getBindingResult().getFieldErrors().stream()
                .map(erro -> erro.getDefaultMessage())
                .sorted()
                .collect(Collectors.joining("; "));
        return responder(HttpStatus.BAD_REQUEST, CodigoErro.CORPO_INVALIDO, mensagem, request);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, HttpMediaTypeNotSupportedException.class})
    public ResponseEntity<ErroResponse> corpoIlegivel(Exception e, HttpServletRequest request) {
        return responder(HttpStatus.BAD_REQUEST, CodigoErro.CORPO_INVALIDO,
                "O corpo da requisição está ausente ou não é um JSON válido", request);
    }

    @ExceptionHandler({MethodArgumentTypeMismatchException.class, MissingServletRequestParameterException.class})
    public ResponseEntity<ErroResponse> parametroInvalido(Exception e, HttpServletRequest request) {
        return responder(HttpStatus.BAD_REQUEST, CodigoErro.PARAMETRO_INVALIDO,
                "Parâmetro da requisição inválido", request);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErroResponse> recursoInexistente(NoResourceFoundException e, HttpServletRequest request) {
        return responder(HttpStatus.NOT_FOUND, CodigoErro.RECURSO_NAO_ENCONTRADO, "Recurso não encontrado", request);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErroResponse> metodoNaoSuportado(HttpRequestMethodNotSupportedException e,
                                                          HttpServletRequest request) {
        return responder(HttpStatus.METHOD_NOT_ALLOWED, CodigoErro.METODO_NAO_PERMITIDO,
                "Método " + e.getMethod() + " não é suportado neste endereço", request);
    }

    @ExceptionHandler({DataIntegrityViolationException.class, PessimisticLockingFailureException.class,
            OptimisticLockingFailureException.class})
    public ResponseEntity<ErroResponse> conflitoDeConcorrencia(Exception e, HttpServletRequest request) {
        log.warn("Conflito de concorrência em {}: {}", request.getRequestURI(), e.getClass().getSimpleName());
        metricaConflitos.registrar(MetricaConflitos.RECURSO_BANCO);
        return responder(HttpStatus.CONFLICT, CodigoErro.CONFLITO_DE_CONCORRENCIA,
                "A operação conflitou com outra requisição simultânea. Tente novamente", request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErroResponse> erroInesperado(Exception e, HttpServletRequest request) {
        log.error("Erro inesperado em {}", request.getRequestURI(), e);
        return responder(HttpStatus.INTERNAL_SERVER_ERROR, CodigoErro.ERRO_INTERNO,
                "Erro interno ao processar a requisição", request);
    }

    private ResponseEntity<ErroResponse> responder(HttpStatus status, CodigoErro codigo, String mensagem,
                                                   HttpServletRequest request) {
        ErroResponse corpo = new ErroResponse(
                DataHora.noFusoBrasil(OffsetDateTime.now(clock)),
                status.value(),
                status.getReasonPhrase(),
                codigo.name(),
                mensagem,
                request.getRequestURI());
        return ResponseEntity.status(status).body(corpo);
    }
}
