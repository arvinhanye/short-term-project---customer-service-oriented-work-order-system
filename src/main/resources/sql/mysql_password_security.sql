USE ticket_management;

ALTER TABLE users
    ADD COLUMN failed_login_attempts INT NOT NULL DEFAULT 0 AFTER status,
    ADD COLUMN locked_until DATETIME NULL AFTER failed_login_attempts,
    ADD COLUMN must_change_password TINYINT NOT NULL DEFAULT 0 AFTER locked_until,
    ADD COLUMN password_changed_at DATETIME NULL AFTER must_change_password,
    ADD INDEX idx_users_locked_until (locked_until);

-- 轮换旧版本内置账号的重复密码。仅内置示例用户名会被修改；其他用户密码不受影响。
UPDATE users
SET password_hash = CASE username
        WHEN 'admin01' THEN '$2a$12$OzJdGwxR6JOPJnEfhJHeOOf49viWN2SY26HIi04oo.SlDoujbeObq'
        WHEN 'admin02' THEN '$2a$12$Xj3FjyJfDPPbur5BJd2icey4bxSD7jR86nc.fNibClFq/bLtfghQa'
        WHEN 'admin03' THEN '$2a$12$bp0Fghd7zsc/TdOoaDm6bO5AkaqEGd8piZTM9uesnoKugtaVi53Tm'
        WHEN 'user01' THEN '$2a$12$XUOV3QxRm5lcVXsCAA9A3euDlAoZg/BRWJLDMLoeoZKZYxqBSG5pa'
        WHEN 'user02' THEN '$2a$12$ymlkux1AKQIsKIFkJF7F2eE2fNE.rFz3FyUhLTVdxuOcLSyLUEoYG'
        WHEN 'user03' THEN '$2a$12$YksUENCIP.kkFx8K7t6qZOfEASug39pXpeGTLYkvaf0XZqP7PtZa2'
        WHEN 'user04' THEN '$2a$12$NJoBy1cT663AaAc/K9WrKuZz6nNh1PD14CaZUgjpQ/TCUiBOK15Km'
        WHEN 'user05' THEN '$2a$12$/bI4dBSunVTD5DUkKQaT0u7AQJ4WThpE9bJ/pc9Cn6v2O/boJZEDO'
        WHEN 'user06' THEN '$2a$12$4MU4fb8K7pyAxHKl4qjije2hRDx0xCo/KwbuDx2zfMMogUmUpi5JW'
        WHEN 'user07' THEN '$2a$12$Q2WK1dK81z5DbVUxgg.j.uwQvVEL6.ZGcdpil8KMrWIr9Gv5MtTEW'
        WHEN 'admin04' THEN '$2a$12$XUOdfrcTzfzXyvO0BnjOiu1A4iwMjIiRtutfhfbPTW63V2Dlq.Jg2'
        WHEN 'admin05' THEN '$2a$12$xmWTZDHkgzRZCt.dWfNpbOOWYnMmLunXo46UFSVzokIPG80ltdDnK'
    END,
    must_change_password = 1,
    password_changed_at = NULL,
    failed_login_attempts = 0,
    locked_until = NULL
WHERE username IN ('admin01', 'admin02', 'admin03', 'admin04', 'admin05',
                   'user01', 'user02', 'user03', 'user04', 'user05', 'user06', 'user07');

-- 非内置管理员保留原密码，但升级后也要求在下次登录时轮换。
UPDATE users
SET must_change_password = CASE WHEN role = 'ADMIN' THEN 1 ELSE must_change_password END,
    failed_login_attempts = 0,
    locked_until = NULL;
