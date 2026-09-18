package br.com.desafio.contas.repository;

import br.com.desafio.contas.entity.Transferencia;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface TransferenciaRepository extends JpaRepository<Transferencia, Long> {

    @Query(value = "select * from transferencia where id = :id for update skip locked", nativeQuery = true)
    Optional<Transferencia> buscarPorIdSeNaoEstiverBloqueada(Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Transferencia t where t.id = :id")
    Optional<Transferencia> buscarPorIdComBloqueio(Long id);
}
