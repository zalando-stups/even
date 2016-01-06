#!/usr/bin/env python3
'''
Grant SSH access for a given user by fetching his public key from the server.

This script should be used as SSH forced command.

Testing this script with a local mock service:

.. code-block:: bash

    $ sudo touch /etc/ssh-access-granting-service.yaml
    $ sudo chown $USER /etc/ssh-access-granting-service.yaml
    $ echo 'ssh_access_granting_service_url: "http://localhost:9000"' > /etc/ssh-access-granting-service.yaml
    $ # serve your own public key via HTTP
    $ mkdir -p public-keys/testuser
    $ cp ~/.ssh/id_rsa.pub public-keys/testuser/sshkey.pub
    $ python3 -m http.server 9000 &
    $ ./grant-ssh-access-forced-command.py grant-ssh-access testuser
    $ ssh testuser@localhost # try logging in
'''

import argparse
import datetime
import ipaddress
import os
import pwd
import re
import requests
import shlex
import socket
import subprocess
import sys
import syslog
import tempfile
import yaml
import time

from pathlib import Path


USER_NAME_PATTERN = re.compile('^[a-z][a-z0-9-]{0,31}$')
HOST_NAME_PATTERN = re.compile('^[a-z0-9.-]{0,255}$')

CONFIG_FILE_PATH = Path('/etc/ssh-access-granting-service.yaml')

MARKER = '(generated by even)'

WELCOME_MESSAGE = 'Your SSH access was granted by even (SSH access granting service) on {date}'
REVOKED_MESSAGE = 'Your SSH access was revoked by even (SSH access granting service) on {date}'
USER_COMMENT = 'SSH user created by even (SSH access granting service) on {date}'

DEFAULT_SHELL = '/bin/bash'


def date():
    now = datetime.datetime.now()
    return now.strftime('%Y-%m-%d %H:%M:%S')


def fix_ssh_pubkey(user: str, pubkey: str):
    '''Validate that the given public SSH key looks like a valid OpenSSH key which can be used in authorized_keys'''

    pubkey = pubkey.strip()
    parts = pubkey.split()[:2]  # just the type and the key, the "mail" is probably wrong
    if not parts:
        raise ValueError('Invalid SSH public key... the key is empty')
    if not (parts[0].startswith('ssh-') or parts[0].startswith('ecdsa-')):
        raise ValueError('Invalid SSH public key... no "rsa", "dsa" or "ecdsa" key...')

    # TODO? check if it can be base64 decoded?
    if len(parts[1]) % 4:
        raise ValueError('Invalid SSH public key... length modulo 4 is not 0')

    pubkey = ' '.join(parts)
    if pubkey.find('@') != -1:
        raise ValueError('Invalid SSH public key... no space between key and mail address')

    # add user name as comment
    pubkey += ' %s' % user
    return pubkey


def get_config():
    if CONFIG_FILE_PATH.exists():
        with CONFIG_FILE_PATH.open('rb') as fd:
            config = yaml.safe_load(fd)
    else:
        config = yaml.safe_load(subprocess.check_output(['sudo', 'cat', '/var/lib/cloud/instance/user-data.txt']))
    return config


def get_service_url():
    '''Get the service URL from the global config file or from cloud config YAML'''

    config = get_config()

    url = config['ssh_access_granting_service_url'].rstrip('/')
    return url


def download_public_key(url, name):
    '''Download the SSH public key for the given user name from URL'''

    r = requests.get('{url}/public-keys/{name}/sshkey.pub'.format(url=url, name=name), timeout=10)
    if r.status_code != 200:
        raise Exception('Failed to download public key for "{}" from {}: server returned status {}'.format(
                        name, url, r.status_code))
    pubkey = fix_ssh_pubkey(name, r.text)
    return pubkey


def add_our_mark(pubkey):
    return '{} {}'.format(pubkey, MARKER)


def add_forced_command(pubkey, forced_command):
    if forced_command:
        return 'command="{}" {}'.format(forced_command, pubkey)
    else:
        return pubkey


def user_exists(user_name: str) -> bool:
    try:
        pwd.getpwnam(user_name)
        return True
    except:
        return False


def get_keys_file_path(user_name: str) -> Path:
    pw_entry = pwd.getpwnam(user_name)

    ssh_dir = Path(pw_entry.pw_dir) / '.ssh'
    keys_file = ssh_dir / 'authorized_keys'
    return keys_file


def generate_authorized_keys(user_name: str, keys_file: Path, pubkey: str, forced_command: str=None):
    ssh_dir = keys_file.parent
    subprocess.check_call(['sudo', 'mkdir', '-p', str(ssh_dir)])
    subprocess.check_call(['sudo', 'chown', user_name, str(ssh_dir)])
    subprocess.check_call(['sudo', 'chmod', '0700', str(ssh_dir)])

    # NOTE: we write the temporary SSH public key into tmpfs (shm) to also work in "disk full" situations
    with tempfile.NamedTemporaryFile(suffix='{name}-sshkey.pub'.format(name=user_name), dir='/run/shm') as fd:
        fd.write(add_our_mark(add_forced_command(pubkey, forced_command)).encode('utf-8'))
        fd.flush()
        shell_template = 'cat {temp} > {keys_file} && chown {name} {keys_file} && chmod 600 {keys_file}'
        subprocess.check_call(['sudo', 'sh', '-c',
                              shell_template.format(temp=fd.name, name=user_name, keys_file=keys_file)])


def write_welcome_message(home_dir: Path):
    '''Write SSH welcome banner to ~/.profile'''
    profile_path = home_dir / '.profile'
    command = 'echo "echo {}" > {}'.format(shlex.quote(WELCOME_MESSAGE.format(date=date())), profile_path)
    subprocess.check_call(['sudo', 'sh', '-c', command])


