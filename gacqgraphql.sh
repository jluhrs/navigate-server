#!/bin/bash

# Default values
HOST="localhost" # navigate host
PORT="9070"
P_ARCSEC="0.1" # P offset in arcseconds
Q_ARCSEC="0.1" # Q offset in arcseconds
IPA_DEGREES="0.10" # IPA in degrees
IAA_DEGREES="0.10" # IAA in degrees
COMMAND="ASK_USER"

# Help message
function show_help {
  echo "Usage: $0 [options]"
  echo "Options:"
  echo "  -h, --host HOST       Host address (default: localhost)"
  echo "  -p, --port PORT       Port number (default: 9070)"
  echo "  -P, --p-arcsec VALUE  P offset in arcseconds (default: 0.1)"
  echo "  -Q, --q-arcsec VALUE  Q offset in arcseconds (default: 0.1)"
  echo "  -i, --ipa-ms VALUE    IPA in degrees (default: 0.10)"
  echo "  -a, --iaa-ms VALUE    IAA in degrees (default: 0.10)"
  echo "  -c, --command CMD     Command (default: USER_CONFIRMS)"
  echo "  --help                Show this help message"
  exit 0
}

# Parse command line arguments
while [[ $# -gt 0 ]]; do
  case "$1" in
    -h|--host)
      HOST="$2"
      shift 2
      ;;
    -p|--port)
      PORT="$2"
      shift 2
      ;;
    -P|--p-arcsec)
      P_ARCSEC="$2"
      shift 2
      ;;
    -Q|--q-arcsec)
      Q_ARCSEC="$2"
      shift 2
      ;;
    -i|--ipa-ms)
      IPA_DEGREES="$2"
      shift 2
      ;;
    -a|--iaa-ms)
      IAA_DEGREES="$2"
      shift 2
      ;;
    -c|--command)
      COMMAND="$2"
      shift 2
      ;;
    --help)
      show_help
      ;;
    *)
      echo "Unknown option: $1"
      show_help
      ;;
  esac
done

# Construct the GraphQL query with the provided parameters
QUERY="
mutation {
  acquisitionAdjustment(adjustment: {
    offset: {
      p: {arcseconds: $P_ARCSEC},
      q: {arcseconds: $Q_ARCSEC}
    },
    ipa: {degrees: $IPA_DEGREES},
    iaa: {degrees: $IAA_DEGREES},
    command: $COMMAND
  }) {
    result
  }
}"

# Escape newlines and quotes for JSON
ESCAPED_QUERY=$(echo "$QUERY" | tr -d '\n' | sed 's/"/\\"/g')
echo "Query: $ESCAPED_QUERY"

curl "http://${HOST}:${PORT}/navigate/graphql" -v \
  -H "Content-Type: application/json" \
  --data-raw "{\"operationName\":null,\"variables\":{},\"query\":\"$ESCAPED_QUERY\"}"
