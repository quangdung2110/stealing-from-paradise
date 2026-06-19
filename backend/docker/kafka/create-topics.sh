#!/bin/bash
set -e

BROKER="kafka:9092"

create_topic() {
  local topic=$1
  local partitions=${2:-3}
  local replication=${3:-1}

  echo "Creating topic: $topic (partitions=$partitions, replication=$replication)"
  kafka-topics --bootstrap-server "$BROKER" \
    --create \
    --if-not-exists \
    --topic "$topic" \
    --partitions "$partitions" \
    --replication-factor "$replication"
}

echo "=== Waiting for Kafka broker to be ready ==="
until kafka-topics --bootstrap-server "$BROKER" --list > /dev/null 2>&1; do
  echo "Kafka not ready yet, retrying in 3s..."
  sleep 3
done
echo "Kafka is ready."

echo ""
echo "=== Product topics ==="
create_topic "product.pending_review"
create_topic "product.approved"
create_topic "product.activated"
create_topic "product.deactivated"
create_topic "product.rejected"
create_topic "product.updated"
create_topic "product.deleted"
create_topic "variant.price_updated"
create_topic "variant.stock_updated"
create_topic "category.updated"

echo ""
echo "=== Review topics ==="
create_topic "review.created"
create_topic "review.updated"
create_topic "review.deleted"
create_topic "review.summary_updated"

echo ""
echo "=== Order topics ==="
create_topic "order.created"
create_topic "order.shipped"
create_topic "order.delivered"
create_topic "order.returned"
create_topic "order.cancelled"
create_topic "order.auto_cancelled"
create_topic "order.checkout_completed"
create_topic "seller.order_cancelled"

echo ""
echo "=== Payment topics ==="
create_topic "payment.requested"
create_topic "payment.success"
create_topic "payment.failed"
create_topic "stripe.account_suspended"
create_topic "stripe.dispute.created"
create_topic "stripe.dispute.closed"
create_topic "stripe.transfer.reversed"
create_topic "stripe.payout.failed"
create_topic "seller.stripe_requirement"

echo ""
echo "=== Refund topics ==="
create_topic "refund.requested"
create_topic "refund.full_requested"
create_topic "refund.created"
create_topic "refund.admin_approved"
create_topic "refund.rejected"
create_topic "refund.rts_completed"
create_topic "refund.stripe_auto"

echo ""
echo "=== Flash Sale topics ==="
create_topic "flash_sale.session_started"
create_topic "flash_sale.session_ended"
create_topic "flash_sale.item_approved"
create_topic "flash_sale.item_rejected"
create_topic "flash_sale.item_sold"
create_topic "flash_sale.reminder"
create_topic "flash_sale.price_sync"

echo ""
echo "=== Request-Reply topics (Cart <-> Product) ==="
create_topic "cart.product_info.request"
create_topic "cart.product_info.response"

echo ""
echo "=== Request-Reply topics (Order <-> Product) ==="
create_topic "order.stock_check.request"
create_topic "order.stock_check.response"

echo ""
echo "=== Request-Reply topics (Order <-> Payment) ==="
create_topic "order.payment_status.request"
create_topic "order.payment_status.response"

echo ""
echo "=== Request-Reply topics (Order <-> Cart) ==="
create_topic "order.cart_items.request"
create_topic "order.cart_items.response"

echo ""
echo "=== Request-Reply topics (Order <-> Identity) ==="
create_topic "order.address.request"
create_topic "order.address.response"
create_topic "order.refunds.request"
create_topic "order.refunds.response"

echo ""
echo "=== Request-Reply topics (Order <-> Refund) ==="
create_topic "order.refund_presigned_url.request"
create_topic "order.refund_presigned_url.response"
create_topic "order.payment_status.request"
create_topic "order.payment_status.response"

echo ""
echo "=== Request-Reply topics (Search <-> Product) ==="
create_topic "search.index_data.request"
create_topic "search.index_data.response"

echo ""
echo "=== All Kafka topics created successfully ==="
kafka-topics --bootstrap-server "$BROKER" --list
