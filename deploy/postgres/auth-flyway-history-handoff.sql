-- ==============================================================================
-- Auth Flyway 历史表安全接管脚本
-- 目的：把共享 public.flyway_schema_history 中已确认属于 Auth 的 V1-V4 成功历史复制到
--      public.auth_flyway_schema_history，保留原表供其他模块继续使用。
-- 前提：应用停写，已备份数据库，且共享历史表中的 Auth V1-V4 均为 success=true。
-- 注意：本脚本不创建新 schema、不删除或重命名旧历史表，也不执行 V5。
-- ==============================================================================

BEGIN;

DO $$
BEGIN
    IF to_regclass('public.flyway_schema_history') IS NULL THEN
        RAISE EXCEPTION 'shared public.flyway_schema_history does not exist';
    END IF;
END
$$;

CREATE TABLE IF NOT EXISTS public.auth_flyway_schema_history (
    installed_rank INTEGER NOT NULL,
    version VARCHAR(50),
    description VARCHAR(200) NOT NULL,
    type VARCHAR(20) NOT NULL,
    script VARCHAR(1000) NOT NULL,
    checksum INTEGER,
    installed_by VARCHAR(100) NOT NULL,
    installed_on TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    execution_time INTEGER NOT NULL,
    success BOOLEAN NOT NULL,
    CONSTRAINT pk_auth_flyway_schema_history PRIMARY KEY (installed_rank)
);

DO $$
DECLARE
    auth_success_count INTEGER;
    auth_failed_count INTEGER;
    auth_version_count INTEGER;
    target_conflict_count INTEGER;
BEGIN
    SELECT COUNT(*)
      INTO auth_success_count
      FROM public.flyway_schema_history
     WHERE script IN (
         'V1__init_auth_schema.sql',
         'auth/V1__init_auth_schema.sql',
         'V2__seed_auth_demo_data.sql',
         'auth/V2__seed_auth_demo_data.sql',
         'V3__repair_demo_password_hash.sql',
         'auth/V3__repair_demo_password_hash.sql',
         'V4__add_admin_menus_and_permissions.sql'
     )
       AND version IN ('1', '2', '3', '4')
       AND success = TRUE;

    SELECT COUNT(*)
      INTO auth_failed_count
      FROM public.flyway_schema_history
     WHERE script IN (
         'V1__init_auth_schema.sql',
         'auth/V1__init_auth_schema.sql',
         'V2__seed_auth_demo_data.sql',
         'auth/V2__seed_auth_demo_data.sql',
         'V3__repair_demo_password_hash.sql',
         'auth/V3__repair_demo_password_hash.sql',
         'V4__add_admin_menus_and_permissions.sql'
     )
       AND success = FALSE;

    SELECT COUNT(DISTINCT version)
      INTO auth_version_count
      FROM public.flyway_schema_history
     WHERE script IN (
         'V1__init_auth_schema.sql',
         'auth/V1__init_auth_schema.sql',
         'V2__seed_auth_demo_data.sql',
         'auth/V2__seed_auth_demo_data.sql',
         'V3__repair_demo_password_hash.sql',
         'auth/V3__repair_demo_password_hash.sql',
         'V4__add_admin_menus_and_permissions.sql'
     )
       AND version IN ('1', '2', '3', '4')
       AND success = TRUE;

    IF auth_success_count <> 4 OR auth_version_count <> 4 OR auth_failed_count <> 0 THEN
        RAISE EXCEPTION 'expected exactly four successful Auth V1-V4 rows and no failed Auth rows in shared history, found success %, versions %, failed %',
            auth_success_count, auth_version_count, auth_failed_count;
    END IF;

    IF EXISTS (
        SELECT 1
          FROM public.flyway_schema_history
        WHERE script IN (
             'V1__init_auth_schema.sql',
             'auth/V1__init_auth_schema.sql',
             'V2__seed_auth_demo_data.sql',
             'auth/V2__seed_auth_demo_data.sql',
             'V3__repair_demo_password_hash.sql',
             'auth/V3__repair_demo_password_hash.sql',
             'V4__add_admin_menus_and_permissions.sql'
         )
           AND success = TRUE
         GROUP BY installed_rank
        HAVING COUNT(*) > 1
    ) THEN
        RAISE EXCEPTION 'duplicate installed_rank found in Auth V1-V4 shared history';
    END IF;

    SELECT COUNT(*)
      INTO target_conflict_count
      FROM public.auth_flyway_schema_history target
      JOIN public.flyway_schema_history source
        ON source.installed_rank = target.installed_rank
     WHERE source.script IN (
         'V1__init_auth_schema.sql',
         'auth/V1__init_auth_schema.sql',
         'V2__seed_auth_demo_data.sql',
         'auth/V2__seed_auth_demo_data.sql',
         'V3__repair_demo_password_hash.sql',
         'auth/V3__repair_demo_password_hash.sql',
         'V4__add_admin_menus_and_permissions.sql'
     )
       AND source.version IN ('1', '2', '3', '4')
       AND source.success = TRUE;

    IF target_conflict_count <> 0 THEN
        RAISE EXCEPTION 'auth_flyway_schema_history already contains installed_rank values used by Auth V1-V4 shared history';
    END IF;
