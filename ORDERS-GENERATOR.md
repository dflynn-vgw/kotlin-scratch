# Order Event Generator

This script simulates realistic customer order lifecycles and appends events to your CSV file in real-time. Your SPSC processors will detect and stream these new events as they're added.

## Order Lifecycle

Each generated order follows this lifecycle:

```
ORDER_PLACED → [ORDER_MODIFIED]* → ORDER_CONFIRMED | ORDER_CANCELLED
```

- **ORDER_PLACED**: Always the first event (required)
- **ORDER_MODIFIED**: Zero or more modifications (probability-based)
- **ORDER_CONFIRMED** or **ORDER_CANCELLED**: Final state (probability-based)

## Basic Usage

```bash
# Generate 10 orders with default settings
./generate-orders.sh ./data/events.csv

# Generate 50 orders with custom delay
./generate-orders.sh ./data/events.csv --count 50 --delay 500

# Run continuously (useful for testing)
./generate-orders.sh ./data/events.csv --continuous
```

## Options

| Option | Description | Default |
|--------|-------------|---------|
| `--customers MIN MAX` | Customer ID range | `1-100` |
| `--count N` | Number of orders to generate | `10` |
| `--delay MS` | Delay between events (milliseconds) | `1000` |
| `--modify-prob PCT` | Probability of modifications (0-100) | `30%` |
| `--max-mods N` | Maximum modifications per order | `3` |
| `--cancel-prob PCT` | Probability of cancellation (0-100) | `20%` |
| `--start-order-id N` | Starting order ID | Auto-detect |
| `--continuous` | Run until interrupted (Ctrl+C) | Off |
| `--help, -h` | Show help message | - |

## Examples

### Example 1: Quick Test (Fast Events)

Generate 20 orders with 100ms delay between events:

```bash
./generate-orders.sh ./data/events.csv --count 20 --delay 100
```

### Example 2: Specific Customer Range

Generate orders only for customers 1-10:

```bash
./generate-orders.sh ./data/events.csv --customers 1 10 --count 30
```

### Example 3: High Modification Rate

Generate orders with 80% chance of modifications:

```bash
./generate-orders.sh ./data/events.csv --modify-prob 80 --max-mods 5 --delay 500
```

### Example 4: High Cancellation Rate

Generate orders with 50% cancellation rate:

```bash
./generate-orders.sh ./data/events.csv --cancel-prob 50 --count 25
```

### Example 5: Continuous Generation

Run continuously for testing your SPSC processors:

```bash
./generate-orders.sh ./data/events.csv --continuous --delay 2000
```

Press `Ctrl+C` to stop.

### Example 6: Realistic Production Simulation

Simulate production-like traffic with moderate modifications and cancellations:

```bash
./generate-orders.sh ./data/events.csv \
  --continuous \
  --customers 1 500 \
  --delay 1500 \
  --modify-prob 40 \
  --cancel-prob 15
```

## Testing with Docker

### Start Your SPSC Processors

```bash
# Build and start 3 SPSC instances
./spsc-docker.sh build
./spsc-docker.sh up

# View logs
./spsc-docker.sh logs
```

### Generate Events While Processors Run

In another terminal:

```bash
# Generate orders continuously
./generate-orders.sh ./data/events.csv --continuous --delay 1000
```

### Monitor Processing

Watch your SPSC processors consume events in real-time:

```bash
# Watch logs from all instances
docker-compose logs -f

# Watch specific instance
docker-compose logs -f spsc-orders-1

# Check bookmark positions
./spsc-docker.sh bookmarks:list
```

## Sample Output

