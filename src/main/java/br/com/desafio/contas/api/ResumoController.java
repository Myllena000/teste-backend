package br.com.desafio.contas.api;

import br.com.desafio.contas.api.dto.ResumoResponse;
import br.com.desafio.contas.api.erro.ErroResponse;
import br.com.desafio.contas.service.ResumoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Resumo", description = "Indicadores gerais do sistema")
public class ResumoController {

    private final ResumoService resumoService;

    public ResumoController(ResumoService resumoService) {
        this.resumoService = resumoService;
    }

    @GetMapping("/resumo")
    @Operation(summary = "Resumo do sistema", description = "Campos terminados em Agora refletem o estado atual. Campos terminados em NaJanela consideram transferências criadas nos últimos janelaMinutos.")
    @ApiResponse(responseCode = "200", description = "Resumo calculado")
    @ApiResponse(responseCode = "400", description = "PARAMETRO_INVALIDO",
            content = @Content(schema = @Schema(implementation = ErroResponse.class)))
    public ResumoResponse resumo(@RequestParam(defaultValue = "60") int janelaMinutos) {
        return resumoService.gerar(janelaMinutos);
    }
}
