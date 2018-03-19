#!/bin/bash

###############################################################################
#
# This script is used for the CS Docker cluster and deploys the Scorex program
# on Docker containers derived from the dm6249/scorex image. Each node connects
# to the next node and the last node connects to the first node.
#
###############################################################################

number_of_nodes=5

# Function that calculates the next IP address.
nextip(){
    ip=$1
    ip_hex=$(printf '%.2X%.2X%.2X%.2X\n' `echo $ip | sed -e 's/\./ /g'`)
    next_ip_hex=$(printf %.8X `echo $(( 0x$ip_hex + 1 ))`)
    next_ip=$(printf '%d.%d.%d.%d\n' `echo $next_ip_hex | sed -r 's/(..)/0x\1 /g'`)
    echo "$next_ip"
}

# Get the latest code.
cd ../consensus-algorithms
git pull

# Create blockchain nodes.
for i in `seq 1 $number_of_nodes`;
do
    # Stop the node if it's running.
    if [ "$(docker ps -aq -f status=running -f name=bcnode$i)" ]; then
        docker stop bcnode$i
    fi
    if [ ! "$(docker ps -q -f name=bcnode$i)" ]; then
        # Remove the container if it already exists.
        if [ "$(docker ps -aq -f status=exited -f name=bcnode$i)" ]; then
            docker rm bcnode$i
        fi
        # Create a container and run it.
        docker run -itd --name bcnode$i dm6249/scorex
        ip=$(docker inspect -f "{{ .NetworkSettings.IPAddress }}" bcnode$i)
        if [ $i -eq 1 ]; then
            first_ip=$ip
        fi
        if [ $i -eq $number_of_nodes ]; then
            next_ip=$first_ip
            else
            next_ip=$(nextip $ip)
        fi
        docker cp ./build.sbt bcnode$i:/home/scorex
        docker cp ./project/build.properties bcnode$i:/home/scorex/project
        docker cp ./src/main/resources/settings.json bcnode$i:/home/scorex/src/main/resources
        docker cp ./src/main/scala/ bcnode$i:/home/scorex/src/main/
        docker exec -d bcnode$i /bin/bash -c "cd home/scorex;
            sed -i 's/NODE_NAME/bcnode$i/g' src/main/resources/settings.json;
            sed -i 's/MY_ADDRESS/$ip/g' src/main/resources/settings.json;
            sed -i 's/KNOWN_IP/$next_ip/g' src/main/resources/settings.json;
            sbt run"
    fi
done

# Create a client to communicate with the nodes
if [ "$(docker ps -aq -f status=running -f name=bcclient)" ]; then
    docker stop bcclient
fi
if [ ! "$(docker ps -q -f name=bcclient)" ]; then
    # Remove the container if it already exists.
    if [ "$(docker ps -aq -f status=exited -f name=bcclient)" ]; then
        docker rm bcclient
    fi
    # Create a container and run it.
    docker run -itd --name bcclient dm6249/scorex
    docker cp ./tools/client.py bcclient:/home/scorex
    docker exec -it bcclient /bin/bash -c "cd home/scorex; python3 client.py; bash"
fi

# Collect the log files and stop the containers.
for i in `seq 1 $number_of_nodes`;
do
    docker cp bcnode$i:/home/scorex/scorex.log ../data/node$i.log
    docker cp bcnode$i:/home/scorex/scorex-errors.log ../data/node$i-errors.log
    docker stop bcnode$i
done
docker stop bcclient
