package br.com.desafio.contas.shared;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;

public final class DataHora {

    public static final ZoneId TIMEZONE_SAO_PAULO = ZoneId.of("America/Sao_Paulo");
    public static final String FORMATO_JSON = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX";

    private DataHora() {
    }

    public static OffsetDateTime inicioDoDia(LocalDate data) {
        return data.atStartOfDay(TIMEZONE_SAO_PAULO).toOffsetDateTime();
    }

    public static OffsetDateTime inicioDoDia(OffsetDateTime momento) {
        return inicioDoDia(momento.atZoneSameInstant(TIMEZONE_SAO_PAULO).toLocalDate());
    }

    public static OffsetDateTime inicioDoDiaSeguinte(OffsetDateTime momento) {
        return inicioDoDia(momento.atZoneSameInstant(TIMEZONE_SAO_PAULO).toLocalDate().plusDays(1));
    }

    public static OffsetDateTime noFusoBrasil(OffsetDateTime momento) {
        if (momento == null) {
            return null;
        }
        return momento.atZoneSameInstant(TIMEZONE_SAO_PAULO).toOffsetDateTime();
    }
}
