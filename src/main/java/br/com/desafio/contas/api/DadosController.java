package br.com.desafio.contas.api;

import br.com.desafio.contas.service.DadosService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Dados", description = "Uso apenas em desenvolvimento e teste")
public class DadosController {

    private final DadosService dadosService;

    public DadosController(DadosService dadosService) {
        this.dadosService = dadosService;
    }

    @DeleteMapping("/dados")
    @Operation(summary = "Restaura a carga inicial", description = "Apaga movimentos, transferências, agendamentos e chaves de idempotência e recria as contas iniciais.")
    @ApiResponse(responseCode = "200", description = "Dados restaurados")
    public ResponseEntity<Void> restaurar() {
        dadosService.restaurarCargaInicial();
        return ResponseEntity.ok().build();
    }
}
