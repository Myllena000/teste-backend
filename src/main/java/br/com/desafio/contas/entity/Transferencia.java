package br.com.desafio.contas.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

@Entity
@Table(name = "transferencia")
public class Transferencia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(updatable = false)
    private TipoTransferencia tipo;

    @Column(name = "conta_origem_id", updatable = false)
    private Long contaOrigemId;

    @Column(name = "conta_destino_id", updatable = false)
    private Long contaDestinoId;

    @Column(name = "valor_centavos", updatable = false)
    private long valorCentavos;

    @Column(name = "taxa_centavos", updatable = false)
    private long taxaCentavos;

    @Enumerated(EnumType.STRING)
    private EstadoTransferencia estado;

    @Column(name = "transferencia_original_id", updatable = false)
    private Long transferenciaOriginalId;

    @Column(updatable = false)
    private String motivo;

    @Column(name = "criada_em", updatable = false)
    private OffsetDateTime criadaEm;

    @Column(name = "concluida_em")
    private OffsetDateTime concluidaEm;

    protected Transferencia() {
    }

    private Transferencia(TipoTransferencia tipo, Long contaOrigemId, Long contaDestinoId, long valorCentavos,
                          long taxaCentavos, Long transferenciaOriginalId, String motivo, OffsetDateTime criadaEm) {
        this.tipo = tipo;
        this.contaOrigemId = contaOrigemId;
        this.contaDestinoId = contaDestinoId;
        this.valorCentavos = valorCentavos;
        this.taxaCentavos = taxaCentavos;
        this.transferenciaOriginalId = transferenciaOriginalId;
        this.motivo = motivo;
        this.criadaEm = criadaEm;
        this.estado = EstadoTransferencia.CRIADA;
    }

    public static Transferencia entreContas(Long contaOrigemId, Long contaDestinoId, long valorCentavos,
                                            long taxaCentavos, OffsetDateTime agora) {
        return new Transferencia(TipoTransferencia.TRANSFERENCIA, contaOrigemId, contaDestinoId, valorCentavos,
                taxaCentavos, null, null, agora);
    }

    public static Transferencia deposito(Long contaEntradaId, Long contaDestinoId, long valorCentavos, OffsetDateTime agora) {
        return new Transferencia(TipoTransferencia.DEPOSITO, contaEntradaId, contaDestinoId, valorCentavos,
                0L, null, null, agora);
    }

    public static Transferencia estornoDe(Transferencia original, String motivo, OffsetDateTime agora) {
        return new Transferencia(TipoTransferencia.ESTORNO, original.getContaDestinoId(), original.getContaOrigemId(),
                original.getValorCentavos(), original.getTaxaCentavos(), original.getId(), motivo, agora);
    }

    public void confirmar(OffsetDateTime agora) {
        estado = MaquinaDeEstados.transicionar(estado, EstadoTransferencia.CONFIRMADA);
        concluidaEm = agora;
    }

    public void falhar() {
        estado = MaquinaDeEstados.transicionar(estado, EstadoTransferencia.FALHADA);
    }

    public void marcarComoEstornada() {
        estado = MaquinaDeEstados.transicionar(estado, EstadoTransferencia.ESTORNADA);
    }

    public Long getId() {
        return id;
    }

    public TipoTransferencia getTipo() {
        return tipo;
    }

    public Long getContaOrigemId() {
        return contaOrigemId;
    }

    public Long getContaDestinoId() {
        return contaDestinoId;
    }

    public long getValorCentavos() {
        return valorCentavos;
    }

    public long getTaxaCentavos() {
        return taxaCentavos;
    }

    public EstadoTransferencia getEstado() {
        return estado;
    }

    public Long getTransferenciaOriginalId() {
        return transferenciaOriginalId;
    }

    public String getMotivo() {
        return motivo;
    }

    public OffsetDateTime getCriadaEm() {
        return criadaEm;
    }

    public OffsetDateTime getConcluidaEm() {
        return concluidaEm;
    }
}
