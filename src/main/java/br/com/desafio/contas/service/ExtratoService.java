package br.com.desafio.contas.service;

import br.com.desafio.contas.api.dto.ExtratoResponse;
import br.com.desafio.contas.api.dto.MovimentoResponse;
import br.com.desafio.contas.api.dto.TitularResponse;
import br.com.desafio.contas.shared.Cpf;
import br.com.desafio.contas.shared.DataHora;
import br.com.desafio.contas.shared.FormatacaoMonetaria;
import br.com.desafio.contas.entity.Conta;
import br.com.desafio.contas.entity.Movimento;
import br.com.desafio.contas.exception.CodigoErro;
import br.com.desafio.contas.exception.RequisicaoInvalidaException;
import br.com.desafio.contas.repository.MovimentoRepository;
import br.com.desafio.contas.repository.UsuarioRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Service
public class ExtratoService {

    private static final int TAMANHO_MAXIMO_PAGINA = 100;
    private static final OffsetDateTime INICIO_SEM_FILTRO = OffsetDateTime.of(1970, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);
    private static final OffsetDateTime FIM_SEM_FILTRO = OffsetDateTime.of(9999, 12, 31, 0, 0, 0, 0, ZoneOffset.UTC);

    private final ContaService contaService;
    private final MovimentoRepository movimentoRepository;
    private final UsuarioRepository usuarioRepository;

    public ExtratoService(ContaService contaService, MovimentoRepository movimentoRepository,
                          UsuarioRepository usuarioRepository) {
        this.contaService = contaService;
        this.movimentoRepository = movimentoRepository;
        this.usuarioRepository = usuarioRepository;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public ExtratoResponse consultar(String numeroConta, LocalDate de, LocalDate ate, int pagina, int tamanho) {
        validarParametros(de, ate, pagina, tamanho);

        Conta conta = contaService.buscarPorNumero(numeroConta);
        OffsetDateTime inicio = de == null ? INICIO_SEM_FILTRO : DataHora.inicioDoDia(de);
        OffsetDateTime fim = ate == null ? FIM_SEM_FILTRO : DataHora.inicioDoDia(ate.plusDays(1));

        Page<Movimento> movimentos = movimentoRepository.buscarExtrato(
                conta.getId(), inicio, fim, PageRequest.of(pagina, tamanho));

        TitularResponse titular = null;
        if (!conta.isContaDoSistema()) {
            titular = usuarioRepository.findById(conta.getUsuarioId())
                    .map(usuario -> new TitularResponse(usuario.getNome(), Cpf.mascarar(usuario.getCpf())))
                    .orElse(null);
        }

        return new ExtratoResponse(
                conta.getNumero(),
                conta.getEstado().name(),
                FormatacaoMonetaria.paraReais(conta.getSaldoCentavos()),
                titular,
                movimentos.getNumber(),
                movimentos.getSize(),
                movimentos.getTotalElements(),
                movimentos.getTotalPages(),
                movimentos.getContent().stream().map(this::paraResposta).toList());
    }

    private MovimentoResponse paraResposta(Movimento movimento) {
        return new MovimentoResponse(
                movimento.getSequencia(),
                movimento.getTipo().name(),
                movimento.getNatureza().name(),
                FormatacaoMonetaria.paraReais(movimento.getValorCentavos()),
                FormatacaoMonetaria.paraReais(movimento.getSaldoAposCentavos()),
                movimento.getTransferenciaId(),
                DataHora.noFusoBrasil(movimento.getCriadoEm()));
    }

    private void validarParametros(LocalDate de, LocalDate ate, int pagina, int tamanho) {
        if (pagina < 0) {
            throw new RequisicaoInvalidaException(CodigoErro.PARAMETRO_INVALIDO, "page não pode ser negativo");
        }
        if (tamanho < 1 || tamanho > TAMANHO_MAXIMO_PAGINA) {
            throw new RequisicaoInvalidaException(CodigoErro.PARAMETRO_INVALIDO,
                    "size deve estar entre 1 e " + TAMANHO_MAXIMO_PAGINA);
        }
        if (de != null && ate != null && de.isAfter(ate)) {
            throw new RequisicaoInvalidaException(CodigoErro.PARAMETRO_INVALIDO, "de não pode ser posterior a ate");
        }
    }
}
