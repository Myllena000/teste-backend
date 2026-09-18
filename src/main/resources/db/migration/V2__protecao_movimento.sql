CREATE FUNCTION impedir_alteracao_movimento() RETURNS trigger AS
$$
BEGIN
    RAISE EXCEPTION 'Movimentos nao podem ser alterados nem apagados (operacao %)', TG_OP
        USING ERRCODE = 'restrict_violation';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER tg_movimento_sem_update_delete
    BEFORE UPDATE OR DELETE
    ON movimento
    FOR EACH ROW
EXECUTE FUNCTION impedir_alteracao_movimento();

CREATE FUNCTION impedir_truncate_movimento() RETURNS trigger AS
$$
BEGIN
    IF current_setting('app.reset_dados', true) IS DISTINCT FROM 'sim' THEN
        RAISE EXCEPTION 'Movimentos nao podem ser apagados'
            USING ERRCODE = 'restrict_violation';
    END IF;
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER tg_movimento_sem_truncate
    BEFORE TRUNCATE
    ON movimento
    FOR EACH STATEMENT
EXECUTE FUNCTION impedir_truncate_movimento();

CREATE FUNCTION validar_novo_movimento() RETURNS trigger AS
$$
DECLARE
    v_ultima_sequencia BIGINT;
    v_ultimo_saldo     BIGINT;
    v_saldo_esperado   BIGINT;
BEGIN
    SELECT sequencia, saldo_apos_centavos
    INTO v_ultima_sequencia, v_ultimo_saldo
    FROM movimento
    WHERE conta_id = NEW.conta_id
    ORDER BY sequencia DESC
    LIMIT 1;

    IF NOT FOUND THEN
        v_ultima_sequencia := 0;
        v_ultimo_saldo := 0;
    END IF;

    IF NEW.sequencia <> v_ultima_sequencia + 1 THEN
        RAISE EXCEPTION 'Sequencia % invalida para a conta %, esperado %',
            NEW.sequencia, NEW.conta_id, v_ultima_sequencia + 1
            USING ERRCODE = 'check_violation';
    END IF;

    IF NEW.tipo = 'ENTRADA' THEN
        v_saldo_esperado := v_ultimo_saldo + NEW.valor_centavos;
    ELSE
        v_saldo_esperado := v_ultimo_saldo - NEW.valor_centavos;
    END IF;

    IF NEW.saldo_apos_centavos <> v_saldo_esperado THEN
        RAISE EXCEPTION 'Saldo apos % invalido para a conta %, esperado %',
            NEW.saldo_apos_centavos, NEW.conta_id, v_saldo_esperado
            USING ERRCODE = 'check_violation';
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER tg_movimento_validar_insert
    BEFORE INSERT
    ON movimento
    FOR EACH ROW
EXECUTE FUNCTION validar_novo_movimento();
