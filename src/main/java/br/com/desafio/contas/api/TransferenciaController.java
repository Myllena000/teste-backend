package br.com.desafio.contas.api;

import br.com.desafio.contas.api.dto.ComprovanteEstornoResponse;
import br.com.desafio.contas.api.dto.ComprovanteTransferenciaResponse;
import br.com.desafio.contas.api.dto.EstornoRequest;
import br.com.desafio.contas.api.dto.TransferenciaRequest;
import br.com.desafio.contas.api.erro.ErroResponse;
import br.com.desafio.contas.service.EstornoService;
import br.com.desafio.contas.service.Resultado;
import br.com.desafio.contas.service.TransferenciaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/transferencias")
@Tag(name = "Transferências", description = "Transferências entre contas e estornos")
public class TransferenciaController {

    private final TransferenciaService transferenciaService;
    private final EstornoService estornoService;

    public TransferenciaController(TransferenciaService transferenciaService, EstornoService estornoService) {
        this.transferenciaService = transferenciaService;
        this.estornoService = estornoService;
    }

    @PostMapping
    @Operation(summary = "Transfere valor entre duas contas", description = "Acima de R$ 100,00 é cobrada taxa de 1% (mínimo R$ 1,00, máximo R$ 20,00).")
    @ApiResponse(responseCode = "201", description = "Transferência confirmada")
    @ApiResponse(responseCode = "200", description = "Idempotency-Key já usada com o mesmo corpo. Retorna a resposta original")
    @ApiResponse(responseCode = "400", description = "CORPO_INVALIDO, IDEMPOTENCY_KEY_AUSENTE, IDEMPOTENCY_KEY_INVALIDA",
            content = @Content(schema = @Schema(implementation = ErroResponse.class)))
    @ApiResponse(responseCode = "404", description = "CONTA_NAO_ENCONTRADA",
            content = @Content(schema = @Schema(implementation = ErroResponse.class)))
    @ApiResponse(responseCode = "409", description = "CONTA_ORIGEM_BLOQUEADA, CONTA_ORIGEM_ENCERRADA, CONTA_DESTINO_ENCERRADA, CONTAS_IGUAIS, CONTA_DO_SISTEMA, IDEMPOTENCY_KEY_REUTILIZADA, CONFLITO_DE_CONCORRENCIA",
            content = @Content(schema = @Schema(implementation = ErroResponse.class)))
    @ApiResponse(responseCode = "422", description = "SALDO_INSUFICIENTE, LIMITE_DIARIO_EXCEDIDO, VALOR_INVALIDO",
            content = @Content(schema = @Schema(implementation = ErroResponse.class)))
    public ResponseEntity<ComprovanteTransferenciaResponse> transferir(
            @Parameter(description = "Chave única da operação", required = true)
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody TransferenciaRequest request) {
        Resultado<ComprovanteTransferenciaResponse> resultado = transferenciaService.transferir(idempotencyKey, request);
        return ResponseEntity.status(resultado.repetido() ? HttpStatus.OK : HttpStatus.CREATED).body(resultado.corpo());
    }

    @PostMapping("/{id}/estorno")
    @Operation(summary = "Estorna uma transferência confirmada", description = "Cria uma nova transferência no sentido contrário e devolve também a taxa. A original passa para ESTORNADA.")
    @ApiResponse(responseCode = "201", description = "Estorno criado")
    @ApiResponse(responseCode = "200", description = "Idempotency-Key já usada com o mesmo corpo. Retorna a resposta original")
    @ApiResponse(responseCode = "400", description = "CORPO_INVALIDO, IDEMPOTENCY_KEY_AUSENTE, IDEMPOTENCY_KEY_INVALIDA",
            content = @Content(schema = @Schema(implementation = ErroResponse.class)))
    @ApiResponse(responseCode = "404", description = "TRANSFERENCIA_NAO_ENCONTRADA",
            content = @Content(schema = @Schema(implementation = ErroResponse.class)))
    @ApiResponse(responseCode = "409", description = "TRANSICAO_DE_ESTADO_INVALIDA (não está CONFIRMADA ou já foi estornada), ESTORNO_NAO_PERMITIDO, CONTA_ENCERRADA, IDEMPOTENCY_KEY_REUTILIZADA",
            content = @Content(schema = @Schema(implementation = ErroResponse.class)))
    @ApiResponse(responseCode = "422", description = "SALDO_INSUFICIENTE (a conta que recebeu não tem saldo para devolver)",
            content = @Content(schema = @Schema(implementation = ErroResponse.class)))
    public ResponseEntity<ComprovanteEstornoResponse> estornar(
            @PathVariable Long id,
            @Parameter(description = "Chave única da operação", required = true)
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody EstornoRequest request) {
        Resultado<ComprovanteEstornoResponse> resultado = estornoService.estornar(idempotencyKey, id, request);
        return ResponseEntity.status(resultado.repetido() ? HttpStatus.OK : HttpStatus.CREATED).body(resultado.corpo());
    }
}
