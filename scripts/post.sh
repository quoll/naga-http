#!/bin/sh
curl -v -X POST -d '[{"data1":"hello", "data2":2}, {"data1":"world", "data2":3}]' -H "Content-Type: application/json" http://localhost:3000/data
