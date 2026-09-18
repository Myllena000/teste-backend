package br.com.desafio.contas.repository;

import br.com.desafio.contas.entity.Conta;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface ContaRepository extends JpaRepository<Conta, Long> {

    Optional<Conta> findByNumero(String numero);

    @Query("select c.id from Conta c where c.numero = :numero")
    Optional<Long> buscarIdPorNumero(String numero);

    @Query(value = "select * from conta where id = :id for update skip locked", nativeQuery = true)
    Optional<Conta> buscarPorIdSeNaoEstiverBloqueada(Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Conta c where c.id = :id")
    Optional<Conta> buscarPorIdComBloqueio(Long id);
}
