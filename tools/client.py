#!/usr/bin/python3

"""
client.py

This script provides an interactive shell to communicate with a Scorex node.
When a file name is given as a command line argument, this script reads the
file and executes the API line by line. See scorex_apis.txt for the available
APIs and the pydoc for the readfile function for the syntax.

Author: Daichi Mae
"""

from urllib.request import Request, urlopen
import json
from pprint import pprint
import random

def request(host, method, api, args, data=None):
    """
    Communicate with a node through an API.
    
    :param host (str): URL of a node to communicate with
    :param method (str): type of the HTTP request
    :param api (str): API to use
    :param args (list[str]): list of arguments for the API
    :param data (dict): data to submit
    :return (str): response from the node
    """
    url = host + api
    for arg in args:
        url += "/" + arg
    data = None if data is None else json.dumps(data).encode('utf-8')
    request = Request(url, data=data)
    request.get_method = lambda: method
    request.add_header("Accept", "application/json")

    with urlopen(request) as f:
        response = json.loads(f.read().decode('utf-8'))
    return response

def readfile(filename):
    """
    Read a text file and execute commands line by line. The first line
    specifies the IP address and the port number of a node (hostname:port)
    and the format of an API call is:
        method; api; arg1, arg2,...; key1:val1, key2:val2,...
    Empty lines and lines that begin with # will be ignored.

    :param filename: file name of a text file to read
    """
    with open(filename) as file:
        host = "http://" + file.readline().strip()
        
        for line in file:
            line = line.strip()

            # Skip empty lines and comment lines
            if len(line) == 0 or line[0] == "#":
                continue

            # Split the line by ";"
            items = line.split(";")
            items = list(map(str.strip, items))

            # Create a list of the arguments
            args = items[2]
            args = list(map(str.strip, args))
            args = [] if len(args) == 1 and args[0] == "" else args

            # Create a dictionary for the POST data
            data = dict()
            for pair in items[3]:
                key, value = pair.split(";")
                data[key.strip()] = value.strip()
            data = None if len(data) == 0 else data
                
            pprint(request(host, items[0], items[1], args, data))

def get_all_addresses(host):
    """
    Get the list of all addresses in the network.

    :param host: host to get the list of peers from
    """
    addresses = []
    
    for peer in request(host, "GET", "/peers/all", [])[0]:
        url = "http://" + peer["address"].split("/")[1].split(":")[0] + ":9085"
        addresses.append(request(url, "GET", "/addresses", [])[0])
        
    return addresses

def split_foundation_tokens(host):
    """
    Distribute the foundation tokens to other peers.

    :param host: host name of the founder node
    """
    # Get the balance of the foundation node.
    this_address = request(host, "GET", "/addresses", [])[0]
    balance = request(host, "GET", "/addresses/balance", [this_address])["balance"]

    # Calculate the amount of tokens to distribte to other nodes to even out
    # the tokens among the nodes
    addresses = get_all_addresses(host)
    amount = balance // len(addresses)

    # Send tokens to other nodes
    for address in addresses:
        if address != this_address:
            data = {"amount": amount,
                    "fee": 1,
                    "sender": this_address,
                    "recipient": address}
            pprint(request(host, "POST", "/payment", [], data))

def make_random_transactions(host, n, lower=1, upper=100, fee=1):
    """
    Make n random transactions. The nodes and the amount are randomly selected
    for each transactions.

    :param host: host to get the list of peers from
    :param n: number of transactions
    :param lower: lower bound of transaction amounts
    :param upper: upper bound of transaction amounts
    :prama fee: amount of transaction fees
    """
    addresses = get_all_addresses(host)

    for _ in range(n):
        sender = random.choice(addresses)
        recipient = sender
        while sender == recipient:
            recipient = random.choice(addresses)
        amount = random.randint(lower, upper)
        data = {"amount": amount,
                "fee": 1,
                "sender": sender,
                "recipient": recipient}
        pprint(request(host, "POST", "/payment", [], data))

def test():
    host = "http://localhost:9085"
    split_foundation_tokens(host)

def main():
    host = "http://172.17.0.3:9085"
    #host = "http://localhost:9085"
    command = [""]
    
    while True:
        command = input("> ").strip().split()

        if len(command) == 0:
            continue
        elif command[0] == "balance":
            if len(command) < 2:
                print("Error: No argument")
                continue
            pprint(request(host, "GET", "/addresses/balance", [command[1]]))
        elif command[0] == "addresses":
            pprint(request(host, "GET", "/addresses", []))
        elif command[0] == "alladdrs":
            print(get_all_addresses(host))
        elif command[0] == "genesis":
            pprint(request(host, "GET", "/blocks/first", []))
        elif command[0] == "blockat":
            if len(command) < 2:
                print("Error: No argument")
                continue
            pprint(request(host, "GET", "/blocks/at", [command[1]]))
        elif command[0] == "height":
            pprint(request(host, "GET", "/blocks/height", []))
        elif command[0] == "last":
            pprint(request(host, "GET", "/blocks/last", []))
        elif command[0] == "target":
            if len(command) == 1:
                pprint(request(host, "GET", "/consensus/basetarget", []))
            else:
                pprint(request(host, "GET", "/consensus/basetarget", [command[1]]))
        elif command[0] == "gensig":
            if len(command) == 1:
                pprint(request(host, "GET", "/consensus/generationsignature", []))
            else:
                pprint(request(host, "GET", "/consensus/generationsignature", [command[1]]))
        elif command[0] == "genbal":
            if len(command) < 2:
                print("Error: No argument")
                continue
            pprint(request(host, "GET", "/consensus/generatingbalance", [command[1]]))
        elif command[0] == "pay":
            if len(command) < 5:
                print("Error: No argument")
                continue
            data = {"amount": int(command[1]),
                    "fee": int(command[2]),
                    "sender": command[3],
                    "recipient": command[4]}
            pprint(request(host, "POST", "/payment", [], data))
        elif command[0] == "peers":
            pprint(request(host, "GET", "/peers/connected", []))
        elif command[0] == "allpeers":
            pprint(request(host, "GET", "/peers/all", []))
        elif command[0] == "tinfo":
            if len(command) < 2:
                print("Error: No argument")
                continue
            pprint(request(host, "GET", "/transactions/info", [command[1]]))
        elif command[0] == "tlist":
            if len(command) < 2:
                print("Error: No argument")
                continue
            pprint(request(host, "GET", "/transactions/address", [command[1]]))
        elif command[0] == "pending":
            pprint(request(host, "GET", "/transactions/unconfirmed", []))
        elif command[0] == "read":
            if len(command) < 2:
                print("Error: No argument")
                continue
            readfile(command[1])
        elif command[0] == "help":
            print("balance {address}")
            print("addresses")
            print("alladdrs")
            print("genesis")
            print("blockat {height}")
            print("height")
            print("last")
            print("target [height]")
            print("gensig [height]")
            print("genbal {address}")
            print("pay {amount} {fee} {sender} {recipient}")
            print("peers")
            print("allpeers")
            print("tinfo {transaction signature}")
            print("tlist {address}")
            print("pending")
            print("help")
            print("exit")
        elif command[0] == "exit":
            break
        elif command[0] == "test":
            split_foundation_tokens(host)
            make_random_transactions(host, 30, 1, 100)
        else:
            print("Invalid command: {0}".format(command[0]))
    
if __name__ == "__main__":
    main()
