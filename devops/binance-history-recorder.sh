#! /bin/bash

ansible-playbook -v -i hosts jar-deploy.yaml \
  -e user_name=heurai \
  -e service_name=binance-history-recorder \
  -e jar_local_dir=../crypto/target/ \
  -e jar_local_filename=crypto-0.1.0-SNAPSHOT-standalone.jar \
  -e jar_args=config:binance-history-recorder \
  -e hosts=gdax