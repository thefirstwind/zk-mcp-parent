#!/bin/bash

# Base URL
BASE_URL="http://localhost:9091"
VIRTUAL_PROJECT="virtual-d10"

echo "Testing SSE connection to $BASE_URL/sse/$VIRTUAL_PROJECT..."

# Start SSE connection in background and capture output
curl -N -H "Accept: text/event-stream" "$BASE_URL/sse/$VIRTUAL_PROJECT" > sse_output.txt 2>&1 &
PID=$!

echo "SSE client started with PID $PID. Waiting for endpoint event..."
sleep 2

# Extract endpoint from sse_output.txt
# Expected format: event: endpoint\ndata: http://...\n\n
ENDPOINT_URL=$(grep "data:http" sse_output.txt | sed 's/data:http/http/')

if [ -z "$ENDPOINT_URL" ]; then
    echo "Failed to get endpoint URL from SSE stream."
    cat sse_output.txt
    kill $PID
    exit 1
fi

echo "Got endpoint URL: $ENDPOINT_URL"

# Test POST to endpoint (initialize)
echo "Sending initialize request..."
curl -v -X POST -H "Content-Type: application/json" -H "Accept: application/json" \
     -d '{"jsonrpc":"2.0","method":"initialize","id":1}' \
     "$ENDPOINT_URL"

echo ""
echo "Sending tools/list request..."
curl -v -X POST -H "Content-Type: application/json" -H "Accept: application/json" \
     -d '{"jsonrpc":"2.0","method":"tools/list","id":2}' \
     "$ENDPOINT_URL"

# Clean up
kill $PID
rm sse_output.txt
