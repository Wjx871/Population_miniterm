"""Isolated real Redis / two JVM HTTP characterization. No JWT or credential is logged."""
import base64, json, os, pathlib, secrets, socket, subprocess, sys, time, urllib.error, urllib.request, uuid

root = pathlib.Path(__file__).resolve().parents[2]
base = root.parent / 'redis-isolated'
binary = base / 'bin/Redis-8.10.3-Windows-x64-msys2/redis-server.exe'
cp = (root / 'target/issue29-64-classpath.txt').read_text().strip()
classpath = str(root / 'target/test-classes') + ';' + str(root / 'target/classes') + ';' + cp
password = secrets.token_urlsafe(32)
app_password = secrets.token_urlsafe(32)
prefix = 'issue64-' + uuid.uuid4().hex
def cyg(p):
    s = p.as_posix()
    return '/cygdrive/' + s[0].lower() + s[2:]

processes = []
logdir = base / ('run-' + uuid.uuid4().hex)
logdir.mkdir()

def port():
    with socket.socket() as s:
        s.bind(('127.0.0.1', 0)); return s.getsockname()[1]
rport, aport, bport = port(), port(), port()

def event(name, **values):
    print(json.dumps({'event': name, **values}), flush=True)

def redis(*parts):
    def wire(cmd):
        b = [str(x).encode() for x in cmd]
        return b'*%d\r\n' % len(b) + b''.join(b'$%d\r\n'%len(x)+x+b'\r\n' for x in b)
    with socket.create_connection(('127.0.0.1', rport), timeout=3) as sock:
        f = sock.makefile('rb')
        def send(cmd):
            sock.sendall(wire(cmd)); line = f.readline()
            if not line: raise RuntimeError('redis disconnected')
            if line[:1] == b'-': raise RuntimeError('redis returned error')
            if line[:1] == b':': return int(line[1:])
            if line[:1] == b'$':
                n=int(line[1:]); return None if n < 0 else (f.read(n), f.read(2))[0]
            return line[1:].strip()
        send(('AUTH', password))
        return send(parts)

def start_redis(name):
    data = logdir / ('data-' + name); data.mkdir()
    conf = logdir / ('redis-' + name + '.conf')
    conf.write_text('\n'.join(['bind 127.0.0.1', 'protected-mode yes', f'port {rport}',
        f'dir {data.as_posix()}', 'dbfilename dump.rdb', 'save ""', 'appendonly no',
        f'requirepass {password}', f'user app on >{app_password} ~{prefix}:* +@all', 'loglevel notice'])+'\n', encoding='utf8')
    out=open(logdir / ('redis-'+name+'.log'),'wb')
    p=subprocess.Popen([str(binary),cyg(conf)], stdout=out, stderr=subprocess.STDOUT, cwd=str(base), creationflags=subprocess.CREATE_NO_WINDOW)
    processes.append(p)
    for _ in range(100):
        if p.poll() is not None: raise RuntimeError('redis exited during startup')
        try:
            if redis('PING') == b'PONG': break
        except (OSError, RuntimeError): time.sleep(.1)
    else: raise RuntimeError('redis startup timed out')
    event('redis_started', pid=p.pid, port=rport, data_dir=str(data), persistence='RDB explicit SAVE; AOF disabled')
    return p

def http(port_, path, token=None, body=None):
    headers={}
    if token: headers['Authorization']='Bearer '+token
    if body is not None: headers['Content-Type']='application/json'
    req=urllib.request.Request(f'http://127.0.0.1:{port_}{path}', data=None if body is None else json.dumps(body).encode(), headers=headers, method='GET' if body is None else 'POST')
    try:
        with urllib.request.urlopen(req,timeout=12) as r: return r.status, r.read().decode()
    except urllib.error.HTTPError as e: return e.code, e.read().decode()

def app(name, port_):
    env=os.environ.copy(); env.update({'POPULATION_REDIS_ENABLED':'true','SPRING_DATA_REDIS_HOST':'127.0.0.1','SPRING_DATA_REDIS_PORT':str(rport), 'SPRING_DATA_REDIS_PASSWORD':app_password, 'POPULATION_REDIS_KEY_PREFIX':prefix, 'SECURITY_JWT_REVOCATION_MODE':'SHARED_REDIS_REQUIRED'})
    args=['java','-cp',classpath,'com.wjx871.population.PopulationMinitermApplication', f'--server.port={port_}', '--server.address=127.0.0.1', '--spring.data.redis.username=app', '--spring.data.redis.timeout=1s', f'--spring.datasource.url=jdbc:h2:mem:issue64_{name};MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE']
    out=open(logdir / ('app-'+name+'.log'),'wb')
    p=subprocess.Popen(args, env=env, cwd=str(root), stdout=out, stderr=subprocess.STDOUT, creationflags=subprocess.CREATE_NO_WINDOW); processes.append(p)
    for _ in range(180):
        if p.poll() is not None: raise RuntimeError('application exited during startup')
        try:
            if http(port_, '/api/health')[0] == 200: break
        except (OSError, TimeoutError): pass
        time.sleep(.5)
    else: raise RuntimeError('application startup timed out')
    event('app_started', instance=name, pid=p.pid, port=port_)
    return p

