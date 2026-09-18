package br.com.desafio.contas.entity;

import br.com.desafio.contas.shared.FormatacaoMonetaria;
import br.com.desafio.contas.exception.CodigoErro;
import br.com.desafio.contas.exception.RegraDeNegocioException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "conta")
public class Conta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String numero;

    @Column(name = "usuario_id")
    private Long usuarioId;

    @Column(name = "saldo_centavos")
    private long saldoCentavos;

    @Column(name = "limite_diario_centavos")
    private long limiteDiarioCentavos;

    @Enumerated(EnumType.STRING)
    private EstadoConta estado;

    @Version
    private Long versao;

    protected Conta() {
    }

    public Conta(Long id, String numero, Long usuarioId, long saldoCentavos, long limiteDiarioCentavos, EstadoConta estado) {
        this.id = id;
        this.numero = numero;
        this.usuarioId = usuarioId;
        this.saldoCentavos = saldoCentavos;
        this.limiteDiarioCentavos = limiteDiarioCentavos;
        this.estado = estado;
    }

    public boolean isContaDoSistema() {
        return usuarioId == null;
    }

    public void creditar(long valorCentavos) {
        saldoCentavos = Math.addExact(saldoCentavos, valorCentavos);
    }

    public void debitar(long valorCentavos) {
        long novoSaldo = Math.subtractExact(saldoCentavos, valorCentavos);
        if (!isContaDoSistema() && novoSaldo < 0) {
            throw new RegraDeNegocioException(CodigoErro.SALDO_INSUFICIENTE,
                    "Saldo insuficiente na conta " + numero + " para debitar " + FormatacaoMonetaria.formatar(valorCentavos));
        }
        saldoCentavos = novoSaldo;
    }

    public void bloquear() {
        estado = MaquinaDeEstados.transicionar(estado, EstadoConta.BLOQUEADA);
    }

    public void desbloquear() {
        estado = MaquinaDeEstados.transicionar(estado, EstadoConta.ATIVA);
    }

    public void encerrar() {
        estado = MaquinaDeEstados.transicionar(estado, EstadoConta.ENCERRADA);
    }

    public Long getId() {
        return id;
    }

    public String getNumero() {
        return numero;
    }

    public Long getUsuarioId() {
        return usuarioId;
    }

    public long getSaldoCentavos() {
        return saldoCentavos;
    }

    public long getLimiteDiarioCentavos() {
        return limiteDiarioCentavos;
    }

    public EstadoConta getEstado() {
        return estado;
    }
}
