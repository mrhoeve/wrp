#!/bin/bash

docker build -t mrhoeve/wrp .
docker image push mrhoeve/wrp:latest
