package br.com.desafio.contas.exception;

import org.springframework.http.HttpStatus;

public class RegraDeNegocioException extends NegocioException {

    public RegraDeNegocioException(CodigoErro codigo, String mensagem) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, codigo, mensagem);
    }
}
