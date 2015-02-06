import java.util.concurrent.TimeUnit;

import scala.concurrent.duration.Duration;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Inbox;
import akka.actor.Props;


public class Main {

	public static void main(String[] args)
	{
		ActorSystem act = ActorSystem.create();
		
		ActorRef master = act.actorOf(Props.create(MasterActor.class));
		
		
		final Inbox inbox = Inbox.create(act);
		
		inbox.watch(master);
		
		
		inbox.send(master, "Hello");
		
		System.out.println(inbox.receive(Duration.create(1, TimeUnit.DAYS)));
		
		
		
		
	}
}
