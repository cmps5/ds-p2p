#!/bin/bash

if [ "$#" -lt 2 ] || [ $(( $# % 2 )) -ne 0 ]; then
    echo "Usage: $0 <host> <port> [<neighborHost> <neighborPort>...]"
    exit 1
fi

java -cp .:poisson/src ds.assign.p2p.Peer "$@"
