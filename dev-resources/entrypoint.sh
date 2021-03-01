#!/bin/env bash

cp /authorized_keys /root/.ssh/authorized_keys
chmod 600 /root/.ssh/authorized_keys
chown root:root /root/.ssh/authorized_keys

exec "/usr/sbin/sshd" "-D" "-e" "-p" "22" "-o" "PasswordAuthentication=no"
