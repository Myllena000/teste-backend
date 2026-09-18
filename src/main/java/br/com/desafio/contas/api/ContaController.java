package br.com.desafio.contas.api;

import br.com.desafio.contas.api.dto.ComprovanteDepositoResponse;
import br.com.desafio.contas.api.dto.DepositoRequest;
import br.com.desafio.contas.api.dto.ExtratoResponse;
import br.com.desafio.contas.api.erro.ErroResponse;
import br.com.desafio.contas.service.DepositoService;
import br.com.desafio.contas.service.ExtratoService;
import br.com.desafio.contas.service.Resultado;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/contas/{numero}")
@Tag(name = "Contas", description = "Depósitos e extrato")
public class ContaController {

    private final DepositoService depositoService;
    private final ExtratoService extratoService;

    public ContaController(DepositoService depositoService, ExtratoService extratoService) {
        this.depositoService = depositoService;
        this.extratoService = extratoService;
    }

    @PostMapping("/depositos")
    @Operation(summary = "Realiza um depósito", description = "O dinheiro sai da conta SISTEMA-ENTRADA e entra na conta informada. Conta BLOQUEADA aceita depósito.")
    @ApiResponse(responseCode = "201", description = "Depósito efetivado")
    @ApiResponse(responseCode = "200", description = "Idempotency-Key já usada com o mesmo corpo. Retorna a resposta original")
    @ApiResponse(responseCode = "400", description = "CORPO_INVALIDO, IDEMPOTENCY_KEY_AUSENTE, IDEMPOTENCY_KEY_INVALIDA",
            content = @Content(schema = @Schema(implementation = ErroResponse.class)))
    @ApiResponse(responseCode = "404", description = "CONTA_NAO_ENCONTRADA",
            content = @Content(schema = @Schema(implementation = ErroResponse.class)))
    @ApiResponse(responseCode = "409", description = "CONTA_ENCERRADA, CONTA_DO_SISTEMA, IDEMPOTENCY_KEY_REUTILIZADA",
            content = @Content(schema = @Schema(implementation = ErroResponse.class)))
    @ApiResponse(responseCode = "422", description = "VALOR_INVALIDO, VALOR_ACIMA_DO_PERMITIDO",
            content = @Content(schema = @Schema(implementation = ErroResponse.class)))
    public ResponseEntity<ComprovanteDepositoResponse> depositar(
            @PathVariable String numero,
            @Parameter(description = "Chave única da operação", required = true)
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody DepositoRequest request) {
        Resultado<ComprovanteDepositoResponse> resultado = depositoService.depositar(idempotencyKey, numero, request);
        return ResponseEntity.status(resultado.repetido() ? HttpStatus.OK : HttpStatus.CREATED).body(resultado.corpo());
    }

    @GetMapping("/extrato")
    @Operation(summary = "Consulta o extrato", description = "Movimentos em ordem decrescente de sequência. As datas usam o fuso America/Sao_Paulo.")
    @ApiResponse(responseCode = "200", description = "Extrato da conta")
    @ApiResponse(responseCode = "400", description = "PARAMETRO_INVALIDO",
            content = @Content(schema = @Schema(implementation = ErroResponse.class)))
    @ApiResponse(responseCode = "404", description = "CONTA_NAO_ENCONTRADA",
            content = @Content(schema = @Schema(implementation = ErroResponse.class)))
    public ExtratoResponse extrato(
            @PathVariable String numero,
            @Parameter(example = "2026-09-01") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate de,
            @Parameter(example = "2026-09-30") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate ate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return extratoService.consultar(numero, de, ate, page, size);
    }
}
