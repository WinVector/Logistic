#!/bin/bash

rm -f ../WinVectorLogistic.Hadoop0.20.2.jar

# get assets we want, to avoid duplicate entry errors due to matching directory names
pushd ../../SQLScrewdriver/bin
BINLIST=`find com -type f`
cd ..
SRCLIST=`find src -type f`
popd

jar cmvf MANIFEST.MF ../WinVectorLogistic.Hadoop0.20.2.jar -C ../bin com/winvector -C .. src/com/winvector -C ../../SQLScrewdriver/bin $BINLIST -C ../../SQLScrewdriver $SRCLIST -C ../../Colt-1.2.0/bin cern -C ../../Colt-1.2.0 src/cern 




