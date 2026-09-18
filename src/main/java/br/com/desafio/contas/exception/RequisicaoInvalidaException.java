package br.com.desafio.contas.exception;

import org.springframework.http.HttpStatus;

public class RequisicaoInvalidaException extends NegocioException {

    public RequisicaoInvalidaException(CodigoErro codigo, String mensagem) {
        super(HttpStatus.BAD_REQUEST, codigo, mensagem);
    }
}
