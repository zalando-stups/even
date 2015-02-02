===========================
SSH Access Granting Service
===========================

.. image:: https://travis-ci.org/zalando/ssh-access-granting-service.svg?branch=master
   :target: https://travis-ci.org/zalando/ssh-access-granting-service
   :alt: Travis CI build status

.. image:: https://coveralls.io/repos/zalando/ssh-access-granting-service/badge.svg
   :target: https://coveralls.io/r/zalando/ssh-access-granting-service
   :alt: Coveralls status

This is work in progress.

Idea
====

Users can request temporary SSH access to servers by calling the "SSH Access Granting Service" which puts their public SSH key in place.

* The user needs to authenticate against the service
* The user requests temporary SSH access for a certain host
* The service instructs the host to grant access via a SSH forced command script
* The forced command script downloads the user's public SSH key from the service
* The forced command script configures the ``/home/<user>/.ssh/authorized_keys`` file accordingly


Testing
=======

Testing with a local mock service:

.. code-block:: bash

    $ # mock cloud-config YAML
    $ sudo touch /etc/ssh-access-granting-service.yaml
    $ sudo chown $USER /etc/ssh-access-granting-service.yaml
    $ echo 'ssh_access_granting_service_url: "http://localhost:9000"' > /etc/ssh-access-granting-service.yaml
    $ # serve your own public key via HTTP
    $ mkdir -p public-keys/testuser
    $ cp ~/.ssh/id_rsa.pub public-keys/testuser/sshkey.pub
    $ python3 -m http.server 9000 &
    $ ./grant-ssh-access-forced-command.py grant-ssh-access testuser
    $ ssh testuser@localhost # try logging in

To start a web server for the application, run:

.. code-block:: bash

    $ lein ring server

ToDos
=====

This is purely experimental, but at least the following would be needed:

* Add server endpoint to request access (needs authentication)
* Implement LDAP integration to retrieve public SSH keys
* Review security concept
* Harden everything

