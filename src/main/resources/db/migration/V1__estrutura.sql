CREATE TABLE usuario (
    id   BIGSERIAL PRIMARY KEY,
    nome VARCHAR(120) NOT NULL,
    cpf  VARCHAR(11)  NOT NULL,
    CONSTRAINT ux_usuario_cpf UNIQUE (cpf),
    CONSTRAINT ck_usuario_cpf CHECK (cpf ~ '^[0-9]{11}$')
);

CREATE TABLE conta (
    id                     BIGSERIAL PRIMARY KEY,
    numero                 VARCHAR(30) NOT NULL,
    usuario_id             BIGINT REFERENCES usuario (id),
    saldo_centavos         BIGINT      NOT NULL DEFAULT 0,
    limite_diario_centavos BIGINT      NOT NULL DEFAULT 200000,
    estado                 VARCHAR(20) NOT NULL,
    versao                 BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT ux_conta_numero UNIQUE (numero),
    CONSTRAINT ck_conta_estado CHECK (estado IN ('ATIVA', 'BLOQUEADA', 'ENCERRADA')),
    CONSTRAINT ck_conta_saldo_usuario CHECK (usuario_id IS NULL OR saldo_centavos >= 0),
    CONSTRAINT ck_conta_limite CHECK (limite_diario_centavos >= 0)
);

CREATE TABLE transferencia (
    id                        BIGSERIAL PRIMARY KEY,
    tipo                      VARCHAR(20)  NOT NULL,
    conta_origem_id           BIGINT       NOT NULL REFERENCES conta (id),
    conta_destino_id          BIGINT       NOT NULL REFERENCES conta (id),
    valor_centavos            BIGINT       NOT NULL,
    taxa_centavos             BIGINT       NOT NULL DEFAULT 0,
    estado                    VARCHAR(20)  NOT NULL,
    transferencia_original_id BIGINT REFERENCES transferencia (id),
    motivo                    VARCHAR(255),
    criada_em                 TIMESTAMPTZ  NOT NULL,
    concluida_em              TIMESTAMPTZ,
    CONSTRAINT ck_transferencia_tipo CHECK (tipo IN ('DEPOSITO', 'TRANSFERENCIA', 'ESTORNO')),
    CONSTRAINT ck_transferencia_estado CHECK (estado IN ('CRIADA', 'CONFIRMADA', 'ESTORNADA', 'FALHADA')),
    CONSTRAINT ck_transferencia_valor CHECK (valor_centavos > 0),
    CONSTRAINT ck_transferencia_taxa CHECK (taxa_centavos >= 0),
    CONSTRAINT ck_transferencia_contas CHECK (conta_origem_id <> conta_destino_id),
    CONSTRAINT ck_transferencia_estorno CHECK ((tipo = 'ESTORNO') = (transferencia_original_id IS NOT NULL))
);

CREATE UNIQUE INDEX ux_transferencia_original
    ON transferencia (transferencia_original_id)
    WHERE transferencia_original_id IS NOT NULL;

CREATE INDEX ix_transferencia_criada_em
    ON transferencia (criada_em)
    INCLUDE (tipo, valor_centavos, taxa_centavos);

CREATE TABLE movimento (
    id                  BIGSERIAL PRIMARY KEY,
    conta_id            BIGINT      NOT NULL REFERENCES conta (id),
    transferencia_id    BIGINT      NOT NULL REFERENCES transferencia (id),
    sequencia           BIGINT      NOT NULL,
    tipo                VARCHAR(10) NOT NULL,
    natureza            VARCHAR(10) NOT NULL,
    valor_centavos      BIGINT      NOT NULL,
    saldo_apos_centavos BIGINT      NOT NULL,
    criado_em           TIMESTAMPTZ NOT NULL,
    CONSTRAINT ux_movimento_conta_sequencia UNIQUE (conta_id, sequencia),
    CONSTRAINT ck_movimento_sequencia CHECK (sequencia > 0),
    CONSTRAINT ck_movimento_tipo CHECK (tipo IN ('ENTRADA', 'SAIDA')),
    CONSTRAINT ck_movimento_natureza CHECK (natureza IN ('VALOR', 'TAXA')),
    CONSTRAINT ck_movimento_valor CHECK (valor_centavos > 0)
);

CREATE INDEX ix_movimento_conta_criado_em ON movimento (conta_id, criado_em);

CREATE TABLE agendamento (
    id               BIGSERIAL PRIMARY KEY,
    conta_origem_id  BIGINT       NOT NULL REFERENCES conta (id),
    conta_destino_id BIGINT       NOT NULL REFERENCES conta (id),
    valor_centavos   BIGINT       NOT NULL,
    executar_em      TIMESTAMPTZ  NOT NULL,
    estado           VARCHAR(20)  NOT NULL,
    tentativas       INTEGER      NOT NULL DEFAULT 0,
    transferencia_id BIGINT REFERENCES transferencia (id),
    motivo_falha     VARCHAR(500),
    criado_em        TIMESTAMPTZ  NOT NULL,
    atualizado_em    TIMESTAMPTZ  NOT NULL,
    CONSTRAINT ck_agendamento_estado CHECK (estado IN ('AGENDADO', 'EXECUTADO', 'FALHADO')),
    CONSTRAINT ck_agendamento_valor CHECK (valor_centavos > 0),
    CONSTRAINT ck_agendamento_contas CHECK (conta_origem_id <> conta_destino_id)
);

CREATE INDEX ix_agendamento_pendente
    ON agendamento (executar_em)
    WHERE estado = 'AGENDADO';

CREATE TABLE idempotencia (
    chave           VARCHAR(100) NOT NULL,
    endpoint        VARCHAR(60)  NOT NULL,
    hash_requisicao VARCHAR(64)  NOT NULL,
    resposta_json   TEXT,
    status_http     INTEGER,
    criado_em       TIMESTAMPTZ  NOT NULL,
    CONSTRAINT pk_idempotencia PRIMARY KEY (chave, endpoint)
);
