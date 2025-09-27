# Simple HTTP Server Project

This project is a simple Java HTTP server that handles POST requests with JSON payloads.
It also includes a lightweight load testing client to simulate concurrent users.

## Project Overview

- **Server.java** – Creates an HTTP server on port 8080 with a POST endpoint at /api.
- **RequestHandler.java** – Processes incoming requests and validates message content.
- **LoadTestClient.java** – Performance testing client that simulates concurrent users.

## How to Run

### 1. Compile the project
### 2. Start the server  {Server.java}
### 3. Run the load test client {LoadTestClient.java}

##  Notes
```
The server runs on http://localhost:8080/api
Only POST requests with valid JSON payloads are accepted.
The LoadTestClient helps measure server performance under concurrent requests.
```