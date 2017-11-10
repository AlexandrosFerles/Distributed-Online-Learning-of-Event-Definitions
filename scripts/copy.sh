#!/bin/bash

BASE="/home/nkatz/Label-caviar"

for outer_dir in `ls $BASE`; do
    echo $outer_dir
    for inner_dir in `ls $BASE/$outer_dir`; do
        dir=$BASE/$outer_dir/$inner_dir
        echo $dir
        cp "/home/nkatz/Documents/theoryCrossval.lp"  $dir/XHAIL-Jar/knowledge
        cp "/home/nkatz/Documents/theory"  $dir/XHAIL-Jar   
        #if [ $inner_dir = "meeting" ]
        #then
        #    cp "/home/nkatz/Desktop/modes/meeting-modes/modes" $dir/XHAIL-Jar/knowledge
        #fi

        #if [ $inner_dir = "moving" ]
        #then
        #   cp "/home/nkatz/Desktop/modes/moving-modes/modes" $dir/XHAIL-Jar/knowledge
        #fi

        #if [ $inner_dir = "fighting" ]
        #then
        #   cp "/home/nkatz/Desktop/modes/fighting-modes/modes" $dir/XHAIL-Jar/knowledge
        #fi
    done    
done
