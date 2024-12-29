#!/bin/bash

docker buildx build -t mrhoeve/wrp:latest -t mrhoeve/wrp:1.6 --sbom=true --provenance=mode=max .
docker push mrhoeve/wrp:1.6
docker push mrhoeve/wrp:latest
