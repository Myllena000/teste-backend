package br.com.desafio.contas.service;

public record Resultado<T>(T corpo, boolean repetido) {

    public static <T> Resultado<T> criado(T corpo) {
        return new Resultado<>(corpo, false);
    }

    public static <T> Resultado<T> repetido(T corpo) {
        return new Resultado<>(corpo, true);
    }
}
