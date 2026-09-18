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
@Table(name = "agendamento")
public class Agendamento {

    private static final int TAMANHO_MAXIMO_MOTIVO = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "conta_origem_id", updatable = false)
    private Long contaOrigemId;

    @Column(name = "conta_destino_id", updatable = false)
    private Long contaDestinoId;

    @Column(name = "valor_centavos", updatable = false)
    private long valorCentavos;

    @Column(name = "executar_em", updatable = false)
    private OffsetDateTime executarEm;

    @Enumerated(EnumType.STRING)
    private EstadoAgendamento estado;

    private int tentativas;

    @Column(name = "transferencia_id")
    private Long transferenciaId;

    @Column(name = "motivo_falha")
    private String motivoFalha;

    @Column(name = "criado_em", updatable = false)
    private OffsetDateTime criadoEm;

    @Column(name = "atualizado_em")
    private OffsetDateTime atualizadoEm;

    protected Agendamento() {
    }

    public Agendamento(Long contaOrigemId, Long contaDestinoId, long valorCentavos, OffsetDateTime executarEm,
                       OffsetDateTime agora) {
        this.contaOrigemId = contaOrigemId;
        this.contaDestinoId = contaDestinoId;
        this.valorCentavos = valorCentavos;
        this.executarEm = executarEm;
        this.estado = EstadoAgendamento.AGENDADO;
        this.tentativas = 0;
        this.criadoEm = agora;
        this.atualizadoEm = agora;
    }

    public void marcarComoExecutado(Long transferenciaId, OffsetDateTime agora) {
        estado = MaquinaDeEstados.transicionar(estado, EstadoAgendamento.EXECUTADO);
        this.transferenciaId = transferenciaId;
        this.tentativas++;
        this.atualizadoEm = agora;
    }

    public void marcarComoFalhado(String motivo, OffsetDateTime agora) {
        estado = MaquinaDeEstados.transicionar(estado, EstadoAgendamento.FALHADO);
        this.motivoFalha = limitarTamanho(motivo);
        this.tentativas++;
        this.atualizadoEm = agora;
    }

    public void registrarErroTecnico(String motivo, int maximoTentativas, OffsetDateTime agora) {
        tentativas++;
        motivoFalha = limitarTamanho(motivo);
        atualizadoEm = agora;
        if (tentativas >= maximoTentativas) {
            estado = MaquinaDeEstados.transicionar(estado, EstadoAgendamento.FALHADO);
        }
    }

    private static String limitarTamanho(String texto) {
        if (texto == null || texto.length() <= TAMANHO_MAXIMO_MOTIVO) {
            return texto;
        }
        return texto.substring(0, TAMANHO_MAXIMO_MOTIVO);
    }

    public Long getId() {
        return id;
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

    public OffsetDateTime getExecutarEm() {
        return executarEm;
    }

    public EstadoAgendamento getEstado() {
        return estado;
    }

    public int getTentativas() {
        return tentativas;
    }

    public Long getTransferenciaId() {
        return transferenciaId;
    }

    public String getMotivoFalha() {
        return motivoFalha;
    }
}
