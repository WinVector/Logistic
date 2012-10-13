#!/bin/bash

rm -f ../WinVectorLogistic.Hadoop0.20.2.jar

# get assets we want, to avoid duplicate entry errors due to matching directory names
SQLDIR=../../SQLScrewdriver
pushd $SQLDIR/bin
BINLIST=`find com -type f | sed "s,^,-C $SQLDIR/bin ,g"`
cd ..
SRCLIST=`find src -type f | sed "s,^,-C $SQLDIR ,g"`
popd


jar cmf MANIFEST.MF ../WinVectorLogistic.Hadoop0.20.2.jar -C ../bin com/winvector -C .. src/com/winvector -C ../../Colt-1.2.0/bin cern -C ../../Colt-1.2.0 src/cern $BINLIST $SRCLIST






