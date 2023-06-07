#!/bin/bash
set -e

case "$1" in
	java17)
		echo "https://github.com/bell-sw/Liberica/releases/download/17.0.5+8/bellsoft-jdk17.0.5+8-linux-amd64.tar.gz"
	;;
	java18)
		echo "https://github.com/bell-sw/Liberica/releases/download/18.0.2.1%2B1/bellsoft-jdk18.0.2.1+1-linux-amd64.tar.gz"
	;;
	java19)
		echo "https://github.com/bell-sw/Liberica/releases/download/19.0.1%2B11/bellsoft-jdk19.0.1+11-linux-amd64.tar.gz"
	;;
	*)
		echo $"Unknown java version"
		exit 1
esac
