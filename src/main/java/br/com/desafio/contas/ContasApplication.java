package br.com.desafio.contas;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ContasApplication {

    public static void main(String[] args) {
        SpringApplication.run(ContasApplication.class, args);
    }
}
