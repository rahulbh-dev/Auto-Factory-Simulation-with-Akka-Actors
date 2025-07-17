// Rahul Bhardwaj, 237868
package com.myauto.actors;

import akka.actor.typed.*;
import akka.actor.typed.javadsl.*;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class LocalStorage extends AbstractBehavior<LocalStorage.Command> {

    // Interface for messages this actor can handle
    public interface Command {}

    // Request from a worker for a number of parts for a specific order
    public static class RequestParts implements Command {
        public final int orderId;
        public final int count;
        public final ActorRef<PartsResponse> replyTo;

        public RequestParts(int orderId, int count, ActorRef<PartsResponse> replyTo) {
            this.orderId = orderId;
            this.count = count;
            this.replyTo = replyTo;
        }
    }

    // Reply message containing the parts delivered for the given order
    public static class PartsResponse {
        public final int orderId;
        public final List<Part> parts;

        public PartsResponse(int orderId, List<Part> parts) {
            this.orderId = orderId;
            this.parts = parts;
        }
    }

    // Internal message to trigger restocking
    private static class Restock implements Command {}

    // Factory method to create the LocalStorage actor
    public static Behavior<Command> create() {
        return Behaviors.setup(ctx ->
                Behaviors.withTimers(timers -> new LocalStorage(ctx, timers))
        );
    }

    //  State
    private final Map<Part, Integer> inventory = new EnumMap<>(Part.class);  // Tracks quantity of each part
    private final TimerScheduler<Command> timers;

    private LocalStorage(ActorContext<Command> context, TimerScheduler<Command> timers) {
        super(context);
        this.timers = timers;

        // Initialize with 4 of each part
        for (Part part : Part.values()) {
            inventory.put(part, 4);
        }

        getContext().getLog().info("LocalStorage initialized with 4 of each part.");
    }

    // Define how this actor handles different incoming messages
    @Override
    public Receive<Command> createReceive() {
        return newReceiveBuilder()
                .onMessage(RequestParts.class, this::onRequestParts)
                .onMessage(Restock.class, this::onRestock)
                .build();
    }

    // Handles a request from a worker for parts
    private Behavior<Command> onRequestParts(RequestParts msg) {
        List<Part> requested = new ArrayList<>();
        List<Part> delivered = new ArrayList<>();
        List<Part> missing = new ArrayList<>();

        // Randomly pick 'count' parts to fulfill
        List<Part> all = new ArrayList<>(List.of(Part.values()));
        Collections.shuffle(all);
        requested.addAll(all.subList(0, msg.count));

        // Try to fulfill the request from current inventory
        for (Part part : requested) {
            int available = inventory.getOrDefault(part, 0);
            if (available > 0) {
                inventory.put(part, available - 1);
                delivered.add(part);
            } else {
                missing.add(part);
            }
        }

        // If some parts couldn't be delivered, schedule a restock
        if (!missing.isEmpty()) {
            getContext().getLog().info("Parts missing: {}. Triggering restock...", missing);
            timers.startSingleTimer(new Restock(), Duration.ofSeconds(ThreadLocalRandom.current().nextInt(10, 16)));
        }

        // Send back the parts that were successfully delivered
        msg.replyTo.tell(new PartsResponse(msg.orderId, delivered));
        return this;
    }

    // Adds more stock when restocking is triggered
    private Behavior<Command> onRestock(Restock msg) {
        for (Part part : Part.values()) {
            inventory.put(part, inventory.getOrDefault(part, 0) + 3);
        }
        getContext().getLog().info("Restocked 3 of each part.");
        return this;
    }
}