END
$$;

INSERT INTO public.auth_flyway_schema_history (
    installed_rank,
    version,
    description,
    type,
    script,
    checksum,
    installed_by,
    installed_on,
    execution_time,
    success
)
SELECT installed_rank,
       version,
       description,
       type,
       script,
       checksum,
       installed_by,
       installed_on,
       execution_time,
       success
  FROM public.flyway_schema_history
 WHERE script IN (
     'V1__init_auth_schema.sql',
      'auth/V1__init_auth_schema.sql',
     'V2__seed_auth_demo_data.sql',
      'auth/V2__seed_auth_demo_data.sql',
     'V3__repair_demo_password_hash.sql',
      'auth/V3__repair_demo_password_hash.sql',
     'V4__add_admin_menus_and_permissions.sql'
 )
   AND version IN ('1', '2', '3', '4')
   AND success = TRUE
 ORDER BY installed_rank;

DO $$
DECLARE
    copied_count INTEGER;
    copied_non_auth_count INTEGER;
    field_mismatch_count INTEGER;
BEGIN
    SELECT COUNT(*)
      INTO copied_count
      FROM public.auth_flyway_schema_history
     WHERE script IN (
         'V1__init_auth_schema.sql',
         'auth/V1__init_auth_schema.sql',
         'V2__seed_auth_demo_data.sql',
         'auth/V2__seed_auth_demo_data.sql',
         'V3__repair_demo_password_hash.sql',
         'auth/V3__repair_demo_password_hash.sql',
         'V4__add_admin_menus_and_permissions.sql'
     )
       AND version IN ('1', '2', '3', '4')
       AND success = TRUE;

    SELECT COUNT(*)
      INTO copied_non_auth_count
      FROM public.auth_flyway_schema_history
     WHERE script NOT IN (
         'V1__init_auth_schema.sql',
         'auth/V1__init_auth_schema.sql',
         'V2__seed_auth_demo_data.sql',
         'auth/V2__seed_auth_demo_data.sql',
         'V3__repair_demo_password_hash.sql',
         'auth/V3__repair_demo_password_hash.sql',
         'V4__add_admin_menus_and_permissions.sql'
     );

    SELECT COUNT(*)
      INTO field_mismatch_count
      FROM public.flyway_schema_history source
      JOIN public.auth_flyway_schema_history target
        ON target.installed_rank = source.installed_rank
     WHERE source.script IN (
         'V1__init_auth_schema.sql',
         'auth/V1__init_auth_schema.sql',
         'V2__seed_auth_demo_data.sql',
         'auth/V2__seed_auth_demo_data.sql',
         'V3__repair_demo_password_hash.sql',
         'auth/V3__repair_demo_password_hash.sql',
         'V4__add_admin_menus_and_permissions.sql'
     )
       AND source.version IN ('1', '2', '3', '4')
       AND source.success = TRUE
       AND (
           target.version IS DISTINCT FROM source.version OR
           target.description IS DISTINCT FROM source.description OR
           target.type IS DISTINCT FROM source.type OR
           target.script IS DISTINCT FROM source.script OR
           target.checksum IS DISTINCT FROM source.checksum OR
           target.installed_by IS DISTINCT FROM source.installed_by OR
           target.installed_on IS DISTINCT FROM source.installed_on OR
           target.execution_time IS DISTINCT FROM source.execution_time OR
           target.success IS DISTINCT FROM source.success
       );

    IF copied_count <> 4 OR copied_non_auth_count <> 0 OR field_mismatch_count <> 0 THEN
        RAISE EXCEPTION 'auth_flyway_schema_history handoff verification failed: copied %, non_auth %, mismatched %',
            copied_count, copied_non_auth_count, field_mismatch_count;
    END IF;
END
$$;

COMMIT;
