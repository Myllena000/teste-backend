package br.com.desafio.contas.repository;

import br.com.desafio.contas.entity.Agendamento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface AgendamentoRepository extends JpaRepository<Agendamento, Long> {

    @Query(value = """
            select id from agendamento
             where estado = 'AGENDADO'
               and executar_em <= :agora
             order by executar_em, id
             limit :limite
            """, nativeQuery = true)
    List<Long> buscarIdsVencidos(OffsetDateTime agora, int limite);

    @Query(value = """
            select * from agendamento
             where id = :id
               and estado = 'AGENDADO'
               for update skip locked
            """, nativeQuery = true)
    Optional<Agendamento> buscarPendenteComBloqueio(Long id);
}
