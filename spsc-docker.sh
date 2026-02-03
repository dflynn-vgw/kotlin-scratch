#!/usr/bin/env bash
#
# SPSC Docker Management Script
# Helper script to manage SPSC docker-compose instances
#

set -e

COMPOSE_FILE="docker-compose.yml"
BOOKMARKS_DIR="bookmarks"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

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

# Command functions
cmd_build() {
    info "Building Docker images..."
    docker-compose build "$@"
    success "Build complete"
}

cmd_up() {
    info "Starting SPSC instances..."
    docker-compose up -d "$@"
    success "SPSC instances started"
    info "Use './spsc-docker.sh logs' to view logs"
}

cmd_down() {
    info "Stopping SPSC instances..."
    docker-compose down "$@"
    success "SPSC instances stopped"
}

cmd_restart() {
    info "Restarting SPSC instances..."
    docker-compose restart "$@"
    success "SPSC instances restarted"
}

cmd_logs() {
    docker-compose logs -f "$@"
}

cmd_ps() {
    docker-compose ps "$@"
}

cmd_stats() {
    info "Container resource usage:"
    docker stats $(docker-compose ps -q)
}

cmd_bookmarks_list() {
    info "Current bookmark status:"
    echo ""
    
    if [ ! -d "$BOOKMARKS_DIR" ]; then
        warning "No bookmarks directory found"
        return
    fi
    
    for dir in "$BOOKMARKS_DIR"/*; do
        if [ -d "$dir" ]; then
            instance=$(basename "$dir")
            echo -e "${GREEN}Instance: $instance${NC}"
            
            for bookmark in "$dir"/*.csv; do
                if [ -f "$bookmark" ]; then
                    bookmark_name=$(basename "$bookmark" .csv)
                    # Read position from CSV (2nd line, 1st column)
                    position=$(sed -n '2p' "$bookmark" | cut -d',' -f1)
                    timestamp=$(sed -n '2p' "$bookmark" | cut -d',' -f2)
                    
                    echo "  Bookmark: $bookmark_name"
                    echo "    Position: $position"
                    echo "    Timestamp: $timestamp"
                fi
            done
            echo ""
        fi
    done
}

cmd_bookmarks_reset() {
    if [ -z "$1" ]; then
        warning "Resetting ALL bookmarks..."
        read -p "Are you sure? (y/N) " -n 1 -r
        echo
        if [[ ! $REPLY =~ ^[Yy]$ ]]; then
            info "Reset cancelled"
            return
        fi
        
        info "Removing all bookmarks..."
        rm -rf "$BOOKMARKS_DIR"
        success "All bookmarks reset"
        
        info "Restarting all instances..."
        docker-compose restart
    else
        instance="$1"
        warning "Resetting bookmark for instance: $instance"
        read -p "Are you sure? (y/N) " -n 1 -r
        echo
        if [[ ! $REPLY =~ ^[Yy]$ ]]; then
            info "Reset cancelled"
            return
        fi
        
        info "Removing bookmark for $instance..."
        rm -rf "$BOOKMARKS_DIR/$instance"
        success "Bookmark reset for $instance"
        
        info "Restarting instance $instance..."
        docker-compose restart "$instance"
    fi
}

cmd_shell() {
    instance="${1:-spsc-orders-1}"
    info "Opening shell in $instance..."
    docker-compose exec "$instance" /bin/sh
}

cmd_clean() {
    warning "This will remove all containers, images, and bookmarks!"
    read -p "Are you sure? (y/N) " -n 1 -r
    echo
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        info "Clean cancelled"
        return
    fi
    
    info "Stopping containers..."
    docker-compose down
    
    info "Removing bookmarks..."
    rm -rf "$BOOKMARKS_DIR"
    
    info "Removing images..."
    docker-compose down --rmi all
    
    success "Clean complete"
}

cmd_help() {
    cat << EOF
SPSC Docker Management Script

Usage: ./spsc-docker.sh <command> [options]

Commands:
  build              Build Docker images
  up                 Start all SPSC instances (detached)
  down               Stop all SPSC instances
  restart [instance] Restart all or specific instance
  logs [instance]    View logs (follow mode)
  ps                 Show container status
  stats              Show container resource usage
  
  bookmarks:list     List all bookmarks with positions
  bookmarks:reset [instance]  Reset bookmarks (all or specific instance)
  
  shell [instance]   Open shell in container (default: spsc-orders-1)
  clean              Remove all containers, images, and bookmarks
  
  help               Show this help message

Examples:
  # Build and start all instances
  ./spsc-docker.sh build
  ./spsc-docker.sh up
  
  # View logs for specific instance
  ./spsc-docker.sh logs spsc-orders-1
  
  # Check bookmark status
  ./spsc-docker.sh bookmarks:list
  
  # Reset specific instance
  ./spsc-docker.sh bookmarks:reset spsc-orders-1
  
  # Access container shell
  ./spsc-docker.sh shell spsc-orders-2

EOF
}

# Main script logic
main() {
    case "${1:-help}" in
        build)
            shift
            cmd_build "$@"
            ;;
        up)
            shift
            cmd_up "$@"
            ;;
        down)
            shift
            cmd_down "$@"
            ;;
        restart)
            shift
            cmd_restart "$@"
            ;;
        logs)
            shift
            cmd_logs "$@"
            ;;
        ps)
            shift
            cmd_ps "$@"
            ;;
        stats)
            cmd_stats
            ;;
        bookmarks:list)
            cmd_bookmarks_list
            ;;
        bookmarks:reset)
            shift
            cmd_bookmarks_reset "$@"
            ;;
        shell)
            shift
            cmd_shell "$@"
            ;;
        clean)
            cmd_clean
            ;;
        help|--help|-h)
            cmd_help
            ;;
        *)
            error "Unknown command: $1"
            echo ""
            cmd_help
            exit 1
            ;;
    esac
}

main "$@"
