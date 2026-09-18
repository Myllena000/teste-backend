package br.com.desafio.contas.repository;

import br.com.desafio.contas.entity.Movimento;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.OffsetDateTime;

public interface MovimentoRepository extends JpaRepository<Movimento, Long> {

    @Query("select coalesce(max(m.sequencia), 0) from Movimento m where m.contaId = :contaId")
    long buscarUltimaSequencia(Long contaId);

    @Query(value = """
            select cast(coalesce(sum(m.valor_centavos), 0) as bigint)
              from movimento m
              join transferencia t on t.id = m.transferencia_id
             where m.conta_id = :contaId
               and m.tipo = 'SAIDA'
               and m.natureza = 'VALOR'
               and t.tipo = 'TRANSFERENCIA'
               and m.criado_em >= :inicio
               and m.criado_em < :fim
            """, nativeQuery = true)
    long somarValoresTransferidosNoPeriodo(Long contaId, OffsetDateTime inicio, OffsetDateTime fim);

    @Query(value = """
            select m from Movimento m
             where m.contaId = :contaId
               and m.criadoEm >= :inicio
               and m.criadoEm < :fim
             order by m.sequencia desc
            """,
            countQuery = """
            select count(m) from Movimento m
             where m.contaId = :contaId
               and m.criadoEm >= :inicio
               and m.criadoEm < :fim
            """)
    Page<Movimento> buscarExtrato(Long contaId, OffsetDateTime inicio, OffsetDateTime fim, Pageable pageable);
}
