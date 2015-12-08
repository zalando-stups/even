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

* The user needs to authenticate against the service (via OAuth2 access token)
* The user requests temporary SSH access for a certain host (``POST /access-requests``)
* The service checks whether the user is allowed to gain access to the specified host by checking if the hostname follows the given pattern (``HTTP_ALLOWED_HOSTNAME_TEMPLATE``)
* The service instructs the host to grant access via a SSH forced command script
* The forced command script downloads the user's public SSH key from the service (the public SSH key is read from the HTTP endpoint given by ``USERSVC_SSH_PUBLIC_KEY_URL_TEMPLATE``)
* The forced command script configures the ``/home/<user>/.ssh/authorized_keys`` file accordingly

.. image:: http://docs.stups.io/en/latest/_images/grant-ssh-access-flow.svg
   :alt: Grant SSH access flow

Development
===========

The service is written in Clojure. You need Leiningen_ installed to build or develop.

To start a web server for the application, run:

.. code-block:: bash

    $ export CREDENTIALS_DIR=. # to make "tokens" library happy
    $ export OAUTH2_ACCESS_TOKENS=user-service=abc123-456 # fixed token for local development
    $ export .. # see configuration section below
    $ lein repl
    => (go)

The service is now exposing its HTTP REST API under http://localhost:8080/.

Requesting access to server "127.0.0.1" for user "jdoe":

.. code-block:: bash

    $ curl -XPOST -H Content-Type:application/json -H 'Authorization: Bearer mytoken' --data '{"hostname": "127.0.0.1", "reason": "test"}' http://localhost:8080/access-requests

Building
========

To build a deployable artifact, use the ``uberjar`` task, that produces a single JAR file, that every JVM should be able to execute.

.. code-block:: bash

    $ lein do uberjar, scm-source, docker build

Running
=======

Running the previously built Docker image and passing configuration via environment variables:

.. code-block:: bash

    $ docker run -p 8080:8080 -e AWS_REGION_ID=eu-west-1 -e CREDENTIALS_DIR=/ -e HTTP_TEAM_SERVICE_URL=https://teams.example.org -e HTTP_TOKENINFO_URL=https://oauth2.example.org/tokeninfo -e HTTP_ALLOWED_HOSTNAME_TEMPLATE="odd-[a-z0-9-]*.{team}.example.org" -e OAUTH2_ACCESS_TOKEN_URL=https://oauth2.example.org/access_token -e USERSVC_SSH_PUBLIC_KEY_URL_TEMPLATE=https://users.example.org/{user}/ssh -e SSH_PRIVATE_KEY="$SSH_PRIVATE_KEY" stups/even

All configuration values can be passed encrypted when running on AWS (this is supported by the underlying Friboo_ library):

.. code-block:: bash

    $ aws kms encrypt --key-id 123 --plaintext "secret" # encrypt with KMS
    $ export SSH_PRIVATE_KEY="aws:kms:<KMS-CIPHERTEXT-BLOB>"

Configuration
=============

The following configuration parameters can/should be passed via environment variables:

``AWS_REGION_ID``
    Optional AWS region ID to use for KMS decryption (e.g. "eu-west-1").
``CREDENTIALS_DIR``
    Folder with OAuth2 application credentials (user.json and client.json) --- this is automatically set when running on the Taupage AMI.
``HTTP_TEAM_SERVICE_URL``
    URL of the Team Service to check team membership for authorization.
``HTTP_TOKENINFO_URL``
    URL to OAuth2 token info endpoint.
``HTTP_ALLOWED_HOSTNAME_TEMPLATE``
    Regex template for the allowed hostname. "{team}" will be replaced by the user's team ID. Example: "odd-[a-z0-9-]*.{team}.example.org"
``OAUTH2_ACCESS_TOKEN_URL``
    URL to OAuth2 provider endpoint to get a new service access token.
``SSH_AGENT_FORWARDING``
    Boolean flag whether to use agent forwarding (``-A``). Agent forwarding is necessary for bastion host support.
``SSH_PORT``
    SSH port number to use (default: 22).
``SSH_PRIVATE_KEY``
    The SSH private key (can be encrypted with KMS).
``SSH_USER``
    The SSH username on remote servers (default: "granting-service").
``USERSVC_CACHE_BUCKET``
    Optional S3 bucket name to use for caching SSH public keys (to bridge potential downtimes of upstream HTTP service).
``USERSVC_SSH_PUBLIC_KEY_URL_TEMPLATE``
    URL template for the public SSH key endpoints ("{user}" will be replaced with the user's ID/username). Example: "https://users.example.org/employees/{user}/ssh"

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
