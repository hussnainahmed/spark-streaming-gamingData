#!/bin/bash

echo "/bin/sh /root/master/all-stop-slave.sh"
echo "/bin/sh /usr/local/spark/sbin/start-master.sh"

#/bin/sync; /bin/echo 3 > /proc/sys/vm/drop_caches

#/bin/sh /root/execute_command_cluster '/bin/sync; /bin/echo 3 > /proc/sys/vm/drop_caches'


for ((a=$1; a <= $2; a++))
do
   echo "/bin/sh /root/master/start-slaves.sh  $a"
done
