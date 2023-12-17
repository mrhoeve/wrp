#!/bin/bash

docker build -t mrhoeve/wrp:latest -t mrhoeve/wrp:1.3 .
docker push mrhoeve/wrp:1.3
docker push mrhoeve/wrp:latest
