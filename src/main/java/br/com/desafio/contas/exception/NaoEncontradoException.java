package br.com.desafio.contas.exception;

import org.springframework.http.HttpStatus;

public class NaoEncontradoException extends NegocioException {

    public NaoEncontradoException(CodigoErro codigo, String mensagem) {
        super(HttpStatus.NOT_FOUND, codigo, mensagem);
    }
}
