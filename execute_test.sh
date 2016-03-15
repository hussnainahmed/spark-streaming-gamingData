#!/bin/bash

#NODE_LIST=`cat node_list |awk '{print $1}'`
#for f in $NODE_LIST
#do
#  echo "=============================================================="
#  echo ""
#  echo "executing $1 to $f"
#  ssh $f $1
#done

#/bin/sh /root/master/all-stop-slave.sh

#/bin/sync; /bin/echo 3 > /proc/sys/vm/drop_caches

#/bin/sh /root/execute_command_cluster '/bin/sync; /bin/echo 3 > /proc/sys/vm/drop_caches'


for ((a=$1; a <= $2; a++))
do
   echo "/bin/sh /root/master/start-slaves.sh  $a"
done
