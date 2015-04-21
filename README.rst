==================================
Even - SSH Access Granting Service
==================================

.. image:: https://travis-ci.org/zalando-stups/even.svg?branch=master
   :target: https://travis-ci.org/zalando-stups/even
   :alt: Travis CI build status

.. image:: https://coveralls.io/repos/zalando-stups/even/badge.svg
   :target: https://coveralls.io/r/zalando-stups/even
   :alt: Coveralls status

SSH access granting service to distribute personal public SSH keys on demand.


Idea
====

Users can request temporary SSH access to servers by calling the "SSH Access Granting Service" which puts their public SSH key in place.

* The user needs to authenticate against the service (currently checked via LDAP bind)
* The user requests temporary SSH access for a certain host (``POST /access-requests``)
* The service checks whether the user is allowed to gain access to the specified host by checking if the host's IP is included in one of the IP networks configured on the user's LDAP roles (using the ``ipHostNumber`` LDAP attribute)
* The service instructs the host to grant access via a SSH forced command script
* The forced command script downloads the user's public SSH key from the service (the public SSH key is read from LDAP)
* The forced command script configures the ``/home/<user>/.ssh/authorized_keys`` file accordingly

.. image:: https://raw.githubusercontent.com/zalando-stups/even/master/docs/_static/grant-ssh-access-flow.png
   :alt: Grant SSH access flow


Testing
=======


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
    $ docker build -t stups/even .

Running
=======

Running the previously built Docker image and passing configuration via environment variables:

.. code-block:: bash

    $ docker run -p 8080:8080 -e AWS_REGION_ID=eu-west-1 -e LDAP_HOST=ldap.example.org -e LDAP_SSL=true -e LDAP_BASE_DN=ou=users,dc=example,dc=org -e LDAP_GROUP_BASE_DN=ou=groups,dc=example,dc=org -e LDAP_BIND_DN=uid=ssh-key-reader,ou=users,dc=example,dc=org -e LDAP_PASSWORD="$LDAP_PASSWORD" -e SSH_PRIVATE_KEY="$SSH_PRIVATE_KEY" ssh-access-granting-service

All configuration values can be passed encrypted when running on AWS (this is supported by the underlying Friboo_ library):

.. code-block:: bash

    $ aws kms encrypt --key-id 123 --plaintext "secret" # encrypt with KMS
    $ export LDAP_PASSWORD="aws:kms:<KMS-CIPHERTEXT-BLOB>"

Configuration
=============

The following configuration parameters can/should be passed via environment variables:

``AWS_REGION_ID``
    Optional AWS region ID to use for KMS decryption (e.g. "eu-west-1").
``LDAP_BASE_DN``
    DN of the user base.
``LDAP_BIND_DN``
    DN of the user to bind with.
``LDAP_CONNECT_TIMEOUT``
    LDAP connection timeout in milliseconds (default: 10s).
``LDAP_GROUP_BASE_DN``
    DN of the group base.
``LDAP_HOST``
    The LDAP server hostname to connect to.
``LDAP_PASSWORD``
    The user's bind password (can be encrypted with KMS).
``LDAP_SSL``
    Boolean flag (``true`` or ``false``) whether to use TLS for the LDAP connection.
``SSH_AGENT_FORWARDING``
    Boolean flag whether to use agent forwarding (``-A``). Agent forwarding is necessary for bastion host support.
``SSH_PORT``
    SSH port number to use (default: 22).
``SSH_PRIVATE_KEY``
    The SSH private key (can be encrypted with KMS).
``SSH_USER``
    The SSH username on remote servers (default: "granting-service").

Requesting SSH Access
=====================

Users can use the convenience script Piu_ instead of doing a manual HTTP POST.

.. code-block:: bash

    $ sudo pip3 install --upgrade stups-piu
    $ piu 172.31.0.1 "testing the piu script"


.. _Leiningen: http://leiningen.org/
.. _Friboo: https://github.com/zalando-stups/friboo
.. _Piu: http://stups.readthedocs.org/en/latest/components/piu.html

License
=======

Copyright © 2015 Zalando SE

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
