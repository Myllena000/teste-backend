package br.com.desafio.contas.exception;

import org.springframework.http.HttpStatus;

public abstract class NegocioException extends RuntimeException {

    private final HttpStatus status;
    private final CodigoErro codigo;

    protected NegocioException(HttpStatus status, CodigoErro codigo, String mensagem) {
        super(mensagem);
        this.status = status;
        this.codigo = codigo;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public CodigoErro getCodigo() {
        return codigo;
    }
}
