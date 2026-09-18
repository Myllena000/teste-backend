package br.com.desafio.contas.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DadosService {

    private final JdbcTemplate jdbcTemplate;

    public DadosService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public void restaurarCargaInicial() {
        jdbcTemplate.execute("select resetar_dados()");
    }
}
