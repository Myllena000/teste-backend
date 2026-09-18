package br.com.desafio.contas.api;

import br.com.desafio.contas.api.dto.AgendamentoRequest;
import br.com.desafio.contas.api.dto.AgendamentoResponse;
import br.com.desafio.contas.api.erro.ErroResponse;
import br.com.desafio.contas.service.AgendamentoService;
import br.com.desafio.contas.service.Resultado;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Agendamentos", description = "Transferências agendadas")
public class AgendamentoController {

    private final AgendamentoService agendamentoService;

    public AgendamentoController(AgendamentoService agendamentoService) {
        this.agendamentoService = agendamentoService;
    }

    @PostMapping("/transferencias-agendadas")
    @Operation(summary = "Agenda uma transferência", description = "Nenhum dinheiro sai no agendamento. Saldo e limite são verificados na hora da execução.")
    @ApiResponse(responseCode = "201", description = "Agendamento criado com estado AGENDADO")
    @ApiResponse(responseCode = "200", description = "Idempotency-Key já usada com o mesmo corpo. Retorna a resposta original")
    @ApiResponse(responseCode = "400", description = "CORPO_INVALIDO, IDEMPOTENCY_KEY_AUSENTE, IDEMPOTENCY_KEY_INVALIDA",
            content = @Content(schema = @Schema(implementation = ErroResponse.class)))
    @ApiResponse(responseCode = "404", description = "CONTA_NAO_ENCONTRADA",
            content = @Content(schema = @Schema(implementation = ErroResponse.class)))
    @ApiResponse(responseCode = "409", description = "CONTAS_IGUAIS, CONTA_DO_SISTEMA, IDEMPOTENCY_KEY_REUTILIZADA",
            content = @Content(schema = @Schema(implementation = ErroResponse.class)))
    @ApiResponse(responseCode = "422", description = "DATA_DE_EXECUCAO_INVALIDA, VALOR_INVALIDO",
            content = @Content(schema = @Schema(implementation = ErroResponse.class)))
    public ResponseEntity<AgendamentoResponse> agendar(
            @Parameter(description = "Chave única da operação", required = true)
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody AgendamentoRequest request) {
        Resultado<AgendamentoResponse> resultado = agendamentoService.agendar(idempotencyKey, request);
        return ResponseEntity.status(resultado.repetido() ? HttpStatus.OK : HttpStatus.CREATED).body(resultado.corpo());
    }
}
