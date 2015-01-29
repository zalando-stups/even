#!/usr/bin/env python3
'''
Grant SSH access for a given user by fetching his public key from the server.

This script should be used as SSH forced command.
'''

import argparse
import os
import pwd
import re
import requests
import subprocess
import sys
import tempfile
import yaml


USER_NAME_PATTERN = re.compile('^[a-z][a-z0-9-]{0,31}$')


def fix_ssh_pubkey(user, pubkey):
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


def get_service_url():
    '''get the service URL from cloud config YAML'''

    config = yaml.safe_load(subprocess.check_output(['sudo', 'cat', '/var/lib/cloud/instance/user-data.txt']))

    url = config['ssh_access_granting_service_url'].rstrip('/')
    return url


def download_public_key(url, name):
    r = requests.get('{url}/public-keys/{name}/sshkey.pub'.format(url=url, name=name))
    r.raise_for_status()
    pubkey = fix_ssh_pubkey(name, r.text)
    return pubkey


def grant_ssh_access(args):
    user_name = args.name

    url = get_service_url()
    pubkey = download_public_key(url, user_name)

    try:
        pwd.getpwnam(user_name)
    except:
        subprocess.check_call(['sudo', 'useradd', '--user-group', '--groups', 'adm', user_name])

    pw_entry = pwd.getpwnam(user_name)

    ssh_dir = os.path.join(pw_entry.pw_dir, '.ssh')
    keys_file = os.path.join(ssh_dir, 'authorized_keys')
    subprocess.check_call(['sudo', 'mkdir', '-p', ssh_dir])
    subprocess.check_call(['sudo', 'chown', user_name, ssh_dir])
    subprocess.check_call(['sudo', 'chmod', '700', ssh_dir])

    with tempfile.NamedTemporaryFile(suffix='{name}-sshkey.pub'.format(name=user_name)) as fd:
        fd.write(pubkey.encode('utf-8'))
        fd.flush()
        shell_template = 'cat {temp} > {keys_file} && chown {name} {keys_file} && chmod 600 {keys_file}'
        subprocess.check_call(['sudo', 'sh', '-c',
                              shell_template.format(temp=fd.name, name=user_name, keys_file=keys_file)])


def revoke_ssh_access(args):
    user_name = args.name

    pwd.getpwnam(user_name)
    # TODO: verify that the user was created by us
    raise NotImplementedError()


def fail_on_missing_command():
    sys.stderr.write('Missing command argument\n')
    sys.exit(1)


def user_name(val):
    if not USER_NAME_PATTERN.match(val):
        raise argparse.ArgumentTypeError('Invalid user name')
    return val


def main(argv):
    parser = argparse.ArgumentParser()
    subparsers = parser.add_subparsers()
    cmd = subparsers.add_parser('grant-ssh-access')
    cmd.set_defaults(func=grant_ssh_access)
    cmd.add_argument('name', help='User name', type=user_name)
    cmd = subparsers.add_parser('revoke-ssh-access')
    cmd.set_defaults(func=revoke_ssh_access)
    cmd.add_argument('name', help='User name', type=user_name)
    args = parser.parse_args(argv)

    if not hasattr(args, 'func'):
        fail_on_missing_command()

    args.func(args)


if __name__ == '__main__':
    original_command = os.environ.get('SSH_ORIGINAL_COMMAND')
    if original_command:
        sys.argv[1:] = original_command.split()

    main(sys.argv[1:])
