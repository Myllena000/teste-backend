package br.com.desafio.contas.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI().info(new Info()
                .title("API de Contas e Transferências")
                .version("1.0.0")
                .description("Depósitos, transferências, estornos, agendamentos, extrato e resumo. "
                        + "Valores em reais com duas casas decimais. "
                        + "Todos os erros retornam o campo codigo, que deve ser usado por quem consome a API."));
    }
}
