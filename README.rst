===========================
SSH Access Granting Service
===========================

This is work in progress.

Testing with a local mock service:

.. code-block:: bash

    $ # mock cloud-config YAML
    $ sudo mkdir -p /var/lib/cloud/instance
    $ sudo chown $USER /var/lib/cloud/instance
    $ echo 'ssh_access_granting_service_url: "http://localhost:9000' > /var/lib/cloud/instance/user-data.txt
    $ # serve your own public key via HTTP
    $ mkdir -p public-keys/testuser
    $ cp ~/.ssh/id_rsa.pub public-keys/testuser/sshkey.pub
    $ python3 -m http.server 9000 &
    $ ./grant-ssh-access-forced-command.py grant-ssh-access testuser
    $ ssh testuser@localhost # try logging in
