CREATE FUNCTION carga_registrar_movimento(p_conta_id BIGINT,
                                          p_transferencia_id BIGINT,
                                          p_tipo VARCHAR,
                                          p_valor BIGINT,
                                          p_momento TIMESTAMPTZ) RETURNS void AS
$$
DECLARE
    v_saldo     BIGINT;
    v_sequencia BIGINT;
BEGIN
    SELECT saldo_centavos INTO v_saldo FROM conta WHERE id = p_conta_id FOR UPDATE;

    IF p_tipo = 'ENTRADA' THEN
        v_saldo := v_saldo + p_valor;
    ELSE
        v_saldo := v_saldo - p_valor;
    END IF;

    SELECT COALESCE(MAX(sequencia), 0) + 1 INTO v_sequencia FROM movimento WHERE conta_id = p_conta_id;

    UPDATE conta SET saldo_centavos = v_saldo, versao = versao + 1 WHERE id = p_conta_id;

    INSERT INTO movimento (conta_id, transferencia_id, sequencia, tipo, natureza, valor_centavos,
                           saldo_apos_centavos, criado_em)
    VALUES (p_conta_id, p_transferencia_id, v_sequencia, p_tipo, 'VALOR', p_valor, v_saldo, p_momento);
END;
$$ LANGUAGE plpgsql;

CREATE FUNCTION carga_inicial() RETURNS void AS
$$
DECLARE
    v_entrada_id       BIGINT;
    v_usuario_id       BIGINT;
    v_conta_id         BIGINT;
    v_transferencia_id BIGINT;
    v_agora            TIMESTAMPTZ := now();
    r                  RECORD;
BEGIN
    INSERT INTO conta (numero, usuario_id, saldo_centavos, limite_diario_centavos, estado)
    VALUES ('SISTEMA-ENTRADA', NULL, 0, 0, 'ATIVA')
    RETURNING id INTO v_entrada_id;

    INSERT INTO conta (numero, usuario_id, saldo_centavos, limite_diario_centavos, estado)
    VALUES ('SISTEMA-TAXAS', NULL, 0, 0, 'ATIVA');

    FOR r IN
        SELECT *
        FROM (VALUES ('Ana Paula Ribeiro', '52998224725', 'CONTA-001', 100000::BIGINT, 'ATIVA'),
                     ('Bruno Carvalho Lima', '39053344705', 'CONTA-002', 100000::BIGINT, 'ATIVA'),
                     ('Carla Mendes Rocha', '11144477735', 'CONTA-003', 100000::BIGINT, 'ATIVA'),
                     ('Diego Fernandes Alves', '93541134780', 'CONTA-004', 100000::BIGINT, 'ATIVA'),
                     ('Elaine Costa Barros', '28625587887', 'CONTA-005', 100000::BIGINT, 'ATIVA'),
                     ('Fabio Nogueira Pinto', '74682489070', 'CONTA-006', 30000::BIGINT, 'BLOQUEADA'),
                     ('Gabriela Martins Dias', '35706545028', 'CONTA-007', 0::BIGINT, 'ENCERRADA'))
                 AS dados(nome, cpf, numero, saldo, estado)
        ORDER BY numero
        LOOP
            INSERT INTO usuario (nome, cpf) VALUES (r.nome, r.cpf) RETURNING id INTO v_usuario_id;

            INSERT INTO conta (numero, usuario_id, saldo_centavos, limite_diario_centavos, estado)
            VALUES (r.numero, v_usuario_id, 0, 200000, r.estado)
            RETURNING id INTO v_conta_id;

            IF r.saldo > 0 THEN
                INSERT INTO transferencia (tipo, conta_origem_id, conta_destino_id, valor_centavos, taxa_centavos,
                                           estado, motivo, criada_em, concluida_em)
                VALUES ('DEPOSITO', v_entrada_id, v_conta_id, r.saldo, 0, 'CONFIRMADA', 'Carga inicial', v_agora,
                        v_agora)
                RETURNING id INTO v_transferencia_id;

                PERFORM carga_registrar_movimento(v_entrada_id, v_transferencia_id, 'SAIDA', r.saldo, v_agora);
                PERFORM carga_registrar_movimento(v_conta_id, v_transferencia_id, 'ENTRADA', r.saldo, v_agora);
            END IF;
        END LOOP;
END;
$$ LANGUAGE plpgsql;

CREATE FUNCTION resetar_dados() RETURNS void AS
$$
BEGIN
    PERFORM set_config('app.reset_dados', 'sim', true);
    TRUNCATE TABLE movimento, agendamento, idempotencia, transferencia, conta, usuario RESTART IDENTITY;
    PERFORM set_config('app.reset_dados', 'nao', true);
    PERFORM carga_inicial();
END;
$$ LANGUAGE plpgsql;

SELECT carga_inicial();
