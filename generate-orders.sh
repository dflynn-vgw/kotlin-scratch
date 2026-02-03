#!/usr/bin/env bash
#
# Order Event Generator
# Simulates customer orders and appends lifecycle events to CSV file
#

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

# Default configuration
DEFAULT_CUSTOMER_MIN=1
DEFAULT_CUSTOMER_MAX=100
DEFAULT_ORDER_COUNT=10
DEFAULT_DELAY_MS=1000
DEFAULT_MODIFICATION_PROBABILITY=30  # 30% chance of modifications
DEFAULT_MAX_MODIFICATIONS=3
DEFAULT_CANCELLATION_PROBABILITY=20  # 20% chance of cancellation

# Global state
NEXT_ORDER_ID=1000
CSV_PATH=""
CUSTOMER_MIN=$DEFAULT_CUSTOMER_MIN
CUSTOMER_MAX=$DEFAULT_CUSTOMER_MAX
ORDER_COUNT=$DEFAULT_ORDER_COUNT
DELAY_MS=$DEFAULT_DELAY_MS
MODIFICATION_PROBABILITY=$DEFAULT_MODIFICATION_PROBABILITY
MAX_MODIFICATIONS=$DEFAULT_MAX_MODIFICATIONS
CANCELLATION_PROBABILITY=$DEFAULT_CANCELLATION_PROBABILITY

# Helper functions
info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

event() {
    echo -e "${CYAN}[EVENT]${NC} $1"
}

usage() {
    cat << EOF
Order Event Generator

Simulates customer orders and appends lifecycle events to a CSV file.
Each order follows the lifecycle: PLACED -> [MODIFIED*] -> CONFIRMED|CANCELLED

Usage: $0 <csv-path> [options]

Arguments:
  csv-path              Path to CSV file (required)

Options:
  --customers MIN MAX   Customer ID range (default: $DEFAULT_CUSTOMER_MIN-$DEFAULT_CUSTOMER_MAX)
  --count N             Number of orders to generate (default: $DEFAULT_ORDER_COUNT)
  --delay MS            Delay between events in milliseconds (default: ${DEFAULT_DELAY_MS}ms)
  --modify-prob PCT     Probability of order modifications 0-100 (default: ${DEFAULT_MODIFICATION_PROBABILITY}%)
  --max-mods N          Maximum modifications per order (default: $DEFAULT_MAX_MODIFICATIONS)
  --cancel-prob PCT     Probability of cancellation vs confirmation 0-100 (default: ${DEFAULT_CANCELLATION_PROBABILITY}%)
  --start-order-id N    Starting order ID (default: auto-detect from CSV)
  --continuous          Run continuously until interrupted
  --help, -h            Show this help message

Examples:
  # Generate 10 orders with default settings
  $0 ./data/events.csv

  # Generate 50 orders for customers 1-20 with 500ms delay
  $0 ./data/events.csv --customers 1 20 --count 50 --delay 500

  # Continuous generation with high modification rate
  $0 ./data/events.csv --continuous --modify-prob 80 --delay 2000

  # Generate orders with 50% cancellation rate
  $0 ./data/events.csv --count 20 --cancel-prob 50

EOF
}

# Parse arguments
parse_args() {
    if [ $# -eq 0 ]; then
        usage
        exit 1
    fi

    # First positional argument is CSV path
    CSV_PATH="$1"
    shift

    local continuous=false

    while [ $# -gt 0 ]; do
        case "$1" in
            --customers)
                CUSTOMER_MIN="$2"
                CUSTOMER_MAX="$3"
                shift 3
                ;;
            --count)
                ORDER_COUNT="$2"
                shift 2
                ;;
            --delay)
                DELAY_MS="$2"
                shift 2
                ;;
            --modify-prob)
                MODIFICATION_PROBABILITY="$2"
                shift 2
                ;;
            --max-mods)
                MAX_MODIFICATIONS="$2"
                shift 2
                ;;
            --cancel-prob)
                CANCELLATION_PROBABILITY="$2"
                shift 2
                ;;
            --start-order-id)
                NEXT_ORDER_ID="$2"
                shift 2
                ;;
            --continuous)
                continuous=true
                shift
                ;;
            --help|-h)
                usage
                exit 0
                ;;
            *)
                error "Unknown option: $1"
                usage
                exit 1
                ;;
        esac
    done

    # Validate CSV path
    if [ ! -f "$CSV_PATH" ]; then
        error "CSV file not found: $CSV_PATH"
        exit 1
    fi

    # Auto-detect next order ID if not specified
    if [ "$NEXT_ORDER_ID" -eq 1000 ]; then
        detect_next_order_id
    fi

    # Export continuous flag for later use
    export CONTINUOUS=$continuous
}

# Detect the next available order ID from CSV
detect_next_order_id() {
    local max_id=$(tail -n +2 "$CSV_PATH" 2>/dev/null | cut -d',' -f1 | sort -n | tail -1)
    if [ -n "$max_id" ] && [ "$max_id" -ge 1000 ]; then
        NEXT_ORDER_ID=$((max_id + 1))
    fi
    info "Starting from order ID: $NEXT_ORDER_ID"
}

