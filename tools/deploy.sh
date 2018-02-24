#!/bin/bash

############################################################
#
# This script deploys the Scorex program on docker containers
# derived from the daichi/scorex image.
#
############################################################

number_of_nodes=3
project_directory="/home/daichi/IdeaProjects/ScorexTestingMore/"
script_path="$project_directory"tools/client.py
window_size="44x12"

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
    docker run -itd --name bcclient daichi/scorex
    docker cp $script_path bcclient:/home/scorex
    gnome-terminal --geometry=$window_size -x bash -c "docker exec -it bcclient /bin/bash -c \"cd home/scorex; python3 client.py\""
fi

# Create blockchain nodes
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
        docker run -itd --name bcnode$i daichi/scorex
        docker cp "$project_directory"build.sbt bcnode$i:/home/scorex
        docker cp "$project_directory"project/build.properties bcnode$i:/home/scorex/project
        docker cp "$project_directory"src/main/resources/settings.json bcnode$i:/home/scorex/src/main/resources
        docker cp "$project_directory"src/main/scala/ bcnode$i:/home/scorex/src/main/
        gnome-terminal --geometry=$window_size -x bash -c "docker exec -it bcnode$i /bin/bash -c \"cd home/scorex; sed -i 's/x26HLcjg/bcnode$i/g' src/main/resources/settings.json; sbt run\""
    fi
done