def login(p, user='population'):
    status, body=http(p,'/api/auth/login',body={'username':user,'password':'123456'})
    check('login_'+user,status,200)
    return json.loads(body)['data']['token']

def check(name, actual, expected):
    event(name, actual=actual, expected=expected, passed=actual==expected)
    if actual != expected: raise AssertionError(name)

def claims(token):
    b=token.split('.')[1]; return json.loads(base64.urlsafe_b64decode(b+'='*(-len(b)%4)))

def stop(p):
    if p.poll() is None: p.terminate(); p.wait(timeout=20)

try:
    rp=start_redis('initial'); a=app('A1',aport); b=app('B1',bport)
    t=login(aport); other=login(aport,'viewer')
    check('B_before_logout',http(bport,'/api/auth/me',t)[0],200)
    check('A_logout',http(aport,'/api/auth/logout',t,{})[0],200)
    c=claims(t); key=prefix+':security:jwt:blacklist:'+c['jti']
    check('marker_exists',redis('EXISTS',key),1)
    check('absolute_expiry_ms',redis('PEXPIRETIME',key),c['exp']*1000)
    for i in range(5): check('B_repeated_revoked_'+str(i),http(bport,'/api/auth/me',t)[0],401)
    check('A_revoked',http(aport,'/api/auth/me',t)[0],401)
    check('other_user_unaffected',http(bport,'/api/auth/me',other)[0],200)
    fresh=login(aport); check('new_jti_different',claims(fresh)['jti'] != c['jti'],True)
    check('new_token_valid',http(bport,'/api/auth/me',fresh)[0],200)
    check('explicit_save',redis('SAVE') == b'OK',True)
    try: redis('SHUTDOWN','NOSAVE')
    except (OSError, RuntimeError): pass
    rp.wait(timeout=20)
    event('redis_graceful_shutdown_after_save', exit_code=rp.returncode)
    status, body=http(bport,'/api/auth/me',fresh); event('redis_down_read',status=status,closed=status!=200,credentials_or_address_leaked=(password in body or app_password in body or '127.0.0.1' in body))
    check('redis_down_login_rejected',http(aport,'/api/auth/login',body={'username':'population','password':'123456'})[0] != 200,True)
    check('public_health_available',http(aport,'/api/health')[0],200)
    # Restart using the same directory and configuration to test the saved RDB.
    out=open(logdir/'redis-restart.log','wb'); rp=subprocess.Popen([str(binary),cyg(logdir/'redis-initial.conf')],stdout=out,stderr=subprocess.STDOUT,cwd=str(base),creationflags=subprocess.CREATE_NO_WINDOW); processes.append(rp)
    for _ in range(100):
        try:
            if redis('PING') == b'PONG': break
        except (OSError,RuntimeError): time.sleep(.1)
    else: raise RuntimeError('redis restart timed out')
    event('redis_restarted',pid=rp.pid)
    first=http(aport,'/api/auth/me',t)[0]
    event('first_response_after_redis_restart', status=first)
    for _ in range(60):
        recovered=http(aport,'/api/auth/me',t)[0]
        if recovered != 503: break
        time.sleep(.5)
    check('old_token_after_redis_restart_A',recovered,401)
    check('old_token_after_redis_restart_B',http(bport,'/api/auth/me',t)[0],401)
    check('fresh_token_after_recovery',http(bport,'/api/auth/me',fresh)[0],200)
    redis('ACL','SETUSER','app','-set')
    status, body=http(aport,'/api/auth/logout',fresh,{})
    event('write_denied_logout',status=status,failed=status!=200,credentials_or_address_leaked=(password in body or app_password in body or '127.0.0.1' in body))
    redis('ACL','SETUSER','app','+set')
    stop(a); stop(b); a=app('A2',aport); b=app('B2',bport)
    check('old_token_after_app_restart_A',http(aport,'/api/auth/me',t)[0],401)
    check('old_token_after_app_restart_B',http(bport,'/api/auth/me',t)[0],401)
    stop(rp); rp=start_redis('empty')
    status,_=http(bport,'/api/auth/me',t)
    event('DATA_LOSS_CHARACTERIZATION',status=status,previously_revoked_token_accepted=(status==200))
    event('completed')
finally:
    for p in reversed(processes):
        try: stop(p)
        except Exception: event('stop_failed',pid=p.pid)
    event('own_processes_stopped')