# Get current timestamp in milliseconds
get_timestamp() {
    echo $(($(date +%s) * 1000))
}

# Generate random number between min and max (inclusive)
random_range() {
    local min=$1
    local max=$2
    echo $((min + RANDOM % (max - min + 1)))
}

# Generate random customer ID
random_customer() {
    local customer_num=$(random_range $CUSTOMER_MIN $CUSTOMER_MAX)
    printf "CUST%03d" $customer_num
}

# Check if event should occur based on probability (0-100)
should_occur() {
    local probability=$1
    local random=$((RANDOM % 100))
    [ $random -lt $probability ]
}

# Append event to CSV
append_event() {
    local order_id=$1
    local customer_id=$2
    local event_type=$3
    local timestamp=$4
    
    echo "$order_id,$customer_id,$event_type,$timestamp" >> "$CSV_PATH"
    event "$(printf '%-20s' "$event_type") | Order: $order_id | Customer: $customer_id | TS: $timestamp"
}

# Sleep with visual feedback
sleep_with_feedback() {
    local delay_seconds=$(echo "scale=3; $DELAY_MS / 1000" | bc)
    if (( $(echo "$delay_seconds >= 1" | bc -l) )); then
        printf "${BLUE}[WAIT]${NC} Sleeping %.2fs..." "$delay_seconds"
        sleep "$delay_seconds"
        echo -e "\r${BLUE}[WAIT]${NC} Sleeping %.2fs... done" "$delay_seconds"
    else
        sleep "$delay_seconds"
    fi
}

# Generate a single order lifecycle
generate_order_lifecycle() {
    local order_id=$1
    local customer_id=$(random_customer)
    local timestamp
    
    # 1. ORDER_PLACED
    timestamp=$(get_timestamp)
    append_event "$order_id" "$customer_id" "ORDER_PLACED" "$timestamp"
    sleep_with_feedback
    
    # 2. ORDER_MODIFIED (0 to MAX_MODIFICATIONS times)
    if should_occur $MODIFICATION_PROBABILITY; then
        local num_modifications=$(random_range 1 $MAX_MODIFICATIONS)
        for ((i=1; i<=num_modifications; i++)); do
            timestamp=$(get_timestamp)
            append_event "$order_id" "$customer_id" "ORDER_MODIFIED" "$timestamp"
            sleep_with_feedback
        done
    fi
    
    # 3. ORDER_CONFIRMED or ORDER_CANCELLED
    timestamp=$(get_timestamp)
    if should_occur $CANCELLATION_PROBABILITY; then
        append_event "$order_id" "$customer_id" "ORDER_CANCELLED" "$timestamp"
    else
        append_event "$order_id" "$customer_id" "ORDER_CONFIRMED" "$timestamp"
    fi
    sleep_with_feedback
}

# Main execution
main() {
    parse_args "$@"
    
    info "Order Event Generator Configuration:"
    echo "  CSV Path:              $CSV_PATH"
    echo "  Customer Range:        CUST$(printf '%03d' $CUSTOMER_MIN) - CUST$(printf '%03d' $CUSTOMER_MAX)"
    echo "  Order Count:           $ORDER_COUNT"
    echo "  Event Delay:           ${DELAY_MS}ms"
    echo "  Modification Prob:     ${MODIFICATION_PROBABILITY}%"
    echo "  Max Modifications:     $MAX_MODIFICATIONS"
    echo "  Cancellation Prob:     ${CANCELLATION_PROBABILITY}%"
    echo "  Starting Order ID:     $NEXT_ORDER_ID"
    echo "  Continuous Mode:       $CONTINUOUS"
    echo ""
    
    if [ "$CONTINUOUS" = true ]; then
        info "Running in continuous mode. Press Ctrl+C to stop."
        trap 'echo ""; info "Stopping..."; exit 0' INT TERM
        
        local count=0
        while true; do
            count=$((count + 1))
            info "Generating order #$count (ID: $NEXT_ORDER_ID)..."
            generate_order_lifecycle $NEXT_ORDER_ID
            NEXT_ORDER_ID=$((NEXT_ORDER_ID + 1))
            echo ""
        done
    else
        info "Generating $ORDER_COUNT orders..."
        echo ""
        
        for ((i=1; i<=ORDER_COUNT; i++)); do
            info "Generating order $i/$ORDER_COUNT (ID: $NEXT_ORDER_ID)..."
            generate_order_lifecycle $NEXT_ORDER_ID
            NEXT_ORDER_ID=$((NEXT_ORDER_ID + 1))
            echo ""
        done
        
        success "Generated $ORDER_COUNT orders successfully!"
    fi
    
    info "Total events in CSV: $(tail -n +2 "$CSV_PATH" | wc -l | tr -d ' ')"
}

main "$@"
