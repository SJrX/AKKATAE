#!/usr/bin/env bash
jps -vl | grep AkkaWorkerExecutor | cut -n -c 1-5 | xargs -L 1 kill -9 

