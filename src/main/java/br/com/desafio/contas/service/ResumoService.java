package br.com.desafio.contas.service;

import br.com.desafio.contas.api.dto.ResumoResponse;
import br.com.desafio.contas.shared.FormatacaoMonetaria;
import br.com.desafio.contas.exception.CodigoErro;
import br.com.desafio.contas.exception.RequisicaoInvalidaException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;

@Service
public class ResumoService {

    private static final int JANELA_MAXIMA_MINUTOS = 60 * 24 * 30;

    private final JdbcTemplate jdbcTemplate;
    private final Clock clock;

    public ResumoService(JdbcTemplate jdbcTemplate, Clock clock) {
        this.jdbcTemplate = jdbcTemplate;
        this.clock = clock;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public ResumoResponse gerar(int janelaMinutos) {
        if (janelaMinutos < 1 || janelaMinutos > JANELA_MAXIMA_MINUTOS) {
            throw new RequisicaoInvalidaException(CodigoErro.PARAMETRO_INVALIDO,
                    "janelaMinutos deve estar entre 1 e " + JANELA_MAXIMA_MINUTOS);
        }
        OffsetDateTime inicioJanela = OffsetDateTime.now(clock).minusMinutes(janelaMinutos);

        EstadoAtual atual = jdbcTemplate.queryForObject("""
                select
                    (select count(*) from conta where usuario_id is not null and estado = 'ATIVA') as contas_ativas,
                    (select coalesce(sum(saldo_centavos), 0) from conta where usuario_id is not null) as saldo_usuarios,
                    (select count(*) from agendamento where estado = 'AGENDADO') as agendamentos_pendentes,
                    (select coalesce(sum(case when tipo = 'ENTRADA' then valor_centavos else -valor_centavos end), 0)
                       from movimento) as soma_movimentos
                """, (rs, linha) -> new EstadoAtual(
                rs.getLong("contas_ativas"),
                rs.getLong("saldo_usuarios"),
                rs.getLong("agendamentos_pendentes"),
                rs.getLong("soma_movimentos")));

        Janela janela = jdbcTemplate.queryForObject("""
                select
                    count(*) filter (where tipo = 'TRANSFERENCIA') as transferencias,
                    coalesce(sum(valor_centavos) filter (where tipo = 'TRANSFERENCIA'), 0) as valor_transferido,
                    coalesce(sum(taxa_centavos) filter (where tipo = 'TRANSFERENCIA'), 0) as taxas,
                    coalesce(round(avg(valor_centavos) filter (where tipo = 'TRANSFERENCIA')), 0) as ticket_medio,
                    count(*) filter (where tipo = 'ESTORNO') as estornos
                  from transferencia
                 where criada_em >= ?
                """, (rs, linha) -> new Janela(
                rs.getLong("transferencias"),
                rs.getLong("valor_transferido"),
                rs.getLong("taxas"),
                rs.getLong("ticket_medio"),
                rs.getLong("estornos")), inicioJanela);

        return new ResumoResponse(
                atual.contasAtivas(),
                FormatacaoMonetaria.paraReais(atual.saldoUsuarios()),
                atual.agendamentosPendentes(),
                janela.transferencias(),
                FormatacaoMonetaria.paraReais(janela.valorTransferido()),
                FormatacaoMonetaria.paraReais(janela.taxas()),
                FormatacaoMonetaria.paraReais(janela.ticketMedio()),
                janela.estornos(),
                atual.somaMovimentos() == 0);
    }

    private record EstadoAtual(long contasAtivas, long saldoUsuarios, long agendamentosPendentes, long somaMovimentos) {
    }

    private record Janela(long transferencias, long valorTransferido, long taxas, long ticketMedio, long estornos) {
    }
}
