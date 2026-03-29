#!/bin/bash

docker buildx build -t mrhoeve/wrp:latest -t mrhoeve/wrp:1.8 --sbom=true --provenance=mode=max .
docker push mrhoeve/wrp:1.8
docker push mrhoeve/wrp:latest