def is_remote_host_allowed(remote_host: str):
    config = get_config()
    allowed_networks = config.get('allowed_remote_networks', [])
    host_ips = []
    for addrinfo in socket.getaddrinfo(remote_host, 22, proto=socket.IPPROTO_TCP):
        host_ips.append(ipaddress.ip_address(addrinfo[4][0]))
    for net in allowed_networks:
        for host_ip in host_ips:
            if host_ip in ipaddress.ip_network(net):
                return True
    return False


def grant_ssh_access(args):
    user_name = args.name

    url = get_service_url()
    pubkey = download_public_key(url, user_name)

    try:
        pwd.getpwnam(user_name)

    except:
        config = get_config()
        try:
            subprocess.check_call(['sudo', 'useradd',
                                   '--user-group',
                                   '--groups', ','.join(config.get('user_groups', ['adm'])),
                                   '--shell', DEFAULT_SHELL,
                                   '--create-home',
                                   # colon is not allowed in the comment field..
                                   '--comment', USER_COMMENT.format(date=date()).replace(':', '-'),
                                   user_name])
        except:
            # out of disk space? try to continue anyway
            pass

    try:
        keys_file = get_keys_file_path(user_name)
        generate_authorized_keys(user_name, keys_file, pubkey)
        write_welcome_message(keys_file.parent.parent)
    except:
        # out of disk space? use fallback and allow login via root
        # /root/.ssh/ must be mounted as tmpfs (memory disk) for this to work!
        generate_authorized_keys('root', Path('/root/.ssh/authorized_keys'), pubkey)

    if args.remote_host:
        if not is_remote_host_allowed(args.remote_host):
            raise Exception('Remote host "{}" is not in one of the allowed networks'.format(args.remote_host))
        grant_ssh_access_on_remote_host(user_name, args.remote_host)


def execute_ssh(user: str, host: str, command: str):
    subprocess.check_call(['ssh',
                           '-o', 'UserKnownHostsFile=/dev/null',
                           '-o', 'StrictHostKeyChecking=no',
                           '-o', 'BatchMode=yes',
                           '-o', 'ConnectTimeout=10',
                           '-l', 'granting-service', host, command, user])


def grant_ssh_access_on_remote_host(user: str, host: str):
    execute_ssh(user, host, 'grant-ssh-access')


def revoke_ssh_access_on_remote_host(user: str, host: str):
    execute_ssh(user, host, 'revoke-ssh-access')


def is_generated_by_us(keys_file):
    '''verify that the user was created by us'''
    output = subprocess.check_output(['sudo', 'cat', str(keys_file)])
    return MARKER.encode('utf-8') in output


def kill_all_processes(user_name: str):
    '''try to write session before killing all processes'''
    subprocess.call(['sudo', 'killall', '-u', user_name, '-w'])
    time.sleep(2)
    subprocess.call(['sudo', 'killall', '-KILL', '-u', user_name, '-w'])


def revoke_ssh_access(args: list):
    user_name = args.name

    if not args.keep_local and user_exists(user_name):
        url = get_service_url()
        pubkey = download_public_key(url, user_name)

        keys_file = get_keys_file_path(user_name)

        if not is_generated_by_us(keys_file):
            raise Exception('Cannot revoke SSH access from user "{}": ' +
                            'the user was not created by this script.\n'.format(user_name))

        forced_command = 'echo {}'.format(shlex.quote(REVOKED_MESSAGE.format(date=date())))
        generate_authorized_keys(user_name, keys_file, pubkey, forced_command)
        kill_all_processes(user_name)

    if args.remote_host:
        if not is_remote_host_allowed(args.remote_host):
            raise Exception('Remote host "{}" is not in one of the allowed networks'.format(args.remote_host))
        revoke_ssh_access_on_remote_host(user_name, args.remote_host)


def fail_on_missing_command():
    sys.stderr.write('Missing command argument\n')
    sys.exit(1)


def user_name(val: str):
    '''Validate user name parameter'''

    if not USER_NAME_PATTERN.match(val):
        raise argparse.ArgumentTypeError('Invalid user name')
    return val


def host_name(val: str):
    '''Validate host name parameter'''

    if not HOST_NAME_PATTERN.match(val):
        raise argparse.ArgumentTypeError('Invalid host name')
    return val


def main(argv: list):
    parser = argparse.ArgumentParser()
    subparsers = parser.add_subparsers()
    cmd = subparsers.add_parser('grant-ssh-access')
    cmd.set_defaults(func=grant_ssh_access)
    cmd.add_argument('name', help='User name', type=user_name)
    cmd.add_argument('--remote-host', help='Remote host to add user on', type=host_name)
    cmd = subparsers.add_parser('revoke-ssh-access')
    cmd.set_defaults(func=revoke_ssh_access)
    cmd.add_argument('name', help='User name', type=user_name)
    cmd.add_argument('--remote-host', help='Remote host to remove user from', type=host_name)
    cmd.add_argument('--keep-local', help='Keep local SSH access, only remove on remote host', action='store_true')
    args = parser.parse_args(argv)

    if not hasattr(args, 'func'):
        fail_on_missing_command()

    syslog.openlog(ident=os.path.basename(__file__), logoption=syslog.LOG_PID, facility=syslog.LOG_AUTH)
    syslog.syslog(' '.join(argv))
    try:
        args.func(args)
    except Exception as e:
        sys.stderr.write('ERROR: {}\n'.format(e))
        sys.exit(1)


if __name__ == '__main__':
    original_command = os.environ.get('SSH_ORIGINAL_COMMAND')
    if original_command:
        sys.argv[1:] = original_command.split()

    main(sys.argv[1:])
