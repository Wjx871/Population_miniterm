"""须经用户明确授权后，建立仅访问 RAG 的 SSH 账号；不读取 DeepSeek Key。"""
from pathlib import Path
import argparse
import os
import pwd
import shutil
import subprocess


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("public_key", type=Path)
    args = parser.parse_args()
    assert os.geteuid() == 0
    assert Path('/opt/yixun-ai/.yixun-managed').is_file()
    expected_ip = os.environ.get('YIXUN_RAG_EXPECTED_PRIVATE_IP')
    assert expected_ip and expected_ip in subprocess.check_output(['hostname', '-I'], text=True).split(), 'Set YIXUN_RAG_EXPECTED_PRIVATE_IP to the verified target address'
    memory = int(next(line.split()[1] for line in Path('/proc/meminfo').read_text().splitlines() if line.startswith('MemTotal:')))
    assert memory < 3145728, 'Never configure this on the treehole host'
    subprocess.run(['ssh-keygen', '-lf', str(args.public_key)], check=True)
    public = args.public_key.read_text().strip().split()
    assert len(public) >= 2 and public[0] == 'ssh-ed25519'
    directory = Path('/etc/yixun-tunnel')
    try:
        account = pwd.getpwnam('yixun-tunnel')
        assert (directory / '.managed').exists(), 'Existing unrelated account'
    except KeyError:
        subprocess.run(['useradd', '--system', '--user-group', '--no-create-home', '--home-dir', '/var/lib/yixun-tunnel', '--shell', '/bin/sh', 'yixun-tunnel'], check=True)
        account = pwd.getpwnam('yixun-tunnel')
    directory.mkdir(mode=0o750, exist_ok=True)
    os.chown(directory, 0, account.pw_gid)
    directory.chmod(0o750)
    (directory / '.managed').touch(mode=0o600, exist_ok=True)
    values = dict(line.split('=', 1) for line in Path('/etc/yixun-ai/service.env').read_text().splitlines())
    service_key = directory / 'service.key'
    service_key.write_text(values['YIXUN_AI_API_KEY'] + '\n')
    os.chown(service_key, 0, account.pw_gid)
    service_key.chmod(0o640)
    helper = Path('/usr/local/libexec/yixun-tunnel-command')
    helper.parent.mkdir(mode=0o755, exist_ok=True)
    helper.write_text('#!/bin/sh\nset -eu\ncase "${SSH_ORIGINAL_COMMAND:-}" in\n  get-service-key) exec cat /etc/yixun-tunnel/service.key ;;\n  *) echo "Only the RAG tunnel and service-key retrieval are allowed." >&2; exit 1 ;;\nesac\n')
    helper.chmod(0o755)
    profile = Path('/etc/ssh/yixun-tunnel.conf')
    profile.write_text('''Match User yixun-tunnel
    AuthenticationMethods publickey
    PasswordAuthentication no
    KbdInteractiveAuthentication no
    AllowTcpForwarding local
    AllowStreamLocalForwarding no
    PermitOpen 127.0.0.1:8091
    PermitListen none
    AllowAgentForwarding no
    X11Forwarding no
    PermitTTY no
    PermitTunnel no
    PermitUserRC no
    ForceCommand /usr/local/libexec/yixun-tunnel-command
''')
    profile.chmod(0o644)
    config = Path('/etc/ssh/sshd_config')
    original = config.read_bytes()
    include = b'Include /etc/ssh/yixun-tunnel.conf'
    if include not in original:
        backup = directory / 'sshd_config.before-yixun'
        if not backup.exists():
            shutil.copy2(config, backup)
            backup.chmod(0o600)
        # Match 只在主配置末尾引入，避免影响后续全局选项。
        config.write_bytes(original + b'\n# YIXUN_RAG_TUNNEL\n' + include + b'\n')
    try:
        subprocess.run(['/usr/sbin/sshd', '-t'], check=True)
        effective = subprocess.check_output(['/usr/sbin/sshd', '-T', '-C', 'user=yixun-tunnel,host=localhost,addr=127.0.0.1'], text=True)
        settings = dict(line.split(' ', 1) for line in effective.splitlines() if ' ' in line)
        for key, value in {'allowtcpforwarding':'local', 'permitopen':'127.0.0.1:8091',
                           'permitlisten':'none', 'permittty':'no', 'passwordauthentication':'no',
                           'forcecommand':'/usr/local/libexec/yixun-tunnel-command'}.items():
            assert settings.get(key) == value, 'SSH restriction not effective: ' + key
        subprocess.run(['systemctl', 'reload', 'ssh'], check=True)
    except Exception:
        config.write_bytes(original)
        raise
    # 只有限制配置校验并载入之后，才允许该公钥认证。
    home = Path('/var/lib/yixun-tunnel')
    for path in (home, home / '.ssh'):
        path.mkdir(mode=0o755, exist_ok=True)
        os.chown(path, 0, 0)
        path.chmod(0o755)
    authorized = home / '.ssh/authorized_keys'
    authorized.write_text('restrict,port-forwarding,permitopen="127.0.0.1:8091",command="/usr/local/libexec/yixun-tunnel-command" ' + ' '.join(public[:2]) + ' yixun-rag-tunnel\n')
    authorized.chmod(0o644)
    os.chown(authorized, 0, 0)
    print('Restricted RAG SSH account configured; no provider credential was read.')
    print('YIXUN_HOST_PUBLIC_KEY_BEGIN')
    print(Path('/etc/ssh/ssh_host_ed25519_key.pub').read_text().strip())
    print('YIXUN_HOST_PUBLIC_KEY_END')


if __name__ == '__main__':
    main()
