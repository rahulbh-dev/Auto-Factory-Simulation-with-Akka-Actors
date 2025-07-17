// Rahul Bhardwaj, 237868
package com.myauto.actors;

import akka.actor.typed.*;
import akka.actor.typed.javadsl.*;

import java.time.Duration;
import java.util.List;
import java.util.Random;

public class Worker extends AbstractBehavior<Worker.Command> {

    // Base interface for all messages this actor handles
    public interface Command {}

    // Message sent by ProductionLine to start the full job for a car
    public static class StartJob implements Command {
        public final int orderId;
        public final ActorRef<ProductionLine.Command> replyTo;

        public StartJob(int orderId, ActorRef<ProductionLine.Command> replyTo) {
            this.orderId = orderId;
            this.replyTo = replyTo;
        }
    }

    // Message sent when parts have been received from LocalStorage
    public static class PartsReceived implements Command {
        public final int orderId;
        public final List<Part> parts;
        public final ActorRef<ProductionLine.Command> productionLineRef;

        public PartsReceived(int orderId, List<Part> parts, ActorRef<ProductionLine.Command> productionLineRef) {
            this.orderId = orderId;
            this.parts = parts;
            this.productionLineRef = productionLineRef;
        }
    }

    // Internal message triggered by timer to simulate body build completion
    private static class BuildBodyDone implements Command {
        final int orderId;
        final ActorRef<ProductionLine.Command> replyTo;

        BuildBodyDone(int orderId, ActorRef<ProductionLine.Command> replyTo) {
            this.orderId = orderId;
            this.replyTo = replyTo;
        }
    }

    // Internal message triggered by timer to simulate part installation completion
    private static class InstallDone implements Command {
        final int orderId;
        final ActorRef<ProductionLine.Command> replyTo;

        InstallDone(int orderId, ActorRef<ProductionLine.Command> replyTo) {
            this.orderId = orderId;
            this.replyTo = replyTo;
        }
    }

    // Factory method to create a worker instance
    public static Behavior<Command> create(String name, ActorRef<LocalStorage.Command> localStorage) {
        return Behaviors.setup(ctx ->
                Behaviors.withTimers(timers ->
                        new Worker(ctx, timers, name, localStorage)
                )
        );
    }

    // Internal state
    private final TimerScheduler<Command> timers;
    private final String workerName;  // Used in logs
    private final ActorRef<LocalStorage.Command> localStorage;
    private final Random random = new Random();

    private Worker(ActorContext<Command> context,
                   TimerScheduler<Command> timers,
                   String name,
                   ActorRef<LocalStorage.Command> localStorage) {
        super(context);
        this.timers = timers;
        this.workerName = name;
        this.localStorage = localStorage;
    }

    // Define how the worker reacts to each kind of message
    @Override
    public Receive<Command> createReceive() {
        return newReceiveBuilder()
                .onMessage(StartJob.class, this::onStartJob)
                .onMessage(BuildBodyDone.class, this::onBuildBodyDone)
                .onMessage(PartsReceived.class, this::onPartsReceived)
                .onMessage(InstallDone.class, this::onInstallDone)
                .build();
    }

    // When a job starts, simulate building the car body
    private Behavior<Command> onStartJob(StartJob msg) {
        getContext().getLog().info("[{}] Building body for car #{}", workerName, msg.orderId);

        // Schedule a timer to simulate body construction
        timers.startSingleTimer(
                "buildBody",
                new BuildBodyDone(msg.orderId, msg.replyTo),
                Duration.ofSeconds(5)
        );

        return this;
    }

    // After body build completes, request parts from local storage
    private Behavior<Command> onBuildBodyDone(BuildBodyDone msg) {
        getContext().getLog().info("[{}] Finished building car body for #{}", workerName, msg.orderId);

        // Use message adapter to convert PartsResponse to internal PartsReceived
        ActorRef<LocalStorage.PartsResponse> adapter =
                getContext().messageAdapter(LocalStorage.PartsResponse.class,
                        resp -> new PartsReceived(resp.orderId, resp.parts, msg.replyTo));

        // Ask local storage for 2 random parts
        localStorage.tell(new LocalStorage.RequestParts(msg.orderId, 2, adapter));

        return this;
    }

    // After parts are received, simulate installing them
    private Behavior<Command> onPartsReceived(PartsReceived msg) {
        getContext().getLog().info("[{}] Installing parts {} for car #{}", workerName, msg.parts, msg.orderId);

        // Schedule a timer to simulate installation time
        timers.startSingleTimer(
                "installDone",
                new InstallDone(msg.orderId, msg.productionLineRef),
                Duration.ofSeconds(3)
        );

        return this;
    }

    // Notify production line that the car is fully assembled
    private Behavior<Command> onInstallDone(InstallDone msg) {
        getContext().getLog().info("[{}] Final assembly complete for car #{}", workerName, msg.orderId);

        msg.replyTo.tell(new ProductionLine.CarComplete(msg.orderId));

        return this;
    }
}
