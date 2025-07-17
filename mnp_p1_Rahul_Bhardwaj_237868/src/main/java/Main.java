// Rahul Bhardwaj, 237868

import akka.actor.typed.ActorRef;
import akka.actor.typed.ActorSystem;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.Behaviors;

import com.myauto.actors.*;

import java.util.List;

public class Main {
    public static void main(String[] args) {
        ActorSystem<Void> system = ActorSystem.create(AppGuardian.create(), "CarFactory");
    }

    public static class AppGuardian {
        public static Behavior<Void> create() {
            return Behaviors.setup(context -> {

                // Local storages
                ActorRef<LocalStorage.Command> storage1 = context.spawn(LocalStorage.create(), "storage1");
                ActorRef<LocalStorage.Command> storage2 = context.spawn(LocalStorage.create(), "storage2");

                //  Workers
                ActorRef<Worker.Command> w1 = context.spawn(Worker.create("Rahul", storage1), "worker1");
                ActorRef<Worker.Command> w2 = context.spawn(Worker.create("Shivam", storage1), "worker2");
                ActorRef<Worker.Command> w3 = context.spawn(Worker.create("Sujal", storage2), "worker3");
                ActorRef<Worker.Command> w4 = context.spawn(Worker.create("Sahil", storage2), "worker4");

                List<ActorRef<Worker.Command>> workersLine1 = List.of(w1, w2);
                List<ActorRef<Worker.Command>> workersLine2 = List.of(w3, w4);

                // Production lines
                ActorRef<ProductionLine.Command> line1 =
                        context.spawn(ProductionLine.create("Line-1", workersLine1, storage1), "line1");

                ActorRef<ProductionLine.Command> line2 =
                        context.spawn(ProductionLine.create("Line-2", workersLine2, storage2), "line2");

                //  OrderBook
                ActorRef<OrderBook.Command> orderBook =
                        context.spawn(OrderBook.create(List.of(line1, line2)), "orderBook");

                // Send initial test order
               // orderBook.tell(new OrderBook.NewOrder(1001));

                return Behaviors.empty();
            });
        }
    }
}
