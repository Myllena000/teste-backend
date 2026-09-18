package br.com.desafio.contas.entity;

public enum EstadoConta {
    ATIVA,
    BLOQUEADA,
    ENCERRADA;

    public boolean podeEnviar() {
        return this == ATIVA;
    }

    public boolean podeReceber() {
        return this != ENCERRADA;
    }
}
