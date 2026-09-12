#!/bin/bash
logcat -d | grep -i -E "fatal|exception|error" | tail -n 100
