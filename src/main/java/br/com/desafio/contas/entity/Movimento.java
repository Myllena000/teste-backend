package br.com.desafio.contas.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

import java.time.OffsetDateTime;

@Entity
@Immutable
@Table(name = "movimento")
public class Movimento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "conta_id")
    private Long contaId;

    @Column(name = "transferencia_id")
    private Long transferenciaId;

    private long sequencia;

    @Enumerated(EnumType.STRING)
    private TipoMovimento tipo;

    @Enumerated(EnumType.STRING)
    private NaturezaMovimento natureza;

    @Column(name = "valor_centavos")
    private long valorCentavos;

    @Column(name = "saldo_apos_centavos")
    private long saldoAposCentavos;

    @Column(name = "criado_em")
    private OffsetDateTime criadoEm;

    protected Movimento() {
    }

    public Movimento(Long contaId, Long transferenciaId, long sequencia, TipoMovimento tipo, NaturezaMovimento natureza,
                     long valorCentavos, long saldoAposCentavos, OffsetDateTime criadoEm) {
        this.contaId = contaId;
        this.transferenciaId = transferenciaId;
        this.sequencia = sequencia;
        this.tipo = tipo;
        this.natureza = natureza;
        this.valorCentavos = valorCentavos;
        this.saldoAposCentavos = saldoAposCentavos;
        this.criadoEm = criadoEm;
    }

    public Long getId() {
        return id;
    }

    public Long getContaId() {
        return contaId;
    }

    public Long getTransferenciaId() {
        return transferenciaId;
    }

    public long getSequencia() {
        return sequencia;
    }

    public TipoMovimento getTipo() {
        return tipo;
    }

    public NaturezaMovimento getNatureza() {
        return natureza;
    }

    public long getValorCentavos() {
        return valorCentavos;
    }

    public long getSaldoAposCentavos() {
        return saldoAposCentavos;
    }

    public OffsetDateTime getCriadoEm() {
        return criadoEm;
    }
}
