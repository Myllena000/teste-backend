package br.com.desafio.contas.exception;

import org.springframework.http.HttpStatus;

public class ConflitoException extends NegocioException {

    public ConflitoException(CodigoErro codigo, String mensagem) {
        super(HttpStatus.CONFLICT, codigo, mensagem);
    }
}
