package br.com.desafio.contas.exception;

public class TransicaoInvalidaException extends ConflitoException {

    public TransicaoInvalidaException(String mensagem) {
        super(CodigoErro.TRANSICAO_DE_ESTADO_INVALIDA, mensagem);
    }
}
