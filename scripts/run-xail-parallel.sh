#!/bin/bash

BASE="/home/nkatz/Label-caviar"

for DB_NAME in `ls $BASE`; do
    echo $DB_NAME
    for inner_dir in `ls $BASE/$DB_NAME`; do
        dir=$BASE/$DB_NAME/$inner_dir
        echo $dir
        cd $dir/XHAIL-Jar
        java -cp $dir/XHAIL-Jar/Xhail.jar testing.GeneralTest $DB_NAME > $dir/XHAIL-Jar/xhail-results.txt & # the "&" is what starts the processes concurently
    done    
done
