-- 重置 admin 用户密码为 Admin@123 (BCrypt 格式)
UPDATE sys_user
SET password_hash = '$2a$12$PF5x1ftpyHUrnR1ho2WqlOWkI81/To.GP/1/i2eeZ5bNo1B4mRos6'
WHERE username = 'admin';
