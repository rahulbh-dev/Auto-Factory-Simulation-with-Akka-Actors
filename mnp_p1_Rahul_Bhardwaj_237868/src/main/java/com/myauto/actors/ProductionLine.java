// Rahul Bhardwaj, 237868
package com.myauto.actors;

import akka.actor.typed.*;
import akka.actor.typed.javadsl.*;

import java.util.List;
import java.util.Random;

public class ProductionLine extends AbstractBehavior<ProductionLine.Command> {

    // Interface for messages this actor can process
    public interface Command {}

    // Message: Start building a car for a given order
    public static class StartProduction implements Command {
        public final int orderId;
        public StartProduction(int orderId) {
            this.orderId = orderId;
        }
    }

    // Message: Notify that a car is finished
    public static class CarComplete implements Command {
        public final int orderId;
        public CarComplete(int orderId) {
            this.orderId = orderId;
        }
    }

    // Factory method to create the ProductionLine actor
    public static Behavior<Command> create(String name,
                                           List<ActorRef<Worker.Command>> workers,
                                           ActorRef<LocalStorage.Command> storageRef) {
        return Behaviors.setup(ctx ->
                Behaviors.withTimers(timers ->
                        new ProductionLine(ctx, timers, name, workers, storageRef)
                )
        );
    }

    // Internal state
    private final String lineName;                              // e.g., "Line-1"
    private final List<ActorRef<Worker.Command>> workers;       // List of available workers for this line
    private final ActorRef<LocalStorage.Command> storage;       // Reference to the associated local storage
    private final TimerScheduler<Command> timers;
    private final Random random = new Random();                 // For picking a worker at random

    private ProductionLine(ActorContext<Command> context,
                           TimerScheduler<Command> timers,
                           String lineName,
                           List<ActorRef<Worker.Command>> workers,
                           ActorRef<LocalStorage.Command> storage) {
        super(context);
        this.lineName = lineName;
        this.workers = workers;
        this.storage = storage;
        this.timers = timers;
    }

    @Override
    public Receive<Command> createReceive() {
        return newReceiveBuilder()
                .onMessage(StartProduction.class, this::onStartProduction)
                .onMessage(CarComplete.class, this::onCarComplete)
                .build();
    }

    // When a new production order is received
    private Behavior<Command> onStartProduction(StartProduction msg) {
        getContext().getLog().info("[{}] Starting production of car #{}", lineName, msg.orderId);

        // Choose a worker randomly and give them the job
        ActorRef<Worker.Command> selected = workers.get(random.nextInt(workers.size()));
        selected.tell(new Worker.StartJob(msg.orderId, getContext().getSelf()));

        return this;
    }

    // When a worker reports the car is finished
    private Behavior<Command> onCarComplete(CarComplete msg) {
        getContext().getLog().info("[{}] Car #{} is fully assembled and ready!", lineName, msg.orderId);
        return this;
    }
}
