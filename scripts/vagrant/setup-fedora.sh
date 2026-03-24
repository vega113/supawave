#!/usr/bin/env bash

# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
#
#   http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied.  See the License for the
# specific language governing permissions and limitations
# under the License.

# install the dependencies
# Dependencies (Java 17; MongoDB optional)
dnf install -y java-17-openjdk mongodb || dnf install -y java-17-openjdk || true
# create install location
cd /opt
sudo mkdir apache
cd apache
sudo mkdir wave
# install SBT
curl -L https://www.scala-sbt.org/sbt-rpm.repo | sudo tee /etc/yum.repos.d/sbt-rpm.repo > /dev/null
dnf install -y sbt || true

# create the binary
cd /vagrant || exit 1
sbt --batch Universal/stage

# Get Apache Wave version
WAVE_INSTALL="target/universal/stage"
sudo mkdir -p /opt/apache/wave/wave
sudo cp -R "$WAVE_INSTALL"/. /opt/apache/wave/wave/
cd ..
cp scripts/vagrant/application.conf /opt/apache/wave/wave/config/application.conf
