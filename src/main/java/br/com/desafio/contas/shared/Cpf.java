package br.com.desafio.contas.shared;

public final class Cpf {

    private Cpf() {
    }

    public static String mascarar(String cpf) {
        if (cpf == null || !cpf.matches("\\d{11}")) {
            return "***.***.***-**";
        }
        return "***." + cpf.substring(3, 6) + "." + cpf.substring(6, 9) + "-**";
    }
}
