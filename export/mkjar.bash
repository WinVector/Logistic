#!/bin/bash

rm -f ../WinVectorLogistic.Hadoop0.20.2.jar

# get assets we want, to avoid duplicate entry errors due to matching directory names
pushd ../../SQLScrewdriver/bin
BINLIST=`find com -type f`
cd ..
SRCLIST=`find src -type f`
popd


jar cmvf MANIFEST.MF ../WinVectorLogistic.Hadoop0.20.2.jar -C ../bin com/winvector -C .. src/com/winvector -C ../../Colt-1.2.0/bin cern -C ../../Colt-1.2.0 src/cern 

# no idea why jar util can't take a list
for fi in $BINLIST
do
jar uvf ../WinVectorLogistic.Hadoop0.20.2.jar -C ../../SQLScrewdriver/bin $fi
done

for fi in $SRCLIST
do
jar uvf ../WinVectorLogistic.Hadoop0.20.2.jar -C ../../SQLScrewdriver $fi
done