```
[INFO] Order Event Generator Configuration:
  CSV Path:              ./data/events.csv
  Customer Range:        CUST001 - CUST100
  Order Count:           10
  Event Delay:           1000ms
  Modification Prob:     30%
  Max Modifications:     3
  Cancellation Prob:     20%
  Starting Order ID:     1006
  Continuous Mode:       false

[INFO] Generating 10 orders...

[INFO] Generating order 1/10 (ID: 1006)...
[EVENT] ORDER_PLACED         | Order: 1006 | Customer: CUST042 | TS: 1738565234000
[EVENT] ORDER_MODIFIED       | Order: 1006 | Customer: CUST042 | TS: 1738565235000
[EVENT] ORDER_CONFIRMED      | Order: 1006 | Customer: CUST042 | TS: 1738565236000

[INFO] Generating order 2/10 (ID: 1007)...
[EVENT] ORDER_PLACED         | Order: 1007 | Customer: CUST015 | TS: 1738565237000
[EVENT] ORDER_CANCELLED      | Order: 1007 | Customer: CUST015 | TS: 1738565238000

...

[SUCCESS] Generated 10 orders successfully!
[INFO] Total events in CSV: 25
```

## How It Works

1. **Auto-detection**: Script automatically detects the next order ID from your CSV
2. **Append-only**: Events are appended to the CSV file (never overwrites)
3. **Real-time**: SPSC processors poll the CSV and detect new events via their bookmarks
4. **Realistic**: Follows actual order lifecycle patterns with configurable probabilities

## Integration with SPSC

The generator works seamlessly with your SPSC processors:

1. **CSV Appending**: New events are appended to the CSV file
2. **Polling Detection**: SPSC processors poll the CSV at regular intervals
3. **Bookmark Tracking**: Each processor tracks its position independently
4. **Parallel Processing**: Multiple SPSC instances can process the same events

### Processing Flow

```
generate-orders.sh → appends to events.csv
                              ↓
                     SPSC polls CSV file
                              ↓
                     Detects new events after bookmark
                              ↓
                     Processes events in batches
                              ↓
                     Updates bookmark position
```

## Advanced Usage

### Custom Starting Point

Start from a specific order ID:

```bash
./generate-orders.sh ./data/events.csv --start-order-id 5000 --count 100
```

### Stress Testing

Generate high-volume events quickly:

```bash
./generate-orders.sh ./data/events.csv \
  --continuous \
  --delay 50 \
  --customers 1 1000 \
  --modify-prob 50
```

### No Modifications

Generate simple order flow (PLACED → CONFIRMED/CANCELLED):

```bash
./generate-orders.sh ./data/events.csv \
  --modify-prob 0 \
  --count 50
```

### All Orders Cancelled

Test cancellation handling:

```bash
./generate-orders.sh ./data/events.csv \
  --cancel-prob 100 \
  --count 20
```

## Tips

- **Delay Tuning**: Lower delays (100-500ms) for stress testing, higher delays (1-5s) for realistic simulation
- **Customer Range**: Narrow range (1-10) creates more events per customer, wide range (1-1000) spreads events
- **Continuous Mode**: Great for long-running tests; use with moderate delay (1-2s) to avoid overwhelming the system
- **Bookmark Monitoring**: Use `./spsc-docker.sh bookmarks:list` to verify processors are keeping up

## Troubleshooting

### Events Not Being Processed

Check if SPSC processors are running:
```bash
./spsc-docker.sh ps
```

Check logs for errors:
```bash
./spsc-docker.sh logs
```

### Orders Starting from Wrong ID

Manually specify starting ID:
```bash
./generate-orders.sh ./data/events.csv --start-order-id 2000
```

### Script Won't Stop in Continuous Mode

Press `Ctrl+C` to gracefully stop the generator.

## CSV Format

The script appends events in this format:

```csv
ORDER_ID,CUSTOMER_ID,EVENT_TYPE,TIMESTAMP
1006,CUST042,ORDER_PLACED,1738565234000
1006,CUST042,ORDER_MODIFIED,1738565235000
1006,CUST042,ORDER_CONFIRMED,1738565236000
```

This matches your existing CSV structure and event types.
