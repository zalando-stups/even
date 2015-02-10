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
* The user requests temporary SSH access for a certain host (``POST /access-requests``)
* The service instructs the host to grant access via a SSH forced command script
* The forced command script downloads the user's public SSH key from the service
* The forced command script configures the ``/home/<user>/.ssh/authorized_keys`` file accordingly


Testing
=======

Testing the forced command script with a local mock service:

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

Development
===========

The service is written in Clojure. You need Leiningen_ installed to build or develop.

To start a web server for the application, run:

.. code-block:: bash

    $ lein repl
    => (go)

The service is now exposing its HTTP REST API under http://localhost:8080/.

Requesting access to server "127.0.0.1" for user "jdoe":

.. code-block:: bash

    $ curl -u jdoe -XPOST -H Content-Type:application/json --data '{"hostname": "127.0.0.1", "reason": "test"}' http://localhost:8080/access-requests

Building
========

To build a deployable artifact, use the ``uberjar`` task, that produces a single JAR file, that every JVM should be able to execute.

.. code-block:: bash

    $ lein uberjar
    $ docker build -t ssh-access-granting-service .

Running
=======

Running the previously built Docker image and passing configuration via environment variables:

.. code-block:: bash

    $ docker run -p 8080:8080 -e AWS_REGION_ID=eu-west-1 -e LDAP_HOST=ldap.example.org -e LDAP_SSL=true -e LDAP_BASE_DN=ou=users,dc=example,dc=org -e LDAP_BIND_DN=uid=ssh-key-reader,ou=users,dc=example,dc=org -e LDAP_PASSWORD="$LDAP_PASSWORD" -e SSH_PRIVATE_KEY="$SSH_PRIVATE_KEY" ssh-access-granting-service

All configuration values can be passed encrypted when running on AWS:

.. code-block:: bash

    $ aws kms encrypt --key-id 123 --plaintext "secret" # encrypt with KMS
    $ export LDAP_PASSWORD="aws:kms:crypto:<KMS-CIPHERTEXT-BLOB>"

ToDos
=====

This is purely experimental, but at least the following would be needed:

* Implement authorization rules (who can access which host)
* Integrate with Kerberos infrastructure
* Implement SSH key rotation
* Review security concept
* Harden everything

.. _Leiningen: http://leiningen.org/
