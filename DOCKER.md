# Docker Deployment Guide

This guide explains how to run multiple SPSC (Single Producer, Single Consumer) instances using Docker and Docker Compose.

## Overview

The docker-compose setup allows you to run multiple SPSC processor instances in parallel. Each instance:
- Streams from the **same CSV source** (`data/events.csv`)
- Maintains its **own bookmark** for independent processing
- Runs in an isolated container with separate bookmark storage

## Prerequisites

- Docker (version 20.10 or later)
- Docker Compose (version 1.29 or later)

## Quick Start

### 1. Build and Start All Instances

```bash
docker-compose up --build
```

This will:
- Build the Docker image
- Start 3 SPSC instances (`spsc-orders-1`, `spsc-orders-2`, `spsc-orders-3`)
- Each instance will process events from `data/events.csv`

### 2. View Logs

```bash
# View logs from all instances
docker-compose logs -f

# View logs from a specific instance
docker-compose logs -f spsc-orders-1
```

### 3. Stop All Instances

```bash
docker-compose down
```

## Architecture

### Container Layout

```
├── spsc-orders-1
│   ├── Reads: data/events.csv (shared, read-only)
│   └── Writes: bookmarks/spsc-orders-1/orders-processor-1.csv
├── spsc-orders-2
│   ├── Reads: data/events.csv (shared, read-only)
│   └── Writes: bookmarks/spsc-orders-2/orders-processor-2.csv
└── spsc-orders-3
    ├── Reads: data/events.csv (shared, read-only)
    └── Writes: bookmarks/spsc-orders-3/orders-processor-3.csv
```

### Key Features

- **Shared CSV Source**: All instances read from the same `data/events.csv` file (mounted read-only)
- **Independent Bookmarks**: Each instance has its own bookmark file in a separate directory
- **Parallel Processing**: Instances can process events independently and at their own pace
- **Bookmark Persistence**: Bookmarks are stored on the host in `bookmarks/` directory

## Configuration

### Environment Variables

Each SPSC instance can be configured using environment variables:

| Variable | Description | Default |
|----------|-------------|---------|
| `APP_SPSC_CSV_PATH` | Path to CSV events file | `/app/data/events.csv` |
| `APP_SPSC_BOOKMARKS_DIR` | Directory for bookmarks | `/app/bookmarks` |
| `APP_SPSC_BOOKMARK_NAME` | Bookmark filename | `orders-processor-{N}` |
| `APP_SPSC_PRODUCER_BATCH_SIZE` | Events fetched per batch | `10` |
| `APP_SPSC_CONSUMER_BATCH_SIZE` | Events consumed per batch | `5` |
| `APP_SPSC_MAX_QUEUE_DEPTH` | Internal queue size | `100` |
| `APP_SPSC_PRODUCER_EMPTY_BATCH_THRESHOLD` | Exit after N empty batches (0=indefinite) | `0` |
| `APP_SPSC_ENABLED` | Enable SPSC processing | `true` |

### Customizing Instances

To modify configuration for a specific instance, edit `docker-compose.yml`:

```yaml
services:
  spsc-orders-1:
    environment:
      APP_SPSC_PRODUCER_BATCH_SIZE: 20  # Increase batch size
      APP_SPSC_CONSUMER_BATCH_SIZE: 10
```

## Managing Instances

### Start Specific Instances

```bash
# Start only instance 1 and 2
docker-compose up spsc-orders-1 spsc-orders-2
```

### Scale Instances

To add more instances, edit `docker-compose.yml` and add a new service:

```yaml
  spsc-orders-4:
    build:
      context: .
      dockerfile: Dockerfile
    container_name: spsc-orders-4
    environment:
      SPRING_PROFILES_ACTIVE: worker
      SPRING_APPLICATION_NAME: spsc-orders-4
      APP_SPSC_CSV_PATH: /app/data/events.csv
      APP_SPSC_BOOKMARKS_DIR: /app/bookmarks
      APP_SPSC_BOOKMARK_NAME: orders-processor-4
      # ... other config
    volumes:
      - ./data/events.csv:/app/data/events.csv:ro
      - ./bookmarks/spsc-orders-4:/app/bookmarks
    restart: unless-stopped
    networks:
      - spsc-network
```

