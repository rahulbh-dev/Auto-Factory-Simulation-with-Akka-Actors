// Rahul Bhardwaj, 237868
package com.myauto.actors;

import akka.actor.typed.*;
import akka.actor.typed.javadsl.*;
import java.util.List;
import java.time.Duration;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicInteger;

public class OrderBook extends AbstractBehavior<OrderBook.Command> {

    // Interface for messages this actor can handle
    public interface Command {}

    // Represents a new order coming into the system
    public static class NewOrder implements Command {
        public final int orderId;
        public NewOrder(int orderId) {
            this.orderId = orderId;
        }
    }

    // Periodic check to try and assign waiting orders to production lines
    public static class TryAssignOrder implements Command {}

    // Actor creation method, gets called when OrderBook is spawned
    public static Behavior<Command> create(List<ActorRef<ProductionLine.Command>> lines) {
        return Behaviors.setup(ctx -> new OrderBook(ctx, lines));
    }

    // Internal state
    private final Queue<Integer> orderQueue = new LinkedList<>();               // Orders waiting to be processed
    private final AtomicInteger orderIdCounter = new AtomicInteger(1);         // For generating automatic order IDs
    private final List<ActorRef<ProductionLine.Command>> lines;                // References to production lines
    private int nextLine = 0;                                                  // Index for round-robin selection

    // === Constructor ===
    private OrderBook(ActorContext<Command> context, List<ActorRef<ProductionLine.Command>> lines) {
        super(context);
        this.lines = lines;

        getContext().getLog().info("OrderBook started");

        // Schedule the first auto-generated order
        scheduleNextOrder();

        // Set up recurring task to try assigning orders every 10 seconds
        getContext().getSystem().scheduler().scheduleAtFixedRate(
                Duration.ofSeconds(10),
                Duration.ofSeconds(10),
                () -> getContext().getSelf().tell(new TryAssignOrder()),
                getContext().getSystem().executionContext()
        );
    }

    // Define how this actor reacts to messages
    @Override
    public Receive<Command> createReceive() {
        return newReceiveBuilder()
                .onMessage(NewOrder.class, this::onNewOrder)
                .onMessage(TryAssignOrder.class, this::onTryAssignOrder)
                .build();
    }

    // Handle incoming new orders
    private Behavior<Command> onNewOrder(NewOrder msg) {
        orderQueue.add(msg.orderId);
        getContext().getLog().info("New order received: #" + msg.orderId);

        scheduleNextOrder();

        return this;
    }

    // Try to assign a waiting order to a production line
    private Behavior<Command> onTryAssignOrder(TryAssignOrder msg) {
        if (!orderQueue.isEmpty()) {
            int orderId = orderQueue.poll();
            getContext().getLog().info("Trying to assign order #" + orderId + " to a production line...");

            // Send the order to the next production line in round-robin fashion
            ActorRef<ProductionLine.Command> chosenLine = lines.get(nextLine);
            chosenLine.tell(new ProductionLine.StartProduction(orderId));
            nextLine = (nextLine + 1) % lines.size();
        } else {
            getContext().getLog().info("No pending orders to assign");
        }
        return this;
    }

    // Schedule the next automatic order to be placed after a short delay
    private void scheduleNextOrder() {
        int nextId = orderIdCounter.getAndIncrement();
        getContext().getSystem().scheduler().scheduleOnce(
                Duration.ofSeconds(15),
                () -> getContext().getSelf().tell(new NewOrder(nextId)),
                getContext().getSystem().executionContext()
        );
    }
}