### Restart a Single Instance

```bash
docker-compose restart spsc-orders-1
```

### Remove and Rebuild

```bash
# Remove containers and rebuild
docker-compose down
docker-compose up --build
```

## Bookmark Management

### View Bookmark Status

Bookmarks are stored as CSV files in the `bookmarks/` directory:

```bash
# Check bookmark for instance 1
cat bookmarks/spsc-orders-1/orders-processor-1.csv

# Example output:
# POSITION,TIMESTAMP
# 42,1705059300000
```

### Reset a Bookmark

To reset an instance to start from the beginning:

```bash
# Remove the bookmark file
rm bookmarks/spsc-orders-1/orders-processor-1.csv

# Restart the instance
docker-compose restart spsc-orders-1
```

### Reset All Bookmarks

```bash
# Remove all bookmarks
rm -rf bookmarks/

# Restart all instances
docker-compose restart
```

## Monitoring

### Health Checks

Check if containers are running:

```bash
docker-compose ps
```

### Resource Usage

```bash
# View resource usage for all containers
docker stats $(docker-compose ps -q)
```

### Container Shell Access

```bash
# Access container shell
docker-compose exec spsc-orders-1 /bin/sh

# View files inside container
docker-compose exec spsc-orders-1 ls -la /app/bookmarks
```

## Troubleshooting

### Instance Not Processing Events

1. Check logs for errors:
   ```bash
   docker-compose logs spsc-orders-1
   ```

2. Verify CSV file is mounted:
   ```bash
   docker-compose exec spsc-orders-1 cat /app/data/events.csv
   ```

3. Check bookmark directory permissions:
   ```bash
   ls -la bookmarks/spsc-orders-1/
   ```

### Build Failures

```bash
# Clean rebuild
docker-compose down
docker-compose build --no-cache
docker-compose up
```

### Bookmark Corruption

If a bookmark file becomes corrupted:

```bash
# Remove corrupted bookmark
rm bookmarks/spsc-orders-1/orders-processor-1.csv

# Restart instance (will start from position 0)
docker-compose restart spsc-orders-1
```

## Future Enhancements

### Using SpscConfiguration and Spsc.Type

Currently, all instances use the same configuration. In the future, you can extend this to support multiple SPSC types:

```yaml
services:
  spsc-orders:
    environment:
      APP_SPSC_TYPE: ORDERS  # Future: Select config by type
      
  spsc-inventory:
    environment:
      APP_SPSC_TYPE: INVENTORY
      APP_SPSC_CSV_PATH: /app/data/inventory.csv
```

This would allow:
- Different configurations per SPSC type
- Multiple event sources
- Type-specific processing logic

## Production Considerations

### Resource Limits

Add resource limits in `docker-compose.yml`:

```yaml
services:
  spsc-orders-1:
    deploy:
      resources:
        limits:
          cpus: '0.5'
          memory: 512M
        reservations:
          memory: 256M
```

### Logging

Configure log rotation:

```yaml
services:
  spsc-orders-1:
    logging:
      driver: "json-file"
      options:
        max-size: "10m"
        max-file: "3"
```

### Networking

By default, all instances are on a bridge network. For production, consider using:
- Host network mode
- Custom networks with specific subnet configurations
- Network policies for security

## Cleanup

### Remove All Containers and Volumes

```bash
# Stop and remove containers, networks
docker-compose down

# Remove volumes (bookmarks will be lost)
docker-compose down -v
```

### Remove Docker Images

```bash
# List images
docker images | grep kotlin-scratch

# Remove image
docker rmi <image-id>
```
